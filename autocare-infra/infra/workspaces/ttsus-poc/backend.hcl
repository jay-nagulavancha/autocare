# Partial backend config for the ttsus-poc workspace, passed via:
#   terraform init -backend-config=workspaces/ttsus-poc/backend.hcl
# Must contain bare attribute assignments only (no terraform/backend block wrapper) —
# that's the format -backend-config expects for a file-based partial backend config.
# Bucket lives in the POC account (673725943782) — versioned + encrypted + public access blocked.
bucket  = "otasdp-poc-tfstate-673725943782"
key     = "ttsus-poc/autocare-infra/terraform.tfstate"
region  = "us-west-2"
profile = "poc"
