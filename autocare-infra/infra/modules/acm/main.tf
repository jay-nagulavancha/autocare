# ACM module — TLS certificate with DNS validation
#
# NOTE: DNS validation records must be created in Route53 (or your DNS provider)
# using the domain_validation_options output from aws_acm_certificate.this.
# This is out of scope for this module. The CI/CD or a separate Terraform
# configuration should create the required CNAME records before this module's
# aws_acm_certificate_validation resource can complete successfully.

resource "aws_acm_certificate" "this" {
  domain_name       = var.domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = var.domain_name
  }
}

resource "aws_acm_certificate_validation" "this" {
  certificate_arn         = aws_acm_certificate.this.arn
  validation_record_fqdns = [for dvo in aws_acm_certificate.this.domain_validation_options : dvo.resource_record_name]
}
