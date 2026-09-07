package io.pqa.sandbox.crypto;

import io.pqa.sandbox.crypto.model.CryptoDeposit;
import io.pqa.sandbox.crypto.model.CryptoWebhookEndpoint;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CryptoWebhookSender {
    private static final Logger log = LoggerFactory.getLogger(CryptoWebhookSender.class);
    private final HttpClient client = HttpClient.newBuilder().build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CryptoStore store;

    public CryptoWebhookSender(CryptoStore store) {
        this.store = store;
    }

    public static String sign(String secret, String timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    public void dispatchConfirmed(CryptoDeposit deposit) {
        String payload = payload(deposit);
        for (CryptoWebhookEndpoint ep : store.endpoints) {
            executor.submit(() -> deliver(ep, payload, deposit.id));
        }
    }

    private void deliver(CryptoWebhookEndpoint ep, String payload, String depositId) {
        String t = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = "t=" + t + ",v1=" + sign(ep.secret, t, payload);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ep.url))
                    .header("Content-Type", "application/json")
                    .header("X-Pqa-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[crypto-webhook] {} -> {} : {}", depositId, ep.url, response.statusCode());
        } catch (Exception e) {
            log.warn("[crypto-webhook] delivery to {} failed: {}", ep.url, e.toString());
        }
    }

    private static String payload(CryptoDeposit d) {
        return "{\"id\":\"evt_crypto_" + d.id + "\",\"type\":\"deposit.confirmed\""
                + ",\"data\":{\"deposit\":{\"id\":\"" + d.id
                + "\",\"merchant_order_id\":\"" + d.merchantOrderId
                + "\",\"currency\":\"" + d.currency
                + "\",\"address\":\"" + d.address
                + "\",\"value\":\"" + d.value
                + "\",\"fee\":\"" + d.fee
                + "\",\"tx_hash\":\"" + d.txHash
                + "\",\"confirmations\":" + d.confirmations
                + ",\"status\":\"" + d.status + "\"}}}";
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
