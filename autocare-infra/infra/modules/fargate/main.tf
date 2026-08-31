# Fargate module — hosts CoreDNS and the Karpenter controller on Fargate so
# they don't depend on any EC2 node group existing. This breaks the
# chicken-and-egg problem Karpenter otherwise has (its controller is a pod
# that needs somewhere to run before it can provision the node it would
# schedule pods onto) without needing a static EC2 node-group floor.
#
# Selectors are scoped by label, not bare namespace — kube-system also hosts
# aws-node (VPC CNI) and kube-proxy as DaemonSets, which cannot run on
# Fargate at all. A namespace-only selector would try to schedule those there
# too and break cluster networking.

resource "aws_iam_role" "fargate_pod_execution" {
  name = "${var.cluster_name}-fargate-pod-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks-fargate-pods.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name = "${var.cluster_name}-fargate-pod-execution"
  }
}

resource "aws_iam_role_policy_attachment" "fargate_pod_execution" {
  role       = aws_iam_role.fargate_pod_execution.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSFargatePodExecutionRolePolicy"
}

resource "aws_eks_fargate_profile" "platform" {
  cluster_name           = var.cluster_name
  fargate_profile_name   = "${var.cluster_name}-platform"
  pod_execution_role_arn = aws_iam_role.fargate_pod_execution.arn
  subnet_ids             = var.private_subnet_ids

  selector {
    namespace = "kube-system"
    labels = {
      "k8s-app" = "kube-dns"
    }
  }

  selector {
    namespace = "kube-system"
    labels = {
      "app.kubernetes.io/name" = "karpenter"
    }
  }

  tags = {
    Name = "${var.cluster_name}-platform"
  }
}
