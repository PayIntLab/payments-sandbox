package io.pqa.sandbox.merchant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sandbox")
public class SandboxProperties {
    private String stripeBaseUrl = "http://localhost:8101";
    private String paypalBaseUrl = "http://localhost:8102";
    private String stripeWebhookSecret = "whsec_pqa_stripe_test";
    private String paypalWebhookSecret = "whsec_pqa_paypal_test";

    public String getStripeBaseUrl() {
        return stripeBaseUrl;
    }

    public void setStripeBaseUrl(String stripeBaseUrl) {
        this.stripeBaseUrl = stripeBaseUrl;
    }

    public String getPaypalBaseUrl() {
        return paypalBaseUrl;
    }

    public void setPaypalBaseUrl(String paypalBaseUrl) {
        this.paypalBaseUrl = paypalBaseUrl;
    }

    public String getStripeWebhookSecret() {
        return stripeWebhookSecret;
    }

    public void setStripeWebhookSecret(String stripeWebhookSecret) {
        this.stripeWebhookSecret = stripeWebhookSecret;
    }

    public String getPaypalWebhookSecret() {
        return paypalWebhookSecret;
    }

    public void setPaypalWebhookSecret(String paypalWebhookSecret) {
        this.paypalWebhookSecret = paypalWebhookSecret;
    }
}
