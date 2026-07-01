package dev.grimstats.data.collect;

import dev.grimstats.GrimStats;
import dev.grimstats.data.model.PlayerHighlights;
import dev.grimstats.data.model.PlayerStats;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Reads statistics for players who are not currently online, off the game thread.
 *
 * <p>The stats directory is resolved through the game's own {@link LevelResource} path API via
 * {@link MinecraftServer#getWorldPath}. This is the primary defense against the 26.1 world-storage
 * overhaul: when Minecraft moves these files, its own path constants move with them, so a simple
 * recompile follows the new layout with no code change.
 *
 * <p>Parsed counters are cached by file last-modified time so repeated snapshots do not re-parse
 * unchanged files.
 */
public final class OfflinePlayerReader {

    private final MinecraftServer server;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final UsernameCache usernames;

    public OfflinePlayerReader(MinecraftServer server) {
        this.server = server;
        this.usernames = new UsernameCache(FabricLoader.getInstance().getGameDir());
    }

    private record Cached(long mtime, Map<String, Map<String, Long>> stats) {
    }

    /**
     * Reads offline players, skipping any uuid in {@code excludeOnline}.
     *
     * @param max maximum number of offline players to read this pass
     */
    public List<PlayerStats> read(Set<UUID> excludeOnline, int max) {
        Path statsDir = server.getWorldPath(LevelResource.PLAYER_STATS_DIR);
        if (!Files.isDirectory(statsDir)) {
            return List.of();
        }
        List<PlayerStats> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(statsDir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                if (out.size() >= max) {
                    break;
                }
                UUID uuid = parseUuid(file.getFileName().toString());
                if (uuid == null || excludeOnline.contains(uuid)) {
                    continue;
                }
                PlayerStats stats = readOne(uuid, file);
                if (stats != null) {
                    out.add(stats);
                }
            }
        } catch (Exception e) {
            GrimStats.LOGGER.warn("Failed listing offline player stats in {}", statsDir, e);
        }
        return out;
    }

    private PlayerStats readOne(UUID uuid, Path file) {
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            Cached cached = cache.get(uuid);
            Map<String, Map<String, Long>> stats;
            if (cached != null && cached.mtime() == mtime) {
                stats = cached.stats();
            } else {
                ServerStatsCounter counter = new ServerStatsCounter(server, file);
                stats = LiveCollector.readStats(counter);
                cache.put(uuid, new Cached(mtime, stats));
            }
            String name = usernames.nameFor(uuid);
            return new PlayerStats(uuid.toString(), name, false, mtimeOrNull(file),
                    PlayerHighlights.from(stats), stats, List.of());
        } catch (Exception e) {
            GrimStats.LOGGER.warn("Failed reading offline stats for {}", uuid, e);
            return null;
        }
    }

    private Long mtimeOrNull(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return null;
        }
    }

    private static UUID parseUuid(String fileName) {
        String base = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
        try {
            return UUID.fromString(base);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
