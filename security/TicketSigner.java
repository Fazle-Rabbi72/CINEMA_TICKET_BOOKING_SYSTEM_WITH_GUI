package security;

import db.DatabaseConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Generates and verifies HMAC-SHA256 signatures for tamper-proof E-Tickets.
 */
public final class TicketSigner {

    private static final String HMAC_ALGO = "HmacSHA256";

    private TicketSigner() {
    }

    public static String sign(String bookingCode, int userId, int showId,
                              List<String> seatLabels, double totalAmount) {
        String payload = buildPayload(bookingCode, userId, showId, seatLabels, totalAmount);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(
                    DatabaseConfig.getHmacSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGO
            );
            mac.init(keySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHexUpper(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC signing error", e);
        }
    }

    public static boolean verify(String bookingCode, int userId, int showId,
                                 List<String> seatLabels, double totalAmount,
                                 String expectedSignature) {
        if (expectedSignature == null || expectedSignature.isBlank()) {
            return false;
        }
        String recomputed = sign(bookingCode, userId, showId, seatLabels, totalAmount);
        return MessageDigest.isEqual(
                recomputed.getBytes(StandardCharsets.UTF_8),
                expectedSignature.trim().toUpperCase(Locale.US).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildPayload(String bookingCode, int userId, int showId,
                                       List<String> seatLabels, double totalAmount) {
        List<String> sorted = new ArrayList<>();
        if (seatLabels != null) {
            for (String s : seatLabels) {
                if (s != null && !s.isBlank()) {
                    sorted.add(s.trim().toUpperCase(Locale.US));
                }
            }
        }
        Collections.sort(sorted);
        return (bookingCode == null ? "" : bookingCode.trim().toUpperCase(Locale.US))
                + "|" + userId
                + "|" + showId
                + "|" + String.join(",", sorted)
                + "|" + String.format(Locale.US, "%.2f", totalAmount);
    }

    private static String toHexUpper(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02X", b));
        }
        return sb.toString();
    }
}
