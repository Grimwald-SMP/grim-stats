package dev.grimstats.data.collect;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.grimstats.GrimStats;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves UUID -&gt; last-known username by reading the server's {@code usercache.json}.
 *
 * <p>In 1.21.11 {@code MinecraftServer} no longer exposes a synchronous profile cache, and
 * {@code Services} only offers async resolvers. {@code usercache.json} is a stable, cheap,
 * version-independent source for offline player names, refreshed only when its mtime changes.
 */
final class UsernameCache {

    private final Path file;
    private volatile long loadedMtime = -1;
    private volatile Map<UUID, String> names = new HashMap<>();

    UsernameCache(Path gameDir) {
        this.file = gameDir.resolve("usercache.json");
    }

    String nameFor(UUID uuid) {
        refreshIfNeeded();
        return names.getOrDefault(uuid, uuid.toString());
    }

    private void refreshIfNeeded() {
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (mtime == loadedMtime) {
                return;
            }
            Map<UUID, String> parsed = new HashMap<>();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    var obj = el.getAsJsonObject();
                    if (obj.has("uuid") && obj.has("name")) {
                        try {
                            parsed.put(UUID.fromString(obj.get("uuid").getAsString()), obj.get("name").getAsString());
                        } catch (IllegalArgumentException ignored) {
                            // skip malformed entry
                        }
                    }
                }
            }
            names = parsed;
            loadedMtime = mtime;
        } catch (Exception e) {
            GrimStats.LOGGER.debug("Could not read usercache.json", e);
        }
    }
}
