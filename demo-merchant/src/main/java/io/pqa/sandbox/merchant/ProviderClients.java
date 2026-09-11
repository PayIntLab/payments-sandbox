package io.pqa.sandbox.merchant;

import io.pqa.sandbox.merchant.model.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ProviderClients implements ProviderStatusLookup {
    private static final String PAYPAL_TOKEN = "A21AA_TEST_ACCESS_TOKEN";
    private final SandboxProperties props;

    public ProviderClients(SandboxProperties props) {
        this.props = props;
    }

    public String createStripePaymentIntent(Order order) {
        return createStripePaymentIntent(order, true);
    }

    public String createStripePaymentIntent(Order order, boolean confirm) {
        RestClient client = RestClient.builder().baseUrl(props.getStripeBaseUrl()).build();
        Map<?, ?> resp = client.post().uri("/v1/payment_intents")
                .header("Content-Type", "application/json")
                .body(Map.of(
                        "amount", order.amountCents,
                        "currency", order.currency,
                        "merchant_order_id", order.merchantOrderId,
                        "confirm", confirm))
                .retrieve()
                .body(Map.class);
        return String.valueOf(resp.get("id"));
    }

    public String confirmStripeIntent(Order order) {
        RestClient client = RestClient.builder().baseUrl(props.getStripeBaseUrl()).build();
        Map<?, ?> resp = client.post().uri("/v1/payment_intents/{id}/confirm", order.externalId)
                .retrieve()
                .body(Map.class);
        return String.valueOf(resp.get("status"));
    }

    public String fetchStripeIntentStatus(Order order) {
        RestClient client = RestClient.builder().baseUrl(props.getStripeBaseUrl()).build();
        Map<?, ?> resp = client.get().uri("/v1/payment_intents/{id}", order.externalId)
                .retrieve()
                .body(Map.class);
        return String.valueOf(resp.get("status"));
    }

    public String fetchPayPalOrderStatus(Order order) {
        RestClient client = RestClient.builder().baseUrl(props.getPaypalBaseUrl()).build();
        Map<?, ?> resp = client.get().uri("/v2/checkout/orders/{id}", order.externalId)
                .header("Authorization", "Bearer " + PAYPAL_TOKEN)
                .retrieve()
                .body(Map.class);
        return String.valueOf(resp.get("status"));
    }

    public String fetchCardChargeStatus(Order order) {
        RestClient client = RestClient.builder().baseUrl(props.getCardBaseUrl()).build();
        Map<?, ?> resp = client.get().uri("/v1/charges/{id}", order.externalId)
                .retrieve()
                .body(Map.class);
        return String.valueOf(resp.get("status"));
    }

    @Override
    public String providerStatus(Order order) {
        return switch (order.provider) {
            case "stripe" -> fetchStripeIntentStatus(order);
            case "paypal" -> fetchPayPalOrderStatus(order);
            case "card" -> fetchCardChargeStatus(order);
            default -> null;
        };
    }

    public String createPayPalOrder(Order order) {
        RestClient client = RestClient.builder().baseUrl(props.getPaypalBaseUrl()).build();
        Map<?, ?> resp = client.post().uri("/v2/checkout/orders")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + PAYPAL_TOKEN)
                .body(Map.of(
                        "intent", "CAPTURE",
                        "purchase_units", List.of(Map.of(
                                "reference_id", "default",
                                "custom_id", order.merchantOrderId,
                                "amount", Map.of(
                                        "currency_code", order.currency.toUpperCase(Locale.ROOT),
                                        "value", centsToDecimal(order.amountCents))))))
                .retrieve()
                .body(Map.class);
        return String.valueOf(resp.get("id"));
    }

    public void capturePayPalOrder(Order order) {
        RestClient client = RestClient.builder().baseUrl(props.getPaypalBaseUrl()).build();
        client.post().uri("/v2/checkout/orders/{id}/capture", order.externalId)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + PAYPAL_TOKEN)
                .retrieve()
                .toBodilessEntity();
    }

    public Map<?, ?> createCardCharge(Order order, String cardNumber) {
        RestClient client = RestClient.builder().baseUrl(props.getCardBaseUrl()).build();
        return client.post().uri("/v1/charges")
                .header("Content-Type", "application/json")
                .body(Map.of(
                        "merchant_order_id", order.merchantOrderId,
                        "amount_cents", order.amountCents,
                        "currency", order.currency,
                        "card", Map.of(
                                "number", cardNumber,
                                "exp_month", "12",
                                "exp_year", "2030",
                                "cvc", "123")))
                .retrieve()
                .body(Map.class);
    }

    public Map<?, ?> challengeCardCharge(Order order, String result) {
        RestClient client = RestClient.builder().baseUrl(props.getCardBaseUrl()).build();
        return client.post().uri("/v1/charges/{id}/challenge", order.externalId)
                .header("Content-Type", "application/json")
                .body(Map.of("result", result))
                .retrieve()
                .body(Map.class);
    }

    public void captureCardCharge(Order order) {
        RestClient client = RestClient.builder().baseUrl(props.getCardBaseUrl()).build();
        client.post().uri("/v1/charges/{id}/capture", order.externalId)
                .retrieve()
                .toBodilessEntity();
    }

    public String createCryptoAddress(Order order) {
        RestClient client = RestClient.builder().baseUrl(props.getCryptoBaseUrl()).build();
        Map<?, ?> resp = client.post().uri("/v1/deposit/address")
                .header("Content-Type", "application/json")
                .body(Map.of(
                        "currency", order.currency,
                        "merchant_order_id", order.merchantOrderId))
                .retrieve()
                .body(Map.class);
        return String.valueOf(resp.get("address"));
    }

    private static String centsToDecimal(long cents) {
        return String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100);
    }
}
