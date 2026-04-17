# Implementation Plan: AWS EKS Deployment (Autocare)

## Overview

Implement the full infrastructure-as-code and Kubernetes configuration stack to deploy the Autocare Vehicle Maintenance System to AWS EKS. The work is broken into discrete coding steps: Terraform modules, Kubernetes manifests, ArgoCD GitOps configuration, and the GitHub Actions CI/CD pipeline. Each task builds on the previous and ends with all components wired together.

No property-based tests are included — this is a declarative IaC and Kubernetes configuration project. The testing strategy uses static analysis tools (`terraform validate`, `tflint`, `checkov`, `kubeconform`, `kube-score`, `conftest`) run in CI, plus smoke and integration tests against a staging environment.

---

## Tasks

- [x] 1. Scaffold `autocare-infra` project structure
  - Create the top-level directory layout: `autocare-infra/infra/`, `autocare-infra/k8s/`, `autocare-infra/.github/workflows/`
  - Create `autocare-infra/infra/main.tf`, `variables.tf`, `outputs.tf` with the AWS provider block, required Terraform version constraint (`>= 1.6`), and backend configuration (S3 + DynamoDB state locking placeholders)
  - Create stub `modules/` subdirectories: `vpc/`, `ecr/`, `eks/`, `rds/`, `iam/`, `secrets/`, `acm/`, `cloudwatch/` — each with empty `main.tf`, `variables.tf`, `outputs.tf`
  - Create `autocare-infra/infra/.terraform.lock.hcl` placeholder and `.gitignore` excluding `*.tfstate`, `*.tfstate.backup`, `.terraform/`
  - _Requirements: 2.1, 2.2_

- [x] 2. Implement Terraform VPC module
  - [x] 2.1 Write `modules/vpc/main.tf`
    - Define `aws_vpc` resource (`10.0.0.0/16`, DNS hostnames enabled)
    - Define two public subnets (`10.0.1.0/24`, `10.0.2.0/24`) across two AZs with `map_public_ip_on_launch = true` and the `kubernetes.io/role/elb = 1` tag required by the ALB controller
    - Define two private subnets (`10.0.3.0/24`, `10.0.4.0/24`) with the `kubernetes.io/role/internal-elb = 1` tag
    - Define `aws_internet_gateway`, one `aws_eip`, one `aws_nat_gateway` (in a public subnet), and route tables routing private subnets through the NAT and public subnets through the IGW
    - _Requirements: 2.2, 13.1, 13.2_
  - [x] 2.2 Write `modules/vpc/variables.tf` and `modules/vpc/outputs.tf`
    - Expose `vpc_id`, `public_subnet_ids`, `private_subnet_ids` as outputs
    - _Requirements: 2.2_

- [x] 3. Implement Terraform ECR module
  - [x] 3.1 Write `modules/ecr/main.tf`
    - Define three `aws_ecr_repository` resources: `autocare/user-auth-service`, `autocare/vehicle-maintenance-service`, `autocare/vehicle-maintenance-ui`
    - Set `image_tag_mutability = "IMMUTABLE"` and `scan_on_push = true` on each
    - Attach an `aws_ecr_lifecycle_policy` to each repository keeping the 30 most recent tagged images
    - _Requirements: 1.1, 1.3, 1.4, 1.5_
  - [x] 3.2 Write `modules/ecr/outputs.tf`
    - Expose repository URLs for all three repositories
    - _Requirements: 1.1_

- [x] 4. Implement Terraform EKS module
  - [x] 4.1 Write `modules/eks/main.tf` — cluster and OIDC provider
    - Define `aws_eks_cluster` with Kubernetes version `1.29`, VPC config using private subnets, and endpoint public access enabled
    - Define `aws_iam_role` and `aws_iam_role_policy_attachment` for the cluster service role (`AmazonEKSClusterPolicy`)
    - Define `aws_iam_openid_connect_provider` using the cluster's OIDC issuer URL (required for IRSA)
    - _Requirements: 2.1, 2.2, 2.4_
  - [x] 4.2 Write `modules/eks/main.tf` — managed node group
    - Define `aws_eks_node_group` with instance type `t3.medium`, desired/min/max sizes (2/2/4), placement in private subnets
    - Define the node IAM role with `AmazonEKSWorkerNodePolicy`, `AmazonEKS_CNI_Policy`, `AmazonEC2ContainerRegistryReadOnly` attachments
    - _Requirements: 2.3, 2.4, 2.7_
  - [x] 4.3 Write `modules/eks/outputs.tf`
    - Expose `cluster_name`, `cluster_endpoint`, `cluster_certificate_authority_data`, `oidc_provider_arn`, `node_group_role_arn`
    - _Requirements: 2.1_

