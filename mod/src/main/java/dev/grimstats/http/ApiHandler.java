package dev.grimstats.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.grimstats.GrimStats;
import dev.grimstats.config.ConfigManager;
import dev.grimstats.config.GrimStatsConfig;
import dev.grimstats.data.SnapshotProvider;
import dev.grimstats.data.model.Leaderboard;
import dev.grimstats.data.model.ObjectiveInfo;
import dev.grimstats.data.model.PinnedStat;
import dev.grimstats.data.model.PlayerStats;
import dev.grimstats.data.model.StatsSnapshot;
import dev.grimstats.data.model.WorldInfo;
import dev.grimstats.http.auth.PasswordHasher;
import dev.grimstats.http.auth.Principal;
import dev.grimstats.http.auth.Role;
import dev.grimstats.http.auth.SessionManager;
import dev.grimstats.season.Season;
import dev.grimstats.season.SeasonStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Routes and serves all {@code /api/...} endpoints off the cached snapshot, config and auth. */
public final class ApiHandler implements HttpHandler {

    // A dummy hash of the right shape so login runs an equal-cost verify for unknown usernames,
    // keeping response time from revealing whether an account exists.
    private static final String DUMMY_HASH = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String DUMMY_SALT = Base64.getEncoder().encodeToString(new byte[16]);

    // Live-stream connection caps: bound total open streams and streams per client so anonymous
    // callers cannot exhaust the (bounded) HTTP thread pool by holding many SSE connections.
    private static final int MAX_STREAMS_TOTAL = 64;
    private static final int MAX_STREAMS_PER_HOST = 4;

    private final ConfigManager config;
    private final SessionManager sessions;
    private final SnapshotProvider snapshots;
    private final SeasonStore seasons;

    // Per-client login throttle: at most 10 attempts per minute.
    private final RateLimiter loginLimiter = new RateLimiter(10, 60_000L);
    private final AtomicInteger openStreams = new AtomicInteger();
    private final Map<String, Integer> streamsPerHost = new ConcurrentHashMap<>();

