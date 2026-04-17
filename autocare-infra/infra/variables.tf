variable "aws_region" {
  description = "AWS region to deploy resources into"
  type        = string
  default     = "us-west-2"
}

variable "environment" {
  description = "Deployment environment (e.g. staging, production)"
  type        = string
  default     = "production"
}

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "autocare-eks"
}

variable "db_password" {
  description = "MySQL root password for RDS — stored in Secrets Manager, never committed to source control"
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "HS256 JWT signing key — stored in Secrets Manager, never committed to source control"
  type        = string
  sensitive   = true
}

variable "github_org" {
  description = "GitHub organisation name — used in the OIDC trust policy condition for the CI/CD IAM role"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name (without org prefix) — used in the OIDC trust policy condition for the CI/CD IAM role"
  type        = string
}

variable "enable_auto_shutdown" {
  description = "Enable Lambda-based auto-shutdown when no ALB traffic for idle_minutes. Recommended for dev/staging only."
  type        = bool
  default     = false
}

variable "auto_shutdown_idle_minutes" {
  description = "Minutes of zero ALB traffic before the auto-shutdown Lambda scales down the cluster"
  type        = number
  default     = 30
}