- [x] 5. Implement Terraform RDS module
  - [x] 5.1 Write `modules/rds/main.tf`
    - Define `aws_db_subnet_group` using private subnets
    - Define `aws_security_group` for RDS allowing inbound TCP 3306 only from the EKS node security group
    - Define `aws_db_instance` with engine `mysql`, engine version `8.0`, class `db.t3.micro`, `multi_az = true`, `storage_type = "gp3"`, `allocated_storage = 20`, `max_allocated_storage = 100`, `backup_retention_period = 7`, `skip_final_snapshot = false`
    - Reference `DB_PASSWORD` from a variable (value sourced from Secrets Manager at apply time via `data` source or passed in)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.6_
  - [x] 5.2 Write `modules/rds/outputs.tf`
    - Expose `rds_endpoint`, `rds_security_group_id`
    - _Requirements: 4.3_

- [x] 6. Implement Terraform Secrets Manager module
  - [x] 6.1 Write `modules/secrets/main.tf`
    - Define three `aws_secretsmanager_secret` resources: `autocare/jwt-secret`, `autocare/db-password`, `autocare/rds-endpoint`
    - Define corresponding `aws_secretsmanager_secret_version` resources with placeholder values (actual values injected outside Terraform or via `terraform.tfvars` excluded from source control)
    - _Requirements: 5.1, 5.5_
  - [x] 6.2 Write `modules/secrets/outputs.tf`
    - Expose secret ARNs for use in IAM policy documents
    - _Requirements: 5.3_

- [x] 7. Implement Terraform IAM module (IRSA)
  - [x] 7.1 Write `modules/iam/main.tf` — IRSA role for the `autocare` service account
    - Define `aws_iam_role` with a trust policy that allows the EKS OIDC provider to assume the role for the `autocare/autocare-sa` service account
    - Define `aws_iam_policy` granting `secretsmanager:GetSecretValue` and `secretsmanager:DescribeSecret` on only the three Autocare secret ARNs
    - Attach the policy to the role
    - _Requirements: 5.3, 10.5_
  - [x] 7.2 Write `modules/iam/main.tf` — CI/CD pipeline IAM role
    - Define an IAM role assumable by GitHub Actions OIDC with least-privilege ECR-only permissions: `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`
    - Do NOT include `eks:DescribeCluster` — the CI/CD role never calls the EKS API; ArgoCD handles cluster sync from inside the cluster
    - _Requirements: 10.5_
  - [x] 7.3 Write `modules/iam/outputs.tf`
    - Expose `irsa_role_arn`, `cicd_role_arn`
    - _Requirements: 5.3, 10.5_

- [x] 8. Implement Terraform ACM and CloudWatch modules
  - [x] 8.1 Write `modules/acm/main.tf`
    - Define `aws_acm_certificate` with `validation_method = "DNS"` for the configured domain
    - Define `aws_acm_certificate_validation` resource
    - Expose `certificate_arn` as output
    - _Requirements: 8.2_
  - [x] 8.2 Write `modules/cloudwatch/main.tf`
    - Define three `aws_cloudwatch_log_group` resources: `/autocare/user-auth-service`, `/autocare/vehicle-maintenance-service`, `/autocare/vehicle-maintenance-ui`
    - Set `retention_in_days = 30` on each
    - _Requirements: 11.2, 11.3_

- [x] 9. Wire Terraform root module
  - [x] 9.1 Update `autocare-infra/infra/main.tf` to call all child modules
    - Pass VPC outputs into EKS, RDS, and IAM modules
    - Pass EKS OIDC provider ARN into IAM module
    - Pass RDS security group ID into EKS node group security group rules
    - Pass secret ARNs from secrets module into IAM module policy
    - _Requirements: 2.2, 4.3, 5.3, 13.1_
  - [x] 9.2 Write `autocare-infra/infra/variables.tf` with all root-level input variables
    - `aws_region`, `cluster_name`, `domain_name`, `db_password` (sensitive), `jwt_secret` (sensitive)
    - _Requirements: 2.1_
  - [x] 9.3 Write `autocare-infra/infra/outputs.tf`
    - Expose `eks_cluster_name`, `ecr_repository_urls`, `rds_endpoint`, `alb_dns_name`
    - _Requirements: 2.1_

