package dev.grimstats.data.model;

import java.util.List;

/** Server / world summary. {@code seed} is null unless admin-exposed. */
public record WorldInfo(
        String serverName,
        String motd,
        String minecraftVersion,
        int onlinePlayers,
        int maxPlayers,
        long gameTime,
        long dayTime,
        String difficulty,
        boolean hardcore,
        Long seed,
        List<DimensionInfo> dimensions) {

    public static WorldInfo unknown() {
        return new WorldInfo("unknown", "", "", 0, 0, 0L, 0L, "normal", false, null, List.of());
    }

    public record DimensionInfo(String id, long time) {
    }
}
