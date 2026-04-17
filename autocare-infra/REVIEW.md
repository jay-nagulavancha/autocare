# Autocare EKS Deployment — Review Summary

## ✅ What's Complete

### Build & Test
- ✅ Maven tests for both Spring Boot services (JDK 11, H2 in-memory DB)
- ✅ npm tests for React UI (Node 20, vitest)
- ✅ Dockerfiles for all three services (multi-stage builds, production-ready)
- ✅ Test execution in CI before any build

### CI/CD Pipeline
- ✅ GitHub Actions workflow at `.github/workflows/deploy.yml`
- ✅ Three-job pipeline: test → build-and-push → update-manifests
- ✅ OIDC authentication (no long-lived AWS keys)
- ✅ ECR push with git SHA tags
- ✅ Manifest update via sed + git commit (GitOps)
- ✅ `[skip ci]` flag prevents infinite loops
- ✅ No kubectl — ArgoCD handles cluster sync
- ✅ Terraform validation workflow (PR-triggered)
- ✅ Kubernetes manifest validation workflow (PR-triggered)

### Infrastructure (Terraform)
- ✅ VPC module — 2 public + 2 private subnets, IGW, NAT, route tables
- ✅ ECR module — 3 repositories, immutable tags, scan on push, lifecycle policy (keep 30)
- ✅ EKS module — cluster 1.29, OIDC provider, managed node group (t3.medium, 2-4 nodes)
- ✅ RDS module — MySQL 8.0 Multi-AZ, private subnets, security group, 7-day backups
- ✅ Secrets Manager module — JWT_SECRET, DB_PASSWORD, RDS_ENDPOINT
- ✅ IAM module — IRSA role for autocare-sa, CI/CD role (ECR-only, no EKS permissions)
- ✅ ACM module — TLS certificate with DNS validation
- ✅ CloudWatch module — log groups with 30-day retention
- ✅ Root module wiring — all modules connected, outputs exposed

### Kubernetes Manifests
- ✅ Namespace + ResourceQuota
- ✅ ConfigMaps for all three services
- ✅ ExternalSecret CRDs (1h refresh interval, no plaintext secrets)
- ✅ ServiceAccount with IRSA annotation
- ✅ Deployments — 2 replicas, resource limits, rolling updates, health probes
- ✅ Services — ClusterIP for all three
- ✅ Ingress — ALB with path-based routing, HTTPS redirect, ACM cert
- ✅ HPAs — 2-6 replicas at 70% CPU, 5min scale-down stabilization
- ✅ NetworkPolicies — egress restrictions for UI and backends

### ArgoCD GitOps
- ✅ ArgoCD Application CRD with automated sync, self-heal, prune
- ✅ Health assessment + auto-rollback on failure
- ✅ Bootstrap documentation with all one-time setup steps

### Validation & Policy
- ✅ OPA policies (conftest) — resource limits, probes, no hostNetwork, no plaintext secrets
- ✅ kubeconform — schema validation against K8s 1.29
- ✅ kube-score — best practices checks
- ✅ tflint + checkov — Terraform linting and security scanning

---

## 🔧 Issues Fixed

### 1. Duplicate CI/CD workflows (critical)
**Problem**: Old workflows at `.github/workflows/user-auth-service.yml` and `vehicle-maintenance-service.yml` would fire alongside the new `deploy.yml`, causing conflicts and pushing a meaningless `latest` tag.

**Fixed**: Deleted both old workflows. Only `deploy.yml` remains.

### 2. Workflows in wrong location (critical)
**Problem**: Workflows were in `autocare-infra/.github/workflows/` — GitHub Actions only reads from `.github/workflows/` at repo root.

**Fixed**: Moved all three workflows to `.github/workflows/` at repo root.

### 3. Overly broad sed pattern
**Problem**: `s|autocare/user-auth-service:.*|...|g` would match any line containing that string, including comments.

**Fixed**: Changed to `s|image: ${ECR}/autocare/user-auth-service:.*|image: ${ECR}/autocare/user-auth-service:${SHA}|g` to target only the `image:` field.

### 4. kubeconform CRD handling
**Problem**: ExternalSecret and ArgoCD Application CRDs were excluded via grep, but kubeconform has a proper flag for this.

**Fixed**: Removed grep exclusion, added `--ignore-missing-schemas` flag to kubeconform.

### 5. conftest policy path
**Problem**: conftest was running without an explicit `--policy` flag, relying on default discovery.

