#!/usr/bin/env bash
# NetworkPeer demo launcher (Mac). Brings up everything needed for the
# presentation and prints the URLs + next steps.
#
# Usage:
#   ./scripts/start-demo.sh              # local-only (Mac browser demo)
#   ./scripts/start-demo.sh --public     # + Cloudflare quick tunnels for the office laptop
#   ./scripts/stop-demo.sh               # stop everything started here
#
# The script is idempotent: it reuses the API/frontend if they are already up.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND="$ROOT/NetworkPeer-main"
FRONTEND="$ROOT/NetworkPeer-platform-main"
LOG_DIR="/tmp/networkpeer-demo"
PUBLIC="${1:-local}"

API_URL="http://localhost:3000"
FE_URL="http://localhost:8080"
DEMO_CORS_ORIGINS="${CORS_ORIGINS:-http://localhost:8080,http://localhost:3001,http://localhost:5173}"
DEMO_COOKIE_SAME_SITE="${WEB_SESSION_COOKIE_SAME_SITE:-lax}"
DEMO_COOKIE_SECURE="${WEB_SESSION_COOKIE_SECURE:-false}"

mkdir -p "$LOG_DIR"

info()  { printf "\033[1;34m[networkpeer]\033[0m %s\n" "$*"; }
ok()    { printf "\033[1;32m[networkpeer]\033[0m %s\n" "$*"; }
warn()  { printf "\033[1;33m[networkpeer]\033[0m %s\n" "$*"; }
die()   { printf "\033[1;31m[networkpeer]\033[0m %s\n" "$*" >&2; exit 1; }

api_up()    { curl -sf -m 3 "$API_URL/api/v1/live" >/dev/null 2>&1; }
front_up()  { curl -sf -m 3 -o /dev/null "$FE_URL/" >/dev/null 2>&1; }
has_cognito_setting() {
  local name="$1"
  [ -n "${!name:-}" ] || {
    [ -f "$BACKEND/.env" ] && grep -qE "^${name}=[^[:space:]#]+" "$BACKEND/.env"
  }
}
start_api() {
  (
    cd "$BACKEND" && \
      CORS_ORIGINS="$DEMO_CORS_ORIGINS" \
      WEB_SESSION_COOKIE_SAME_SITE="$DEMO_COOKIE_SAME_SITE" \
      WEB_SESSION_COOKIE_SECURE="$DEMO_COOKIE_SECURE" \
      nohup node dist/index.js >"$LOG_DIR/api.log" 2>&1 &
  )
}

has_cognito_setting COGNITO_USER_POOL_ID && has_cognito_setting COGNITO_CLIENT_ID || \
  die "Cognito is required for the demo. Configure COGNITO_USER_POOL_ID and COGNITO_CLIENT_ID in $BACKEND/.env, then attach the Custom Auth triggers."

# ---------------------------------------------------------------------------
info "Step 1/6  Infrastructure (PostGIS + Redis)"
# ---------------------------------------------------------------------------
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qE "^(networkpeer-postgis|networkpeer-redis)$"; then
  ok "Containers already running"
else
  info "Starting containers (first time can take a minute)..."
  (cd "$BACKEND" && docker compose up -d) || die "docker compose up failed"
fi

info "Step 2/6  Database migrations"
(cd "$BACKEND" && npm run migrate >"$LOG_DIR/migrate.log" 2>&1) || { tail -5 "$LOG_DIR/migrate.log"; die "migrate failed"; }
ok "Migrations applied"

# ---------------------------------------------------------------------------
info "Step 3/6  API (port 3000)"
# ---------------------------------------------------------------------------
info "Building current API source..."
(cd "$BACKEND" && npm run build >"$LOG_DIR/build.log" 2>&1) || die "build failed"
if pgrep -f "node dist/index.js" >/dev/null 2>&1; then
  warn "Restarting API with the current build"
  pkill -f "node dist/index.js"; sleep 2
fi
start_api
for i in $(seq 1 20); do api_up && break; sleep 1; done
api_up || die "API did not become ready — see $LOG_DIR/api.log"

# ---------------------------------------------------------------------------
info "Step 4/6  Frontend (port 8080)"
# ---------------------------------------------------------------------------
if front_up; then
  ok "Frontend already running ($FE_URL)"
else
  (cd "$FRONTEND" && nohup npm run dev >"$LOG_DIR/frontend.log" 2>&1 &)
  for i in $(seq 1 30); do front_up && break; sleep 1; done
  front_up || die "Frontend did not become ready — see $LOG_DIR/frontend.log"
