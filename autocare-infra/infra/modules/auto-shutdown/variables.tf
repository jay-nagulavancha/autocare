variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "node_group_name" {
  description = "EKS managed node group name"
  type        = string
}

variable "rds_identifier" {
  description = "RDS DB instance identifier"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "idle_minutes" {
  description = "Minutes of zero ALB traffic before triggering shutdown"
  type        = number
  default     = 30
}
