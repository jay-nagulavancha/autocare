# Cluster Bootstrap Guide

This document describes the **one-time** steps required to bootstrap a new EKS cluster for the Autocare platform. These steps are performed once after the cluster is provisioned — they are not repeated on every deploy. Ongoing deployments are handled automatically by ArgoCD via GitOps.

---

## Prerequisites

- AWS CLI configured with sufficient permissions
- `kubectl` installed and configured
- `helm` v3 installed
- Terraform >= 1.6 installed
- Access to the Git repository

---

## Bootstrap Steps

### 1. Provision AWS Infrastructure with Terraform

Run `terraform apply` from the `infra/` directory to provision all AWS resources:

```bash
cd autocare-infra/infra
terraform init
terraform apply
```

This provisions:
- VPC (public + private subnets across two AZs)
- EKS cluster (Kubernetes 1.29, managed node group, OIDC provider)
- RDS MySQL 8.0 (Multi-AZ, private subnets)
- ECR repositories (user-auth-service, vehicle-maintenance-service, vehicle-maintenance-ui)
- IAM roles (IRSA for autocare-sa, CI/CD pipeline role)
- ACM certificate (DNS validation)
- CloudWatch log groups

After `terraform apply` completes, configure `kubectl` to point at the new cluster:

```bash
aws eks update-kubeconfig --region <AWS_REGION> --name <CLUSTER_NAME>
```

---

### 2. Install ArgoCD

Create the `argocd` namespace and install ArgoCD using the official stable manifest:

```bash
kubectl create namespace argocd
# Use server-side apply: client-side apply can fail on the ApplicationSet CRD (annotation size limit).
# With multiple kube contexts, add --context <your-eks-context> to each kubectl command (02-bootstrap-cluster.sh does this via CLUSTER_NAME).
kubectl apply -n argocd --server-side --force-conflicts \
  --field-manager=autocare-bootstrap \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

---

### 3. Wait for ArgoCD to Be Ready

Wait until the ArgoCD server deployment is available before proceeding:

```bash
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=15m
```

---

### 4. Apply the ArgoCD Application CRD

Register the Autocare application with ArgoCD. This tells ArgoCD to watch the `k8s/` directory in the Git repository and sync it to the `autocare` namespace:

```bash
kubectl apply -f autocare-infra/k8s/argocd/autocare-app.yaml
```

ArgoCD will immediately begin syncing all manifests under `k8s/` to the cluster. From this point on, every `git push` that updates `k8s/` will trigger an automatic sync.

#### Argo CD UI on a separate ALB

The repo includes `autocare-infra/k8s/argocd/alb/argocd-server-alb-ingress.yaml`, which creates a **second** internet-facing ALB (the app uses `k8s/ingress/autocare-ingress.yaml` in namespace `autocare`). That Ingress stays in namespace `argocd` and targets `Service/argocd-server` port **80**. `02-bootstrap-cluster.sh` sets `server.insecure: "true"` in `argocd-cmd-params-cm` so the API server speaks **HTTP** on that port (required for a plain HTTP listener).

After sync, wait for the AWS Load Balancer Controller to set the hostname, then open the UI:

```bash
kubectl get ingress argocd-server-alb -n argocd -o jsonpath='{.status.loadBalancer.ingress[0].hostname}{"\n"}'
```

Use `http://<hostname>/` and log in as `admin` with the password from `argocd-initial-admin-secret`. For **HTTPS** and full **gRPC** support on ALB, add an ACM certificate annotation and follow the [Argo CD AWS ALB ingress section](https://argo-cd.readthedocs.io/en/stable/operator-manual/ingress/#aws-application-load-balancers-albs-ingress).

---

### 5. Install External Secrets Operator

The External Secrets Operator bridges AWS Secrets Manager and Kubernetes Secrets:

Helm’s `--wait` alone is brittle here: Kubernetes defaults `progressDeadlineSeconds` to **600s**, so slow image pulls (e.g. `ghcr.io` on a single small node) can mark the Deployments **Failed** even when pods would become ready shortly after. Prefer install **without** Helm `--wait`, raise the deadline, then wait for rollouts:

```bash
helm repo add external-secrets https://charts.external-secrets.io
helm repo update
helm upgrade --install external-secrets external-secrets/external-secrets \
  -n external-secrets \
  --create-namespace \
  --timeout 15m

for d in external-secrets external-secrets-webhook external-secrets-cert-controller; do
  kubectl patch deployment "$d" -n external-secrets \
    -p '{"spec":{"progressDeadlineSeconds":1800}}' --type=merge
done

kubectl rollout status deployment/external-secrets -n external-secrets --timeout=25m &
kubectl rollout status deployment/external-secrets-webhook -n external-secrets --timeout=25m &
kubectl rollout status deployment/external-secrets-cert-controller -n external-secrets --timeout=25m &
wait
```

If a prior attempt left Deployments stuck, run `kubectl rollout restart deployment/<name> -n external-secrets` for each, then the `rollout status` lines again.

