# RDS module outputs
# Requirements: 4.3

output "rds_endpoint" {
  description = "Hostname of the RDS MySQL instance (without port)"
  value       = aws_db_instance.this.address
}

output "rds_security_group_id" {
  description = "ID of the RDS security group"
  value       = aws_security_group.rds.id
}

output "rds_identifier" {
  description = "RDS DB instance identifier"
  value       = aws_db_instance.this.identifier
}
