package io.pqa.sandbox.card.model;

import java.time.Instant;

public class CardCharge {
    public String id;
    public String merchantOrderId;
    public long amountCents;
    public String currency;
    public String status;
    public String declineReason;
    public String cardLast4;
    public String brand;
    public boolean requires3ds;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = createdAt;

    public CardCharge() {
    }

    public CardCharge(String id, String merchantOrderId, long amountCents, String currency) {
        this.id = id;
        this.merchantOrderId = merchantOrderId;
        this.amountCents = amountCents;
        this.currency = currency;
    }

    public void mark(String nextStatus, String reason) {
        this.status = nextStatus;
        this.declineReason = reason;
        this.updatedAt = Instant.now();
    }
}
