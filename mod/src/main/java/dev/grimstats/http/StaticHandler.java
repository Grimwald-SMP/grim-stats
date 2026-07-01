package dev.grimstats.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.grimstats.GrimStats;
import dev.grimstats.config.ConfigManager;
import dev.grimstats.config.GrimStatsConfig;
import dev.grimstats.data.SnapshotProvider;
import dev.grimstats.data.model.PlayerHighlights;
import dev.grimstats.data.model.PlayerStats;
import dev.grimstats.data.model.StatsSnapshot;
import dev.grimstats.http.og.MetaInjector;
import dev.grimstats.http.og.OgImageRenderer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves the bundled dashboard SPA from the mod jar ({@code assets/grimstats/web/...}).
 *
 * <p>Real assets (hashed js/css/fonts) are served verbatim with long caching. Every other path is an
 * app navigation and receives the {@code index.html} shell with per-route Open Graph/Twitter share
 * tags injected (see {@link MetaInjector}) so player and leaderboard links unfurl on Discord. The
 * matching preview images are generated on demand under {@code /og/...} by {@link OgImageRenderer}.
 */
public final class StaticHandler implements HttpHandler {

    private static final String ROOT = "assets/grimstats/web";
    private static final Pattern PLAYER_ROUTE = Pattern.compile("^/players/([^/]+)/?$");
    private static final Pattern OG_PLAYER = Pattern.compile("^/og/player/([^/]+)\\.png$");

    private final ClassLoader loader = StaticHandler.class.getClassLoader();
    private final ConfigManager config;
    private final SnapshotProvider snapshots;

    public StaticHandler(ConfigManager config, SnapshotProvider snapshots) {
        this.config = config;
        this.snapshots = snapshots;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        GrimStatsConfig cfg = config.get();
        Http.applyCors(exchange, cfg.http.corsAllowAll, cfg.http.corsOrigins);
        if (exchange.getRequestMethod().equals("OPTIONS")) {
            Http.noContent(exchange, 204);
            return;
        }
        String path = exchange.getRequestURI().getPath();

        // Generated preview images for social/Discord link unfurls (public, no auth by design).
        if (path.startsWith("/og/")) {
            serveOgImage(exchange, cfg, path);
            return;
        }
        if (path.contains("..")) {
            Http.error(exchange, 400, "bad path");
            return;
        }

        String routePath = (path.isEmpty() || path.equals("/")) ? "/" : path;
        String resourcePath = routePath.equals("/") ? "/index.html" : routePath;

        // Content-hashed static assets: serve as-is. index.html always goes through the shell path.
        boolean isAsset = hasExtension(resourcePath) && !resourcePath.endsWith(".html");
        if (isAsset) {
            byte[] data = read(ROOT + resourcePath);
            if (data == null) {
                Http.error(exchange, 404, "not found");
                return;
            }
            sendAsset(exchange, resourcePath, data);
            return;
        }

        // App navigation (root, client route, or index.html): serve the shell with share metadata.
        byte[] shell = read(ROOT + "/index.html");
        if (shell == null) {
            sendHtml(exchange, 200, placeholder());
            return;
        }
        String html = MetaInjector.inject(new String(shell, StandardCharsets.UTF_8), buildMeta(exchange, cfg, routePath));
        sendHtml(exchange, 200, html.getBytes(StandardCharsets.UTF_8));
    }

    // ----- share metadata -------------------------------------------------------------

    private MetaInjector.Meta buildMeta(HttpExchange exchange, GrimStatsConfig cfg, String routePath) {
        String base = baseUrl(exchange);
        String pageUrl = base + routePath;
        StatsSnapshot snap = snapshots.current();
        String server = snap.world().serverName();
        boolean named = server != null && !server.isBlank() && !"unknown".equalsIgnoreCase(server);
        String siteName = named ? server : "GrimStats";

        // Player and leaderboard cards only when the dashboard is public (private data must not leak
        // to anonymous crawlers). Private dashboards get a generic branded card.
        if (cfg.display.publicDashboard) {
            Matcher pm = PLAYER_ROUTE.matcher(routePath);
            if (pm.matches()) {
                PlayerStats p = snap.findPlayer(decode(pm.group(1)));
                if (p != null) {
                    return new MetaInjector.Meta(
                            p.name() + " • GrimStats",
                            playerDescription(p, siteName),
                            base + "/og/player/" + encode(p.uuid()) + ".png",
                            pageUrl, "profile");
                }
            }
            if (routePath.equals("/leaderboards")) {
                return new MetaInjector.Meta(
                        "Leaderboards • " + siteName,
                        "Player leaderboards and rankings on " + siteName + ".",
                        base + "/og/leaderboard.png", pageUrl, "website");
            }
        }
        return new MetaInjector.Meta(
                named ? "GrimStats • " + server : "GrimStats",
                "World and player statistics" + (named ? " for " + server : "") + ".",
                base + "/og/site.png", pageUrl, "website");
    }