- [x] 10. Checkpoint — validate Terraform
  - Run `terraform validate` across all modules and the root module to confirm HCL syntax is correct
  - Run `tflint` to check AWS provider best practices
  - Run `checkov -d infra/` to verify encryption at rest, no public RDS, IAM least-privilege, and security group rules
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Create Kubernetes namespace and resource quota manifests
  - [x] 11.1 Write `autocare-infra/k8s/namespace.yaml`
    - Define `Namespace` named `autocare` with label `name: autocare`
    - _Requirements: 3.1_
  - [x] 11.2 Write `autocare-infra/k8s/resource-quota.yaml`
    - Define `ResourceQuota` in namespace `autocare` with limits: `requests.cpu: "4"`, `requests.memory: 8Gi`, `limits.cpu: "8"`, `limits.memory: 12Gi`, `count/pods: "30"`
    - _Requirements: 3.2_

- [x] 12. Create Kubernetes ConfigMap manifests
  - [x] 12.1 Write `autocare-infra/k8s/configmaps/user-auth-service-config.yaml`
    - Define `ConfigMap` with keys: `DB_PORT: "3306"`, `DB_NAME: auth_db`, `DB_USERNAME: root`, `JWT_EXPIRATION_MS: "86400000"`
    - _Requirements: 6.4_
  - [x] 12.2 Write `autocare-infra/k8s/configmaps/vehicle-maintenance-service-config.yaml`
    - Define `ConfigMap` with keys: `DB_PORT: "3306"`, `DB_NAME: maintenance_db`, `DB_USERNAME: root`
    - _Requirements: 6.4_
  - [x] 12.3 Write `autocare-infra/k8s/configmaps/vehicle-maintenance-ui-config.yaml`
    - Define `ConfigMap` with keys: `VITE_AUTH_API_URL` and `VITE_MAINTENANCE_API_URL` pointing to the production ALB HTTPS endpoints (parameterised with a placeholder domain)
    - _Requirements: 6.4, 14.1_

- [x] 13. Create External Secrets manifests
  - [x] 13.1 Write `autocare-infra/k8s/secrets/external-secrets/secret-store.yaml`
    - Define `SecretStore` (or `ClusterSecretStore`) resource referencing the AWS Secrets Manager provider and the IRSA-annotated service account
    - _Requirements: 5.2, 5.3_
  - [x] 13.2 Write `autocare-infra/k8s/secrets/external-secrets/autocare-secrets.yaml`
    - Define `ExternalSecret` CRD that maps `autocare/jwt-secret` → `JWT_SECRET`, `autocare/db-password` → `DB_PASSWORD`, `autocare/rds-endpoint` → `DB_HOST`
    - Set `refreshInterval: 1h` to satisfy the 1-hour rotation requirement
    - Ensure no plaintext `data:` fields are present
    - _Requirements: 5.1, 5.2, 5.4, 5.5_
  - [x] 13.3 Write `autocare-infra/k8s/secrets/external-secrets/service-account.yaml`
    - Define `ServiceAccount` named `autocare-sa` in namespace `autocare` with the `eks.amazonaws.com/role-arn` annotation pointing to the IRSA role ARN
    - _Requirements: 5.3_

