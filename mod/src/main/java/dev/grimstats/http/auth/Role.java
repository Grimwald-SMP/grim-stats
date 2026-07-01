package dev.grimstats.http.auth;

/**
 * Access level for a principal (a logged-in user or an API key).
 *
 * <ul>
 *   <li>{@link #ROOT} - full control, including managing other users and minting ROOT-level API keys.</li>
 *   <li>{@link #ADMIN} - manage server settings, pins, visibility and their own API keys/password.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    ROOT;

    /** True if this role is at least as privileged as {@code required}. ROOT outranks ADMIN. */
    public boolean satisfies(Role required) {
        if (required == ADMIN) {
            return this == ADMIN || this == ROOT;
        }
        return this == ROOT;
    }

    public static Role parse(String value, Role fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