**Fixed**: Added `--policy policy/` to explicitly point to `autocare-infra/policy/k8s.rego`.

---

## 📋 Pre-Deployment Checklist

### AWS Setup
- [ ] Create S3 bucket + DynamoDB table for Terraform state (update `infra/main.tf` backend block)
- [ ] Set up GitHub OIDC provider in AWS IAM (for the CI/CD role trust policy)
- [ ] Create a Route53 hosted zone for your domain (for ACM DNS validation)

### GitHub Secrets
Configure these in your GitHub repository settings:
- [ ] `AWS_ROLE_ARN` — IAM role ARN for CI/CD (output from `terraform apply`)
- [ ] `AWS_REGION` — e.g. `us-west-2`
- [ ] `ECR_REGISTRY` — e.g. `123456789.dkr.ecr.us-west-2.amazonaws.com`
- [ ] `VITE_AUTH_API_URL` — e.g. `https://autocare.example.com/api/auth`
- [ ] `VITE_MAINTENANCE_API_URL` — e.g. `https://autocare.example.com/api/v1`

### Terraform Variables
Create `autocare-infra/infra/terraform.tfvars` (excluded from git):
```hcl
aws_region   = "us-west-2"
cluster_name = "autocare-eks"
github_org   = "jay-nagulavancha"
github_repo  = "autocare"

# Sensitive — never commit
db_password = "CHANGE_ME"
jwt_secret  = "CHANGE_ME"
```

### Git Repository
- [ ] Update `autocare-infra/k8s/argocd/autocare-app.yaml` — replace `https://github.com/YOUR_ORG/autocare-infra` with your actual repo URL

---

## 🚀 Deployment Flow

### One-Time Bootstrap
1. `cd autocare-infra/infra && terraform apply`
2. `aws eks update-kubeconfig --region <region> --name <cluster>`
3. Follow `autocare-infra/docs/bootstrap.md`:
   - Install ArgoCD
   - Install External Secrets Operator
   - Install AWS Load Balancer Controller
   - Install Metrics Server
   - Install Fluent Bit
   - Populate secrets in AWS Secrets Manager
   - Apply ArgoCD Application CRD

### Ongoing Deployments
1. Developer merges PR to `main`
2. GitHub Actions: test → build → push to ECR → update manifests → git commit
3. ArgoCD: detects commit → syncs to cluster → monitors health → auto-rollbacks if needed

---

## 📊 What You Have

```
.github/workflows/
├── deploy.yml               # CI/CD: test → build → push → update manifests
├── terraform-validate.yml   # PR checks: terraform validate, tflint, checkov
└── k8s-validate.yml         # PR checks: kubeconform, kube-score, conftest

autocare-infra/
├── infra/                   # Terraform modules (VPC, EKS, RDS, ECR, IAM, ACM, CloudWatch, Secrets)
├── k8s/                     # Kubernetes manifests (Deployments, Services, Ingress, HPA, NetworkPolicies, ExternalSecrets, ArgoCD App)
├── policy/                  # OPA policies for conftest
└── docs/                    # Bootstrap guide

autocare/
├── user-auth-service/       # Spring Boot, JWT, MySQL
├── vehicle-maintenance-service/  # Spring Boot, JWT, MySQL
└── vehicle-maintenance-ui/  # React, Vite, Nginx
```

---

## ✨ Key Features

- **GitOps**: ArgoCD watches Git, syncs automatically, rolls back on failure
- **Zero kubectl in CI**: GitHub Actions only updates manifests, ArgoCD handles cluster
- **OIDC everywhere**: No long-lived AWS keys in GitHub secrets
- **Least-privilege IAM**: CI role has ECR-only permissions, no EKS access
- **Secrets rotation**: External Secrets Operator polls Secrets Manager every hour
- **Zero downtime**: Rolling updates with `maxUnavailable: 0`
- **Auto-scaling**: HPA scales 2-6 replicas at 70% CPU
- **Observability**: CloudWatch Container Insights + Fluent Bit for centralized logs
- **Security**: Network policies, private subnets, security groups, no plaintext secrets
- **Validation**: Terraform + K8s manifest checks on every PR

---

## 🎯 Next Steps

1. Set up AWS prerequisites (S3 backend, OIDC provider, Route53)
2. Configure GitHub secrets
3. Create `terraform.tfvars` with your values
4. Run `terraform apply`
5. Follow bootstrap guide
6. Push to `main` — watch it deploy!
