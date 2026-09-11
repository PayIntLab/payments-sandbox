package io.pqa.sandbox.merchant;

import io.pqa.sandbox.merchant.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderStore store;
    private final ProviderClients clients;
    private final SandboxProperties props;

    public OrderService(OrderStore store, ProviderClients clients, SandboxProperties props) {
        this.store = store;
        this.clients = clients;
        this.props = props;
    }

    public Order create(String provider, long amountCents, String currency, String cardNumber) {
        return create(provider, amountCents, currency, cardNumber, true);
    }

    public Order create(String provider, long amountCents, String currency, String cardNumber,
                        boolean confirm) {
        String p = provider == null ? "" : provider.toLowerCase();
        if (!p.equals("stripe") && !p.equals("paypal")
                && !p.equals("card") && !p.equals("crypto")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "provider must be stripe, paypal, card or crypto");
        }
        Order order = store.create(p, amountCents, currency);
        switch (p) {
            case "stripe" -> {
                order.externalId = clients.createStripePaymentIntent(order, false);
                order.providerStatus = "requires_confirmation";
                if (confirm) {
                    order.providerStatus = clients.confirmStripeIntent(order);
                }
            }
            case "paypal" -> {
                order.externalId = clients.createPayPalOrder(order);
                order.providerStatus = "CREATED";
            }
            case "card" -> {
                String number = cardNumber == null || cardNumber.isBlank()
                        ? props.getCardTestNumber() : cardNumber;
                Map<?, ?> charge = clients.createCardCharge(order, number);
                order.externalId = String.valueOf(charge.get("id"));
                order.providerStatus = String.valueOf(charge.get("status"));
            }
            case "crypto" -> {
                order.externalId = clients.createCryptoAddress(order);
                order.providerStatus = "AWAITING_DEPOSIT";
            }
            default -> {
            }
        }
        log.info("[merchant] created order {} (provider={}, amount={} {}, external={}, confirm={})",
                order.id, order.provider, order.amountCents, order.currency, order.externalId, confirm);
        return order;
    }

    public Order confirmPayment(String orderId) {
        Order order = get(orderId);
        if (!"stripe".equals(order.provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "confirm-payment is only available for stripe orders");
        }
        if ("PENDING".equals(order.status)) {
            order.providerStatus = clients.confirmStripeIntent(order);
        }
        log.info("[merchant] order {} confirm requested; provider status={}, waiting for webhook",
                order.id, order.providerStatus);
        return order;
    }

    public Order approve(String orderId) {
        Order order = get(orderId);
        switch (order.provider) {
            case "paypal" -> {
                if (!"PAID".equals(order.status)) {
                    clients.capturePayPalOrder(order);
                }
            }
            case "card" -> {
                if ("AUTHORIZED".equals(order.providerStatus)) {
                    clients.captureCardCharge(order);
                    order.providerStatus = "CAPTURED";
                } else if (!"PAID".equals(order.status)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Card order is " + order.providerStatus
                                    + "; complete a 3DS challenge first if required");
                }
            }
            case "crypto" -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Crypto orders wait for an on-chain deposit; simulate it in the crypto emulator");
            default -> {
            }
        }
        return order;
    }

    public Order challenge(String orderId, String result) {
        Order order = get(orderId);
        if (!"card".equals(order.provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "3DS challenge is only available for card orders");
        }
        Map<?, ?> charge = clients.challengeCardCharge(order, result);
        order.providerStatus = String.valueOf(charge.get("status"));
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
