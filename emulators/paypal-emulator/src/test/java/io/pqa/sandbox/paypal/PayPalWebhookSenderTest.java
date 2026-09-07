package io.pqa.sandbox.paypal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayPalWebhookSenderTest {
    @Test
    void transmissionSignatureIsDeterministic() {
        String s1 = PayPalWebhookSender.sign("whsec_pqa_paypal_test",
                "txn-1|2026-09-07T00:00:00Z|WH-MOCK-10|{}");
        String s2 = PayPalWebhookSender.sign("whsec_pqa_paypal_test",
                "txn-1|2026-09-07T00:00:00Z|WH-MOCK-10|{}");
        assertEquals(s1, s2);
    }
}
