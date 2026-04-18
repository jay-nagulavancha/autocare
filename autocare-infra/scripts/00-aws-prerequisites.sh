#!/usr/bin/env bash
# 00-aws-prerequisites.sh
# One-time AWS account setup BEFORE running terraform apply.
# Run this once per AWS account — not per environment.
#
# Usage:
#   export AWS_REGION=us-west-2
#   export TF_STATE_BUCKET=autocare-terraform-state-<your-account-id>
#   ./autocare-infra/scripts/00-aws-prerequisites.sh

set -euo pipefail

AWS_REGION="${AWS_REGION:-us-west-2}"
TF_STATE_BUCKET="${TF_STATE_BUCKET:-autocare-terraform-state}"
TF_LOCK_TABLE="${TF_LOCK_TABLE:-autocare-terraform-locks}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Autocare — AWS Prerequisites Setup"
echo "  Region      : $AWS_REGION"
echo "  State bucket: $TF_STATE_BUCKET"
echo "  Lock table  : $TF_LOCK_TABLE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo ""
echo "  AWS Account : $ACCOUNT_ID"
echo ""

# ── 1. S3 bucket for Terraform state ─────────────────────────────────────────
echo "▶ Step 1/3 — Terraform state S3 bucket"

if aws s3api head-bucket --bucket "$TF_STATE_BUCKET" 2>/dev/null; then
  echo "  ✓ Bucket $TF_STATE_BUCKET already exists — skipping"
else
  # us-east-1 is the only region that must NOT include LocationConstraint.
  # Every other region requires it — including us-west-2.
  if [[ "$AWS_REGION" == "us-east-1" ]]; then
    aws s3api create-bucket \
      --bucket "$TF_STATE_BUCKET" \
      --region "$AWS_REGION"
  else
    aws s3api create-bucket \
      --bucket "$TF_STATE_BUCKET" \
      --region "$AWS_REGION" \
      --create-bucket-configuration LocationConstraint="$AWS_REGION"
  fi

  # Enable versioning so you can recover from bad applies
  aws s3api put-bucket-versioning \
    --bucket "$TF_STATE_BUCKET" \
    --versioning-configuration Status=Enabled

  # Block all public access
  aws s3api put-public-access-block \
    --bucket "$TF_STATE_BUCKET" \
    --public-access-block-configuration \
      BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

  # Enable server-side encryption
  aws s3api put-bucket-encryption \
    --bucket "$TF_STATE_BUCKET" \
    --server-side-encryption-configuration '{
      "Rules": [{
        "ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}
      }]
    }'

  echo "  ✓ Created bucket $TF_STATE_BUCKET (versioned, encrypted, private)"
fi

# ── 2. DynamoDB table for state locking ──────────────────────────────────────
echo ""
echo "▶ Step 2/3 — DynamoDB state lock table"

if aws dynamodb describe-table --table-name "$TF_LOCK_TABLE" --region "$AWS_REGION" 2>/dev/null; then
  echo "  ✓ Table $TF_LOCK_TABLE already exists — skipping"
else
  aws dynamodb create-table \
    --table-name "$TF_LOCK_TABLE" \
    --attribute-definitions AttributeName=LockID,AttributeType=S \
    --key-schema AttributeName=LockID,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region "$AWS_REGION"

  echo "  Waiting for table to become active..."
  aws dynamodb wait table-exists \
    --table-name "$TF_LOCK_TABLE" \
    --region "$AWS_REGION"

  echo "  ✓ Created DynamoDB table $TF_LOCK_TABLE"
fi

# ── 3. GitHub Actions OIDC provider ──────────────────────────────────────────
echo ""
echo "▶ Step 3/3 — GitHub Actions OIDC provider"

GITHUB_OIDC_URL="https://token.actions.githubusercontent.com"
EXISTING=$(aws iam list-open-id-connect-providers \
  --query "OpenIDConnectProviderList[?ends_with(Arn, 'token.actions.githubusercontent.com')].Arn" \
  --output text)

if [[ -n "$EXISTING" ]]; then
  echo "  ✓ GitHub OIDC provider already registered — skipping"
  echo "  ARN: $EXISTING"
else
  # Fetch the thumbprint of the top intermediate CA cert in the chain
  # (AWS requires the thumbprint of the last cert before the root)
  THUMBPRINT=$(openssl s_client \
    -servername token.actions.githubusercontent.com \
    -connect token.actions.githubusercontent.com:443 \
    -showcerts 2>/dev/null \
    </dev/null \
    | awk '/-----BEGIN CERTIFICATE-----/{c++} c==2{print}' \
    | openssl x509 -fingerprint -noout -sha1 2>/dev/null \
    | sed 's/.*Fingerprint=//' \
    | tr -d ':' \
    | tr '[:upper:]' '[:lower:]')

  # Fallback: use the well-known static thumbprint for token.actions.githubusercontent.com
  if [[ ${#THUMBPRINT} -ne 40 ]]; then
    echo "  ⚠ Dynamic thumbprint fetch failed — using known static thumbprint"
    THUMBPRINT="6938fd4d98bab03faadb97b34396831e3780aea1"
  fi

  echo "  Thumbprint: $THUMBPRINT"

  PROVIDER_ARN=$(aws iam create-open-id-connect-provider \
    --url "$GITHUB_OIDC_URL" \
    --client-id-list sts.amazonaws.com \
    --thumbprint-list "$THUMBPRINT" \
    --query OpenIDConnectProviderArn \
    --output text)

  echo "  ✓ Created GitHub OIDC provider"
  echo "  ARN: $PROVIDER_ARN"
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✓ All prerequisites complete"
echo ""
echo "  Default Terraform backend is local (terraform.tfstate in infra/). OK to run:"
echo "    ./autocare-infra/scripts/01-terraform-apply.sh"
echo ""
echo "  Optional — remote state: replace backend \"local\" in infra/main.tf with:"
echo "    backend \"s3\" {"
echo "      bucket         = \"$TF_STATE_BUCKET\""
echo "      key            = \"autocare/terraform.tfstate\""
echo "      region         = \"$AWS_REGION\""
echo "      dynamodb_table = \"$TF_LOCK_TABLE\""
echo "      encrypt        = true"
echo "    }"
echo "  then: cd autocare-infra/infra && terraform init -migrate-state"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
