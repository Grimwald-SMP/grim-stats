package dev.grimstats.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple fixed-window rate limiter keyed by an arbitrary string (used to throttle login attempts
 * per client address). Not distributed and not perfectly smooth, but enough to blunt online
 * brute-force and to bound the cost an anonymous caller can impose.
 *
 * <p>The window map is pruned opportunistically so a flood of distinct keys cannot grow it without
 * bound. Keying on the socket peer means requests behind a shared proxy count together; that is the
 * safe default, since {@code X-Forwarded-For} is client-spoofable.
 */
public final class RateLimiter {

    private static final int MAX_KEYS = 10_000;

    private final int maxEvents;
    private final long windowMs;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxEvents, long windowMs) {
        this.maxEvents = maxEvents;
        this.windowMs = windowMs;
    }

    /** Records one event for {@code key} and returns true if it is within the allowed rate. */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_KEYS) {
            windows.entrySet().removeIf(e -> now - e.getValue().start >= windowMs);
        }
        // compute is atomic per key, so the count update is race-free.
        Window w = windows.compute(key, (k, cur) -> {
            if (cur == null || now - cur.start >= windowMs) {
                return new Window(now);
            }
            cur.count++;
            return cur;
        });
        return w.count <= maxEvents;
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
            this.count = 1;
        }
    }
}
