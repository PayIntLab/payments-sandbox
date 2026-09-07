package io.pqa.sandbox.stripe;

import io.pqa.sandbox.stripe.model.PaymentIntent;
import io.pqa.sandbox.stripe.model.ScenarioRule;
import io.pqa.sandbox.stripe.model.WebhookEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class StripeApiController {
    private static final String DEFAULT_SECRET = "whsec_pqa_stripe_test";
    private final StripeStore store;
    private final WebhookSender sender;

    public StripeApiController(StripeStore store, WebhookSender sender) {
        this.store = store;
        this.sender = sender;
    }

    @PostMapping("/webhook_endpoints")
    public WebhookEndpoint registerEndpoint(@RequestBody Map<String, Object> body) {
        String url = String.valueOf(body.get("url"));
        String id = store.nextEndpointId();
        WebhookEndpoint ep = new WebhookEndpoint(id, url, DEFAULT_SECRET,
                List.of("payment_intent.succeeded", "payment_intent.payment_failed"));
        store.endpoints.add(ep);
        return ep;
    }

    @PostMapping("/payment_intents")
    public ResponseEntity<?> createIntent(@RequestBody Map<String, Object> body) {
        long amount = Long.parseLong(String.valueOf(body.get("amount")));
        String currency = String.valueOf(body.getOrDefault("currency", "usd"));
        String merchantOrderId = (String) body.get("merchant_order_id");
        boolean confirm = Boolean.TRUE.equals(body.get("confirm"));
        boolean decline = Boolean.TRUE.equals(body.get("decline"));

        PaymentIntent pi = new PaymentIntent(store.nextIntentId(), amount, currency,
                decline ? "requires_payment_method" : "requires_confirmation", merchantOrderId);
        store.intents.put(pi.id, pi);
        if (confirm) {
            if (decline) {
                pi.status = "requires_payment_method";
                sender.dispatch("payment_intent.payment_failed", pi);
            } else {
                pi.status = "succeeded";
                sender.dispatch("payment_intent.succeeded", pi);
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(pi);
    }

    @GetMapping("/payment_intents/{id}")
    public PaymentIntent getIntent(@PathVariable("id") String id) {
        PaymentIntent pi = store.intents.get(id);
        if (pi == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such payment_intent: " + id);
        }
        return pi;
    }

    @PostMapping("/payment_intents/{id}/confirm")
    public PaymentIntent confirm(@PathVariable("id") String id) {
        PaymentIntent pi = store.intents.get(id);
        if (pi == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such payment_intent: " + id);
        }
        if (!"requires_confirmation".equals(pi.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment intent is already " + pi.status);
        }
        pi.status = "succeeded";
        sender.dispatch("payment_intent.succeeded", pi);
        return pi;
    }

    @PostMapping("/scenarios")
    public Map<String, String> injectScenario(@RequestBody Map<String, Object> body) {
        String intentId = String.valueOf(body.get("payment_intent_id"));
        String eventType = String.valueOf(body.getOrDefault("event", "payment_intent.succeeded"));
        ScenarioRule rule = new ScenarioRule();
        rule.drop = Boolean.TRUE.equals(body.get("drop"));
        rule.duplicate = Boolean.TRUE.equals(body.get("duplicate"));
        rule.badSignature = Boolean.TRUE.equals(body.get("bad_signature"));
        Object delay = body.get("delay_ms");
        rule.delayMillis = delay == null ? 0 : Long.parseLong(String.valueOf(delay));
        store.scenarios.put(eventType + ":" + intentId, rule);
        return Map.of("status", "ok");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "provider", "stripe-emulator");
    }
}
