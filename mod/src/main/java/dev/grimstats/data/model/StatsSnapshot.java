package dev.grimstats.data.model;

import java.util.List;

/**
 * Immutable point-in-time view of all collected statistics. Built off-thread on a schedule and
 * published into an {@code AtomicReference}; HTTP threads serialize it directly with Gson.
 *
 * <p>All members are plain data (no Minecraft types) so this whole package compiles and is testable
 * without the game on the classpath, and so the file structure of the API stays decoupled from
 * mapping changes.
 */
public record StatsSnapshot(
        long generatedAtEpochMs,
        WorldInfo world,
        List<PlayerStats> players,
        List<ObjectiveInfo> objectives,
        List<StatTypeInfo> statTypes,
        List<PinnedStat> pinnedStats) {

    public static StatsSnapshot empty() {
        return new StatsSnapshot(System.currentTimeMillis(), WorldInfo.unknown(),
                List.of(), List.of(), List.of(), List.of());
    }

    /** Returns the player with the given uuid, or null. */
    public PlayerStats findPlayer(String uuid) {
        for (PlayerStats p : players) {
            if (p.uuid().equalsIgnoreCase(uuid)) {
                return p;
            }
        }
        return null;
    }
}
