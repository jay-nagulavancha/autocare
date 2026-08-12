# Monitoring (AWS Managed Prometheus + Grafana) — jn workspace

Runbook of everything done to add Prometheus + Grafana to the `jn` workspace,
backed by Amazon Managed Service for Prometheus (AMP), and every issue hit
along the way with the exact commands used to fix it.

## Summary

- **Terraform**: new `modules/amp` (AMP workspace + 2 IRSA roles), wired into
  root `main.tf`/`outputs.tf`. Node group scaled 3 → 5 to fit the added pods.
- **Kubernetes**: new ArgoCD Application (`k8s/argocd/monitoring-app.yaml`,
  Helm source: `prometheus-community/kube-prometheus-stack`). Prometheus runs
  in **Agent mode** (scrape + remote-write only, no local TSDB/PVC/EBS).
  Grafana queries AMP directly via native SigV4 auth.
- **Access**: `kubectl port-forward svc/monitoring-grafana -n monitoring 8899:80`
  → `http://localhost:8899` → `admin` / (see `monitoring-grafana` secret,
  command below).

---

## 1. Terraform changes

New module `autocare-infra/infra/modules/amp/{main,variables,outputs}.tf`:
- `aws_prometheus_workspace.this` — the AMP workspace
- `aws_iam_role.amp_ingest` — IRSA role for `monitoring:amp-ingest` SA, policy:
  `aps:RemoteWrite`, `GetSeries`, `GetLabels`, `GetMetricMetadata`
- `aws_iam_role.amp_query` — IRSA role for `monitoring:grafana` SA, policy:
  `aps:QueryMetrics`, `GetSeries`, `GetLabels`, `GetMetricMetadata`

Wired into root `main.tf` as `module "amp"`, outputs added to root `outputs.tf`
(`amp_remote_write_endpoint`, `amp_query_endpoint`, `amp_ingest_role_arn`,
`amp_query_role_arn`).

`workspaces/jn/terraform.tfvars`: `eks_node_desired_size`/`max_size` bumped
3 → 4 → 5 in two passes (see §3.3) to fit the monitoring pods.

```bash
cd autocare-infra/infra
terraform init -backend-config=workspaces/jn/backend.hcl

terraform plan \
  -var-file=workspaces/jn/terraform.tfvars \
  -var-file=workspaces/jn/secrets.tfvars \
  -out=/tmp/jn-amp.tfplan

terraform apply /tmp/jn-amp.tfplan
```

Result: `7 added, 1 changed` (AMP workspace + 2 IAM roles + 2 policies + 2
attachments; node group `desired_size` 3→4). A second pass took it 4→5:

```bash
# after editing eks_node_desired_size/max_size = 5 in terraform.tfvars
terraform plan  -var-file=workspaces/jn/terraform.tfvars -var-file=workspaces/jn/secrets.tfvars -out=/tmp/jn-scale2.tfplan
terraform apply /tmp/jn-scale2.tfplan
```

---

## 2. Kubernetes / ArgoCD deployment

New file: `autocare-infra/k8s/argocd/monitoring-app.yaml` — an ArgoCD
`Application` sourced from the `kube-prometheus-stack` Helm chart
(`prometheus-community`, version `88.3.0`), with inline `helm.values`.

Also edited `autocare-infra/k8s/argocd/autocare-app.yaml` to exclude
`argocd/monitoring-app.yaml` from the `autocare` app's own recursive sync
(same pattern already used to exclude `autocare-app.yaml` itself).

`monitoring-app.yaml` is **not** managed by any parent Application — apply it
manually whenever its spec changes:

```bash
kubectl apply -f autocare-infra/k8s/argocd/monitoring-app.yaml --context jn-eks
```

---

## 3. Issues hit and exact fixes

### 3.1 — prometheus-operator CRDs exceed the 262144-byte annotation limit

Same class of issue as ArgoCD's own `ApplicationSet` CRD during initial
cluster bootstrap. `ServerSideApply=true` in `syncOptions` did **not** fix it
— ArgoCD kept retrying its own apply and hitting the same limit regardless.

**Fix**: install the CRDs directly (bypassing ArgoCD's apply path for just
this step), then set `crds.enabled: false` in the chart values so ArgoCD
stops trying to manage them at all.

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm template monitoring prometheus-community/kube-prometheus-stack \
  --version 88.3.0 --include-crds --namespace monitoring \
  > /tmp/kps-full.yaml

python3 -c "
content = open('/tmp/kps-full.yaml').read()
docs = content.split('\n---\n')
crds = [d for d in docs if 'kind: CustomResourceDefinition' in d]
open('/tmp/kps-crds-only.yaml','w').write('\n---\n'.join(crds))
"

kubectl apply --server-side --force-conflicts --context jn-eks -f /tmp/kps-crds-only.yaml
```

Then in `monitoring-app.yaml` values: `crds: { enabled: false }`.

### 3.2 — Grafana crash-loop: "Only one datasource per organization can be marked as default"

The chart's own auto-provisioned local-Prometheus datasource is also
`isDefault: true` by default, conflicting with the AMP datasource.

**Fix** (values.yaml, no command — committed to git):
```yaml
grafana:
  sidecar:
    datasources:
      defaultDatasourceEnabled: false
