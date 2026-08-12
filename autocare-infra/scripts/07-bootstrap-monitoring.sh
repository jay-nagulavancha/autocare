#!/usr/bin/env bash
# 07-bootstrap-monitoring.sh
# Installs the Prometheus + Grafana monitoring stack (AWS Managed Prometheus backend)
# on top of an already-bootstrapped cluster (run 02-bootstrap-cluster.sh first).
#
# Not part of 02-bootstrap-cluster.sh's flow — kept separate since it's optional and
# was added after initial cluster setup. See workspaces/jn/MONITORING.md for the full
# story of every issue this script works around.
#
# Usage:
#   export CLUSTER_NAME=jn-eks
#   export AWS_REGION=us-west-2
#   ./autocare-infra/scripts/07-bootstrap-monitoring.sh
#
# What this does, and why each step exists:
#   1. Install prometheus-operator's CRDs directly via `kubectl apply --server-side`,
#      BEFORE the operator pod starts. Two independent bugs otherwise bite:
#        - The CRDs exceed the 262144-byte metadata.annotations limit when ArgoCD
#          applies them (same class of issue as ArgoCD's own ApplicationSet CRD
#          during cluster bootstrap) — happens even with ServerSideApply=true.
#        - If the operator starts before the CRDs exist, it caches "not installed"
#          at boot and never notices them later, so it never reconciles Prometheus/
#          Alertmanager objects (zero reconcile logs, no StatefulSet, ever).
#      Installing CRDs first, before the chart's Deployment is created, avoids both.
#   2. Apply the monitoring ArgoCD Application (Helm source: kube-prometheus-stack,
#      values already have crds.enabled=false so it doesn't fight over the CRDs).
#   3. Wait for sync, working around ArgoCD getting stuck on a stale operation lock
#      (observed twice during development — status.operationState.phase stays
#      "Running" forever and the controller logs "another operation is in progress"
#      on every loop, even after a fresh kubectl apply).
#   4. Verify the Prometheus pod is healthy (not OOMKilled) and print Grafana access.

set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-autocare-eks}"
AWS_REGION="${AWS_REGION:-us-west-2}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MONITORING_APP="$REPO_ROOT/autocare-infra/k8s/argocd/monitoring-app.yaml"
NAMESPACE="monitoring"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Autocare — Monitoring Bootstrap (Prometheus + Grafana)"
echo "  Cluster : $CLUSTER_NAME"
echo "  Region  : $AWS_REGION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

[[ -f "$MONITORING_APP" ]] || { echo "✗ Not found: $MONITORING_APP"; exit 1; }

kubectl cluster-info --context "$CLUSTER_NAME" >/dev/null || {
  echo "✗ Cannot reach cluster '$CLUSTER_NAME' — run 02-bootstrap-cluster.sh first (or aws eks update-kubeconfig)"
  exit 1
}

# ── 1. Install prometheus-operator CRDs (server-side apply, before the operator starts) ──
echo ""
echo "▶ Step 1/4 — Install prometheus-operator CRDs"

CHART_VERSION=$(grep -A1 'chart: kube-prometheus-stack' "$MONITORING_APP" | grep targetRevision | sed 's/.*targetRevision: *//')
[[ -n "$CHART_VERSION" ]] || { echo "✗ Could not parse chart version from $MONITORING_APP"; exit 1; }
echo "  Chart version: $CHART_VERSION"

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update prometheus-community >/dev/null

RENDERED=$(mktemp)
CRDS_ONLY=$(mktemp)
trap 'rm -f "$RENDERED" "$CRDS_ONLY"' EXIT

helm template monitoring prometheus-community/kube-prometheus-stack \
  --version "$CHART_VERSION" --include-crds --namespace "$NAMESPACE" \
  > "$RENDERED"

python3 -c "
content = open('$RENDERED').read()
docs = content.split('\n---\n')
crds = [d for d in docs if 'kind: CustomResourceDefinition' in d]
open('$CRDS_ONLY', 'w').write('\n---\n'.join(crds))
print(f'  {len(crds)} CRD manifests extracted')
"

kubectl apply --server-side --force-conflicts --context "$CLUSTER_NAME" -f "$CRDS_ONLY"
echo "  ✓ CRDs installed"

# ── 2. Apply the monitoring ArgoCD Application ────────────────────────────────
echo ""
echo "▶ Step 2/4 — Apply monitoring ArgoCD Application"
kubectl apply -f "$MONITORING_APP" --context "$CLUSTER_NAME"
echo "  ✓ Application applied"

# ── 3. Wait for sync, unsticking a stale operation lock if needed ────────────
echo ""
echo "▶ Step 3/4 — Wait for ArgoCD sync"

STUCK_STARTED_AT=""
UNSTUCK_ATTEMPTED=false
SYNCED=false

