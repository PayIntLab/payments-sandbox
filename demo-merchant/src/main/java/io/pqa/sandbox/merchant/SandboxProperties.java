package io.pqa.sandbox.merchant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sandbox")
public class SandboxProperties {
    private String stripeBaseUrl = "http://localhost:8101";
    private String paypalBaseUrl = "http://localhost:8102";
    private String stripeWebhookSecret = "whsec_pqa_stripe_test";
    private String paypalWebhookSecret = "whsec_pqa_paypal_test";
    private String cardBaseUrl = "http://localhost:8103";
    private String cryptoBaseUrl = "http://localhost:8104";
    private String cardWebhookSecret = "whsec_pqa_card_test";
    private String cryptoWebhookSecret = "whsec_pqa_crypto_test";
    private String cardTestNumber = "4242424242424242";
    private long reconciliationIntervalSeconds = 300;

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

    public String getCardBaseUrl() {
        return cardBaseUrl;
    }

    public void setCardBaseUrl(String cardBaseUrl) {
        this.cardBaseUrl = cardBaseUrl;
    }

    public String getCryptoBaseUrl() {
        return cryptoBaseUrl;
    }

    public void setCryptoBaseUrl(String cryptoBaseUrl) {
        this.cryptoBaseUrl = cryptoBaseUrl;
    }

    public String getCardWebhookSecret() {
        return cardWebhookSecret;
    }

    public void setCardWebhookSecret(String cardWebhookSecret) {
        this.cardWebhookSecret = cardWebhookSecret;
    }

    public String getCryptoWebhookSecret() {
        return cryptoWebhookSecret;
    }

    public void setCryptoWebhookSecret(String cryptoWebhookSecret) {
        this.cryptoWebhookSecret = cryptoWebhookSecret;
    }

    public String getCardTestNumber() {
        return cardTestNumber;
    }

    public void setCardTestNumber(String cardTestNumber) {
        this.cardTestNumber = cardTestNumber;
    }

    public long getReconciliationIntervalSeconds() {
        return reconciliationIntervalSeconds;
    }

    public void setReconciliationIntervalSeconds(long reconciliationIntervalSeconds) {
        this.reconciliationIntervalSeconds = reconciliationIntervalSeconds;
    }
}
