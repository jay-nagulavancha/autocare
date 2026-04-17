#!/usr/bin/env bash
# cluster-stop.sh — Scale EKS node group to 0 and stop RDS
# Usage: ./scripts/cluster-stop.sh [cluster-name] [region]
#
# Defaults read from terraform.tfvars if not provided.

set -euo pipefail

CLUSTER_NAME="${1:-autocare-eks}"
REGION="${2:-us-west-2}"
RDS_IDENTIFIER="${CLUSTER_NAME}-mysql"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Autocare Dev Cluster — STOP"
echo "  Cluster : $CLUSTER_NAME"
echo "  Region  : $REGION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 1. Get node group name ────────────────────────────────────────────────────
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

# ── 2. Scale node group to 0 ─────────────────────────────────────────────────
echo ""
echo "▶ Scaling node group to 0..."
CURRENT=$(aws eks describe-nodegroup \
  --cluster-name "$CLUSTER_NAME" \
  --nodegroup-name "$NODE_GROUP" \
  --region "$REGION" \
  --query "nodegroup.scalingConfig.desiredSize" \
  --output text)

if [[ "$CURRENT" == "0" ]]; then
  echo "  Node group already at 0 — skipping"
else
  aws eks update-nodegroup-config \
    --cluster-name "$CLUSTER_NAME" \
    --nodegroup-name "$NODE_GROUP" \
    --region "$REGION" \
    --scaling-config minSize=0,maxSize=4,desiredSize=0 \
    --query "update.id" \
    --output text
  echo "  ✓ Scale-down requested (nodes will terminate in ~2-3 min)"
fi

# ── 3. Stop RDS ───────────────────────────────────────────────────────────────
echo ""
echo "▶ Checking RDS instance $RDS_IDENTIFIER..."
RDS_STATUS=$(aws rds describe-db-instances \
  --db-instance-identifier "$RDS_IDENTIFIER" \
  --region "$REGION" \
  --query "DBInstances[0].DBInstanceStatus" \
  --output text 2>/dev/null || echo "not-found")

case "$RDS_STATUS" in
  available)
    echo "  Stopping RDS..."
    aws rds stop-db-instance \
      --db-instance-identifier "$RDS_IDENTIFIER" \
      --region "$REGION" \
      --query "DBInstance.DBInstanceStatus" \
      --output text
    echo "  ✓ RDS stop requested (takes ~1-2 min)"
    ;;
  stopped)
    echo "  RDS already stopped — skipping"
    ;;
  not-found)
    echo "  RDS instance not found — skipping"
    ;;
  *)
    echo "  RDS status is '$RDS_STATUS' — cannot stop now, try again shortly"
    ;;
esac

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✓ Stop commands issued"
echo "  EKS control plane continues running"
echo "  (~\$0.10/hr — cannot be paused)"
echo ""
echo "  To start again:"
echo "  ./scripts/cluster-start.sh $CLUSTER_NAME $REGION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
