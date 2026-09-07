#!/usr/bin/env bash
set -euo pipefail

# NetworkPeer Infrastructure Health Check
# Usage: AWS_ACCESS_KEY_ID=xxx AWS_SECRET_ACCESS_KEY=xxx AWS_REGION=us-east-1 ./scripts/check-infra.sh

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; }
warn() { echo -e "${YELLOW}⚠${NC} $1"; }
info() { echo -e "  $1"; }

echo "============================================"
echo "  NetworkPeer Infrastructure Health Check"
echo "============================================"
echo ""

# --- 1. AWS Identity ---
echo "▸ AWS Identity"
if command -v aws &>/dev/null; then
  if identity=$(aws sts get-caller-identity 2>/dev/null); then
    account=$(echo "$identity" | python3 -c "import sys,json; print(json.load(sys.stdin)['Account'])" 2>/dev/null || echo "unknown")
    arn=$(echo "$identity" | python3 -c "import sys,json; print(json.load(sys.stdin)['Arn'])" 2>/dev/null || echo "unknown")
    pass "AWS Identity verified"
    info "Account: $account"
    info "ARN: $arn"
  else
    fail "AWS credentials invalid or not configured"
    info "Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY"
  fi
else
  warn "AWS CLI not installed"
  info "Install: curl 'https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip' -o 'awscliv2.zip' && unzip awscliv2.zip && sudo ./aws/install"
fi
echo ""

# --- 2. ECS Cluster ---
echo "▸ ECS Cluster"
if command -v aws &>/dev/null && aws sts get-caller-identity &>/dev/null; then
  clusters=$(aws ecs list-clusters --output text --query 'clusterArns' 2>/dev/null || echo "")
  if [ -n "$clusters" ] && [ "$clusters" != "None" ]; then
    pass "ECS clusters found"
    for cluster_arn in $clusters; do
      cluster_name=$(echo "$cluster_arn" | awk -F/ '{print $NF}')
      info "Cluster: $cluster_name"
      services=$(aws ecs list-services --cluster "$cluster_name" --output text --query 'serviceArns' 2>/dev/null || echo "")
      for svc_arn in $services; do
        svc_name=$(echo "$svc_arn" | awk -F/ '{print $NF}')
        status=$(aws ecs describe-services --cluster "$cluster_name" --services "$svc_name" --query 'services[0].{status:status,desired:desiredCount,running:runningCount}' --output text 2>/dev/null || echo "unknown")
        info "  Service: $svc_name — $status"
      done
    done
  else
    warn "No ECS clusters found (infrastructure may not be deployed)"
  fi
else
  warn "Skipping ECS check (no AWS credentials)"
fi
echo ""

# --- 3. RDS ---
echo "▸ RDS Database"
if command -v aws &>/dev/null && aws sts get-caller-identity &>/dev/null; then
  dbs=$(aws rds describe-db-instances --query 'DBInstances[?contains(DBInstanceIdentifier, `networkpeer`)].{id:DBInstanceIdentifier,status:DBInstanceStatus,engine:Engine}' --output text 2>/dev/null || echo "")
  if [ -n "$dbs" ] && [ "$dbs" != "None" ]; then
    pass "RDS instances found"
    echo "$dbs" | while read -r line; do info "  $line"; done
  else
    warn "No RDS instances found matching 'networkpeer'"
  fi
else
  warn "Skipping RDS check (no AWS credentials)"
fi
echo ""

# --- 4. ElastiCache ---
echo "▸ ElastiCache Redis"
if command -v aws &>/dev/null && aws sts get-caller-identity &>/dev/null; then
  caches=$(aws elasticache describe-cache-clusters --query 'CacheClusters[?contains(CacheClusterId, `networkpeer`)].{id:CacheClusterId,status:CacheClusterStatus,engine:Engine}' --output text 2>/dev/null || echo "")
  if [ -n "$caches" ] && [ "$caches" != "None" ]; then
    pass "ElastiCache clusters found"
    echo "$caches" | while read -r line; do info "  $line"; done
  else
    warn "No ElastiCache clusters found matching 'networkpeer'"
  fi
else
  warn "Skipping ElastiCache check (no AWS credentials)"
fi
echo ""

