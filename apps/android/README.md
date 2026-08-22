# NetworkPeer Android

Native Kotlin/Compose client for the NetworkPeer mobile API contract.

## Implemented flows

- OTP sign-in with encrypted session storage, optional delivery metadata, resend/edit recovery, serialized one-time refresh-token rotation, and refresh-token logout that does not require a usable access bearer.
- Client job creation with location, optional address/schedule/public-safe fields, checklist items, local validation, and payload-fingerprinted idempotency keys.
- Client job paging/detail, server wallet balances, escrow funding, approval/payout release, cancellation with an optional reason only for API-shaped `FUNDING`/`UNFUNDED` jobs, completion, disputes, and evidence review.
- Stripe PaymentSheet presentation when the API returns a `clientSecret` and a public publishable key is configured. A completed sheet is never treated as settled; the app reconciles API state after the provider webhook path.
- Worker location refresh with current-location then last-location recovery, radius selection, pagination, wallet, and a pre-accept detail screen that does not render address or coordinates until `is_assigned_to_requester` is true.
- Worker status progression, selected-subtask evidence capture or document/media selection, optional checklist evidence, a 25 MiB client guard matching the backend default, SHA-256 upload reservation, opaque presigned S3 POST, confirmation, durable retry metadata, and submit gating for required evidence.
- Cursor-based reconciliation stored per authenticated user. Workers use `/worker/sync`, including assigned-job snapshots; other roles use `/sync`. Reconciliation and Socket.IO work are cancelled and account-bound on session changes, so a prior account cannot write into a new account's state.
- Server-backed notification paging, read, and read-all actions with a durable local cache. FCM notification channel, token registration, Android 13 notification permission, and `networkpeer://job/<jobId>` deep links are wired when Firebase is configured. A data-only push is displayed only when its account-scoped hint matches the active session, then triggers authenticated reconciliation. Notifications created for an account are cancelled when that account logs out or is cleaned up.

## Authority and storage

- The API is authoritative for jobs, lifecycle transitions, wallet balances, evidence acceptance, escrow, and payouts.
- Evidence upload uses only API-issued presigned S3 POST targets. Client review opens only the API-issued, short-lived download target returned by the review endpoint. The app never embeds AWS credentials, creates S3 keys, or talks to PostgreSQL/Redis.
- Durable state contains only user-scoped sync cursors, API-sourced notification/worker-sync cache data, idempotency keys, and pending evidence metadata needed to retry the exact request. Tokens remain in Android Keystore-backed encrypted preferences.

## Public build configuration

Install Android Studio with JDK 17+ and Android API 35. The committed `./gradlew` wrapper uses Gradle 8.10.2 and verifies its distribution checksum; `android.yml` runs the development unit suite in GitHub Actions.

The tracked build has safe unconfigured defaults. Copy the flavor-specific examples to the corresponding ignored local files; do not reuse a development file for production. Command-line `-PNETWORKPEER_DEVELOPMENT_*` or `-PNETWORKPEER_PRODUCTION_*` values can override the matching flavor in CI. Do not commit keys, signing material, `local.properties`, local NetworkPeer properties, or Firebase configuration.

```properties
# networkpeer.development.local.properties or networkpeer.production.local.properties
API_BASE_URL=https://api.example.com/api/v1/

# Stripe publishable keys are public identifiers, not secret keys.
STRIPE_PUBLISHABLE_KEY=pk_test_replace_me

# Optional. If omitted, Socket.IO derives the origin from the API URL and uses
# the contract path /api/v1/realtime. Production accepts HTTPS or WSS only.
REALTIME_URL=https://api.example.com

# Required only when the production Socket.IO server enforces a browser-style
# Origin check for native WebSocket handshakes.
REALTIME_ORIGIN=https://app.example.com
```

`development` appends `.dev` to the application ID. `production` keeps the production ID. If no usable API URL is supplied, the app shows a configuration state and does not call the placeholder `api.invalid` endpoint.

For FCM, place the environment-matched Firebase configuration only at `app/src/development/google-services.json` or `app/src/production/google-services.json` locally. A shared `app/google-services.json`, `src/main`, or build-type Firebase config is rejected so one Firebase project cannot silently ship in the wrong flavor. Missing flavor config leaves FCM disabled for that flavor; the app remains usable without device registration or notification permission.

## Build and test

Use Android Studio or the committed Gradle wrapper:

```text
./gradlew :app:testDevelopmentDebugUnitTest
./gradlew :app:assembleProductionRelease
```

The unit tests cover client draft validation, GeoJSON longitude/latitude ordering, optional checklist serialization, request-fingerprint changes, notification paging/read-state decoding, evidence-review download target decoding, and OTP delivery decoding without optional transport metadata.

## External requirements

The deployed API must include `POST /client/jobs/{jobId}/cancel`, `complete`, and `dispute`; `GET /client/jobs/{jobId}/evidence`; `GET /notifications`; notification read routes; and `GET /worker/sync`, with the response shapes consumed by this client. If an environment has not deployed those routes yet, the corresponding screen action will surface the API's safe error response rather than fall back to a fabricated operation.

Stripe funding also requires the backend to return a PaymentIntent client secret from `POST /client/jobs/{jobId}/fund` and to reconcile provider webhooks. FCM requires flavor-matched Firebase project configuration, `POST`/`DELETE /notifications/devices`, and data-only hints containing the contract-required `recipient_user_id`, `cursor`, `topic`, `title`, and `body`; Android discards a hint without a matching active account, revokes the refresh-token family before best-effort device deregistration, and cancels that account's app notifications during logout or account cleanup. Socket.IO requires a server compatible with the WebSocket transport, `auth: { token }`, `sync:ready`, and `sync:event`; production deployments must use HTTPS/WSS and, when needed, the corresponding public realtime-Origin property.
