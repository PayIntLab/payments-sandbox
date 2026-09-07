package io.pqa.sandbox.card;

import java.time.YearMonth;
import java.util.Map;

/**
 * Deterministic test-card rules. Card numbers mirror the common acquirer
 * test cards so integration code can be exercised without a real gateway.
 */
public final class CardChargeRules {
    public static final String APPROVE = "approve";
    public static final String DECLINE = "decline";
    public static final String REQUIRE_3DS = "require_3ds";

    private CardChargeRules() {
    }

    public static Map<String, String> classify(String number, String expMonth,
                                               String expYear, String cvc) {
        String last4 = number.length() >= 4 ? number.substring(number.length() - 4) : number;
        String brand = number.startsWith("4") ? "visa"
                : number.startsWith("5") ? "mastercard"
                : number.startsWith("3") ? "amex" : "unknown";

        YearMonth now = YearMonth.now();
        int month = parseInt(expMonth);
        int year = parseInt(expYear);
        if (year < now.getYear()
                || (year == now.getYear() && month < now.getMonthValue())) {
            return Map.of("decision", DECLINE, "reason", "expired_card",
                    "last4", last4, "brand", brand);
        }
        if ("000".equals(cvc)) {
            return Map.of("decision", DECLINE, "reason", "incorrect_cvc",
                    "last4", last4, "brand", brand);
        }
        if (number.equals("4000000000000002")) {
            return Map.of("decision", DECLINE, "reason", "insufficient_funds",
                    "last4", last4, "brand", brand);
        }
        if (number.equals("4000000000009995")) {
            return Map.of("decision", DECLINE, "reason", "expired_card",
                    "last4", last4, "brand", brand);
        }
        if (number.equals("4000000000000069")) {
            return Map.of("decision", DECLINE, "reason", "processing_error",
                    "last4", last4, "brand", brand);
        }
        if (number.equals("4000000000000027")) {
            return Map.of("decision", REQUIRE_3DS, "reason", "",
                    "last4", last4, "brand", brand);
        }
        return Map.of("decision", APPROVE, "reason", "",
                "last4", last4, "brand", brand);
    }

    private static int parseInt(String v) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return 0;
        }
    }
}
