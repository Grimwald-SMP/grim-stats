package dev.grimstats.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads, holds and atomically persists the {@link GrimStatsConfig}.
 *
 * <p>The live config is kept in an {@link AtomicReference} so HTTP threads can read it without
 * locking. Writes go through a temp-file + atomic move so a crash mid-write cannot corrupt the
 * config file.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;
    private final AtomicReference<GrimStatsConfig> current = new AtomicReference<>(new GrimStatsConfig());

    public ConfigManager(Path configDir) {
        this.configFile = configDir.resolve("grimstats.json");
    }

    public GrimStatsConfig get() {
        return current.get();
    }

    /** Loads the config from disk, creating a default file if none exists. */
    public synchronized GrimStatsConfig load() throws IOException {
        if (Files.exists(configFile)) {
            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            GrimStatsConfig cfg = GSON.fromJson(json, GrimStatsConfig.class);
            if (cfg == null) {
                cfg = new GrimStatsConfig();
            }
            if (migrateLegacyAdmin(cfg)) {
                save(cfg);
            }
            current.set(cfg);
        } else {
            Files.createDirectories(configFile.getParent());
            save(current.get());
        }
        return current.get();
    }

    /**
     * Migrates a pre-1.0 single-admin config (auth.username/passwordHash) into the users list as a
     * ROOT user, then clears the legacy fields. Returns true if anything changed.
     */
    private boolean migrateLegacyAdmin(GrimStatsConfig cfg) {
        if (cfg.auth.users == null) {
            cfg.auth.users = new java.util.ArrayList<>();
        }
        boolean hasLegacy = cfg.auth.username != null && !cfg.auth.username.isBlank()
                && cfg.auth.passwordHash != null && !cfg.auth.passwordHash.isBlank();
        if (cfg.auth.users.isEmpty() && hasLegacy) {
            GrimStatsConfig.User root = new GrimStatsConfig.User();
            root.username = cfg.auth.username;
            root.passwordHash = cfg.auth.passwordHash;
            root.passwordSalt = cfg.auth.passwordSalt;
            root.iterations = cfg.auth.iterations;
            root.role = "ROOT";
            cfg.auth.users.add(root);
            cfg.auth.username = "";
            cfg.auth.passwordHash = "";
            cfg.auth.passwordSalt = "";
            return true;
        }
        return false;
    }

    /** Replaces the live config and persists it atomically. */
    public synchronized void update(GrimStatsConfig cfg) throws IOException {
        save(cfg);
        current.set(cfg);
    }

    private void save(GrimStatsConfig cfg) throws IOException {
        Files.createDirectories(configFile.getParent());
        Path tmp = configFile.resolveSibling("grimstats.json.tmp");
        Files.writeString(tmp, GSON.toJson(cfg), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            // Some filesystems do not support atomic moves; fall back to a plain replace.
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path getConfigFile() {
        return configFile;
    }
}
