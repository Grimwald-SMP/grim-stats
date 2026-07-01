package dev.grimstats.world;

import java.nio.file.Path;

/**
 * World layout for Minecraft 26.1+, which reorganized world storage:
 * <pre>
 *   &lt;world&gt;/players/stats/&lt;uuid&gt;.json
 *   &lt;world&gt;/players/advancements/&lt;uuid&gt;.json
 *   &lt;world&gt;/players/playerdata/&lt;uuid&gt;.dat
 *   &lt;world&gt;/data/minecraft/scoreboard.dat          (data is now namespaced)
 *   &lt;world&gt;/dimensions/minecraft/overworld/...      (all dimensions under dimensions/)
 * </pre>
 *
 * <p>The exact sub-structure under {@code players/} should be confirmed against the 26.1 release
 * when recompiling for it; the constants here encode the documented moves and are isolated so any
 * correction is a one-line change. In practice the collector prefers the game's own
 * {@code getWorldPath(LevelResource)} API, which makes these explicit paths a backstop.
 */
public final class NamespacedLayout implements WorldLayout {

    private static final String PLAYERS = "players";

    @Override
    public Path statsDir(Path worldRoot) {
        return worldRoot.resolve(PLAYERS).resolve("stats");
    }

    @Override
    public Path advancementsDir(Path worldRoot) {
        return worldRoot.resolve(PLAYERS).resolve("advancements");
    }

    @Override
    public Path playerDataDir(Path worldRoot) {
        return worldRoot.resolve(PLAYERS).resolve("playerdata");
    }

    @Override
    public Path scoreboardFile(Path worldRoot) {
        return worldRoot.resolve("data").resolve("minecraft").resolve("scoreboard.dat");
    }

    @Override
    public Path dimensionRoot(Path worldRoot, String namespace, String dimensionPath) {
        return worldRoot.resolve("dimensions").resolve(namespace).resolve(dimensionPath);
    }

    @Override
    public String id() {
        return "namespaced-26.1";
    }
}
