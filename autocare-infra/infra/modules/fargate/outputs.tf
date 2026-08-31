output "fargate_profile_arn" {
  description = "ARN of the platform Fargate profile (CoreDNS + Karpenter)"
  value       = aws_eks_fargate_profile.platform.arn
}
