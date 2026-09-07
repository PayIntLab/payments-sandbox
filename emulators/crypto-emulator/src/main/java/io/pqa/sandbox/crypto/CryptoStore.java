package io.pqa.sandbox.crypto;

import io.pqa.sandbox.crypto.model.CryptoDeposit;
import io.pqa.sandbox.crypto.model.CryptoWebhookEndpoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CryptoStore {
    private final AtomicLong seq = new AtomicLong(3000);
    private final AtomicLong endpointSeq = new AtomicLong(1);

    public final Map<String, String> addressByMerchantOrder = new ConcurrentHashMap<>();
    public final Map<String, CryptoDeposit> deposits = new ConcurrentHashMap<>();
    public final List<CryptoWebhookEndpoint> endpoints = new CopyOnWriteArrayList<>();

    public String nextDepositId() {
        return "dep_" + seq.incrementAndGet();
    }

    public String nextAddress() {
        return "T-PQA-" + seq.incrementAndGet();
    }

    public String nextEndpointId() {
        return "wex_" + endpointSeq.incrementAndGet();
    }

    public String nextTxHash() {
        return "0xpqa" + Long.toHexString(System.nanoTime())
                + Long.toHexString(seq.incrementAndGet());
    }

    /**
     * Advances confirmations. Returns true when the deposit crosses the
     * required threshold for the first time.
     */
    public boolean applyConfirmations(CryptoDeposit deposit, int confirmations) {
        int before = deposit.confirmations;
        deposit.confirmations = Math.max(confirmations, deposit.confirmations);
        deposit.updatedAt = java.time.Instant.now();
        boolean crossed = before < deposit.requiredConfirmations
                && deposit.confirmations >= deposit.requiredConfirmations;
        if (crossed) {
            deposit.status = "CONFIRMED";
        }
        return crossed;
    }
}
