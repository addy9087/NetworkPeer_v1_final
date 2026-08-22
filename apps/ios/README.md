# NetworkPeer iOS

Release-oriented native iPhone client for the versioned NetworkPeer mobile contract. The client uses Keychain, authenticated `URLSession`, API-issued S3 POST policies, and server reconciliation only. It contains no database, AWS, Stripe secret, or Firebase service-account credential path.

## Implemented flows

- OTP/JWT authentication with Keychain session persistence, a single serialized refresh flight, one refresh retry, and refresh-token logout that does not depend on a usable access token.
- Client job creation with address, GeoJSON location, scheduled time, privacy-safe public copy, evidence checklist, validation, and a locally retained retry draft/idempotency key per identical submission.
- Client job detail, escrow funding preparation, Stripe PaymentSheet when a public publishable key and SDK are configured, approval/payout reconciliation, guarded cancel/complete/dispute actions, and read-only client evidence review with short-lived HTTPS download targets.
- Worker location recovery, selectable nearby radius, paging, privacy-safe pre-accept detail, atomic acceptance, assigned task progression, and both wallets.
- Required and optional per-subtask evidence from camera, Photos picker, or document/media picker. Files are copied locally before SHA-256 reservation, opaque S3 POST, API confirmation, and retry with the original timestamp/idempotency key.
- SwiftData-backed API snapshots, pending evidence records, remote inbox cache fallback, and per-account durable sync cursors. Workers reconcile through `/worker/sync`, including assignment snapshots, removals, and ledger entries.
- Socket.IO reconciliation hints through the supported client library. `sync:ready` and `sync:event` never mutate job state directly; they trigger paginated HTTP `/sync` or `/worker/sync` reconciliation.
- APNs background data-only FCM delivery with a Firebase Messaging registration-token bridge. Payloads are accepted only when their top-level `recipient_user_id` matches the active Keychain session; they schedule one local notification hint and never run paginated reconciliation or write inbox state directly in the background.

## Local validation

The Foundation API core has no third-party dependency and can be tested independently:

```sh
swift test
```

Generate the iOS project with XcodeGen after installing a full Xcode release and XcodeGen:

```sh
brew install xcodegen
xcodegen generate --spec project.yml
open NetworkPeer.xcodeproj
```

The generated project resolves StripePaymentSheet, Socket.IO, FirebaseCore, and FirebaseMessaging through Swift Package Manager. The app conditionally degrades if a corresponding SDK is not linked.

## Public configuration

`Config.Debug.xcconfig` and `Config.Release.xcconfig` are tracked, safe defaults. They intentionally leave the API unset, and each optionally includes a different ignored local override. Copy only the matching example before running that configuration:

```xcconfig
# Config.Debug.local.xcconfig or Config.Release.local.xcconfig
NETWORKPEER_API_BASE_URL = https://api.your-domain.example/api/v1/
NETWORKPEER_STRIPE_PUBLISHABLE_KEY = pk_live_or_test_public_key
NETWORKPEER_SOCKET_IO_ENABLED = YES
NETWORKPEER_MAX_EVIDENCE_BYTES = 26214400
```

Do not reuse a Debug local config for Release or vice versa. `NETWORKPEER_MAX_EVIDENCE_BYTES` must exactly match the backend environment's evidence-upload limit. The app defaults to 26214400 bytes (25 MiB), which matches the backend default, only when no public build setting is present. `project.yml` sets `NETWORKPEER_APNS_ENVIRONMENT` to `development` for Debug and `production` for Release; local files must not override it. Do not add AWS credentials, database URLs, Stripe secret keys, Firebase service-account JSON, JWT secrets, Twilio credentials, or provider webhooks to this target.

## Release setup blockers

- Create and sign the iOS App ID with Push Notifications, then select the correct APNs environment during archive signing.
- Add the environment-specific `GoogleService-Info.plist` to enable iOS push delivery. The API accepts Firebase Messaging registration tokens only, so the app never sends a raw APNs device token; without Firebase configuration, the in-app inbox remains available but remote push delivery is not configured. Device-token cleanup is best effort and never delays refresh-token session revocation.
- Send FCM as APNs data-only background delivery, with `aps.content-available: 1` and top-level string fields `recipient_user_id`, `title`, `body`, `cursor`, and `topic`, plus optional `job_id`. Do not include an APNs alert: the client validates the Keychain account before scheduling one local notification and defers full reconciliation to an active foreground session.
- Configure the Stripe publishable key and confirm that the API returns a PaymentIntent client secret for the funding flow. PaymentSheet completion is intentionally not treated as escrow success; the API sync is authoritative.
- Deploy and verify the Socket.IO namespace at `/api/v1/realtime` with websocket transport, CONNECT auth payload `{ token }`, and `sync:ready` / `sync:event` server events before setting `NETWORKPEER_SOCKET_IO_ENABLED = YES`.
- Complete the App Store privacy questionnaire for location, evidence media, identifiers, and notification data using the final provider/data-retention policy before submission.
- Client evidence review is intentionally read-only. The app opens only HTTPS API-issued download targets; the contract does not expose rejection, rework, or a client settlement-decision workflow, so the app does not invent those mutations.
