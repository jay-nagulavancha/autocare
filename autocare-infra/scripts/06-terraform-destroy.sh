#!/usr/bin/env bash
# 06-terraform-destroy.sh — Tear down all Autocare AWS infrastructure managed by Terraform
#
# Use this when you want a clean slate to re-run apply + bootstrap for verification.
#
# Prerequisites:
#   - Same terraform.tfvars as apply (needed for destroy; sensitive vars still in state)
#   - AWS credentials with permission to delete EKS, RDS, VPC, IAM, etc.
#
# Optional pre-step (default ON): if kubectl can reach the EKS cluster, delete the
# autocare namespace first so Ingress-managed ALBs are removed cleanly before nodes go away.
#
# Usage:
#   cd autocare-infra/infra && terraform init   # if needed
#   cd ..   # back to autocare-infra
#   export CLUSTER_NAME=autocare-eks
#   export AWS_REGION=us-west-2
#   ./scripts/06-terraform-destroy.sh
#   ./scripts/06-terraform-destroy.sh --skip-kubectl   # skip namespace delete (no kube access)
#
# After destroy — recreate:
#   ./scripts/01-terraform-apply.sh
#   export DB_PASSWORD=... JWT_SECRET=...
#   ./scripts/02-bootstrap-cluster.sh
#
# Secrets Manager: secrets use recovery_window_in_days = 7. If terraform apply fails
# after destroy because names are still pending deletion, either wait or force-delete:
#   aws secretsmanager delete-secret --secret-id autocare/jwt-secret \
#     --force-delete-without-recovery --region "$AWS_REGION"
#   (repeat for autocare/db-password and autocare/rds-endpoint if needed)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../infra"

CLUSTER_NAME="${CLUSTER_NAME:-autocare-eks}"
AWS_REGION="${AWS_REGION:-us-west-2}"
SKIP_KUBECTL=false
for arg in "$@"; do
  [[ "$arg" == "--skip-kubectl" ]] && SKIP_KUBECTL=true
done

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Autocare — Terraform DESTROY (irreversible in practice)"
echo "  Region       : $AWS_REGION"
echo "  Cluster name : $CLUSTER_NAME"
echo "  Terraform dir: $INFRA_DIR"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  This deletes EKS, RDS, VPC, IAM roles used by the stack, Secrets Manager"
echo "  secrets (scheduled deletion), ECR repos if in state, etc."
echo ""

if [[ ! -f "$INFRA_DIR/terraform.tfvars" ]]; then
  echo "  ✗ Missing $INFRA_DIR/terraform.tfvars (needed so Terraform can load destroy context)"
  exit 1
fi

read -rp "  Type DESTROY (all caps) to continue: " CONFIRM
if [[ "$CONFIRM" != "DESTROY" ]]; then
  echo "  Aborted."
  exit 0
fi

# ── Optional: remove workloads so ALB controller can delete load balancers ─────
if [[ "$SKIP_KUBECTL" != true ]]; then
  echo ""
  echo "▶ Optional Kubernetes cleanup (Ingress → ALB)"
  if aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$AWS_REGION" --alias "$CLUSTER_NAME" &>/dev/null; then
    if kubectl get ns autocare &>/dev/null; then
      echo "  Deleting namespace autocare (Ingress, pods, Services) — may take 2–5 min..."
      kubectl delete namespace autocare --wait=true --timeout=600s || true
      echo "  ✓ Namespace autocare removed or deletion started"
    else
      echo "  Namespace autocare not found — skipping"
    fi
  else
    echo "  Could not update kubeconfig (cluster may already be gone) — skipping kubectl cleanup"
  fi
else
  echo ""
  echo "▶ Skipping kubectl cleanup (--skip-kubectl)"
fi

# ── Terraform destroy ─────────────────────────────────────────────────────────
cd "$INFRA_DIR"

echo ""
echo "▶ terraform plan -destroy"
terraform plan -destroy -out=tfdestroy.plan

echo ""
read -rp "  Review the destroy plan above. Run terraform apply? (yes/no): " APPLY
if [[ "$APPLY" != "yes" ]]; then
  echo "  Aborted (plan file left as tfdestroy.plan — rm when done)."
  exit 0
fi

echo ""
echo "▶ terraform apply destroy plan (~15–25 min)"
terraform apply tfdestroy.plan
rm -f tfdestroy.plan

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✓ Terraform destroy finished"
echo ""
echo "  Recreate from scratch:"
echo "    ./autocare-infra/scripts/01-terraform-apply.sh"
echo "    export DB_PASSWORD=... JWT_SECRET=..."
echo "    ./autocare-infra/scripts/02-bootstrap-cluster.sh"
echo ""
echo "  If the next apply errors on Secrets Manager names still in recovery, see header"
echo "  comments in this script (force-delete-without-recovery)."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
