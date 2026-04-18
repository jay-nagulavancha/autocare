# Monthly Cost Estimate — Autocare Dev Environment (us-west-2)

All prices are AWS us-west-2 on-demand rates as of 2025.
Actual costs vary by usage. Use [AWS Pricing Calculator](https://calculator.aws) for exact quotes.

---

## Without Auto-Shutdown (always on)

| Resource | Config | $/hr | Hours/mo | $/mo |
|---|---|---|---|---|
| EKS Control Plane | 1 cluster | $0.10 | 730 | **$73.00** |
| EC2 Nodes | 2× t3.medium | $0.0416 each | 730 | **$60.74** |
| RDS MySQL | db.t3.micro Multi-AZ | $0.034 | 730 | **$24.82** |
| NAT Gateway | 1× | $0.045 + data | 730 | **~$33.00** |
| ALB | 1× (provisioned by ingress) | $0.008 + LCU | 730 | **~$16.00** |
| ECR Storage | 3 repos × ~500MB | $0.10/GB | — | **~$0.15** |
| Secrets Manager | 3 secrets | $0.40/secret | — | **$1.20** |
| CloudWatch Logs | ~1GB/mo | $0.50/GB | — | **~$0.50** |
| ACM Certificate | 1× | Free | — | **$0.00** |
| **Total (always on)** | | | | **~$209/mo** |

---

## With Auto-Shutdown (dev usage pattern)

Assumes the cluster is actively used **4 hours/day, 5 days/week** = ~87 hours/month.
The remaining ~643 hours/month the nodes and RDS are stopped.

| Resource | Config | Active hrs | Stopped hrs | $/mo |
|---|---|---|---|---|
| EKS Control Plane | 1 cluster | 730 (can't stop) | — | **$73.00** |
| EC2 Nodes | 2× t3.medium | 87 hrs | 643 hrs (free) | **$7.24** |
| RDS MySQL | db.t3.micro Multi-AZ | 87 hrs | 643 hrs (free) | **$2.96** |
| NAT Gateway | 1× | 730 hrs | — | **~$33.00** |
| ALB | 1× | ~87 hrs active | — | **~$2.00** |
| Lambda (auto-shutdown) | 144 invocations/day | — | — | **~$0.00** |
| EventBridge | 144 rules/day | — | — | **~$0.00** |
| ECR + Secrets + CW | same | — | — | **~$2.00** |
| **Total (auto-shutdown)** | | | | **~$120/mo** |

**Saving: ~$89/mo (~43% reduction)**

> Note: EKS control plane ($73/mo) is the dominant cost and cannot be paused.
> If you want to eliminate it entirely for dev, consider using a local cluster
> (kind, minikube) or a single shared EKS cluster across multiple environments.

---

## Further Savings Options

| Option | Additional Saving | Trade-off |
|---|---|---|
| Single NAT Gateway (already using 1) | — | Already optimised |
| Spot instances for nodes | ~70% off EC2 | Nodes can be interrupted |
| t3.small nodes instead of t3.medium | ~50% off EC2 | May OOM Spring Boot pods |
| Delete NAT when stopped | ~$33/mo | Recreate takes ~2 min on start |
| Share EKS cluster across dev/staging | ~$73/mo | More complex namespace isolation |
| Use RDS t3.micro single-AZ for dev | ~50% off RDS | No HA (fine for dev) |

### Terraform toggles (`autocare-infra/infra/terraform.tfvars`)

These are implemented in Terraform (see `variables.tf` and `terraform.tfvars.example`):

| Variable | Effect |
|---|---|
| `eks_single_az_nodes` | Node group uses only the first private subnet (single-AZ workers). |
| `eks_node_instance_types` | e.g. `["t3.small"]` instead of `t3.medium`. |
| `eks_node_desired_size` / `min` / `max` | Smaller floor (e.g. 1 node) for dev. |
| `rds_multi_az` | `false` removes the standby instance (large RDS saving). |
| `rds_backup_retention_days` | Lower (e.g. 1) reduces backup storage cost. |

After setting `eks_node_min_size = 1`, run `cluster-start.sh` with `CLUSTER_MIN_NODES=1` and `CLUSTER_MAX_NODES` matching Terraform max.

---

## Auto-Shutdown Behaviour

The Lambda runs every **10 minutes** and checks ALB `RequestCount` over the last **30 minutes**.

```
Every 10 min:
  IF ALB requests in last 30min == 0
    AND node group desiredSize > 0
  THEN
    Scale node group → 0 (EC2 billing stops)
    Stop RDS instance (RDS billing stops)
```

**Start-up time after auto-shutdown:**
- RDS: ~2-3 minutes to become available
- EKS nodes: ~3-5 minutes to join and become Ready
- ArgoCD re-sync: ~1 minute
- **Total: ~5-8 minutes from cold**

Use `./scripts/cluster-start.sh` or the GitHub Actions "Cluster Control" workflow to wake it up.

---

## Single Command Reference

```bash
# Stop everything (scale nodes to 0, stop RDS)
./autocare-infra/scripts/cluster-stop.sh autocare-eks us-west-2

# Start everything (start RDS, scale nodes up, update kubeconfig)
./autocare-infra/scripts/cluster-start.sh autocare-eks us-west-2

# Or via GitHub Actions UI:
# Actions → "Cluster Control (Start / Stop)" → Run workflow → choose start/stop
```

---

## Enabling Auto-Shutdown via Terraform

In `autocare-infra/infra/terraform.tfvars`:

```hcl
enable_auto_shutdown        = true
auto_shutdown_idle_minutes  = 30   # default, adjust as needed
```

Then `terraform apply` to deploy the Lambda + EventBridge rule.

To disable (e.g. before a demo or production promotion):

```hcl
enable_auto_shutdown = false
```