- [x] 14. Create Kubernetes Deployment manifests
  - [x] 14.1 Write `autocare-infra/k8s/deployments/user-auth-service.yaml`
    - Define `Deployment` with `replicas: 2`, container image placeholder `<ECR_URL>/autocare/user-auth-service:<TAG>`, port 8080
    - Set resource requests/limits: CPU 250m/500m, memory 512Mi/768Mi
    - Inject env vars from ConfigMap (`DB_PORT`, `DB_NAME`, `DB_USERNAME`, `JWT_EXPIRATION_MS`) and from the ExternalSecret-generated Secret (`DB_HOST`, `DB_PASSWORD`, `JWT_SECRET`)
    - Configure liveness probe: TCP socket port 8080, `initialDelaySeconds: 30`, `periodSeconds: 10`, `failureThreshold: 3`
    - Configure readiness probe: HTTP GET `/api/auth/signin`, port 8080, `initialDelaySeconds: 30`, `periodSeconds: 10`, `failureThreshold: 3`
    - Set `strategy.type: RollingUpdate` with `maxUnavailable: 0`, `maxSurge: 1`
    - Reference `serviceAccountName: autocare-sa`
    - The `<TAG>` placeholder is replaced by the CI `update-manifests` job on each deploy via `sed`
    - _Requirements: 6.1, 6.4, 6.8, 7.2, 7.4, 7.5, 7.6, 12.1_
  - [x] 14.2 Write `autocare-infra/k8s/deployments/vehicle-maintenance-service.yaml`
    - Define `Deployment` with `replicas: 2`, container image placeholder, port 8081
    - Set resource requests/limits: CPU 250m/500m, memory 512Mi/768Mi
    - Inject env vars from ConfigMap and ExternalSecret-generated Secret (same pattern as auth service, `DB_NAME: maintenance_db`)
    - Configure liveness and readiness probes: HTTP GET `/actuator/health`, port 8081, `initialDelaySeconds: 30`, `periodSeconds: 10`, `failureThreshold: 3`
    - Set rolling update strategy with `maxUnavailable: 0`, `maxSurge: 1`
    - _Requirements: 6.2, 6.4, 6.8, 7.1, 7.4, 7.5, 7.6, 12.2_
  - [x] 14.3 Write `autocare-infra/k8s/deployments/vehicle-maintenance-ui.yaml`
    - Define `Deployment` with `replicas: 2`, container image placeholder, port 3000
    - Set resource requests/limits: CPU 100m/200m, memory 128Mi/256Mi
    - Inject `VITE_AUTH_API_URL` and `VITE_MAINTENANCE_API_URL` from ConfigMap
    - Configure liveness and readiness probes: HTTP GET `/`, port 3000, `initialDelaySeconds: 30`, `periodSeconds: 10`, `failureThreshold: 3`
    - Set rolling update strategy with `maxUnavailable: 0`, `maxSurge: 1`
    - _Requirements: 6.3, 6.4, 6.8, 7.3, 7.4, 7.5, 7.6, 12.3, 14.2_

- [x] 15. Create Kubernetes Service manifests
  - [x] 15.1 Write `autocare-infra/k8s/services/user-auth-service.yaml`
    - Define `Service` of type `ClusterIP`, selector matching the auth deployment pods, port 8080 → targetPort 8080
    - _Requirements: 6.5_
  - [x] 15.2 Write `autocare-infra/k8s/services/vehicle-maintenance-service.yaml`
    - Define `Service` of type `ClusterIP`, port 8081 → targetPort 8081
    - _Requirements: 6.6_
  - [x] 15.3 Write `autocare-infra/k8s/services/vehicle-maintenance-ui.yaml`
    - Define `Service` of type `ClusterIP`, port 3000 → targetPort 3000
    - _Requirements: 6.7_

- [x] 16. Create Kubernetes Ingress manifest
  - Write `autocare-infra/k8s/ingress/autocare-ingress.yaml`
  - Define `Ingress` with `kubernetes.io/ingress.class: alb` annotation, `alb.ingress.kubernetes.io/scheme: internet-facing`, `alb.ingress.kubernetes.io/target-type: ip`, `alb.ingress.kubernetes.io/certificate-arn` annotation referencing the ACM cert ARN placeholder
  - Add `alb.ingress.kubernetes.io/listen-ports: '[{"HTTP":80},{"HTTPS":443}]'` and `alb.ingress.kubernetes.io/ssl-redirect: "443"` for HTTP→HTTPS 301 redirect
  - Define routing rules: `/api/auth/` → `user-auth-service:8080`, `/api/v1/` → `vehicle-maintenance-service:8081`, `/` (default) → `vehicle-maintenance-ui:3000`
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

