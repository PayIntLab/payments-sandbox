package io.pqa.sandbox.paypal;

import io.pqa.sandbox.paypal.model.PayPalOrder;
import io.pqa.sandbox.paypal.model.PayPalWebhook;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PayPalApiController {
    private static final String DEFAULT_SECRET = "whsec_pqa_paypal_test";
    private final PayPalStore store;
    private final PayPalWebhookSender sender;

    public PayPalApiController(PayPalStore store, PayPalWebhookSender sender) {
        this.store = store;
        this.sender = sender;
    }

    @PostMapping("/v2/oauth2/token")
    public Map<String, Object> token(@RequestParam(name = "grant_type", required = false) String grantType) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("access_token", PayPalStore.TEST_ACCESS_TOKEN);
        out.put("token_type", "Bearer");
        out.put("expires_in", 32400);
        return out;
    }

    @PostMapping("/v1/webhooks")
    public PayPalWebhook registerWebhook(@RequestBody Map<String, Object> body) {
        List<String> events = new ArrayList<>();
        Object eventTypes = body.get("event_types");
        if (eventTypes instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("name") != null) {
                    events.add(String.valueOf(m.get("name")));
                }
            }
        }
        PayPalWebhook wh = new PayPalWebhook(store.nextWebhookId(),
                String.valueOf(body.get("url")), DEFAULT_SECRET, events);
        store.webhooks.add(wh);
        return wh;
    }

    @PostMapping("/v2/checkout/orders")
    public ResponseEntity<?> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("name", "UNAUTHORIZED", "message", "Authentication failed"));
        }
        Object purchaseUnits = body.get("purchase_units");
        String customId = "";
        String currency = "USD";
        String value = "0.00";
        if (purchaseUnits instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> unit) {
            customId = unit.get("custom_id") == null ? "" : String.valueOf(unit.get("custom_id"));
            if (unit.get("amount") instanceof Map<?, ?> amount) {
                currency = amount.get("currency_code") == null ? "USD" : String.valueOf(amount.get("currency_code"));
                value = amount.get("value") == null ? "0.00" : String.valueOf(amount.get("value"));
            }
        }
        PayPalOrder order = new PayPalOrder(store.nextOrderId(), customId, currency, value);
        store.orders.put(order.id, order);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", order.id);
        response.put("status", order.status);
        response.put("intent", order.intent);
        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("reference_id", "default");
        unit.put("custom_id", customId);
        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("currency_code", currency);
        amount.put("value", value);
        unit.put("amount", amount);
        response.put("purchase_units", List.of(unit));
        response.put("create_time", order.createTime.toString());
        Map<String, Object> link = new LinkedHashMap<>();
        link.put("href", "http://localhost:8102/v2/checkout/orders/" + order.id);
        link.put("rel", "self");
        link.put("method", "GET");
        response.put("links", List.of(link));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/v2/checkout/orders/{id}")
    public PayPalOrder getOrder(@PathVariable("id") String id) {
        PayPalOrder order = store.orders.get(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id);
        }
        return order;
    }

    @PostMapping("/v2/checkout/orders/{id}/capture")
    public ResponseEntity<?> capture(@PathVariable("id") String id,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("name", "UNAUTHORIZED", "message", "Authentication failed"));
        }
        PayPalOrder order = store.orders.get(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id);
        }
        if (!"CREATED".equals(order.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order status is " + order.status);
        }
        order.status = "COMPLETED";
        order.updateTime = Instant.now();
        sender.dispatch(order);
        return ResponseEntity.ok(Map.of(
                "id", "capture_mock_" + order.id,
                "status", "COMPLETED",
                "custom_id", order.customId,
                "amount", Map.of("currency_code", order.currencyCode, "value", order.value)));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "provider", "paypal-emulator");
    }
}
