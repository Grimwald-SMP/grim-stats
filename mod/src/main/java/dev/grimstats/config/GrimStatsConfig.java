package dev.grimstats.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration object, serialized to/from {@code config/grimstats.json} with Gson.
 * All fields are public with sensible defaults so a missing/partial file still produces a
 * usable configuration.
 */
public class GrimStatsConfig {

    public Http http = new Http();
    public Auth auth = new Auth();
    public Display display = new Display();
    public Collection collection = new Collection();

    public static class Http {
        /** Bind address. 0.0.0.0 listens on all interfaces so the dashboard is reachable remotely. */
        public String host = "0.0.0.0";
        public int port = 8765;
        /**
         * When true, the API answers CORS requests from any origin (wildcard). Safe because auth is
         * token-based (no cookies). Set false to restrict to {@link #corsOrigins}.
         */
        public boolean corsAllowAll = true;
        /** Origins allowed for CORS when {@link #corsAllowAll} is false (also used by the Vite dev server). */
        public List<String> corsOrigins = defaultCorsOrigins();
        /** Size of the HTTP worker thread pool. Kept small to stay lightweight. */
        public int threadPoolSize = 4;

        private static List<String> defaultCorsOrigins() {
            List<String> l = new ArrayList<>();
            l.add("http://localhost:5173");
            l.add("http://127.0.0.1:5173");
            return l;
        }
    }

    public static class Auth {
        /** Dashboard users. The first user created (via /grimstats setup) is a ROOT. */
        public List<User> users = new ArrayList<>();
        /** API keys for programmatic access. Secrets are stored only as SHA-256 hashes. */
        public List<ApiKey> apiKeys = new ArrayList<>();
        /** Session lifetime in minutes. */
        public int sessionTtlMinutes = 720;
        /** Server-side secret used to sign session tokens; generated on first run. */
        public String tokenSecret = "";
        /** Default PBKDF2 iteration count for new passwords. */
        public int iterations = 210_000;

        // --- Legacy single-admin fields, migrated into `users` on load. Kept for compatibility. ---
        public String username = "";
        public String passwordHash = "";
        public String passwordSalt = "";
    }

    /** A dashboard user account. Passwords are stored as PBKDF2 hashes (slow, for low-entropy input). */
    public static class User {
        public String username = "";
        public String passwordHash = "";
        public String passwordSalt = "";
        public int iterations = 210_000;
        /** "ROOT" or "ADMIN". */
        public String role = "ADMIN";
    }

    /**
     * An API key. The secret is high-entropy so it is stored as a fast SHA-256 hash (no need for
     * PBKDF2). {@code id} is a public lookup handle; the full secret is shown only once at creation.
     */
    public static class ApiKey {
        public String id = "";
        public String name = "";
        /** Base64 SHA-256 of the secret. */
        public String hash = "";
        /** First few characters of the secret, for display only. */
        public String preview = "";
        /** "ROOT" or "ADMIN". */
        public String role = "ADMIN";
        public String createdBy = "";
        public long createdAtEpochMs = 0;
        public Long lastUsedEpochMs = null;
    }

    public static class Display {
        /** daisyUI theme name applied by default in the dashboard. */
        public String defaultTheme = "grimwald";
        /** Whether non-admin visitors may view the dashboard at all. */
        public boolean publicDashboard = true;
        /** Stat-type ids (e.g. "minecraft:custom") hidden from non-admins. */
        public List<String> hiddenStatTypes = new ArrayList<>();
        /** Scoreboard objective names hidden from non-admins. */
        public List<String> hiddenObjectives = new ArrayList<>();
        /** Whether to expose the world seed (admin only regardless). */
        public boolean exposeSeed = false;
        /** Stats pinned to the homepage, rendered as small leaderboards in this order. */
        public List<PinnedRef> pinned = new ArrayList<>();
    }

    /** A reference to a single statistic by its stat-type id and key (e.g. minecraft:custom / minecraft:play_time). */
    public static class PinnedRef {
        public String statType = "";
        public String statKey = "";

        public PinnedRef() {
        }

        public PinnedRef(String statType, String statKey) {
            this.statType = statType;
            this.statKey = statKey;
        }
    }

    public static class Collection {
        /** How often the snapshot is rebuilt, in seconds. */
        public int snapshotIntervalSeconds = 10;
        /** Whether to scan save files for offline players. */
        public boolean includeOfflinePlayers = true;
        /** Maximum number of players returned in list endpoints. */
        public int maxPlayers = 500;
    }
}
