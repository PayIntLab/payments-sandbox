package io.pqa.sandbox.paypal;

import io.pqa.sandbox.paypal.model.PayPalOrder;
import io.pqa.sandbox.paypal.model.PayPalWebhook;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PayPalWebhookSender {
    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookSender.class);
    private final HttpClient client = HttpClient.newBuilder().build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PayPalStore store;

    public PayPalWebhookSender(PayPalStore store) {
        this.store = store;
    }

    public static String sign(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    public void dispatch(PayPalOrder order) {
        String eventId = "WH-" + UUID.randomUUID();
        String payload = eventPayload(eventId, order);
        for (PayPalWebhook wh : store.webhooks) {
            if (wh.eventTypes != null && !wh.eventTypes.contains("PAYMENT.CAPTURE.COMPLETED")) {
                continue;
            }
            executor.submit(() -> deliver(wh, payload, order.id));
        }
    }

    private void deliver(PayPalWebhook wh, String payload, String orderId) {
        String transmissionId = UUID.randomUUID().toString();
        String transmissionTime = Instant.now().toString();
        String message = transmissionId + "|" + transmissionTime + "|" + wh.id + "|" + payload;
        String signature = sign(wh.secret, message);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(wh.url))
                    .header("Content-Type", "application/json")
                    .header("paypal-transmission-id", transmissionId)
                    .header("paypal-transmission-time", transmissionTime)
                    .header("paypal-transmission-sig", signature)
                    .header("paypal-webhook-id", wh.id)
                    .header("paypal-cert-url", "https://mock.pqa.example/cert.pem")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[paypal-webhook] order {} -> {} : {}", orderId, wh.url, response.statusCode());
        } catch (Exception e) {
            log.warn("[paypal-webhook] delivery to {} failed: {}", wh.url, e.toString());
        }
    }

    private static String eventPayload(String eventId, PayPalOrder order) {
        return "{\"id\":\"" + eventId
                + "\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\""
                + ",\"resource\":{\"id\":\"capture_mock_" + order.id
                + "\",\"status\":\"COMPLETED\",\"custom_id\":\"" + order.customId
                + "\",\"amount\":{\"currency_code\":\"" + order.currencyCode
                + "\",\"value\":\"" + order.value + "\"}"
                + ",\"order_id\":\"" + order.id + "\"}}";
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
