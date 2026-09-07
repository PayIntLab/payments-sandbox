package io.pqa.sandbox.merchant.model;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Order {
    public String id;
    public String merchantOrderId;
    public String provider;
    public long amountCents;
    public String currency;
    public String status = "PENDING";
    public String externalId;
    public String providerStatus;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = createdAt;
    public final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public Order() {
    }

    public Order(String id, String merchantOrderId, String provider,
                 long amountCents, String currency) {
        this.id = id;
        this.merchantOrderId = merchantOrderId;
        this.provider = provider;
        this.amountCents = amountCents;
        this.currency = currency;
    }

    public void markPaid(String externalId, String providerStatus, String eventId) {
        this.externalId = externalId;
        this.providerStatus = providerStatus;
        this.status = "PAID";
        this.updatedAt = Instant.now();
        if (eventId != null) {
            this.processedEventIds.add(eventId);
        }
    }

    public boolean alreadyProcessed(String eventId) {
        return eventId != null && processedEventIds.contains(eventId);
    }
}
