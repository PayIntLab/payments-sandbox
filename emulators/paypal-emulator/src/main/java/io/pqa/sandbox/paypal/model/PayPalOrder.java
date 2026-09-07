package io.pqa.sandbox.paypal.model;

import java.time.Instant;

public class PayPalOrder {
    public String id;
    public String status;
    public String intent = "CAPTURE";
    public String customId;
    public String currencyCode;
    public String value;
    public Instant createTime;
    public Instant updateTime;

    public PayPalOrder() {
    }

    public PayPalOrder(String id, String customId, String currencyCode, String value) {
        this.id = id;
        this.status = "CREATED";
        this.customId = customId;
        this.currencyCode = currencyCode;
        this.value = value;
        this.createTime = Instant.now();
        this.updateTime = this.createTime;
    }
}
