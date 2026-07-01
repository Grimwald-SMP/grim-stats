package dev.grimstats.http.auth;

/**
 * The authenticated identity behind a request: either a logged-in user (session) or an API key.
 * {@code name} is the username or the API key's label. {@code kind} distinguishes the two so that
 * key-based principals are barred from user-only actions (e.g. changing "their" password).
 */
public record Principal(String name, Role role, Kind kind) {

    public enum Kind {
        SESSION,
        API_KEY
    }

    public boolean isRoot() {
        return role == Role.ROOT;
    }

    public boolean isSession() {
        return kind == Kind.SESSION;
    }
}
