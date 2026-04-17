locals {
  services = toset([
    "user-auth-service",
    "vehicle-maintenance-service",
    "vehicle-maintenance-ui",
  ])
}

resource "aws_cloudwatch_log_group" "autocare" {
  for_each = local.services

  name              = "/autocare/${each.key}"
  retention_in_days = 30

  tags = {
    Service = each.key
  }
}