for i in $(seq 1 30); do
  PHASE=$(kubectl get application monitoring -n argocd --context "$CLUSTER_NAME" -o jsonpath='{.status.operationState.phase}' 2>/dev/null || echo "")
  STARTED_AT=$(kubectl get application monitoring -n argocd --context "$CLUSTER_NAME" -o jsonpath='{.status.operationState.startedAt}' 2>/dev/null || echo "")
  SYNC_STATUS=$(kubectl get application monitoring -n argocd --context "$CLUSTER_NAME" -o jsonpath='{.status.sync.status}' 2>/dev/null || echo "")
  HEALTH=$(kubectl get application monitoring -n argocd --context "$CLUSTER_NAME" -o jsonpath='{.status.health.status}' 2>/dev/null || echo "")
  echo "  [$i/30] phase=$PHASE sync=$SYNC_STATUS health=$HEALTH"

  if [[ "$SYNC_STATUS" == "Synced" && ( "$HEALTH" == "Healthy" || "$HEALTH" == "Progressing" ) ]]; then
    SYNCED=true
    break
  fi

  # Detect a stuck operation: same startedAt seen 3 times in a row (~45s) while
  # not making progress -> clear it and force a fresh sync, once.
  if [[ "$PHASE" == "Running" && -n "$STARTED_AT" ]]; then
    if [[ "$STARTED_AT" == "$STUCK_STARTED_AT" ]]; then
      STUCK_COUNT=$((${STUCK_COUNT:-0} + 1))
    else
      STUCK_COUNT=0
      STUCK_STARTED_AT="$STARTED_AT"
    fi
    if [[ "$STUCK_COUNT" -ge 3 && "$UNSTUCK_ATTEMPTED" == false ]]; then
      echo "  ⚠ Operation appears stuck (startedAt unchanged for ~45s) — clearing and restarting the controller"
      kubectl patch application monitoring -n argocd --context "$CLUSTER_NAME" \
        --type json -p '[{"op":"remove","path":"/operation"}]' 2>/dev/null || true
      kubectl delete pod argocd-application-controller-0 -n argocd --context "$CLUSTER_NAME" 2>/dev/null || true
      kubectl wait --for=condition=Ready pod/argocd-application-controller-0 -n argocd --context "$CLUSTER_NAME" --timeout=120s 2>/dev/null || true
      UNSTUCK_ATTEMPTED=true
      STUCK_COUNT=0
    fi
  fi

  sleep 15
done

if [[ "$SYNCED" != true ]]; then
  echo "  ✗ Application did not reach Synced/Healthy within 7.5 min"
  kubectl get application monitoring -n argocd --context "$CLUSTER_NAME" -o wide 2>/dev/null || true
  echo "  Check manually: kubectl get application monitoring -n argocd -o yaml"
  exit 1
fi
echo "  ✓ Application Synced"

# ── 4. Verify Prometheus is healthy, print access info ───────────────────────
echo ""
echo "▶ Step 4/4 — Verify"

echo "  Waiting for the Prometheus operator to reconcile the Prometheus CR into a StatefulSet..."
for i in $(seq 1 20); do
  if kubectl get statefulset -n "$NAMESPACE" --context "$CLUSTER_NAME" -l app.kubernetes.io/name=prometheus 2>/dev/null | grep -q prometheus; then
    break
  fi
  sleep 10
done

if ! kubectl rollout status statefulset/prometheus-monitoring-kube-prometheus-prometheus \
  -n "$NAMESPACE" --context "$CLUSTER_NAME" --timeout=180s; then
  echo "  ✗ Prometheus StatefulSet did not become healthy"
  echo "  Common cause: OOMKilled — check exit code 137 below."
  kubectl get pods -n "$NAMESPACE" --context "$CLUSTER_NAME" -l app.kubernetes.io/name=prometheus
  kubectl get pod -n "$NAMESPACE" --context "$CLUSTER_NAME" -l app.kubernetes.io/name=prometheus \
    -o jsonpath='{.items[0].status.containerStatuses[?(@.name=="prometheus")].lastState}' 2>/dev/null || true
  exit 1
fi
echo "  ✓ Prometheus healthy"

echo ""
echo "  Pods:"
kubectl get pods -n "$NAMESPACE" --context "$CLUSTER_NAME"

GRAFANA_PASS=$(kubectl get secret monitoring-grafana -n "$NAMESPACE" --context "$CLUSTER_NAME" \
  -o jsonpath='{.data.admin-password}' 2>/dev/null | base64 -d || echo "(could not read — check secret manually)")

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✓ Monitoring bootstrap complete"
echo ""
echo "  Grafana:"
echo "    kubectl port-forward svc/monitoring-grafana -n $NAMESPACE --context $CLUSTER_NAME 8899:80"
echo "    Open: http://localhost:8899/  (admin / $GRAFANA_PASS)"
echo ""
echo "  Note: node-exporter DaemonSet may show one pod Pending if a node is already"
echo "  at its per-node pod cap (t3.small caps at 11) — not a monitoring bug, see"
echo "  workspaces/jn/MONITORING.md §3.7. Everything else should be fully healthy."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
