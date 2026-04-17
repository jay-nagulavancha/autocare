# Requirements Document

## Introduction

This document defines the requirements for deploying the Autocare Vehicle Maintenance System to AWS Elastic Kubernetes Service (EKS). The system consists of three application services — `user-auth-service` (Spring Boot, port 8080), `vehicle-maintenance-service` (Spring Boot, port 8081), and `vehicle-maintenance-ui` (React/Vite, port 3000) — backed by a MySQL database with two schemas (`auth_db`, `maintenance_db`). The current local development setup uses Docker Compose. This deployment targets a production-grade AWS EKS cluster with managed MySQL (Amazon RDS), secure secret management, public ingress, and automated image delivery via CI/CD.

## Glossary

- **EKS**: Amazon Elastic Kubernetes Service — managed Kubernetes control plane on AWS.
- **ECR**: Amazon Elastic Container Registry — managed Docker image registry on AWS.
- **RDS**: Amazon Relational Database Service — managed MySQL instance on AWS.
- **Secrets_Manager**: AWS Secrets Manager — service for storing and rotating sensitive credentials.
- **ALB**: AWS Application Load Balancer — Layer 7 load balancer used as the Kubernetes ingress controller.
- **ALB_Ingress_Controller**: The AWS Load Balancer Controller running inside EKS that provisions ALB resources from Kubernetes Ingress objects.
- **Namespace**: A Kubernetes namespace used to logically isolate Autocare workloads within the cluster.
- **Deployment**: A Kubernetes Deployment resource managing replica sets for a service.
- **Service**: A Kubernetes Service resource providing stable internal DNS and load balancing for pods.
- **Ingress**: A Kubernetes Ingress resource that configures the ALB_Ingress_Controller to route external HTTP/HTTPS traffic.
- **ConfigMap**: A Kubernetes resource holding non-sensitive environment configuration.
- **Secret**: A Kubernetes resource holding sensitive configuration values (sourced from Secrets_Manager).
- **HPA**: Horizontal Pod Autoscaler — Kubernetes resource that scales pod replicas based on CPU/memory metrics.
- **CI_CD_Pipeline**: An automated pipeline (e.g. GitHub Actions) that builds, tags, pushes Docker images to ECR, and applies Kubernetes manifests.
- **JWT_Secret**: The shared HS256 signing key used by both `user-auth-service` and `vehicle-maintenance-service` to sign and validate JSON Web Tokens.
- **Health_Probe**: A Kubernetes liveness or readiness probe that checks whether a pod is alive and ready to serve traffic.
- **Node_Group**: A managed group of EC2 worker nodes in EKS.
- **IAM_Role**: An AWS Identity and Access Management role granting permissions to AWS services or Kubernetes service accounts.
- **IRSA**: IAM Roles for Service Accounts — mechanism that binds an IAM_Role to a Kubernetes service account for fine-grained pod-level AWS permissions.
- **TLS_Certificate**: An SSL/TLS certificate provisioned via AWS Certificate Manager (ACM) and attached to the ALB for HTTPS termination.
- **VPC**: AWS Virtual Private Cloud — isolated network containing all EKS and RDS resources.
- **Private_Subnet**: A VPC subnet with no direct internet route, used for RDS and worker nodes.
- **Public_Subnet**: A VPC subnet with an internet gateway route, used for the ALB.

---

## Requirements

### Requirement 1: Container Image Registry

**User Story:** As a platform engineer, I want all Autocare service images stored in a private AWS registry, so that EKS can pull images securely without exposing them publicly.

#### Acceptance Criteria

1. THE ECR SHALL provide one private repository per service: `autocare/user-auth-service`, `autocare/vehicle-maintenance-service`, and `autocare/vehicle-maintenance-ui`.
2. WHEN a CI_CD_Pipeline build completes successfully, THE CI_CD_Pipeline SHALL push a tagged Docker image to the corresponding ECR repository using the Git commit SHA as the image tag.
3. THE ECR SHALL enforce image tag immutability so that an existing tag cannot be overwritten.
4. WHEN an image is pushed to ECR, THE ECR SHALL scan the image for known vulnerabilities and report findings.
5. THE ECR SHALL retain a maximum of 30 tagged images per repository, removing older images automatically.

---

