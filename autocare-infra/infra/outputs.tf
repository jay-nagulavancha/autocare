output "eks_cluster_name" {
  description = "Name of the EKS cluster"
  value       = module.eks.cluster_name
}

output "ecr_repository_urls" {
  description = "ECR repository URLs for all three Autocare services"
  value = {
    user_auth_service           = module.ecr.user_auth_service_repository_url
    vehicle_maintenance_service = module.ecr.vehicle_maintenance_service_repository_url
    vehicle_maintenance_ui      = module.ecr.vehicle_maintenance_ui_repository_url
  }
}

output "rds_endpoint" {
  description = "RDS MySQL endpoint hostname"
  value       = module.rds.rds_endpoint
  sensitive   = true
}

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer — provisioned at runtime by the AWS Load Balancer Controller, not available as a Terraform output"
  # The ALB is created by the ALB Ingress Controller when the Ingress resource is applied to the cluster.
  # Its DNS name is not known at terraform apply time; retrieve it with:
  #   kubectl get ingress -n autocare autocare-ingress -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
  value = ""
}

output "cicd_role_arn" {
  description = "IAM role ARN for GitHub Actions CI/CD — set as AWS_ROLE_ARN GitHub secret"
  value       = module.iam.cicd_role_arn
}

output "irsa_role_arn" {
  description = "IAM role ARN for the autocare-sa Kubernetes service account (IRSA)"
  value       = module.iam.irsa_role_arn
}
