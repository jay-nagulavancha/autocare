output "certificate_arn" {
  description = "ARN of the issued ACM certificate"
  value       = aws_acm_certificate.this.arn
}
