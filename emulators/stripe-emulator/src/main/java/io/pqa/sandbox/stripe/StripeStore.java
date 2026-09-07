package io.pqa.sandbox.stripe;

import io.pqa.sandbox.stripe.model.PaymentIntent;
import io.pqa.sandbox.stripe.model.ScenarioRule;
import io.pqa.sandbox.stripe.model.WebhookEndpoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class StripeStore {
    private final AtomicLong intentSeq = new AtomicLong(1000);
    private final AtomicLong endpointSeq = new AtomicLong(1);
    private final AtomicLong eventSeq = new AtomicLong(1);

    public final Map<String, PaymentIntent> intents = new ConcurrentHashMap<>();
    public final List<WebhookEndpoint> endpoints = new CopyOnWriteArrayList<>();
    public final Map<String, ScenarioRule> scenarios = new ConcurrentHashMap<>();

    public String nextIntentId() {
        return "pi_" + intentSeq.incrementAndGet();
    }

    public String nextEndpointId() {
        return "we_" + endpointSeq.incrementAndGet();
    }

    public String nextEventId() {
        return "evt_" + eventSeq.incrementAndGet();
    }
}
