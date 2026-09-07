# NetworkPeer End-to-End Delivery Guide

**Document status:** Delivery build complete; external environment activation and store signing pending

**Version:** 1.3.0

**Prepared:** 23 August 2026

**Audience:** Product owners, engineering leads, cloud/platform engineers, mobile engineers, QA, security reviewers, and release managers

## 1. Purpose and Delivery Status

NetworkPeer is a field-work marketplace where client funding, job acceptance, evidence, and payouts are enforced by authenticated API calls and PostgreSQL lifecycle/ledger controls. This guide is the canonical end-to-end delivery reference for the web, Android, iOS, backend, and AWS target architecture.

The repository has a complete implementation path and source-controlled deployment artifacts. It is not appropriate to call a real cloud or mobile-store rollout complete until authorised owners provide the required account, domain, certificate, secret, Firebase, Stripe, Apple, and Google Play values listed in Section 12. No AWS resources, payment charges, DNS records, or app-store submissions were made while producing this build.

### 1.1 Delivered Source Artifacts

| Area | Delivered capability | Primary source |
| --- | --- | --- |
| Backend | Fastify API, PostgreSQL/PostGIS lifecycle, authenticated roles, Redis refresh/limits, durable outboxes, Stripe gateway adapter, evidence verifier, structured logging, Sentry hooks, Docker targets | `NetworkPeer-main/src/`, `NetworkPeer-main/Dockerfile` |
| Client evidence review | Client-owned review endpoint issues short-lived, version-pinned S3 downloads without exposing bucket/key/version/worker/location metadata | `NetworkPeer-main/src/services/client-evidence-review-service.ts` |
| Web | Vercel/Nitro frontend with client/worker live API flows and corrected work/evidence response models | `NetworkPeer-platform-main/src/` |
| Android | Kotlin/Compose client/worker workflows, secure session, Stripe adapter, data-only FCM account validation, Socket.IO recovery, native location, camera/file evidence, durable retry state, inbox, wallet | `apps/android/` |
| iOS | SwiftUI client/worker workflows, Keychain session, Stripe PaymentSheet adapter, data-only FCM/APNs account validation, Socket.IO recovery, Core Location, camera/photo/file evidence, SwiftData cache, inbox | `apps/ios/` |
| Mobile contract | Versioned mobile API/lifecycle/S3 wire contract | `packages/api-contracts/networkpeer-mobile-contract.yaml` |
| AWS target | Terraform for S3, VPC, ALB/ACM/WAF, ECS API/worker/migrator, RDS/PostGIS, Redis TLS, IAM, OIDC, alarms, and backup | `infra/terraform/` |
| CI/CD | API verification, native/infrastructure foundation checks, Terraform plan/apply, and migration-gated ECS release workflows | `.github/workflows/` |

### 1.2 External Activation Gates

| Gate | Why it cannot be fabricated in source code | Required owner |
| --- | --- | --- |
| AWS account and region | Determines resources, quotas, cost, network CIDRs, and IAM boundary | AWS/platform owner |
| DNS and ACM | HTTPS API and webhook endpoint require a verified domain/certificate | Domain/DNS owner |
| Runtime and migration secrets | Database URLs, JWT secrets, Stripe/Twilio/FCM credentials must remain outside Git | Security/platform owner |
| Stripe test/live setup | PaymentSheet needs an environment publishable key; server needs verified webhook signing secret | Payments owner |
| Firebase/APNs | Push needs Firebase project config, APNs key/certificate, Android `google-services.json`, and iOS plist/entitlements | Mobile/push owner |
| Android/iOS signing | Play App Signing and Apple Developer Team assets are account-bound | Mobile release owner |

## 2. Architecture and Trust Boundaries

```text
Android Kotlin/Compose ----\
iOS SwiftUI ---------------+--> HTTPS + Socket.IO --> ALB + WAF --> ECS Fastify API
Vercel web ----------------/                                  |           |
                                                                    |           +--> ECS background worker
                                                                    |           +--> RDS PostgreSQL + PostGIS
                                                                    |           +--> ElastiCache Redis over TLS
                                                                    |           +--> S3 private versioned evidence
                                                                    |           +--> Stripe / Twilio / Firebase / Sentry
                                                                    |
                                                          CloudWatch, AWS Backup, Secrets Manager
```