### Requirement 2: EKS Cluster Provisioning

**User Story:** As a platform engineer, I want a managed EKS cluster provisioned in AWS, so that Autocare workloads run on a reliable, scalable Kubernetes control plane.

#### Acceptance Criteria

1. THE EKS cluster SHALL run Kubernetes version 1.29 or later.
2. THE EKS cluster SHALL be deployed inside a dedicated VPC with at least two availability zones.
3. THE Node_Group SHALL consist of at least 2 worker nodes of instance type `t3.medium` or larger to satisfy the memory requirements of the Spring Boot services.
4. THE Node_Group SHALL use Private_Subnet placement so that worker nodes are not directly reachable from the internet.
5. THE EKS cluster SHALL have the AWS Load Balancer Controller installed to manage ALB resources from Kubernetes Ingress objects.
6. THE EKS cluster SHALL have the Metrics Server installed to enable HPA CPU and memory metrics collection.
7. WHEN a worker node becomes unhealthy, THE Node_Group SHALL automatically replace it without manual intervention.

---

### Requirement 3: Namespace and Workload Isolation

**User Story:** As a platform engineer, I want all Autocare workloads isolated in a dedicated Kubernetes namespace, so that they are logically separated from other cluster tenants.

#### Acceptance Criteria

1. THE EKS cluster SHALL contain a Kubernetes Namespace named `autocare` that holds all Autocare Deployments, Services, ConfigMaps, Secrets, and Ingress resources.
2. THE Namespace SHALL have resource quotas applied to prevent a single service from consuming all cluster CPU and memory.

---

### Requirement 4: Managed MySQL Database (RDS)

**User Story:** As a platform engineer, I want the MySQL database hosted on Amazon RDS, so that the database is managed, backed up, and highly available without manual administration.

#### Acceptance Criteria

1. THE RDS instance SHALL run MySQL 8.0 and be deployed in a Multi-AZ configuration for high availability.
2. THE RDS instance SHALL be placed in Private_Subnet so that it is not directly accessible from the internet.
3. THE RDS instance SHALL accept connections only from the EKS worker node security group on port 3306.
4. THE RDS instance SHALL have automated daily backups with a retention period of at least 7 days.
5. WHEN the Autocare system is first deployed, THE RDS instance SHALL have both `auth_db` and `maintenance_db` schemas created and seeded using the existing `init.sql` scripts.
6. THE RDS instance SHALL use a db.t3.micro or larger instance class.

---

### Requirement 5: Secret Management

**User Story:** As a platform engineer, I want all sensitive credentials stored in AWS Secrets Manager and injected into pods at runtime, so that secrets are never hard-coded in container images or Kubernetes manifests committed to source control.

#### Acceptance Criteria

1. THE Secrets_Manager SHALL store the following credentials as individual secrets: `JWT_SECRET`, `DB_PASSWORD`, and the RDS endpoint hostname.
2. WHEN a pod starts, THE Secrets_Manager SHALL supply secret values to the pod as environment variables via the Kubernetes External Secrets Operator or the AWS Secrets and Configuration Provider (ASCP).
3. THE IAM_Role bound to the `autocare` Namespace service account via IRSA SHALL have read-only access to only the Autocare secrets in Secrets_Manager.
4. IF a secret value is rotated in Secrets_Manager, THEN THE pod SHALL receive the updated value within 1 hour without requiring a manual deployment.
5. THE Kubernetes manifests committed to source control SHALL contain no plaintext secret values.

---

### Requirement 6: Kubernetes Deployments and Services

**User Story:** As a platform engineer, I want each Autocare service deployed as a Kubernetes Deployment with a corresponding Service, so that pods are managed declaratively and reachable within the cluster.

#### Acceptance Criteria

