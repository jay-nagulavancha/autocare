#!/usr/bin/env bash
# 05-reconcile-autocare.sh — Operational fixes that are often run manually:
#   - Argo CD stale Git: hard-refresh the autocare Application so HEAD is re-fetched
#   - After AWS Secrets Manager updates: force ExternalSecret sync + restart workloads that read JWT/DB
#
# Usage:
#   export CLUSTER_NAME=autocare-eks   # optional
#   export AWS_REGION=us-west-2       # optional
#   ./autocare-infra/scripts/05-reconcile-autocare.sh argo
#   ./autocare-infra/scripts/05-reconcile-autocare.sh secrets
#   ./autocare-infra/scripts/05-reconcile-autocare.sh all
#
# Modes:
#   argo     — patch Application with hard refresh; print sync revision and status
#   secrets  — annotate ExternalSecret to pull from Secrets Manager; rollout restart auth + maintenance
#   all      — secrets then argo (typical after rotating secrets in AWS)

set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-autocare-eks}"
AWS_REGION="${AWS_REGION:-us-west-2}"
NAMESPACE="${NAMESPACE:-autocare}"
ARGO_APP="${ARGO_APP:-autocare}"

MODE="${1:-}"

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
}

[[ -n "$MODE" ]] || usage

refresh_argo() {
  echo "▶ Argo CD hard refresh: application/$ARGO_APP (namespace argocd)"
  kubectl patch application "$ARGO_APP" -n argocd --type merge -p \
    "{\"metadata\":{\"annotations\":{\"argocd.argoproj.io/refresh\":\"hard\"}}}"
  echo "  Waiting for controller to reconcile..."
  sleep 8
  kubectl get application "$ARGO_APP" -n argocd -o jsonpath='  sync={.status.sync.status} health={.status.health.status} revision={.status.sync.revision}{"\n"}' || true
  kubectl get application "$ARGO_APP" -n argocd -o wide 2>/dev/null || true
}

refresh_secrets() {
  echo "▶ External Secrets: force sync autocare-secrets"
  kubectl annotate externalsecret autocare-secrets -n "$NAMESPACE" \
    "external-secrets.io/force-sync=$(date +%s)" --overwrite
  echo "  Waiting for Secret to update..."
  sleep 6
  kubectl get externalsecret autocare-secrets -n "$NAMESPACE" -o wide 2>/dev/null || true

  echo "▶ Rollout restart (pods read env at start — required after secret change)"
  kubectl rollout restart deployment/user-auth-service \
    deployment/vehicle-maintenance-service -n "$NAMESPACE"
  kubectl rollout status deployment/user-auth-service -n "$NAMESPACE" --timeout=180s
  kubectl rollout status deployment/vehicle-maintenance-service -n "$NAMESPACE" --timeout=180s
  echo "  ✓ Backend rollouts complete"
}

case "$MODE" in
  argo)
    refresh_argo
    ;;
  secrets)
    refresh_secrets
    ;;
  all)
    refresh_secrets
    echo ""
    refresh_argo
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "Unknown mode: $MODE"
    usage
    ;;
esac

echo ""
echo "▶ Quick check: kubectl get pods -n $NAMESPACE"
