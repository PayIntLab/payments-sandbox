package io.pqa.sandbox.stripe.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class PaymentIntent {
    public String id;
    public String object = "payment_intent";
    public Long amount;
    public String currency;
    public String status;
    public String clientSecret;
    public Long created;
    public Map<String, String> metadata = new LinkedHashMap<>();

    public PaymentIntent() {
    }

    public PaymentIntent(String id, Long amount, String currency, String status,
                         String merchantOrderId) {
        this.id = id;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.clientSecret = id + "_secret_test";
        this.created = System.currentTimeMillis() / 1000;
        if (merchantOrderId != null) {
            this.metadata.put("merchant_order_id", merchantOrderId);
        }
    }
}
