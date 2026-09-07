package io.pqa.sandbox.card;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal card-acquirer emulator skeleton.
 * MVP scope (M1): authorize + capture happy path with deterministic test-card rules.
 * Planned (M2): 3DS challenge, batch settlement, refund/void, AVS/CVV mismatch, ISO-ish messages.
 */
@RestController
public class CardApiController {
    private final AtomicLong seq = new AtomicLong(2000);

    @PostMapping("/v1/charges")
    public ResponseEntity<Map<String, Object>> charge(@RequestBody Map<String, Object> body) {
        Map<String, Object> card = asMap(body.get("card"));
        String number = String.valueOf(card.getOrDefault("number", ""));
        String id = "chg_" + seq.incrementAndGet();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("object", "charge");
        out.put("merchant_order_id", String.valueOf(body.getOrDefault("merchant_order_id", "")));
        out.put("amount", body.get("amount"));
        out.put("currency", String.valueOf(body.getOrDefault("currency", "usd")));
        if (number.endsWith("0002")) {
            out.put("status", "declined");
            out.put("decline_reason", "insufficient_funds");
        } else {
            out.put("status", "succeeded");
            out.put("card", Map.of("brand", brandOf(number), "last4", last4(number)));
        }
        return ResponseEntity.ok(out);
    }

    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? new LinkedHashMap<>() {{
            m.forEach((k, v) -> put(String.valueOf(k), v));
        }} : Map.of();
    }

    private static String brandOf(String number) {
        if (number.startsWith("4")) return "visa";
        if (number.startsWith("5")) return "mastercard";
        return "unknown";
    }

    private static String last4(String number) {
        return number.length() >= 4 ? number.substring(number.length() - 4) : number;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "provider", "card-acquirer-emulator");
    }
}
