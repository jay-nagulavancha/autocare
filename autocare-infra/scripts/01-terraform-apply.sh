#!/usr/bin/env bash
# 01-terraform-apply.sh
# Initialise and apply Terraform to provision all AWS infrastructure.
#
# Usage:
#   export AWS_REGION=us-west-2
#   ./autocare-infra/scripts/01-terraform-apply.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../infra"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Autocare — Terraform Apply"
echo "  Directory: $INFRA_DIR"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Check terraform.tfvars exists
if [[ ! -f "$INFRA_DIR/terraform.tfvars" ]]; then
  echo ""
  echo "  ✗ terraform.tfvars not found at $INFRA_DIR/terraform.tfvars"
  echo "  Copy the example and fill in your values:"
  echo "    cp $INFRA_DIR/terraform.tfvars.example $INFRA_DIR/terraform.tfvars"
  exit 1
fi

cd "$INFRA_DIR"

# ── 1. Init ───────────────────────────────────────────────────────────────────
echo ""
echo "▶ Step 1/3 — terraform init"
terraform init -upgrade

# ── 2. Plan ───────────────────────────────────────────────────────────────────
echo ""
echo "▶ Step 2/3 — terraform plan"
terraform plan -out=tfplan

echo ""
read -rp "  Review the plan above. Apply? (yes/no): " CONFIRM
if [[ "$CONFIRM" != "yes" ]]; then
  echo "  Aborted."
  exit 0
fi

# ── 3. Apply ──────────────────────────────────────────────────────────────────
echo ""
echo "▶ Step 3/3 — terraform apply (~15-20 min)"
terraform apply tfplan
rm -f tfplan

# ── Capture outputs ───────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✓ Terraform apply complete"
echo ""
echo "  Key outputs:"
terraform output eks_cluster_name
terraform output ecr_repository_urls
echo ""
echo "  Sensitive outputs (copy these to GitHub secrets):"
echo "  cicd_role_arn:"
terraform output -raw cicd_role_arn 2>/dev/null || \
  echo "  (run: cd $INFRA_DIR && terraform output cicd_role_arn)"
echo ""
echo "  Next: ./autocare-infra/scripts/02-bootstrap-cluster.sh"
echo "  Full teardown: ./autocare-infra/scripts/06-terraform-destroy.sh"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
