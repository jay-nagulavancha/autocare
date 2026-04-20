#!/usr/bin/env bash
# 04-verify-deployment.sh
# Post-deployment health check — run after git push triggers the CI/CD pipeline
# and ArgoCD has synced.
#
# Usage:
#   export CLUSTER_NAME=autocare-eks
#   export AWS_REGION=us-west-2
#   ./autocare-infra/scripts/04-verify-deployment.sh

set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-autocare-eks}"
AWS_REGION="${AWS_REGION:-us-west-2}"

# Match other scripts: kubeconfig context name is the EKS cluster name (--alias on update-kubeconfig).
kubectl_ctx() { kubectl --context "$CLUSTER_NAME" "$@"; }

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Autocare — Deployment Verification"
echo "  Cluster : $CLUSTER_NAME"
echo "  Region  : $AWS_REGION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

PASS=0
FAIL=0

check() {
  local label="$1"
  local cmd="$2"
  echo -n "  $label ... "
  if eval "$cmd" &>/dev/null; then
    echo "✓"
    PASS=$((PASS + 1))
  else
    echo "✗ FAILED"
    FAIL=$((FAIL + 1))
  fi
}

# ── Cluster ───────────────────────────────────────────────────────────────────
echo ""
echo "▶ Cluster"
check "kubectl can reach cluster" "kubectl_ctx cluster-info"
check "Nodes are Ready" \
  "kubectl_ctx get nodes --no-headers | grep -v NotReady | grep -c Ready | grep -qv '^0$'"

# ── Add-ons ───────────────────────────────────────────────────────────────────
echo ""
echo "▶ Add-ons"
check "Metrics Server running" \
  "kubectl_ctx get deployment metrics-server -n kube-system --no-headers | grep -q '1/1'"
check "AWS LB Controller running" \
  "kubectl_ctx get deployment aws-load-balancer-controller -n kube-system --no-headers | grep -E -q '[12]/[12]'"
check "External Secrets Operator running" \
  "kubectl_ctx get deployment external-secrets -n external-secrets --no-headers | grep -q '1/1'"
check "Fluent Bit DaemonSet has ready pods" \
  "kubectl_ctx get daemonset fluent-bit -n amazon-cloudwatch -o jsonpath='{.status.numberReady}' 2>/dev/null | grep -qE '^[1-9][0-9]*$'"
check "ArgoCD server running" \
  "kubectl_ctx get deployment argocd-server -n argocd --no-headers | grep -q '1/1'"

# ── ArgoCD ────────────────────────────────────────────────────────────────────
echo ""
echo "▶ ArgoCD"
check "autocare Application exists" \
  "kubectl_ctx get application autocare -n argocd"
check "autocare Application is Synced" \
  "kubectl_ctx get application autocare -n argocd -o jsonpath='{.status.sync.status}' | grep -q Synced"
check "autocare Application is Healthy" \
  "kubectl_ctx get application autocare -n argocd -o jsonpath='{.status.health.status}' | grep -q Healthy"

# ── Autocare workloads ────────────────────────────────────────────────────────
echo ""
echo "▶ Autocare workloads (namespace: autocare)"
check "user-auth-service pods Running" \
  "kubectl_ctx get pods -n autocare -l app=user-auth-service --no-headers | grep -q Running"
check "vehicle-maintenance-service pods Running" \
  "kubectl_ctx get pods -n autocare -l app=vehicle-maintenance-service --no-headers | grep -q Running"
check "vehicle-maintenance-ui pods Running" \
  "kubectl_ctx get pods -n autocare -l app=vehicle-maintenance-ui --no-headers | grep -q Running"
check "HPA targets showing metrics (not <unknown>)" \
  "kubectl_ctx get hpa -n autocare --no-headers | grep -v '<unknown>'"
check "Ingress has ALB hostname" \
  "kubectl_ctx get ingress -n autocare --no-headers | grep -v '<none>'"

# ── External Secrets ──────────────────────────────────────────────────────────
echo ""
echo "▶ Secrets"
check "autocare-secrets ExternalSecret is Ready" \
  "kubectl_ctx get externalsecret autocare-secrets -n autocare -o jsonpath='{.status.conditions[0].type}' | grep -q Ready"

# ── Print pod status ──────────────────────────────────────────────────────────
echo ""
echo "▶ Pod status"
kubectl_ctx get pods -n autocare 2>/dev/null || echo "  (namespace not found)"

echo ""
echo "▶ Ingress"
kubectl_ctx get ingress -n autocare 2>/dev/null || echo "  (no ingress found)"

ALB_HOST=$(kubectl_ctx get ingress -n autocare --no-headers \
  -o jsonpath='{.items[0].status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
if [[ -n "$ALB_HOST" ]]; then
  echo ""
  echo "  ALB hostname: $ALB_HOST"
  echo ""
  echo "  Test endpoints:"
  echo "  curl -s -o /dev/null -w '%{http_code}' http://$ALB_HOST/"
  echo "  (expect 301 redirect to HTTPS)"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Results: $PASS passed, $FAIL failed"
if [[ $FAIL -eq 0 ]]; then
  echo "  ✓ All checks passed — deployment is healthy"
else
  echo "  ✗ Some checks failed — review output above"
  echo ""
  if ! kubectl_ctx get application autocare -n argocd &>/dev/null; then
    echo "  Argo: Application missing — register it (after Argo CD is up):"
    echo "    kubectl apply --context $CLUSTER_NAME -f autocare-infra/k8s/argocd/autocare-app.yaml"
  else
    echo "  Argo: application/autocare status: $(kubectl_ctx get application autocare -n argocd -o jsonpath='sync={.status.sync.status} health={.status.health.status}{"\n"}' 2>/dev/null || true)"
    echo "    ./autocare-infra/scripts/05-reconcile-autocare.sh all"
  fi
  echo ""
  echo "  Fluent Bit: re-run bootstrap step 6 (curl | sed placeholders | kubectl apply) or full 02-bootstrap-cluster.sh."
  echo ""
  echo "  Useful debug commands (add --context $CLUSTER_NAME):"
  echo "  kubectl describe pods -n autocare"
  echo "  kubectl logs -n autocare -l app=user-auth-service --tail=50"
  echo "  kubectl get events -n autocare --sort-by=.lastTimestamp"
  echo "  kubectl get application autocare -n argocd -o yaml"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

[[ $FAIL -eq 0 ]]
