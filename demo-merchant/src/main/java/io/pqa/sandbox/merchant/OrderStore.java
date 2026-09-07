package io.pqa.sandbox.merchant;

import io.pqa.sandbox.merchant.model.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OrderStore {
    private final AtomicLong seq = new AtomicLong(100);
    public final Map<String, Order> byId = new ConcurrentHashMap<>();
    public final Map<String, String> byMerchantOrderId = new ConcurrentHashMap<>();

    public Order create(String provider, long amountCents, String currency) {
        String merchantOrderId = "ORD-" + seq.incrementAndGet();
        Order order = new Order("order_" + seq.incrementAndGet(),
                merchantOrderId, provider, amountCents, currency);
        byId.put(order.id, order);
        byMerchantOrderId.put(order.merchantOrderId, order.id);
        return order;
    }
}