### 2.1 Source-of-Truth Rules

1. PostgreSQL is authoritative for jobs, escrow, immutable ledger postings, evidence reservations/confirmation, sync events, and outbox state.
2. Redis/BullMQ accelerates OTP, refresh rotation, rate limiting, push/media dispatch, and retries. It is never the financial or job-lifecycle source of truth.
3. Mobile and web clients call the API. They never receive PostgreSQL, Redis, Stripe secret, Twilio, Firebase service-account, or AWS credentials.
4. S3 uploads use API-issued presigned POST fields. Native code treats the URL and fields as opaque and never chooses an S3 object key.
5. Realtime Socket.IO delivery is best-effort. Clients persist decimal-string cursors and reconcile through `/sync` or `/worker/sync` until the server reports `has_more: false`.
6. Push delivery is an FCM data-only hint. A client validates `recipient_user_id` against its current Keystore/Keychain session, then reconciles through the API; it never trusts push content as state authority.

### 2.2 Privacy Rules

| Stage | Worker can see | Worker cannot see |
| --- | --- | --- |
| Nearby discovery | Public title/description, category, budget, schedule, coarse distance band | Client identity, exact address, exact coordinates, exact distance, arbitrary metadata |
| Pre-accept detail | Privacy-safe job projection | Client identity, exact address/location, private checklist |
| Accepted assignment | Assigned task location/address and subtasks | Other client data outside the authorised job |
| Client evidence review | Safe evidence metadata and a short-lived download URL for confirmed evidence | S3 bucket/key/version, worker identity/location, unconfirmed uploads |

## 3. End-to-End Business Flow

### 3.1 Client to Worker Lifecycle

1. A client signs in through the OTP API. The native app stores the access/refresh pair in Android Keystore-backed storage or iOS Keychain.
2. The client creates a `FUNDING` job with a GeoJSON point using `[longitude, latitude]`, public-safe discovery text, a checklist, and an idempotency key.
3. The client calls the funding endpoint. The API returns a Stripe PaymentIntent client secret; native PaymentSheet presents the payment method using only a public Stripe publishable key.
4. A signed Stripe webhook settles escrow. PostgreSQL creates the balanced ledger effect and transitions an eligible job to `POSTED`/`HELD`.
5. A verified worker submits a current location and discovers only nearby posted/funded jobs. PostgreSQL atomically accepts the job; concurrent workers can never both win.
6. The worker advances `ASSIGNED -> EN_ROUTE -> AT_LOCATION -> IN_PROGRESS` one legal state at a time.
7. For each evidence item, the mobile client computes SHA-256, reserves the media through the API, POSTs the exact file and opaque fields to S3, and confirms the immutable version through the API.
8. The worker submits once required evidence is confirmed. The client reviews safe evidence download targets and approves the job.
9. Approval creates ledger release/fee/payout records. The worker payout reaches final provider state only after independent webhook confirmation.

### 3.2 Lifecycle and Escrow Matrix

| Job status | Allowed principal action | Escrow expectation |
| --- | --- | --- |
| `FUNDING` | Client creates or cancels while unfunded | `UNFUNDED` or funding in progress |
| `POSTED` | Verified nearby worker discovers/accepts | `HELD` |
| `ASSIGNED` through `IN_PROGRESS` | Assigned worker progresses; client/worker may dispute where policy permits | `HELD` |
| `SUBMITTED` | Client reviews evidence and approves or disputes | `HELD` |
| `APPROVED` | Client can complete; payout dispatch/reconciliation continues | `RELEASED` |
| `COMPLETED` | Terminal success | `RELEASED` |
| `CANCELLED`, `DISPUTED` | Policy-dependent terminal/resolution paths | Do not invent a client refund or evidence-rejection workflow without a financial lifecycle change |

## 4. API and Mobile Contract

The contract is `packages/api-contracts/networkpeer-mobile-contract.yaml`. Backend Zod route schemas remain runtime authority until generated OpenAPI clients are introduced.

### 4.1 Wire Rules