---

### 6. Install AWS Load Balancer Controller

The AWS Load Balancer Controller provisions ALBs from Kubernetes Ingress resources.

Add the EKS Helm chart repository and install the controller:

```bash
helm repo add eks https://aws.github.io/eks-charts
helm repo update
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<CLUSTER_NAME> \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

> The IAM service account for the controller must be created and annotated with the appropriate IRSA role ARN before running this command. Refer to the [AWS Load Balancer Controller documentation](https://kubernetes-sigs.github.io/aws-load-balancer-controller/) for the required IAM policy.

---

### 7. Install Metrics Server

The Metrics Server is required for Horizontal Pod Autoscaler (HPA) CPU metrics:

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```

Verify it is running:

```bash
kubectl get deployment metrics-server -n kube-system
```

---

### 8. Install Fluent Bit for CloudWatch Container Insights

Fluent Bit ships container logs to the CloudWatch log groups provisioned by Terraform.

The upstream quickstart manifest contains placeholders (`{{cluster_name}}`, `{{region_name}}`, …). **Do not** `kubectl apply` the raw URL without replacing them, or the CloudWatch agent config and Fluent Bit `ConfigMap` stay invalid.

For `fluent-bit-cluster-info`, `ConfigMap.data` values must be **strings**. In YAML 1.1, bare `On` / `Off` / `2020` become bool/int, and `kubectl apply` then fails with `unrecognized type: string` — quote those substitutions (as below).

```bash
kubectl create namespace amazon-cloudwatch --dry-run=client -o yaml | kubectl apply -f -

CW_FLUENT_URL="https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluent-bit-quickstart.yaml"
FB_MANIFEST=$(mktemp)
curl -fsSL "$CW_FLUENT_URL" | sed \
  -e "s/{{cluster_name}}/${CLUSTER_NAME}/g" \
  -e "s/{{region_name}}/${AWS_REGION}/g" \
  -e 's/{{http_server_toggle}}/"On"/g' \
  -e 's/{{http_server_port}}/"2020"/g' \
  -e 's/{{read_from_head}}/"Off"/g' \
  -e 's/{{read_from_tail}}/"On"/g' \
  >"$FB_MANIFEST"
kubectl apply -f "$FB_MANIFEST"
rm -f "$FB_MANIFEST"
```

Refer to the [CloudWatch Container Insights EKS quickstart](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-setup-EKS-quickstart.html) for details.

---

### 9. Populate Secrets in AWS Secrets Manager

The following secrets must be populated with real values before the application pods can start. Terraform creates the secret resources with placeholder values — replace them now:

```bash
# JWT signing key (shared between user-auth-service and vehicle-maintenance-service)
aws secretsmanager put-secret-value \
  --secret-id autocare/jwt-secret \
  --secret-string "<YOUR_BASE64_JWT_SECRET>"

# MySQL root password
aws secretsmanager put-secret-value \
  --secret-id autocare/db-password \
  --secret-string "<YOUR_DB_PASSWORD>"

# RDS endpoint hostname (output from terraform apply)
aws secretsmanager put-secret-value \
  --secret-id autocare/rds-endpoint \
  --secret-string "<RDS_ENDPOINT_HOSTNAME>"
```

> **Never commit secret values to source control.** All secrets are injected at pod startup via the External Secrets Operator.

---

### 10. ArgoCD Auto-Sync Deploys All Services

Once the above steps are complete, ArgoCD will automatically sync the `k8s/` manifests to the cluster. The External Secrets Operator will pull the secrets from Secrets Manager and create the Kubernetes Secrets. The pods will start and become healthy.

Monitor the sync status:

```bash
# Check ArgoCD application status
kubectl get application autocare -n argocd

# Watch pods come up in the autocare namespace
kubectl get pods -n autocare -w
```

The application is ready when the ArgoCD Application reports `Healthy` and `Synced`, and all pods in the `autocare` namespace are in `Running` state.

---

## Post-Bootstrap Verification

Run these checks to confirm the cluster is healthy:

```bash
# Cluster reachable
kubectl cluster-info

# All autocare pods running
kubectl get pods -n autocare

# ArgoCD application healthy
kubectl get application autocare -n argocd

# HPA targets showing CPU metrics (not <unknown>)
kubectl get hpa -n autocare

# Fluent Bit DaemonSet running on all nodes
kubectl get daemonset -n amazon-cloudwatch
```

---

## Ongoing Deployments

After bootstrap, deployments are fully automated:

1. A developer merges a PR to `main`
2. GitHub Actions runs tests, builds Docker images, pushes to ECR tagged with the git SHA
3. The CI pipeline updates the image tag in `k8s/deployments/*.yaml` and commits back to the repository
4. ArgoCD detects the new commit and syncs the updated manifests to the `autocare` namespace
5. ArgoCD monitors rollout health — if health checks fail, it automatically rolls back to the previous revision

No manual `kubectl` commands are needed for routine deployments.
