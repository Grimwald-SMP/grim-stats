package dev.grimstats.world;

import java.nio.file.Path;

/**
 * Resolves on-disk locations of save data that GrimStats may read for offline players.
 *
 * <p>This is the explicit fallback layer for the Minecraft 26.1 world-storage overhaul. The
 * <em>primary</em> strategy elsewhere is to ask the running game for paths via its own
 * {@code LevelResource}/{@code getWorldPath} API, which automatically follows file moves on a
 * recompile. This interface exists for the cases where we scan the directory directly and for
 * unit-testing the path logic without the game on the classpath.
 *
 * @see LegacyLayout 1.21.x and earlier
 * @see NamespacedLayout 26.1+ (dimensions/, players/, namespaced data)
 */
public interface WorldLayout {

    /** Directory holding per-player stats JSON files ({@code <uuid>.json}). */
    Path statsDir(Path worldRoot);

    /** Directory holding per-player advancement JSON files. */
    Path advancementsDir(Path worldRoot);

    /** Directory holding per-player NBT data ({@code <uuid>.dat}). */
    Path playerDataDir(Path worldRoot);

    /** The scoreboard data file. */
    Path scoreboardFile(Path worldRoot);

    /** Root of a single dimension's region data (used for basic dimension discovery). */
    Path dimensionRoot(Path worldRoot, String namespace, String dimensionPath);

    /** Human-readable id for logging/diagnostics. */
    String id();

    /**
     * Detects the appropriate layout for an existing world directory. Presence of a {@code players/}
     * directory (or a {@code dimensions/} directory) indicates the 26.1 namespaced layout.
     */
    static WorldLayout detect(Path worldRoot) {
        Path players = worldRoot.resolve("players");
        Path dimensions = worldRoot.resolve("dimensions");
        if (java.nio.file.Files.isDirectory(players) || java.nio.file.Files.isDirectory(dimensions)) {
            return new NamespacedLayout();
        }
        return new LegacyLayout();
    }
}
