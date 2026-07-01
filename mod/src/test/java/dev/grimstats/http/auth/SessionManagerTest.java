package dev.grimstats.http.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionManagerTest {

    private SessionManager newManager(int ttlMinutes) {
        return new SessionManager(PasswordHasher.randomSecret(), ttlMinutes);
    }

    @Test
    void issuedTokenValidatesWithPrincipal() {
        SessionManager sm = newManager(60);
        String token = sm.issue("root", Role.ROOT);
        Principal p = sm.validate(token);
        assertNotNull(p);
        assertEquals("root", p.name());
        assertEquals(Role.ROOT, p.role());
        assertEquals(Principal.Kind.SESSION, p.kind());
    }

    @Test
    void tamperedTokenRejected() {
        SessionManager sm = newManager(60);
        String token = sm.issue("admin", Role.ADMIN);
        assertNull(sm.validate(token + "x"));
        assertNull(sm.validate("garbage"));
        assertNull(sm.validate(""));
        assertNull(sm.validate(null));
    }

    @Test
    void revokedTokenRejected() {
        SessionManager sm = newManager(60);
        String token = sm.issue("admin", Role.ADMIN);
        sm.revoke(token);
        assertNull(sm.validate(token));
    }

    @Test
    void expiredTokenRejected() {
        SessionManager sm = newManager(0); // ttl 0 minutes -> already expired
        String token = sm.issue("admin", Role.ADMIN);
        assertNull(sm.validate(token));
    }

    @Test
    void tokenFromDifferentSecretRejected() {
        SessionManager a = newManager(60);
        SessionManager b = newManager(60);
        String token = a.issue("admin", Role.ADMIN);
        assertNull(b.validate(token));
    }
}
