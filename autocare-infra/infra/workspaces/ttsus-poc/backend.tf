# Backend configuration for ttsus-poc workspace
# Terraform will use workspace-specific state files

terraform {
  backend "s3" {
    # Workspace-specific state key
    key = "ttsus-poc/autocare-infra/terraform.tfstate"
  }
}
