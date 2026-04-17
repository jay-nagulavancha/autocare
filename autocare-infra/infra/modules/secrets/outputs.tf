# Secrets module outputs — expose secret ARNs for use in IAM policy documents
# Implementation: task 6.2

output "jwt_secret_arn" {
  description = "ARN of the JWT signing secret in Secrets Manager"
  value       = aws_secretsmanager_secret.jwt_secret.arn
}

output "db_password_secret_arn" {
  description = "ARN of the database password secret in Secrets Manager"
  value       = aws_secretsmanager_secret.db_password.arn
}

output "rds_endpoint_secret_arn" {
  description = "ARN of the RDS endpoint secret in Secrets Manager"
  value       = aws_secretsmanager_secret.rds_endpoint.arn
}

output "secret_arns" {
  description = "List of all Autocare secret ARNs — convenience output for IAM policy documents"
  value = [
    aws_secretsmanager_secret.jwt_secret.arn,
    aws_secretsmanager_secret.db_password.arn,
    aws_secretsmanager_secret.rds_endpoint.arn,
  ]
}
