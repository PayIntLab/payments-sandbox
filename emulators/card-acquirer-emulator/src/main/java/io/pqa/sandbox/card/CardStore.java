package io.pqa.sandbox.card;

import io.pqa.sandbox.card.model.CardCharge;
import io.pqa.sandbox.card.model.CardWebhookEndpoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CardStore {
    private final AtomicLong seq = new AtomicLong(2000);
    private final AtomicLong endpointSeq = new AtomicLong(1);

    public final Map<String, CardCharge> charges = new ConcurrentHashMap<>();
    public final List<CardWebhookEndpoint> endpoints = new CopyOnWriteArrayList<>();

    public String nextChargeId() {
        return "chg_" + seq.incrementAndGet();
    }

    public String nextEndpointId() {
        return "wec_" + endpointSeq.incrementAndGet();
    }
}
