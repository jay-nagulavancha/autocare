output "irsa_role_arn" {
  description = "ARN of the IRSA IAM role for the autocare-sa service account"
  value       = aws_iam_role.autocare_irsa.arn
}

output "cicd_role_arn" {
  description = "ARN of the IAM role assumed by the GitHub Actions CI/CD pipeline"
  value       = aws_iam_role.cicd.arn
}
