package com.aniflow.service;

import com.aniflow.model.Anime;
import com.aniflow.model.AnimeDetail;
import com.aniflow.model.EpisodeInfo;
import com.aniflow.model.EpisodeStream;
import com.aniflow.model.Genre;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtakudesuApiService {
    private static final String BASE_URL = "https://otakudesu-api.vercel.app";
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OtakudesuApiService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();
        this.mapper = new ObjectMapper();
    }

    public List<Anime> fetchHomeTrending() {
        JsonNode root = requestAny(List.of("/api/anime/ongoing", "/api/anime/home", "/home"));
        return parseAnimeArray(root);
    }

    public List<Anime> fetchOngoing() {
        JsonNode root = requestAny(List.of("/api/anime/ongoing", "/ongoing"));
        return parseAnimeArray(root);
    }

    public List<Anime> fetchTopByRating() {
        JsonNode root = requestAny(List.of("/api/anime/complete?sort=rating", "/api/anime/complete", "/complete?sort=rating"));
        return parseAnimeArray(root).stream()
            .sorted(Comparator.comparingDouble(Anime::getScore).reversed())
            .toList();
    }

    public List<Genre> fetchGenres() {
        JsonNode root = requestAny(List.of("/api/anime/genre", "/genres"));
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return Collections.emptyList();
        }

        List<Genre> genres = new ArrayList<>();
        data.forEach(item -> {
            String name = text(item, "name", "-");
            String slug = text(item, "slug", slugify(name));
            genres.add(new Genre(name, slug));
        });
        return genres;
    }

    public List<Anime> searchAnime(String query) {
        String encoded = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
        JsonNode root = requestAny(List.of("/api/anime/search?q=" + encoded, "/search?q=" + encoded));
        return parseAnimeArray(root);
    }

    public List<Anime> fetchByGenre(String genreSlug) {
        String encoded = URLEncoder.encode(genreSlug, StandardCharsets.UTF_8);
        JsonNode root = requestAny(List.of("/api/anime/genre/" + encoded, "/genre/" + encoded));
        return parseAnimeArray(root);
    }

    public AnimeDetail fetchAnimeDetail(String slug) {
        String encoded = URLEncoder.encode(slug, StandardCharsets.UTF_8);
        JsonNode root = requestAny(List.of("/api/anime/details/" + encoded, "/anime/" + encoded));
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new IllegalStateException("Anime detail not found for slug: " + slug);
        }

        Anime anime = parseAnime(data);
        List<EpisodeInfo> episodes = parseEpisodeList(data.path("episodeList").isMissingNode() ? data.path("episode_list") : data.path("episodeList"));
        return new AnimeDetail(anime, episodes, Collections.emptyList());
    }

    public EpisodeStream fetchEpisodeStream(String episodeSlug) {
        String encoded = URLEncoder.encode(episodeSlug, StandardCharsets.UTF_8);
        JsonNode root = requestAny(List.of("/api/anime/stream/" + encoded, "/episode/" + encoded));
        JsonNode data = root.path("data");

        String title = text(data, "title", "Episode");
        String slug = text(data, "episodeSlug", episodeSlug);

        List<String> streamingUrls = extractUrls(data.path("streamingUrls"));
        if (streamingUrls.isEmpty()) {
            streamingUrls.addAll(extractUrls(data.path("stream_url")));
        }

        Map<String, List<String>> downloadUrls = extractDownloadUrls(
            data.path("downloadUrls").isMissingNode() ? data.path("download_urls") : data.path("downloadUrls")
        );

        JsonNode navigation = data.path("navigation");
        String prev = text(navigation.path("prev"), "slug", null);
        String next = text(navigation.path("next"), "slug", null);
        String animeSlug = text(navigation.path("list"), "slug", null);

        return new EpisodeStream(title, slug, streamingUrls, downloadUrls, prev, next, animeSlug);
    }

    public boolean healthCheck() {
        try {
            JsonNode root = requestAny(List.of("/api/anime/health", "/health"));
            return root.path("success").asBoolean(true);
        } catch (Exception ignored) {
            return false;
        }
    }

    private JsonNode requestAny(List<String> paths) {
        Exception last = null;
        for (String path : paths) {
            try {
                return requestWithRetry(path, 3);
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw new IllegalStateException("All endpoint candidates failed", last);
    }

    private JsonNode requestWithRetry(String path, int retries) throws IOException, InterruptedException {
        IOException ioError = null;
        InterruptedException interrupted = null;

        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                return request(path);
            } catch (IOException ex) {
                ioError = ex;
                sleepBackoff(attempt);
            } catch (InterruptedException ex) {
                interrupted = ex;
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (interrupted != null) {
            throw interrupted;
        }
        throw ioError == null ? new IOException("Request failed: " + path) : ioError;
    }

    private JsonNode request(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for " + path);
        }

        JsonNode root = mapper.readTree(response.body());
        if (root.has("error")) {
            throw new IOException("API error for " + path + ": " + root.path("error").asText());
        }
        return root;
    }

    private List<Anime> parseAnimeArray(JsonNode root) {
        JsonNode data = root.path("data");

        if (data.isArray()) {
            return parseAnimeListFromArray((ArrayNode) data);
        }

        if (data.isObject()) {
            for (String key : List.of("popular", "trending", "ongoing", "complete", "results", "anime")) {
                JsonNode node = data.path(key);
                if (node.isArray()) {
                    return parseAnimeListFromArray((ArrayNode) node);
                }
            }
        }

        return Collections.emptyList();
    }

    private List<Anime> parseAnimeListFromArray(ArrayNode data) {
        List<Anime> items = new ArrayList<>();
        data.forEach(node -> {
            Anime anime = parseAnime(node);
            if (anime != null) {
                items.add(anime);
            }
        });
        return items;
    }

    private Anime parseAnime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String slug = text(node, "slug", "");
        int id = node.path("id").isInt() ? node.path("id").asInt() : Math.abs(slug.hashCode());

        String title = text(node, "title", "Untitled");
        String thumbnail = firstNonBlank(
            text(node, "thumbnail", ""),
            text(node, "cover", ""),
            text(node, "image", "")
        );

        String banner = firstNonBlank(text(node, "banner", ""), thumbnail);
        String description = firstNonBlank(
            text(node, "synopsis", ""),
            text(node, "description", ""),
            "No description"
        );

        String episodeLabel = firstNonBlank(
            text(node, "episode", ""),
            text(node, "episodes", ""),
            text(node, "totalEpisodes", "")
        );

        int episodes = parseEpisodeNumber(episodeLabel);
        String status = firstNonBlank(text(node, "status", ""), "Unknown");
        String scoreText = firstNonBlank(text(node, "rating", ""), text(node, "score", ""), "-");
        double score = parseScore(scoreText);

        List<String> genres = parseGenres(node.path("genres"));
        String detailEndpoint = firstNonBlank(text(node, "endpoint", ""), "/api/anime/details/" + slug);
        String releaseInfo = firstNonBlank(
            text(node, "releaseDay", ""),
            text(node, "releaseDate", ""),
            text(node, "date", "")
        );
        String studio = text(node, "studio", "");

        return new Anime(
            id,
            slug,
            title,
            thumbnail,
            banner,
            description,
            episodes,
            status,
            score,
            scoreText,
            genres,
            "",
            null,
            detailEndpoint,
            episodeLabel,
            releaseInfo,
            studio
        );
    }

    private List<EpisodeInfo> parseEpisodeList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }

        List<EpisodeInfo> list = new ArrayList<>();
        node.forEach(item -> {
            int number = item.path("episodeNumber").asInt(parseEpisodeNumber(text(item, "title", "")));
            String title = text(item, "title", "Episode " + number);
            String slug = text(item, "slug", "");
            String endpoint = firstNonBlank(text(item, "endpoint", ""), "/api/anime/stream/" + slug);
            String releaseDate = text(item, "releaseDate", "");
            list.add(new EpisodeInfo(number, title, slug, releaseDate, endpoint));
        });
        return list;
    }

    private List<String> parseGenres(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }

        List<String> genres = new ArrayList<>();
        node.forEach(g -> genres.add(g.asText()));
        return genres;
    }

    private List<String> extractUrls(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Collections.emptyList();
        }

        List<String> urls = new ArrayList<>();
        if (node.isTextual()) {
            urls.add(node.asText());
            return urls;
        }

        if (node.isArray()) {
            node.forEach(item -> urls.addAll(extractUrls(item)));
            return urls;
        }

        if (node.isObject()) {
            for (String key : List.of("url", "link", "src", "file", "href")) {
                if (node.hasNonNull(key) && node.path(key).isTextual()) {
                    urls.add(node.path(key).asText());
                }
            }

            node.fields().forEachRemaining(entry -> {
                if (entry.getValue().isArray() || entry.getValue().isObject()) {
                    urls.addAll(extractUrls(entry.getValue()));
                }
            });
        }

        return urls.stream().filter(url -> url != null && !url.isBlank()).distinct().toList();
    }

    private Map<String, List<String>> extractDownloadUrls(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        if (node.isArray()) {
            List<String> urls = extractUrls(node);
            if (!urls.isEmpty()) {
                result.put("default", urls);
            }
            return result;
        }

        if (!node.isObject()) {
            return Collections.emptyMap();
        }

        node.fields().forEachRemaining(entry -> {
            List<String> urls = extractUrls(entry.getValue());
            if (!urls.isEmpty()) {
                result.put(entry.getKey(), urls);
            }
        });

        return result;
    }

    private int parseEpisodeNumber(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        Matcher matcher = DIGIT_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private double parseScore(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        String normalized = value.replace(',', '.').replaceAll("[^0-9.]", "").trim();
        if (normalized.isBlank()) {
            return 0;
        }

        try {
            double parsed = Double.parseDouble(normalized);
            return parsed <= 10.0 ? parsed : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String result = value.asText();
        return result == null || result.isBlank() ? fallback : result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String slugify(String input) {
        if (input == null) {
            return "";
        }
        return input
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    }

    private void sleepBackoff(int attempt) {
        try {
            TimeUnit.MILLISECONDS.sleep(250L * attempt);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
