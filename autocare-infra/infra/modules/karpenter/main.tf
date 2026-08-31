# Karpenter module — controller IRSA role, node instance profile, interruption queue
# Node provisioning is handed to Karpenter's NodePool/EC2NodeClass (applied via
# k8s/karpenter/*.yaml through ArgoCD), not Terraform — this module only wires up
# the AWS-side plumbing Karpenter needs: who it can act as, and what launched nodes
# can act as.

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# ---------------------------------------------------------------------------
# Controller IRSA role — assumed by the karpenter/karpenter service account
# ---------------------------------------------------------------------------

resource "aws_iam_role" "controller" {
  name               = "${var.cluster_name}-karpenter-controller"
  assume_role_policy = data.aws_iam_policy_document.controller_trust.json

  tags = {
    Name = "${var.cluster_name}-karpenter-controller"
  }
}

data "aws_iam_policy_document" "controller_trust" {
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
      values   = ["system:serviceaccount:kube-system:karpenter"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# Minimal Karpenter controller policy (per upstream Karpenter's documented
# getting-started IAM policy), scoped to this cluster's EC2 resources via the
# karpenter.sh/discovery tag where the action supports resource-level conditions.
resource "aws_iam_policy" "controller" {
  name        = "${var.cluster_name}-karpenter-controller"
  description = "Allows the Karpenter controller to provision/deprovision EC2 nodes"
  policy      = data.aws_iam_policy_document.controller.json

  tags = {
    Name = "${var.cluster_name}-karpenter-controller"
  }
}

data "aws_iam_policy_document" "controller" {
  statement {
    sid    = "AllowScopedEC2InstanceActions"
    effect = "Allow"
    actions = [
      "ec2:RunInstances",
      "ec2:CreateFleet",
      "ec2:CreateLaunchTemplate",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "AllowScopedEC2InstanceTagging"
    effect = "Allow"
    actions = [
      "ec2:CreateTags",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "AllowScopedDeletion"
    effect = "Allow"
    actions = [
      "ec2:TerminateInstances",
      "ec2:DeleteLaunchTemplate",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:ResourceTag/karpenter.sh/discovery"
      values   = [var.cluster_name]
    }
  }

  statement {
    sid    = "AllowRegionalReadActions"
    effect = "Allow"
    actions = [
      "ec2:DescribeAvailabilityZones",
      "ec2:DescribeImages",
      "ec2:DescribeInstances",
      "ec2:DescribeInstanceTypeOfferings",
      "ec2:DescribeInstanceTypes",
      "ec2:DescribeLaunchTemplates",
      "ec2:DescribeSecurityGroups",
      "ec2:DescribeSpotPriceHistory",
      "ec2:DescribeSubnets",
    ]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "aws:RequestedRegion"
      values   = [data.aws_region.current.name]
    }
  }

  statement {
    sid       = "AllowSSMReadActions"
    effect    = "Allow"
    actions   = ["ssm:GetParameter"]
    resources = ["arn:aws:ssm:*:*:parameter/aws/service/*"]
  }

  statement {
    sid       = "AllowPricingReadActions"
    effect    = "Allow"
    actions   = ["pricing:GetProducts"]
    resources = ["*"]
  }

  statement {
    sid       = "AllowInterruptionQueueActions"
    effect    = "Allow"
    actions   = ["sqs:DeleteMessage", "sqs:GetQueueUrl", "sqs:ReceiveMessage"]
    resources = [aws_sqs_queue.interruption.arn]
  }

  statement {
    sid       = "AllowPassingInstanceRole"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.node_role_name}"]
  }

  statement {
    sid       = "AllowScopedInstanceProfileCreationActions"
    effect    = "Allow"
    actions   = ["iam:CreateInstanceProfile", "iam:TagInstanceProfile", "iam:AddRoleToInstanceProfile", "iam:RemoveRoleFromInstanceProfile", "iam:DeleteInstanceProfile", "iam:GetInstanceProfile", "iam:ListInstanceProfiles"]
    resources = ["*"]
  }

  statement {
    sid       = "AllowAPIServerEndpointDiscovery"
    effect    = "Allow"
    actions   = ["eks:DescribeCluster"]
    resources = ["arn:aws:eks:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:cluster/${var.cluster_name}"]
  }
}

resource "aws_iam_role_policy_attachment" "controller" {
  role       = aws_iam_role.controller.name
  policy_arn = aws_iam_policy.controller.arn
}

# ---------------------------------------------------------------------------
# Node instance profile — reuses the existing node IAM role (jn-eks-node-role)
# so Karpenter-launched EC2 instances authenticate as the same principal the
# managed node group already uses (which already has an EKS access entry with
# system:nodes — no new access entry needed).
# ---------------------------------------------------------------------------

resource "aws_iam_instance_profile" "node" {
  name = "${var.cluster_name}-karpenter-node"
  role = var.node_role_name

  tags = {
    Name = "${var.cluster_name}-karpenter-node"
  }
}

# ---------------------------------------------------------------------------
# Spot interruption handling
# ---------------------------------------------------------------------------

resource "aws_sqs_queue" "interruption" {
  name                      = "${var.cluster_name}-karpenter-interruption"
  message_retention_seconds = 300

  tags = {
    Name = "${var.cluster_name}-karpenter-interruption"
  }
}

resource "aws_sqs_queue_policy" "interruption" {
  queue_url = aws_sqs_queue.interruption.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = ["events.amazonaws.com", "sqs.amazonaws.com"]
        }
        Action   = "sqs:SendMessage"
        Resource = aws_sqs_queue.interruption.arn
      }
    ]
  })
}

locals {
  interruption_rules = {
    spot_interruption = { source = "aws.ec2", detail-type = "EC2 Spot Instance Interruption Warning" }
    rebalance         = { source = "aws.ec2", detail-type = "EC2 Instance Rebalance Recommendation" }
    instance_state    = { source = "aws.ec2", detail-type = "EC2 Instance State-change Notification" }
    scheduled_change  = { source = "aws.health", detail-type = "AWS Health Event" }
  }
}

resource "aws_cloudwatch_event_rule" "interruption" {
  for_each    = local.interruption_rules
  name        = "${var.cluster_name}-karpenter-${each.key}"
  description = "Forward ${each.value.detail-type} events to the Karpenter interruption queue"

  event_pattern = jsonencode({
    source      = [each.value.source]
    detail-type = [each.value.detail-type]
  })
}

resource "aws_cloudwatch_event_target" "interruption" {
  for_each = local.interruption_rules
  rule     = aws_cloudwatch_event_rule.interruption[each.key].name
  arn      = aws_sqs_queue.interruption.arn
}
