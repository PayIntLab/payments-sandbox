package io.pqa.sandbox.crypto.model;

import java.time.Instant;

public class CryptoDeposit {
    public static final int DEFAULT_REQUIRED_CONFIRMATIONS = 3;

    public String id;
    public String merchantOrderId;
    public String currency;
    public String address;
    public String value;
    public String fee;
    public String txHash;
    public int confirmations;
    public int requiredConfirmations = DEFAULT_REQUIRED_CONFIRMATIONS;
    public String status = "PENDING";
    public Instant createdAt = Instant.now();
    public Instant updatedAt = createdAt;

    public CryptoDeposit() {
    }

    public CryptoDeposit(String id, String merchantOrderId, String currency,
                         String address, String value, String fee, String txHash) {
        this.id = id;
        this.merchantOrderId = merchantOrderId;
        this.currency = currency;
        this.address = address;
        this.value = value;
        this.fee = fee;
        this.txHash = txHash;
    }
}
