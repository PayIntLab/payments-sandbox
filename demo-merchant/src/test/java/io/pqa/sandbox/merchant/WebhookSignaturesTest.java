package io.pqa.sandbox.merchant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignaturesTest {
    @Test
    void stripeSignatureRoundTrip() {
        String raw = "{\"type\":\"payment_intent.succeeded\"}";
        String t = "1700000000";
        String v1 = WebhookSignatures.hmacHex("whsec_pqa_stripe_test", t + "." + raw);
        assertTrue(WebhookSignatures.verifyStripe(
                "t=" + t + ",v1=" + v1, raw, "whsec_pqa_stripe_test"));
        assertFalse(WebhookSignatures.verifyStripe(
                "t=" + t + ",v1=" + v1, raw, "whsec_other"));
    }

    @Test
    void paypalTransmissionSignatureRoundTrip() {
        String raw = "{\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}";
        String sig = WebhookSignatures.hmacHex("whsec_pqa_paypal_test",
                "txn|time|WH-1|" + raw);
        assertTrue(WebhookSignatures.verifyPayPal(
                "txn", "time", sig, "WH-1", raw, "whsec_pqa_paypal_test"));
        assertFalse(WebhookSignatures.verifyPayPal(
                "txn", "time", sig, "WH-1", raw, "whsec_other"));
    }
}
