package io.pqa.sandbox.stripe.model;

import java.util.List;

public class WebhookEndpoint {
    public String id;
    public String url;
    public String secret;
    public List<String> enabledEvents;

    public WebhookEndpoint() {
    }

    public WebhookEndpoint(String id, String url, String secret, List<String> enabledEvents) {
        this.id = id;
        this.url = url;
        this.secret = secret;
        this.enabledEvents = enabledEvents;
    }
}
