package dev.grimstats.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void allowsUpToLimitThenRejects() {
        RateLimiter limiter = new RateLimiter(3, 60_000L);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"), "4th attempt in the window is throttled");
        assertFalse(limiter.tryAcquire("a"));
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, 60_000L);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        // A different client is unaffected by another's usage.
        assertTrue(limiter.tryAcquire("b"));
    }

    @Test
    void windowResetsAfterExpiry() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, 20L);
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        Thread.sleep(40L);
        assertTrue(limiter.tryAcquire("a"), "a fresh window allows again");
    }
}
