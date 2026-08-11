output "workspace_id" {
  description = "ID of the AMP workspace"
  value       = aws_prometheus_workspace.this.id
}

output "workspace_arn" {
  description = "ARN of the AMP workspace"
  value       = aws_prometheus_workspace.this.arn
}

output "remote_write_endpoint" {
  description = "Remote-write URL for the AMP workspace (Prometheus remoteWrite target)"
  value       = "${aws_prometheus_workspace.this.prometheus_endpoint}api/v1/remote_write"
}

output "query_endpoint" {
  description = "Query base URL for the AMP workspace (Grafana AMP datasource)"
  value       = aws_prometheus_workspace.this.prometheus_endpoint
}

output "ingest_role_arn" {
  description = "ARN of the IRSA role for the Prometheus agent (monitoring/amp-ingest) to remote-write into AMP"
  value       = aws_iam_role.amp_ingest.arn
}

output "query_role_arn" {
  description = "ARN of the IRSA role for Grafana (monitoring/grafana) to query AMP"
  value       = aws_iam_role.amp_query.arn
}