1. THE Deployment for `user-auth-service` SHALL run a minimum of 2 pod replicas and expose container port 8080.
2. THE Deployment for `vehicle-maintenance-service` SHALL run a minimum of 2 pod replicas and expose container port 8081.
3. THE Deployment for `vehicle-maintenance-ui` SHALL run a minimum of 2 pod replicas and expose container port 3000.
4. WHEN a pod starts, THE Deployment SHALL inject the following environment variables from ConfigMap and Secret resources: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `JWT_EXPIRATION_MS` for backend services; `VITE_AUTH_API_URL` and `VITE_MAINTENANCE_API_URL` for the UI service.
5. THE Service for `user-auth-service` SHALL be of type `ClusterIP` and route traffic to port 8080.
6. THE Service for `vehicle-maintenance-service` SHALL be of type `ClusterIP` and route traffic to port 8081.
7. THE Service for `vehicle-maintenance-ui` SHALL be of type `ClusterIP` and route traffic to port 3000.
8. WHEN a new image tag is deployed, THE Deployment SHALL perform a rolling update with zero downtime, ensuring at least one pod remains available throughout the rollout.

---

### Requirement 7: Health Probes

**User Story:** As a platform engineer, I want liveness and readiness probes configured for each service, so that Kubernetes can detect unhealthy pods and route traffic only to ready pods.

#### Acceptance Criteria

1. THE Health_Probe for `vehicle-maintenance-service` SHALL use the existing `/actuator/health` endpoint for both liveness and readiness checks on port 8081.
2. THE Health_Probe for `user-auth-service` SHALL use an HTTP GET check on `/api/auth/signin` returning a non-5xx response, or a TCP socket check on port 8080, for liveness and readiness.
3. THE Health_Probe for `vehicle-maintenance-ui` SHALL use an HTTP GET check on port 3000 returning HTTP 200 for liveness and readiness.
4. WHEN a liveness probe fails 3 consecutive times, THE Deployment SHALL restart the affected pod automatically.
5. WHEN a readiness probe fails, THE Service SHALL remove the affected pod from its endpoint list until the probe succeeds again.
6. THE Health_Probe for each service SHALL have an `initialDelaySeconds` of at least 30 seconds to allow Spring Boot startup to complete before probing begins.

---

### Requirement 8: Ingress and External Access

**User Story:** As an end user, I want to access the Autocare UI and APIs over HTTPS through a single public load balancer, so that traffic is encrypted and routed correctly to each service.

#### Acceptance Criteria

1. THE Ingress SHALL be managed by the ALB_Ingress_Controller and provision a single internet-facing ALB in Public_Subnet.
2. THE ALB SHALL terminate TLS using a TLS_Certificate provisioned in AWS Certificate Manager for the configured domain name.
3. THE Ingress SHALL route requests with path prefix `/api/auth/` to the `user-auth-service` Service on port 8080.
4. THE Ingress SHALL route requests with path prefix `/api/v1/` to the `vehicle-maintenance-service` Service on port 8081.
5. THE Ingress SHALL route all remaining requests to the `vehicle-maintenance-ui` Service on port 3000.
6. WHEN a client sends an HTTP request to port 80, THE ALB SHALL redirect the request to HTTPS on port 443 with a 301 status code.
7. THE ALB SHALL return HTTP 502 or 503 with a meaningful error body WHEN no healthy backend pods are available.

---

### Requirement 9: Horizontal Pod Autoscaling

**User Story:** As a platform engineer, I want the backend services to scale pod replicas automatically based on CPU load, so that the system handles traffic spikes without manual intervention.

#### Acceptance Criteria

1. THE HPA for `user-auth-service` SHALL scale between 2 and 6 replicas when average CPU utilization exceeds 70%.
2. THE HPA for `vehicle-maintenance-service` SHALL scale between 2 and 6 replicas when average CPU utilization exceeds 70%.
3. WHEN CPU utilization drops below the target threshold for 5 consecutive minutes, THE HPA SHALL scale down replicas to the minimum.
4. THE HPA SHALL require the Metrics Server to be running in the cluster to function.

---

### Requirement 10: CI/CD Pipeline

**User Story:** As a developer, I want a CI/CD pipeline that automatically builds, pushes, and deploys updated service images to EKS on every merge to the main branch, so that deployments are repeatable and do not require manual steps.

#### Acceptance Criteria

