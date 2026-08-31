variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "cluster_endpoint" {
  description = "EKS cluster API server endpoint"
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the EKS cluster's OIDC provider"
  type        = string
}

variable "oidc_provider_url" {
  description = "EKS cluster OIDC issuer URL, without the https:// prefix"
  type        = string
}

variable "node_role_name" {
  description = "Name of the existing node IAM role (reused for Karpenter-launched nodes' instance profile)"
  type        = string
}
