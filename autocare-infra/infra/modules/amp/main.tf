# AMP module — Amazon Managed Service for Prometheus workspace + IRSA roles
# for the in-cluster Prometheus agent (remote-write/ingest) and Grafana (query).

resource "aws_prometheus_workspace" "this" {
  alias = "${var.cluster_name}-amp"

  tags = {
    Name = "${var.cluster_name}-amp"
  }
}

# ============================================================================
# IRSA role for the Prometheus agent (monitoring/prometheus service account)
# Remote-writes scraped samples into the AMP workspace.
# ============================================================================

data "aws_iam_policy_document" "amp_ingest_trust" {
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
      values   = ["system:serviceaccount:monitoring:amp-ingest"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "amp_ingest" {
  name               = "${var.cluster_name}-amp-ingest"
  assume_role_policy = data.aws_iam_policy_document.amp_ingest_trust.json

  tags = {
    Name = "${var.cluster_name}-amp-ingest"
  }
}

data "aws_iam_policy_document" "amp_ingest" {
  statement {
    effect = "Allow"

    actions = [
      "aps:RemoteWrite",
      "aps:GetSeries",
      "aps:GetLabels",
      "aps:GetMetricMetadata",
    ]

    resources = [aws_prometheus_workspace.this.arn]
  }
}

resource "aws_iam_policy" "amp_ingest" {
  name        = "${var.cluster_name}-amp-ingest"
  description = "Allows the in-cluster Prometheus agent to remote-write metrics into the AMP workspace"
  policy      = data.aws_iam_policy_document.amp_ingest.json

  tags = {
    Name = "${var.cluster_name}-amp-ingest"
  }
}

resource "aws_iam_role_policy_attachment" "amp_ingest" {
  role       = aws_iam_role.amp_ingest.name
  policy_arn = aws_iam_policy.amp_ingest.arn
}

# ============================================================================
# IRSA role for Grafana (monitoring/grafana service account)
# Queries the AMP workspace as a datasource via native SigV4 auth.
# ============================================================================

data "aws_iam_policy_document" "amp_query_trust" {
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
      values   = ["system:serviceaccount:monitoring:grafana"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "amp_query" {
  name               = "${var.cluster_name}-amp-query"
  assume_role_policy = data.aws_iam_policy_document.amp_query_trust.json

  tags = {
    Name = "${var.cluster_name}-amp-query"
  }
}

data "aws_iam_policy_document" "amp_query" {
  statement {
    effect = "Allow"

    actions = [
      "aps:QueryMetrics",
      "aps:GetSeries",
      "aps:GetLabels",
      "aps:GetMetricMetadata",
    ]

    resources = [aws_prometheus_workspace.this.arn]
  }
}

resource "aws_iam_policy" "amp_query" {
  name        = "${var.cluster_name}-amp-query"
  description = "Allows Grafana to query the AMP workspace as a datasource"
  policy      = data.aws_iam_policy_document.amp_query.json

  tags = {
    Name = "${var.cluster_name}-amp-query"
  }
}

resource "aws_iam_role_policy_attachment" "amp_query" {
  role       = aws_iam_role.amp_query.name
  policy_arn = aws_iam_policy.amp_query.arn
}
