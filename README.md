# Autocare — Vehicle Maintenance Management System

A multi-service platform for auto shops to manage vehicles, work orders, technicians, bays, parts, labor, and service scheduling. Deployed on AWS EKS with a GitOps pipeline.

---

## Services

| Service | Stack | Port | Description |
|---|---|---|---|
| `user-auth-service` | Spring Boot 3.x, MySQL | 8080 | Registration, login, JWT issuance, role management |
| `vehicle-maintenance-service` | Spring Boot 3.x, MySQL | 8081 | Vehicles, work orders, scheduling, technicians, bays, parts/labor |
| `vehicle-maintenance-ui` | React, TypeScript, Vite, Nginx | 3000 | Web frontend |

## User Roles

| Role | Access |
|---|---|
| `ROLE_ADMIN` | Full access to all operations |
| `ROLE_TECHNICIAN` | Assigned work orders and vehicle data |
| `ROLE_CUSTOMER` | Read-only access to own vehicles and work orders |

## Work Order Lifecycle

```
OPEN → IN_PROGRESS → PENDING_PARTS → IN_PROGRESS → COMPLETED → INVOICED
```

---

## Local Development (Docker Compose)

**Prerequisites:** Docker, Docker Compose

```bash
cd autocare
cp .env.example .env
# Edit .env — set JWT_SECRET and DB_PASSWORD
docker compose up --build
```

| URL | Description |
|---|---|
| http://localhost:3000 | UI |
| http://localhost:8080/api/auth/signin | Auth API |
| http://localhost:8081/swagger-ui.html | Maintenance API docs |
| http://localhost:8081/v3/api-docs | OpenAPI spec |

Services start in order: MySQL → auth + maintenance → UI.

---

## Running Tests

```bash
# Auth Service
cd autocare/user-auth-service
./mvnw test -B

# Maintenance Service
cd autocare/vehicle-maintenance-service
./mvnw test -B

# UI
cd autocare/vehicle-maintenance-ui
npm ci && npm run test
```

---

## AWS EKS Deployment

The platform runs on AWS EKS with a GitOps delivery model via ArgoCD.

### Architecture

```
GitHub Actions CI
  └── test → build → push to ECR → update k8s manifests → git commit
        │
        ▼
ArgoCD (in-cluster)
  └── detects manifest change → syncs to EKS → monitors health → auto-rollback
```

### Infrastructure (Terraform)

All AWS resources are managed by Terraform in `autocare-infra/infra/`:

| Module | Resources |
|---|---|
| `vpc` | VPC, public/private subnets, IGW, NAT gateway |
| `eks` | EKS cluster 1.29, managed node group, OIDC provider |
| `rds` | MySQL 8.0 Multi-AZ, private subnets |
| `ecr` | 3 private repositories with immutable tags |
| `iam` | IRSA role, CI/CD pipeline role (ECR-only) |
| `secrets` | AWS Secrets Manager (JWT_SECRET, DB_PASSWORD, RDS endpoint) |
| `cloudwatch` | Log groups with 30-day retention |
| `auto-shutdown` | Lambda + EventBridge for dev cost optimisation |

### One-Time Setup

```bash
# 1. AWS prerequisites (S3 state bucket, DynamoDB lock table, GitHub OIDC)
export AWS_REGION=us-west-2
export TF_STATE_BUCKET=autocare-terraform-state-<account-id>
./autocare-infra/scripts/00-aws-prerequisites.sh

# 2. Deploy infrastructure (~15-20 min)
./autocare-infra/scripts/01-terraform-apply.sh

# 3. Bootstrap cluster (ArgoCD, External Secrets, LBC, Metrics Server, Fluent Bit)
export CLUSTER_NAME=autocare-eks
export DB_PASSWORD=<your-db-password>
export JWT_SECRET=<your-jwt-secret>
./autocare-infra/scripts/02-bootstrap-cluster.sh

# 4. Set GitHub secrets
export GITHUB_REPO=jay-nagulavancha/autocare
export VITE_AUTH_API_URL=http://<alb-dns>/api/auth
export VITE_MAINTENANCE_API_URL=http://<alb-dns>/api/v1
./autocare-infra/scripts/03-configure-github-secrets.sh
```

### Deploy

```bash
git push origin main
```

GitHub Actions runs tests → builds images → pushes to ECR → commits updated manifests. ArgoCD detects the commit and syncs to the cluster automatically.

### Verify

```bash
./autocare-infra/scripts/04-verify-deployment.sh
```

### Start / Stop (Dev Cost Saving)

```bash
# Stop cluster (scales nodes to 0, stops RDS — saves ~$89/mo)
./autocare-infra/scripts/cluster-stop.sh autocare-eks us-west-2

# Start cluster
./autocare-infra/scripts/cluster-start.sh autocare-eks us-west-2
```

Auto-shutdown is also available via Lambda — enable in `terraform.tfvars`:
```hcl
enable_auto_shutdown       = true
auto_shutdown_idle_minutes = 30
```

### Access ArgoCD UI

```bash
# Port-forward
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Get admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d && echo
```

Open `https://localhost:8080` — login with `admin` and the password above.

### Access the UI

```bash
# Via ALB (after ingress is provisioned)
kubectl get ingress -n autocare

# Or port-forward directly
kubectl port-forward svc/vehicle-maintenance-ui -n autocare 3000:3000
```

---

## Repository Structure

```
autocare/
├── user-auth-service/          Spring Boot auth service
├── vehicle-maintenance-service/ Spring Boot domain service
├── vehicle-maintenance-ui/     React frontend
├── db/                         MySQL init scripts
├── tests/postman/              Postman collection
├── docker-compose.yml          Local dev orchestration
└── .env.example                Environment variable template

autocare-infra/
├── infra/                      Terraform modules
├── k8s/                        Kubernetes manifests
├── scripts/                    Deployment scripts
├── docs/                       Bootstrap guide, cost estimate
└── policy/                     OPA policies for manifest validation

.github/workflows/
├── deploy.yml                  CI/CD: test → build → push → update manifests
├── terraform-validate.yml      PR: Terraform static analysis
└── k8s-validate.yml            PR: Kubernetes manifest validation
```

---

## Environment Variables

| Variable | Used by | Description |
|---|---|---|
| `JWT_SECRET` | auth, maintenance | Shared HS256 signing key |
| `JWT_EXPIRATION_MS` | auth | Token TTL (default 86400000 = 24h) |
| `DB_HOST` | auth, maintenance | MySQL hostname |
| `DB_PORT` | auth, maintenance | MySQL port (3306) |
| `DB_NAME` | auth, maintenance | Schema name (auth_db / maintenance_db) |
| `DB_USERNAME` | auth, maintenance | MySQL user |
| `DB_PASSWORD` | auth, maintenance | MySQL password |
| `VITE_AUTH_API_URL` | UI | Auth service base URL |
| `VITE_MAINTENANCE_API_URL` | UI | Maintenance service base URL |

See `autocare/.env.example` for local dev values.

---

## Monthly Cost (Dev, us-west-2)

| Mode | Cost |
|---|---|
| Always on | ~$209/mo |
| Auto-shutdown (4hr/day, 5 days/week) | ~$120/mo |

See `autocare-infra/docs/cost-estimate.md` for full breakdown.
