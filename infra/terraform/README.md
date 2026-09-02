# NetworkPeer AWS ECS/Fargate Target

This Terraform root provisions a cautious AWS target for the existing Fastify API, background worker, and one-shot migrator. It is an infrastructure configuration only: it never reads local `.env` files and it never creates a `aws_secretsmanager_secret_version` resource with a real runtime secret.

## Resources

- A dedicated two-AZ VPC with public ALB/NAT subnets, private ECS subnets, isolated RDS/Redis subnets, locked default security group, tightly scoped security groups, optional VPC flow logs, and VPC endpoints including Cognito IDP.
- Cost-selectable NAT: `none`, `single`, or `per_az`. Private Fargate tasks never receive public IPs.
- S3 gateway and private interface endpoints for ECR, CloudWatch Logs, Secrets Manager, STS, and KMS when enabled.
- An internet-facing ALB, IP target group, HTTP-to-HTTPS redirect only, HTTPS/TLS policy, WebSocket-compatible HTTP/1 target handling, optional ACM/Route53 automation, and optional regional WAF.
- Immutable, scan-on-push ECR repositories for `api`, `worker`, and `migrator` images.
- An ECS cluster, Fargate API and worker services, a one-shot migrator task definition, deployment circuit breakers, target-tracking autoscaling, and optional worker Fargate Spot.
- RDS PostgreSQL in isolated subnets with storage encryption, forced TLS, automated backups, an AWS-managed master credential, enhanced monitoring, CloudWatch PostgreSQL logs, and a PostGIS-ready engine.
- ElastiCache Redis in isolated subnets with TLS required, at-rest encryption, AUTH, snapshots, slow/engine logs, and optional cross-AZ failover.
- Empty Secrets Manager containers referenced by ECS task definitions, CloudWatch alarms/dashboard, and a separate AWS Backup RDS recovery-point policy.
- A no-secret Cognito User Pool app client, `CLIENT`/`WORKER`/`ADMIN` groups, and a Custom Auth Lambda that delivers transactional OTPs through SNS.
- Separate GitHub OIDC plan, apply, and ECR-publish roles. The publish role remains available through the legacy `github_actions_deploy_role_arn` output.

The evidence bucket retains its existing compatibility setting: default server-side encryption is `AES256`, not KMS. Do not change client upload headers to require KMS encryption.

Current objects tagged `networkpeer-evidence-state=pending` expire after `evidence_pending_expiration_days` (default: seven days). Confirmation retags the exact version to `confirmed`, so the pending-only rule does not expire current confirmed evidence; the existing 365-day noncurrent-version retention remains unchanged.

## Non-Secret Inputs

Copy the examples without committing the copies:

```sh
cp backend.hcl.example backend.hcl
cp terraform.tfvars.example terraform.tfvars
```

The following inputs are mandatory and non-secret:

- `aws_region`, `environment`, `evidence_bucket_name`, `web_cors_origins`.
- `terraform_state_bucket_name` and `terraform_lock_table_name`; both must already exist in the approved account.
- `github_repository`, `github_branch`, and `github_environment`.

Confirm CIDR overlap before any plan. Production should explicitly set two stable `availability_zones`, all three subnet CIDR lists, sizing, backup retention, final snapshot name, and the intended NAT mode. `terraform.tfvars.example` identifies the required values and intentionally contains no credential or secret value.

