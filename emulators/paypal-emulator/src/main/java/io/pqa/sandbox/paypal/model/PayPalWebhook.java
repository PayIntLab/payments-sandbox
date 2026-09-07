package io.pqa.sandbox.paypal.model;

import java.util.List;

public class PayPalWebhook {
    public String id;
    public String url;
    public String secret;
    public List<String> eventTypes;

    public PayPalWebhook() {
    }

    public PayPalWebhook(String id, String url, String secret, List<String> eventTypes) {
        this.id = id;
        this.url = url;
        this.secret = secret;
        this.eventTypes = eventTypes;
    }
}