| Rule | Requirement |
| --- | --- |
| Success envelope | `{ success: true, data: <payload>, error: null }` |
| Failure envelope | `{ success: false, data: null, error: { code, message } }` |
| Dates | ISO-8601 strings |
| IDs | UUID strings |
| Cursors | Decimal strings, never JavaScript/Swift/Kotlin numeric values |
| Location | GeoJSON `[longitude, latitude]` |
| Idempotency | Body `idempotency_key` for create, fund, approval, and evidence reservation actions |
| Auth | `Authorization: Bearer <access token>` with one refresh/retry cycle |

### 4.2 Native API Coverage

| Workflow | Android | iOS | API dependency |
| --- | --- | --- |
| OTP, session rotation, logout | Implemented | Implemented | `/auth/*` |
| Client create/list/detail/fund/approve | Implemented | Implemented | `/client/jobs`, financial routes |
| Client cancel/complete/dispute | Implemented, server-state guarded | Implemented, server-state guarded | `/client/jobs/:id/{cancel,complete,dispute}` |
| Evidence review | Implemented with API-issued download target | Implemented with API-issued download target | `/client/jobs/:id/evidence` |
| Worker location/nearby/detail/accept | Implemented | Implemented | `/worker/*` |
| Worker task and S3 evidence | Implemented | Implemented | `/work/*` |
| Wallet | Client and worker | Client and worker | `/client/wallet`, `/worker/wallet` |
| Inbox/read state | Implemented | Implemented | `/notifications/*`, `/sync` |
| Realtime/sync | Socket.IO hint + cursor reconciliation | Socket.IO hint + cursor reconciliation | `/sync`, `/worker/sync`, `/api/v1/realtime` |
| Push | Data-only FCM recipient validation, sync, local notification, and logout deregistration | Data-only FCM/APNs validation, sync, local notification, and logout deregistration | `/notifications/devices` |
| Admin | Intentionally web-only | Intentionally web-only | Audited admin APIs remain out of mobile scope |

### 4.3 Push Delivery Contract

The backend sends FCM HTTP v1 data-only messages, not provider-rendered notification payloads. Every message includes `recipient_user_id`, `title`, `body`, decimal-string `cursor`, and `topic`; optional event fields are scalar and safe for a notification hint. Android receives high-priority data delivery. iOS receives an APNs background payload and schedules a local notification only after matching `recipient_user_id` to the active Keychain session.

FCM HTTP v1 `NOT_FOUND` responses with an `FcmError` detail of `UNREGISTERED` deactivate only that device token and continue the remaining tokens. FCM quota/network failures release the PostgreSQL outbox claim with a durable `push_not_before_at` timestamp set to the larger of `Retry-After` and one minute. Candidate and claim queries enforce that timestamp, so restarts and early BullMQ retries cannot send before the due time.

Clients discard alert-style, malformed, logged-out, or wrong-account delivery. Android treats a matching hint as an authenticated sync trigger; iOS background delivery schedules one local notification hint and defers full pagination to the foreground. Inbox, job, wallet, and lifecycle state remain API-authoritative. Native logout revokes with its refresh token without first refreshing an expired access bearer; device deregistration is best effort and cannot delay revocation. If the network is unavailable, local session validation still prevents cross-account display.

## 5. Android Delivery

The Android project is in `apps/android` and uses Kotlin, Jetpack Compose, Retrofit/OkHttp, Android Keystore-backed encrypted session storage, Fused Location, Stripe Android, Firebase Messaging, and Socket.IO.

### 5.1 Android Configuration

Use the ignored flavor-specific local properties file or matching CI-injected values only:

```properties
# networkpeer.development.local.properties
API_BASE_URL=https://api.staging.example.com/api/v1/
STRIPE_PUBLISHABLE_KEY=pk_test_...
REALTIME_URL=https://api.staging.example.com
REALTIME_ORIGIN=https://api.staging.example.com
```

Use a separate `networkpeer.production.local.properties` for production; matching `NETWORKPEER_DEVELOPMENT_*` and `NETWORKPEER_PRODUCTION_*` CI properties can override only their named flavor. Production rejects plaintext realtime transport before it can send a bearer token. Firebase configuration is ignored and belongs only in `app/src/development/google-services.json` or `app/src/production/google-services.json`; shared/root/build-type JSON is rejected. No field above accepts a secret value.

