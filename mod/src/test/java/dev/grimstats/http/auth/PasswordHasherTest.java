package dev.grimstats.http.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void hashThenVerifySucceeds() {
        PasswordHasher.Hashed h = PasswordHasher.hash("correct horse battery staple", 10_000);
        assertTrue(PasswordHasher.verify("correct horse battery staple", h.hashBase64(), h.saltBase64(), h.iterations()));
    }

    @Test
    void wrongPasswordFails() {
        PasswordHasher.Hashed h = PasswordHasher.hash("s3cret", 10_000);
        assertFalse(PasswordHasher.verify("s3cre7", h.hashBase64(), h.saltBase64(), h.iterations()));
    }

    @Test
    void emptyOrMissingHashFails() {
        assertFalse(PasswordHasher.verify("x", "", "", 10_000));
        assertFalse(PasswordHasher.verify(null, "a", "b", 10_000));
    }

    @Test
    void saltsAreRandomPerHash() {
        PasswordHasher.Hashed a = PasswordHasher.hash("same", 10_000);
        PasswordHasher.Hashed b = PasswordHasher.hash("same", 10_000);
        assertNotEquals(a.saltBase64(), b.saltBase64());
        assertNotEquals(a.hashBase64(), b.hashBase64());
    }
}
