package io.pqa.sandbox.card.model;

public class CardWebhookEndpoint {
    public String id;
    public String url;
    public String secret;

    public CardWebhookEndpoint() {
    }

    public CardWebhookEndpoint(String id, String url, String secret) {
        this.id = id;
        this.url = url;
        this.secret = secret;
    }
}