### 5.2 Android Release Gates

1. Install JDK 17+, Android Studio, and Android SDK 35. The committed Gradle wrapper and `android.yml` CI workflow run the development unit suite.
2. Confirm the supplied launcher artwork, add the real application ID, Play App Signing configuration, release signing in the approved CI secret store, and a Firebase Android app.
3. Verify FCM notification permission, camera/FileProvider, location denial/recovery, deep links, dark mode, TalkBack, large fonts, and network interruption.
4. Run unit/UI/device tests and publish through Google Play Internal Testing before production.

## 6. iOS Delivery

The iOS project is in `apps/ios` and uses SwiftUI, a Keychain session store, URLSession, Core Location, Photos/files/camera flows, SwiftData cache, Stripe PaymentSheet, Socket.IO, Firebase Messaging, and APNs data-only integration points.

### 6.1 iOS Configuration

`Config.Debug.xcconfig` and `Config.Release.xcconfig` are safe tracked defaults. Copy the matching ignored local override example and set public values only:

```xcconfig
# Config.Debug.local.xcconfig or Config.Release.local.xcconfig
NETWORKPEER_API_BASE_URL = https://api.staging.example.com/api/v1/
NETWORKPEER_STRIPE_PUBLISHABLE_KEY = pk_test_...
NETWORKPEER_SOCKET_IO_ENABLED = YES
NETWORKPEER_MAX_EVIDENCE_BYTES = 26214400
```

The evidence-byte value must match the backend environment's `MEDIA_MAX_FILE_SIZE_BYTES`. The current backend default is 25 MiB unless intentionally changed. Debug and Release local files are intentionally separate; do not share one configuration between them.

### 6.2 iOS Release Gates

1. Install/select the full Xcode release and generate the project with XcodeGen from `project.yml`.
2. Set Apple Developer Team, real bundle IDs, signing/provisioning, APNs environment, privacy declarations, Firebase `GoogleService-Info.plist`, and final brand approval for the supplied launcher artwork through approved channels.
3. Test Keychain rotation, Dynamic Type, VoiceOver, compact iPhone, iPad, camera/photo/file evidence, background push, deep links, and network interruption.
4. Distribute through TestFlight before App Store submission.

## 7. AWS ECS/Fargate Deployment

`infra/terraform` defines the target deployment. It is intentionally not applied by this repository without a protected, reviewed environment.

### 7.1 Target AWS Services

| Service | Responsibility |
| --- | --- |
| IAM Identity Center/OIDC | Human short-lived access and GitHub deployment roles; no static CI keys |
| VPC, private subnets, endpoints, security groups | Network isolation for ECS/RDS/Redis |
| ECR | Immutable API, worker, and migrator images |
| ECS/Fargate | Long-running Fastify API, background worker, one-shot migrator |
| ALB, ACM, WAF, Route 53 optional | HTTPS, webhook traffic, WebSocket upgrade, optional DNS management |
| RDS PostgreSQL/PostGIS | Authoritative jobs, ledger, lifecycle, outboxes, and spatial queries |
| ElastiCache Redis | TLS OTP/refresh/rate-limit/BullMQ transport |
| S3 | Private versioned evidence with SSE-S3 `AES256`, block-public-access, POST CORS only for web uploads |
| Secrets Manager | Runtime/migration secret references, populated out of band |
| CloudWatch and AWS Backup | Logs, alarms, dashboards, backups, recovery controls |

### 7.2 Terraform Safety Rules

1. Copy `backend.hcl.example` and `terraform.tfvars.example` into ignored local files.
2. Use a dedicated encrypted/versioned Terraform state bucket and DynamoDB lock table.
3. Use a short-lived human role for initial bootstrap. GitHub uses OIDC plan/apply/publish roles only.
4. Never create secret values in Terraform configuration or `tfvars`. Redis AUTH is the AWS control-plane exception and must be passed as protected `TF_VAR_redis_auth_token`; it remains encrypted state data by Terraform design.
5. Start services parked at zero until images and Secrets Manager JSON are populated. `allow_service_activation` defaults to false, so generic Terraform cannot start API or worker tasks from bootstrap or stale image tags. Terraform rejects activation unless an HTTPS listener and an explicit canonical API domain are configured; do not expose an HTTP API listener without ACM/HTTPS configuration.

