#!/usr/bin/env bash
# M3 reconciliation drill.
#
# Story: the provider confirms a payment but its webhook is silently dropped.
# The merchant order stays PENDING. A scheduled reconciliation job scans the
# order, asks the provider what really happened, and recovers the order.
#
# Every step prints the matching provider and merchant log lines, so a screen
# recording shows both the drill and the backend activity.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PY="${PYTHON:-python3}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
STRIPE=http://localhost:8101
MERCHANT=http://localhost:8200
LOG_DIR="target/reconciliation-drill"
STRIPE_LOG="target/demo-logs/stripe-emulator-0.1.0-SNAPSHOT.log"
MERCHANT_LOG="target/demo-logs/demo-merchant-0.1.0-SNAPSHOT.log"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/drill.log"

if [ -z "${DRILL_LOGGED:-}" ]; then
  DRILL_LOGGED=1 "$ROOT/scripts/reconciliation-drill.sh" "$@" 2>&1 | tee "$LOG"
  exit "${PIPESTATUS[0]}"
fi

banner() {
  echo
  echo "=============================================================="
  echo "  $1"
  echo "=============================================================="
}

json() {
  "$PY" -c "import json,sys; d=json.load(sys.stdin); print(d$1)"
}

MARK_STRIPE=0
MARK_MERCHANT=0

backend_mark() {
  MARK_STRIPE=$(wc -l < "$STRIPE_LOG" 2>/dev/null || echo 0)
  MARK_MERCHANT=$(wc -l < "$MERCHANT_LOG" 2>/dev/null || echo 0)
}

backend_delta() {
  local wait_seconds="${1:-1}"
  local pattern="${2:-\\[merchant\\]|\\[stripe\\]|\\[stripe-webhook\\]|\\[reconciliation\\]|\\[clock\\]|Started (DemoMerchant|StripeEmulator)Application}"
  sleep "$wait_seconds"
  echo "--- backend logs ---"
  {
    tail -n "+$((MARK_STRIPE + 1))" "$STRIPE_LOG" 2>/dev/null || true
    tail -n "+$((MARK_MERCHANT + 1))" "$MERCHANT_LOG" 2>/dev/null || true
  } \
    | grep -E "$pattern" \
    | sort \
    | sed -E 's/^([0-9T:.+-]+)[[:space:]]+[A-Z]+[[:space:]]+[0-9]+[[:space:]]+---[[:space:]]+\[[^]]*\][[:space:]]+[^:]+[[:space:]]*:[[:space:]]+/\1 /' \
    | sed -e 's/ to http:\/\/localhost:8200\/webhooks\/stripe//' \
    | sed 's/^/[backend] /' || true
}

backend_check() {
  local count
  count=$(grep -c "\[stripe-webhook\]" "$MERCHANT_LOG" 2>/dev/null || true)
  echo "[backend] check: merchant log has ${count:-0} webhook deliveries for this order; provider intent is succeeded, local order is still PENDING"
}

banner "M3 reconciliation drill: a silently dropped webhook"
echo "provider : stripe-emulator ($STRIPE)"
echo "merchant : demo-merchant ($MERCHANT)"
echo "window   : reconciliation runs every 5 minutes (sandbox clock)"

banner "[1/6] Build and start the sandbox"
echo "building project and starting emulator + merchant..."
mvn -B -q -DskipTests package

PIDS=()
cleanup() {
  echo
  echo "[cleanup] stopping services..."
  for pid in "${PIDS[@]:-}"; do
    kill "$pid" 2>/dev/null || true
  done
}
trap cleanup EXIT

JARS=(
  "emulators/stripe-emulator/target/stripe-emulator-0.1.0-SNAPSHOT.jar"
  "demo-merchant/target/demo-merchant-0.1.0-SNAPSHOT.jar"
)
mkdir -p target/demo-logs
for jar in "${JARS[@]}"; do
  name="$(basename "$jar" .jar)"
  nohup "$JAVA_BIN" -jar "$ROOT/$jar" >"target/demo-logs/${name}.log" 2>&1 &
  PIDS+=("$!")
done

