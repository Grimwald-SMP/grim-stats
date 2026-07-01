package dev.grimstats.http;

import dev.grimstats.config.GrimStatsConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigViewTest {

    @Test
    void applyToPreservesAuthSecretsAndAppliesEdits() {
        GrimStatsConfig cfg = new GrimStatsConfig();
        cfg.auth.tokenSecret = "keeptoken";
        GrimStatsConfig.User root = new GrimStatsConfig.User();
        root.username = "root";
        root.passwordHash = "keepme";
        root.role = "ROOT";
        cfg.auth.users.add(root);

        ConfigView view = ConfigView.from(cfg);
        view.defaultTheme = "nord";
        view.publicDashboard = false;
        view.hiddenObjectives = List.of("deaths");
        view.port = 9999;
        view.applyTo(cfg);

        assertEquals("nord", cfg.display.defaultTheme);
        assertFalse(cfg.display.publicDashboard);
        assertEquals(List.of("deaths"), cfg.display.hiddenObjectives);
        assertEquals(9999, cfg.http.port);
        // The config view has no access to users or token secret, so they are untouched.
        assertEquals("keeptoken", cfg.auth.tokenSecret);
        assertEquals("keepme", cfg.auth.users.get(0).passwordHash);
        assertEquals(1, cfg.auth.users.size());
    }

    @Test
    void applyToIgnoresInvalidPort() {
        GrimStatsConfig cfg = new GrimStatsConfig();
        cfg.http.port = 8765;

        ConfigView view = ConfigView.from(cfg);
        view.port = 0; // invalid, should be ignored
        view.applyTo(cfg);

        assertEquals(8765, cfg.http.port);
    }
}
