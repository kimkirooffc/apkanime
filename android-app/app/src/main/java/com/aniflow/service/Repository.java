package com.aniflow.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.aniflow.db.AniFlowDatabase;
import com.aniflow.db.dao.DownloadsDao;
import com.aniflow.db.dao.HistoryDao;
import com.aniflow.db.dao.WatchlistDao;
import com.aniflow.db.entity.DownloadEntity;
import com.aniflow.db.entity.HistoryEntity;
import com.aniflow.db.entity.WatchlistEntity;
import com.aniflow.model.Anime;
import com.aniflow.model.DownloadItem;
import com.aniflow.model.Episode;
import com.aniflow.model.StreamInfo;
import com.aniflow.model.WatchHistoryItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Response;

public class Repository {
    private static final String PREFS = "aniflow_prefs";
    private static final long MIN_HISTORY_PROGRESS_MS = 5000L;
    private static final int MAX_WATCHLIST_ITEMS = 200;
    private static final int MAX_HISTORY_ITEMS = 300;
    private static final long CACHE_TTL_MS = 15000L;
    private static final Pattern LAST_NUMBER_PATTERN = Pattern.compile("(\\d+)(?!.*\\d)");
    private static final String PREF_THEME_MODE = "theme_mode";
    private static final String PREF_ACCENT_COLOR = "accent_color";
    private static final String PREF_LEGACY_DARK_MODE = "dark_mode";

    private final ApiClient.OtakudesuService api;
    private final AnichinClient anichin;
    private final SharedPreferences prefs;
    private final HistoryDao historyDao;
    private final WatchlistDao watchlistDao;
    private final DownloadsDao downloadsDao;
    private final Handler mainHandler;

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private final Object localDataLock = new Object();
    private List<Anime> watchlistCache;
    private long watchlistCacheAt;
    private List<WatchHistoryItem> historyCache;
    private long historyCacheAt;
    private Integer downloadCountCache;
    private long downloadCountCacheAt;

    public Repository(Context context) {
        this.api = ApiClient.getService();
        this.anichin = new AnichinClient();
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AniFlowDatabase database = AniFlowDatabase.getInstance(context.getApplicationContext());
        this.historyDao = database.historyDao();
        this.watchlistDao = database.watchlistDao();
        this.downloadsDao = database.downloadsDao();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public LiveData<Boolean> loading() { return loading; }
    public LiveData<String> error() { return error; }

    public void getTrending(Callback<List<Anime>> callback) {
        runListTask("Failed to load anime", () -> fetchOtakudesuList(api.getOngoing()), callback);
    }

    public void getTopRated(Callback<List<Anime>> callback) {
        runListTask("Failed to load donghua", () -> {
            List<Anime> result = anichin.getCompletedTopRated();
            if (result == null) {
                return Collections.emptyList();
            }
            List<Anime> sorted = new ArrayList<>(result);
            sorted.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            return sorted;
        }, callback);
    }

    public void search(String query, Callback<List<Anime>> callback) {
        String normalized = safe(query);
        if (normalized.isEmpty()) {
            runListTask("Failed to load anime catalog", () -> {
                List<Anime> anime = safeProviderList(() -> fetchOtakudesuList(api.getOngoing()));
                List<Anime> donghua = safeProviderList(() -> anichin.getOngoing());
                return mergeAnimeLists(anime, donghua);
            }, callback);
            return;
        }

        runListTask("Search failed", () -> {
            List<Anime> anime = safeProviderList(() -> fetchOtakudesuList(api.search(normalized)));
            List<Anime> donghua = safeProviderList(() -> anichin.search(normalized));
            return mergeAnimeLists(anime, donghua);
        }, callback);
    }

    public void getOngoing(Callback<List<Anime>> callback) {
        runListTask("Failed to load ongoing anime", () -> fetchOtakudesuList(api.getOngoing()), callback);
    }

    public void getAnimeDetail(String slug, Callback<ApiClient.AnimeDetail> callback) {
        getAnimeDetail(slug, "", callback);
    }

    public void getAnimeDetail(String slug, String titleHint, Callback<ApiClient.AnimeDetail> callback) {
        if (slug == null || slug.trim().isEmpty()) {
            callback.onFailure(new Exception("Detail not found"));
            return;
        }

        String normalizedSlug = slug.trim();
        if (AnichinClient.isAnichinSlug(normalizedSlug)) {
            runAnichinTask(() -> anichin.getAnimeDetail(normalizedSlug), new Callback<ApiClient.AnimeDetail>() {
                @Override
                public void onSuccess(ApiClient.AnimeDetail result) {
                    if (ApiClient.isDetailUsable(result)) {
                        callback.onSuccess(result);
                        return;
                    }
                    callback.onFailure(new Exception("Detail not found"));
                }

                @Override
                public void onFailure(Throwable error) {
                    callback.onFailure(error != null ? error : new Exception("Detail not found"));
                }
            });
            return;
        }

        api.getDetail(normalizedSlug).enqueue(new retrofit2.Callback<ApiClient.DetailResponse>() {
            @Override
            public void onResponse(Call<ApiClient.DetailResponse> call, Response<ApiClient.DetailResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiClient.AnimeDetail detail = ApiClient.toAnimeDetail(response.body());
                    if (ApiClient.isDetailUsable(detail)) {
                        callback.onSuccess(detail);
                        return;
                    }
                }
                callback.onFailure(new Exception("Detail not found"));
            }

            @Override
            public void onFailure(Call<ApiClient.DetailResponse> call, Throwable t) {
                callback.onFailure(t != null ? t : new Exception("Detail not found"));
            }
        });
    }

