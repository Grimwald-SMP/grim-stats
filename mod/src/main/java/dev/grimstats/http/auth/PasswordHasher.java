package dev.grimstats.http.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2-WithHmacSHA256 password hashing using only the JDK (no bcrypt dependency).
 *
 * <p>Salts and hashes are exchanged as base64 strings so they live comfortably in the JSON config.
 * Verification is constant-time to avoid timing side channels.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public record Hashed(String hashBase64, String saltBase64, int iterations) {
    }

    public static Hashed hash(String password, int iterations) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, iterations);
        return new Hashed(
                Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(salt),
                iterations);
    }

    public static boolean verify(String password, String hashBase64, String saltBase64, int iterations) {
        if (password == null || hashBase64 == null || hashBase64.isEmpty()
                || saltBase64 == null || saltBase64.isEmpty()) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        byte[] expected = Base64.getDecoder().decode(hashBase64);
        byte[] actual = pbkdf2(password.toCharArray(), salt, iterations);
        return constantTimeEquals(expected, actual);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("PBKDF2 hashing failed", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /** Generates a random base64 secret, used for the session-token signing key. */
    public static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Generates a URL-safe random token of the given byte length (for API key ids and secrets). */
    public static String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * Fast SHA-256 hash (base64) for high-entropy secrets such as API keys, which do not need the
     * slow key-stretching that low-entropy passwords require.
     */
    public static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time comparison of two base64 strings. */
    public static boolean matches(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return constantTimeEquals(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
