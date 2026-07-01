package dev.grimstats.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @Test
    void createsDefaultFileWhenMissing(@TempDir Path dir) throws IOException {
        ConfigManager manager = new ConfigManager(dir);
        GrimStatsConfig cfg = manager.load();
        assertTrue(Files.exists(manager.getConfigFile()));
        assertEquals(8765, cfg.http.port);
        assertEquals("0.0.0.0", cfg.http.host);
    }

    @Test
    void persistsAndReloadsChanges(@TempDir Path dir) throws IOException {
        ConfigManager manager = new ConfigManager(dir);
        GrimStatsConfig cfg = manager.load();
        cfg.http.port = 9000;
        cfg.display.defaultTheme = "dracula";
        cfg.auth.username = "admin";
        manager.update(cfg);

        ConfigManager reopened = new ConfigManager(dir);
        GrimStatsConfig loaded = reopened.load();
        assertEquals(9000, loaded.http.port);
        assertEquals("dracula", loaded.display.defaultTheme);
        assertEquals("admin", loaded.auth.username);
    }

    @Test
    void toleratesPartialJson(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("grimstats.json"), "{\"http\":{\"port\":1234}}");
        ConfigManager manager = new ConfigManager(dir);
        GrimStatsConfig cfg = manager.load();
        assertEquals(1234, cfg.http.port);
        // Missing sections fall back to defaults rather than null.
        assertEquals("grimwald", cfg.display.defaultTheme);
    }
}
