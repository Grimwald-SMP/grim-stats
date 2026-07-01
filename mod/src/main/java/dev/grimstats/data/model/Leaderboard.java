package dev.grimstats.data.model;

import java.util.List;

/** A ranked list of players for a single statistic, computed on demand from the cached snapshot. */
public record Leaderboard(
        String statType,
        String statKey,
        int count,
        long total,
        List<Entry> entries) {

    public record Entry(int rank, String uuid, String name, long value) {
    }
}