- [x] 17. Create HPA manifests
  - [x] 17.1 Write `autocare-infra/k8s/hpa/user-auth-service-hpa.yaml`
    - Define `HorizontalPodAutoscaler` targeting the `user-auth-service` Deployment, `minReplicas: 2`, `maxReplicas: 6`, CPU utilization target 70%
    - Add `behavior.scaleDown.stabilizationWindowSeconds: 300` (5 minutes)
    - _Requirements: 9.1, 9.3, 9.4_
  - [x] 17.2 Write `autocare-infra/k8s/hpa/vehicle-maintenance-service-hpa.yaml`
    - Define `HorizontalPodAutoscaler` targeting the `vehicle-maintenance-service` Deployment with identical scaling parameters
    - _Requirements: 9.2, 9.3, 9.4_

- [x] 18. Create NetworkPolicy manifests
  - [x] 18.1 Write `autocare-infra/k8s/network-policies/ui-policy.yaml`
    - Define `NetworkPolicy` for pods with label `app: vehicle-maintenance-ui`
    - Allow egress only to pods labelled `app: user-auth-service` and `app: vehicle-maintenance-service` within the namespace, and to the ALB (CIDR-based or via `namespaceSelector`)
    - Deny all other egress
    - _Requirements: 13.4_
  - [x] 18.2 Write `autocare-infra/k8s/network-policies/backend-policy.yaml`
    - Define `NetworkPolicy` for pods with labels `app: user-auth-service` and `app: vehicle-maintenance-service`
    - Allow egress to RDS on port 3306 (CIDR of private subnets) and to the ALB
    - Deny all other egress
    - _Requirements: 13.5_

- [x] 19. Checkpoint — validate Kubernetes manifests
  - Run `kubeconform -kubernetes-version 1.29.0 k8s/` to validate all YAML against the Kubernetes 1.29 JSON schema
  - Run `kube-score score k8s/**/*.yaml` to verify resource limits, probes, and security contexts are set
  - Run `conftest test k8s/` with OPA policies enforcing: resource limits present, no `hostNetwork: true`, no plaintext Secret `data:` fields, all Deployments have liveness and readiness probes
  - Ensure all tests pass, ask the user if questions arise.

- [x] 20. Install ArgoCD in the cluster and create the Application CRD
  - [x] 20.1 Write `autocare-infra/k8s/argocd/autocare-app.yaml` — ArgoCD Application CRD
    - Define an ArgoCD `Application` resource in namespace `argocd` with `name: autocare`
    - Set `spec.source.repoURL` to the Git repository URL and `spec.source.path` to `k8s/`
    - Set `spec.destination.server: https://kubernetes.default.svc` and `spec.destination.namespace: autocare`
    - Enable automated sync policy: `automated.selfHeal: true`, `automated.prune: true`
    - Enable health assessment so ArgoCD monitors Deployment rollout status and triggers auto-rollback on failure
    - _Requirements: 10.3, 6.8_
  - [x] 20.2 Add ArgoCD installation step to cluster bootstrap documentation
    - Document the one-time bootstrap command to install ArgoCD into the `argocd` namespace:
      `kubectl create namespace argocd`
      `kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml`
    - Document applying the `k8s/argocd/autocare-app.yaml` Application CRD after ArgoCD is running
    - This is a one-time cluster bootstrap step, not repeated on every deploy
    - _Requirements: 2.5, 10.3_

