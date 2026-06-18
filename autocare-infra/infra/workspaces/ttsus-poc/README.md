# ttsus-poc Workspace

Environment: POC (Proof of Concept)
Region: us-west-2
Naming Convention: otasdp-poc-{AWS service}-{resource name}

## Resources Deployed

| Resource Type | Resource Name (AWS) | Resource Name (Terraform) |
|--------------|---------------------|---------------------------|
| EKS | otasdp-poc-eks-ttsus | module.eks |
| RDS | otasdp-poc-rds-ttsus | module.rds |
| ECR | otasdp-poc-ecr-ttsus | module.ecr |
| VPC | otasdp-poc-vpc-ttsus | module.vpc |
| Secrets | otasdp-poc-secrets-ttsus | module.secrets |

## Usage

```bash
# Initialize with the workspace backend
terraform init -reconfigure

# Set workspace
terraform workspace select ttsus-poc

# Plan with workspace-specific variables
terraform plan -var-file=workspaces/ttsus-poc/terraform.tfvars

# Apply
terraform apply -var-file=workspaces/ttsus-poc/terraform.tfvars

# Destroy
terraform destroy -var-file=workspaces/ttsus-poc/terraform.tfvars
```

## Cost Optimization

- Auto-shutdown enabled (scales down after 30 minutes of idle)
- Single AZ node group (reduced cost)
- Smaller node instance types (t3.small)
- Reduced backup retention (3 days)
