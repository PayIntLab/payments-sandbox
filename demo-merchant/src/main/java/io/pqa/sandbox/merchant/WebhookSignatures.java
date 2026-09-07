package io.pqa.sandbox.merchant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class WebhookSignatures {
    private WebhookSignatures() {
    }

    public static boolean verifyStripe(String header, String rawBody, String secret) {
        if (header == null || rawBody == null) {
            return false;
        }
        String expectedV1 = null;
        String timestamp = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && "t".equals(kv[0])) {
                timestamp = kv[1];
            } else if (kv.length == 2 && "v1".equals(kv[0])) {
                expectedV1 = kv[1];
            }
        }
        if (timestamp == null || expectedV1 == null) {
            return false;
        }
        String actual = hmacHex(secret, timestamp + "." + rawBody);
        return constantTimeEquals(actual, expectedV1);
    }

    public static boolean verifyPayPal(String transmissionId, String transmissionTime,
                                       String signature, String webhookId,
                                       String rawBody, String secret) {
        if (transmissionId == null || transmissionTime == null || signature == null
                || webhookId == null || rawBody == null) {
            return false;
        }
        String message = transmissionId + "|" + transmissionTime + "|" + webhookId + "|" + rawBody;
        return constantTimeEquals(hmacHex(secret, message), signature);
    }

    static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
