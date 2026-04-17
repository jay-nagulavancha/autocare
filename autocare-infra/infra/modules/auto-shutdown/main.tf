# auto-shutdown module
# Monitors ALB RequestCount via CloudWatch. If no requests in the last 30 minutes,
# scales the EKS node group to 0 and stops the RDS instance.
# An EventBridge rule fires the Lambda every 10 minutes.

locals {
  function_name = "${var.cluster_name}-auto-shutdown"
}

# ---------------------------------------------------------------------------
# Lambda execution role
# ---------------------------------------------------------------------------

resource "aws_iam_role" "lambda" {
  name = "${local.function_name}-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "lambda" {
  name = "${local.function_name}-policy"
  role = aws_iam_role.lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "Logs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Sid    = "CloudWatchMetrics"
        Effect = "Allow"
        Action = ["cloudwatch:GetMetricStatistics"]
        Resource = "*"
      },
      {
        Sid    = "EKSNodeGroup"
        Effect = "Allow"
        Action = [
          "eks:DescribeNodegroup",
          "eks:UpdateNodegroupConfig"
        ]
        Resource = "*"
      },
      {
        Sid    = "RDS"
        Effect = "Allow"
        Action = [
          "rds:DescribeDBInstances",
          "rds:StopDBInstance"
        ]
        Resource = "*"
      },
      {
        Sid    = "ELB"
        Effect = "Allow"
        Action = ["elasticloadbalancing:DescribeLoadBalancers"]
        Resource = "*"
      }
    ]
  })
}

# ---------------------------------------------------------------------------
# Lambda function (inline Python — no S3 bucket needed)
# ---------------------------------------------------------------------------

data "archive_file" "lambda" {
  type        = "zip"
  output_path = "${path.module}/auto_shutdown.zip"

  source {
    content  = <<-PYTHON
import boto3
import os
import json
from datetime import datetime, timezone, timedelta

CLUSTER_NAME    = os.environ["CLUSTER_NAME"]
NODE_GROUP_NAME = os.environ["NODE_GROUP_NAME"]
RDS_IDENTIFIER  = os.environ["RDS_IDENTIFIER"]
IDLE_MINUTES    = int(os.environ.get("IDLE_MINUTES", "30"))
AWS_REGION      = os.environ["AWS_REGION"]

eks = boto3.client("eks",        region_name=AWS_REGION)
rds = boto3.client("rds",        region_name=AWS_REGION)
cw  = boto3.client("cloudwatch", region_name=AWS_REGION)
elb = boto3.client("elbv2",      region_name=AWS_REGION)

def get_alb_request_count():
    """Return total ALB requests in the last IDLE_MINUTES minutes.
    Returns -1 if no ALB is found yet (cluster may be starting up)."""
    lbs = elb.describe_load_balancers()["LoadBalancers"]
    autocare_lbs = [
        lb for lb in lbs
        if "autocare" in lb.get("LoadBalancerName", "").lower()
        or any("autocare" in str(t) for t in lb.get("Tags", []))
    ]
    if not autocare_lbs:
        print("No Autocare ALB found — skipping idle check")
        return -1

    lb_arn_suffix = "/".join(autocare_lbs[0]["LoadBalancerArn"].split("/")[-3:])
    end   = datetime.now(timezone.utc)
    start = end - timedelta(minutes=IDLE_MINUTES)

    resp = cw.get_metric_statistics(
        Namespace  = "AWS/ApplicationELB",
        MetricName = "RequestCount",
        Dimensions = [{"Name": "LoadBalancer", "Value": lb_arn_suffix}],
        StartTime  = start,
        EndTime    = end,
        Period     = IDLE_MINUTES * 60,
        Statistics = ["Sum"],
    )
    datapoints = resp.get("Datapoints", [])
    total = sum(d["Sum"] for d in datapoints)
    print(f"ALB RequestCount over last {IDLE_MINUTES}min: {total}")
    return total

def get_node_group_desired():
    ng = eks.describe_nodegroup(clusterName=CLUSTER_NAME, nodegroupName=NODE_GROUP_NAME)
    return ng["nodegroup"]["scalingConfig"]["desiredSize"]

def get_rds_status():
    dbs = rds.describe_db_instances(DBInstanceIdentifier=RDS_IDENTIFIER)
    return dbs["DBInstances"][0]["DBInstanceStatus"]

def scale_down():
    print(f"Scaling EKS node group {NODE_GROUP_NAME} to 0")
    eks.update_nodegroup_config(
        clusterName   = CLUSTER_NAME,
        nodegroupName = NODE_GROUP_NAME,
        scalingConfig = {"minSize": 0, "maxSize": 4, "desiredSize": 0},
    )

def stop_rds():
    status = get_rds_status()
    if status == "available":
        print(f"Stopping RDS instance {RDS_IDENTIFIER}")
        rds.stop_db_instance(DBInstanceIdentifier=RDS_IDENTIFIER)
    else:
        print(f"RDS status is '{status}' — skipping stop")

def handler(event, context):
    print(f"Auto-shutdown check: cluster={CLUSTER_NAME}, rds={RDS_IDENTIFIER}")

    desired = get_node_group_desired()
    if desired == 0:
        print("Node group already at 0 — nothing to do")
        return {"status": "already_stopped"}

    request_count = get_alb_request_count()
    if request_count == -1:
        return {"status": "no_alb_found"}

    if request_count == 0:
        print(f"No traffic in last {IDLE_MINUTES}min — shutting down")
        scale_down()
        stop_rds()
        return {"status": "shutdown_triggered"}
    else:
        print(f"Traffic detected ({request_count} requests) — keeping cluster running")
        return {"status": "active", "request_count": request_count}
PYTHON
    filename = "auto_shutdown.py"
  }
}

resource "aws_lambda_function" "auto_shutdown" {
  function_name    = local.function_name
  role             = aws_iam_role.lambda.arn
  filename         = data.archive_file.lambda.output_path
  source_code_hash = data.archive_file.lambda.output_base64sha256
  handler          = "auto_shutdown.handler"
  runtime          = "python3.12"
  timeout          = 60

  environment {
    variables = {
      CLUSTER_NAME    = var.cluster_name
      NODE_GROUP_NAME = var.node_group_name
      RDS_IDENTIFIER  = var.rds_identifier
      IDLE_MINUTES    = tostring(var.idle_minutes)
      AWS_REGION      = var.aws_region
    }
  }

  depends_on = [aws_iam_role_policy.lambda]
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${local.function_name}"
  retention_in_days = 7
}

# ---------------------------------------------------------------------------
# EventBridge rule — fires every 10 minutes
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_event_rule" "auto_shutdown" {
  name                = "${local.function_name}-schedule"
  description         = "Trigger auto-shutdown check every 10 minutes"
  schedule_expression = "rate(10 minutes)"
  state               = "ENABLED"
}

resource "aws_cloudwatch_event_target" "auto_shutdown" {
  rule      = aws_cloudwatch_event_rule.auto_shutdown.name
  target_id = "AutoShutdownLambda"
  arn       = aws_lambda_function.auto_shutdown.arn
}

resource "aws_lambda_permission" "eventbridge" {
  statement_id  = "AllowEventBridgeInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.auto_shutdown.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.auto_shutdown.arn
}