    public ApiHandler(ConfigManager config, SessionManager sessions, SnapshotProvider snapshots,
                      SeasonStore seasons) {
        this.config = config;
        this.sessions = sessions;
        this.snapshots = snapshots;
        this.seasons = seasons;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        GrimStatsConfig cfg = config.get();
        Http.applyCors(exchange, cfg.http.corsAllowAll, cfg.http.corsOrigins);
        String method = exchange.getRequestMethod();
        if (method.equals("OPTIONS")) {
            Http.noContent(exchange, 204);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        try {
            route(exchange, method, path, cfg);
        } catch (Http.BodyTooLargeException tooLarge) {
            if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                Http.error(exchange, 413, "request body too large");
            }
        } catch (Exception e) {
            GrimStats.LOGGER.error("Error handling {} {}", method, path, e);
            if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                Http.error(exchange, 500, "internal error");
            }
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange, String method, String path, GrimStatsConfig cfg) throws IOException {
        // A token in the query string is only honored for the SSE stream (EventSource cannot set
        // headers). Everywhere else, credentials must be in the Authorization header so tokens do
        // not leak into logs, proxies or browser history.
        Principal principal = authenticate(exchange, cfg, path.equals("/api/stream"));
        boolean admin = principal != null;

        // Public read endpoints (gated by publicDashboard unless admin). /api/ui and the login/health
        // endpoints stay reachable even when private so the login page can still theme itself.
        if (!admin && !cfg.display.publicDashboard && path.startsWith("/api/")
                && !path.equals("/api/admin/login") && !path.equals("/api/health") && !path.equals("/api/ui")) {
            Http.error(exchange, 403, "dashboard is private");
            return;
        }

        switch (path) {
            case "/api/health" -> health(exchange);
            case "/api/ui" -> ui(exchange, cfg);
            case "/api/world" -> requireGet(exchange, method, () -> world(exchange, cfg, admin));
            case "/api/players" -> requireGet(exchange, method, () -> players(exchange, cfg, admin));
            case "/api/scoreboard" -> requireGet(exchange, method, () -> scoreboard(exchange, cfg, admin));
            case "/api/pinned" -> requireGet(exchange, method, () -> pinned(exchange, cfg, admin));
            case "/api/leaderboard" -> requireGet(exchange, method, () -> leaderboard(exchange, cfg, admin));
            case "/api/stats/registry" -> requireGet(exchange, method, () -> registry(exchange, cfg, admin));
            case "/api/stream" -> requireGet(exchange, method, () -> stream(exchange, cfg, admin));
            case "/api/seasons" -> requireGet(exchange, method, () -> Http.json(exchange, 200, seasons.list()));
            case "/api/admin/seasons/export" -> requireAdminPost(exchange, method, admin,
                    () -> exportSeason(exchange));
            case "/api/admin/seasons/import" -> requireAdminPost(exchange, method, admin,
                    () -> importSeason(exchange));
            case "/api/admin/login" -> requirePost(exchange, method, () -> login(exchange, cfg));
            case "/api/admin/logout" -> requirePost(exchange, method, () -> logout(exchange));
            case "/api/admin/me" -> requireGet(exchange, method, () -> me(exchange, principal));
            case "/api/admin/password" -> requireAdmin(exchange, method, "PUT", principal,
                    () -> changePassword(exchange, principal));
            case "/api/admin/users" -> adminUsers(exchange, method, principal);
            case "/api/admin/apikeys" -> adminApiKeys(exchange, method, principal);
            case "/api/admin/refresh" -> requireAdminPost(exchange, method, admin, () -> {
                snapshots.requestRefresh();
                Http.json(exchange, 200, Map.of("ok", true));
            });
            case "/api/admin/config" -> adminConfig(exchange, method, admin);
            default -> {
                if (path.startsWith("/api/players/")) {
                    requireGet(exchange, method, () -> player(exchange, path, cfg, admin));
                } else if (path.startsWith("/api/seasons/")) {
                    season(exchange, method, path.substring("/api/seasons/".length()), cfg, admin);
                } else if (path.startsWith("/api/admin/seasons/")) {
                    adminSeason(exchange, method, path.substring("/api/admin/seasons/".length()), admin);
                } else if (path.startsWith("/api/admin/users/")) {
                    adminUser(exchange, method, path.substring("/api/admin/users/".length()), principal);
                } else if (path.startsWith("/api/admin/apikeys/")) {
                    adminApiKey(exchange, method, path.substring("/api/admin/apikeys/".length()), principal);
                } else {
                    Http.error(exchange, 404, "not found");
                }
            }
        }
    }

    // ----- endpoints -----------------------------------------------------------------

    private void health(HttpExchange exchange) throws IOException {
        StatsSnapshot snap = snapshots.current();
        JsonObject body = new JsonObject();
        body.addProperty("status", "ok");
        body.addProperty("mod", "grimstats");
        body.addProperty("snapshotAge", System.currentTimeMillis() - snap.generatedAtEpochMs());
        body.addProperty("players", snap.players().size());
        Http.json(exchange, 200, body);
    }

