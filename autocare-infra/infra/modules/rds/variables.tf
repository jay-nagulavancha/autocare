variable "cluster_name" {
  description = "Name of the EKS cluster, used to prefix RDS resource names"
  type        = string
}

variable "vpc_id" {
  description = "ID of the VPC where RDS will be deployed"
  type        = string
}

variable "private_subnet_ids" {
  description = "List of private subnet IDs for the RDS subnet group"
  type        = list(string)
}

variable "eks_node_security_group_id" {
  description = "Security group ID of the EKS node group (allowed to reach RDS on port 3306)"
  type        = string
}

variable "db_password" {
  description = "Master password for the RDS MySQL instance (sourced from Secrets Manager at apply time)"
  type        = string
  sensitive   = true
}
