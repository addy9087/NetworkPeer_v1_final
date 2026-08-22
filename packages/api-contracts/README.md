# NetworkPeer API Contracts

This folder is the cross-client contract boundary for the web, React Native, Android, and iOS applications.

## Source of truth

At runtime, the API's Zod schemas and route handlers remain authoritative:

- `NetworkPeer-main/src/contracts.ts`
- `NetworkPeer-main/src/routes/*.ts`
- `NetworkPeer-main/migrations/*.sql`

`networkpeer-mobile-contract.yaml` records the stable mobile-facing projections, lifecycle values, request casing, API envelope, and secure evidence workflow. It is intentionally not a database client schema. Native clients must never use direct database or AWS credentials.

## Contract rules

1. Success responses use `{ success: true, data, error: null }`.
2. Failure responses use `{ success: false, data: null, error: { code, message } }`.
3. IDs are UUID strings; ordered sync/audit cursors are decimal strings and must not be converted to a 64-bit numeric type.
4. GeoJSON points are `[longitude, latitude]`.
5. Request bodies use documented snake_case fields; service responses can include the documented camelCase financial/wallet fields.
6. Every create/fund/approve/evidence action carries a body `idempotency_key`, not an `Idempotency-Key` header.
7. S3 upload and download targets are opaque. Upload every returned field unchanged, then call evidence confirmation through the API. Do not parse or persist a download target.
8. `POST /auth/logout` is authorized by its `refresh_token`. A valid access bearer is optional for backward compatibility and, when supplied, must identify the refresh-token owner. Clients should send logout without refreshing an expired access token first.

## Change process

1. Change the backend Zod schema/route and tests first.
2. Update this contract in the same pull request.
3. Regenerate/compile Android and iOS API clients.
4. Add or update an API contract test before merging a breaking change.

The next phase will publish this as a generated OpenAPI 3.1 document. It is deliberately reviewed as a hand-curated mobile projection first because the current backend does not yet expose a generated OpenAPI definition.
