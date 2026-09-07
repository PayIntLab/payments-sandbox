# pqa-payments-sandbox

Local payment integration environment that emulates multiple payment providers. It is meant for developing and verifying merchant-side payment integrations before switching to real sandbox accounts.

Each emulator implements provider-style endpoints, state transitions and webhook delivery with provider-style signatures. The demo merchant shows a reference integration: provider adapters, signature verification, idempotent webhook handling and order state.

Do not use this project with real payment data.

## Modules

| Module | Port | Role |
| --- | --- | --- |
| `stripe-emulator` | 8101 | Stripe-style PaymentIntents and signed webhooks (`Stripe-Signature`) |
| `paypal-emulator` | 8102 | PayPal Orders v2 create/capture and transmission-signed webhooks |
| `card-acquirer-emulator` | 8103 | Test-card rules: approve and `insufficient_funds` decline (skeleton) |
| `crypto-emulator` | 8104 | Deposit address generation for USDT, ETH and BTC chains (skeleton) |
| `demo-merchant` | 8200 | Reference merchant: adapters, signature verification, idempotent webhooks |

## Build and run

Requirements: JDK 17, Maven 3.8 or newer.

```bash
mvn -B test        # build and run unit tests
./scripts/demo.sh  # local end-to-end demo
```

`demo.sh` builds the project, starts the Stripe emulator, the PayPal emulator and the demo merchant, registers webhook endpoints, runs one Stripe payment and one PayPal payment to a terminal state, prints each order, then stops the services.

## Payment flows

### Stripe

1. The merchant creates an order (`POST /orders` with `provider=stripe`).
2. The merchant calls the emulator `POST /v1/payment_intents` with `confirm=true`.
3. The emulator moves the intent to `succeeded` and delivers `payment_intent.succeeded` to registered webhook endpoints, signed as `Stripe-Signature: t=...,v1=<hmac>`.
4. The merchant verifies the signature, deduplicates by event id and marks the order `PAID`.

### PayPal

1. The merchant creates an order (`provider=paypal`) and receives a `CREATED` order id.
2. `POST /orders/{id}/approve` calls the emulator `POST /v2/checkout/orders/{id}/capture`.
3. The emulator delivers `PAYMENT.CAPTURE.COMPLETED` with PayPal transmission headers (`paypal-transmission-id`, `paypal-transmission-time`, `paypal-transmission-sig`, `paypal-webhook-id`).
4. The merchant verifies the transmission signature and marks the order `PAID`.

## Emulator notes

- Request and response bodies are JSON in all emulators. The real Stripe API accepts form-encoded requests, so the body format is a deliberate simplification; endpoint paths, field names and headers follow provider conventions.
- Default webhook secrets are fixed for local use: `whsec_pqa_stripe_test` and `whsec_pqa_paypal_test`. Override them through environment variables when needed.
- The Stripe emulator supports failure injection through `POST /v1/scenarios` with `drop`, `duplicate`, `bad_signature` or `delay_ms` for a given payment intent and event type.

## Adding another provider

A new provider is an independent module under `emulators/` plus one adapter in `demo-merchant`. The steps are the same for each provider:

1. Create a module that implements the provider's API style: endpoint paths, request and response fields, and its webhook signature format.
2. Implement webhook registration and delivery, including retry and failure-injection behavior.
3. Add an adapter in `demo-merchant` that maps an order to that provider and back.
4. Add a flow to `demo.sh` or module tests that ends with the order in `PAID`.
5. Document provider-specific differences in the emulator notes section.

Existing emulators do not depend on each other, so one provider can be added or changed without touching the others.

## Roadmap

- M2: card acquirer lifecycle (authorize, capture, refund, void, 3DS challenge) and crypto confirmations (1/3/6 blocks, transaction hash, deposit callback)
- M3: reconciliation drill. The emulator drops a webhook silently and the merchant scheduled reconciliation finds and recovers the order
- M4: containerized deployment with Dockerfiles, a parity matrix against real sandbox behavior, and a CI smoke test for the full demo

## License

MIT. See [LICENSE](LICENSE).