### 7.3 Migration-Gated Release Sequence

1. Build/push immutable AMD64 API and worker images from Docker `runtime` target.
2. Build/push a distinct migrator image from Docker `migrator` target.
3. Terraform applies task definitions with API/worker desired counts set to zero.
4. Run the migrator ECS task. It requires a TLS `DATABASE_MIGRATION_URL` and provisions least-privilege roles after migrations.
5. Confirm zero migrator exit code and inspect CloudWatch migration logs.
6. Activate API/worker desired counts only with the release gate, an HTTPS canonical domain, and freshly published SHA tags. Prior API/worker task-definition revisions remain active for rollback. The workflow verifies both services are stable on their expected task definitions, API ALB targets are healthy, then calls canonical public `/api/v1/live` and `/api/v1/health` over HTTPS. It restores the pre-release task definitions and desired/autoscaling min/max capacity if migration or post-activation verification fails; an initial parked bootstrap remains parked.
7. Verify Redis connectivity, S3 evidence flow, Socket.IO, Stripe webhook delivery, alarms, and backup state before broader release approval.

## 8. Security and Operational Controls

| Control | Implementation |
| --- | --- |
| Authentication | Native cryptographic JWTs, issuer/audience/expiry validation, Redis single-use refresh rotation, token-family replay revocation, and refresh-token-family logout even when an access token has expired |
| Mobile storage | Android encrypted preferences/Keystore and iOS Keychain; no durable browser-style local token store |
| Rate limiting | Redis-backed Fastify limits plus stricter OTP handling; optional edge WAF guardrails |
| Database access | Isolated application/admin/media/financial roles and least-privilege security-definer functions |
| Evidence | SHA-256, exact length/MIME verification, S3 version ID/ETag validation, immutable version review target, private bucket, pending-current object expiry that excludes confirmed evidence |
| Payments | Stripe client secret does not equal settlement; signed webhooks drive authoritative escrow/payout state |
| Push privacy | Server-generated FCM data-only hints include the intended account; clients validate against the current secure session, Android reconciles over HTTPS, iOS background delivery stays a bounded local hint, and refresh-token logout does not wait for device deregistration |
| Observability | Redacted Pino logs, optional Sentry, CloudWatch logs/alarms/dashboard, ECS/RDS/Redis monitoring |
| Recovery | Migration checksums/advisory locking, RDS backups, AWS Backup plan, durable PostgreSQL outboxes, Redis non-authority rule |

## 9. CI/CD and Quality Gates

| Workflow | Scope |
| --- | --- |
| `deploy.yml` | Backend lint/typecheck/build/unit/E2E setup and Docker runtime build |
| `android.yml` | JDK 17, Android 35 setup, and wrapper-driven development unit tests |
| `foundations.yml` | Swift core tests and Terraform format/validation in CI |
| `terraform-plan.yml` | Manually dispatched protected-environment parked AWS plan through OIDC |
| `terraform-apply.yml` | Fresh parked Terraform plan and apply in one protected run; never a separate reviewed-plan replay |
| `ecs-release.yml` | Idempotent immutable SHA images, rollback-safe parked migration gate, activation only behind canonical HTTPS, ECS/ALB health checks, and external canonical liveness/health verification |

### 9.1 Verified During This Delivery Build

| Check | Result |
| --- | --- |
| Backend lint/typecheck/build/unit tests | Passed: 43 tests; 1 conditional E2E test skipped unless an approved E2E database is configured |
| Client evidence-review, refresh-token logout, data-only push, and device-deregistration tests | Passed |
| Web TypeScript check and production build | Passed |
| iOS Foundation contract tests | Passed: 18 tests |
| Mobile contract YAML and iOS plist | Parsed/validated |
| Android project compile | The Gradle wrapper and CI workflow are committed; local Android SDK/JDK are absent on this machine |
| Full iOS archive/UI test | Not runnable on this machine until full Xcode/XcodeGen/signing are installed/configured |
| Terraform local validation | Not runnable on this machine because Terraform is absent; CI workflow performs validation |

## 10. Release Acceptance Checklist

### Backend and Cloud

