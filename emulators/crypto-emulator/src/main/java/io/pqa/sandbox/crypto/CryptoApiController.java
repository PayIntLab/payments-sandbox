package io.pqa.sandbox.crypto;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal crypto emulator skeleton.
 * MVP scope (M1): deposit address generation.
 * Planned (M2): simulated on-chain confirmations (1/3/6), tx hash, fee deduction,
 * withdrawal, deposit webhook with confirmation-count rules.
 */
@RestController
public class CryptoApiController {
    private final AtomicLong seq = new AtomicLong(1);
    private final Map<String, String> addresses = new ConcurrentHashMap<>();

    @PostMapping("/v1/deposit/address")
    public Map<String, String> depositAddress(@RequestBody Map<String, Object> body) {
        String currency = String.valueOf(body.getOrDefault("currency", "USDT").toString().toUpperCase());
        String merchantOrderId = String.valueOf(body.getOrDefault("merchant_order_id", ""));
        String key = currency + ":" + merchantOrderId;
        String address = addresses.computeIfAbsent(key,
                k -> "T-PQA-" + (currency.equals("BTC") ? "1" : "T") + seq.incrementAndGet());
        return Map.of("currency", currency, "address", address, "chain", chainOf(currency));
    }

    private static String chainOf(String currency) {
        return switch (currency) {
            case "BTC" -> "bitcoin";
            case "ETH" -> "ethereum";
            default -> "tron";
        };
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "provider", "crypto-emulator");
    }
}