    public void getStreamInfo(String slug, Callback<StreamInfo> callback) {
        if (slug == null || slug.trim().isEmpty()) {
            callback.onFailure(new Exception("Stream not found"));
            return;
        }

        String normalizedSlug = slug.trim();
        if (AnichinClient.isAnichinSlug(normalizedSlug)) {
            runAnichinTask(() -> anichin.getStreamInfo(normalizedSlug), new Callback<StreamInfo>() {
                @Override
                public void onSuccess(StreamInfo result) {
                    if (result != null && result.firstPlayableUrl() != null) {
                        callback.onSuccess(result);
                        return;
                    }
                    callback.onFailure(new Exception("Stream not found"));
                }

                @Override
                public void onFailure(Throwable error) {
                    callback.onFailure(error != null ? error : new Exception("Stream not found"));
                }
            });
            return;
        }

        api.getStream(normalizedSlug).enqueue(new retrofit2.Callback<ApiClient.StreamResponse>() {
            @Override
            public void onResponse(Call<ApiClient.StreamResponse> call, Response<ApiClient.StreamResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    StreamInfo info = ApiClient.toStreamInfo(response.body());
                    if (info != null && info.firstPlayableUrl() != null) {
                        callback.onSuccess(info);
                        return;
                    }
                }
                callback.onFailure(new Exception("Stream not found"));
            }

            @Override
            public void onFailure(Call<ApiClient.StreamResponse> call, Throwable t) {
                callback.onFailure(t != null ? t : new Exception("Stream not found"));
            }
        });
    }

    private List<Anime> fetchOtakudesuList(Call<ApiClient.ApiResponse> call) throws Exception {
        if (call == null) {
            return Collections.emptyList();
        }

        Response<ApiClient.ApiResponse> response = call.execute();
        if (!response.isSuccessful() || response.body() == null) {
            return Collections.emptyList();
        }
        return ApiClient.parseAnimeList(response.body());
    }

    private void runListTask(String failureMessage,
                             AnichinTask<List<Anime>> task,
                             Callback<List<Anime>> callback) {
        loading.postValue(true);
        runAnichinTask(task, new Callback<List<Anime>>() {
            @Override
            public void onSuccess(List<Anime> result) {
                loading.postValue(false);
                if (result != null && !result.isEmpty()) {
                    callback.onSuccess(result);
                    return;
                }
                Repository.this.error.postValue(failureMessage);
                callback.onFailure(new Exception(failureMessage));
            }

            @Override
            public void onFailure(Throwable error) {
                loading.postValue(false);
                String message = error != null ? safe(error.getMessage()) : "";
                if (message.isEmpty()) {
                    message = failureMessage;
                }
                Repository.this.error.postValue(message);
                callback.onFailure(error != null ? error : new Exception(failureMessage));
            }
        });
    }

    private List<Anime> safeProviderList(AnichinTask<List<Anime>> task) {
        try {
            List<Anime> result = task.run();
            return result != null ? result : Collections.emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<Anime> mergeAnimeLists(List<Anime> animeList, List<Anime> donghuaList) {
        LinkedHashMap<String, Anime> merged = new LinkedHashMap<>();
        appendAnimeList(merged, animeList);
        appendAnimeList(merged, donghuaList);
        return new ArrayList<>(merged.values());
    }

    private void appendAnimeList(Map<String, Anime> target, List<Anime> source) {
        if (source == null || source.isEmpty()) {
            return;
        }

        for (Anime anime : source) {
            if (anime == null) {
                continue;
            }

            String slug = safe(anime.getSlug());
            String key = slug.isEmpty()
                ? ("anime_" + target.size() + "_" + Math.abs(safe(anime.getTitle()).hashCode()))
                : slug.toLowerCase(Locale.US);
            if (!target.containsKey(key)) {
                target.put(key, anime);
            }
        }
    }

    private <T> void runAnichinTask(AnichinTask<T> task, Callback<T> callback) {
        new Thread(() -> {
            try {
                T result = task.run();
                if (callback == null) {
                    return;
                }
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Throwable error) {
                if (callback == null) {
                    return;
                }
                mainHandler.post(() -> callback.onFailure(error));
            }
        }).start();
    }

    public void getGenres(Callback<List<String>> callback) {
        api.getGenres().enqueue(new retrofit2.Callback<ApiClient.GenreResponse>() {
            @Override
            public void onResponse(Call<ApiClient.GenreResponse> call, Response<ApiClient.GenreResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    List<String> genres = new ArrayList<>();
                    for (ApiClient.GenreItem item : response.body().data) {
                        if (item.name != null) {
                            genres.add(item.name);
                        }
                    }
                    callback.onSuccess(genres);
                } else {
                    callback.onSuccess(Collections.emptyList());
                }
            }

            @Override
            public void onFailure(Call<ApiClient.GenreResponse> call, Throwable t) {
                callback.onSuccess(Collections.emptyList());
            }
        });
    }

    public List<Anime> getWatchlist() {
        synchronized (localDataLock) {
            if (watchlistCache != null && isCacheValid(watchlistCacheAt)) {
                return new ArrayList<>(watchlistCache);
            }
        }

        List<WatchlistEntity> rows = watchlistDao.getAllOrderByLatest();
        List<Anime> mapped = new ArrayList<>();
        for (WatchlistEntity row : rows) {
            mapped.add(toAnime(row));
        }

        synchronized (localDataLock) {
            watchlistCache = mapped;
            watchlistCacheAt = System.currentTimeMillis();
            return new ArrayList<>(watchlistCache);
        }
    }

    public void addToWatchlist(Anime anime) {
        if (anime == null || safe(anime.getSlug()).isEmpty()) {
            return;
        }

        WatchlistEntity row = toWatchlistEntity(anime);
        watchlistDao.deleteByAnimeSlug(row.getAnimeSlug());
        watchlistDao.insert(row);
        watchlistDao.trimToMaxRows(MAX_WATCHLIST_ITEMS);
        invalidateWatchlistCache();
        invalidateHistoryCache();
    }

    public void removeFromWatchlist(Anime anime) {
        if (anime == null || safe(anime.getSlug()).isEmpty()) {
            return;
        }

        watchlistDao.deleteByAnimeSlug(anime.getSlug());
        invalidateWatchlistCache();
        invalidateHistoryCache();
    }

    public boolean isInWatchlist(Anime anime) {
        if (anime == null || safe(anime.getSlug()).isEmpty()) {
            return false;
        }

        List<Anime> list = getWatchlist();
        for (Anime item : list) {
            if (anime.getSlug().equals(item.getSlug())) {
                return true;
            }
        }
        return false;
    }

    public void recordWatchProgress(Anime anime, Episode episode, long progressMs, long durationMs) {
        if (anime == null || episode == null || safe(anime.getSlug()).isEmpty() || safe(episode.getSlug()).isEmpty()) {
            return;
        }
        if (progressMs < MIN_HISTORY_PROGRESS_MS) {
            return;
        }

        String animeSlug = anime.getSlug();
        String episodeSlug = episode.getSlug();
        long now = System.currentTimeMillis();

        HistoryEntity existing = historyDao.getByAnimeAndEpisode(animeSlug, episodeSlug);
        if (existing == null) {
            HistoryEntity row = new HistoryEntity();
            row.setAnimeSlug(animeSlug);
            row.setEpisodeSlug(episodeSlug);
            row.setProgress(progressMs);
            row.setTimestamp(now);
            historyDao.insert(row);
        } else {
            existing.setProgress(Math.max(existing.getProgress(), progressMs));
            existing.setTimestamp(now);
            historyDao.update(existing);
        }

        historyDao.trimToMaxRows(MAX_HISTORY_ITEMS);
        invalidateHistoryCache();
    }

    public List<WatchHistoryItem> getWatchHistoryItems() {
        synchronized (localDataLock) {
            if (historyCache != null && isCacheValid(historyCacheAt)) {
                return new ArrayList<>(historyCache);
            }
        }

        List<HistoryEntity> rows = historyDao.getAllOrderByLatest();
        List<WatchHistoryItem> mapped = mapHistoryRows(rows);

        synchronized (localDataLock) {
            historyCache = mapped;
            historyCacheAt = System.currentTimeMillis();
            return new ArrayList<>(historyCache);
        }
    }

    public List<WatchHistoryItem> getContinueWatchingItems(int maxItems) {
        List<WatchHistoryItem> sorted = getWatchHistoryItems();
        Map<String, WatchHistoryItem> uniqueByAnime = new LinkedHashMap<>();

        for (WatchHistoryItem item : sorted) {
            if (item == null || item.getAnimeSlug() == null) {
                continue;
            }
            if (item.getProgressMs() < MIN_HISTORY_PROGRESS_MS) {
                continue;
            }
            if (!uniqueByAnime.containsKey(item.getAnimeSlug())) {
                uniqueByAnime.put(item.getAnimeSlug(), item);
            }
        }

        List<WatchHistoryItem> result = new ArrayList<>(uniqueByAnime.values());
        if (maxItems > 0 && result.size() > maxItems) {
            return new ArrayList<>(result.subList(0, maxItems));
        }
        return result;
    }

    public WatchHistoryItem getLatestHistoryForAnime(String animeSlug) {
        if (animeSlug == null || animeSlug.trim().isEmpty()) {
            return null;
        }

        HistoryEntity row = historyDao.getLatestByAnime(animeSlug.trim());
        if (row == null || row.getProgress() < MIN_HISTORY_PROGRESS_MS) {
            return null;
        }

        Map<String, Anime> watchlistMap = buildWatchlistMap(getWatchlist());
        return toWatchHistoryItem(row, watchlistMap);
    }

    public List<Anime> getHistory() {
        List<WatchHistoryItem> history = getContinueWatchingItems(50);
        List<Anime> result = new ArrayList<>();
        for (WatchHistoryItem item : history) {
            result.add(item.toAnime());
        }
        return result;
    }

    public WatchHistoryItem getHistoryById(long id) {
        HistoryEntity row = historyDao.getById(id);
        if (row == null) {
            return null;
        }
        return toWatchHistoryItem(row, buildWatchlistMap(getWatchlist()));
    }

    public void deleteHistoryById(long id) {
        historyDao.deleteById(id);
        invalidateHistoryCache();
    }

    public void clearHistory() {
        historyDao.clear();
        invalidateHistoryCache();
    }

    public void saveDownload(String episodeSlug, String path, String resolusi) {
        String normalizedEpisodeSlug = safe(episodeSlug);
        String normalizedPath = safe(path);
        String normalizedResolusi = safe(resolusi);
        if (normalizedEpisodeSlug.isEmpty() || normalizedPath.isEmpty()) {
            return;
        }
        if (normalizedResolusi.isEmpty()) {
            normalizedResolusi = "Auto";
        }

        DownloadEntity existing = downloadsDao.getByEpisodeAndResolution(normalizedEpisodeSlug, normalizedResolusi);
        if (existing == null) {
            DownloadEntity row = new DownloadEntity();
            row.setEpisodeSlug(normalizedEpisodeSlug);
            row.setPath(normalizedPath);
            row.setResolusi(normalizedResolusi);
            downloadsDao.insert(row);
        } else {
            existing.setPath(normalizedPath);
            downloadsDao.update(existing);
        }
        invalidateDownloadCountCache();
    }

    public List<DownloadItem> getDownloads() {
        List<DownloadEntity> rows = downloadsDao.getAllOrderByLatest();
        List<DownloadItem> result = new ArrayList<>();
        for (DownloadEntity row : rows) {
            result.add(new DownloadItem(
                row.getId(),
                row.getEpisodeSlug(),
                row.getPath(),
                row.getResolusi()
            ));
        }
        return result;
    }

    public DownloadItem getDownloadById(long id) {
        DownloadEntity row = downloadsDao.getById(id);
        if (row == null) {
            return null;
        }
        return new DownloadItem(row.getId(), row.getEpisodeSlug(), row.getPath(), row.getResolusi());
    }

    public void removeDownload(long id) {
        downloadsDao.deleteById(id);
        invalidateDownloadCountCache();
    }

    public int getDownloadCount() {
        synchronized (localDataLock) {
            if (downloadCountCache != null && isCacheValid(downloadCountCacheAt)) {
                return downloadCountCache;
            }
        }

        int count = downloadsDao.count();
        synchronized (localDataLock) {
            downloadCountCache = count;
            downloadCountCacheAt = System.currentTimeMillis();
        }
        return count;
    }

    public String getThemeMode() {
        String mode = safe(prefs.getString(PREF_THEME_MODE, ""));
        if (mode.isEmpty()) {
            return prefs.getBoolean(PREF_LEGACY_DARK_MODE, false) ? "dark" : "system";
        }
        if ("light".equalsIgnoreCase(mode)) {
            return "light";
        }
        if ("dark".equalsIgnoreCase(mode)) {
            return "dark";
        }
        if ("amoled".equalsIgnoreCase(mode)) {
            return "amoled";
        }
        return "system";
    }

    public void setThemeMode(String mode) {
        String normalized = normalizeThemeMode(mode);
        prefs.edit()
            .putString(PREF_THEME_MODE, normalized)
            .putBoolean(PREF_LEGACY_DARK_MODE, "dark".equals(normalized) || "amoled".equals(normalized))
            .apply();
    }

    public String getAccentColor() {
        String accent = normalizeAccent(prefs.getString(PREF_ACCENT_COLOR, ""));
        return accent.isEmpty() ? "purple" : accent;
    }

    public void setAccentColor(String accent) {
        prefs.edit().putString(PREF_ACCENT_COLOR, normalizeAccent(accent)).apply();
    }

    public boolean isDarkMode() {
        String mode = getThemeMode();
        return "dark".equals(mode) || "amoled".equals(mode);
    }

    public void setDarkMode(boolean enabled) {
        setThemeMode(enabled ? "dark" : "light");
    }

    private List<WatchHistoryItem> mapHistoryRows(List<HistoryEntity> rows) {
        Map<String, Anime> watchlistMap = buildWatchlistMap(getWatchlist());
        List<WatchHistoryItem> mapped = new ArrayList<>();
        for (HistoryEntity row : rows) {
            mapped.add(toWatchHistoryItem(row, watchlistMap));
        }
        return mapped;
    }

    private WatchHistoryItem toWatchHistoryItem(HistoryEntity row, Map<String, Anime> watchlistMap) {
        String animeSlug = safe(row.getAnimeSlug());
        String episodeSlug = safe(row.getEpisodeSlug());

        Anime anime = watchlistMap.get(animeSlug);
        if (anime == null) {
            anime = fallbackAnime(animeSlug);
        }

        int episodeNumber = parseEpisodeNumber(episodeSlug);
        String episodeTitle = episodeNumber > 0 ? "Episode " + episodeNumber : safe(prettifySlug(episodeSlug));
        if (episodeTitle.isEmpty()) {
            episodeTitle = "Episode terbaru";
        }

        Episode episode = new Episode(
            episodeNumber,
            episodeTitle,
            episodeSlug,
            "",
            episodeSlug
        );

        return new WatchHistoryItem(
            anime,
            episode,
            row.getProgress(),
            0L,
            row.getTimestamp()
        );
    }

    private Map<String, Anime> buildWatchlistMap(List<Anime> watchlist) {
        Map<String, Anime> map = new LinkedHashMap<>();
        for (Anime anime : watchlist) {
            if (anime == null || safe(anime.getSlug()).isEmpty()) {
                continue;
            }
            map.put(anime.getSlug(), anime);
        }
        return map;
    }

    private Anime fallbackAnime(String slug) {
        String title = prettifySlug(slug);
        if (title.isEmpty()) {
            title = "Anime";
        }
        return new Anime(
            Math.abs(slug.hashCode()),
            slug,
            title,
            "",
            "",
            "",
            0,
            "Unknown",
            0,
            "-",
            Collections.emptyList(),
            "",
            ""
        );
    }

    private static Anime toAnime(WatchlistEntity row) {
        String slug = safe(row.getAnimeSlug());
        String title = safe(row.getJudul());
        if (title.isEmpty()) {
            title = prettifySlug(slug);
        }

        return new Anime(
            Math.abs(slug.hashCode()),
            slug,
            title,
            safe(row.getThumbnail()),
            safe(row.getThumbnail()),
            "",
            0,
            "Unknown",
            0,
            "-",
            Collections.emptyList(),
            "",
            ""
        );
    }

    private static WatchlistEntity toWatchlistEntity(Anime anime) {
        WatchlistEntity row = new WatchlistEntity();
        row.setAnimeSlug(safe(anime.getSlug()));
        row.setJudul(safeOr(anime.getTitle(), prettifySlug(anime.getSlug())));
        row.setThumbnail(safe(anime.getCoverImage()));
        return row;
    }

    private static int parseEpisodeNumber(String episodeSlug) {
        if (episodeSlug == null || episodeSlug.trim().isEmpty()) {
            return 0;
        }
        Matcher matcher = LAST_NUMBER_PATTERN.matcher(episodeSlug);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String prettifySlug(String value) {
        String normalized = safe(value).replace('_', '-').replace('-', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "";
        }

        String[] words = normalized.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (word.length() == 1) {
                builder.append(word.toUpperCase(Locale.US));
            } else {
                builder.append(Character.toUpperCase(word.charAt(0)));
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    private boolean isCacheValid(long cachedAt) {
        return cachedAt > 0L && (System.currentTimeMillis() - cachedAt) <= CACHE_TTL_MS;
    }

    private void invalidateWatchlistCache() {
        synchronized (localDataLock) {
            watchlistCache = null;
            watchlistCacheAt = 0L;
        }
    }

    private void invalidateHistoryCache() {
        synchronized (localDataLock) {
            historyCache = null;
            historyCacheAt = 0L;
        }
    }

    private void invalidateDownloadCountCache() {
        synchronized (localDataLock) {
            downloadCountCache = null;
            downloadCountCacheAt = 0L;
        }
    }

    private static String safeOr(String value, String fallback) {
        String safe = safe(value);
        return safe.isEmpty() ? fallback : safe;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeThemeMode(String value) {
        String lower = safe(value).toLowerCase(Locale.US);
        if ("light".equals(lower) || "dark".equals(lower) || "amoled".equals(lower) || "system".equals(lower)) {
            return lower;
        }
        return "system";
    }

    private static String normalizeAccent(String value) {
        String lower = safe(value).toLowerCase(Locale.US);
        if ("blue".equals(lower)
            || "purple".equals(lower)
            || "green".equals(lower)
            || "red".equals(lower)
            || "orange".equals(lower)) {
            return lower;
        }
        return "purple";
    }

    private interface AnichinTask<T> {
        T run() throws Exception;
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onFailure(Throwable error);
    }
}
