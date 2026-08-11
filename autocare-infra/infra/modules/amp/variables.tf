# AMP module input variables

variable "cluster_name" {
  description = "Name of the EKS cluster — used to namespace AMP/IAM resource names"
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the EKS cluster OIDC provider (used in IRSA trust policies)"
  type        = string
}

variable "oidc_provider_url" {
  description = "URL of the EKS cluster OIDC provider, without https:// prefix (used as condition key in trust policies)"
  type        = string
}
