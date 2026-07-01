package dev.grimstats.data.collect;

import dev.grimstats.GrimStats;
import dev.grimstats.config.ConfigManager;
import dev.grimstats.data.model.PinnedStat;
import dev.grimstats.data.model.PlayerStats;
import dev.grimstats.data.model.StatsSnapshot;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Produces a complete {@link StatsSnapshot} by combining:
 * <ol>
 *   <li>live state read on the <em>game thread</em> (world, online players, scoreboard, registry), and</li>
 *   <li>offline-player stats read on the calling (snapshot) thread to keep file I/O off the game thread.</li>
 * </ol>
 *
 * <p>Intended to be the {@code Supplier<StatsSnapshot>} handed to {@code SnapshotService}, where it
 * runs on the snapshot thread; the brief game-thread hop is done with {@link MinecraftServer#submit}.
 */
public final class CombinedCollector implements Supplier<StatsSnapshot> {

    private static final long GAME_THREAD_TIMEOUT_SECONDS = 5;
    private static final int MAX_PINNED_ENTRIES = 10;

    private final MinecraftServer server;
    private final ConfigManager config;
    private final LiveCollector live;
    private final OfflinePlayerReader offline;

    public CombinedCollector(MinecraftServer server, ConfigManager config) {
        this.server = server;
        this.config = config;
        this.live = new LiveCollector(server);
        this.offline = new OfflinePlayerReader(server);
    }

    @Override
    public StatsSnapshot get() {
        StatsSnapshot liveSnapshot = collectLiveOnGameThread();
        if (liveSnapshot == null) {
            return StatsSnapshot.empty();
        }

        List<PlayerStats> all = new ArrayList<>(liveSnapshot.players());
        if (config.get().collection.includeOfflinePlayers) {
            Set<UUID> online = new HashSet<>();
            for (PlayerStats p : liveSnapshot.players()) {
                try {
                    online.add(UUID.fromString(p.uuid()));
                } catch (IllegalArgumentException ignored) {
                    // non-uuid holder, skip
                }
            }
            int budget = Math.max(0, config.get().collection.maxPlayers - liveSnapshot.players().size());
            all.addAll(offline.read(online, budget));
        }

        List<PinnedStat> pinned = computePinned(all);
        return new StatsSnapshot(liveSnapshot.generatedAtEpochMs(), liveSnapshot.world(), all,
                liveSnapshot.objectives(), liveSnapshot.statTypes(), pinned);
    }

    /** Builds a top-N leaderboard for each configured pinned stat, across the full player list. */
    private List<PinnedStat> computePinned(List<PlayerStats> players) {
        var refs = config.get().display.pinned;
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<PinnedStat> out = new ArrayList<>();
        for (var ref : refs) {
            if (ref == null || ref.statType == null || ref.statKey == null
                    || ref.statType.isBlank() || ref.statKey.isBlank()) {
                continue;
            }
            List<PinnedStat.Entry> entries = new ArrayList<>();
            for (PlayerStats p : players) {
                Map<String, Long> byKey = p.stats().get(ref.statType);
                if (byKey == null) {
                    continue;
                }
                Long value = byKey.get(ref.statKey);
                if (value != null && value > 0) {
                    entries.add(new PinnedStat.Entry(p.uuid(), p.name(), value));
                }
            }
            entries.sort((a, b) -> Long.compare(b.value(), a.value()));
            if (entries.size() > MAX_PINNED_ENTRIES) {
                entries = new ArrayList<>(entries.subList(0, MAX_PINNED_ENTRIES));
            }
            out.add(new PinnedStat(ref.statType, ref.statKey, entries));
        }
        return out;
    }

    private StatsSnapshot collectLiveOnGameThread() {
        try {
            CompletableFuture<StatsSnapshot> future = server.submit(live::collect);
            return future.get(GAME_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            GrimStats.LOGGER.warn("Live collection on game thread failed/timed out", e);
            return null;
        }
    }
}
