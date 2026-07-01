package dev.grimstats.http.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and validates HMAC-signed session tokens for admin access.
 *
 * <p>Tokens are of the form {@code <id>.<expiryEpochSeconds>.<hmac>}. The HMAC binds the id and
 * expiry to a server-side secret, so tokens cannot be forged without it. A small in-memory set of
 * live ids lets us revoke individual sessions (e.g. on logout) without persistence.
 */
public final class SessionManager {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private record Session(long expiry, String username, Role role) {
    }

    private final byte[] secret;
    private final long ttlSeconds;
    private final Map<String, Session> liveSessions = new ConcurrentHashMap<>();

    public SessionManager(String secretBase64, int ttlMinutes) {
        this.secret = Base64.getDecoder().decode(secretBase64);
        this.ttlSeconds = ttlMinutes * 60L;
    }

    /** Issues a signed session token bound to a user and role. */
    public String issue(String username, Role role) {
        String id = newId();
        long expiry = nowSeconds() + ttlSeconds;
        liveSessions.put(id, new Session(expiry, username, role));
        pruneExpired();
        String payload = id + "." + expiry;
        return payload + "." + sign(payload);
    }

    /** Validates a token, returning the authenticated {@link Principal} or null. */
    public Principal validate(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        int lastDot = token.lastIndexOf('.');
        if (lastDot <= 0) {
            return null;
        }
        String payload = token.substring(0, lastDot);
        String sig = token.substring(lastDot + 1);
        if (!constantTimeEquals(sig, sign(payload))) {
            return null;
        }
        String[] parts = payload.split("\\.");
        if (parts.length != 2) {
            return null;
        }
        String id = parts[0];
        long expiry;
        try {
            expiry = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        // Boundary is exclusive: a token is valid only while now is strictly before expiry.
        if (nowSeconds() >= expiry) {
            liveSessions.remove(id);
            return null;
        }
        Session session = liveSessions.get(id);
        if (session == null || session.expiry() != expiry) {
            return null;
        }
        return new Principal(session.username(), session.role(), Principal.Kind.SESSION);
    }

    public void revoke(String token) {
        if (token == null) {
            return;
        }
        String[] parts = token.split("\\.");
        if (parts.length >= 1) {
            liveSessions.remove(parts[0]);
        }
    }

    private void pruneExpired() {
        long now = nowSeconds();
        liveSessions.entrySet().removeIf(e -> e.getValue().expiry() < now);
    }

    private String newId() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return B64.encodeToString(bytes);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return B64.encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign session token", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < ab.length; i++) {
            r |= ab[i] ^ bb[i];
        }
        return r == 0;
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }
}