1. Production config starts with TLS PostgreSQL URLs, `rediss://`, real JWT secrets, `PAYMENT_GATEWAY=stripe`, production OTP settings, and exact CORS origins.
2. S3 versioning, default encryption, block-public-access, lifecycle policy, and POST CORS have been verified by the API startup check.
3. Migration task succeeded, application roles exist, and no API/worker task uses the RDS master account or static AWS keys.
4. API liveness, health, websocket upgrade, Stripe webhook signature, FCM dispatch, and CloudWatch/Sentry alarms are verified.
5. A backup/restore rehearsal, API rollback, worker restart, Redis restart/outbox recovery, and secret-rotation process are documented and tested.

### Mobile

1. OTP sign-in, refresh rotation, logout, and inactive/suspended user behavior pass on real devices.
2. Client create/fund/post, worker discover/accept, evidence reserve/upload/confirm, client review/approve, and wallet results pass against Stripe test mode.
3. Duplicate acceptance, duplicate idempotency actions, interrupted S3 upload/retry, cold-start sync, offline/reconnect, data-only push account mismatch/logout behavior, notification deep link, location denial, camera denial, and large-file rejection are tested.
4. Android TalkBack/iOS VoiceOver, Dynamic Type/font scaling, contrast, dark mode, RTL, iPad/tablet, and small-screen tests are recorded.
5. Store privacy metadata, support URL, terms/privacy policy, screenshots, signing, test track/TestFlight, and crash reporting are complete.

## 11. Known Product Boundaries

1. Client evidence review is read-only. Evidence rejection/rework requires an explicit lifecycle, dispute, financial, and retention design before implementation.
2. Funded cancellation/refund remains a separate payment/ledger workflow; a client may only cancel within the backend's existing safe unfunded lifecycle path.
3. Self-service worker profile, verification, availability, payout-account onboarding, ledger history, withdrawals, and admin mobile workflows require additional authorised backend projections. Admin remains intentionally web-only.
4. AWS infrastructure code is a deployable target, not proof that a particular AWS account has been provisioned. Costs and quotas require account-owner approval.

## 12. Handover Inputs and Owners

| Input | Classification | Owner |
| --- | --- | --- |
| AWS account, region, environment, cost budget, CIDRs | Non-secret decision | Platform owner |
| API domain, DNS zone, ACM validation | Non-secret decision | DNS owner |
| Terraform state bucket/lock table | Sensitive infrastructure metadata | Platform owner |
| API/worker/migration secret JSON | Secret | Security/platform owner |
| Stripe publishable key | Public configuration | Payments owner |
| Stripe secret/webhook keys | Secret | Payments owner |
| Firebase Android/iOS config | Platform configuration | Push/mobile owner |
| Android package/signing, iOS Team/bundle/signing | Restricted release material | Mobile release owner |
| Exact Vercel web origin(s) | Non-secret configuration | Web owner |

## 13. Source Map

| Need | Source |
| --- | --- |
| API/domain contracts | `NetworkPeer-main/src/contracts.ts`, `NetworkPeer-main/src/routes/` |
| Database lifecycle/roles | `NetworkPeer-main/migrations/`, `NetworkPeer-main/scripts/provision-app-role.sql` |
| S3 evidence handling | `NetworkPeer-main/src/services/media-storage-service.ts`, `work-evidence-service.ts` |
| Client evidence review | `NetworkPeer-main/src/services/client-evidence-review-service.ts` |
| Data-only push and device lifecycle | `NetworkPeer-main/src/services/push-notification-service.ts`, `src/routes/notifications.ts` |
| Android | `apps/android/README.md` |
| iOS | `apps/ios/README.md` |
| Mobile wire contract | `packages/api-contracts/networkpeer-mobile-contract.yaml` |
| AWS Terraform | `infra/terraform/README.md` |
| Historical demo references | Treat older “tonight/tomorrow”, tunnel, local-machine, Railway, or cloud-demo guides as historical unless explicitly revalidated |

## 14. Approval Record

| Role | Name | Approval | Date |
| --- | --- | --- | --- |
| Product owner |  |  |  |
| Engineering lead |  |  |  |
| Platform/security owner |  |  |  |
| Mobile release owner |  |  |  |
| QA owner |  |  |  |
