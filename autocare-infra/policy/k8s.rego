package main

# Deny Deployments without resource limits
deny[msg] {
  input.kind == "Deployment"
  container := input.spec.template.spec.containers[_]
  not container.resources.limits
  msg := sprintf("Deployment '%s': container '%s' is missing resource limits", [input.metadata.name, container.name])
}

# Deny Deployments without resource requests
deny[msg] {
  input.kind == "Deployment"
  container := input.spec.template.spec.containers[_]
  not container.resources.requests
  msg := sprintf("Deployment '%s': container '%s' is missing resource requests", [input.metadata.name, container.name])
}

# Deny Deployments without liveness probe
deny[msg] {
  input.kind == "Deployment"
  container := input.spec.template.spec.containers[_]
  not container.livenessProbe
  msg := sprintf("Deployment '%s': container '%s' is missing a liveness probe", [input.metadata.name, container.name])
}

# Deny Deployments without readiness probe
deny[msg] {
  input.kind == "Deployment"
  container := input.spec.template.spec.containers[_]
  not container.readinessProbe
  msg := sprintf("Deployment '%s': container '%s' is missing a readiness probe", [input.metadata.name, container.name])
}

# Deny hostNetwork: true
deny[msg] {
  input.kind == "Deployment"
  input.spec.template.spec.hostNetwork == true
  msg := sprintf("Deployment '%s': hostNetwork must not be true", [input.metadata.name])
}

# Deny plaintext Secret data fields
deny[msg] {
  input.kind == "Secret"
  input.data
  msg := sprintf("Secret '%s': plaintext data fields are not allowed — use ExternalSecret instead", [input.metadata.name])
}
