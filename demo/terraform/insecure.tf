// DEMO_ONLY — Intentionally insecure Terraform used to demo Checkov.
// DO NOT apply. Safe to delete this file.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# DEMO_ONLY — Security group open to the world on SSH and DB ports
resource "aws_security_group" "demo_open_sg" {
  name        = "autocare-demo-open-sg"
  description = "DEMO_ONLY open security group"
  vpc_id      = "vpc-00000000"

  ingress {
    description = "DEMO_ONLY SSH from anywhere"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "DEMO_ONLY MySQL from anywhere"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# DEMO_ONLY — Public, unencrypted S3 bucket without versioning or logging
resource "aws_s3_bucket" "demo_public" {
  bucket = "autocare-demo-public-bucket-123456"
}

resource "aws_s3_bucket_acl" "demo_public_acl" {
  bucket = aws_s3_bucket.demo_public.id
  acl    = "public-read"
}

# DEMO_ONLY — Unencrypted RDS, publicly accessible, weak password
resource "aws_db_instance" "demo_unencrypted" {
  identifier          = "autocare-demo-mysql"
  engine              = "mysql"
  engine_version      = "5.7"
  instance_class      = "db.t3.micro"
  allocated_storage   = 20
  username            = "admin"
  password            = "Password123!"
  storage_encrypted   = false
  publicly_accessible = true
  skip_final_snapshot = true
}

# DEMO_ONLY — IAM policy with full admin access
resource "aws_iam_policy" "demo_admin_policy" {
  name        = "autocare-demo-admin-policy"
  description = "DEMO_ONLY policy granting full admin access"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "*"
        Resource = "*"
      }
    ]
  })
}
