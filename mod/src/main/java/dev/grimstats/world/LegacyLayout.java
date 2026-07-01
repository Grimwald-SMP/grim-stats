package dev.grimstats.world;

import java.nio.file.Path;

/**
 * World layout for Minecraft 1.21.x and earlier:
 * <pre>
 *   &lt;world&gt;/stats/&lt;uuid&gt;.json
 *   &lt;world&gt;/advancements/&lt;uuid&gt;.json
 *   &lt;world&gt;/playerdata/&lt;uuid&gt;.dat
 *   &lt;world&gt;/data/scoreboard.dat
 *   &lt;world&gt;/DIM-1, &lt;world&gt;/DIM1, region/ (overworld at root)
 * </pre>
 */
public final class LegacyLayout implements WorldLayout {

    @Override
    public Path statsDir(Path worldRoot) {
        return worldRoot.resolve("stats");
    }

    @Override
    public Path advancementsDir(Path worldRoot) {
        return worldRoot.resolve("advancements");
    }

    @Override
    public Path playerDataDir(Path worldRoot) {
        return worldRoot.resolve("playerdata");
    }

    @Override
    public Path scoreboardFile(Path worldRoot) {
        return worldRoot.resolve("data").resolve("scoreboard.dat");
    }

    @Override
    public Path dimensionRoot(Path worldRoot, String namespace, String dimensionPath) {
        // Vanilla dimensions live at the root in the legacy layout.
        if ("minecraft".equals(namespace)) {
            return switch (dimensionPath) {
                case "overworld" -> worldRoot;
                case "the_nether" -> worldRoot.resolve("DIM-1");
                case "the_end" -> worldRoot.resolve("DIM1");
                default -> worldRoot.resolve("dimensions").resolve(namespace).resolve(dimensionPath);
            };
        }
        return worldRoot.resolve("dimensions").resolve(namespace).resolve(dimensionPath);
    }

    @Override
    public String id() {
        return "legacy-1.21";
    }
}
