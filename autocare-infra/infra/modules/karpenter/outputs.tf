output "controller_role_arn" {
  description = "IAM role ARN for the karpenter/karpenter service account (IRSA)"
  value       = aws_iam_role.controller.arn
}

output "node_instance_profile_name" {
  description = "Instance profile name for Karpenter-launched nodes — used by EC2NodeClass.spec.instanceProfile"
  value       = aws_iam_instance_profile.node.name
}

output "interruption_queue_name" {
  description = "SQS queue name for spot interruption/instance-state events — used by the Karpenter Helm values"
  value       = aws_sqs_queue.interruption.name
}
