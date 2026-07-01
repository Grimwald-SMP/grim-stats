package dev.grimstats.season;

import dev.grimstats.data.model.ObjectiveInfo;
import dev.grimstats.data.model.PlayerStats;
import dev.grimstats.data.model.StatsSnapshot;
import dev.grimstats.data.model.WorldInfo;

import java.util.List;

/**
 * A frozen archive of a world's statistics, exported as a standalone JSON document. Seasons let a
 * server show off past worlds ("season 1", "season 2") after a reset, and the format is portable:
 * a file exported on one server can be imported on another.
 *
 * <p>{@code formatVersion} guards future shape changes. The document is intentionally plain data
 * (same model records the live API serves) so an export is lossless: full per-player stat maps,
 * highlights and scoreboard objectives.
 */
public record Season(
        int formatVersion,
        String id,
        String name,
        long createdAtEpochMs,
        String serverName,
        String minecraftVersion,
        long gameTime,
        List<PlayerStats> players,
        List<ObjectiveInfo> objectives) {

    public static final int CURRENT_FORMAT = 1;

    /** Freezes the given live snapshot into a season named {@code name}. */
    public static Season fromSnapshot(String name, StatsSnapshot snapshot, long now) {
        WorldInfo w = snapshot.world();
        return new Season(
                CURRENT_FORMAT,
                SeasonStore.newId(name, now),
                name,
                now,
                w.serverName(),
                w.minecraftVersion(),
                w.gameTime(),
                snapshot.players(),
                snapshot.objectives());
    }

    /** Lightweight listing view; the full player data stays on disk until a season is opened. */
    public record Info(
            String id,
            String name,
            long createdAtEpochMs,
            String serverName,
            String minecraftVersion,
            long gameTime,
            int playerCount,
            long totalPlayTimeTicks) {
    }

    public Info info() {
        long playTime = 0;
        for (PlayerStats p : players) {
            if (p.highlights() != null) {
                playTime += p.highlights().playTime();
            }
        }
        return new Info(id, name, createdAtEpochMs, serverName, minecraftVersion, gameTime,
                players.size(), playTime);
    }
}