    private static String playerDescription(PlayerStats p, String server) {
        PlayerHighlights h = p.highlights();
        return (p.online() ? "Online" : "Offline") + " on " + server
                + " • " + OgImageRenderer.formatTicks(h.playTime()) + " played"
                + " • " + OgImageRenderer.formatNumber(h.playerKills()) + " player kills"
                + " • " + OgImageRenderer.formatNumber(h.deaths()) + " deaths"
                + " • " + OgImageRenderer.formatNumber(h.blocksMined()) + " blocks mined";
    }

    private void serveOgImage(HttpExchange exchange, GrimStatsConfig cfg, String path) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            Http.error(exchange, 405, "method not allowed");
            return;
        }
        StatsSnapshot snap = snapshots.current();
        try {
            byte[] png;
            Matcher pm = OG_PLAYER.matcher(path);
            if (!cfg.display.publicDashboard) {
                png = OgImageRenderer.site(snap, false); // no player data on private dashboards
            } else if (pm.matches()) {
                PlayerStats p = snap.findPlayer(decode(pm.group(1)));
                png = p != null ? OgImageRenderer.player(p, snap.world()) : OgImageRenderer.site(snap, true);
            } else if (path.equals("/og/leaderboard.png")) {
                png = OgImageRenderer.leaderboard(snap);
            } else if (path.equals("/og/site.png")) {
                png = OgImageRenderer.site(snap, true);
            } else {
                Http.error(exchange, 404, "not found");
                return;
            }
            sendImage(exchange, png);
        } catch (Throwable t) {
            // A failed render must not break the unfurl; the page still has text tags. Log and 500.
            GrimStats.LOGGER.warn("OG image render failed for {}", path, t);
            Http.error(exchange, 500, "image render failed");
        }
    }

    /** Absolute origin for building share URLs, honoring a reverse proxy's forwarded headers. */
    private static String baseUrl(HttpExchange exchange) {
        var headers = exchange.getRequestHeaders();
        String proto = firstNonBlank(headers.getFirst("X-Forwarded-Proto"), "http");
        String host = firstNonBlank(headers.getFirst("X-Forwarded-Host"), headers.getFirst("Host"));
        if (host == null || host.isBlank()) {
            var local = exchange.getLocalAddress();
            host = local.getHostString() + ":" + local.getPort();
        }
        // A forwarded proto/host list may be comma-separated; the first entry is the client-facing one.
        return proto.split(",")[0].trim() + "://" + host.split(",")[0].trim();
    }

    // ----- io -------------------------------------------------------------------------

    private byte[] read(String resource) throws IOException {
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    private static void sendAsset(HttpExchange exchange, String path, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType(path));
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        write(exchange, 200, data);
    }

    private static void sendHtml(HttpExchange exchange, int status, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        // The shell carries per-route/per-player metadata, so it must not be cached across routes.
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        write(exchange, status, data);
    }

    private static void sendImage(HttpExchange exchange, byte[] data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        // Short cache: stats change, but repeated crawler hits within a few minutes can reuse.
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=300");
        write(exchange, 200, data);
    }

    private static void write(HttpExchange exchange, int status, byte[] data) throws IOException {
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static boolean hasExtension(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash;
    }

    private static String contentType(String path) {
        if (path.endsWith(".js") || path.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".woff2")) return "font/woff2";
        String guess = URLConnection.guessContentTypeFromName(path);
        return guess != null ? guess : "application/octet-stream";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static byte[] placeholder() {
        String html = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>GrimStats</title>
                <style>body{font-family:system-ui,sans-serif;margin:3rem auto;max-width:40rem;padding:0 1rem;color:#222}
                code{background:#f2f2f2;padding:.15rem .35rem;border-radius:.25rem}</style></head>
                <body><h1>GrimStats</h1>
                <p>The API is running, but the dashboard was not bundled into this build.</p>
                <p>Build it with <code>./gradlew buildDashboard</code> (or <code>npm run build</code> in
                <code>dashboard/</code>) and rebuild the mod.</p>
                <p>API health: <a href="/api/health">/api/health</a></p>
                </body></html>
                """;
        return html.getBytes(StandardCharsets.UTF_8);
    }
}
