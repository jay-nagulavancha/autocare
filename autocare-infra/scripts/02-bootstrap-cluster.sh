#!/usr/bin/env bash
# 02-bootstrap-cluster.sh
# One-time cluster bootstrap after terraform apply.
# Installs ArgoCD, External Secrets Operator, AWS Load Balancer Controller,
# Metrics Server, Fluent Bit, and populates AWS Secrets Manager.
#
# Usage:
#   export CLUSTER_NAME=autocare-eks
#   export AWS_REGION=us-west-2
#   ./autocare-infra/scripts/02-bootstrap-cluster.sh
#
# DB_PASSWORD / JWT_SECRET are optional:
#   - If unset: read current values from Secrets Manager (secrets must exist — run terraform apply first).
#   - If set:   those values are written to Secrets Manager in step 7 (override / rotation).

set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-autocare-eks}"
AWS_REGION="${AWS_REGION:-us-west-2}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

JWT_SECRET_OVERRIDDEN=false
DB_PASSWORD_OVERRIDDEN=false

get_secret_string() {
  local secret_id=$1
  aws secretsmanager get-secret-value \
    --secret-id "$secret_id" \
    --region "$AWS_REGION" \
    --query SecretString \
    --output text
}

if [[ -z "${JWT_SECRET:-}" ]]; then
  echo "▶ JWT_SECRET unset — reading from Secrets Manager (autocare/jwt-secret)"
  if ! JWT_SECRET=$(get_secret_string autocare/jwt-secret); then
    echo "✗ Could not read autocare/jwt-secret"
    echo "  Run: cd autocare-infra/infra && terraform apply"
    echo "  Or: export JWT_SECRET=... and re-run"
    exit 1
  fi
else
  JWT_SECRET_OVERRIDDEN=true
fi

if [[ -z "${DB_PASSWORD:-}" ]]; then
  echo "▶ DB_PASSWORD unset — reading from Secrets Manager (autocare/db-password)"
  if ! DB_PASSWORD=$(get_secret_string autocare/db-password); then
    echo "✗ Could not read autocare/db-password"
    echo "  Run: cd autocare-infra/infra && terraform apply"
    echo "  Or: export DB_PASSWORD=... and re-run"
    exit 1
  fi
else
  DB_PASSWORD_OVERRIDDEN=true
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
# Server-side apply: client-side apply stores last-applied-configuration on each object;
# the ApplicationSet CRD exceeds the 256KiB annotation limit (invalid CRD error).
kubectl apply -n argocd --server-side --force-conflicts \
  --field-manager=autocare-bootstrap \
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

ESO_NS=external-secrets
ESO_DEPLOYS=(external-secrets external-secrets-webhook external-secrets-cert-controller)

# Helm --wait treats Deployments as failed when Kubernetes marks ProgressDeadlineExceeded
# (default 600s). Slow pulls from ghcr.io on a single small node often exceed that, even if
# pods would become healthy shortly after. Install without Helm --wait, extend the deadline,
# then wait for rollouts (with one restart retry after a prior failed install).
echo "  Installing chart (extended Deployment progress deadline + kubectl rollout wait)..."
helm upgrade --install external-secrets external-secrets/external-secrets \
  --namespace "$ESO_NS" \
  --create-namespace \
  --timeout 15m

echo "  Waiting for Deployments to exist..."
seen=0
for _ in $(seq 1 90); do
  seen=0
  for d in "${ESO_DEPLOYS[@]}"; do
    if kubectl get deploy -n "$ESO_NS" "$d" &>/dev/null; then
      seen=$((seen + 1))
    fi
  done
  [[ "$seen" -eq 3 ]] && break
  sleep 2
done
if [[ "$seen" -ne 3 ]]; then
  echo "  ✗ External Secrets Deployments did not appear within ~3m."
  kubectl get pods,deploy -n "$ESO_NS" 2>/dev/null || true
  exit 1
fi

echo "  Patching progressDeadlineSeconds=1800 (default 600s is tight for cold image pulls)..."
for d in "${ESO_DEPLOYS[@]}"; do
  kubectl patch deployment "$d" -n "$ESO_NS" \
    -p '{"spec":{"progressDeadlineSeconds":1800}}' --type=merge
done

wait_eso_parallel() {
  local timeout=$1
  local pids=()
  for d in "${ESO_DEPLOYS[@]}"; do
    kubectl rollout status "deployment/$d" -n "$ESO_NS" --timeout="$timeout" &
    pids+=("$!")
  done
  local ok=0
  for pid in "${pids[@]}"; do
    if wait "$pid"; then
      ok=$((ok + 1))
    fi
  done
  [[ "$ok" -eq "${#ESO_DEPLOYS[@]}" ]]
}

if ! wait_eso_parallel 25m; then
  echo "  ⚠ Rollout not healthy; restarting Deployments once (often clears a stuck state after timeout)..."
  for d in "${ESO_DEPLOYS[@]}"; do
    kubectl rollout restart "deployment/$d" -n "$ESO_NS" || true
  done
  wait_eso_parallel 25m
fi
echo "  ✓ External Secrets Operator installed"

# ── 4. Install AWS Load Balancer Controller ───────────────────────────────────
echo ""
echo "▶ Step 4/9 — Install AWS Load Balancer Controller"

# Get the LBC IAM role ARN and VPC ID from Terraform outputs
LBC_ROLE_ARN=$(cd "$REPO_ROOT/autocare-infra/infra" && \
  AWS_DEFAULT_REGION="$AWS_REGION" terraform output -raw lbc_role_arn 2>/dev/null || echo "")