Use an AWS IAM Identity Center session or another short-lived human role locally. GitHub uses OIDC only. Never add static `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, or a long-lived IAM user credential to GitHub or Terraform files.

## Networking And Cost

`nat_gateway_mode` is a deliberate tradeoff:

- `none` creates no NAT gateway. ECS can reach AWS services through the required endpoints and S3 gateway endpoint, including Cognito IDP, but cannot call Stripe, Firebase, Sentry, or other public HTTPS APIs.
- `single` is the lower-cost starting point and creates one NAT gateway. It is an AZ dependency for outbound traffic.
- `per_az` creates a NAT gateway in each AZ and is the production-resilience option.

Interface endpoints, NAT gateways, VPC flow logs, WAF, RDS Multi-AZ, Redis replicas, Performance Insights, snapshots, CloudWatch retention, and AWS Backup all incur recurring charges. Review region-specific pricing and set retention/sizing before apply. The defaults are a reasonable target, not a cost commitment.

RDS and Redis have no public route and only accept traffic from the ECS task security group. The ALB only reaches the API port inside the VPC. ECS task egress is limited to HTTPS plus PostgreSQL and Redis ports; public HTTPS still requires NAT.

The API task receives `TRUST_PROXY_CIDRS` from the two configured public ALB subnet CIDRs, not a broad VPC or internet range. The API security group accepts its listener port only from the ALB security group, so only ALB source ENIs can supply trusted forwarded headers.

## HTTPS, DNS, And WAF

By default the ALB has no public listener. This avoids unintentionally serving plaintext HTTP while certificate ownership is unresolved.

- Route53-managed DNS: set `domain_name`, `route53_zone_id`, `enable_https_listener = true`, and `enable_http_redirect = true`. Terraform requests and DNS-validates an ACM certificate, creates the alias record, and exposes port 80 only as a 301 redirect.
- External DNS: request/validate the ACM certificate with the external DNS provider first, then set the issued regional `acm_certificate_arn` and enable the HTTPS/redirect listeners.
- WebSockets work through the ALB's HTTP/1.1 upgrade handling. Set `alb_idle_timeout_seconds` according to application ping/heartbeat behavior.
- `enable_waf` adds AWS managed common/bad-input rule groups and a rate limit. Review application traffic before enabling it and account for WAF cost.
- `api_url` is populated only when both `domain_name` and the HTTPS listener are configured. `api_alb_dns_name` is an infrastructure address, not a canonical HTTPS URL for an externally issued custom-domain certificate.
- `allow_service_activation=true` is rejected unless both `enable_https_listener=true` and a non-empty canonical `domain_name` are configured. A certificate ARN alone is not an activation endpoint. The release workflow then verifies the canonical `https://<domain>/api/v1/live` and `/api/v1/health` externally after ECS and ALB checks pass.

## Secrets And Data Credentials

Terraform creates only the secret containers below and ECS references individual JSON keys by ARN/key. Populate values out of band through the approved Secrets Manager workflow after the first infrastructure apply. Do not use Terraform secret versions for these values.

Populate `runtime_secret_arn` with the exact API/worker keys:

```text
DATABASE_URL
DATABASE_ADMIN_URL
DATABASE_MEDIA_VERIFIER_URL
DATABASE_FINANCIAL_URL
REDIS_URL
AWS_REGION
AWS_S3_BUCKET
STRIPE_SECRET_KEY
STRIPE_WEBHOOK_SECRET
STRIPE_CONNECT_CLIENT_ID
PAYMENT_WEBHOOK_SECRET
FIREBASE_PROJECT_ID
FIREBASE_CLIENT_EMAIL
FIREBASE_PRIVATE_KEY
SENTRY_DSN
```

Populate `migration_secret_arn` only for the one-shot migrator:

```text
DATABASE_MIGRATION_URL
NETWORKPEER_APP_DB_PASSWORD
NETWORKPEER_ADMIN_DB_PASSWORD
NETWORKPEER_MEDIA_DB_PASSWORD
NETWORKPEER_FINANCIAL_DB_PASSWORD
```

The API and worker task definitions receive the Terraform-managed Cognito pool/client IDs, exact web CORS origins, and secure cross-site browser-cookie settings. They lock production payment, queue, and JSON logging settings (`PAYMENT_GATEWAY=stripe`, `PAYMENT_DISPATCH_ENABLED=true`, `LOG_PRETTY=false`, `LOG_LEVEL=info`). The API additionally receives the Terraform-derived restricted ALB `TRUST_PROXY_CIDRS` value. Runtime secrets contain database, Redis, S3, Stripe, payment-webhook, and optional Sentry/Firebase values. The API disables background queues while the worker enables them.

