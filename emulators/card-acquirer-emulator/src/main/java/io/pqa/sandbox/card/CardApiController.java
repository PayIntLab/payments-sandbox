package io.pqa.sandbox.card;

import io.pqa.sandbox.card.model.CardCharge;
import io.pqa.sandbox.card.model.CardWebhookEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Card acquirer emulator: authorize, capture, refund, void and a simulated
 * 3DS challenge. Charge statuses: AUTHORIZED, CAPTURED, REFUNDED, VOIDED,
 * REQUIRES_ACTION, DECLINED.
 */
@RestController
public class CardApiController {
    private static final String DEFAULT_SECRET = "whsec_pqa_card_test";
    private final CardStore store;
    private final CardWebhookSender sender;

    public CardApiController(CardStore store, CardWebhookSender sender) {
        this.store = store;
        this.sender = sender;
    }

    @PostMapping("/v1/webhook_endpoints")
    public CardWebhookEndpoint registerEndpoint(@RequestBody Map<String, Object> body) {
        CardWebhookEndpoint ep = new CardWebhookEndpoint(store.nextEndpointId(),
                String.valueOf(body.get("url")), DEFAULT_SECRET);
        store.endpoints.add(ep);
        return ep;
    }

    @PostMapping("/v1/charges")
    public ResponseEntity<?> authorize(@RequestBody Map<String, Object> body) {
        Map<String, Object> card = asMap(body.get("card"));
        String number = String.valueOf(card.getOrDefault("number", ""));
        long amount = Long.parseLong(String.valueOf(body.get("amount_cents")));
        String currency = String.valueOf(body.getOrDefault("currency", "usd"));
        String merchantOrderId = String.valueOf(body.getOrDefault("merchant_order_id", ""));
        boolean require3ds = Boolean.TRUE.equals(body.get("require_3ds"));

        Map<String, String> rule = CardChargeRules.classify(number,
                String.valueOf(card.getOrDefault("exp_month", "12")),
                String.valueOf(card.getOrDefault("exp_year", "2030")),
                String.valueOf(card.getOrDefault("cvc", "123")));

        CardCharge charge = new CardCharge(store.nextChargeId(), merchantOrderId, amount, currency);
        charge.cardLast4 = rule.get("last4");
        charge.brand = rule.get("brand");
        store.charges.put(charge.id, charge);

        if (CardChargeRules.DECLINE.equals(rule.get("decision"))) {
            charge.mark("DECLINED", rule.get("reason"));
            return ResponseEntity.ok(asChargeView(charge));
        }
        if (require3ds || CardChargeRules.REQUIRE_3DS.equals(rule.get("decision"))) {
            charge.mark("REQUIRES_ACTION", "");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(requireActionView(charge));
        }
        charge.mark("AUTHORIZED", "");
        sender.dispatch("payment.authorized", charge);
        return ResponseEntity.status(HttpStatus.CREATED).body(asChargeView(charge));
    }

    @GetMapping("/v1/charges/{id}")
    public Map<String, Object> getCharge(@PathVariable("id") String id) {
        CardCharge charge = mustGet(id);
        return asChargeView(charge);
    }

    @PostMapping("/v1/charges/{id}/challenge")
    public ResponseEntity<?> challenge(@PathVariable("id") String id,
                                       @RequestBody Map<String, Object> body) {
        CardCharge charge = mustGet(id);
        if (!"REQUIRES_ACTION".equals(charge.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Charge is not waiting for action: " + charge.status);
        }
        String result = String.valueOf(body.getOrDefault("result", "successful"));
        if ("failed".equals(result)) {
            charge.mark("DECLINED", "authentication_failed");
            sender.dispatch("payment.authentication_failed", charge);
            return ResponseEntity.ok(asChargeView(charge));
        }
        charge.mark("AUTHORIZED", "");
        sender.dispatch("payment.authorized", charge);
        return ResponseEntity.ok(asChargeView(charge));
    }

    @PostMapping("/v1/charges/{id}/capture")
    public Map<String, Object> capture(@PathVariable("id") String id) {
        CardCharge charge = mustGet(id);
        if (!"AUTHORIZED".equals(charge.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only AUTHORIZED charges can be captured: " + charge.status);
        }
        charge.mark("CAPTURED", "");
        sender.dispatch("payment.captured", charge);
        return asChargeView(charge);
    }

    @PostMapping("/v1/charges/{id}/refund")
    public Map<String, Object> refund(@PathVariable("id") String id) {
        CardCharge charge = mustGet(id);
        if (!"CAPTURED".equals(charge.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only CAPTURED charges can be refunded: " + charge.status);
        }
        charge.mark("REFUNDED", "");
        sender.dispatch("payment.refunded", charge);
        return asChargeView(charge);
    }

    @PostMapping("/v1/charges/{id}/void")
    public Map<String, Object> voidCharge(@PathVariable("id") String id) {
        CardCharge charge = mustGet(id);
        if (!"AUTHORIZED".equals(charge.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only AUTHORIZED charges can be voided: " + charge.status);
        }
        charge.mark("VOIDED", "");
        sender.dispatch("payment.voided", charge);
        return asChargeView(charge);
    }

    private CardCharge mustGet(String id) {
        CardCharge charge = store.charges.get(id);
        if (charge == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such charge: " + id);
        }
        return charge;
    }

    private static Map<String, Object> requireActionView(CardCharge c) {
        Map<String, Object> out = asChargeView(c);
        out.put("action", Map.of(
                "type", "three_d_secure",
                "url", "/v1/charges/" + c.id + "/challenge"));
        return out;
    }

    private static Map<String, Object> asChargeView(CardCharge c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.id);
        out.put("object", "charge");
        out.put("merchant_order_id", c.merchantOrderId);
        out.put("amount_cents", c.amountCents);
        out.put("currency", c.currency);
        out.put("status", c.status);
        out.put("brand", c.brand);
        out.put("last4", c.cardLast4);
        if (c.declineReason != null && !c.declineReason.isEmpty()) {
            out.put("decline_reason", c.declineReason);
        }
        return out;
    }

    private static Map<String, Object> asMap(Object o) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) {
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "provider", "card-acquirer-emulator");
    }
}
