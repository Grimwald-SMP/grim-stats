package dev.grimstats.http;

import com.sun.net.httpserver.HttpServer;
import dev.grimstats.GrimStats;
import dev.grimstats.config.ConfigManager;
import dev.grimstats.config.GrimStatsConfig;
import dev.grimstats.data.SnapshotProvider;
import dev.grimstats.http.auth.SessionManager;
import dev.grimstats.season.SeasonStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps the JDK {@link HttpServer}, serving the JSON API ({@code /api}) and the bundled dashboard
 * (everything else) from a single port.
 *
 * <p>Uses a small, elastic thread pool: a couple of always-available threads for API calls plus
 * on-demand threads that absorb long-lived SSE connections without starving regular requests, all
 * reaped when idle so the footprint stays minimal.
 */
public final class WebServer {

    private final ConfigManager config;
    private final SessionManager sessions;
    private final SnapshotProvider snapshots;
    private final SeasonStore seasons;

    private HttpServer server;
    private ExecutorService executor;

    public WebServer(ConfigManager config, SessionManager sessions, SnapshotProvider snapshots,
                     SeasonStore seasons) {
        this.config = config;
        this.sessions = sessions;
        this.snapshots = snapshots;
        this.seasons = seasons;
    }

    public void start() throws IOException {
        GrimStatsConfig cfg = config.get();
        server = HttpServer.create(new InetSocketAddress(cfg.http.host, cfg.http.port), 0);

        int core = Math.max(2, cfg.http.threadPoolSize);
        AtomicInteger n = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                core, core + 32, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
                r -> {
                    Thread t = new Thread(r, "grimstats-http-" + n.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        ((ThreadPoolExecutor) executor).allowCoreThreadTimeOut(true);
        server.setExecutor(executor);

        server.createContext("/api", new ApiHandler(config, sessions, snapshots, seasons));
        server.createContext("/", new StaticHandler(config, snapshots));

        server.start();
        // 0.0.0.0 means "all interfaces"; print localhost as the clickable URL in that case.
        String displayHost = "0.0.0.0".equals(cfg.http.host) ? "localhost" : cfg.http.host;
        GrimStats.LOGGER.info("GrimStats dashboard listening on {}:{} (http://{}:{})",
                cfg.http.host, cfg.http.port, displayHost, cfg.http.port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
