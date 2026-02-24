package com.aniflow.service;

import com.aniflow.app.AppState;
import com.aniflow.model.Anime;
import com.aniflow.model.AnimeDetail;
import com.aniflow.model.EpisodeInfo;
import com.aniflow.model.EpisodeStream;
import com.aniflow.util.DesktopNotifier;
import javafx.application.Platform;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class NotificationService {
    private final AppState state;
    private final AnimeRepository repository;
    private final DownloadService downloadService;
    private final DesktopNotifier notifier;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Integer> knownLatestEpisode = new ConcurrentHashMap<>();

    private ScheduledFuture<?> checkTask;

    public NotificationService(AppState state,
                               AnimeRepository repository,
                               DownloadService downloadService,
                               DesktopNotifier notifier) {
        this.state = state;
        this.repository = repository;
        this.downloadService = downloadService;
        this.notifier = notifier;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        state.batteryEfficientModeProperty().addListener((obs, oldValue, newValue) -> restart());
    }

    public void start() {
        scheduleTask();
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel(true);
        }
        scheduler.shutdownNow();
    }

    public void seedWatchlist(List<Anime> animeList) {
        if (animeList == null) {
            return;
        }

        animeList.forEach(anime -> {
            if (anime.getSlug() != null) {
                knownLatestEpisode.put(anime.getSlug(), Math.max(anime.getEpisodes(), 0));
            }
        });
    }

    private void restart() {
        if (checkTask != null) {
            checkTask.cancel(false);
        }
        scheduleTask();
    }

    private void scheduleTask() {
        long intervalHours = state.isBatteryEfficientMode() ? 12 : 6;
        checkTask = scheduler.scheduleWithFixedDelay(this::runSyncCycle, 1, intervalHours, TimeUnit.HOURS);
    }

    private void runSyncCycle() {
        repository.syncOngoingNow();

        List<Anime> watchlist = snapshotWatchlist();
        if (watchlist.isEmpty()) {
            return;
        }

        for (Anime anime : watchlist) {
            if (anime.getSlug() == null || anime.getSlug().isBlank()) {
                continue;
            }

            AnimeDetail detail = repository.getAnimeDetail(anime.getSlug()).join();
            int latest = detail.latestEpisodeNumber();
            int previous = knownLatestEpisode.getOrDefault(anime.getSlug(), latest);

            if (latest > previous) {
                notifier.notify("Episode Baru", anime.getTitle() + " episode " + latest + " sudah tersedia.");

                if (state.isAutoDownloadNewEpisode()) {
                    detail.getEpisodeList().stream()
                        .max(Comparator.comparingInt(EpisodeInfo::getEpisodeNumber))
                        .ifPresent(ep -> autoDownload(anime, ep));
                }
            }

            knownLatestEpisode.put(anime.getSlug(), Math.max(previous, latest));
        }
    }

    private void autoDownload(Anime anime, EpisodeInfo episodeInfo) {
        repository.getEpisodeStream(episodeInfo.getSlug()).thenAccept(stream -> {
            String source = pickDownloadSource(stream);
            if (source == null || source.isBlank()) {
                return;
            }

            Path target = buildAutoDownloadPath(anime.getTitle(), episodeInfo.getEpisodeNumber());
            downloadService.downloadEpisode(source, target, progress -> {
            }).thenAccept(path -> notifier.notify("Auto Download", "Tersimpan: " + path.getFileName()));
        });
    }

    private String pickDownloadSource(EpisodeStream stream) {
        String direct = stream.firstPlayableUrl();
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        return null;
    }

    private Path buildAutoDownloadPath(String title, int episodeNumber) {
        String safeTitle = title.replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now());
        String name = safeTitle + "-E" + episodeNumber + '-' + timestamp + ".bin";
        return Path.of(System.getProperty("user.home"), "Downloads", "AniFlow", name);
    }

    private List<Anime> snapshotWatchlist() {
        CompletableFuture<List<Anime>> future = new CompletableFuture<>();
        Platform.runLater(() -> future.complete(new ArrayList<>(state.getWatchlist())));

        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
