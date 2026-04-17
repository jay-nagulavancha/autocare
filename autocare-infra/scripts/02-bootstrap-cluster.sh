#!/usr/bin/env bash
# 02-bootstrap-cluster.sh
# One-time cluster bootstrap after terraform apply.
# Installs ArgoCD, External Secrets Operator, AWS Load Balancer Controller,
# Metrics Server, Fluent Bit, and populates AWS Secrets Manager.
#
# Usage:
#   export CLUSTER_NAME=autocare-eks
#   export AWS_REGION=us-west-2
#   export DB_PASSWORD=your-db-password
#   export JWT_SECRET=your-jwt-secret
#   ./autocare-infra/scripts/02-bootstrap-cluster.sh

set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-autocare-eks}"
AWS_REGION="${AWS_REGION:-us-west-2}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Validate required env vars
if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "✗ DB_PASSWORD env var is required"
  echo "  export DB_PASSWORD=your-db-password"
  exit 1
fi
if [[ -z "${JWT_SECRET:-}" ]]; then
  echo "✗ JWT_SECRET env var is required"
  echo "  export JWT_SECRET=your-jwt-secret"
  exit 1
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Autocare — Cluster Bootstrap"
echo "  Cluster : $CLUSTER_NAME"
echo "  Region  : $AWS_REGION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 1. Configure kubectl ──────────────────────────────────────────────────────
echo ""
echo "▶ Step 1/9 — Configure kubectl"
aws eks update-kubeconfig \
  --name "$CLUSTER_NAME" \
  --region "$AWS_REGION" \
  --alias "$CLUSTER_NAME"
echo "  ✓ kubeconfig updated"

# Verify cluster is reachable
kubectl cluster-info --context "$CLUSTER_NAME"

# ── 2. Install ArgoCD ─────────────────────────────────────────────────────────
echo ""
echo "▶ Step 2/9 — Install ArgoCD"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

echo "  Waiting for ArgoCD server to be ready..."
kubectl wait --for=condition=available deployment/argocd-server \
  -n argocd --timeout=180s
echo "  ✓ ArgoCD installed"

# Print initial admin password
ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d)
echo ""
echo "  ArgoCD admin password: $ARGOCD_PASSWORD"
echo "  (save this — it won't be shown again)"
echo ""

# ── 3. Install External Secrets Operator ─────────────────────────────────────
echo ""
echo "▶ Step 3/9 — Install External Secrets Operator"
helm repo add external-secrets https://charts.external-secrets.io 2>/dev/null || true
helm repo update

helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace external-secrets \
  --create-namespace \
  --wait \
  --timeout 120s
echo "  ✓ External Secrets Operator installed"

# ── 4. Install AWS Load Balancer Controller ───────────────────────────────────
echo ""
echo "▶ Step 4/9 — Install AWS Load Balancer Controller"

# Get the LBC IAM role ARN from Terraform outputs
LBC_ROLE_ARN=$(cd "$REPO_ROOT/autocare-infra/infra" && \
  terraform output -raw lbc_role_arn 2>/dev/null || echo "")

if [[ -z "$LBC_ROLE_ARN" ]]; then
  echo "  ⚠ Could not read lbc_role_arn from Terraform outputs"
  echo "  You may need to add an LBC IAM role to the IAM module."
  echo "  Skipping LBC install — install manually after adding the IAM role."
else
  # Create service account with IRSA annotation
  kubectl create serviceaccount aws-load-balancer-controller \
    -n kube-system --dry-run=client -o yaml | kubectl apply -f -
  kubectl annotate serviceaccount aws-load-balancer-controller \
    -n kube-system \
    eks.amazonaws.com/role-arn="$LBC_ROLE_ARN" \
    --overwrite

  helm repo add eks https://aws.github.io/eks-charts 2>/dev/null || true
  helm repo update

  helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
    --namespace kube-system \
    --set clusterName="$CLUSTER_NAME" \
    --set serviceAccount.create=false \
    --set serviceAccount.name=aws-load-balancer-controller \
    --wait \
    --timeout 120s
  echo "  ✓ AWS Load Balancer Controller installed"
fi

# ── 5. Install Metrics Server ─────────────────────────────────────────────────
echo ""
echo "▶ Step 5/9 — Install Metrics Server"
kubectl apply -f \
  https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml

echo "  Waiting for Metrics Server..."
kubectl wait --for=condition=available deployment/metrics-server \
  -n kube-system --timeout=120s
echo "  ✓ Metrics Server installed"

# ── 6. Install Fluent Bit (CloudWatch Container Insights) ────────────────────
echo ""
echo "▶ Step 6/9 — Install Fluent Bit for CloudWatch"

# Create the CloudWatch namespace and ConfigMap
kubectl create namespace amazon-cloudwatch --dry-run=client -o yaml | kubectl apply -f -

kubectl create configmap fluent-bit-cluster-info \
  --from-literal=cluster.name="$CLUSTER_NAME" \
  --from-literal=http.server=On \
  --from-literal=http.port=2020 \
  --from-literal=read.head=Off \
  --from-literal=read.tail=On \
  --from-literal=logs.region="$AWS_REGION" \
  -n amazon-cloudwatch \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluent-bit-quickstart.yaml

echo "  Waiting for Fluent Bit DaemonSet..."
kubectl rollout status daemonset/fluent-bit \
  -n amazon-cloudwatch --timeout=120s 2>/dev/null || \
  echo "  ⚠ Fluent Bit rollout check timed out — verify manually"
echo "  ✓ Fluent Bit installed"

# ── 7. Populate AWS Secrets Manager ──────────────────────────────────────────
echo ""
echo "▶ Step 7/9 — Populate AWS Secrets Manager"

# Get RDS endpoint from Terraform
RDS_ENDPOINT=$(cd "$REPO_ROOT/autocare-infra/infra" && \
  terraform output -raw rds_endpoint 2>/dev/null || echo "")

if [[ -z "$RDS_ENDPOINT" ]]; then
  echo "  ⚠ Could not read rds_endpoint from Terraform — enter it manually:"
  read -rp "  RDS endpoint hostname: " RDS_ENDPOINT
fi

aws secretsmanager put-secret-value \
  --secret-id autocare/jwt-secret \
  --secret-string "$JWT_SECRET" \
  --region "$AWS_REGION"
echo "  ✓ autocare/jwt-secret updated"

aws secretsmanager put-secret-value \
  --secret-id autocare/db-password \
  --secret-string "$DB_PASSWORD" \
  --region "$AWS_REGION"
echo "  ✓ autocare/db-password updated"

aws secretsmanager put-secret-value \
  --secret-id autocare/rds-endpoint \
  --secret-string "$RDS_ENDPOINT" \
  --region "$AWS_REGION"
echo "  ✓ autocare/rds-endpoint updated"

# ── 8. Apply ArgoCD Application CRD ──────────────────────────────────────────
echo ""
echo "▶ Step 8/9 — Apply ArgoCD Application CRD"
kubectl apply -f "$REPO_ROOT/autocare-infra/k8s/argocd/autocare-app.yaml"
echo "  ✓ ArgoCD Application registered"
echo "  ArgoCD will now sync k8s/ manifests to the autocare namespace"

# ── 9. Verify ─────────────────────────────────────────────────────────────────
echo ""
echo "▶ Step 9/9 — Verification"
echo ""
echo "  Cluster info:"
kubectl cluster-info --context "$CLUSTER_NAME"

echo ""
echo "  Nodes:"
kubectl get nodes

echo ""
echo "  ArgoCD application:"
kubectl get application autocare -n argocd 2>/dev/null || \
  echo "  (ArgoCD app not synced yet — wait 30s and check again)"

echo ""
echo "  Pods in autocare namespace:"
kubectl get pods -n autocare 2>/dev/null || \
  echo "  (namespace not yet created — ArgoCD will create it on first sync)"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✓ Bootstrap complete"
echo ""
echo "  ArgoCD UI (port-forward to access locally):"
echo "  kubectl port-forward svc/argocd-server -n argocd 8888:443"
echo "  Open: https://localhost:8888  (admin / $ARGOCD_PASSWORD)"
echo ""
echo "  Next steps:"
echo "  1. Configure GitHub secrets (see autocare-infra/REVIEW.md)"
echo "  2. git add . && git commit -m 'feat: EKS deployment' && git push"
echo "  3. Watch ArgoCD sync: kubectl get pods -n autocare -w"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