VPC_ID=$(cd "$REPO_ROOT/autocare-infra/infra" && \
  AWS_DEFAULT_REGION="$AWS_REGION" terraform output -raw vpc_id 2>/dev/null || echo "")

if [[ -z "$LBC_ROLE_ARN" ]]; then
  echo "  ⚠ Could not read lbc_role_arn from Terraform outputs"
  echo "  Run: cd autocare-infra/infra && terraform apply"
  echo "  Skipping LBC install — install manually after Terraform creates the LBC IAM role."
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

  LBC_HELM_ARGS=(
    --namespace kube-system
    --set "clusterName=$CLUSTER_NAME"
    --set "region=$AWS_REGION"
    --set "defaultTargetType=ip"
    --set serviceAccount.create=false
    --set serviceAccount.name=aws-load-balancer-controller
  )
  if [[ -n "$VPC_ID" ]]; then
    LBC_HELM_ARGS+=(--set "vpcId=$VPC_ID")
  else
    echo "  ⚠ vpc_id not in Terraform outputs — LBC may still work if nodes can infer VPC"
  fi

  helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
    "${LBC_HELM_ARGS[@]}" \
    --wait \
    --timeout 10m
  echo "  ✓ AWS Load Balancer Controller installed"
  echo ""
  echo "  Verify (ALB is created from an Ingress, not from Service type: LoadBalancer):"
  echo "    kubectl get ingressclass"
  echo "    kubectl get deploy -n kube-system aws-load-balancer-controller"
  echo "  After you apply autocare-ingress, wait 2–5 min then:"
  echo "    kubectl get ingress -n autocare -o wide"
  echo "  ADDRESS must show *.elb.amazonaws.com. If it stays empty, check LBC logs:"
  echo "    kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller --tail=50"
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

# Upstream quickstart YAML contains {{cluster_name}}, {{region_name}}, etc. Applying it
# without substitution leaves invalid cwagent JSON / ConfigMaps and often no healthy
# fluent-bit DaemonSet (verify script expects DS fluent-bit in amazon-cloudwatch).
kubectl create namespace amazon-cloudwatch --dry-run=client -o yaml | kubectl apply -f -

CW_FLUENT_URL="https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluent-bit-quickstart.yaml"
FB_MANIFEST=$(mktemp)
trap 'rm -f "${FB_MANIFEST:-}"' EXIT
# ConfigMap .data values must be strings. YAML 1.1 parses bare On/Off as booleans and
# 2020 as an int, which makes kubectl apply fail with "unrecognized type: string".
curl -fsSL "$CW_FLUENT_URL" | sed \
  -e "s/{{cluster_name}}/${CLUSTER_NAME}/g" \
  -e "s/{{region_name}}/${AWS_REGION}/g" \
  -e 's/{{http_server_toggle}}/"On"/g' \
  -e 's/{{http_server_port}}/"2020"/g' \
  -e 's/{{read_from_head}}/"Off"/g' \
  -e 's/{{read_from_tail}}/"On"/g' \
  >"$FB_MANIFEST"

kubectl apply --context "$CLUSTER_NAME" -f "$FB_MANIFEST"
rm -f "$FB_MANIFEST"
trap - EXIT

echo "  Waiting for Fluent Bit DaemonSet..."
kubectl rollout status daemonset/fluent-bit \
  --context "$CLUSTER_NAME" \
  -n amazon-cloudwatch --timeout=180s 2>/dev/null || \
  echo "  ⚠ Fluent Bit rollout check timed out — verify manually"
echo "  ✓ Fluent Bit installed"

# ── 7. Sync AWS Secrets Manager ───────────────────────────────────────────────
echo ""
echo "▶ Step 7/9 — Sync AWS Secrets Manager"

# Get RDS endpoint from Terraform
RDS_ENDPOINT=$(cd "$REPO_ROOT/autocare-infra/infra" && \
  terraform output -raw rds_endpoint 2>/dev/null || echo "")

if [[ -z "$RDS_ENDPOINT" ]]; then
  echo "  ⚠ Could not read rds_endpoint from Terraform — enter it manually:"
  read -rp "  RDS endpoint hostname: " RDS_ENDPOINT
fi

if [[ "$JWT_SECRET_OVERRIDDEN" == true ]]; then
  aws secretsmanager put-secret-value \
    --secret-id autocare/jwt-secret \
    --secret-string "$JWT_SECRET" \
    --region "$AWS_REGION"
  echo "  ✓ autocare/jwt-secret updated (from JWT_SECRET env)"
else
  echo "  ○ autocare/jwt-secret unchanged (fetched from Secrets Manager; set JWT_SECRET to override)"
fi

if [[ "$DB_PASSWORD_OVERRIDDEN" == true ]]; then
  aws secretsmanager put-secret-value \
    --secret-id autocare/db-password \
    --secret-string "$DB_PASSWORD" \
    --region "$AWS_REGION"
  echo "  ✓ autocare/db-password updated (from DB_PASSWORD env)"
else
  echo "  ○ autocare/db-password unchanged (fetched from Secrets Manager; set DB_PASSWORD to override)"
fi

aws secretsmanager put-secret-value \
  --secret-id autocare/rds-endpoint \
  --secret-string "$RDS_ENDPOINT" \
  --region "$AWS_REGION"
echo "  ✓ autocare/rds-endpoint updated (from Terraform output)"

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
