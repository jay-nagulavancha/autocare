variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs Fargate pods launch into (must route via NAT, not a direct IGW)"
  type        = list(string)
}