    /** Public UI bootstrap: the admin-chosen default theme and whether the dashboard is public. */
    private void ui(HttpExchange exchange, GrimStatsConfig cfg) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("defaultTheme", cfg.display.defaultTheme);
        body.addProperty("publicDashboard", cfg.display.publicDashboard);
        Http.json(exchange, 200, body);
    }

    private void world(HttpExchange exchange, GrimStatsConfig cfg, boolean admin) throws IOException {
        WorldInfo w = snapshots.current().world();
        if (!(admin && cfg.display.exposeSeed)) {
            w = new WorldInfo(w.serverName(), w.motd(), w.minecraftVersion(), w.onlinePlayers(),
                    w.maxPlayers(), w.gameTime(), w.dayTime(), w.difficulty(), w.hardcore(), null, w.dimensions());
        }
        Http.json(exchange, 200, w);
    }

    private void players(HttpExchange exchange, GrimStatsConfig cfg, boolean admin) throws IOException {
        List<PlayerStats> out = new ArrayList<>();
        for (PlayerStats p : snapshots.current().players()) {
            out.add(filterPlayer(p, cfg, admin, /* summaryOnly */ true));
            if (out.size() >= cfg.collection.maxPlayers) {
                break;
            }
        }
        Http.json(exchange, 200, out);
    }

    private void player(HttpExchange exchange, String path, GrimStatsConfig cfg, boolean admin) throws IOException {
        String uuid = path.substring("/api/players/".length());
        PlayerStats p = snapshots.current().findPlayer(uuid);
        if (p == null) {
            Http.error(exchange, 404, "player not found");
            return;
        }
        Http.json(exchange, 200, filterPlayer(p, cfg, admin, false));
    }

    private void scoreboard(HttpExchange exchange, GrimStatsConfig cfg, boolean admin) throws IOException {
        List<ObjectiveInfo> out = new ArrayList<>();
        for (ObjectiveInfo o : snapshots.current().objectives()) {
            if (!admin && cfg.display.hiddenObjectives.contains(o.name())) {
                continue;
            }
            out.add(o);
        }
        Http.json(exchange, 200, out);
    }

    private void pinned(HttpExchange exchange, GrimStatsConfig cfg, boolean admin) throws IOException {
        Http.json(exchange, 200, visiblePinned(cfg, admin));
    }

    /** On-demand ranking for any single stat: /api/leaderboard?type=<statType>&key=<statKey>&limit=<n>. */
    private void leaderboard(HttpExchange exchange, GrimStatsConfig cfg, boolean admin) throws IOException {
        Map<String, String> q = queryParams(exchange);
        String statType = q.get("type");
        String statKey = q.get("key");
        if (statType == null || statKey == null || statType.isBlank() || statKey.isBlank()) {
            Http.error(exchange, 400, "type and key are required");
            return;
        }
        if (!admin && cfg.display.hiddenStatTypes.contains(statType)) {
            Http.error(exchange, 403, "stat type is hidden");
            return;
        }
        int limit = parseInt(q.get("limit"), 50);

        List<Leaderboard.Entry> entries = new ArrayList<>();
        long total = 0;
        for (PlayerStats p : snapshots.current().players()) {
            Map<String, Long> byKey = p.stats().get(statType);
            if (byKey == null) {
                continue;
            }
            Long value = byKey.get(statKey);
            if (value != null && value > 0) {
                total += value;
                entries.add(new Leaderboard.Entry(0, p.uuid(), p.name(), value));
            }
        }
        entries.sort((a, b) -> Long.compare(b.value(), a.value()));
        int count = entries.size();
        List<Leaderboard.Entry> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < limit; i++) {
            Leaderboard.Entry e = entries.get(i);
            ranked.add(new Leaderboard.Entry(i + 1, e.uuid(), e.name(), e.value()));
        }
        Http.json(exchange, 200, new Leaderboard(statType, statKey, count, total, ranked));
    }

    private void registry(HttpExchange exchange, GrimStatsConfig cfg, boolean admin) throws IOException {
        var types = snapshots.current().statTypes();
        if (admin) {
            Http.json(exchange, 200, types);
            return;
        }
        var visible = types.stream().filter(t -> !cfg.display.hiddenStatTypes.contains(t.id())).toList();
        Http.json(exchange, 200, visible);
    }

    private void stream(HttpExchange exchange, GrimStatsConfig cfg, boolean admin) throws IOException {
        String host = clientHost(exchange);
        if (!acquireStream(host)) {
            Http.error(exchange, 503, "too many live connections; retry shortly");
            return;
        }
        try {
            new SseStream(snapshots, () -> filteredSnapshot(cfg, admin)).run(exchange);
        } finally {
            releaseStream(host);
        }
    }

    /** Reserves a stream slot within the global and per-host caps; false if either is exceeded. */
    private boolean acquireStream(String host) {
        if (openStreams.incrementAndGet() > MAX_STREAMS_TOTAL) {
            openStreams.decrementAndGet();
            return false;
        }
        int perHost = streamsPerHost.merge(host, 1, Integer::sum);
        if (perHost > MAX_STREAMS_PER_HOST) {
            decHost(host);
            openStreams.decrementAndGet();
            return false;
        }
        return true;
    }

    private void releaseStream(String host) {
        openStreams.decrementAndGet();
        decHost(host);
    }

    /** Decrements a host's stream count, removing the entry at zero so the map stays bounded. */
    private void decHost(String host) {
        streamsPerHost.merge(host, -1, (oldValue, dec) -> oldValue + dec <= 0 ? null : oldValue + dec);
    }

    // ----- seasons (archived world stats) ----------------------------------------------

    /** Uploaded season documents are capped so a bad upload cannot exhaust server memory. */
    private static final int MAX_SEASON_BYTES = 32 * 1024 * 1024;

    private void season(HttpExchange exchange, String method, String id, GrimStatsConfig cfg, boolean admin)
            throws IOException {
        if (!method.equals("GET")) {
            Http.error(exchange, 405, "method not allowed");
            return;
        }
        Season season = seasons.get(id);
        if (season == null) {
            Http.error(exchange, 404, "season not found");
            return;
        }
        // Seasons honor the same visibility rules as live data for non-admins: hidden stat types are
        // stripped from each player and hidden objectives are dropped entirely.
        if (!admin && (!cfg.display.hiddenStatTypes.isEmpty() || !cfg.display.hiddenObjectives.isEmpty())) {
            List<PlayerStats> players = new ArrayList<>(season.players().size());
            for (PlayerStats p : season.players()) {
                players.add(filterPlayer(p, cfg, false, false));
            }
            List<ObjectiveInfo> objectives = new ArrayList<>();
            for (ObjectiveInfo o : season.objectives()) {
                if (!cfg.display.hiddenObjectives.contains(o.name())) {
                    objectives.add(o);
                }
            }
            season = new Season(season.formatVersion(), season.id(), season.name(),
                    season.createdAtEpochMs(), season.serverName(), season.minecraftVersion(),
                    season.gameTime(), players, objectives);
        }
        if ("1".equals(queryParams(exchange).get("download"))) {
            // The id is server-generated ([a-z0-9-]), so it is header-safe as a filename.
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"grimstats-" + season.id() + ".json\"");
        }
        Http.json(exchange, 200, season);
    }

    private void exportSeason(HttpExchange exchange) throws IOException {
        SeasonExport req = Http.GSON.fromJson(Http.readBody(exchange), SeasonExport.class);
        String name = req == null || req.name == null || req.name.isBlank()
                ? "Season " + java.time.LocalDate.now()
                : req.name.trim();
        Season season = Season.fromSnapshot(name, snapshots.current(), System.currentTimeMillis());
        seasons.save(season);
        Http.json(exchange, 201, season.info());
    }

    private void importSeason(HttpExchange exchange) throws IOException {
        // Bounded read: refuses (413) before buffering anything larger than the cap into memory.
        String body = Http.readBody(exchange, MAX_SEASON_BYTES);
        try {
            Season imported = seasons.importJson(body, System.currentTimeMillis());
            Http.json(exchange, 201, imported.info());
        } catch (IllegalArgumentException e) {
            Http.error(exchange, 400, e.getMessage());
        }
    }

    private void adminSeason(HttpExchange exchange, String method, String id, boolean admin) throws IOException {
        if (!admin) {
            Http.error(exchange, 401, "unauthorized");
            return;
        }
        if (!method.equals("DELETE")) {
            Http.error(exchange, 405, "method not allowed");
            return;
        }
        if (!seasons.delete(id)) {
            Http.error(exchange, 404, "season not found");
            return;
        }
        Http.json(exchange, 200, Map.of("ok", true));
    }

    private record SeasonExport(String name) {
    }

    // ----- admin ---------------------------------------------------------------------

    private void login(HttpExchange exchange, GrimStatsConfig cfg) throws IOException {
        // Throttle before doing any parsing or hashing so brute force is cheap to reject.
        if (!loginLimiter.tryAcquire(clientHost(exchange))) {
            Http.error(exchange, 429, "too many login attempts; wait a minute and try again");
            return;
        }
        LoginRequest req = Http.GSON.fromJson(Http.readBody(exchange), LoginRequest.class);
        if (req == null || cfg.auth.users.isEmpty()) {
            Http.error(exchange, 401, "no admin configured; run /grimstats setup <user> <password> in-game");
            return;
        }
        GrimStatsConfig.User user = findUser(cfg, req.username);
        String password = req.password == null ? "" : req.password;
        boolean ok;
        if (user == null) {
            // Spend equivalent PBKDF2 work against a dummy so timing does not enumerate usernames.
            PasswordHasher.verify(password, DUMMY_HASH, DUMMY_SALT, cfg.auth.iterations);
            ok = false;
        } else {
            ok = PasswordHasher.verify(password, user.passwordHash, user.passwordSalt, user.iterations);
        }
        if (!ok) {
            Http.error(exchange, 401, "invalid credentials");
            return;
        }
        Role role = Role.parse(user.role, Role.ADMIN);
        Http.json(exchange, 200, Map.of(
                "token", sessions.issue(user.username, role),
                "username", user.username,
                "role", role.name()));
    }

    private void logout(HttpExchange exchange) throws IOException {
        sessions.revoke(bearer(exchange, false));
        Http.json(exchange, 200, Map.of("ok", true));
    }

    /** Returns the current principal's identity (used to restore session state after a page reload). */
    private void me(HttpExchange exchange, Principal principal) throws IOException {
        if (principal == null) {
            Http.error(exchange, 401, "unauthorized");
            return;
        }
        Http.json(exchange, 200, Map.of(
                "name", principal.name(),
                "role", principal.role().name(),
                "kind", principal.kind().name()));
    }

    private void adminConfig(HttpExchange exchange, String method, boolean admin) throws IOException {
        if (!admin) {
            Http.error(exchange, 401, "unauthorized");
            return;
        }
        if (method.equals("GET")) {
            Http.json(exchange, 200, ConfigView.from(config.get()));
        } else if (method.equals("PUT")) {
            ConfigView view = Http.GSON.fromJson(Http.readBody(exchange), ConfigView.class);
            if (view == null) {
                Http.error(exchange, 400, "invalid body");
                return;
            }
            GrimStatsConfig cfg = config.get();
            view.applyTo(cfg);
            config.update(cfg);
            Http.json(exchange, 200, ConfigView.from(cfg));
        } else {
            Http.error(exchange, 405, "method not allowed");
        }
    }

    // ----- users (root manages; any admin may self-change password) -------------------

    private record UserView(String username, String role) {
    }

    private void changePassword(HttpExchange exchange, Principal principal) throws IOException {
        if (!principal.isSession()) {
            Http.error(exchange, 403, "API keys cannot change a password");
            return;
        }
        PasswordChange req = Http.GSON.fromJson(Http.readBody(exchange), PasswordChange.class);
        if (req == null || req.newPassword == null || req.newPassword.length() < 4) {
            Http.error(exchange, 400, "new password must be at least 4 characters");
            return;
        }
        GrimStatsConfig cfg = config.get();
        GrimStatsConfig.User user = findUser(cfg, principal.name());
        if (user == null) {
            Http.error(exchange, 404, "user no longer exists");
            return;
        }
        if (!PasswordHasher.verify(req.currentPassword, user.passwordHash, user.passwordSalt, user.iterations)) {
            Http.error(exchange, 403, "current password is incorrect");
            return;
        }
        setPassword(user, req.newPassword, cfg.auth.iterations);
        config.update(cfg);
        Http.json(exchange, 200, Map.of("ok", true));
    }

    private void adminUsers(HttpExchange exchange, String method, Principal principal) throws IOException {
        if (principal == null) {
            Http.error(exchange, 401, "unauthorized");
            return;
        }
        GrimStatsConfig cfg = config.get();
        if (method.equals("GET")) {
            // Any admin may see who has access.
            List<UserView> out = new ArrayList<>();
            for (GrimStatsConfig.User u : cfg.auth.users) {
                out.add(new UserView(u.username, u.role));
            }
            Http.json(exchange, 200, out);
        } else if (method.equals("POST")) {
            if (!principal.isRoot()) {
                Http.error(exchange, 403, "only ROOT can add users");
                return;
            }
            UserUpsert req = Http.GSON.fromJson(Http.readBody(exchange), UserUpsert.class);
            if (req == null || req.username == null || req.username.isBlank()
                    || req.password == null || req.password.length() < 4) {
                Http.error(exchange, 400, "username and a password of 4+ characters are required");
                return;
            }
            if (findUser(cfg, req.username) != null) {
                Http.error(exchange, 409, "a user with that name already exists");
                return;
            }
            GrimStatsConfig.User user = new GrimStatsConfig.User();
            user.username = req.username.trim();
            user.role = Role.parse(req.role, Role.ADMIN).name();
            setPassword(user, req.password, cfg.auth.iterations);
            cfg.auth.users.add(user);
            config.update(cfg);
            Http.json(exchange, 201, new UserView(user.username, user.role));
        } else {
            Http.error(exchange, 405, "method not allowed");
        }
    }

    private void adminUser(HttpExchange exchange, String method, String username, Principal principal)
            throws IOException {
        if (principal == null) {
            Http.error(exchange, 401, "unauthorized");
            return;
        }
        if (!principal.isRoot()) {
            Http.error(exchange, 403, "only ROOT can manage users");
            return;
        }
        GrimStatsConfig cfg = config.get();
        GrimStatsConfig.User user = findUser(cfg, username);
        if (user == null) {
            Http.error(exchange, 404, "user not found");
            return;
        }
        if (method.equals("PUT")) {
            UserUpsert req = Http.GSON.fromJson(Http.readBody(exchange), UserUpsert.class);
            if (req == null) {
                Http.error(exchange, 400, "invalid body");
                return;
            }
            if (req.role != null && !req.role.isBlank()) {
                Role newRole = Role.parse(req.role, Role.ADMIN);
                // Do not allow demoting the last ROOT.
                if (Role.parse(user.role, Role.ADMIN) == Role.ROOT && newRole != Role.ROOT && countRoots(cfg) <= 1) {
                    Http.error(exchange, 409, "cannot demote the last ROOT user");
                    return;
                }
                user.role = newRole.name();
            }
            if (req.password != null && !req.password.isBlank()) {
                if (req.password.length() < 4) {
                    Http.error(exchange, 400, "password must be at least 4 characters");
                    return;
                }
                setPassword(user, req.password, cfg.auth.iterations);
            }
            config.update(cfg);
            Http.json(exchange, 200, new UserView(user.username, user.role));
        } else if (method.equals("DELETE")) {
            if (Role.parse(user.role, Role.ADMIN) == Role.ROOT && countRoots(cfg) <= 1) {
                Http.error(exchange, 409, "cannot remove the last ROOT user");
                return;
            }
            cfg.auth.users.remove(user);
            config.update(cfg);
            Http.json(exchange, 200, Map.of("ok", true));
        } else {
            Http.error(exchange, 405, "method not allowed");
        }
    }

    // ----- API keys (admin creates; ROOT keys require ROOT) ---------------------------

    private record ApiKeyView(String id, String name, String role, String preview, String createdBy,
                              long createdAtEpochMs, Long lastUsedEpochMs) {
        static ApiKeyView of(GrimStatsConfig.ApiKey k) {
            return new ApiKeyView(k.id, k.name, k.role, k.preview, k.createdBy, k.createdAtEpochMs, k.lastUsedEpochMs);
        }
    }

    private void adminApiKeys(HttpExchange exchange, String method, Principal principal) throws IOException {
        if (principal == null) {
            Http.error(exchange, 401, "unauthorized");
            return;
        }
        GrimStatsConfig cfg = config.get();
        if (method.equals("GET")) {
            List<ApiKeyView> out = new ArrayList<>();
            for (GrimStatsConfig.ApiKey k : cfg.auth.apiKeys) {
                out.add(ApiKeyView.of(k));
            }
            Http.json(exchange, 200, out);
        } else if (method.equals("POST")) {
            ApiKeyCreate req = Http.GSON.fromJson(Http.readBody(exchange), ApiKeyCreate.class);
            if (req == null || req.name == null || req.name.isBlank()) {
                Http.error(exchange, 400, "a key name is required");
                return;
            }
            Role role = Role.parse(req.role, Role.ADMIN);
            if (role == Role.ROOT && !principal.isRoot()) {
                Http.error(exchange, 403, "only ROOT can create ROOT keys");
                return;
            }
            String id = PasswordHasher.randomToken(6);
            String secret = PasswordHasher.randomToken(24);
            String full = "gsk_" + id + "_" + secret;

            GrimStatsConfig.ApiKey key = new GrimStatsConfig.ApiKey();
            key.id = id;
            key.name = req.name.trim();
            key.role = role.name();
            key.hash = PasswordHasher.sha256(secret);
            key.preview = full.substring(0, Math.min(12, full.length())) + "…";
            key.createdBy = principal.name();
            key.createdAtEpochMs = System.currentTimeMillis();
            cfg.auth.apiKeys.add(key);
            config.update(cfg);

            // The full secret is returned exactly once here and never stored in plaintext.
            JsonObject body = new JsonObject();
            body.addProperty("key", full);
            body.addProperty("id", key.id);
            body.addProperty("name", key.name);
            body.addProperty("role", key.role);
            Http.json(exchange, 201, body);
        } else {
            Http.error(exchange, 405, "method not allowed");
        }
    }

    private void adminApiKey(HttpExchange exchange, String method, String id, Principal principal)
            throws IOException {
        if (principal == null) {
            Http.error(exchange, 401, "unauthorized");
            return;
        }
        if (!method.equals("DELETE")) {
            Http.error(exchange, 405, "method not allowed");
            return;
        }
        GrimStatsConfig cfg = config.get();
        GrimStatsConfig.ApiKey key = cfg.auth.apiKeys.stream().filter(k -> k.id.equals(id)).findFirst().orElse(null);
        if (key == null) {
            Http.error(exchange, 404, "key not found");
            return;
        }
        // ROOT may revoke any key; an admin may revoke only keys they created.
        if (!principal.isRoot() && !principal.name().equals(key.createdBy)) {
            Http.error(exchange, 403, "you can only revoke keys you created");
            return;
        }
        cfg.auth.apiKeys.remove(key);
        config.update(cfg);
        Http.json(exchange, 200, Map.of("ok", true));
    }

    // ----- helpers -------------------------------------------------------------------

    private StatsSnapshot filteredSnapshot(GrimStatsConfig cfg, boolean admin) {
        StatsSnapshot s = snapshots.current();
        List<PlayerStats> players = new ArrayList<>();
        for (PlayerStats p : s.players()) {
            players.add(filterPlayer(p, cfg, admin, true));
        }
        List<ObjectiveInfo> objectives = new ArrayList<>();
        for (ObjectiveInfo o : s.objectives()) {
            if (admin || !cfg.display.hiddenObjectives.contains(o.name())) {
                objectives.add(o);
            }
        }
        return new StatsSnapshot(s.generatedAtEpochMs(), s.world(), players, objectives, s.statTypes(),
                visiblePinned(cfg, admin));
    }

    /** Pinned leaderboards, dropping any whose stat type is hidden from non-admins. */
    private List<PinnedStat> visiblePinned(GrimStatsConfig cfg, boolean admin) {
        List<PinnedStat> out = new ArrayList<>();
        for (PinnedStat p : snapshots.current().pinnedStats()) {
            if (admin || !cfg.display.hiddenStatTypes.contains(p.statType())) {
                out.add(p);
            }
        }
        return out;
    }

    private PlayerStats filterPlayer(PlayerStats p, GrimStatsConfig cfg, boolean admin, boolean summaryOnly) {
        Map<String, Map<String, Long>> stats = p.stats();
        if (!admin && !cfg.display.hiddenStatTypes.isEmpty()) {
            Map<String, Map<String, Long>> filtered = new java.util.LinkedHashMap<>();
            for (var e : stats.entrySet()) {
                if (!cfg.display.hiddenStatTypes.contains(e.getKey())) {
                    filtered.put(e.getKey(), e.getValue());
                }
            }
            stats = filtered;
        }
        if (summaryOnly) {
            stats = Map.of();
        }
        return new PlayerStats(p.uuid(), p.name(), p.online(), p.lastSeenEpochMs(), p.highlights(), stats, p.scores());
    }

    /**
     * Resolves the authenticated principal from a session token or an API key, or null if neither.
     * API keys are formatted {@code gsk_<id>_<secret>}; the id gives an O(1) lookup, then the secret
     * is checked with a fast SHA-256 compare (keys are high-entropy).
     */
    private Principal authenticate(HttpExchange exchange, GrimStatsConfig cfg, boolean allowQueryToken) {
        String token = bearer(exchange, allowQueryToken);
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (token.startsWith("gsk_")) {
            return authenticateApiKey(token, cfg);
        }
        return sessions.validate(token);
    }

    private Principal authenticateApiKey(String token, GrimStatsConfig cfg) {
        String rest = token.substring("gsk_".length());
        int sep = rest.indexOf('_');
        if (sep <= 0) {
            return null;
        }
        String id = rest.substring(0, sep);
        String secret = rest.substring(sep + 1);
        for (GrimStatsConfig.ApiKey key : cfg.auth.apiKeys) {
            if (key.id.equals(id) && PasswordHasher.matches(key.hash, PasswordHasher.sha256(secret))) {
                // Best-effort last-used timestamp; persisted with the next config write.
                key.lastUsedEpochMs = System.currentTimeMillis();
                return new Principal(key.name, Role.parse(key.role, Role.ADMIN), Principal.Kind.API_KEY);
            }
        }
        return null;
    }

    private static GrimStatsConfig.User findUser(GrimStatsConfig cfg, String username) {
        if (username == null) {
            return null;
        }
        for (GrimStatsConfig.User u : cfg.auth.users) {
            if (u.username.equalsIgnoreCase(username)) {
                return u;
            }
        }
        return null;
    }

    private static int countRoots(GrimStatsConfig cfg) {
        int n = 0;
        for (GrimStatsConfig.User u : cfg.auth.users) {
            if (Role.parse(u.role, Role.ADMIN) == Role.ROOT) {
                n++;
            }
        }
        return n;
    }

    private static void setPassword(GrimStatsConfig.User user, String password, int iterations) {
        PasswordHasher.Hashed h = PasswordHasher.hash(password, iterations);
        user.passwordHash = h.hashBase64();
        user.passwordSalt = h.saltBase64();
        user.iterations = h.iterations();
    }

    private static String bearer(HttpExchange exchange, boolean allowQueryToken) {
        String h = exchange.getRequestHeaders().getFirst("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            return h.substring("Bearer ".length()).trim();
        }
        // EventSource (SSE) cannot send headers, so the stream endpoint alone accepts ?token=...
        return allowQueryToken ? queryParams(exchange).get("token") : null;
    }

    /** Client address used as the key for rate limiting and per-host stream caps. */
    private static String clientHost(HttpExchange exchange) {
        var remote = exchange.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }

    private static Map<String, String> queryParams(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty()) {
            return Map.of();
        }
        Map<String, String> params = new java.util.HashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = java.net.URLDecoder.decode(pair.substring(0, eq), java.nio.charset.StandardCharsets.UTF_8);
                String v = java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                params.put(k, v);
            }
        }
        return params;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private interface IoRunnable {
        void run() throws IOException;
    }

    private void requireGet(HttpExchange exchange, String method, IoRunnable body) throws IOException {
        if (!method.equals("GET")) {
            Http.error(exchange, 405, "method not allowed");
            return;
        }
        body.run();
    }

    private void requirePost(HttpExchange exchange, String method, IoRunnable body) throws IOException {
        if (!method.equals("POST")) {
            Http.error(exchange, 405, "method not allowed");
            return;
        }
        body.run();
    }

    private void requireAdminPost(HttpExchange exchange, String method, boolean admin, IoRunnable body) throws IOException {
        if (!admin) {
            Http.error(exchange, 401, "unauthorized");
            return;
        }
        requirePost(exchange, method, body);
    }

    /** Requires an authenticated principal and a specific HTTP method. */
    private void requireAdmin(HttpExchange exchange, String method, String required, Principal principal,
                              IoRunnable body) throws IOException {
        if (principal == null) {
            Http.error(exchange, 401, "unauthorized");
            return;
        }
        if (!method.equals(required)) {
            Http.error(exchange, 405, "method not allowed");
            return;
        }
        body.run();
    }

    private record LoginRequest(String username, String password) {
    }

    private record PasswordChange(String currentPassword, String newPassword) {
    }

    private record UserUpsert(String username, String password, String role) {
    }

    private record ApiKeyCreate(String name, String role) {
    }
}
