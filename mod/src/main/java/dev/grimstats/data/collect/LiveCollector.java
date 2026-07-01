package dev.grimstats.data.collect;

import dev.grimstats.data.model.ObjectiveInfo;
import dev.grimstats.data.model.PlayerHighlights;
import dev.grimstats.data.model.PlayerStats;
import dev.grimstats.data.model.StatTypeInfo;
import dev.grimstats.data.model.StatsSnapshot;
import dev.grimstats.data.model.WorldInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link StatsSnapshot} from live server state.
 *
 * <p>Everything is registry-driven: stat types and their keys come from {@link BuiltInRegistries},
 * so anything added by a mod or datapack is included automatically with no hardcoded list. This
 * runs on the server thread (scheduled by {@code SnapshotService}) and only performs cheap in-memory
 * map lookups, keeping the tick cost negligible.
 *
 * <p>Offline-player stats are layered in separately by {@link OfflinePlayerReader} to avoid blocking
 * the game thread on file I/O.
 */
public final class LiveCollector {

    private final MinecraftServer server;

    public LiveCollector(MinecraftServer server) {
        this.server = server;
    }

    public StatsSnapshot collect() {
        long now = System.currentTimeMillis();
        // Pinned leaderboards are computed by CombinedCollector once the full player list (incl.
        // offline players) is assembled, so they are left empty here.
        return new StatsSnapshot(now, collectWorld(), collectOnlinePlayers(), collectObjectives(),
                collectStatTypes(), List.of());
    }

    // ----- world ---------------------------------------------------------------------

    private WorldInfo collectWorld() {
        ServerLevel overworld = server.overworld();
        List<WorldInfo.DimensionInfo> dims = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            dims.add(new WorldInfo.DimensionInfo(level.dimension().identifier().toString(), level.getDayTime()));
        }
        String name = server.getWorldData().getLevelName();
        String difficulty = server.getWorldData().getDifficulty().getKey();
        long seed = overworld.getSeed();
        return new WorldInfo(
                name,
                server.getMotd(),
                server.getServerVersion(),
                server.getPlayerList().getPlayerCount(),
                server.getMaxPlayers(),
                overworld.getGameTime(),
                overworld.getDayTime(),
                difficulty,
                server.getWorldData().isHardcore(),
                seed,
                dims);
    }

    // ----- players -------------------------------------------------------------------

    private List<PlayerStats> collectOnlinePlayers() {
        List<PlayerStats> out = new ArrayList<>();
        Scoreboard scoreboard = server.getScoreboard();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String uuid = player.getGameProfile().id().toString();
            String name = player.getGameProfile().name();
            Map<String, Map<String, Long>> stats = readStats(player.getStats());
            List<PlayerStats.Score> scores = readScores(scoreboard, player.getScoreboardName());
            out.add(new PlayerStats(uuid, name, true, System.currentTimeMillis(),
                    PlayerHighlights.from(stats), stats, scores));
        }
        return out;
    }

    /** Reads every stat value for a counter, grouped by stat-type id. */
    public static Map<String, Map<String, Long>> readStats(ServerStatsCounter counter) {
        Map<String, Map<String, Long>> byType = new LinkedHashMap<>();
        for (StatType<?> type : BuiltInRegistries.STAT_TYPE) {
            Identifier typeId = BuiltInRegistries.STAT_TYPE.getKey(type);
            if (typeId == null) {
                continue;
            }
            Map<String, Long> values = readStatType(counter, type);
            if (!values.isEmpty()) {
                byType.put(typeId.toString(), values);
            }
        }
        return byType;
    }

    private static <T> Map<String, Long> readStatType(ServerStatsCounter counter, StatType<T> type) {
        Map<String, Long> values = new LinkedHashMap<>();
        Registry<T> registry = type.getRegistry();
        for (T value : registry) {
            Stat<T> stat = type.get(value);
            int v = counter.getValue(stat);
            if (v != 0) {
                Identifier key = registry.getKey(value);
                if (key != null) {
                    values.put(key.toString(), (long) v);
                }
            }
        }
        return values;
    }

    private static List<PlayerStats.Score> readScores(Scoreboard scoreboard, String holderName) {
        List<PlayerStats.Score> scores = new ArrayList<>();
        for (Objective objective : scoreboard.getObjectives()) {
            ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(holderName), objective);
            if (info != null) {
                scores.add(new PlayerStats.Score(objective.getName(), info.value()));
            }
        }
        return scores;
    }

    // ----- scoreboard ----------------------------------------------------------------

    private List<ObjectiveInfo> collectObjectives() {
        Scoreboard scoreboard = server.getScoreboard();
        List<ObjectiveInfo> out = new ArrayList<>();
        for (Objective objective : scoreboard.getObjectives()) {
            List<ObjectiveInfo.Entry> entries = new ArrayList<>();
            for (ScoreHolder holder : scoreboard.getTrackedPlayers()) {
                ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(holder, objective);
                if (info != null) {
                    entries.add(new ObjectiveInfo.Entry(holder.getScoreboardName(), info.value()));
                }
            }
            entries.sort((a, b) -> Integer.compare(b.value(), a.value()));
            out.add(new ObjectiveInfo(
                    objective.getName(),
                    objective.getDisplayName().getString(),
                    objective.getCriteria().getName(),
                    objective.getRenderType().getId(),
                    entries));
        }
        return out;
    }

    // ----- stat type registry --------------------------------------------------------

    private List<StatTypeInfo> collectStatTypes() {
        List<StatTypeInfo> out = new ArrayList<>();
        for (StatType<?> type : BuiltInRegistries.STAT_TYPE) {
            Identifier typeId = BuiltInRegistries.STAT_TYPE.getKey(type);
            if (typeId == null) {
                continue;
            }
            out.add(new StatTypeInfo(
                    typeId.toString(),
                    "stat_type." + typeId.getNamespace() + "." + typeId.getPath(),
                    !"minecraft".equals(typeId.getNamespace()),
                    collectStatKeys(type)));
        }
        return out;
    }

    private <T> List<String> collectStatKeys(StatType<T> type) {
        List<String> keys = new ArrayList<>();
        Registry<T> registry = type.getRegistry();
        for (T value : registry) {
            Identifier key = registry.getKey(value);
            if (key != null) {
                keys.add(key.toString());
            }
        }
        return keys;
    }
}