# --- 5. S3 ---
echo "▸ S3 Evidence Bucket"
if command -v aws &>/dev/null && aws sts get-caller-identity &>/dev/null; then
  buckets=$(aws s3 ls 2>/dev/null | grep -i "networkpeer\|evidence" || echo "")
  if [ -n "$buckets" ]; then
    pass "S3 buckets found"
    echo "$buckets" | while read -r line; do info "  $line"; done
  else
    warn "No S3 buckets found matching 'networkpeer' or 'evidence'"
  fi
else
  warn "Skipping S3 check (no AWS credentials)"
fi
echo ""

# --- 6. Cognito ---
echo "▸ Cognito User Pool"
if command -v aws &>/dev/null && aws sts get-caller-identity &>/dev/null; then
  pools=$(aws cognito-idp list-user-pools --max-results 10 --query 'UserPools[?contains(Name, `networkpeer`)].{id:Id,name:Name}' --output text 2>/dev/null || echo "")
  if [ -n "$pools" ] && [ "$pools" != "None" ]; then
    pass "Cognito user pools found"
    echo "$pools" | while read -r line; do info "  $line"; done
  else
    warn "No Cognito user pools found matching 'networkpeer'"
  fi
else
  warn "Skipping Cognito check (no AWS credentials)"
fi
echo ""

# --- 7. Web Frontend ---
echo "▸ Web Frontend"
web_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 10 --max-time 20 "https://network-peer-web.vercel.app" 2>/dev/null || echo "000")
if [ "$web_code" = "200" ]; then
  pass "Web frontend live — https://network-peer-web.vercel.app (HTTP $web_code)"
else
  fail "Web frontend unreachable (HTTP $web_code)"
fi
echo ""

# --- 8. API Backend ---
echo "▸ API Backend"
api_vercel_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 10 --max-time 20 "https://network-peer-api-alpha.vercel.app/" 2>/dev/null || echo "000")
if [ "$api_vercel_code" = "200" ]; then
  pass "API Vercel deployment live (HTTP $api_vercel_code)"
else
  warn "API Vercel deployment not found (HTTP $api_vercel_code)"
  info "API may be deployed on AWS ECS instead"
  if command -v aws &>/dev/null && aws sts get-caller-identity &>/dev/null; then
    api_url=$(aws ecs describe-services --cluster networkpeer --services networkpeer-api --query 'services[0].loadBalancers[0].dnsName' --output text 2>/dev/null || echo "")
    if [ -n "$api_url" ] && [ "$api_url" != "None" ]; then
      api_code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 10 --max-time 20 "https://$api_url/api/v1/health" 2>/dev/null || echo "000")
      if [ "$api_code" = "200" ]; then
        pass "API ECS endpoint live — https://$api_url (HTTP $api_code)"
      else
        warn "API ECS endpoint unreachable (HTTP $api_code)"
      fi
    else
      info "Could not determine ECS API endpoint"
    fi
  fi
fi
echo ""

# --- 9. GitHub Repositories ---
echo "▸ GitHub Repositories"
for repo in "rudraaxl/NetworkPeer" "addy9087/Networkpeer"; do
  code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 10 --max-time 15 "https://github.com/$repo" 2>/dev/null || echo "000")
  if [ "$code" = "200" ]; then
    pass "$repo — accessible (HTTP $code)"
  else
    fail "$repo — unreachable (HTTP $code)"
  fi
done
echo ""

# --- 10. Android APK ---
echo "▸ Android Build"
apk_paths=(
  "$HOME/Desktop/NetworkPeer_Demo/NetworkPeer-v0.1.0-dev.apk"
  "/tmp/NETWORKPEER_ADDY/apps/android/app/build/outputs/apk/development/debug/app-development-debug.apk"
  "/tmp/NETWORKPEER_FRESH/apps/android/app/build/outputs/apk/development/debug/app-development-debug.apk"
)
apk_found=false
for apk in "${apk_paths[@]}"; do
  if [ -f "$apk" ]; then
    size=$(ls -lh "$apk" | awk '{print $5}')
    pass "Android APK found — $apk ($size)"
    apk_found=true
    break
  fi
done
if [ "$apk_found" = false ]; then
  warn "Android APK not found locally"
  info "Build with: cd apps/android && ./gradlew assembleDevelopmentDebug"
fi
echo ""

echo "============================================"
echo "  Health check complete"
echo "============================================"
