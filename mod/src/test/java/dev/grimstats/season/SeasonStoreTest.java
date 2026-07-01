package dev.grimstats.season;

import dev.grimstats.data.model.PlayerHighlights;
import dev.grimstats.data.model.PlayerStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeasonStoreTest {

    private static Season sample(String name, long now) {
        PlayerStats p = new PlayerStats("uuid-1", "Roc", true, null,
                new PlayerHighlights(1200, 3, 1, 1, 2, 0, 40, 5, 10, 20),
                Map.of("minecraft:custom", Map.of("minecraft:play_time", 1200L)),
                List.of());
        return new Season(Season.CURRENT_FORMAT, SeasonStore.newId(name, now), name, now,
                "TestServer", "1.21.11", 99000, List.of(p), List.of());
    }

    @Test
    void savesListsAndReloadsAcrossInstances(@TempDir Path dir) throws IOException {
        SeasonStore store = new SeasonStore(dir);
        store.load();
        store.save(sample("Season One", 1000));
        store.save(sample("Season Two", 2000));

        SeasonStore reopened = new SeasonStore(dir);
        reopened.load();
        List<Season.Info> list = reopened.list();
        assertEquals(2, list.size());
        // Newest first.
        assertEquals("Season Two", list.get(0).name());
        assertEquals(1, list.get(0).playerCount());
        assertEquals(1200, list.get(0).totalPlayTimeTicks());

        Season full = reopened.get(list.get(1).id());
        assertNotNull(full);
        assertEquals("Roc", full.players().get(0).name());
        assertEquals(1200, full.players().get(0).stats().get("minecraft:custom").get("minecraft:play_time"));
    }

    @Test
    void deleteRemovesFileAndIndex(@TempDir Path dir) throws IOException {
        SeasonStore store = new SeasonStore(dir);
        store.load();
        Season s = store.save(sample("Gone", 1000));
        assertTrue(store.delete(s.id()));
        assertFalse(store.delete(s.id()));
        assertNull(store.get(s.id()));
        assertEquals(0, store.list().size());
    }

    @Test
    void importNormalizesAndRegeneratesId(@TempDir Path dir) throws IOException {
        SeasonStore store = new SeasonStore(dir);
        store.load();
        // Hostile id, no highlights, null objectives: all must be normalized.
        String json = """
                {"formatVersion":1,"id":"../../evil","name":"Old World",
                 "createdAtEpochMs":5000,"serverName":"Elsewhere","minecraftVersion":"1.20.4",
                 "gameTime":123,
                 "players":[{"uuid":"u1","name":"Alex",
                   "stats":{"minecraft:custom":{"minecraft:play_time":600,"minecraft:deaths":2}}}]}
                """;
        Season imported = store.importJson(json, 99999);
        assertEquals("old-world-" + Long.toString(99999, 36), imported.id());
        assertTrue(imported.id().matches("[a-z0-9-]+"), "id must be filename-safe");
        assertEquals(5000, imported.createdAtEpochMs(), "original export date is kept");
        assertEquals(600, imported.players().get(0).highlights().playTime(), "highlights recomputed");
        assertEquals(2, imported.players().get(0).highlights().deaths());
        assertEquals(List.of(), imported.objectives());
        assertNotNull(store.get(imported.id()));
    }

    @Test
    void importRejectsGarbageAndMissingPlayers(@TempDir Path dir) throws IOException {
        SeasonStore store = new SeasonStore(dir);
        store.load();
        assertThrows(IllegalArgumentException.class, () -> store.importJson("not json {", 1));
        assertThrows(IllegalArgumentException.class, () -> store.importJson("{\"name\":\"x\"}", 1));
        assertThrows(IllegalArgumentException.class,
                () -> store.importJson("{\"formatVersion\":99,\"players\":[]}", 1));
    }

    @Test
    void loadSkipsCorruptFiles(@TempDir Path dir) throws IOException {
        SeasonStore store = new SeasonStore(dir);
        store.load();
        store.save(sample("Good", 1000));
        Files.writeString(dir.resolve("grimstats-seasons").resolve("bad.json"), "{{{{");
        SeasonStore reopened = new SeasonStore(dir);
        reopened.load();
        assertEquals(1, reopened.list().size());
    }
}