RDS uses `manage_master_user_password = true`. AWS generates the master password and stores it in an AWS-managed secret; Terraform never receives that value. Do not put that high-privilege account in the runtime secret. Use it only to bootstrap least-privilege migration/application roles.

Every PostgreSQL URL must use `sslmode=require` or a stronger certificate-verifying mode. The Redis URL must be `rediss://` and include the approved AUTH token. The ElastiCache API cannot consume a Secrets Manager ARN for `auth_token`, so `redis_auth_token` must be supplied only as a protected `TF_VAR_redis_auth_token` value. It is marked sensitive, but AWS requires it during create/update and Terraform therefore records it in encrypted state. This is the one AWS control-plane limitation to review with the security owner before deployment.

## Cognito Custom Auth And SMS

Terraform creates a User Pool that accepts accounts only through the API's `AdminCreateUser` broker, a no-secret app client with Custom Auth and refresh-token support, and exactly one role group per marketplace role. The API creates public `CLIENT` and `WORKER` accounts on OTP request; create an `ADMIN` Cognito user manually, add it to the `ADMIN` group, then bind its immutable `sub` with `NetworkPeer-main/scripts/provision-admin.sql`.

The Custom Auth Lambda creates a fresh six-digit OTP for each authentication challenge and publishes it as transactional SNS SMS. Before a real test, request production SMS access if the AWS account is in the SNS SMS sandbox, verify any sandbox destination numbers, and configure a registered sender ID or origination number where the target country requires one. Terraform deliberately does not create an SNS spend limit, phone number, or sender registration because these require account- and country-specific approval.

The app client intentionally has no client secret: browser and native clients authenticate through the API broker, and adding a client secret would require a `SECRET_HASH` on every broker request. The API task role is limited to the Cognito Admin actions used by that broker.

## Migration Order

PostGIS is available in the selected RDS PostgreSQL engine but is not enabled by Terraform. The one-shot migrator must create `postgis`, schema objects, and least-privilege database roles after RDS is available.

1. Bootstrap the infrastructure with API and worker counts parked at zero. `allow_service_activation` defaults to `false`, so generic Terraform cannot start API or worker tasks from a `bootstrap` or stale task definition.
2. Publish immutable ECR images.
3. Populate runtime and migration Secrets Manager JSON values, including TLS URLs and the Redis `rediss://` URL.
4. Run the migrator task once and inspect its CloudWatch log stream and exit code.
5. Only after a successful migration, activate API and worker services.

The protected `ECS Release` workflow captures the currently running API/worker task definitions plus desired and autoscaling min/max capacity before it parks anything. API and worker task-definition revisions use `skip_destroy = true`, keeping a captured revision active for rollback. The workflow then idempotently publishes the three immutable commit-SHA tags, applies task definitions with services parked, waits for zero running service tasks, runs the migrator, verifies a zero exit code, then opens the activation gate. It verifies both services are stable on their expected task definitions, API ALB targets are healthy, and the canonical public `/api/v1/live` and `/api/v1/health` endpoints return HTTP 200. If migration or any post-activation verification fails, it restores the captured task definitions and capacity. An initial parked bootstrap is restored to its prior zero-capacity state instead of attempting to start a placeholder image. Do not bypass that ordering for schema changes.

For a first local bootstrap before images exist, explicitly park services:

```sh
terraform plan \
  -var='allow_service_activation=false' \
  -var='api_desired_count=0' -var='worker_desired_count=0' \
  -var='api_min_capacity=0' -var='worker_min_capacity=0'
```

## Terraform Procedure

