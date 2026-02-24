package com.aniflow.service;

import com.aniflow.app.AppState;
import com.aniflow.model.Anime;
import com.aniflow.model.AnimeDetail;
import com.aniflow.model.EpisodeInfo;
import com.aniflow.model.EpisodeStream;
import com.aniflow.model.Genre;
import com.aniflow.model.SearchFilter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class AnimeRepository {
    private final OtakudesuApiService apiService;
    private final AppState appState;
    private final ExecutorService executor;

    private final Cache<String, List<Anime>> homeCache;
    private final Cache<String, List<Anime>> searchCache;
    private final Cache<String, AnimeDetail> detailCache;
    private final Cache<String, List<EpisodeInfo>> episodeCache;
    private final Cache<String, List<Genre>> genreCache;

    public AnimeRepository(OtakudesuApiService apiService, AppState appState) {
        this.apiService = apiService;
        this.appState = appState;
        this.executor = Executors.newFixedThreadPool(6);

        this.homeCache = Caffeine.newBuilder()
            .maximumSize(40)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

        this.searchCache = Caffeine.newBuilder()
            .maximumSize(160)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

        this.detailCache = Caffeine.newBuilder()
            .maximumSize(300)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

        this.episodeCache = Caffeine.newBuilder()
            .maximumSize(300)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

        this.genreCache = Caffeine.newBuilder()
            .maximumSize(5)
            .expireAfterWrite(Duration.ofHours(24))
            .build();
    }

    public CompletableFuture<List<Anime>> getTrending() {
        return loadListWithFallback("home:trending", apiService::fetchHomeTrending, homeCache);
    }

    public CompletableFuture<List<Anime>> getTopAnime() {
        return loadListWithFallback("home:top", apiService::fetchTopByRating, homeCache);
    }

    public CompletableFuture<List<Anime>> getRecommendations() {
        return loadListWithFallback("home:recommendations", () -> {
            List<Anime> ongoing = apiService.fetchOngoing();
            Collections.shuffle(ongoing);
            return ongoing.stream().limit(6).toList();
        }, homeCache);
    }

    public CompletableFuture<List<Genre>> getGenres() {
        List<Genre> cached = genreCache.getIfPresent("genres");
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Genre> fresh = apiService.fetchGenres();
                genreCache.put("genres", fresh);
                setOffline(false);
                return fresh;
            } catch (Exception ex) {
                setOffline(true);
                return cached != null ? cached : Collections.emptyList();
            }
        }, executor);
    }

    public CompletableFuture<List<Anime>> search(String keyword, SearchFilter filter) {
        String safeQuery = keyword == null ? "" : keyword.trim();
        SearchFilter safeFilter = filter == null ? SearchFilter.empty() : filter;

        String key = "search:"
            + safeQuery.toLowerCase(Locale.ROOT) + ':'
            + safeFilter.genre() + ':'
            + safeFilter.season() + ':'
            + safeFilter.status();

        return loadListWithFallback(key, () -> {
            List<Anime> base;
            String genre = safeFilter.genre();

            if (!safeQuery.isBlank()) {
                base = apiService.searchAnime(safeQuery);
                if (genre != null && !"All".equalsIgnoreCase(genre)) {
                    String genreMatch = genre.toLowerCase(Locale.ROOT);
                    base = base.stream()
                        .filter(a -> a.getGenres().stream().anyMatch(g -> g.equalsIgnoreCase(genreMatch) || g.toLowerCase(Locale.ROOT).contains(genreMatch)))
                        .collect(Collectors.toList());
                }
            } else if (genre != null && !"All".equalsIgnoreCase(genre)) {
                base = apiService.fetchByGenre(slugify(genre));
            } else {
                base = apiService.fetchOngoing();
            }

            String status = safeFilter.status();
            if (status != null && !status.isBlank() && !"Any".equalsIgnoreCase(status)) {
                String statusMatch = status.toLowerCase(Locale.ROOT);
                base = base.stream()
                    .filter(a -> a.getStatus().toLowerCase(Locale.ROOT).contains(statusMatch)
                        || ("ongoing".equals(statusMatch) && a.getStatus().toLowerCase(Locale.ROOT).contains("releas"))
                        || ("finished".equals(statusMatch) && a.getStatus().toLowerCase(Locale.ROOT).contains("complete")))
                    .collect(Collectors.toList());
            }

            return base;
        }, searchCache);
    }

    public CompletableFuture<AnimeDetail> getAnimeDetail(String slug) {
        AnimeDetail cached = detailCache.getIfPresent(slug);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                AnimeDetail detail = apiService.fetchAnimeDetail(slug);
                List<Anime> related = fetchRelated(detail.getAnime());
                AnimeDetail merged = new AnimeDetail(detail.getAnime(), detail.getEpisodeList(), related);
                detailCache.put(slug, merged);
                episodeCache.put(slug, merged.getEpisodeList());
                setOffline(false);
                return merged;
            } catch (Exception ex) {
                setOffline(true);
                AnimeDetail fallback = detailCache.getIfPresent(slug);
                return fallback != null
                    ? fallback
                    : new AnimeDetail(null, Collections.emptyList(), Collections.emptyList());
            }
        }, executor);
    }

    public CompletableFuture<List<EpisodeInfo>> getEpisodeList(String slug) {
        List<EpisodeInfo> cached = episodeCache.getIfPresent(slug);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return getAnimeDetail(slug).thenApply(detail -> {
            List<EpisodeInfo> list = detail.getEpisodeList();
            episodeCache.put(slug, list);
            return list;
        });
    }

    public CompletableFuture<EpisodeStream> getEpisodeStream(String episodeSlug) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                EpisodeStream stream = apiService.fetchEpisodeStream(episodeSlug);
                setOffline(false);
                return stream;
            } catch (Exception ex) {
                setOffline(true);
                return new EpisodeStream(
                    "Episode",
                    episodeSlug,
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    null,
                    null,
                    null
                );
            }
        }, executor);
    }

    public void prefetchDetail(String slug) {
        if (slug == null || slug.isBlank() || detailCache.getIfPresent(slug) != null) {
            return;
        }
        getAnimeDetail(slug);
    }

    public void prefetchNextEpisode(String episodeSlug) {
        if (episodeSlug == null || episodeSlug.isBlank()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                apiService.fetchEpisodeStream(episodeSlug);
            } catch (Exception ignored) {
            }
        }, executor);
    }

    public CompletableFuture<List<Anime>> syncOngoingNow() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Anime> ongoing = apiService.fetchOngoing();
                homeCache.put("home:ongoing-sync", ongoing);
                homeCache.put("home:trending", ongoing);

                List<Anime> recommendationSeed = new ArrayList<>(ongoing);
                Collections.shuffle(recommendationSeed);
                homeCache.put("home:recommendations", recommendationSeed.stream().limit(6).toList());

                setOffline(false);
                return ongoing;
            } catch (Exception ex) {
                setOffline(true);
                List<Anime> cached = homeCache.getIfPresent("home:ongoing-sync");
                return cached != null ? cached : Collections.emptyList();
            }
        }, executor);
    }

    public CompletableFuture<List<Anime>> getByIds(List<Integer> ids) {
        return getTrending().thenApply(list -> list.stream()
            .filter(a -> ids.contains(a.getId()))
            .collect(Collectors.toList()));
    }

    public void clearCache() {
        homeCache.invalidateAll();
        searchCache.invalidateAll();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private List<Anime> fetchRelated(Anime anime) {
        if (anime == null || anime.getGenres().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String genre = anime.getGenres().get(0);
            return apiService.fetchByGenre(slugify(genre)).stream()
                .filter(item -> !Objects.equals(item.getSlug(), anime.getSlug()))
                .limit(8)
                .collect(Collectors.toList());
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private CompletableFuture<List<Anime>> loadListWithFallback(
        String key,
        Loader loader,
        Cache<String, List<Anime>> cache
    ) {
        List<Anime> cached = cache.getIfPresent(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Anime> fresh = loader.load();
                List<Anime> result = fresh == null ? Collections.emptyList() : new ArrayList<>(fresh);
                cache.put(key, result);
                setOffline(false);
                return result;
            } catch (Exception ex) {
                setOffline(true);
                return cached != null ? cached : Collections.emptyList();
            }
        }, executor);
    }

    private void setOffline(boolean offline) {
        if (appState != null) {
            appState.setOfflineMode(offline);
        }
    }

    private String slugify(String input) {
        return input.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    }

    @FunctionalInterface
    private interface Loader {
        List<Anime> load() throws Exception;
    }
}
