terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Remote state (backend block cannot use variables — region must match the bucket's region).
  backend "s3" {
    bucket  = "autocare-terraform-state-123456789"
    key     = "autocare-terraform-state-123456789.state"
    region  = "us-west-2"
    encrypt = true
    # Optional: uncomment if you created a DynamoDB table for state locking
    # dynamodb_table = "autocare-terraform-locks"
    # Terraform 1.10+: optional S3-native locking (omit on older Terraform)
    # use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "autocare"
      ManagedBy   = "terraform"
      Environment = var.environment
    }
  }
}

# ---------------------------------------------------------------------------
# VPC
# ---------------------------------------------------------------------------

module "vpc" {
  source       = "./modules/vpc"
  region       = var.aws_region
  cluster_name = var.cluster_name
}

# ---------------------------------------------------------------------------
# ECR
# ---------------------------------------------------------------------------

module "ecr" {
  source = "./modules/ecr"
}

# ---------------------------------------------------------------------------
# EKS
# ---------------------------------------------------------------------------

module "eks" {
  source = "./modules/eks"

  cluster_name       = var.cluster_name
  private_subnet_ids = module.vpc.private_subnet_ids

  node_group_subnet_ids = var.eks_single_az_nodes ? [module.vpc.private_subnet_ids[0]] : module.vpc.private_subnet_ids
  node_instance_types   = var.eks_node_instance_types
  node_desired_size     = var.eks_node_desired_size
  node_min_size         = var.eks_node_min_size
  node_max_size         = var.eks_node_max_size
}

# ---------------------------------------------------------------------------
# RDS
# ---------------------------------------------------------------------------

# The EKS module does not expose the node group security group ID directly.
# We look it up via the aws_eks_cluster data source after the cluster is created.
data "aws_eks_cluster" "this" {
  name = module.eks.cluster_name

  depends_on = [module.eks]
}

module "rds" {
  source = "./modules/rds"

  cluster_name               = var.cluster_name
  vpc_id                     = module.vpc.vpc_id
  private_subnet_ids         = module.vpc.private_subnet_ids
  eks_node_security_group_id = data.aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
  db_password                = var.db_password
  multi_az                   = var.rds_multi_az
  backup_retention_days      = var.rds_backup_retention_days
}

# ---------------------------------------------------------------------------
# Secrets Manager
# ---------------------------------------------------------------------------

module "secrets" {
  source       = "./modules/secrets"
  jwt_secret   = var.jwt_secret
  db_password  = var.db_password
  rds_endpoint = module.rds.rds_endpoint
}

# ---------------------------------------------------------------------------
# IAM (IRSA + CI/CD + AWS Load Balancer Controller)
# ---------------------------------------------------------------------------

module "iam" {
  source            = "./modules/iam"
  cluster_name      = var.cluster_name
  oidc_provider_arn = module.eks.oidc_provider_arn
  # Strip the https:// prefix from the OIDC issuer URL for use as a condition key
  oidc_provider_url = replace(data.aws_eks_cluster.this.identity[0].oidc[0].issuer, "https://", "")
  secret_arns       = module.secrets.secret_arns
  github_org        = var.github_org
  github_repo       = var.github_repo
}

# ---------------------------------------------------------------------------
# CloudWatch
# ---------------------------------------------------------------------------

module "cloudwatch" {
  source = "./modules/cloudwatch"
}

# ---------------------------------------------------------------------------
# Auto-shutdown (dev/staging cost optimisation)
# Disabled by default — set enable_auto_shutdown = true in terraform.tfvars
# ---------------------------------------------------------------------------

module "auto_shutdown" {
  count  = var.enable_auto_shutdown ? 1 : 0
  source = "./modules/auto-shutdown"

  cluster_name    = var.cluster_name
  node_group_name = module.eks.node_group_name
  rds_identifier  = module.rds.rds_identifier
  aws_region      = var.aws_region
  idle_minutes    = var.auto_shutdown_idle_minutes
}
