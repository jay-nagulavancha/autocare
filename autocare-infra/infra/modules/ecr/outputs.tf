output "user_auth_service_repository_url" {
  description = "ECR repository URL for the user-auth-service"
  value       = aws_ecr_repository.this["autocare/user-auth-service"].repository_url
}

output "vehicle_maintenance_service_repository_url" {
  description = "ECR repository URL for the vehicle-maintenance-service"
  value       = aws_ecr_repository.this["autocare/vehicle-maintenance-service"].repository_url
}

output "vehicle_maintenance_ui_repository_url" {
  description = "ECR repository URL for the vehicle-maintenance-ui"
  value       = aws_ecr_repository.this["autocare/vehicle-maintenance-ui"].repository_url
}

output "repository_urls" {
  description = "Map of all ECR repository URLs keyed by repository name"
  value = {
    for name, repo in aws_ecr_repository.this : name => repo.repository_url
  }
}
