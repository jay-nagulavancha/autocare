# VPC module input variables

variable "region" {
  description = "AWS region (used to derive availability zone names, e.g. us-west-2)"
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name — used for resource naming and Kubernetes subnet discovery tags"
  type        = string
}
