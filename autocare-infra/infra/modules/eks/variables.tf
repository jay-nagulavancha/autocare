# EKS module input variables

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for the EKS control plane (must span at least two AZs per AWS requirement)"
  type        = list(string)
}

variable "node_group_subnet_ids" {
  description = "Subnets for the managed node group. Use the first subnet only for single-AZ workers (lower cross-AZ traffic; dev cost profile)."
  type        = list(string)
}

variable "node_instance_types" {
  description = "EC2 instance types for the managed node group"
  type        = list(string)
}

variable "node_desired_size" {
  description = "Desired worker node count"
  type        = number
}

variable "node_min_size" {
  description = "Minimum worker nodes"
  type        = number
}

variable "node_max_size" {
  description = "Maximum worker nodes"
  type        = number
}
