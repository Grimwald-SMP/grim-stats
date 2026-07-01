package dev.grimstats.season;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.grimstats.data.model.PlayerHighlights;
import dev.grimstats.data.model.PlayerStats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Persists {@link Season} archives as individual JSON files under {@code config/grimstats-seasons/}.
 *
 * <p>Only the lightweight {@link Season.Info} summaries are kept in memory; full player data is read
 * from disk when a season is opened, so archiving many large seasons does not grow the server's
 * steady-state footprint. Writes are temp-file + atomic-move, matching the config manager.
 *
 * <p>Imported documents are validated and normalized: the id is regenerated server-side (it doubles
 * as the filename, so a client-supplied id is never trusted), missing collections become empty, and
 * players without highlights get them recomputed from their raw stat maps.
 */
public final class SeasonStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dir;
    private final Map<String, Season.Info> index = new LinkedHashMap<>();

    public SeasonStore(Path configDir) {
        this.dir = configDir.resolve("grimstats-seasons");
    }

    /** Scans the seasons directory and builds the in-memory index. Call once at startup. */
    public synchronized void load() throws IOException {
        index.clear();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                try {
                    Season season = parse(Files.readString(f, StandardCharsets.UTF_8));
                    index.put(season.id(), season.info());
                } catch (Exception e) {
                    // A corrupt file must not take the whole store down; skip it.
                }
            }
        }
    }

    /** Newest first. */
    public synchronized List<Season.Info> list() {
        List<Season.Info> out = new ArrayList<>(index.values());
        out.sort(Comparator.comparingLong(Season.Info::createdAtEpochMs).reversed());
        return out;
    }

    /** The full season, read from disk; null if unknown. */
    public synchronized Season get(String id) throws IOException {
        if (!index.containsKey(id)) {
            return null;
        }
        return parse(Files.readString(fileFor(id), StandardCharsets.UTF_8));
    }

    public synchronized Season save(Season season) throws IOException {
        Files.createDirectories(dir);
        Path target = fileFor(season.id());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(season), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        index.put(season.id(), season.info());
        return season;
    }

    public synchronized boolean delete(String id) throws IOException {
        if (index.remove(id) == null) {
            return false;
        }
        Files.deleteIfExists(fileFor(id));
        return true;
    }

    /**
     * Parses, validates and normalizes an uploaded season document, then persists it under a freshly
     * generated id. Throws {@link IllegalArgumentException} with a user-presentable message on bad input.
     */
    public synchronized Season importJson(String json, long now) throws IOException {
        Season parsed;
        try {
            parsed = parse(json);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("not a valid season JSON document");
        }
        if (parsed == null || parsed.players() == null) {
            throw new IllegalArgumentException("season file has no players array");
        }
        if (parsed.formatVersion() > Season.CURRENT_FORMAT) {
            throw new IllegalArgumentException("season format v" + parsed.formatVersion()
                    + " is newer than this mod supports (v" + Season.CURRENT_FORMAT + ")");
        }
        String name = parsed.name() == null || parsed.name().isBlank() ? "Imported season" : parsed.name().trim();
        long createdAt = parsed.createdAtEpochMs() > 0 ? parsed.createdAtEpochMs() : now;
        Season normalized = new Season(
                Season.CURRENT_FORMAT,
                newId(name, now),
                name,
                createdAt,
                parsed.serverName() == null ? "" : parsed.serverName(),
                parsed.minecraftVersion() == null ? "" : parsed.minecraftVersion(),
                Math.max(0, parsed.gameTime()),
                normalizePlayers(parsed.players()),
                parsed.objectives() == null ? List.of() : parsed.objectives());
        return save(normalized);
    }

    /** Drops null entries and recomputes missing highlights so old/hand-made files still render. */
    private static List<PlayerStats> normalizePlayers(List<PlayerStats> players) {
        List<PlayerStats> out = new ArrayList<>(players.size());
        for (PlayerStats p : players) {
            if (p == null || p.uuid() == null || p.name() == null) {
                continue;
            }
            Map<String, Map<String, Long>> stats = p.stats() == null ? Map.of() : p.stats();
            PlayerHighlights h = p.highlights() != null ? p.highlights() : PlayerHighlights.from(stats);
            out.add(new PlayerStats(p.uuid(), p.name(), false, p.lastSeenEpochMs(), h, stats,
                    p.scores() == null ? List.of() : p.scores()));
        }
        return out;
    }

    /**
     * Filename-safe unique id: a slug of the name plus the timestamp in base 36. Never derived from
     * client input directly, so it cannot traverse paths or collide with careful crafting.
     */
    static String newId(String name, long now) {
        String slug = (name == null ? "" : name)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isEmpty()) {
            slug = "season";
        }
        if (slug.length() > 40) {
            slug = slug.substring(0, 40);
        }
        return slug + "-" + Long.toString(now, 36);
    }

    private Path fileFor(String id) {
        return dir.resolve(id + ".json");
    }

    private static Season parse(String json) {
        return GSON.fromJson(json, Season.class);
    }
}
