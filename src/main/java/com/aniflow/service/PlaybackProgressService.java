package com.aniflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PlaybackProgressService {
    private static final int SCHEMA_VERSION = 1;
    private static final int MIN_SAVE_SEC = 5;
    private static final int MAX_ITEMS = 300;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path dataDir = Path.of(System.getProperty("user.home"), ".aniflow");
    private final Path progressFile = dataDir.resolve("progress.json");

    public PlaybackProgressService() {
        initialize();
    }

    public synchronized Optional<EpisodeProgress> getEpisodeProgress(String animeSlug, String episodeSlug) {
        if (isBlank(animeSlug) || isBlank(episodeSlug)) {
            return Optional.empty();
        }

        ProgressState state = loadStateSafely();
        return state.items.stream()
            .filter(item -> animeSlug.equals(item.animeSlug) && episodeSlug.equals(item.episodeSlug))
            .max(Comparator.comparingLong(item -> item.updatedAtMs))
            .map(item -> new EpisodeProgress(
                item.animeSlug,
                item.episodeSlug,
                item.currentTimeSec,
                item.durationSec,
                item.updatedAtMs
            ));
    }

    public synchronized void saveProgress(String animeSlug,
                                          String episodeSlug,
                                          int currentTimeSec,
                                          Integer durationSec,
                                          String reason) {
        if (isBlank(animeSlug) || isBlank(episodeSlug) || currentTimeSec < MIN_SAVE_SEC) {
            return;
        }

        long now = System.currentTimeMillis();
        ProgressState state = loadStateSafely();
        ProgressItem existing = state.find(animeSlug, episodeSlug);

        int safeDuration = durationSec == null || durationSec <= 0 ? 0 : durationSec;
        int safeCurrent = Math.max(0, currentTimeSec);
        if ("ended".equalsIgnoreCase(reason) && safeDuration > 0) {
            safeCurrent = safeDuration;
        }

        if (existing == null) {
            ProgressItem created = new ProgressItem();
            created.animeSlug = animeSlug;
            created.episodeSlug = episodeSlug;
            created.currentTimeSec = safeCurrent;
            created.durationSec = safeDuration;
            created.updatedAtMs = now;
            state.items.add(created);
        } else {
            existing.currentTimeSec = Math.max(existing.currentTimeSec, safeCurrent);
            if (safeDuration > 0) {
                existing.durationSec = Math.max(existing.durationSec, safeDuration);
            }
            existing.updatedAtMs = now;
        }

        dedupeByKey(state.items);
        trimToLimit(state.items, MAX_ITEMS);
        state.updatedAtMs = now;
        writeStateAtomic(state);
    }

    private void initialize() {
        loadStateSafely();
    }

    private ProgressState loadStateSafely() {
        try {
            Files.createDirectories(dataDir);
            if (!Files.exists(progressFile)) {
                ProgressState empty = new ProgressState();
                writeStateAtomic(empty);
                return empty;
            }
            return loadState(progressFile);
        } catch (Exception ex) {
            archiveCorrupted(progressFile);
            ProgressState empty = new ProgressState();
            writeStateAtomic(empty);
            return empty;
        }
    }

    private ProgressState loadState(Path file) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        if (root == null || root.isNull()) {
            throw new IOException("Invalid progress payload");
        }

        if (root.isArray()) {
            return fromLegacyArray((ArrayNode) root);
        }

        if (!root.isObject()) {
            throw new IOException("Invalid progress root type");
        }

        int version = root.path("schemaVersion").asInt(0);
        if (version != SCHEMA_VERSION) {
            throw new IOException("Unsupported progress schema version: " + version);
        }

        JsonNode items = root.get("items");
        if (items == null || !items.isArray()) {
            throw new IOException("Invalid progress items");
        }

        ProgressState state = new ProgressState();
        state.updatedAtMs = root.path("updatedAtMs").asLong(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        items.forEach(node -> {
            ProgressItem item = toProgressItem(node, now);
            if (item != null) {
                upsertLoadedItem(state, item);
            }
        });

        trimToLimit(state.items, MAX_ITEMS);
        return state;
    }

    private ProgressState fromLegacyArray(ArrayNode array) {
        ProgressState state = new ProgressState();
        long now = System.currentTimeMillis();
        state.updatedAtMs = now;

        array.forEach(node -> {
            ProgressItem item = toLegacyProgressItem(node, now);
            if (item != null) {
                upsertLoadedItem(state, item);
            }
        });

        trimToLimit(state.items, MAX_ITEMS);
        writeStateAtomic(state);
        return state;
    }

    private ProgressItem toProgressItem(JsonNode node, long now) {
        String animeSlug = node.path("animeSlug").asText("");
        String episodeSlug = node.path("episodeSlug").asText("");
        int currentTimeSec = node.path("currentTimeSec").asInt(0);
        int durationSec = node.path("durationSec").asInt(0);
        long updatedAtMs = node.path("updatedAtMs").asLong(now);

        if (isBlank(animeSlug) || isBlank(episodeSlug) || currentTimeSec < 0) {
            return null;
        }

        ProgressItem item = new ProgressItem();
        item.animeSlug = animeSlug;
        item.episodeSlug = episodeSlug;
        item.currentTimeSec = currentTimeSec;
        item.durationSec = Math.max(durationSec, 0);
        item.updatedAtMs = updatedAtMs;
        return item;
    }

    private ProgressItem toLegacyProgressItem(JsonNode node, long now) {
        String animeSlug = node.path("animeSlug").asText("");
        String episodeSlug = node.path("episodeSlug").asText("");
        int progressMs = node.path("progressMs").asInt(0);
        int durationMs = node.path("durationMs").asInt(0);

        int currentTimeSec = progressMs > 0 ? (progressMs / 1000) : node.path("currentTimeSec").asInt(0);
        int durationSec = durationMs > 0 ? (durationMs / 1000) : node.path("durationSec").asInt(0);
        long updatedAtMs = node.path("updatedAtMs").asLong(node.path("timestamp").asLong(node.path("updatedAt").asLong(now)));

        if (isBlank(animeSlug) || isBlank(episodeSlug) || currentTimeSec < 0) {
            return null;
        }

        ProgressItem item = new ProgressItem();
        item.animeSlug = animeSlug;
        item.episodeSlug = episodeSlug;
        item.currentTimeSec = currentTimeSec;
        item.durationSec = Math.max(durationSec, 0);
        item.updatedAtMs = updatedAtMs;
        return item;
    }

    private void upsertLoadedItem(ProgressState state, ProgressItem incoming) {
        ProgressItem existing = state.find(incoming.animeSlug, incoming.episodeSlug);
        if (existing == null) {
            state.items.add(incoming);
            return;
        }

        existing.currentTimeSec = Math.max(existing.currentTimeSec, incoming.currentTimeSec);
        existing.durationSec = Math.max(existing.durationSec, incoming.durationSec);
        existing.updatedAtMs = Math.max(existing.updatedAtMs, incoming.updatedAtMs);
    }

    private void dedupeByKey(List<ProgressItem> items) {
        ProgressState tmp = new ProgressState();
        tmp.updatedAtMs = System.currentTimeMillis();
        items.forEach(item -> upsertLoadedItem(tmp, item));
        items.clear();
        items.addAll(tmp.items);
    }

    private void writeStateAtomic(ProgressState state) {
        try {
            Files.createDirectories(dataDir);
            Path tempFile = progressFile.resolveSibling("progress.json.tmp");
            ObjectNode root = mapper.createObjectNode();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("updatedAtMs", state.updatedAtMs > 0 ? state.updatedAtMs : System.currentTimeMillis());

            ArrayNode items = root.putArray("items");
            state.items.stream()
                .sorted(Comparator.comparingLong((ProgressItem item) -> item.updatedAtMs).reversed())
                .forEach(item -> {
                    ObjectNode node = items.addObject();
                    node.put("animeSlug", item.animeSlug);
                    node.put("episodeSlug", item.episodeSlug);
                    node.put("currentTimeSec", item.currentTimeSec);
                    if (item.durationSec > 0) {
                        node.put("durationSec", item.durationSec);
                    }
                    node.put("updatedAtMs", item.updatedAtMs);
                });

            mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), root);
            try {
                Files.move(tempFile, progressFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile, progressFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    private void archiveCorrupted(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            String fileName = "progress.corrupt." + System.currentTimeMillis() + ".json";
            Files.move(path, path.resolveSibling(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    private void trimToLimit(List<ProgressItem> items, int maxItems) {
        if (items.size() <= maxItems) {
            return;
        }
        items.sort(Comparator.comparingLong((ProgressItem item) -> item.updatedAtMs).reversed());
        items.subList(maxItems, items.size()).clear();
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static class ProgressState {
        long updatedAtMs = System.currentTimeMillis();
        List<ProgressItem> items = new ArrayList<>();

        ProgressItem find(String animeSlug, String episodeSlug) {
            return items.stream()
                .filter(item -> animeSlug.equals(item.animeSlug) && episodeSlug.equals(item.episodeSlug))
                .findFirst()
                .orElse(null);
        }
    }

    private static class ProgressItem {
        String animeSlug;
        String episodeSlug;
        int currentTimeSec;
        int durationSec;
        long updatedAtMs;
    }

    public record EpisodeProgress(String animeSlug,
                                  String episodeSlug,
                                  int currentTimeSec,
                                  int durationSec,
                                  long updatedAtMs) {
    }
}
