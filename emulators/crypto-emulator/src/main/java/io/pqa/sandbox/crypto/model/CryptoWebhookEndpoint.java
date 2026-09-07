package io.pqa.sandbox.crypto.model;

public class CryptoWebhookEndpoint {
    public String id;
    public String url;
    public String secret;

    public CryptoWebhookEndpoint() {
    }

    public CryptoWebhookEndpoint(String id, String url, String secret) {
        this.id = id;
        this.url = url;
        this.secret = secret;
    }
}
