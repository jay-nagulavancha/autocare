# RDS module — MySQL 8.0 Multi-AZ instance, subnet group, security group
# Requirements: 4.1, 4.2, 4.3, 4.4, 4.6

# ---------------------------------------------------------------------------
# DB Subnet Group — private subnets only (Requirement 4.2)
# ---------------------------------------------------------------------------
resource "aws_db_subnet_group" "this" {
  name        = "${var.cluster_name}-rds-subnet-group"
  description = "Private subnet group for ${var.cluster_name} RDS instance"
  subnet_ids  = var.private_subnet_ids

  tags = {
    Name = "${var.cluster_name}-rds-subnet-group"
  }
}

# ---------------------------------------------------------------------------
# Security Group — allow TCP 3306 only from EKS node SG (Requirement 4.3)
# ---------------------------------------------------------------------------
resource "aws_security_group" "rds" {
  name        = "${var.cluster_name}-rds-sg"
  description = "Allow MySQL traffic from EKS nodes only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "MySQL from EKS nodes"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [var.eks_node_security_group_id]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.cluster_name}-rds-sg"
  }
}

# ---------------------------------------------------------------------------
# RDS MySQL 8.0 Multi-AZ instance (Requirements 4.1, 4.4, 4.6)
# ---------------------------------------------------------------------------
resource "aws_db_instance" "this" {
  identifier = "${var.cluster_name}-mysql"

  engine         = "mysql"
  engine_version = "8.0"
  instance_class = "db.t3.micro"

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = ""
  username = "root"
  password = var.db_password

  multi_az               = true
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = 7
  skip_final_snapshot     = false
  final_snapshot_identifier = "${var.cluster_name}-mysql-final-snapshot"

  publicly_accessible = false

  tags = {
    Name = "${var.cluster_name}-mysql"
  }
}
