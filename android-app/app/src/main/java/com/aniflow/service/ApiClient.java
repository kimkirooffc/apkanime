package com.aniflow.service;

import com.aniflow.model.Anime;
import com.aniflow.model.Episode;
import com.aniflow.model.StreamInfo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public class ApiClient {
    private static final String BASE_URL = "https://otakudesu-api.vercel.app/";
    private static final Gson GSON = new Gson();
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(19\\d{2}|20\\d{2})");
    private static final Locale ID_LOCALE = new Locale("id", "ID");
    private static final String[] BAD_TITLES = new String[] {
        "untitled", "unknown", "unseen love", "null", "-"
    };

    private static OtakudesuService service;

    public interface OtakudesuService {
        @GET("api/anime/ongoing")
        Call<ApiResponse> getOngoing();

        @GET("api/anime/home")
        Call<ApiResponse> getHome();

        @GET("api/anime/complete?sort=rating")
        Call<ApiResponse> getTopRated();

        @GET("api/anime/search")
        Call<ApiResponse> search(@Query("q") String query);

        @GET("api/anime/genre/{slug}")
        Call<ApiResponse> getByGenre(@Path("slug") String slug);

        @GET("api/anime/details/{slug}")
        Call<DetailResponse> getDetail(@Path("slug") String slug);

        @GET("api/anime/stream/{slug}")
        Call<StreamResponse> getStream(@Path("slug") String slug);

        @GET("api/anime/genre")
        Call<GenreResponse> getGenres();
    }

    public static OtakudesuService getService() {
        if (service == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

            Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

            service = retrofit.create(OtakudesuService.class);
        }
        return service;
    }

    public static class ApiResponse {
        public boolean success;
        public JsonElement data;
    }

    public static class DetailResponse {
        public boolean success;
        public DetailData data;

        public static class DetailData {
            public int id;
            public String title;
            @SerializedName(value = "japaneseTitle", alternate = {"japanese_title"})
            public String japaneseTitle;
            public String slug;
            public String thumbnail;
            @SerializedName(value = "thumbnailUrl", alternate = {"thumbnail_url"})
            public String thumbnailUrl;
            public String cover;
            @SerializedName(value = "coverUrl", alternate = {"cover_url"})
            public String coverUrl;
            public String image;
            @SerializedName(value = "imageUrl", alternate = {"image_url"})
            public String imageUrl;
            public String poster;
            @SerializedName(value = "posterUrl", alternate = {"poster_url"})
            public String posterUrl;
            public String banner;
            public String synopsis;
            public String description;
            public String episodes;
            @SerializedName(value = "totalEpisodes", alternate = {"total_episodes"})
            public String totalEpisodes;
            public String status;
            public String rating;
            public String score;
            public String producer;
            public String type;
            public String duration;
            @SerializedName(value = "releaseDate", alternate = {"release_date"})
            public String releaseDate;
            public String studio;
            public String trailer;
            @SerializedName(value = "trailerUrl", alternate = {"trailer_url"})
            public String trailerUrl;
            public String url;
            public List<String> genres;
            @SerializedName(value = "episodeList", alternate = {"episode_list", "episodes"})
            public List<EpisodeItem> episodeList;
        }
    }

    public static class StreamResponse {
        public boolean success;
        public StreamData data;

        public static class StreamData {
            public String title;
            public String episodeSlug;
            public JsonElement streamingUrls;
            public JsonElement downloadUrls;
            public Navigation navigation;
        }

        public static class Navigation {
            public EpisodeRef prev;
            public EpisodeRef next;
            public EpisodeRef list;
        }

        public static class EpisodeRef {
            public String slug;
        }
    }

    public static class GenreResponse {
        public boolean success;
        public List<GenreItem> data;
    }

    public static class AnimeItem {
        public int id;
        public String slug;
        public String title;
        public String thumbnail;
        public String thumbnailUrl;
        public String cover;
        public String coverUrl;
        public String image;
        public String imageUrl;
        public String poster;
        public String posterUrl;
        public String banner;
        public String synopsis;
        public String description;
        public String episode;
        public String episodes;
        public String totalEpisodes;
        public String status;
        public String rating;
        public String score;
        public List<String> genres;
        public String endpoint;
        public String releaseDay;
        public String releaseDate;
        public String studio;
        public String producer;
        public String duration;
        public String trailer;
        public String trailerUrl;
        public String url;
    }

    public static class EpisodeItem {
        @SerializedName(value = "episodeNumber", alternate = {"episode_number"})
        public int episodeNumber;
        public String title;
        public String slug;
        public String url;
        public String endpoint;
        @SerializedName(value = "releaseDate", alternate = {"release_date"})
        public String releaseDate;
        public String thumbnail;
        public String duration;
    }

    public static class GenreItem {
        public String name;
        public String slug;
    }

    public static List<Anime> parseAnimeList(ApiResponse response) {
        if (response == null || response.data == null || response.data.isJsonNull()) {
            return Collections.emptyList();
        }

        if (response.data.isJsonArray()) {
            return parseAnimeArray(response.data.getAsJsonArray());
        }

        if (!response.data.isJsonObject()) {
            return Collections.emptyList();
        }

        JsonObject object = response.data.getAsJsonObject();
        String[] preferredKeys = new String[] {
            "trending", "ongoing", "popular", "complete", "results", "anime", "list", "items"
        };

        for (String key : preferredKeys) {
            if (object.has(key) && object.get(key).isJsonArray()) {
                return parseAnimeArray(object.getAsJsonArray(key));
            }
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonArray()) {
                return parseAnimeArray(entry.getValue().getAsJsonArray());
            }
        }

        return Collections.emptyList();
    }

    private static List<Anime> parseAnimeArray(JsonArray array) {
        if (array == null || array.size() == 0) {
            return Collections.emptyList();
        }

        List<Anime> result = new ArrayList<>();
        for (JsonElement element : array) {
            try {
                AnimeItem item = GSON.fromJson(element, AnimeItem.class);
                Anime anime = toAnime(item);
                if (anime != null) {
                    result.add(anime);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static Anime toAnime(AnimeItem item) {
        if (item == null) {
            return null;
        }

        String title = cleanTitle(firstNonEmpty(item.title, ""));
        if (isInvalidTitle(title)) {
            return null;
        }

        String slug = firstNonEmpty(item.slug, String.valueOf(item.id));
        String episodeLabel = firstNonEmpty(item.episode, item.episodes, item.totalEpisodes, "").trim();
        int episodes = parseEpisodeCount(episodeLabel);
        int totalEpisodes = parseEpisodeCount(firstNonEmpty(item.totalEpisodes, item.episodes, episodeLabel));

        String cover = normalizeImageUrl(firstNonEmpty(
            item.thumbnail,
            item.thumbnailUrl,
            item.cover,
            item.coverUrl,
            item.image,
            item.imageUrl,
            item.poster,
            item.posterUrl,
            ""
        ), item.url);
        String banner = normalizeImageUrl(firstNonEmpty(item.banner, cover), item.url);

        String rawScore = firstNonEmpty(item.rating, item.score, "-");
        double score = parseScore(rawScore);
        String scoreText = score > 0 ? String.format(Locale.US, "%.2f", score) : "-";

        String releaseInfo = firstNonEmpty(item.releaseDate, item.releaseDay, "");
        String releaseYear = extractYear(firstNonEmpty(item.releaseDate, item.score, releaseInfo));
        if ("-".equals(releaseYear) && !releaseInfo.isEmpty()) {
            releaseYear = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        }

        List<String> genres = normalizeGenres(item.genres, 5);

        String listSynopsis = cleanSynopsis(firstNonEmpty(item.synopsis, ""));
        if (listSynopsis.isEmpty()) {
            listSynopsis = cleanSynopsis(firstNonEmpty(item.description, ""));
        }

        return new Anime(
            item.id != 0 ? item.id : Math.abs(slug.hashCode()),
            slug,
            title,
            cover,
            banner,
            listSynopsis,
            episodes,
            normalizeStatus(firstNonEmpty(item.status, "Unknown")),
            score,
            scoreText,
            genres,
            episodeLabel,
            formatReleaseDate(releaseInfo),
            0,
            0,
            normalizeStudios(firstNonEmpty(item.studio, ""), 2),
            normalizePeople(firstNonEmpty(item.producer, ""), 4),
            normalizeDuration(item.duration),
            releaseYear,
            totalEpisodes,
            firstNonEmpty(item.trailerUrl, item.trailer, ""),
            firstNonEmpty(item.url, "")
        );
    }

    public static class AnimeDetail {
        public final Anime anime;
        public final List<Episode> episodes;
        public final String studio;
        public final String producer;
        public final String type;
        public final String duration;
        public final String releaseDate;

        public AnimeDetail(Anime anime, List<Episode> episodes,
                           String studio, String producer, String type,
                           String duration, String releaseDate) {
            this.anime = anime;
            this.episodes = episodes;
            this.studio = studio;
            this.producer = producer;
            this.type = type;
            this.duration = duration;
            this.releaseDate = releaseDate;
        }
    }

    public static AnimeDetail toAnimeDetail(DetailResponse response) {
        if (response == null || response.data == null) {
            return new AnimeDetail(null, Collections.emptyList(), "", "", "", "", "");
        }

        DetailResponse.DetailData d = response.data;
        String title = cleanTitle(firstNonEmpty(d.title, d.japaneseTitle, ""));
        if (isInvalidTitle(title) && !firstNonEmpty(d.japaneseTitle, "").isEmpty()) {
            title = d.japaneseTitle.trim();
        }
        if (isInvalidTitle(title)) {
            return new AnimeDetail(null, Collections.emptyList(), "", "", "", "", "");
        }

        String scoreRaw = firstNonEmpty(d.rating, d.score, "-");
        double score = parseScore(scoreRaw);
        String scoreText = score > 0 ? String.format(Locale.US, "%.2f", score) : "-";

        String studio = normalizeStudios(firstNonEmpty(d.studio, ""), 2);
        String producer = normalizePeople(firstNonEmpty(d.producer, ""), 4);
        String duration = normalizeDuration(d.duration);

        String releaseDate = formatReleaseDate(firstNonEmpty(d.releaseDate, ""));
        String releaseYear = extractYear(firstNonEmpty(d.releaseDate, ""));

        String episodeText = firstNonEmpty(d.totalEpisodes, d.episodes, "");
        int episodes = parseEpisodeCount(episodeText);
        int totalEpisodes = parseEpisodeCount(firstNonEmpty(d.totalEpisodes, episodeText));

        String synopsis = cleanSynopsis(firstNonEmpty(d.synopsis, ""));
        if (synopsis.isEmpty()) {
            synopsis = cleanSynopsis(firstNonEmpty(d.description, ""));
        }
        if (synopsis.isEmpty()) {
            synopsis = "Sinopsis belum tersedia dari API.";
        }

        String cover = normalizeImageUrl(firstNonEmpty(
            d.thumbnail,
            d.thumbnailUrl,
            d.cover,
            d.coverUrl,
            d.image,
            d.imageUrl,
            d.poster,
            d.posterUrl,
            ""
        ), d.url);
        String banner = normalizeImageUrl(firstNonEmpty(d.banner, cover), d.url);

        Anime anime = new Anime(
            d.id != 0 ? d.id : Math.abs(firstNonEmpty(d.slug, title).hashCode()),
            firstNonEmpty(d.slug, String.valueOf(d.id)),
            title,
            cover,
            banner,
            synopsis,
            episodes,
            normalizeStatus(firstNonEmpty(d.status, "Unknown")),
            score,
            scoreText,
            normalizeGenres(d.genres, 5),
            episodeText,
            releaseDate,
            0,
            0,
            studio,
            producer,
            duration,
            releaseYear,
            totalEpisodes,
            firstNonEmpty(d.trailerUrl, d.trailer, ""),
            firstNonEmpty(d.url, "")
        );

        List<Episode> episodeList = new ArrayList<>();
        Set<String> knownEpisodeSlugs = new LinkedHashSet<>();
        if (d.episodeList != null) {
            for (EpisodeItem e : d.episodeList) {
                if (e == null) {
                    continue;
                }
                String episodeSlug = extractEpisodeSlug(firstNonEmpty(e.slug, e.endpoint, e.url, ""));
                if (episodeSlug.isEmpty()) {
                    continue;
                }
                if (knownEpisodeSlugs.contains(episodeSlug)) {
                    continue;
                }
                knownEpisodeSlugs.add(episodeSlug);

                int number = e.episodeNumber > 0 ? e.episodeNumber : parseEpisodeCount(e.title);
                String episodeTitle = cleanEpisodeTitle(firstNonEmpty(e.title, "Episode " + Math.max(number, 1)));
                String formattedDate = formatReleaseDate(firstNonEmpty(e.releaseDate, ""));
                String endpoint = firstNonEmpty(e.endpoint, episodeSlug);

                episodeList.add(new Episode(
                    number,
                    episodeTitle,
                    episodeSlug,
                    formattedDate,
                    endpoint,
                    normalizeImageUrl(firstNonEmpty(e.thumbnail, cover), d.url),
                    normalizeDuration(firstNonEmpty(e.duration, duration)),
                    inferReleased(firstNonEmpty(e.releaseDate, ""))
                ));
            }
        }

        if (episodeList.size() > 1) {
            episodeList.sort((a, b) -> Integer.compare(b.getEpisodeNumber(), a.getEpisodeNumber()));
        }

        return new AnimeDetail(
            anime,
            episodeList,
            studio,
            producer,
            firstNonEmpty(d.type, "-"),
            duration,
            releaseDate
        );
    }

    public static boolean isDetailUsable(AnimeDetail detail) {
        return detail != null
            && detail.anime != null
            && !isInvalidTitle(detail.anime.getTitle())
            && detail.episodes != null
            && !detail.episodes.isEmpty();
    }

    public static boolean isTopAnimeDisplayable(Anime anime) {
        if (anime == null) {
            return false;
        }

        String title = firstNonEmpty(anime.getTitle(), "");
        if (isInvalidTitle(title)) {
            return false;
        }

        if (anime.getSlug() == null || anime.getSlug().trim().isEmpty()) {
            return false;
        }

        if (anime.getCoverImage() == null || anime.getCoverImage().trim().isEmpty()) {
            return false;
        }

        String normalizedTitle = title.trim();
        if (!normalizedTitle.contains(" ") && normalizedTitle.length() <= 9) {
            return false;
        }

        int epCount = anime.getDisplayEpisodeCount();
        if (epCount <= 0 && firstNonEmpty(anime.getEpisodeLabel(), "").isEmpty()) {
            return false;
        }

        return anime.getScore() > 0;
    }

    public static StreamInfo toStreamInfo(StreamResponse response) {
        if (response == null || response.data == null) {
            return new StreamInfo("", "", Collections.emptyList(),
                Collections.emptyMap(), null, null, null);
        }

        StreamResponse.StreamData d = response.data;
        List<String> streamUrls = parseStreamUrls(d.streamingUrls);
        Map<String, List<String>> downloadUrls = parseDownloadUrls(d.downloadUrls);
        return new StreamInfo(
            firstNonEmpty(d.title, ""),
            firstNonEmpty(d.episodeSlug, ""),
            streamUrls,
            downloadUrls,
            d.navigation != null && d.navigation.prev != null ? d.navigation.prev.slug : null,
            d.navigation != null && d.navigation.next != null ? d.navigation.next.slug : null,
            d.navigation != null && d.navigation.list != null ? d.navigation.list.slug : null
        );
    }

    private static int parseEpisodeCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Matcher matcher = DIGIT_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static double parseScore(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            String sanitized = text.replace(",", ".").replaceAll("[^0-9.]", "");
            if (sanitized.isEmpty()) {
                return 0;
            }
            double value = Double.parseDouble(sanitized);
            if (value > 10.0) {
                return 0;
            }
            return value;
        } catch (Exception e) {
            return 0;
        }
    }

    private static List<String> parseStreamUrls(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();
        collectUrls(element, unique);
        return new ArrayList<>(unique);
    }

    private static Map<String, List<String>> parseDownloadUrls(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            Set<String> unique = new LinkedHashSet<>();
            collectUrls(entry.getValue(), unique);
            if (!unique.isEmpty()) {
                result.put(entry.getKey(), new ArrayList<>(unique));
            }
        }
        return result;
    }

    private static void collectUrls(JsonElement element, Set<String> output) {
        if (element == null || element.isJsonNull() || output == null) {
            return;
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            if (value != null && value.startsWith("http")) {
                output.add(value);
            }
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                collectUrls(item, output);
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("url") && object.get("url").isJsonPrimitive()) {
                String url = object.get("url").getAsString();
                if (url != null && url.startsWith("http")) {
                    output.add(url);
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectUrls(entry.getValue(), output);
            }
        }
    }

    private static String cleanTitle(String value) {
        String title = firstNonEmpty(value, "");
        title = title.replaceAll("(?i)subtitle indonesia", "").trim();
        title = title.replaceAll("\\s{2,}", " ");
        return title;
    }

    private static String cleanEpisodeTitle(String value) {
        String title = firstNonEmpty(value, "");
        title = title.replaceAll("(?i)subtitle indonesia", "").trim();
        title = title.replaceAll("\\s{2,}", " ");
        return title;
    }

    private static String cleanSynopsis(String text) {
        String value = firstNonEmpty(text, "").trim();
        if (value.isEmpty()) {
            return "";
        }

        value = value.replace("\u00A0", " ").replaceAll("\\s+", " ").trim();
        String lower = value.toLowerCase(Locale.US);

        int seasonMentions = countOccurrences(lower, "season ");
        int episodeMentions = countOccurrences(lower, "episode ");
        boolean listingLike = seasonMentions >= 3 || (seasonMentions >= 2 && episodeMentions >= 4);

        if (listingLike) {
            return "";
        }

        return value;
    }

    private static int countOccurrences(String source, String token) {
        if (source == null || token == null || token.isEmpty()) {
            return 0;
        }

        int count = 0;
        int idx = 0;
        while (true) {
            int found = source.indexOf(token, idx);
            if (found < 0) {
                break;
            }
            count++;
            idx = found + token.length();
        }
        return count;
    }

    private static List<String> normalizeGenres(List<String> genres, int max) {
        if (genres == null || genres.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String genre : genres) {
            String cleaned = firstNonEmpty(genre, "").trim();
            if (!cleaned.isEmpty()) {
                unique.add(cleaned);
            }
            if (unique.size() >= max) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private static String normalizePeople(String raw, int max) {
        String value = firstNonEmpty(raw, "").trim();
        if (value.isEmpty()) {
            return "-";
        }

        value = value
            .replaceAll("(?i)produser\\s*[:：]\\s*", "")
            .replaceAll("(?i)producer\\s*[:：]\\s*", "")
            .replaceAll("\\s{2,}", " ")
            .trim();

        String[] parts = value.split("[,;/]|\\band\\b|\\bdan\\b");
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();

        for (String part : parts) {
            String token = part == null ? "" : part.trim();
            if (token.isEmpty()) {
                continue;
            }
            token = token.replaceAll("(?i)^studio\\s+", "").trim();
            if (token.equalsIgnoreCase("studio")) {
                continue;
            }
            cleaned.add(token);
            if (cleaned.size() >= max) {
                break;
            }
        }

        if (cleaned.isEmpty()) {
            return "-";
        }

        return String.join(", ", cleaned);
    }

    private static String normalizeStudios(String raw, int max) {
        String value = firstNonEmpty(raw, "").trim();
        if (value.isEmpty()) {
            return "-";
        }

        String lower = value.toLowerCase(Locale.US);
        if (lower.contains("produser") && lower.contains("studio")) {
            int idx = lower.lastIndexOf("studio");
            if (idx >= 0 && idx + 6 < value.length()) {
                value = value.substring(idx + 6).trim();
            }
        }

        value = value.replaceAll("(?i)^studio\\s*[:：]?\\s*", "");
        return normalizePeople(value, max);
    }

    private static String normalizeDuration(String raw) {
        String value = firstNonEmpty(raw, "").trim();
        if (value.isEmpty()) {
            return "-";
        }

        Matcher matcher = DIGIT_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group(1) + " menit";
        }
        return value;
    }

    private static String extractEpisodeSlug(String raw) {
        String value = firstNonEmpty(raw, "").trim();
        if (value.isEmpty()) {
            return "";
        }

        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int fragmentIndex = value.indexOf('#');
        if (fragmentIndex >= 0) {
            value = value.substring(0, fragmentIndex);
        }

        String lower = value.toLowerCase(Locale.US);
        String streamPrefix = "/api/anime/stream/";
        int streamIndex = lower.indexOf(streamPrefix);
        if (streamIndex >= 0) {
            String candidate = value.substring(streamIndex + streamPrefix.length()).trim();
            return candidate.replaceAll("^/+", "").replaceAll("/+$", "");
        }

        if (!value.contains("/")) {
            return value;
        }

        String normalized = value.replaceAll("/+$", "");
        int slash = normalized.lastIndexOf('/');
        if (slash < 0 || slash >= normalized.length() - 1) {
            return "";
        }
        return normalized.substring(slash + 1).trim();
    }

    public static String formatReleaseDate(String raw) {
        String value = firstNonEmpty(raw, "").trim();
        if (value.isEmpty()) {
            return "-";
        }

        value = value.replace(",", " ").replaceAll("\\s+", " ").trim();
        String[] parts = value.split(" ");
        if (parts.length < 2) {
            return value;
        }

        for (int i = 0; i < parts.length; i++) {
            String shortMonth = shortMonth(parts[i]);
            if (!shortMonth.isEmpty()) {
                parts[i] = shortMonth;
            }
        }

        if (isMonth(parts[0]) && isNumber(parts[1])) {
            if (parts.length >= 3 && isYear(parts[2])) {
                return twoDigit(parts[1]) + " " + parts[0] + " " + parts[2];
            }
            return twoDigit(parts[1]) + " " + parts[0];
        }

        if (isNumber(parts[0]) && isMonth(parts[1])) {
            if (parts.length >= 3 && isYear(parts[2])) {
                return twoDigit(parts[0]) + " " + parts[1] + " " + parts[2];
            }
            return twoDigit(parts[0]) + " " + parts[1];
        }

        return String.join(" ", parts);
    }

    private static String shortMonth(String token) {
        String value = firstNonEmpty(token, "").toLowerCase(Locale.US);
        return switch (value) {
            case "jan", "january", "januari" -> "Jan";
            case "feb", "february", "februari" -> "Feb";
            case "mar", "march", "maret" -> "Mar";
            case "apr", "april" -> "Apr";
            case "mei", "may" -> "Mei";
            case "jun", "june", "juni" -> "Jun";
            case "jul", "july", "juli" -> "Jul";
            case "aug", "agustus", "august", "agu" -> "Agu";
            case "sep", "september" -> "Sep";
            case "oct", "okt", "october", "oktober" -> "Okt";
            case "nov", "november" -> "Nov";
            case "dec", "des", "december", "desember" -> "Des";
            default -> "";
        };
    }

    private static boolean inferReleased(String releaseDateRaw) {
        String raw = firstNonEmpty(releaseDateRaw, "").trim();
        if (raw.isEmpty()) {
            return true;
        }

        long millis = tryParseDateMillis(raw);
        if (millis <= 0L) {
            String lower = raw.toLowerCase(Locale.US);
            return !(lower.contains("coming soon") || lower.contains("preview") || lower.contains("upcoming"));
        }

        long now = System.currentTimeMillis();
        return millis <= now + TimeUnit.HOURS.toMillis(18);
    }

    private static long tryParseDateMillis(String raw) {
        String formatted = formatReleaseDate(raw);
        String[] patterns = new String[] {
            "dd MMM yyyy", "d MMM yyyy", "dd MMMM yyyy", "d MMMM yyyy",
            "MMM dd yyyy", "MMM d yyyy", "MMMM dd yyyy", "MMMM d yyyy",
            "dd MMM", "d MMM", "dd MMMM", "d MMMM"
        };

        for (String pattern : patterns) {
            long parsed = tryParseWithPattern(formatted, pattern, Locale.ENGLISH);
            if (parsed > 0L) {
                return parsed;
            }

            parsed = tryParseWithPattern(formatted, pattern, ID_LOCALE);
            if (parsed > 0L) {
                return parsed;
            }
        }

        return 0L;
    }

    private static long tryParseWithPattern(String text, String pattern, Locale locale) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, locale);
            sdf.setLenient(false);
            sdf.setTimeZone(TimeZone.getDefault());
            Date parsed = sdf.parse(text);
            if (parsed == null) {
                return 0L;
            }

            if (!pattern.contains("yyyy")) {
                Calendar source = Calendar.getInstance();
                source.setTime(parsed);
                Calendar now = Calendar.getInstance();
                source.set(Calendar.YEAR, now.get(Calendar.YEAR));
                parsed = source.getTime();
            }
            return parsed.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String extractYear(String raw) {
        String value = firstNonEmpty(raw, "");
        Matcher matcher = YEAR_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "-";
    }

    private static String normalizeImageUrl(String rawImageUrl, String pageUrl) {
        String value = firstNonEmpty(rawImageUrl, "");
        if (value.isEmpty()) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }

        String origin = extractOrigin(pageUrl);
        if (origin.isEmpty()) {
            return value;
        }
        if (value.startsWith("/")) {
            return origin + value;
        }
        return origin + "/" + value;
    }

    private static String extractOrigin(String rawUrl) {
        String value = firstNonEmpty(rawUrl, "");
        if (value.isEmpty()) {
            return "";
        }
        try {
            URI uri = URI.create(value);
            String scheme = firstNonEmpty(uri.getScheme(), "");
            String host = firstNonEmpty(uri.getHost(), "");
            if (scheme.isEmpty() || host.isEmpty()) {
                return "";
            }
            return scheme + "://" + host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalizeStatus(String raw) {
        String value = firstNonEmpty(raw, "").trim();
        if (value.isEmpty()) {
            return "Unknown";
        }

        String lower = value.toLowerCase(Locale.US);
        if (lower.contains("complete") || lower.contains("completed") || lower.contains("finish") || lower.contains("end")) {
            return "Completed";
        }
        if (lower.contains("ongoing") || lower.contains("release")) {
            return "Ongoing";
        }
        if (lower.contains("upcoming") || lower.contains("coming") || lower.contains("soon") || lower.contains("preview")) {
            return "Upcoming";
        }
        return value;
    }

    private static boolean isInvalidTitle(String title) {
        String value = firstNonEmpty(title, "").trim().toLowerCase(Locale.US);
        if (value.isEmpty()) {
            return true;
        }

        for (String bad : BAD_TITLES) {
            if (value.equals(bad)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMonth(String token) {
        return !shortMonth(token).isEmpty();
    }

    private static boolean isNumber(String token) {
        try {
            Integer.parseInt(token);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isYear(String token) {
        if (!isNumber(token)) {
            return false;
        }
        int value = Integer.parseInt(token);
        return value >= 1900 && value <= 2100;
    }

    private static String twoDigit(String day) {
        try {
            return String.format(Locale.US, "%02d", Integer.parseInt(day));
        } catch (Exception ignored) {
            return day;
        }
    }

    private static String firstNonEmpty(String... values) {
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
}
