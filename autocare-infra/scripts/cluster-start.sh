#!/usr/bin/env bash
# cluster-start.sh — Start RDS and scale EKS node group back up
# Usage: ./scripts/cluster-start.sh [cluster-name] [region] [desired-nodes]
#
# Defaults read from terraform.tfvars if not provided.

set -euo pipefail

CLUSTER_NAME="${1:-autocare-eks}"
REGION="${2:-us-west-2}"
DESIRED_NODES="${3:-2}"
RDS_IDENTIFIER="${CLUSTER_NAME}-mysql"
# Align with Terraform node group min/max when using cost-optimised single-node (e.g. min=1)
CLUSTER_MIN_NODES="${CLUSTER_MIN_NODES:-2}"
CLUSTER_MAX_NODES="${CLUSTER_MAX_NODES:-4}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Autocare Dev Cluster — START"
echo "  Cluster : $CLUSTER_NAME"
echo "  Region  : $REGION"
echo "  Nodes   : $DESIRED_NODES"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 1. Start RDS first (takes longer) ────────────────────────────────────────
echo ""
echo "▶ Checking RDS instance $RDS_IDENTIFIER..."
RDS_STATUS=$(aws rds describe-db-instances \
  --db-instance-identifier "$RDS_IDENTIFIER" \
  --region "$REGION" \
  --query "DBInstances[0].DBInstanceStatus" \
  --output text 2>/dev/null || echo "not-found")

case "$RDS_STATUS" in
  stopped)
    echo "  Starting RDS..."
    aws rds start-db-instance \
      --db-instance-identifier "$RDS_IDENTIFIER" \
      --region "$REGION" \
      --query "DBInstance.DBInstanceStatus" \
      --output text
    echo "  ✓ RDS start requested — waiting for available status..."
    aws rds wait db-instance-available \
      --db-instance-identifier "$RDS_IDENTIFIER" \
      --region "$REGION"
    echo "  ✓ RDS is available"
    ;;
  available)
    echo "  RDS already running — skipping"
    ;;
  not-found)
    echo "  RDS instance not found — skipping"
    ;;
  *)
    echo "  RDS status is '$RDS_STATUS' — waiting for it to stabilise..."
    aws rds wait db-instance-available \
      --db-instance-identifier "$RDS_IDENTIFIER" \
      --region "$REGION"
    echo "  ✓ RDS is available"
    ;;
esac

# ── 2. Get node group name ────────────────────────────────────────────────────
echo ""
echo "▶ Fetching node group name..."
NODE_GROUP=$(aws eks list-nodegroups \
  --cluster-name "$CLUSTER_NAME" \
  --region "$REGION" \
  --query "nodegroups[0]" \
  --output text)

if [[ -z "$NODE_GROUP" || "$NODE_GROUP" == "None" ]]; then
  echo "  ✗ No node group found for cluster $CLUSTER_NAME"
  exit 1
fi
echo "  Node group: $NODE_GROUP"

# ── 3. Scale node group up ────────────────────────────────────────────────────
echo ""
echo "▶ Scaling node group to $DESIRED_NODES nodes..."
CURRENT=$(aws eks describe-nodegroup \
  --cluster-name "$CLUSTER_NAME" \
  --nodegroup-name "$NODE_GROUP" \
  --region "$REGION" \
  --query "nodegroup.scalingConfig.desiredSize" \
  --output text)

if [[ "$CURRENT" -ge "$DESIRED_NODES" ]]; then
  echo "  Node group already has $CURRENT nodes — skipping"
else
  aws eks update-nodegroup-config \
    --cluster-name "$CLUSTER_NAME" \
    --nodegroup-name "$NODE_GROUP" \
    --region "$REGION" \
    --scaling-config "minSize=${CLUSTER_MIN_NODES},maxSize=${CLUSTER_MAX_NODES},desiredSize=${DESIRED_NODES}" \
    --query "update.id" \
    --output text
  echo "  ✓ Scale-up requested — waiting for nodes to be ready..."

  # Wait for nodes to join the cluster
  echo "  Waiting for $DESIRED_NODES node(s) to reach Ready state (~3-5 min)..."
  ATTEMPTS=0
  MAX_ATTEMPTS=30
  while [[ $ATTEMPTS -lt $MAX_ATTEMPTS ]]; do
    READY=$(aws eks describe-nodegroup \
      --cluster-name "$CLUSTER_NAME" \
      --nodegroup-name "$NODE_GROUP" \
      --region "$REGION" \
      --query "nodegroup.status" \
      --output text)
    if [[ "$READY" == "ACTIVE" ]]; then
      echo "  ✓ Node group is ACTIVE"
      break
    fi
    echo "  Status: $READY — waiting 10s..."
    sleep 10
    ATTEMPTS=$((ATTEMPTS + 1))
  done

  if [[ $ATTEMPTS -eq $MAX_ATTEMPTS ]]; then
    echo "  ⚠ Timed out waiting for node group — check AWS console"
  fi
fi

# ── 4. Update kubeconfig ──────────────────────────────────────────────────────
echo ""
echo "▶ Updating kubeconfig..."
aws eks update-kubeconfig \
  --name "$CLUSTER_NAME" \
  --region "$REGION" \
  --alias "$CLUSTER_NAME"
echo "  ✓ kubeconfig updated"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✓ Cluster is ready"
echo ""
echo "  Check pods:  kubectl get pods -n autocare"
echo "  ArgoCD sync: kubectl get app autocare -n argocd"
echo ""
echo "  If pods still crash on DB/JWT after nodes are up, refresh secrets + rollouts:"
echo "    ./scripts/05-reconcile-autocare.sh secrets"
echo ""
echo "  To stop:  ./scripts/cluster-stop.sh $CLUSTER_NAME $REGION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
