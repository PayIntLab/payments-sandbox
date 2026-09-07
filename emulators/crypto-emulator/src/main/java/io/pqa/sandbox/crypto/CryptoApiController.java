package io.pqa.sandbox.crypto;

import io.pqa.sandbox.crypto.model.CryptoDeposit;
import io.pqa.sandbox.crypto.model.CryptoWebhookEndpoint;
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
 * Crypto emulator: deposit address, simulated incoming deposit, and
 * confirmation progression. A deposit is confirmed after 3 confirmations
 * (default) and one deposit.confirmed webhook is delivered.
 */
@RestController
public class CryptoApiController {
    private static final String DEFAULT_SECRET = "whsec_pqa_crypto_test";
    private final CryptoStore store;
    private final CryptoWebhookSender sender;

    public CryptoApiController(CryptoStore store, CryptoWebhookSender sender) {
        this.store = store;
        this.sender = sender;
    }

    @PostMapping("/v1/webhook_endpoints")
    public CryptoWebhookEndpoint registerEndpoint(@RequestBody Map<String, Object> body) {
        CryptoWebhookEndpoint ep = new CryptoWebhookEndpoint(store.nextEndpointId(),
                String.valueOf(body.get("url")), DEFAULT_SECRET);
        store.endpoints.add(ep);
        return ep;
    }

    @PostMapping("/v1/deposit/address")
    public Map<String, String> depositAddress(@RequestBody Map<String, Object> body) {
        String currency = String.valueOf(body.getOrDefault("currency", "USDT")).toUpperCase();
        String merchantOrderId = String.valueOf(body.getOrDefault("merchant_order_id", ""));
        String address = store.addressByMerchantOrder.computeIfAbsent(
                merchantOrderId, k -> store.nextAddress());
        return Map.of("currency", currency, "address", address,
                "chain", chainOf(currency), "merchant_order_id", merchantOrderId);
    }

    @PostMapping("/v1/deposits")
    public ResponseEntity<Map<String, Object>> simulateDeposit(@RequestBody Map<String, Object> body) {
        String merchantOrderId = String.valueOf(body.getOrDefault("merchant_order_id", ""));
        String address = String.valueOf(body.getOrDefault("address", ""));
        if (address.isEmpty() || "null".equals(address)) {
            address = store.addressByMerchantOrder.get(merchantOrderId);
            if (address == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "unknown merchant_order_id, create an address first"));
            }
        }
        String currency = String.valueOf(body.getOrDefault("currency", "USDT")).toUpperCase();
        String value = String.valueOf(body.getOrDefault("value", "0.00"));
        String fee = String.valueOf(body.getOrDefault("fee", "0.00"));
        CryptoDeposit deposit = new CryptoDeposit(store.nextDepositId(), merchantOrderId,
                currency, address, value, fee, store.nextTxHash());
        store.deposits.put(deposit.id, deposit);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(deposit));
    }

    @GetMapping("/v1/deposits/{id}")
    public Map<String, Object> getDeposit(@PathVariable("id") String id) {
        CryptoDeposit deposit = store.deposits.get(id);
        if (deposit == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such deposit: " + id);
        }
        return view(deposit);
    }

    @PostMapping("/v1/deposits/{id}/confirmations")
    public Map<String, Object> confirmations(@PathVariable("id") String id,
                                             @RequestBody Map<String, Object> body) {
        CryptoDeposit deposit = store.deposits.get(id);
        if (deposit == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such deposit: " + id);
        }
        int count = Integer.parseInt(String.valueOf(body.get("count")));
        boolean crossed = store.applyConfirmations(deposit, count);
        if (crossed) {
            sender.dispatchConfirmed(deposit);
        }
        return view(deposit);
    }

    private static Map<String, Object> view(CryptoDeposit d) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.id);
        out.put("merchant_order_id", d.merchantOrderId);
        out.put("currency", d.currency);
        out.put("address", d.address);
        out.put("value", d.value);
        out.put("fee", d.fee);
        out.put("tx_hash", d.txHash);
        out.put("confirmations", d.confirmations);
        out.put("required_confirmations", d.requiredConfirmations);
        out.put("status", d.status);
        return out;
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
