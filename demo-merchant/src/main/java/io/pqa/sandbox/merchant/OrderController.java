package io.pqa.sandbox.merchant;

import io.pqa.sandbox.merchant.model.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    public ResponseEntity<Order> create(@RequestBody Map<String, Object> body) {
        long amount = Long.parseLong(String.valueOf(body.get("amount")));
        String currency = String.valueOf(body.getOrDefault("currency", "usd"));
        String provider = String.valueOf(body.get("provider"));
        String cardNumber = body.get("card_number") == null
                ? null : String.valueOf(body.get("card_number"));
        Order order = service.create(provider, amount, currency, cardNumber);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/orders/{id}")
    public Order get(@PathVariable("id") String id) {
        return service.get(id);
    }

    @PostMapping("/orders/{id}/approve")
    public Order approve(@PathVariable("id") String id) {
        return service.approve(id);
    }

    @PostMapping("/orders/{id}/challenge")
    public Order challenge(@PathVariable("id") String id, @RequestBody Map<String, Object> body) {
        String result = body.get("result") == null
                ? "successful" : String.valueOf(body.get("result"));
        return service.challenge(id, result);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
