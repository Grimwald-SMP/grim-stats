package dev.grimstats;

import dev.grimstats.command.GrimStatsCommand;
import dev.grimstats.config.ConfigManager;
import dev.grimstats.config.GrimStatsConfig;
import dev.grimstats.data.SnapshotService;
import dev.grimstats.data.collect.CombinedCollector;
import dev.grimstats.http.WebServer;
import dev.grimstats.http.auth.PasswordHasher;
import dev.grimstats.http.auth.SessionManager;
import dev.grimstats.season.SeasonStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Mod entrypoint. Owns the lifecycle of the config, snapshot service and web server, binding them to
 * the running {@link MinecraftServer}.
 */
public final class GrimStats implements ModInitializer {

    public static final String MOD_ID = "grimstats";
    public static final Logger LOGGER = LoggerFactory.getLogger("GrimStats");

    private ConfigManager configManager;
    private SnapshotService snapshotService;
    private WebServer webServer;

    @Override
    public void onInitialize() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configManager = new ConfigManager(configDir);

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GrimStatsCommand.register(dispatcher, this));
    }

    private void onServerStarted(MinecraftServer server) {
        try {
            GrimStatsConfig cfg = configManager.load();
            ensureTokenSecret(cfg);

            SessionManager sessions = new SessionManager(cfg.auth.tokenSecret, cfg.auth.sessionTtlMinutes);
            this.snapshotService = new SnapshotService(
                    new CombinedCollector(server, configManager), cfg.collection.snapshotIntervalSeconds);
            SeasonStore seasons = new SeasonStore(FabricLoader.getInstance().getConfigDir());
            seasons.load();
            this.webServer = new WebServer(configManager, sessions, snapshotService, seasons);

            snapshotService.start();
            webServer.start();

            if (cfg.auth.username.isEmpty() || cfg.auth.passwordHash.isEmpty()) {
                LOGGER.info("GrimStats admin is not configured yet. Set it in-game with:");
                LOGGER.info("  /grimstats setup <username> <password>");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to start GrimStats", e);
        }
    }

    private void onServerStopping(MinecraftServer server) {
        if (webServer != null) {
            webServer.stop();
        }
        if (snapshotService != null) {
            snapshotService.stop();
        }
    }

    private void ensureTokenSecret(GrimStatsConfig cfg) throws IOException {
        if (cfg.auth.tokenSecret == null || cfg.auth.tokenSecret.isEmpty()) {
            cfg.auth.tokenSecret = PasswordHasher.randomSecret();
            configManager.update(cfg);
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
