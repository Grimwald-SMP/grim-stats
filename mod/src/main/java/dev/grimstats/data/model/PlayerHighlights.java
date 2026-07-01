package dev.grimstats.data.model;

import java.util.Map;

/**
 * Derived "at a glance" metrics for a player, computed from the raw stat map. These travel with
 * every player (even in summary list responses) so the dashboard can rank, chart and badge players
 * without fetching each one's full stat map.
 */
public record PlayerHighlights(
        long playTime,
        long deaths,
        long deathsToPlayers,
        long mobKills,
        long playerKills,
        long distanceCm,
        long blocksMined,
        long itemsCrafted,
        long damageDealt,
        long damageTaken) {

    private static final String CUSTOM = "minecraft:custom";
    private static final String MINED = "minecraft:mined";
    private static final String CRAFTED = "minecraft:crafted";
    private static final String KILLED_BY = "minecraft:killed_by";

    public static PlayerHighlights empty() {
        return new PlayerHighlights(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static PlayerHighlights from(Map<String, Map<String, Long>> stats) {
        Map<String, Long> custom = stats.getOrDefault(CUSTOM, Map.of());
        long distance = 0;
        for (var e : custom.entrySet()) {
            // Sum movement distances (stored in cm); ignore fall distance, which is not "travel".
            if (e.getKey().endsWith("_one_cm") && !e.getKey().equals("minecraft:fall_one_cm")) {
                distance += e.getValue();
            }
        }
        // "minecraft:killed_by" is keyed by the entity type that killed the player; the player entity
        // key therefore counts deaths caused by other players (PvP deaths).
        Map<String, Long> killedBy = stats.getOrDefault(KILLED_BY, Map.of());
        return new PlayerHighlights(
                custom.getOrDefault("minecraft:play_time", 0L),
                custom.getOrDefault("minecraft:deaths", 0L),
                killedBy.getOrDefault("minecraft:player", 0L),
                custom.getOrDefault("minecraft:mob_kills", 0L),
                custom.getOrDefault("minecraft:player_kills", 0L),
                distance,
                sum(stats.get(MINED)),
                sum(stats.get(CRAFTED)),
                custom.getOrDefault("minecraft:damage_dealt", 0L),
                custom.getOrDefault("minecraft:damage_taken", 0L));
    }

    private static long sum(Map<String, Long> values) {
        if (values == null) {
            return 0;
        }
        long total = 0;
        for (long v : values.values()) {
            total += v;
        }
        return total;
    }
}