1. WHEN a commit is merged to the `main` branch, THE CI_CD_Pipeline SHALL build Docker images for all three services using the existing Dockerfiles.
2. WHEN all images are built successfully, THE CI_CD_Pipeline SHALL push the images to ECR tagged with the Git commit SHA.
3. WHEN images are pushed to ECR, THE CI_CD_Pipeline SHALL update the image tag in the Kubernetes Deployment manifests and apply them to the EKS cluster using `kubectl`.
4. IF any build or push step fails, THEN THE CI_CD_Pipeline SHALL halt and report the failure without applying any Kubernetes changes.
5. THE CI_CD_Pipeline SHALL authenticate to ECR and EKS using an IAM_Role with least-privilege permissions, without storing long-lived AWS access keys in the pipeline environment.
6. THE CI_CD_Pipeline SHALL run the existing Maven test suites for `user-auth-service` and `vehicle-maintenance-service` before building production images, and SHALL halt if any test fails.

---

### Requirement 11: Logging and Observability

**User Story:** As a platform engineer, I want application logs from all pods centralized in AWS CloudWatch, so that I can diagnose issues without needing direct pod access.

#### Acceptance Criteria

1. THE EKS cluster SHALL have the AWS CloudWatch Container Insights agent (Fluent Bit DaemonSet) deployed to collect stdout/stderr logs from all pods in the `autocare` Namespace.
2. WHEN a pod writes a log line to stdout, THE CloudWatch agent SHALL deliver the log to a CloudWatch Log Group named `/autocare/<service-name>` within 60 seconds.
3. THE CloudWatch Log Group for each service SHALL have a retention policy of 30 days.
4. THE EKS cluster SHALL emit cluster-level metrics (CPU, memory, network) to CloudWatch Container Insights for the `autocare` Namespace.

---

### Requirement 12: Resource Requests and Limits

**User Story:** As a platform engineer, I want CPU and memory requests and limits defined for every container, so that the Kubernetes scheduler can place pods correctly and prevent noisy-neighbour resource exhaustion.

#### Acceptance Criteria

1. THE Deployment for `user-auth-service` SHALL specify a CPU request of 250m, a CPU limit of 500m, a memory request of 512Mi, and a memory limit of 768Mi per container.
2. THE Deployment for `vehicle-maintenance-service` SHALL specify a CPU request of 250m, a CPU limit of 500m, a memory request of 512Mi, and a memory limit of 768Mi per container.
3. THE Deployment for `vehicle-maintenance-ui` SHALL specify a CPU request of 100m, a CPU limit of 200m, a memory request of 128Mi, and a memory limit of 256Mi per container.
4. IF a container exceeds its memory limit, THEN Kubernetes SHALL terminate and restart the container automatically.

---

### Requirement 13: Network Security

**User Story:** As a platform engineer, I want network policies and security groups restricting inter-service traffic to only what is necessary, so that the attack surface is minimized.

#### Acceptance Criteria

1. THE VPC security group for the RDS instance SHALL allow inbound TCP traffic on port 3306 only from the EKS worker node security group.
2. THE VPC security group for EKS worker nodes SHALL allow inbound traffic on ports 8080, 8081, and 3000 only from within the VPC CIDR.
3. THE ALB security group SHALL allow inbound TCP traffic on ports 80 and 443 from `0.0.0.0/0`.
4. THE Kubernetes Namespace SHALL have a NetworkPolicy that allows `vehicle-maintenance-ui` pods to communicate only with the ALB and with `user-auth-service` and `vehicle-maintenance-service` pods.
5. THE Kubernetes Namespace SHALL have a NetworkPolicy that allows `user-auth-service` and `vehicle-maintenance-service` pods to communicate only with the RDS instance on port 3306 and with the ALB.

---

### Requirement 14: UI Build-Time Configuration

**User Story:** As a platform engineer, I want the React UI built with the correct production API URLs baked in at image build time, so that the frontend communicates with the live EKS-hosted backend services.

#### Acceptance Criteria

1. WHEN the CI_CD_Pipeline builds the `vehicle-maintenance-ui` Docker image, THE CI_CD_Pipeline SHALL pass `VITE_AUTH_API_URL` and `VITE_MAINTENANCE_API_URL` as Docker build arguments pointing to the production ALB HTTPS endpoints.
2. THE built `vehicle-maintenance-ui` image SHALL serve the compiled static assets via a production-grade web server (e.g. Nginx) rather than the Vite development server.
3. THE Nginx configuration inside the `vehicle-maintenance-ui` container SHALL proxy `/api/auth/` requests to `user-auth-service` and `/api/v1/` requests to `vehicle-maintenance-service` as a fallback, or rely on the ALB Ingress routing defined in Requirement 8.
