package io.pqa.sandbox.stripe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WebhookSenderTest {
    @Test
    void signatureIsStableAndSecretDependent() {
        String s1 = WebhookSender.sign("whsec_pqa_stripe_test", "1700000000", "{}");
        String s2 = WebhookSender.sign("whsec_pqa_stripe_test", "1700000000", "{}");
        String s3 = WebhookSender.sign("whsec_other", "1700000000", "{}");
        assertEquals(s1, s2);
        assertNotEquals(s1, s3);
    }
}
