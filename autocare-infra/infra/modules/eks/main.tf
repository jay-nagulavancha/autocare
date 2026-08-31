# EKS module — cluster, OIDC provider, managed node group
# Implementation: task 4

# ---------------------------------------------------------------------------
# Cluster IAM role
# ---------------------------------------------------------------------------

resource "aws_iam_role" "cluster" {
  name = "${var.cluster_name}-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "eks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

# ---------------------------------------------------------------------------
# EKS Cluster
# ---------------------------------------------------------------------------

resource "aws_eks_cluster" "this" {
  name     = var.cluster_name
  version  = "1.35"
  role_arn = aws_iam_role.cluster.arn

  # Opt out of EKS Extended Support so AWS will not silently keep us on a
  # version past its standard-support window (extended support is billed at
  # ~$0.60/hour per cluster on top of the regular control-plane fee).
  upgrade_policy {
    support_type = "STANDARD"
  }

  vpc_config {
    subnet_ids              = var.private_subnet_ids
    endpoint_public_access  = true
    endpoint_private_access = true
  }

  # authenticationMode was CONFIG_MAP-only, so the cluster-creator admin
  # access entry EKS creates by default (bootstrap_cluster_creator_admin_permissions)
  # was silently ignored — no aws-auth ConfigMap exists in this repo either,
  # leaving nobody authorized against the Kubernetes API (console Resources
  # tab / kubectl both returned Unauthorized). API_AND_CONFIG_MAP makes the
  # API server honor access entries so that grant takes effect.
  access_config {
    authentication_mode                         = "API_AND_CONFIG_MAP"
    bootstrap_cluster_creator_admin_permissions = true
  }

  depends_on = [
    aws_iam_role_policy_attachment.cluster_policy,
  ]
}

# EKS creates this security group internally (not a resource this module
# declares), so it's tagged via aws_ec2_tag rather than a `tags` block.
# Karpenter's EC2NodeClass discovers it via this tag for its launched nodes.
resource "aws_ec2_tag" "cluster_security_group_karpenter_discovery" {
  resource_id = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
  key         = "karpenter.sh/discovery"
  value       = var.cluster_name
}

# ---------------------------------------------------------------------------
# Cluster admin access entries
# ---------------------------------------------------------------------------

# bootstrap_cluster_creator_admin_permissions only provisions an access entry
# during the initial CreateCluster call. This cluster was created under
# authentication_mode = CONFIG_MAP, so that grant never happened; switching
# to API_AND_CONFIG_MAP later doesn't retroactively create it. Admins must be
# granted explicitly here instead.
resource "aws_eks_access_entry" "admin" {
  for_each = toset(var.admin_principal_arns)

  cluster_name  = aws_eks_cluster.this.name
  principal_arn = each.value
}

resource "aws_eks_access_policy_association" "admin" {
  for_each = toset(var.admin_principal_arns)

  cluster_name  = aws_eks_cluster.this.name
  principal_arn = each.value
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.admin]
}

# ---------------------------------------------------------------------------
# OIDC provider (required for IRSA)
# ---------------------------------------------------------------------------

data "tls_certificate" "eks_oidc" {
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "eks" {
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks_oidc.certificates[0].sha1_fingerprint]
}

# ---------------------------------------------------------------------------
# Node group IAM role
# ---------------------------------------------------------------------------

resource "aws_iam_role" "node" {
  name = "${var.cluster_name}-node-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "node_worker_policy" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "node_cni_policy" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "node_ecr_readonly" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# ---------------------------------------------------------------------------
# Managed node group
# ---------------------------------------------------------------------------

# EKS managed node groups don't propagate provider default_tags to the
# underlying EC2 instances/EBS volumes. A custom launch template would fix
# this, but this account's otasdp-tag-controls/otasdp-security-controls
# guardrail policies reject ec2:RunInstances and autoscaling:UpdateAutoScalingGroup
# for any caller-supplied (non-AWS-managed) launch template — confirmed via a
# failed CreateNodegroup and a failed UpdateAutoScalingGroup attempt, both
# denied with "not authorized to use this launch template". Org tags on node
# instances/volumes are applied out-of-band via `aws ec2 create-tags` and are
# NOT Terraform-managed; they must be reapplied if the node group is replaced.
resource "aws_eks_node_group" "this" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${var.cluster_name}-node-group"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = var.node_group_subnet_ids

  instance_types = var.node_instance_types
  ami_type       = "AL2023_x86_64_STANDARD"

  scaling_config {
    desired_size = var.node_desired_size
    min_size     = var.node_min_size
    max_size     = var.node_max_size
  }

  update_config {
    max_unavailable = 1
  }

  # Let AWS resolve the latest supported AMI for this cluster version
  # Remove release_version to avoid AMI availability issues per region
  lifecycle {
    ignore_changes = [release_version]
  }

  depends_on = [
    aws_iam_role_policy_attachment.node_worker_policy,
    aws_iam_role_policy_attachment.node_cni_policy,
    aws_iam_role_policy_attachment.node_ecr_readonly,
  ]
}
