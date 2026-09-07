# pqa-payments-sandbox

Local multi-provider **payment integration development environment**.

Develop and verify real payment integrations for Stripe-like, PayPal-like, card and
crypto flows without waiting for merchant accounts or touching production data.
Each emulator speaks the provider's own style of API, signs its webhooks, and lets
you inject failures — so integration code written here only needs a base URL and
credential swap to point at the real sandbox.

> For local development and testing only. Never use with real payment data.

## Components

| Component | Port | What it does |
| --- | --- | --- |
| `stripe-emulator` | 8101 | Stripe-style PaymentIntents + signed webhooks (`Stripe-Signature`) |
| `paypal-emulator` | 8102 | PayPal Orders v2 create/capture + transmission-signed webhooks |
| `card-acquirer-emulator` | 8103 | Test-card rules: approve / `insufficient_funds` decline (skeleton) |
| `crypto-emulator` | 8104 | Deposit address generation, USDT/ETH/BTC chains (skeleton) |
| `demo-merchant` | 8200 | Reference merchant: provider adapters, signature verification, idempotent webhook handling |

## Quick start

Requirements: JDK 17, Maven 3.8+.

```bash
mvn -B test              # build + unit tests
./scripts/demo.sh        # full local demo: Stripe and PayPal flows to PAID
```

`demo.sh` builds the project, starts the three services, registers webhooks, then
runs both payment flows end to end and prints each order's final state.

## What a full flow looks like

### Stripe

1. Merchant creates an order (`POST /orders` with `provider=stripe`).
2. Merchant calls emulator `POST /v1/payment_intents` with `confirm=true`.
3. Emulator moves the intent to `succeeded` and delivers
   `payment_intent.succeeded` to every registered webhook endpoint,
   signed `Stripe-Signature: t=...,v1=<hmac>`.
4. Merchant verifies the signature, deduplicates by event id, and marks the order `PAID`.

### PayPal

1. Merchant creates an order (`provider=paypal`) and gets a `CREATED` order id.
2. `POST /orders/{id}/approve` calls emulator
   `POST /v2/checkout/orders/{id}/capture`.
3. Emulator delivers `PAYMENT.CAPTURE.COMPLETED` with PayPal transmission headers
   (`paypal-transmission-id/time/sig`, `paypal-webhook-id`).
4. Merchant verifies the transmission signature and marks the order `PAID`.

## Emulator API notes (differences from real providers)

- JSON request/response bodies everywhere (Stripe normally uses form-encoded);
  header names and event payload shapes follow the provider style on purpose.
- Webhook secrets are fixed by default: `whsec_pqa_stripe_test` and
  `whsec_pqa_paypal_test`. Override via env vars when needed.
- Failure injection is available on the Stripe emulator:
  `POST /v1/scenarios` with `drop`, `duplicate`, `bad_signature` or `delay_ms`
  for a specific payment intent and event type.

## Roadmap

- M2: card acquirer full lifecycle (auth/capture/refund/void, 3DS challenge) and
  crypto confirmations (1/3/6 blocks, tx hash, callbacks)
- M3: reconciliation drill: emulator "silently drops a webhook" scenario +
  merchant scheduled reconciliation that finds and recovers the order
- M4: containerized deployment (real Dockerfiles), parity matrix with real
  sandbox behavior, GitHub Actions smoke test for the full demo

## License

MIT — see [LICENSE](LICENSE).
