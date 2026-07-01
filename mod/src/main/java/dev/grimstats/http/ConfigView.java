package dev.grimstats.http;

import dev.grimstats.config.GrimStatsConfig;

import java.util.List;

/**
 * Admin-editable projection of {@link GrimStatsConfig}. Deliberately excludes all auth secrets
 * (user password hashes, API key hashes, token secret); user and key management have their own
 * endpoints. Only the session lifetime is exposed here as a general setting.
 */
public class ConfigView {

    public String host;
    public int port;
    public boolean corsAllowAll;
    public List<String> corsOrigins;

    public String defaultTheme;
    public boolean publicDashboard;
    public List<String> hiddenStatTypes;
    public List<String> hiddenObjectives;
    public boolean exposeSeed;
    public List<GrimStatsConfig.PinnedRef> pinned;

    public int snapshotIntervalSeconds;
    public boolean includeOfflinePlayers;
    public int maxPlayers;

    public int sessionTtlMinutes;

    public static ConfigView from(GrimStatsConfig c) {
        ConfigView v = new ConfigView();
        v.host = c.http.host;
        v.port = c.http.port;
        v.corsAllowAll = c.http.corsAllowAll;
        v.corsOrigins = c.http.corsOrigins;
        v.defaultTheme = c.display.defaultTheme;
        v.publicDashboard = c.display.publicDashboard;
        v.hiddenStatTypes = c.display.hiddenStatTypes;
        v.hiddenObjectives = c.display.hiddenObjectives;
        v.exposeSeed = c.display.exposeSeed;
        v.pinned = c.display.pinned;
        v.snapshotIntervalSeconds = c.collection.snapshotIntervalSeconds;
        v.includeOfflinePlayers = c.collection.includeOfflinePlayers;
        v.maxPlayers = c.collection.maxPlayers;
        v.sessionTtlMinutes = c.auth.sessionTtlMinutes;
        return v;
    }

    /**
     * Applies editable fields onto a copy-merged config. Network bind changes (host/port) are
     * accepted into config but only take effect on the next server start, which is noted to admins.
     */
    public void applyTo(GrimStatsConfig c) {
        if (corsOrigins != null) c.http.corsOrigins = corsOrigins;
        c.http.corsAllowAll = corsAllowAll;
        c.http.host = host != null ? host : c.http.host;
        if (port > 0) c.http.port = port;

        if (defaultTheme != null) c.display.defaultTheme = defaultTheme;
        c.display.publicDashboard = publicDashboard;
        if (hiddenStatTypes != null) c.display.hiddenStatTypes = hiddenStatTypes;
        if (hiddenObjectives != null) c.display.hiddenObjectives = hiddenObjectives;
        c.display.exposeSeed = exposeSeed;
        if (pinned != null) c.display.pinned = pinned;

        if (snapshotIntervalSeconds > 0) c.collection.snapshotIntervalSeconds = snapshotIntervalSeconds;
        c.collection.includeOfflinePlayers = includeOfflinePlayers;
        if (maxPlayers > 0) c.collection.maxPlayers = maxPlayers;

        if (sessionTtlMinutes > 0) c.auth.sessionTtlMinutes = sessionTtlMinutes;
    }
}