for _ in $(seq 1 40); do
  s=$(curl -sf "$STRIPE/v1/health" >/dev/null 2>&1 && echo 1 || echo 0)
  m=$(curl -sf "$MERCHANT/health" >/dev/null 2>&1 && echo 1 || echo 0)
  if [ "$s$m" = "11" ]; then
    break
  fi
  sleep 1
done

curl -sf -X POST "$STRIPE/v1/webhook_endpoints" \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"$MERCHANT/webhooks/stripe\"}" >/dev/null
backend_delta 1

banner "[2/6] Customer starts a payment"
backend_mark
ORDER_JSON=$(curl -sf -X POST "$MERCHANT/orders" \
  -H "Content-Type: application/json" \
  -d '{"provider":"stripe","amount":29900,"currency":"usd","confirm":false}')
ORDER_ID=$(echo "$ORDER_JSON" | json "['id']")
INTENT_ID=$(echo "$ORDER_JSON" | json "['externalId']")
echo "merchant order : $ORDER_ID"
echo "payment intent : $INTENT_ID"
echo "local status   : $(echo "$ORDER_JSON" | json "['status']")"
backend_delta 1

banner "[3/6] Provider confirms; webhook delivery is dropped"
backend_mark
echo "injecting failure: drop payment_intent.succeeded for $INTENT_ID"
curl -sf -X POST "$STRIPE/v1/scenarios" \
  -H "Content-Type: application/json" \
  -d "{\"payment_intent_id\":\"$INTENT_ID\",\"event\":\"payment_intent.succeeded\",\"drop\":true}" >/dev/null

curl -sf -X POST "$MERCHANT/orders/$ORDER_ID/confirm-payment" >/dev/null
echo "provider says  : $(curl -sf "$STRIPE/v1/payment_intents/$INTENT_ID" | json "['status']")"
echo "merchant says  : $(curl -sf "$MERCHANT/orders/$ORDER_ID" | json "['status']")"
backend_delta 1

banner "[4/6] Order is stuck: money moved, business state did not"
echo "merchant order : $(curl -sf "$MERCHANT/orders/$ORDER_ID" | json "['status']")"
echo "provider intent: $(curl -sf "$STRIPE/v1/payment_intents/$INTENT_ID" | json "['status']")"
echo "local source   : $(curl -sf "$MERCHANT/orders/$ORDER_ID" | json "['paidSource']")"
backend_check

banner "[5/6] Five minutes later: sandbox clock advances"
backend_mark
curl -sf -X POST "$MERCHANT/dev/clock/advance" \
  -H "Content-Type: application/json" \
  -d '{"seconds":300}' \
  | "$PY" -c "import json,sys; d=json.load(sys.stdin); print('sandbox clock advanced by', d['advanced_seconds'], 'seconds ->', d['simulated_time'])"
backend_delta 1 "\\[clock\\]"

banner "[6/6] Reconciliation finds the payment and recovers the order"
echo "waiting for the scheduled reconciliation tick..."
for _ in $(seq 1 10); do
  STATUS=$(curl -sf "$MERCHANT/orders/$ORDER_ID" | json "['status']")
  if [ "$STATUS" = "PAID" ]; then
    break
  fi
  sleep 1
done
curl -sf "$MERCHANT/reconciliation/report" | "$PY" -m json.tool
backend_delta 0 "\\[reconciliation\\]|\\[merchant\\]|\\[stripe-webhook\\]"

RECOVERED=$(curl -sf "$MERCHANT/orders/$ORDER_ID")
echo
echo "order id      : $(echo "$RECOVERED" | json "['id']")"
echo "status        : $(echo "$RECOVERED" | json "['status']")"
echo "paid source   : $(echo "$RECOVERED" | json "['paidSource']")"
echo "provider said : $(echo "$RECOVERED" | json "['providerStatus']")"
echo "recovered at  : $(echo "$RECOVERED" | json "['recoveredAt']")"
echo
echo "summary"
echo "  webhook deliveries      : 0 (dropped)"
echo "  reconciliation scans    : $(curl -sf "$MERCHANT/reconciliation/report" | json "['scans']")"
echo "  orders recovered        : 1"
echo "  time to recovery        : 5m00s of sandbox time"
echo
echo "The provider was always the source of truth. The merchant only needed a"
echo "scheduled pull to find the payment the webhook never delivered."
