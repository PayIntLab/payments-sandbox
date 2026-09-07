package io.pqa.sandbox.stripe;

import io.pqa.sandbox.stripe.model.PaymentIntent;
import io.pqa.sandbox.stripe.model.ScenarioRule;
import io.pqa.sandbox.stripe.model.WebhookEndpoint;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class WebhookSender {
    private static final Logger log = LoggerFactory.getLogger(WebhookSender.class);
    private final HttpClient client = HttpClient.newBuilder().build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StripeStore store;

    public WebhookSender(StripeStore store) {
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

    public void dispatch(String eventType, PaymentIntent intent) {
        String eventId = store.nextEventId();
        String payload = eventPayload(eventId, eventType, intent);
        ScenarioRule rule = store.scenarios.get(eventType + ":" + intent.id);
        for (WebhookEndpoint ep : store.endpoints) {
            if (ep.enabledEvents != null && !ep.enabledEvents.contains(eventType)) {
                continue;
            }
            if (rule != null && rule.drop) {
                log.info("[stripe-webhook] DROP scenario active for event {} to {}", eventId, ep.url);
                continue;
            }
            long delay = rule == null ? 0 : rule.delayMillis;
            if (rule != null && rule.duplicate) {
                executor.submit(() -> deliver(ep, payload, eventId, rule.badSignature));
            }
            executor.submit(() -> {
                if (delay > 0) {
                    sleep(delay);
                }
                deliver(ep, payload, eventId, rule != null && rule.badSignature);
            });
        }
    }

    private void deliver(WebhookEndpoint ep, String payload, String eventId, boolean badSignature) {
        String t = String.valueOf(System.currentTimeMillis() / 1000);
        String secret = badSignature ? "whsec_wrong_secret" : ep.secret;
        String signature = "t=" + t + ",v1=" + sign(secret, t, payload);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ep.url))
                    .header("Content-Type", "application/json")
                    .header("Stripe-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[stripe-webhook] {} -> {} : {}", eventId, ep.url, response.statusCode());
        } catch (Exception e) {
            log.warn("[stripe-webhook] delivery to {} failed: {}", ep.url, e.toString());
        }
    }

    private static String eventPayload(String eventId, String eventType, PaymentIntent intent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"").append(eventId)
                .append("\",\"object\":\"event\",\"type\":\"").append(eventType)
                .append("\",\"data\":{\"object\":{\"id\":\"").append(intent.id)
                .append("\",\"object\":\"payment_intent\",\"amount\":").append(intent.amount)
                .append(",\"currency\":\"").append(intent.currency)
                .append("\",\"status\":\"").append(intent.status)
                .append("\",\"metadata\":{\"merchant_order_id\":\"")
                .append(intent.metadata.getOrDefault("merchant_order_id", "")).append("\"}}}}");
        return sb.toString();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
