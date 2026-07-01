package dev.grimstats.data.model;

import java.util.List;

/**
 * A pinned statistic surfaced on the homepage as a small leaderboard: the top players for a single
 * stat (identified by its stat-type id and key). Computed server-side during snapshot collection so
 * the homepage never needs every player's full stat map.
 */
public record PinnedStat(String statType, String statKey, List<Entry> entries) {

    public record Entry(String uuid, String name, long value) {
    }
}