- [x] 21. Write GitHub Actions CI/CD pipeline
  - [x] 21.1 Write `autocare-infra/.github/workflows/deploy.yml` — `test` job
    - Trigger on `push` to `main` branch
    - Define `test` job: checkout code, set up JDK 17, run `mvn test` for `autocare/user-auth-service` and `autocare/vehicle-maintenance-service` in sequence
    - Fail the workflow if any test fails (default Maven behaviour)
    - _Requirements: 10.6_
  - [x] 21.2 Write `deploy.yml` — `build-and-push` job
    - Define `build-and-push` job with `needs: test`
    - Configure AWS credentials step using `aws-actions/configure-aws-credentials` with OIDC role assumption (no long-lived keys); the role requires only ECR permissions — no `eks:DescribeCluster`
    - Log in to ECR using `aws-actions/amazon-ecr-login`
    - Build and push Docker images for all three services tagged with `${{ github.sha }}`
    - For `vehicle-maintenance-ui`, pass `VITE_AUTH_API_URL` and `VITE_MAINTENANCE_API_URL` as `--build-arg` values
    - Fail the workflow if any build or push step fails
    - _Requirements: 10.1, 10.2, 10.4, 10.5, 14.1_
  - [x] 21.3 Write `deploy.yml` — `update-manifests` job (replaces the old `deploy` job)
    - Define `update-manifests` job with `needs: build-and-push`
    - Use `sed` to replace the image tag placeholder in each `k8s/deployments/*.yaml` with `${{ github.sha }}`
    - Commit the updated manifests back to the repository using `git config`, `git add k8s/deployments/`, `git commit`, and `git push`
    - Use a GitHub token or a dedicated deploy key with write access to the repository for the push
    - Do NOT configure `kubectl`, do NOT run `aws eks update-kubeconfig`, do NOT call any EKS API — ArgoCD handles the cluster sync
    - Fail the workflow if the `sed` substitution or `git push` step fails; the cluster state is unaffected because ArgoCD will continue serving the previous manifest revision
    - _Requirements: 10.3, 10.4, 10.5_

- [x] 22. Write Terraform CI validation workflow
  - Write `autocare-infra/.github/workflows/terraform-validate.yml`
  - Trigger on `pull_request` targeting `main`
  - Steps: checkout, setup Terraform, `terraform init -backend=false`, `terraform validate`, `tflint --recursive`, `checkov -d infra/ --framework terraform`
  - _Requirements: 10.4_

- [x] 23. Write Kubernetes manifest CI validation workflow
  - Write `autocare-infra/.github/workflows/k8s-validate.yml`
  - Trigger on `pull_request` targeting `main`
  - Steps: checkout, install `kubeconform`, `kube-score`, `conftest`; run `kubeconform`, `kube-score`, and `conftest` against `k8s/`
  - _Requirements: 10.4_

- [x] 24. Final checkpoint — end-to-end wiring review
  - Verify that every Deployment manifest references the correct ConfigMap names and Secret keys
  - Verify that the Ingress path prefixes match the Service names and ports defined in the Service manifests
  - Verify that the HPA `scaleTargetRef` names match the Deployment names exactly
  - Verify that the ExternalSecret `refreshInterval` is set to `1h`
  - Verify that no Kubernetes manifest contains a plaintext `data:` field in a `Secret` resource
  - Verify that the ArgoCD `Application` CRD in `k8s/argocd/autocare-app.yaml` points to the correct repository URL and `k8s/` path
  - Verify that the CI/CD IAM role policy contains no `eks:*` permissions
  - Verify that `deploy.yml` contains no `kubectl` steps and no `aws eks update-kubeconfig` step
  - Ensure all tests pass, ask the user if questions arise.

---

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP (none in this plan — all tasks are required for a production-grade deployment)
- Each task references specific requirements for traceability
- Checkpoints at tasks 10, 19, and 24 ensure incremental validation before proceeding
- **GitOps delivery**: GitHub Actions CI only builds images and updates image tags in `k8s/deployments/*.yaml` via `sed` + `git commit`. ArgoCD (running in the `argocd` namespace) detects the commit and syncs the cluster. The CI role never holds EKS credentials.
- **ArgoCD health monitoring**: ArgoCD monitors Deployment rollout health using pod readiness probes. If health checks fail after a sync, ArgoCD automatically rolls back to the previous Git revision — no `kubectl rollout status` step is needed in CI.
- **Reduced CI blast radius**: The CI/CD IAM role requires only ECR push permissions (`ecr:*`) and Git write access. `eks:DescribeCluster` and all `kubectl` credentials are removed from the pipeline.
- The design document's testing strategy (Layer 1: Terraform static analysis, Layer 2: Kubernetes manifest validation, Layer 3: integration/smoke tests) is reflected in tasks 10, 19, 22, and 23
- Actual secret values (`JWT_SECRET`, `DB_PASSWORD`) must be populated in AWS Secrets Manager out-of-band; Terraform manages the secret resources but not the plaintext values committed to source control
- The `<ECR_URL>` and `<TAG>` placeholders in Deployment manifests are replaced at deploy time by the CI `update-manifests` job (task 21.3)
- ArgoCD installation (task 20.2) is a one-time cluster bootstrap step performed after `terraform apply` provisions the EKS cluster
