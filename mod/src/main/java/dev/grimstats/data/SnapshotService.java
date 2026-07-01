package dev.grimstats.data;

import dev.grimstats.GrimStats;
import dev.grimstats.data.model.StatsSnapshot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Periodically rebuilds the statistics snapshot and publishes it for the HTTP layer.
 *
 * <p>The collector runs on this service's own single daemon thread. It is responsible for hopping to
 * the game thread for the brief, lock-free read of live state and for doing any file I/O (offline
 * players) off the game thread. HTTP threads never touch game state; they only read the published
 * immutable snapshot via {@link #current()}. This split is the core of the "lightweight" design.
 */
public final class SnapshotService implements SnapshotProvider {

    private final Supplier<StatsSnapshot> collector;
    private final AtomicReference<StatsSnapshot> latest = new AtomicReference<>(StatsSnapshot.empty());

    private ScheduledExecutorService scheduler;
    private volatile int intervalSeconds;

    public SnapshotService(Supplier<StatsSnapshot> collector, int intervalSeconds) {
        this.collector = collector;
        this.intervalSeconds = Math.max(1, intervalSeconds);
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "grimstats-snapshot");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::collectSafely, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    @Override
    public StatsSnapshot current() {
        return latest.get();
    }

    @Override
    public void requestRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.execute(this::collectSafely);
        }
    }

    private void collectSafely() {
        try {
            latest.set(collector.get());
        } catch (Exception e) {
            GrimStats.LOGGER.warn("Snapshot collection failed", e);
        }
    }
}
