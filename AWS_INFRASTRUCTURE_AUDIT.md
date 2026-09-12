# AWS_INFRASTRUCTURE_AUDIT.md — Cloud Architecture, Cost Breakdown & Operations Manual

**Target Region**: `eu-north-1` (Stockholm)  
**Project**: NetworkPeer Multi-Tenant On-Demand Gig Marketplace  
**Environment**: Staging & Production

---

## 1. Complete Running Services Inventory & Necessity

```
Internet Traffic
      │
      ▼
[AWS Application Load Balancer (ALB)] ── SSL / Reverse Proxy ($18/mo)
      │
      ▼
[AWS ECS Fargate API Service] ──────── Serverless Node.js + PostGIS Containers ($15/mo)
      │
      ├───► [AWS RDS PostgreSQL (db.t4g.micro)] ── Relational Data, Spatial Coordinates ($17/mo)
      │
      ├───► [AWS S3 Evidence Bucket] ───────────── High-Resolution Photos & Geotagged Evidence ($1/mo)
      │
      ├───► [AWS NAT Gateway (AZ eu-north-1a)] ─── Outbound Private VPC Egress ($32.40/mo)
      │
      └───► [AWS ECR (Docker Image Registry)] ──── Container Images ($0.50/mo)
```

| AWS Service | Resource ID / Identifier | Function & Architecture Role | Necessity Rating | Dev Monthly Cost | Prod Monthly Cost |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Application Load Balancer (ALB)** | `networkpeer-staging-api-alb` | Public internet entry point. Terminates SSL/TLS, manages ACM certificates, and routes `/api/v1/*` to backend ECS tasks. | **Essential** | $18.00 | $25.00 |
| **ECS Fargate (Backend API)** | `networkpeer-staging-api-service` | Runs serverless Node.js containers executing business logic, financial ledger journal entries, and PostGIS queries. | **Essential** | $14.50 | $45.00 (Autoscaled) |
| **RDS PostgreSQL Database** | `networkpeer-staging-db` | Primary datastore (`db.t4g.micro`, 20 GB gp3). Stores users, roles, job specifications, escrow balances, and audit logs. | **Essential** | $17.00 | $65.00 (Multi-AZ) |
| **NAT Gateway (Private Subnet)** | `nat-04f97663ce9c7b220` (AZ `eu-north-1a`) | Grants private ECS tasks outbound internet access (for external webhooks, email delivery, SMS APIs). | **Required for private VPC** | $32.40 | $65.00 (Dual AZ HA) |
| **S3 Evidence Storage** | `networkpeer-evidence-*` | Object storage for worker submission images, high-res photos, and SHA-256 verification blobs. | **Essential** | $1.00 | $15.00 |
| **ECR Container Registry** | `networkpeer/backend` | Stores versioned Docker container images built and pushed by CI/CD pipelines. | **Essential for Deployments** | $0.50 | $2.00 |
| **CloudFront Distribution** | Edge CDN | Global edge caching for static assets and client deliverables. | **Optional in Staging** | $0.00 (Free Tier) | $15.00 |
| **TOTAL RUN-RATE** | — | — | — | **~$83.40 / month** | **~$232.00 / month** |

---

## 2. Cost Optimization & Redundancy Reduction Achieved

- **Redundant NAT Gateway Deleted**:
  - Previously, an unneeded secondary NAT gateway was active in AZ `eu-north-1b`.
  - Safely rerouted private route table `rtb-087282a29d5d5f849` to primary NAT gateway `nat-04f97663ce9c7b220` in `eu-north-1a`.
  - Deleted `nat-0e6cd782a0ab090e4` and released associated Elastic IP `13.61.125.169`.
  - **Direct Savings**: **$32.40/month (~₹2,700/month)** eliminated permanently.

---

## 3. How to Pause AWS Services During Downtime (Reduce Costs to ~$18/mo)

If active development stops for several days or weeks, pause compute resources to eliminate hourly container and database billing without losing any data.

### Step 1: Scale ECS Fargate API Service to Zero
```bash
aws ecs update-service \
  --cluster networkpeer-staging-cluster \
  --service networkpeer-staging-api-service \
  --desired-count 0 \
  --region eu-north-1
```
*Stops running Fargate container tasks. Compute billing drops to $0.*

### Step 2: Stop the RDS PostgreSQL Database Instance
```bash
aws rds stop-db-instance \
  --db-instance-identifier networkpeer-staging-db \
  --region eu-north-1
```
*Preserves all database records, indexes, and schemas on storage while stopping active CPU/RAM billing.*

### Step 3: Resuming Development
When ready to continue work:
```bash
# 1. Start database
aws rds start-db-instance \
  --db-instance-identifier networkpeer-staging-db \
  --region eu-north-1

# 2. Scale containers back to 1
aws ecs update-service \
  --cluster networkpeer-staging-cluster \
  --service networkpeer-staging-api-service \
  --desired-count 1 \
  --region eu-north-1
```
