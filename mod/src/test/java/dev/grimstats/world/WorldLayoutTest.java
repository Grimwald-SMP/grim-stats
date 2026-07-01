package dev.grimstats.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies path resolution for both the legacy (1.21.x) and namespaced (26.1) world layouts. */
class WorldLayoutTest {

    private final Path world = Path.of("world");

    @Test
    void legacyLayoutResolvesVanillaPaths() {
        WorldLayout layout = new LegacyLayout();
        assertEquals(world.resolve("stats"), layout.statsDir(world));
        assertEquals(world.resolve("advancements"), layout.advancementsDir(world));
        assertEquals(world.resolve("playerdata"), layout.playerDataDir(world));
        assertEquals(world.resolve("data").resolve("scoreboard.dat"), layout.scoreboardFile(world));
        assertEquals(world, layout.dimensionRoot(world, "minecraft", "overworld"));
        assertEquals(world.resolve("DIM-1"), layout.dimensionRoot(world, "minecraft", "the_nether"));
        assertEquals(world.resolve("DIM1"), layout.dimensionRoot(world, "minecraft", "the_end"));
    }

    @Test
    void namespacedLayoutResolves261Paths() {
        WorldLayout layout = new NamespacedLayout();
        assertEquals(world.resolve("players").resolve("stats"), layout.statsDir(world));
        assertEquals(world.resolve("players").resolve("advancements"), layout.advancementsDir(world));
        assertEquals(world.resolve("players").resolve("playerdata"), layout.playerDataDir(world));
        assertEquals(world.resolve("data").resolve("minecraft").resolve("scoreboard.dat"),
                layout.scoreboardFile(world));
        assertEquals(world.resolve("dimensions").resolve("minecraft").resolve("overworld"),
                layout.dimensionRoot(world, "minecraft", "overworld"));
    }

    @Test
    void moddedDimensionsAlwaysUnderDimensions() {
        assertEquals(world.resolve("dimensions").resolve("mymod").resolve("void"),
                new LegacyLayout().dimensionRoot(world, "mymod", "void"));
        assertEquals(world.resolve("dimensions").resolve("mymod").resolve("void"),
                new NamespacedLayout().dimensionRoot(world, "mymod", "void"));
    }

    @Test
    void detectChoosesLegacyForPlainWorld(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("stats"));
        Files.createDirectories(dir.resolve("region"));
        assertInstanceOf(LegacyLayout.class, WorldLayout.detect(dir));
    }

    @Test
    void detectChoosesNamespacedWhenPlayersDirPresent(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("players").resolve("stats"));
        assertInstanceOf(NamespacedLayout.class, WorldLayout.detect(dir));
    }

    @Test
    void detectChoosesNamespacedWhenDimensionsDirPresent(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("dimensions").resolve("minecraft").resolve("overworld"));
        WorldLayout layout = WorldLayout.detect(dir);
        assertInstanceOf(NamespacedLayout.class, layout);
        assertTrue(layout.id().contains("26.1"));
    }
}
