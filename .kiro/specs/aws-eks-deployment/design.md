# Design Document — AWS EKS Deployment (Autocare)

## Overview

This document describes the technical design for deploying the Autocare Vehicle Maintenance System to AWS Elastic Kubernetes Service (EKS). The deployment replaces the local Docker Compose setup with a production-grade AWS infrastructure stack managed entirely by Terraform, with Kubernetes manifests handling the workload layer.

The system comprises three application services (`user-auth-service`, `vehicle-maintenance-service`, `vehicle-maintenance-ui`), a managed MySQL database on Amazon RDS, private image storage on ECR, HTTPS ingress via an AWS Application Load Balancer, secret injection via AWS Secrets Manager, and automated delivery via a GitHub Actions CI pipeline with ArgoCD handling GitOps-based deployment.

### Key Design Decisions

- **Terraform-only infrastructure**: All AWS resources (VPC, EKS, RDS, ECR, IAM, ACM, CloudWatch, Secrets Manager) are declared in Terraform. No manual console provisioning.
- **Kubernetes manifests for workloads**: Deployments, Services, Ingress, HPA, NetworkPolicies, ConfigMaps, and Secrets are maintained as YAML files in the `k8s/` directory and synced to the cluster by ArgoCD.
- **GitOps delivery via ArgoCD**: ArgoCD runs inside the EKS cluster and continuously reconciles the live cluster state against the `k8s/` directory in Git. GitHub Actions CI is responsible only for building images and updating image tags in manifests — it never calls `kubectl` directly.
- **Secrets never in source control**: All sensitive values live in AWS Secrets Manager and are injected at pod startup via the External Secrets Operator.
- **Stateless JWT validation**: The shared `JWT_SECRET` is stored once in Secrets Manager and mounted into both backend pods. No inter-service calls at runtime.
- **IRSA for least-privilege**: Each Kubernetes service account is bound to a dedicated IAM role via IRSA, granting only the permissions it needs.
- **Reduced CI/CD blast radius**: The CI/CD IAM role requires only ECR push permissions and Git write access. It no longer needs `eks:DescribeCluster` or any `kubectl` credentials.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│  GitHub Actions CI Pipeline  (push to main)                          │
│                                                                      │
│  1. mvn test                                                         │
│  2. docker build + push to ECR  (tagged with git SHA)                │
│  3. sed image tag in k8s/deployments/*.yaml                          │
│  4. git commit + push  ──────────────────────────────────────────┐   │
└──────────────────────────────────────────────────────────────────┼───┘
                                                                   │ Git push
                                                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Git Repository  (k8s/ directory)                                    │
│  k8s/deployments/*.yaml  ← image tags updated by CI                 │
│  k8s/argocd/autocare-app.yaml  ← ArgoCD Application CRD             │
└──────────────────────────────────────────────────────────────────┬───┘
                                                                   │ ArgoCD watches & syncs
                                                                   ▼
Internet
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│  AWS ALB (Public Subnets, ports 80/443)                         │
│  TLS termination via ACM certificate                            │
│  HTTP → HTTPS redirect (301)                                    │
└──────────────────────┬──────────────────────────────────────────┘
                       │  Kubernetes Ingress (ALB Ingress Controller)
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  EKS Cluster  (Kubernetes 1.29+)                                │
│                                                                 │
│  Namespace: argocd                                              │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ ArgoCD                                                   │  │
│  │  - watches k8s/ in Git                                   │  │
│  │  - detects manifest changes (image tag commits)          │  │
│  │  - syncs resources to namespace: autocare                │  │
│  │  - monitors rollout health                               │  │
│  │  - auto-rollback on health check failure                 │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Namespace: autocare                                            │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────────────┐            │
│  │ vehicle-          │  │ user-auth-service         │            │
│  │ maintenance-ui    │  │ (Spring Boot :8080)       │            │
│  │ (Nginx :3000)     │  │ 2–6 replicas (HPA)        │            │
│  │ 2 replicas        │  └──────────┬───────────────┘            │
│  └──────────────────┘             │                             │
│                                   │  JWT_SECRET (shared)        │
│  ┌──────────────────────────┐     │                             │
│  │ vehicle-maintenance-      │◄────┘                            │
│  │ service (Spring Boot      │                                  │
│  │ :8081) 2–6 replicas (HPA) │                                  │
│  └──────────────────────────┘                                   │
│                                                                 │
│  Add-ons: AWS LB Controller, Metrics Server, Fluent Bit         │
└──────────────────────┬──────────────────────────────────────────┘
                       │  port 3306 (private subnets only)
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Amazon RDS MySQL 8.0 (Multi-AZ, Private Subnets)               │
│  Schemas: auth_db, maintenance_db                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Supporting Services                                            │
│  ECR: autocare/user-auth-service                                │
│       autocare/vehicle-maintenance-service                      │
│       autocare/vehicle-maintenance-ui                           │
│  Secrets Manager: JWT_SECRET, DB_PASSWORD, RDS_ENDPOINT         │
│  CloudWatch: /autocare/<service-name> log groups                │
│  GitHub Actions CI pipeline  (build + image tag update only)    │
└─────────────────────────────────────────────────────────────────┘
```

### GitOps Delivery Flow

1. A developer merges a PR to `main`.
2. GitHub Actions runs tests, builds Docker images, pushes them to ECR tagged with the git SHA.
3. The CI pipeline updates the image tag in `k8s/deployments/*.yaml` using `sed` and commits the change back to the repository.
4. ArgoCD (running in the `argocd` namespace inside EKS) detects the new commit in the `k8s/` directory.
5. ArgoCD syncs the changed manifests to the `autocare` namespace, triggering a rolling update.
6. ArgoCD monitors the rollout health using the pod readiness probes. If health checks fail, ArgoCD automatically rolls back to the previous revision.

GitHub Actions never calls `kubectl` or holds EKS credentials. The cluster pull model means the CI role only needs ECR push permissions and Git write access.

### Network Topology

```
VPC (e.g. 10.0.0.0/16)
├── Public Subnets  (AZ-a, AZ-b) — ALB only
│     10.0.1.0/24, 10.0.2.0/24
└── Private Subnets (AZ-a, AZ-b) — EKS nodes + RDS
      10.0.3.0/24, 10.0.4.0/24
```

---

## Components and Interfaces

### Terraform Module Structure

```
infra/
├── main.tf                  # root module, provider config
├── variables.tf
├── outputs.tf
├── modules/
│   ├── vpc/                 # VPC, subnets, IGW, NAT, route tables
│   ├── ecr/                 # three ECR repositories
│   ├── eks/                 # EKS cluster, node group, OIDC provider
│   ├── rds/                 # RDS MySQL Multi-AZ instance
│   ├── iam/                 # IAM roles, IRSA bindings
│   ├── secrets/             # Secrets Manager secrets
│   ├── acm/                 # ACM certificate (DNS validation)
│   └── cloudwatch/          # Log groups, retention policies
```

### Kubernetes Manifest Structure

```
k8s/
├── namespace.yaml
├── resource-quota.yaml
├── argocd/
│   └── autocare-app.yaml        # ArgoCD Application CRD
├── configmaps/
│   ├── user-auth-service-config.yaml
│   ├── vehicle-maintenance-service-config.yaml
│   └── vehicle-maintenance-ui-config.yaml
├── secrets/
│   └── external-secrets/        # ExternalSecret CRDs (no plaintext values)
├── deployments/
│   ├── user-auth-service.yaml   # image tag updated by CI on each deploy
│   ├── vehicle-maintenance-service.yaml
│   └── vehicle-maintenance-ui.yaml
├── services/
│   ├── user-auth-service.yaml
│   ├── vehicle-maintenance-service.yaml
│   └── vehicle-maintenance-ui.yaml
├── ingress/
│   └── autocare-ingress.yaml
├── hpa/
│   ├── user-auth-service-hpa.yaml
│   └── vehicle-maintenance-service-hpa.yaml
└── network-policies/
    ├── ui-policy.yaml
    └── backend-policy.yaml
```

The `k8s/argocd/autocare-app.yaml` file defines an ArgoCD `Application` CRD that points to the `k8s/` directory in this repository. ArgoCD polls (or receives a webhook from) the repository and applies any changed manifests to the `autocare` namespace automatically.

### OPA Policy

```
policy/
└── k8s.rego                 # conftest OPA policies for manifest validation
```

The `policy/k8s.rego` file contains OPA rules enforced by `conftest` in the `k8s-validate.yml` CI workflow: resource limits present, no `hostNetwork`, no plaintext Secret `data:` fields, all Deployments have liveness and readiness probes.

### Bootstrap Documentation

```
docs/
└── bootstrap.md             # one-time cluster setup guide
```

### CI/CD Pipeline (GitHub Actions)

```
.github/workflows/
├── deploy.yml               # triggered on push to main — CI only (no kubectl)
├── terraform-validate.yml   # triggered on PR — Terraform static analysis
└── k8s-validate.yml         # triggered on PR — manifest validation
```

Pipeline stages in `deploy.yml`:
1. **test** — `mvn test` for both Spring Boot services + `npm run test` for the UI
2. **build-and-push** — Docker build + ECR push (commit SHA tag) for all three services
3. **update-manifests** — `sed` replaces the image tag placeholder in each `k8s/deployments/*.yaml` with the git SHA, then commits and pushes back to the repository

ArgoCD detects the manifest commit and handles the actual cluster sync. GitHub Actions requires no `kubectl` access, no `aws eks update-kubeconfig`, and no `eks:DescribeCluster` permission.

### External Secrets Operator

The External Secrets Operator is installed in the cluster. An `ExternalSecret` CRD maps Secrets Manager ARNs to Kubernetes Secret keys. The IRSA-bound service account grants read-only access to the three Autocare secrets.

---

## Data Models

### AWS Secrets Manager Secrets

| Secret Name | Value | Consumed By |
|---|---|---|
| `autocare/jwt-secret` | HS256 signing key (base64) | user-auth-service, vehicle-maintenance-service |
| `autocare/db-password` | MySQL root password | user-auth-service, vehicle-maintenance-service |
| `autocare/rds-endpoint` | RDS hostname | user-auth-service, vehicle-maintenance-service |

### ConfigMap Values (non-sensitive)

| Key | Value | Service |
|---|---|---|
| `DB_PORT` | `3306` | both backends |
| `DB_NAME` | `auth_db` / `maintenance_db` | per service |
| `DB_USERNAME` | `root` (or dedicated user) | both backends |
| `JWT_EXPIRATION_MS` | `86400000` | user-auth-service |
| `VITE_AUTH_API_URL` | `https://<domain>/api/auth` | vehicle-maintenance-ui (build arg) |
| `VITE_MAINTENANCE_API_URL` | `https://<domain>/api/v1` | vehicle-maintenance-ui (build arg) |

### Kubernetes Resource Quotas (Namespace: autocare)

| Resource | Limit |
|---|---|
| `requests.cpu` | 4 cores |
| `requests.memory` | 8Gi |
| `limits.cpu` | 8 cores |
| `limits.memory` | 12Gi |
| `count/pods` | 30 |

### Container Resource Requests and Limits

| Service | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---|---|---|---|---|
| user-auth-service | 250m | 500m | 512Mi | 768Mi |
| vehicle-maintenance-service | 250m | 500m | 512Mi | 768Mi |
| vehicle-maintenance-ui | 100m | 200m | 128Mi | 256Mi |

### ECR Repository Configuration

| Repository | Tag Immutability | Scan on Push | Lifecycle Rule |
|---|---|---|---|
| `autocare/user-auth-service` | IMMUTABLE | enabled | keep 30 tagged images |
| `autocare/vehicle-maintenance-service` | IMMUTABLE | enabled | keep 30 tagged images |
| `autocare/vehicle-maintenance-ui` | IMMUTABLE | enabled | keep 30 tagged images |

### RDS Instance Configuration

| Parameter | Value |
|---|---|
| Engine | MySQL 8.0 |
| Instance class | db.t3.micro (minimum) |
| Multi-AZ | true |
| Storage | 20 GiB gp3, auto-scaling enabled |
| Backup retention | 7 days |
| Subnet group | private subnets only |
| Port | 3306 |

### HPA Configuration

| Service | Min Replicas | Max Replicas | CPU Target | Scale-Down Stabilization |
|---|---|---|---|---|
| user-auth-service | 2 | 6 | 70% | 5 minutes |
| vehicle-maintenance-service | 2 | 6 | 70% | 5 minutes |

### Ingress Routing Rules

| Path Prefix | Backend Service | Port |
|---|---|---|
| `/api/auth/` | user-auth-service | 8080 |
| `/api/v1/` | vehicle-maintenance-service | 8081 |
| `/` (default) | vehicle-maintenance-ui | 3000 |

### Security Group Rules

| Security Group | Direction | Protocol | Port | Source/Destination |
|---|---|---|---|---|
| ALB SG | Inbound | TCP | 80, 443 | 0.0.0.0/0 |
| EKS Node SG | Inbound | TCP | 8080, 8081, 3000 | VPC CIDR |
| RDS SG | Inbound | TCP | 3306 | EKS Node SG |

### Health Probe Configuration

| Service | Probe Type | Path / Check | Port | Initial Delay | Period | Failure Threshold |
|---|---|---|---|---|---|---|
| user-auth-service | liveness | TCP socket | 8080 | 30s | 10s | 3 |
| user-auth-service | readiness | HTTP GET `/api/auth/signin` (non-5xx) | 8080 | 30s | 10s | 3 |
| vehicle-maintenance-service | liveness | HTTP GET `/actuator/health` | 8081 | 30s | 10s | 3 |
| vehicle-maintenance-service | readiness | HTTP GET `/actuator/health` | 8081 | 30s | 10s | 3 |
| vehicle-maintenance-ui | liveness | HTTP GET `/` | 3000 | 30s | 10s | 3 |
| vehicle-maintenance-ui | readiness | HTTP GET `/` | 3000 | 30s | 10s | 3 |

### ArgoCD Application Configuration

| Parameter | Value |
|---|---|
| Application Name | `autocare` |
| Namespace | `autocare` |
| Source Repository | Git repository URL (e.g. `https://github.com/org/autocare-infra`) |
| Source Path | `k8s/` |
| Target Cluster | `https://kubernetes.default.svc` (in-cluster) |
| Sync Policy | Automated with self-heal enabled |
| Prune | Enabled (removes resources deleted from Git) |
| Health Assessment | Enabled (monitors Deployment rollout status) |
| Rollback on Failure | Enabled (reverts to previous revision if health checks fail) |

### IAM Role Permissions

**CI/CD Pipeline Role** (assumed by GitHub Actions):
- `ecr:GetAuthorizationToken`
- `ecr:BatchCheckLayerAvailability`
- `ecr:PutImage`
- `ecr:InitiateLayerUpload`
- `ecr:UploadLayerPart`
- `ecr:CompleteLayerUpload`
- Git write access (via GitHub token or SSH key)

**IRSA Role for `autocare-sa` service account**:
- `secretsmanager:GetSecretValue` on `autocare/jwt-secret`, `autocare/db-password`, `autocare/rds-endpoint`
- `secretsmanager:DescribeSecret` on the same secrets

---

## Error Handling

### Pod Startup Failures
- Spring Boot services require the RDS instance to be reachable before the application context starts. If the DB is unavailable, the pod will crash-loop. Kubernetes will apply exponential back-off (CrashLoopBackOff). The readiness probe prevents traffic from reaching a pod that has not yet established a DB connection.
- The `initialDelaySeconds: 30` on all probes absorbs normal Spring Boot startup time (~15–25 seconds) and prevents premature restarts.

### Secret Injection Failures
- If the External Secrets Operator cannot retrieve a secret from Secrets Manager (e.g. IAM permission denied, network issue), the `ExternalSecret` will fail to sync and the Kubernetes Secret will not be created or updated. Pods that depend on the missing Secret will fail to start and remain in `Pending` state. Alerts should be configured on `ExternalSecret` sync failures and pod pending duration.

### Rolling Update Failures
- Deployments use `RollingUpdate` strategy with `maxUnavailable: 0` and `maxSurge: 1`. If the new pod's readiness probe never passes, the rollout stalls and the old pods remain serving traffic. ArgoCD monitors rollout health and automatically rolls back to the previous Git revision if the health assessment fails within the configured timeout. No manual `kubectl rollout undo` is required.

### RDS Failover
- In a Multi-AZ RDS failover, the DNS endpoint automatically points to the standby. Spring Boot's HikariCP connection pool will detect the broken connections and reconnect. Expect ~30–60 seconds of DB unavailability during failover. The liveness probe will not restart pods during this window (failure threshold is 3 × 10s = 30s), but readiness probes will temporarily remove pods from the load balancer if health checks fail.

### ALB 502/503 Responses
- The ALB returns 502 when all backend pods are unhealthy or unavailable. A custom error response body is configured via ALB listener rules to return a JSON error body rather than the default AWS HTML page.

### CI/CD Pipeline Failures
- If Maven tests fail, the pipeline halts before any Docker build. If a Docker build or ECR push fails, no manifest commit is made and ArgoCD sees no change — the cluster state is unaffected. The pipeline uses GitHub Actions job dependencies (`needs:`) to enforce this ordering. Failed runs produce a GitHub Actions notification.
- If the manifest commit step fails (e.g. a Git push conflict), the old image tag remains in Git and ArgoCD continues serving the previous version. The developer must re-run or rebase the pipeline.

### ArgoCD Sync Failures
- If ArgoCD cannot apply a manifest (e.g. a schema validation error in a new CRD), it marks the Application as `Degraded` and stops syncing. The previous revision continues running. Alerts should be configured on ArgoCD Application health status transitions to `Degraded` or `Unknown`.
- ArgoCD's self-heal feature will re-apply manifests if someone manually modifies cluster resources out-of-band, ensuring Git remains the single source of truth.

### Secret Rotation
- When a secret is rotated in Secrets Manager, the External Secrets Operator polls for updates on a configurable interval (default: 1 hour, satisfying Requirement 5.4). Pods receive the updated value without a manual deployment. For the JWT secret specifically, a rotation window must be coordinated: both services must receive the new secret before the old tokens expire, or a brief dual-key validation period must be implemented.

---

## Testing Strategy

This feature is an infrastructure-as-code and Kubernetes configuration project. Property-based testing is not applicable here — the deliverables are Terraform modules and Kubernetes YAML manifests, which are declarative configuration rather than functions with input/output behavior. Running 100 randomized iterations against Terraform plans or Kubernetes manifests would not reveal additional correctness issues beyond what targeted example-based and integration tests provide.

The testing strategy uses three complementary layers:

### Layer 1: Terraform Static Analysis and Unit Tests

**Tools**: `terraform validate`, `terraform plan`, `tflint`, `checkov` (policy-as-code)

- `terraform validate` — syntax and schema correctness for all modules
- `terraform plan` — dry-run against a test AWS account to verify resource graph
- `tflint` — lint rules for AWS provider best practices (instance types, deprecated arguments)
- `checkov` — security policy checks: encryption at rest, public access blocks, security group rules, IAM least-privilege

Run in CI on every pull request before any `terraform apply`.

### Layer 2: Kubernetes Manifest Validation

**Tools**: `kubectl --dry-run=client`, `kubeval` or `kubeconform`, `kube-score`, `conftest` (OPA policies)

- `kubeconform` — validates all YAML manifests against the Kubernetes JSON schema for the target version (1.29)
- `kube-score` — checks best practices: resource limits set, probes configured, security contexts, non-root containers
- `conftest` with OPA policies — enforces project-specific rules:
  - All Deployments must have resource requests and limits
  - No `hostNetwork: true` or `privileged: true`
  - All Secrets must reference ExternalSecret sources (no plaintext `data:` fields)
  - All Deployments in the `autocare` namespace must have liveness and readiness probes

Run in CI on every pull request.

### Layer 3: Integration and Smoke Tests

**Smoke tests** (run once after `terraform apply` to a staging environment):
- EKS cluster is reachable via `kubectl cluster-info`
- All three ECR repositories exist and have tag immutability enabled
- RDS instance is in `available` state and Multi-AZ is enabled
- Secrets Manager contains the three expected secrets
- ACM certificate is in `ISSUED` state
- CloudWatch log groups exist with correct retention policies

**Integration tests** (run after ArgoCD syncs to staging):
- All pods in the `autocare` namespace reach `Running` state within 3 minutes
- ArgoCD Application reports `Healthy` and `Synced` status
- ALB is provisioned and returns HTTP 301 on port 80
- ALB returns HTTP 200 on `https://<domain>/` (UI)
- ALB returns HTTP 200 on `https://<domain>/api/auth/signin` (POST with valid credentials)
- ALB returns HTTP 401 on `https://<domain>/api/v1/vehicles` without a JWT
- HPA objects exist and report `TARGETS` (not `<unknown>`) — confirms Metrics Server is running
- Fluent Bit DaemonSet pods are running on all nodes
- CloudWatch log group `/autocare/user-auth-service` receives a log entry within 60 seconds of a test request

**Rollback test** (run once per release cycle):
- Push a deliberately broken image tag, verify ArgoCD marks the Application as `Degraded`, verify ArgoCD auto-rollback restores the previous revision and the Application returns to `Healthy`

### Test Execution in CI/CD

```
PR opened
  └── terraform validate + tflint + checkov
  └── kubeconform + kube-score + conftest

Merge to main
  └── mvn test (user-auth-service, vehicle-maintenance-service)
  └── docker build + ECR push
  └── update image tags in k8s/deployments/*.yaml + git commit
  └── ArgoCD detects commit → syncs to staging namespace
  └── smoke tests + integration tests (poll ArgoCD app health)
  └── ArgoCD syncs to production (on manual approval / ArgoCD promotion)
```
