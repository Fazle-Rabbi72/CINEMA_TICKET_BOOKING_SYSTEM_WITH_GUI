package net;

import db.DatabaseConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Authenticated line-based TCP frame protocol for real-time CineVault notifications.
 */
public final class SocketProtocol {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private SocketProtocol() {
    }

    public static String generateNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return toHexUpper(bytes);
    }

    public static String signAuth(int userId, String username, String role, String nonce) {
        String payload = userId + "|" + (username == null ? "" : username)
                + "|" + (role == null ? "" : role.toUpperCase(Locale.US))
                + "|" + (nonce == null ? "" : nonce);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec key = new SecretKeySpec(
                    DatabaseConfig.getHmacSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGO
            );
            mac.init(key);
            return toHexUpper(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Socket HMAC error", e);
        }
    }

    public static boolean verifyAuth(int userId, String username, String role,
                                     String nonce, String signatureHex) {
        if (signatureHex == null || signatureHex.isBlank() || nonce == null || nonce.isBlank()) {
            return false;
        }
        String expected = signAuth(userId, username, role, nonce);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHex.trim().toUpperCase(Locale.US).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String buildFrame(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(escape(parts[i] == null ? "" : String.valueOf(parts[i])));
        }
        return sb.toString();
    }

    public static List<String> parseFrame(String line) {
        List<String> fields = new ArrayList<>();
        if (line == null) {
            return fields;
        }
        StringBuilder current = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (escaping) {
                if (ch == 'n') current.append('\n');
                else if (ch == 'r') current.append('\r');
                else if (ch == 'p') current.append('|');
                else current.append(ch);
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else if (ch == '|') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (escaping) {
            current.append('\\');
        }
        fields.add(current.toString());
        return fields;
    }

    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\') sb.append("\\\\");
            else if (ch == '|') sb.append("\\p");
            else if (ch == '\n') sb.append("\\n");
            else if (ch == '\r') sb.append("\\r");
            else sb.append(ch);
        }
        return sb.toString();
    }

    private static String toHexUpper(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02X", b));
        }
        return sb.toString();
    }
}
