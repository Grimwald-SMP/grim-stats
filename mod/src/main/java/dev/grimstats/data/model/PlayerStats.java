package dev.grimstats.data.model;

import java.util.List;
import java.util.Map;

/**
 * Per-player statistics.
 *
 * <p>{@code stats} maps a stat-type id (e.g. {@code "minecraft:custom"}) to a map of the stat key
 * (e.g. {@code "minecraft:play_time"}) to its long value. This nested shape mirrors how Minecraft
 * groups statistics by {@code StatType}, and automatically carries any modded/datapack stat types
 * because it is built by iterating the registry rather than a fixed list.
 */
public record PlayerStats(
        String uuid,
        String name,
        boolean online,
        Long lastSeenEpochMs,
        PlayerHighlights highlights,
        Map<String, Map<String, Long>> stats,
        List<Score> scores) {

    /** A single scoreboard score for this player. */
    public record Score(String objective, int value) {
    }
}
