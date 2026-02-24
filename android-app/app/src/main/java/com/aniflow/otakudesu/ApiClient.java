package com.aniflow.otakudesu;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ApiClient {
    public static final String BASE_URL = "https://otakudesu-api.vercel.app/api";

    private static final int MAX_RETRY = 3;
    private static final long BACKOFF_MS = 350L;
    private static final long CACHE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(24);
    private static final String PREF_NAME = "otakudesu_api_cache_v1";
    private static final String KEY_PAYLOAD_PREFIX = "payload_";
    private static final String KEY_TS_PREFIX = "timestamp_";

    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");
    private static final Gson GSON = new Gson();

    private final OkHttpClient httpClient;
    private final SharedPreferences cachePrefs;
    private final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();

    public ApiClient(Context context) {
        this.cachePrefs = context.getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();
    }

    public List<AnimeModel> getHome() throws IOException {
        JsonObject root = fetchWithRetryAndCache(
            "home",
            Arrays.asList("/home", "/anime/home", "/anime/ongoing")
        );
        return parseAnimeList(root);
    }

    public List<AnimeModel> getOngoing() throws IOException {
        JsonObject root = fetchWithRetryAndCache(
            "ongoing",
            Arrays.asList("/ongoing", "/anime/ongoing")
        );
        return parseAnimeList(root);
    }

    public AnimeModel getAnime(String slug) throws IOException {
        if (slug == null || slug.trim().isEmpty()) {
            throw new IllegalArgumentException("slug cannot be empty");
        }

        String encoded = encode(slug);
        JsonObject root = fetchWithRetryAndCache(
            "anime_" + slug.trim().toLowerCase(Locale.ROOT),
            Arrays.asList("/anime/" + encoded, "/anime/details/" + encoded)
        );
        JsonObject data = asObject(root.get("data"));
        if (data == null) {
            throw new IOException("Anime detail not found");
        }
        return parseAnime(data);
    }

    public EpisodeModel getEpisode(String slug) throws IOException {
        if (slug == null || slug.trim().isEmpty()) {
            throw new IllegalArgumentException("slug cannot be empty");
        }

        String encoded = encode(slug);
        JsonObject root = fetchWithRetryAndCache(
            "episode_" + slug.trim().toLowerCase(Locale.ROOT),
            Arrays.asList("/episode/" + encoded, "/anime/stream/" + encoded)
        );
        JsonObject data = asObject(root.get("data"));
        if (data == null) {
            throw new IOException("Episode detail not found");
        }
        return parseEpisodeDetail(data);
    }

    public List<AnimeModel> search(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String encoded = encode(query.trim());
        JsonObject root = fetchWithRetryAndCache(
            "search_" + encoded,
            Arrays.asList("/search?q=" + encoded, "/anime/search?q=" + encoded)
        );
        return parseAnimeList(root);
    }

    private JsonObject fetchWithRetryAndCache(String cacheKey, List<String> endpointCandidates) throws IOException {
        IOException lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            for (String path : endpointCandidates) {
                try {
                    JsonObject root = request(path);
                    saveCache(cacheKey, root.toString());
                    return root;
                } catch (IOException e) {
                    lastError = e;
                }
            }
            sleepQuietly(BACKOFF_MS * attempt);
        }

        JsonObject fromCache = readCache(cacheKey, false);
        if (fromCache != null) {
            return fromCache;
        }

        fromCache = readCache(cacheKey, true);
        if (fromCache != null) {
            return fromCache;
        }

        throw lastError == null
            ? new IOException("Request failed: " + cacheKey)
            : lastError;
    }

    private JsonObject request(String path) throws IOException {
        Request request = new Request.Builder()
            .url(buildUrl(path))
            .get()
            .header("Accept", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + path);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }

            String raw = body.string();
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) {
                throw new IOException("Invalid JSON response");
            }

            JsonObject root = parsed.getAsJsonObject();
            if (root.has("error") && !root.get("error").isJsonNull()) {
                throw new IOException(root.get("error").getAsString());
            }
            if (root.has("success") && !root.get("success").isJsonNull() && !root.get("success").getAsBoolean()) {
                String message = text(root, "message", "API request failed");
                throw new IOException(message);
            }
            return root;
        } catch (IllegalStateException e) {
            throw new IOException("Malformed JSON", e);
        }
    }

    private String buildUrl(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.isEmpty()) {
            return BASE_URL;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return BASE_URL + normalized;
    }

    private void saveCache(String key, String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        memoryCache.put(key, new CacheEntry(payload, now));
        cachePrefs.edit()
            .putString(KEY_PAYLOAD_PREFIX + key, payload)
            .putLong(KEY_TS_PREFIX + key, now)
            .apply();
    }

    private JsonObject readCache(String key, boolean allowStale) {
        CacheEntry memory = memoryCache.get(key);
        JsonObject fromMemory = parseCacheEntry(memory, allowStale);
        if (fromMemory != null) {
            return fromMemory;
        }

        String payload = cachePrefs.getString(KEY_PAYLOAD_PREFIX + key, null);
        long timestamp = cachePrefs.getLong(KEY_TS_PREFIX + key, 0L);
        CacheEntry disk = new CacheEntry(payload, timestamp);
        JsonObject fromDisk = parseCacheEntry(disk, allowStale);
        if (fromDisk != null) {
            memoryCache.put(key, disk);
            return fromDisk;
        }
        return null;
    }

    private JsonObject parseCacheEntry(CacheEntry entry, boolean allowStale) {
        if (entry == null || entry.payload == null || entry.payload.trim().isEmpty()) {
            return null;
        }

        long age = System.currentTimeMillis() - entry.timestamp;
        if (!allowStale && age > CACHE_MAX_AGE_MS) {
            return null;
        }

        try {
            JsonElement parsed = JsonParser.parseString(entry.payload);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<AnimeModel> parseAnimeList(JsonObject root) {
        JsonElement data = root == null ? null : root.get("data");
        if (data == null || data.isJsonNull()) {
            return Collections.emptyList();
        }

        if (data.isJsonArray()) {
            return parseAnimeArray(data.getAsJsonArray());
        }

        if (data.isJsonObject()) {
            JsonObject object = data.getAsJsonObject();
            for (String key : Arrays.asList("home", "ongoing", "trending", "popular", "complete", "results", "anime", "list")) {
                JsonElement candidate = object.get(key);
                if (candidate != null && candidate.isJsonArray()) {
                    return parseAnimeArray(candidate.getAsJsonArray());
                }
            }
        }

        return Collections.emptyList();
    }

    private List<AnimeModel> parseAnimeArray(JsonArray array) {
        if (array == null || array.size() == 0) {
            return Collections.emptyList();
        }

        List<AnimeModel> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = asObject(element);
            if (object == null) {
                continue;
            }
            AnimeModel anime = parseAnime(object);
            if (anime != null) {
                result.add(anime);
            }
        }
        return result;
    }

    private AnimeModel parseAnime(JsonObject object) {
        if (object == null) {
            return null;
        }

        AnimeModel model = new AnimeModel();

        String slug = text(object, "slug", "");
        if (slug.isEmpty()) {
            slug = extractSlugFromEndpoint(text(object, "endpoint", ""));
        }

        int fallbackId = slug.isEmpty() ? 0 : Math.abs(slug.hashCode());
        model.setId(number(object, "id", fallbackId));
        model.setSlug(slug);
        model.setTitle(text(object, "title", ""));
        model.setThumbnail(firstNonBlank(
            text(object, "thumbnail", ""),
            text(object, "cover", ""),
            text(object, "image", ""),
            text(object, "banner", "")
        ));
        model.setSynopsis(firstNonBlank(
            text(object, "synopsis", ""),
            text(object, "description", "")
        ));
        model.setStatus(text(object, "status", "Unknown"));
        model.setReleaseDate(firstNonBlank(
            text(object, "releaseDate", ""),
            text(object, "releaseDay", "")
        ));

        String episodeLabel = firstNonBlank(
            text(object, "episode", ""),
            text(object, "episodes", ""),
            text(object, "totalEpisodes", "")
        );
        model.setEpisodeLabel(episodeLabel);
        model.setTotalEpisodes(parseEpisodeNumber(firstNonBlank(text(object, "totalEpisodes", ""), episodeLabel)));

        model.setScore(parseScore(firstNonBlank(
            text(object, "score", ""),
            text(object, "rating", "")
        )));
        model.setStudio(text(object, "studio", ""));
        model.setProducer(text(object, "producer", ""));
        model.setDuration(text(object, "duration", ""));
        model.setGenres(parseStringArray(object.get("genres")));

        JsonArray episodeArray = asArray(object.get("episodeList"));
        if (episodeArray == null) {
            episodeArray = asArray(object.get("episode_list"));
        }
        if (episodeArray != null) {
            model.setEpisodes(parseEpisodeList(episodeArray));
        }

        return model;
    }

    private List<EpisodeModel> parseEpisodeList(JsonArray array) {
        List<EpisodeModel> result = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject item = asObject(element);
            if (item == null) {
                continue;
            }

            EpisodeModel episode = new EpisodeModel();
            String title = text(item, "title", "");
            String slug = text(item, "slug", "");
            episode.setEpisodeNumber(number(item, "episodeNumber", parseEpisodeNumber(title)));
            episode.setTitle(title);
            episode.setSlug(slug);
            episode.setReleaseDate(text(item, "releaseDate", ""));
            result.add(episode);
        }
        return result;
    }

    private EpisodeModel parseEpisodeDetail(JsonObject data) {
        EpisodeModel model = new EpisodeModel();
        String title = text(data, "title", "");
        String slug = text(data, "episodeSlug", text(data, "slug", ""));

        model.setTitle(title);
        model.setSlug(slug);
        model.setEpisodeNumber(parseEpisodeNumber(firstNonBlank(title, slug)));
        model.setReleaseDate(text(data, "releaseDate", ""));
        JsonElement streaming = data.get("streamingUrls");
        if (streaming == null || streaming.isJsonNull()) {
            streaming = data.get("stream_url");
        }
        JsonElement downloads = data.get("downloadUrls");
        if (downloads == null || downloads.isJsonNull()) {
            downloads = data.get("download_urls");
        }

        model.setStreamUrls(extractUrls(streaming));
        model.setDownloadUrls(extractDownloadUrls(downloads));

        JsonObject navigation = asObject(data.get("navigation"));
        if (navigation != null) {
            model.setPreviousEpisodeSlug(text(asObject(navigation.get("prev")), "slug", null));
            model.setNextEpisodeSlug(text(asObject(navigation.get("next")), "slug", null));
            model.setAnimeSlug(text(asObject(navigation.get("list")), "slug", null));
        }
        return model;
    }

    private Map<String, List<String>> extractDownloadUrls(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Collections.emptyMap();
        }

        if (!element.isJsonObject()) {
            List<String> urls = extractUrls(element);
            if (urls.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, List<String>> single = new LinkedHashMap<>();
            single.put("default", urls);
            return single;
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            List<String> urls = extractUrls(entry.getValue());
            if (!urls.isEmpty()) {
                result.put(entry.getKey(), urls);
            }
        }
        return result;
    }

    private List<String> extractUrls(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Collections.emptyList();
        }

        Set<String> urls = new LinkedHashSet<>();
        collectUrlsRecursive(element, urls);
        return new ArrayList<>(urls);
    }

    private void collectUrlsRecursive(JsonElement element, Set<String> collector) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (value != null && value.startsWith("http")) {
                collector.add(value);
            }
            return;
        }

        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                collectUrlsRecursive(item, collector);
            }
            return;
        }

        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        for (String key : Arrays.asList("url", "src", "link", "href", "file")) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                String text = value.getAsString();
                if (text != null && text.startsWith("http")) {
                    collector.add(text);
                }
            }
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            collectUrlsRecursive(entry.getValue(), collector);
        }
    }

    private List<String> parseStringArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonPrimitive()) {
                continue;
            }
            String value = item.getAsString();
            if (value != null && !value.trim().isEmpty()) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private int parseEpisodeNumber(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        Matcher matcher = DIGIT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double parseScore(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0.0;
        }

        String cleaned = raw.replace(",", ".").replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty()) {
            return 0.0;
        }

        try {
            double parsed = Double.parseDouble(cleaned);
            return parsed <= 10.0 ? parsed : 0.0;
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String text(JsonObject object, String key, String fallback) {
        if (object == null || key == null || key.isEmpty()) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            String value = element.getAsString();
            if (value == null || value.trim().isEmpty()) {
                return fallback;
            }
            return value.trim();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int number(JsonObject object, String key, int fallback) {
        if (object == null || key == null || key.isEmpty()) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String extractSlugFromEndpoint(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.isEmpty()) {
            return "";
        }

        int queryIdx = value.indexOf('?');
        if (queryIdx >= 0) {
            value = value.substring(0, queryIdx);
        }

        String[] parts = value.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String token = parts[i] == null ? "" : parts[i].trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return "";
    }

    private JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private JsonArray asArray(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private void sleepQuietly(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(Math.max(1L, millis));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public String dumpCachedJson(String cacheKey) {
        JsonObject cached = readCache(cacheKey, true);
        return cached == null ? null : GSON.toJson(cached);
    }

    private static final class CacheEntry {
        private final String payload;
        private final long timestamp;

        private CacheEntry(String payload, long timestamp) {
            this.payload = payload;
            this.timestamp = timestamp;
        }
    }
}
