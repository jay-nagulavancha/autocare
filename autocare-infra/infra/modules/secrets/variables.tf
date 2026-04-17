# Secrets module input variables
# Implementation: task 6.1

variable "jwt_secret" {
  description = "JWT HS256 signing key for Autocare services. Sensitive — do not commit to source control."
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "MySQL root password for the Autocare RDS instance. Sensitive — do not commit to source control."
  type        = string
  sensitive   = true
}

variable "rds_endpoint" {
  description = "RDS MySQL hostname (endpoint) for Autocare services."
  type        = string
}
