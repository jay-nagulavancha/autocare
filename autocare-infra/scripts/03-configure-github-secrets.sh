#!/usr/bin/env bash
# 03-configure-github-secrets.sh
# Reads Terraform outputs and sets all required GitHub Actions secrets
# using the GitHub CLI (gh).
#
# Prerequisites:
#   brew install gh        (macOS)
#   gh auth login          (authenticate with GitHub)
#
# Usage:
#   export GITHUB_REPO=YOUR_ORG/YOUR_REPO   (e.g. acme/autocare-infra)
#   export AWS_REGION=us-west-2
#   export VITE_AUTH_API_URL=https://autocare.yourdomain.com/api/auth
#   export VITE_MAINTENANCE_API_URL=https://autocare.yourdomain.com/api/v1
#   ./autocare-infra/scripts/03-configure-github-secrets.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$SCRIPT_DIR/../infra"

# Validate required env vars
for VAR in GITHUB_REPO AWS_REGION VITE_AUTH_API_URL VITE_MAINTENANCE_API_URL; do
  if [[ -z "${!VAR:-}" ]]; then
    echo "✗ $VAR env var is required"
    echo "  export $VAR=..."
    exit 1
  fi
done

# Check gh CLI is installed
if ! command -v gh &>/dev/null; then
  echo "✗ GitHub CLI (gh) is not installed"
  echo "  Install: brew install gh"
  echo "  Auth:    gh auth login"
  exit 1
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Autocare — Configure GitHub Secrets"
echo "  Repo   : $GITHUB_REPO"
echo "  Region : $AWS_REGION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

cd "$INFRA_DIR"

# ── Read Terraform outputs ────────────────────────────────────────────────────
echo ""
echo "▶ Reading Terraform outputs..."

CICD_ROLE_ARN=$(terraform output -raw cicd_role_arn 2>/dev/null || echo "")
ECR_USER_AUTH=$(terraform output -json ecr_repository_urls 2>/dev/null | \
  python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('user_auth_service',''))" || echo "")

# Derive ECR registry from the first repo URL (strip the repo path)
if [[ -n "$ECR_USER_AUTH" ]]; then
  ECR_REGISTRY=$(echo "$ECR_USER_AUTH" | cut -d'/' -f1)
else
  ECR_REGISTRY=""
fi

if [[ -z "$CICD_ROLE_ARN" ]]; then
  echo "  ⚠ Could not read cicd_role_arn from Terraform outputs"
  read -rp "  Enter AWS_ROLE_ARN manually: " CICD_ROLE_ARN
fi

if [[ -z "$ECR_REGISTRY" ]]; then
  echo "  ⚠ Could not read ECR registry from Terraform outputs"
  read -rp "  Enter ECR_REGISTRY manually (e.g. 123456789.dkr.ecr.us-west-2.amazonaws.com): " ECR_REGISTRY
fi

echo "  CICD role ARN : $CICD_ROLE_ARN"
echo "  ECR registry  : $ECR_REGISTRY"

# ── Set GitHub secrets ────────────────────────────────────────────────────────
echo ""
echo "▶ Setting GitHub secrets on $GITHUB_REPO..."

set_secret() {
  local name="$1"
  local value="$2"
  echo -n "  Setting $name ... "
  echo "$value" | gh secret set "$name" --repo "$GITHUB_REPO"
  echo "✓"
}

set_secret "AWS_ROLE_ARN"              "$CICD_ROLE_ARN"
set_secret "AWS_REGION"                "$AWS_REGION"
set_secret "ECR_REGISTRY"              "$ECR_REGISTRY"
set_secret "VITE_AUTH_API_URL"         "$VITE_AUTH_API_URL"
set_secret "VITE_MAINTENANCE_API_URL"  "$VITE_MAINTENANCE_API_URL"

# ── Verify ────────────────────────────────────────────────────────────────────
echo ""
echo "▶ Verifying secrets are set..."
gh secret list --repo "$GITHUB_REPO"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✓ GitHub secrets configured"
echo ""
echo "  Next: commit and push to trigger the CI/CD pipeline"
echo "    git add ."
echo "    git commit -m 'feat: add EKS deployment infrastructure'"
echo "    git push origin main"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
