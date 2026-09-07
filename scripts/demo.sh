#!/usr/bin/env bash
# pqa-payments-sandbox: local end-to-end demo.
# Builds the project, starts all emulators and the demo merchant, registers
# webhooks, then runs Stripe, PayPal, card (3DS) and crypto payment flows.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PY="${PYTHON:-python3}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"

echo "[1/6] Build project (skip tests)..."
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
  "emulators/card-acquirer-emulator/target/card-acquirer-emulator-0.1.0-SNAPSHOT.jar"
  "emulators/crypto-emulator/target/crypto-emulator-0.1.0-SNAPSHOT.jar"
  "demo-merchant/target/demo-merchant-0.1.0-SNAPSHOT.jar"
)

echo "[2/6] Start services..."
mkdir -p target/demo-logs
for jar in "${JARS[@]}"; do
  name="$(basename "$jar" .jar)"
  log="target/demo-logs/${name}.log"
  nohup "$JAVA_BIN" -jar "$ROOT/$jar" >"$log" 2>&1 &
  PIDS+=("$!")
done

echo "[3/6] Wait for services..."
for _ in $(seq 1 40); do
  em=$(curl -sf http://localhost:8101/v1/health >/dev/null 2>&1 && echo 1 || echo 0)
  pa=$(curl -sf http://localhost:8102/v1/health >/dev/null 2>&1 && echo 1 || echo 0)
  ca=$(curl -sf http://localhost:8103/v1/health >/dev/null 2>&1 && echo 1 || echo 0)
  cr=$(curl -sf http://localhost:8104/v1/health >/dev/null 2>&1 && echo 1 || echo 0)
  me=$(curl -sf http://localhost:8200/health >/dev/null 2>&1 && echo 1 || echo 0)
  if [ "$em$pa$ca$cr$me" = "11111" ]; then
    break
  fi
  sleep 1
done

echo "[4/6] Register webhook endpoints..."
curl -sf -X POST http://localhost:8101/v1/webhook_endpoints   -H "Content-Type: application/json"   -d '{"url":"http://localhost:8200/webhooks/stripe"}' >/dev/null
curl -sf -X POST http://localhost:8102/v1/webhooks   -H "Content-Type: application/json"   -d '{"url":"http://localhost:8200/webhooks/paypal","event_types":[{"name":"PAYMENT.CAPTURE.COMPLETED"}]}' >/dev/null
curl -sf -X POST http://localhost:8103/v1/webhook_endpoints   -H "Content-Type: application/json"   -d '{"url":"http://localhost:8200/webhooks/card"}' >/dev/null
curl -sf -X POST http://localhost:8104/v1/webhook_endpoints   -H "Content-Type: application/json"   -d '{"url":"http://localhost:8200/webhooks/crypto"}' >/dev/null

wait_paid() {
  local order_id="$1" tries="${2:-15}"
  for _ in $(seq 1 "$tries"); do
    status=$(curl -sf "http://localhost:8200/orders/$order_id" | "$PY" -c       "import json,sys; print(json.load(sys.stdin)['status'])")
    if [ "$status" = "PAID" ]; then
      return 0
    fi
    sleep 1
  done
  return 1
}

field() {
  "$PY" -c "import json,sys; print(json.load(sys.stdin)['$1'])"
}

echo "[5/6] Run payment flows..."

echo "--- Stripe: create order (auto-confirm -> webhook -> PAID)"
stripe_order=$(curl -sf -X POST http://localhost:8200/orders   -H "Content-Type: application/json"   -d '{"provider":"stripe","amount":1990,"currency":"usd"}')
echo "$stripe_order"
stripe_id=$(echo "$stripe_order" | field id)
if wait_paid "$stripe_id"; then
  echo "Stripe result: OK  -> $(curl -sf http://localhost:8200/orders/$stripe_id)"
else
  echo "Stripe result: TIMEOUT" >&2
  exit 1
fi

echo "--- PayPal: create order -> approve (capture -> webhook -> PAID)"
paypal_order=$(curl -sf -X POST http://localhost:8200/orders   -H "Content-Type: application/json"   -d '{"provider":"paypal","amount":3500,"currency":"usd"}')
echo "$paypal_order"
paypal_id=$(echo "$paypal_order" | field id)
curl -sf -X POST "http://localhost:8200/orders/$paypal_id/approve" >/dev/null
if wait_paid "$paypal_id"; then
  echo "PayPal result: OK  -> $(curl -sf http://localhost:8200/orders/$paypal_id)"
else
  echo "PayPal result: TIMEOUT" >&2
  exit 1
fi

echo "--- Card: create order (3DS card) -> challenge -> approve -> webhook -> PAID"
card_order=$(curl -sf -X POST http://localhost:8200/orders   -H "Content-Type: application/json"   -d '{"provider":"card","amount":2500,"currency":"usd","card_number":"4000000000000027"}')
echo "$card_order"
card_id=$(echo "$card_order" | field id)
curl -sf -X POST "http://localhost:8200/orders/$card_id/challenge"   -H "Content-Type: application/json" -d '{"result":"successful"}' >/dev/null
curl -sf -X POST "http://localhost:8200/orders/$card_id/approve" >/dev/null
if wait_paid "$card_id"; then
  echo "Card result: OK  -> $(curl -sf http://localhost:8200/orders/$card_id)"
else
  echo "Card result: TIMEOUT" >&2
  exit 1
fi

echo "--- Crypto: create order -> simulate deposit -> 3 confirmations -> webhook -> PAID"
crypto_order=$(curl -sf -X POST http://localhost:8200/orders   -H "Content-Type: application/json"   -d '{"provider":"crypto","amount":2550,"currency":"usdt"}')
echo "$crypto_order"
crypto_id=$(echo "$crypto_order" | field id)
merchant_order_id=$(echo "$crypto_order" | field merchantOrderId)
deposit=$(curl -sf -X POST http://localhost:8104/v1/deposits   -H "Content-Type: application/json"   -d "{\"merchant_order_id\":\"$merchant_order_id\",\"currency\":\"USDT\",\"value\":\"25.50\",\"fee\":\"0.10\"}")
echo "$deposit"
deposit_id=$(echo "$deposit" | field id)
curl -sf -X POST "http://localhost:8104/v1/deposits/$deposit_id/confirmations"   -H "Content-Type: application/json" -d '{"count":3}' >/dev/null
if wait_paid "$crypto_id"; then
  echo "Crypto result: OK -> $(curl -sf http://localhost:8200/orders/$crypto_id)"
else
  echo "Crypto result: TIMEOUT" >&2
  exit 1
fi

echo "[6/6] Done. Logs under target/demo-logs/"
echo "Demo passed: stripe, paypal, card (3DS), crypto"