```

### 3.3 — Grafana → AMP query returns 403 "Missing Authentication Token"

Per-datasource `sigV4Auth: true` alone doesn't make Grafana sign requests —
Grafana was sending **unsigned** requests, which AMP's API rejects outright.

**Fix** (values.yaml):
```yaml
grafana:
  grafana.ini:
    auth:
      sigv4_auth_enabled: true
```

**Verification** used to isolate this (proved IRSA/AMP ingestion itself was
already working before finding the Grafana-specific cause):
```bash
# Confirm the IRSA role itself assumes fine
kubectl run irsa-test -n monitoring --context jn-eks --image=amazon/aws-cli:latest \
  --overrides='{"spec":{"serviceAccountName":"grafana"}}' \
  --restart=Never --command -- sh -c "sleep 20 && aws sts get-caller-identity"

# Manually SigV4-sign a request to AMP's query API to prove ingestion + query both work
# (used python:3.12-slim with botocore installed at runtime — see git history of this
# file's earlier drafts, or re-derive: SigV4Auth(creds, 'aps', 'us-west-2') against
# <query_endpoint>/api/v1/labels)
```

### 3.4 — Prometheus OOMKilled (exit 137) at a 256Mi memory limit

Cluster-wide scraping (apiserver, kubelet, etcd, kube-state-metrics, 5x
node-exporter) plus the remote-write WAL buffer needs more than 256Mi even in
agent mode.

**Fix** (values.yaml): `prometheus.prometheusSpec.resources.limits.memory`
`256Mi` → `512Mi`, `requests.memory` `128Mi` → `256Mi`.

**Diagnosis commands used:**
```bash
kubectl get pod prometheus-monitoring-kube-prometheus-prometheus-0 -n monitoring --context jn-eks \
  -o jsonpath='{.status.containerStatuses[?(@.name=="prometheus")].lastState}'
# -> "reason":"OOMKilled","exitCode":137
```

**Immediate unblock** (direct patch, ahead of ArgoCD picking up the git
change — safe since both converge to the same value):
```bash
kubectl patch prometheus monitoring-kube-prometheus-prometheus -n monitoring --context jn-eks \
  --type merge -p '{"spec":{"resources":{"requests":{"cpu":"50m","memory":"256Mi"},"limits":{"cpu":"300m","memory":"512Mi"}}}}'
```

### 3.5 — ArgoCD sync got stuck on a stale operation lock (happened twice)

Symptom: `.status.operationState.phase` stays `Running` forever with an
unchanging `startedAt`, controller logs show `"Skipping auto-sync: another
operation is in progress"` on every reconcile loop, and new commits never
actually get applied even after `kubectl apply -f monitoring-app.yaml`.

**Fix** — clear the stuck operation, then force a fresh sync:
```bash
kubectl patch application monitoring -n argocd --context jn-eks \
  --type json -p '[{"op":"remove","path":"/operation"}]'

kubectl patch application monitoring -n argocd --context jn-eks \
  --subresource=status --type merge -p '{"status":{"operationState":null}}'

# If still stuck, restart the controller (StatefulSet, safe to delete-and-recreate):
kubectl delete pod argocd-application-controller-0 -n argocd --context jn-eks
kubectl wait --for=condition=Ready pod/argocd-application-controller-0 -n argocd --context jn-eks --timeout=120s
```

### 3.6 — Operator never noticed the CRDs existed

The `kube-prometheus-operator` pod started (`19:11:57`) **before** the CRDs
were manually applied (§3.1), logged `resource "prometheuses" ... not
installed in the cluster` at boot, and cached that — zero reconcile logs for
`Prometheus`/`Alertmanager` objects afterward, no StatefulSet ever created.

**Fix** — restart the operator once CRDs actually exist:
```bash
kubectl rollout restart deployment monitoring-kube-prometheus-operator -n monitoring --context jn-eks
kubectl rollout status deployment monitoring-kube-prometheus-operator -n monitoring --context jn-eks --timeout=120s
```

### 3.7 — Node-exporter DaemonSet pods stuck Pending ("Too many pods")

`t3.small` caps at 11 pods/node (ENI/IP-limited). Adding a global Nth node
doesn't free up an already-full node — DaemonSet pods are affinitized to a
specific node each. Fixed by bumping node count (§1); one node still sits at
its ceiling with `node-exporter` `Pending` there — accepted as a known minor
gap (missing host metrics for that one node only), not blocking.

---

## 4. Final verification

```bash
# Prometheus pod healthy, remote-write clean
kubectl get pods -n monitoring --context jn-eks
kubectl logs prometheus-monitoring-kube-prometheus-prometheus-0 -n monitoring --context jn-eks -c prometheus --tail=20

# Grafana datasource health (the real end-to-end proof)
kubectl port-forward svc/monitoring-grafana -n monitoring --context jn-eks 8899:80 &
GRAFANA_PASS=$(kubectl get secret monitoring-grafana -n monitoring --context jn-eks -o jsonpath='{.data.admin-password}' | base64 -d)
curl -s -u "admin:$GRAFANA_PASS" http://localhost:8899/api/datasources/uid/PD476F800710936AE/health
# -> {"message":"Successfully queried the Prometheus API.","status":"OK"}

# Terraform matches live state
cd autocare-infra/infra
terraform plan -var-file=workspaces/jn/terraform.tfvars -var-file=workspaces/jn/secrets.tfvars
# -> No changes.
```
