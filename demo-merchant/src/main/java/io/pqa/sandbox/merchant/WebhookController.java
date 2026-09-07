package io.pqa.sandbox.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pqa.sandbox.merchant.model.Order;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private final OrderService orderService;
    private final SandboxProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebhookController(OrderService orderService, SandboxProperties props) {
        this.orderService = orderService;
        this.props = props;
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> stripe(HttpServletRequest request) throws IOException {
        String rawBody = readBody(request);
        if (!WebhookSignatures.verifyStripe(
                request.getHeader("Stripe-Signature"), rawBody, props.getStripeWebhookSecret())) {
            log.warn("[stripe-webhook] signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        JsonNode event = mapper.readTree(rawBody);
        String eventId = event.path("id").asText();
        String merchantOrderId = event.path("data").path("object")
                .path("metadata").path("merchant_order_id").asText();
        String externalId = event.path("data").path("object").path("id").asText();
        String type = event.path("type").asText();

        Order order = orderService.findByMerchantOrderId(merchantOrderId);
        if (order == null) {
            log.warn("[stripe-webhook] order not found for {}", merchantOrderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("order not found");
        }
        if (order.alreadyProcessed(eventId)) {
            return ResponseEntity.ok("duplicate ignored");
        }
        if ("payment_intent.succeeded".equals(type)) {
            order.markPaid(externalId, "succeeded", eventId);
            log.info("[stripe-webhook] order {} marked PAID via event {}", order.id, eventId);
        }
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/webhooks/paypal")
    public ResponseEntity<String> paypal(HttpServletRequest request) throws IOException {
        String rawBody = readBody(request);
        boolean valid = WebhookSignatures.verifyPayPal(
                request.getHeader("paypal-transmission-id"),
                request.getHeader("paypal-transmission-time"),
                request.getHeader("paypal-transmission-sig"),
                request.getHeader("paypal-webhook-id"),
                rawBody,
                props.getPaypalWebhookSecret());
        if (!valid) {
            log.warn("[paypal-webhook] signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        JsonNode event = mapper.readTree(rawBody);
        String eventId = event.path("id").asText();
        String merchantOrderId = event.path("resource").path("custom_id").asText();
        String externalId = event.path("resource").path("order_id").asText();
        String eventType = event.path("event_type").asText();

        Order order = orderService.findByMerchantOrderId(merchantOrderId);
        if (order == null) {
            log.warn("[paypal-webhook] order not found for {}", merchantOrderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("order not found");
        }
        if (order.alreadyProcessed(eventId)) {
            return ResponseEntity.ok("duplicate ignored");
        }
        if ("PAYMENT.CAPTURE.COMPLETED".equals(eventType)) {
            order.markPaid(externalId, "COMPLETED", eventId);
            log.info("[paypal-webhook] order {} marked PAID via event {}", order.id, eventId);
        }
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/webhooks/card")
    public ResponseEntity<String> card(HttpServletRequest request) throws IOException {
        String rawBody = readBody(request);
        if (!WebhookSignatures.verifyStripe(
                request.getHeader("X-Pqa-Signature"), rawBody, props.getCardWebhookSecret())) {
            log.warn("[card-webhook] signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        JsonNode event = mapper.readTree(rawBody);
        String eventId = event.path("id").asText();
        String type = event.path("type").asText();
        JsonNode object = event.path("data").path("object");
        String merchantOrderId = object.path("merchant_order_id").asText();
        String externalId = object.path("id").asText();

        Order order = orderService.findByMerchantOrderId(merchantOrderId);
        if (order == null) {
            log.warn("[card-webhook] order not found for {}", merchantOrderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("order not found");
        }
        if (order.alreadyProcessed(eventId)) {
            return ResponseEntity.ok("duplicate ignored");
        }
        if ("payment.captured".equals(type)) {
            order.markPaid(externalId, "CAPTURED", eventId);
            log.info("[card-webhook] order {} marked PAID via event {}", order.id, eventId);
        }
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/webhooks/crypto")
    public ResponseEntity<String> crypto(HttpServletRequest request) throws IOException {
        String rawBody = readBody(request);
        if (!WebhookSignatures.verifyStripe(
                request.getHeader("X-Pqa-Signature"), rawBody, props.getCryptoWebhookSecret())) {
            log.warn("[crypto-webhook] signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        JsonNode event = mapper.readTree(rawBody);
        String eventId = event.path("id").asText();
        String type = event.path("type").asText();
        JsonNode deposit = event.path("data").path("deposit");
        String merchantOrderId = deposit.path("merchant_order_id").asText();
        String txHash = deposit.path("tx_hash").asText();

        Order order = orderService.findByMerchantOrderId(merchantOrderId);
        if (order == null) {
            log.warn("[crypto-webhook] order not found for {}", merchantOrderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("order not found");
        }
        if (order.alreadyProcessed(eventId)) {
            return ResponseEntity.ok("duplicate ignored");
        }
        if ("deposit.confirmed".equals(type)) {
            order.markPaid(txHash, "CONFIRMED", eventId);
            log.info("[crypto-webhook] order {} marked PAID via event {}", order.id, eventId);
        }
        return ResponseEntity.ok("ok");
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
