# Secrets module — AWS Secrets Manager secrets for JWT, DB password, RDS endpoint
# Implementation: task 6.1

# JWT Secret
resource "aws_secretsmanager_secret" "jwt_secret" {
  name                    = "autocare/jwt-secret"
  description             = "JWT signing secret for Autocare services"
  recovery_window_in_days = 7

  tags = {
    Name        = "autocare-jwt-secret"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = var.jwt_secret
}

# Database Password
resource "aws_secretsmanager_secret" "db_password" {
  name                    = "autocare/db-password"
  description             = "MySQL root password for Autocare RDS instance"
  recovery_window_in_days = 7

  tags = {
    Name        = "autocare-db-password"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = var.db_password
}

# RDS Endpoint
resource "aws_secretsmanager_secret" "rds_endpoint" {
  name                    = "autocare/rds-endpoint"
  description             = "RDS MySQL endpoint hostname for Autocare services"
  recovery_window_in_days = 7

  tags = {
    Name        = "autocare-rds-endpoint"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

resource "aws_secretsmanager_secret_version" "rds_endpoint" {
  secret_id     = aws_secretsmanager_secret.rds_endpoint.id
  secret_string = var.rds_endpoint
}
