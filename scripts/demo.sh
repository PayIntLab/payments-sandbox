#!/usr/bin/env bash
# pqa-payments-sandbox: local end-to-end demo.
# Builds the project, starts the Stripe/PayPal emulators and the demo merchant,
# registers webhooks, then runs a Stripe and a PayPal payment to completion.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PY="${PYTHON:-python3}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

echo "[1/5] Build project (skip tests)..."
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
  "emulators/paypal-emulator/target/paypal-emulator-0.1.0-SNAPSHOT.jar"
  "demo-merchant/target/demo-merchant-0.1.0-SNAPSHOT.jar"
)
PORTS=(8101 8102 8200)

echo "[2/5] Start services..."
mkdir -p target/demo-logs
for i in "${!JARS[@]}"; do
  log="target/demo-logs/service-${PORTS[$i]}.log"
  nohup "$JAVA_BIN" -jar "$ROOT/${JARS[$i]}" >"$log" 2>&1 &
  PIDS+=("$!")
done

echo "[3/5] Wait for services..."
for _ in $(seq 1 30); do
  em_up=$(curl -sf http://localhost:8101/v1/health >/dev/null 2>&1 && echo 1 || echo 0)
  pa_up=$(curl -sf http://localhost:8102/v1/health >/dev/null 2>&1 && echo 1 || echo 0)
  me_up=$(curl -sf http://localhost:8200/health >/dev/null 2>&1 && echo 1 || echo 0)
  if [ "$em_up" = 1 ] && [ "$pa_up" = 1 ] && [ "$me_up" = 1 ]; then
    break
  fi
  sleep 1
done

echo "[4/5] Register webhook endpoints..."
curl -sf -X POST http://localhost:8101/v1/webhook_endpoints \
  -H "Content-Type: application/json" \
  -d '{"url":"http://localhost:8200/webhooks/stripe"}' >/dev/null
curl -sf -X POST http://localhost:8102/v1/webhooks \
  -H "Content-Type: application/json" \
  -d '{"url":"http://localhost:8200/webhooks/paypal","event_types":[{"name":"PAYMENT.CAPTURE.COMPLETED"}]}' >/dev/null

wait_paid() {
  local order_id="$1" tries="${2:-15}"
  for _ in $(seq 1 "$tries"); do
    status=$(curl -sf "http://localhost:8200/orders/$order_id" | "$PY" -c \
      "import json,sys; print(json.load(sys.stdin)['status'])")
    if [ "$status" = "PAID" ]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

echo "[5/5] Run payment flows..."

echo "--- Stripe: create order (auto-confirm -> webhook -> PAID)"
stripe_order=$(curl -sf -X POST http://localhost:8200/orders \
  -H "Content-Type: application/json" \
  -d '{"provider":"stripe","amount":1990,"currency":"usd"}')
echo "$stripe_order"
stripe_id=$(echo "$stripe_order" | "$PY" -c "import json,sys; print(json.load(sys.stdin)['id'])")
if wait_paid "$stripe_id"; then
  echo "Stripe result: OK  -> $(curl -sf http://localhost:8200/orders/$stripe_id)"
else
  echo "Stripe result: TIMEOUT" >&2
  exit 1
fi

echo "--- PayPal: create order -> approve (capture -> webhook -> PAID)"
paypal_order=$(curl -sf -X POST http://localhost:8200/orders \
  -H "Content-Type: application/json" \
  -d '{"provider":"paypal","amount":3500,"currency":"usd"}')
echo "$paypal_order"
paypal_id=$(echo "$paypal_order" | "$PY" -c "import json,sys; print(json.load(sys.stdin)['id'])")
curl -sf -X POST "http://localhost:8200/orders/$paypal_id/approve" >/dev/null
if wait_paid "$paypal_id"; then
  echo "PayPal result: OK  -> $(curl -sf http://localhost:8200/orders/$paypal_id)"
else
  echo "PayPal result: TIMEOUT" >&2
  exit 1
fi

echo
echo "Demo passed. Logs under target/demo-logs/"