fi

# ---------------------------------------------------------------------------
if [ "$PUBLIC" = "--public" ]; then
  info "Step 5/6  Cloudflare quick tunnels"
  pkill -f "cloudflared tunnel" >/dev/null 2>&1 || true
  nohup cloudflared tunnel --no-autoupdate --url "$API_URL" >"$LOG_DIR/tunnel-api.log" 2>&1 &
  nohup cloudflared tunnel --no-autoupdate --url "$FE_URL" >"$LOG_DIR/tunnel-fe.log" 2>&1 &
  API_TUNNEL=""; FE_TUNNEL=""
  for i in $(seq 1 30); do
    [ -z "$API_TUNNEL" ] && API_TUNNEL=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$LOG_DIR/tunnel-api.log" 2>/dev/null | head -1)
    [ -z "$FE_TUNNEL" ] && FE_TUNNEL=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$LOG_DIR/tunnel-fe.log" 2>/dev/null | head -1)
    [ -n "$API_TUNNEL" ] && [ -n "$FE_TUNNEL" ] && break
    sleep 2
  done
  [ -z "$API_TUNNEL" ] || [ -z "$FE_TUNNEL" ] && die "Tunnels did not start — see $LOG_DIR/tunnel-*.log"

  # Re-point the frontend at the API tunnel and restart the API with that exact
  # frontend origin allowed for credentialed browser requests.
  info "Wiring frontend -> API tunnel, and API CORS -> frontend tunnel"
  pkill -f "vite dev" >/dev/null 2>&1 || true
  (cd "$FRONTEND" && VITE_API_BASE_URL="$API_TUNNEL/api/v1" VITE_API_PREFIX=/api/v1 nohup npm run dev >"$LOG_DIR/frontend.log" 2>&1 &)
  DEMO_CORS_ORIGINS="$DEMO_CORS_ORIGINS,$FE_TUNNEL"
  DEMO_COOKIE_SAME_SITE="none"
  DEMO_COOKIE_SECURE="true"
  pkill -f "node dist/index.js" >/dev/null 2>&1 || true
  sleep 2
  start_api
  for i in $(seq 1 20); do curl -sf -m 3 "$API_TUNNEL/api/v1/live" >/dev/null 2>&1 && break; sleep 1; done
  curl -sf -m 10 "$API_TUNNEL/api/v1/live" >/dev/null || die "Public API not reachable through tunnel"
  for i in $(seq 1 30); do curl -sf -m 3 -o /dev/null "$FE_TUNNEL/" >/dev/null 2>&1 && break; sleep 1; done
  curl -sf -m 10 -o /dev/null "$FE_TUNNEL/" || die "Public frontend not reachable through tunnel"
  ok "Public API tunnel:    $API_TUNNEL"
  ok "Public frontend:      $FE_TUNNEL"
fi

# ---------------------------------------------------------------------------
info "Step 6/6  Summary"
# ---------------------------------------------------------------------------
ok "API:        $API_URL   (/api/v1/live, /api/v1/health)"
ok "Frontend:   $FE_URL"
if [ "$PUBLIC" = "--public" ]; then
  ok "Public API:  $API_TUNNEL"
  ok "Public FE:   $FE_TUNNEL"
fi
cat <<EOF

Next steps (one time, on this Mac):
  1. Open $FE_URL in a NORMAL window (client) and an INCOGNITO window (worker).
  2. Sign up both roles and enter the SMS code delivered by Cognito Custom Auth.
  3. Provision the admin:
       cd $BACKEND && docker exec networkpeer-postgis psql -U postgres -d networkpeer -f scripts/provision-admin.sql
  4. Verify the worker (use the phone numbers you signed up with):
       docker exec networkpeer-postgis psql -U postgres -d networkpeer \
         --set=admin_phone="+1XXXXXXXXXX" --set=worker_phone="+1XXXXXXXXXX" <<'SQL'
       SELECT public.admin_set_worker_verification(
         (SELECT id FROM public.users WHERE phone_number = :'admin_phone'),
         (SELECT id FROM public.users WHERE phone_number = :'worker_phone'),
         'VERIFIED', TRUE, 'Verified for live demonstration');
       SQL
  5. Run the demo script (docs/EXECUTIVE_PRESENTATION_AND_DEPLOYMENT_GUIDE.md section 4).
     After clicking "Fund escrow", settle funding with:
       node scripts/simulate-payment-webhook.mjs <operationId> <providerReference>

Logs: $LOG_DIR   |   Stop everything: ./scripts/stop-demo.sh
EOF
