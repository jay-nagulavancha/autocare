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

# ---------------------------------------------------------------------------
# Cost / capacity (defaults match a small HA-style footprint; tighten for dev)
# ---------------------------------------------------------------------------

variable "eks_single_az_nodes" {
  description = "If true, the managed node group uses only the first private subnet (single-AZ workers). EKS control plane still uses two AZs (AWS requirement). Reduces cross-AZ traffic and can pair with a single NAT."
  type        = bool
  default     = false
}

variable "eks_node_instance_types" {
  description = "EKS managed node group instance types (e.g. t3.small for dev, t3.medium for more headroom)"
  type        = list(string)
  default     = ["t3.medium"]
}

variable "eks_node_desired_size" {
  description = "EKS node group desired size"
  type        = number
  default     = 2
}

variable "eks_node_min_size" {
  description = "EKS node group minimum size"
  type        = number
  default     = 2
}

variable "eks_node_max_size" {
  description = "EKS node group maximum size"
  type        = number
  default     = 4
}

variable "rds_multi_az" {
  description = "RDS Multi-AZ standby. Disabling saves roughly half of RDS instance hours for dev; use true for production-style HA."
  type        = bool
  default     = true
}

variable "rds_backup_retention_days" {
  description = "RDS automated backup retention (days). Lower values reduce backup storage cost."
  type        = number
  default     = 7
}
