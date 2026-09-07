package io.pqa.sandbox.paypal;

import io.pqa.sandbox.paypal.model.PayPalOrder;
import io.pqa.sandbox.paypal.model.PayPalWebhook;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PayPalStore {
    public static final String TEST_ACCESS_TOKEN = "A21AA_TEST_ACCESS_TOKEN";
    private final AtomicLong orderSeq = new AtomicLong(9000);
    private final AtomicLong webhookSeq = new AtomicLong(10);

    public final Map<String, PayPalOrder> orders = new ConcurrentHashMap<>();
    public final List<PayPalWebhook> webhooks = new CopyOnWriteArrayList<>();

    public String nextOrderId() {
        return "PAYID-MOCK-" + orderSeq.incrementAndGet();
    }

    public String nextWebhookId() {
        return "WH-MOCK-" + webhookSeq.incrementAndGet();
    }
}
