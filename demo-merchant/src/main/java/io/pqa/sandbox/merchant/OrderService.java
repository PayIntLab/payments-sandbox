package io.pqa.sandbox.merchant;

import io.pqa.sandbox.merchant.model.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final OrderStore store;
    private final ProviderClients clients;

    public OrderService(OrderStore store, ProviderClients clients) {
        this.store = store;
        this.clients = clients;
    }

    public Order create(String provider, long amountCents, String currency) {
        String p = provider == null ? "" : provider.toLowerCase();
        if (!p.equals("stripe") && !p.equals("paypal")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "provider must be stripe or paypal");
        }
        Order order = store.create(p, amountCents, currency);
        if (p.equals("stripe")) {
            order.externalId = clients.createStripePaymentIntent(order);
            order.providerStatus = "processing";
        } else {
            order.externalId = clients.createPayPalOrder(order);
            order.providerStatus = "CREATED";
        }
        return order;
    }

    public Order approve(String orderId) {
        Order order = get(orderId);
        if (!"paypal".equals(order.provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "approve is only for paypal orders");
        }
        if ("PAID".equals(order.status)) {
            return order;
        }
        clients.capturePayPalOrder(order);
        return order;
    }

    public Order get(String orderId) {
        Order order = store.byId.get(orderId);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such order: " + orderId);
        }
        return order;
    }

    public Order findByMerchantOrderId(String merchantOrderId) {
        String id = store.byMerchantOrderId.get(merchantOrderId);
        return id == null ? null : store.byId.get(id);
    }
}
