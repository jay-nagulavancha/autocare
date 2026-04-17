# IAM module input variables
# Implementation: task 7

variable "oidc_provider_arn" {
  description = "ARN of the EKS cluster OIDC provider (used in IRSA trust policy)"
  type        = string
}

variable "oidc_provider_url" {
  description = "URL of the EKS cluster OIDC provider, without https:// prefix (used as condition key in trust policy)"
  type        = string
}

variable "secret_arns" {
  description = "List of Secrets Manager secret ARNs the autocare service account is allowed to read"
  type        = list(string)
}

variable "cluster_name" {
  description = "Name of the EKS cluster — used to namespace IAM resource names"
  type        = string
}

variable "github_org" {
  description = "GitHub organisation name — used in the OIDC trust policy condition for the CI/CD role"
  type        = string
}

variable "github_repo" {
  description = "GitHub repository name (without org prefix) — used in the OIDC trust policy condition for the CI/CD role"
  type        = string
}
