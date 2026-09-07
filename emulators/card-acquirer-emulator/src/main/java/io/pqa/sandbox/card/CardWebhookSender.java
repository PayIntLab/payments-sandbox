package io.pqa.sandbox.card;

import io.pqa.sandbox.card.model.CardCharge;
import io.pqa.sandbox.card.model.CardWebhookEndpoint;
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
public class CardWebhookSender {
    private static final Logger log = LoggerFactory.getLogger(CardWebhookSender.class);
    private final HttpClient client = HttpClient.newBuilder().build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CardStore store;

    public CardWebhookSender(CardStore store) {
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

    public void dispatch(String eventType, CardCharge charge) {
        String payload = payload(eventType, charge);
        for (CardWebhookEndpoint ep : store.endpoints) {
            executor.submit(() -> deliver(ep, payload, eventType, charge.id));
        }
    }

    private void deliver(CardWebhookEndpoint ep, String payload, String eventType, String chargeId) {
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
            log.info("[card-webhook] {} {} -> {} : {}", eventType, chargeId, ep.url, response.statusCode());
        } catch (Exception e) {
            log.warn("[card-webhook] delivery to {} failed: {}", ep.url, e.toString());
        }
    }

    private static String payload(String eventType, CardCharge c) {
        return "{\"id\":\"evt_card_" + c.id + "\",\"type\":\"" + eventType
                + "\",\"data\":{\"object\":{\"id\":\"" + c.id
                + "\",\"merchant_order_id\":\"" + c.merchantOrderId
                + "\",\"amount_cents\":" + c.amountCents
                + ",\"currency\":\"" + c.currency
                + "\",\"status\":\"" + c.status
                + "\",\"last4\":\"" + c.cardLast4 + "\"}}}";
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