1. Create or identify the dedicated encrypted state bucket and DynamoDB lock table. These are external prerequisites; this root does not bootstrap its own backend.
2. Fill every non-secret placeholder in ignored `backend.hcl` and `terraform.tfvars`.
3. Obtain `redis_auth_token` from the approved secret source only for the command session. Do not save it in a file or terminal history.
4. Initialise, format, validate, and review a plan with a short-lived human role:

```sh
terraform init -backend-config=backend.hcl
terraform fmt -check -recursive
terraform validate
terraform plan -out=networkpeer.tfplan
```

5. Require infrastructure review before any apply. Generic Terraform plans and applies are intentionally parked with `allow_service_activation=false`; only the migration-gated `ECS Release` workflow may set it to `true` alongside freshly published SHA tags.
6. Record non-secret Terraform outputs in the protected GitHub Environment as described below.

RDS and ALB deletion protection are enabled by default. AWS Backup Vault Lock is opt-in because it can intentionally make teardown impossible until retention expires. A deliberate destroy requires disabling protection, choosing final-snapshot behavior, and confirming backup retention obligations first.

## GitHub OIDC And Workflows

Create one protected GitHub Environment whose exact name matches `github_environment`. In that Environment, configure deployment branch restrictions to the exact `github_branch`, require reviewers as appropriate, and store the following non-secret variables after the initial Terraform apply:

```text
AWS_REGION
TERRAFORM_ENVIRONMENT
TF_STATE_BUCKET
TF_STATE_KEY
TF_LOCK_TABLE
EVIDENCE_BUCKET_NAME
WEB_CORS_ORIGINS_JSON
TERRAFORM_TFVARS_JSON
AWS_TERRAFORM_PLAN_ROLE_ARN
AWS_TERRAFORM_APPLY_ROLE_ARN
AWS_ECS_PUBLISH_ROLE_ARN
ECR_API_REPOSITORY_URL
ECR_WORKER_REPOSITORY_URL
ECR_MIGRATOR_REPOSITORY_URL
```

Store `REDIS_AUTH_TOKEN` as a protected GitHub Environment **secret**, not a variable. Populate runtime/migration secret values directly in AWS Secrets Manager rather than duplicating them in GitHub.

`TERRAFORM_TFVARS_JSON` is optional JSON containing additional **non-secret** Terraform settings such as domain, CIDRs, sizing, and backup controls. The workflows materialize it only on the ephemeral runner. Never put `redis_auth_token`, application URLs, runtime credentials, or `allow_service_activation=true` in it.

AWS evaluates GitHub's supported `aud` and exact Environment `sub` OIDC claims. GitHub Environment deployment-branch restrictions enforce the matching branch, because AWS does not support GitHub custom OIDC claims. Keep the workflow branch filter, `github_branch`, and GitHub Environment rule aligned.

For repositories using GitHub immutable OIDC subject claims, set the exact emitted subject in the non-secret `github_oidc_subject` input instead of the default `repo:owner/repository:environment:name` form. Verify this before enabling the protected workflows.

- `foundations.yml` performs formatting and validation without cloud credentials.
- `terraform-plan.yml` runs a credentialed, parked plan only by manual dispatch from `main` into the protected environment and assumes the read/state-lock plan role.
- `terraform-apply.yml` creates and applies a fresh parked plan in one protected run. It does not claim to reuse a plan from `terraform-plan.yml` and cannot activate ECS services.
- `ecs-release.yml` is manually dispatched from `main`, assumes the ECR-only publish role for idempotent SHA-tag image publication, then the scoped apply role for the migration-gated rollout and post-activation health checks.
- The scoped apply role includes the CloudWatch Logs log-delivery lifecycle actions required by the ElastiCache engine/slow-log configuration; it does not require a general CloudWatch Logs administrator role.

Release images and task definitions are `X86_64`. Do not set `ecs_cpu_architecture=ARM64` until the release build publishes and verifies ARM64 images for every runtime and migrator target.

No workflow accepts static AWS keys. The initial human bootstrap must use an approved elevated short-lived role because the GitHub apply role is itself created by this Terraform configuration.
