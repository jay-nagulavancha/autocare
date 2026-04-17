# IAM module — IRSA roles, CI/CD pipeline role, policies
# Implementation: task 7

# Retrieve the current AWS account ID for use in the CI/CD trust policy principal ARN
data "aws_caller_identity" "current" {}

# ============================================================================
# IRSA Role for autocare/autocare-sa service account
# ============================================================================

# IAM role that the autocare-sa service account will assume via IRSA
resource "aws_iam_role" "autocare_irsa" {
  name               = "${var.cluster_name}-autocare-irsa"
  assume_role_policy = data.aws_iam_policy_document.autocare_irsa_trust.json

  tags = {
    Name        = "${var.cluster_name}-autocare-irsa"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

# Trust policy allowing the EKS OIDC provider to assume this role
# for the autocare/autocare-sa service account
data "aws_iam_policy_document" "autocare_irsa_trust" {
  statement {
    effect = "Allow"

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    actions = ["sts:AssumeRoleWithWebIdentity"]

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:sub"
      values   = ["system:serviceaccount:autocare:autocare-sa"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# IAM policy granting read access to only the three Autocare secrets
resource "aws_iam_policy" "autocare_secrets_access" {
  name        = "${var.cluster_name}-autocare-secrets-access"
  description = "Allows autocare service account to read Autocare secrets from Secrets Manager"
  policy      = data.aws_iam_policy_document.autocare_secrets_access.json

  tags = {
    Name        = "${var.cluster_name}-autocare-secrets-access"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

# Policy document restricting access to only the three secret ARNs
data "aws_iam_policy_document" "autocare_secrets_access" {
  statement {
    effect = "Allow"

    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]

    resources = var.secret_arns
  }
}

# Attach the secrets access policy to the IRSA role
resource "aws_iam_role_policy_attachment" "autocare_irsa_secrets" {
  role       = aws_iam_role.autocare_irsa.name
  policy_arn = aws_iam_policy.autocare_secrets_access.arn
}

# ============================================================================
# CI/CD Pipeline IAM Role — GitHub Actions OIDC
# ============================================================================

# Trust policy allowing GitHub Actions OIDC to assume this role
# Scoped to the configured org/repo via StringLike condition
data "aws_iam_policy_document" "cicd_trust" {
  statement {
    effect = "Allow"

    principals {
      type        = "Federated"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"]
    }

    actions = ["sts:AssumeRoleWithWebIdentity"]

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_org}/${var.github_repo}:*"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# IAM role assumed by GitHub Actions during CI/CD runs
resource "aws_iam_role" "cicd" {
  name               = "${var.cluster_name}-cicd-pipeline"
  assume_role_policy = data.aws_iam_policy_document.cicd_trust.json

  tags = {
    Name        = "${var.cluster_name}-cicd-pipeline"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

# Least-privilege policy for ECR push and EKS cluster inspection
data "aws_iam_policy_document" "cicd_permissions" {
  # ECR auth token — does not support resource-level restrictions
  statement {
    sid       = "ECRAuthToken"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # ECR image push permissions
  statement {
    sid    = "ECRPush"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
    ]
    resources = ["*"]
  }

  # EKS — needed only for cluster-start/stop manual workflow
  statement {
    sid    = "EKSClusterControl"
    effect = "Allow"
    actions = [
      "eks:DescribeCluster",
      "eks:ListNodegroups",
      "eks:DescribeNodegroup",
      "eks:UpdateNodegroupConfig",
    ]
    resources = ["*"]
  }

  # RDS — needed only for cluster-start/stop manual workflow
  statement {
    sid    = "RDSControl"
    effect = "Allow"
    actions = [
      "rds:DescribeDBInstances",
      "rds:StartDBInstance",
      "rds:StopDBInstance",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "cicd_permissions" {
  name        = "${var.cluster_name}-cicd-permissions"
  description = "Least-privilege permissions for the GitHub Actions CI/CD pipeline"
  policy      = data.aws_iam_policy_document.cicd_permissions.json

  tags = {
    Name        = "${var.cluster_name}-cicd-permissions"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

resource "aws_iam_role_policy_attachment" "cicd_permissions" {
  role       = aws_iam_role.cicd.name
  policy_arn = aws_iam_policy.cicd_permissions.arn
}

# ============================================================================
# IRSA role for AWS Load Balancer Controller (kube-system / aws-load-balancer-controller)
# ============================================================================
# Policy JSON from upstream (keep in sync with controller chart / AWS API additions):
# https://github.com/kubernetes-sigs/aws-load-balancer-controller/blob/v2.10.0/docs/install/iam_policy.json

data "aws_iam_policy_document" "lbc_trust" {
  statement {
    effect = "Allow"

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    actions = ["sts:AssumeRoleWithWebIdentity"]

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:sub"
      values   = ["system:serviceaccount:kube-system:aws-load-balancer-controller"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lbc" {
  name               = "${var.cluster_name}-aws-lbc-controller"
  assume_role_policy = data.aws_iam_policy_document.lbc_trust.json

  tags = {
    Name        = "${var.cluster_name}-aws-lbc-controller"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

resource "aws_iam_policy" "lbc" {
  name        = "${var.cluster_name}-aws-lbc"
  description = "AWS Load Balancer Controller — see lbc_iam_policy.json"
  policy      = file("${path.module}/lbc_iam_policy.json")

  tags = {
    Name        = "${var.cluster_name}-aws-lbc"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

resource "aws_iam_role_policy_attachment" "lbc" {
  role       = aws_iam_role.lbc.name
  policy_arn = aws_iam_policy.lbc.arn
}
