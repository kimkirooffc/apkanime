package com.aniflow.ui.pages;

import com.aniflow.app.AppState;
import com.aniflow.model.Anime;
import com.aniflow.model.AnimeDetail;
import com.aniflow.model.EpisodeInfo;
import com.aniflow.model.EpisodeStream;
import com.aniflow.service.AnalyticsService;
import com.aniflow.service.AnimeRepository;
import com.aniflow.service.CastService;
import com.aniflow.service.DownloadService;
import com.aniflow.service.PlaybackProgressService;
import com.jfoenix.controls.JFXSlider;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public class PlayerPage extends BorderPane {
    private static final String DEMO_STREAM_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
    private static final int PROGRESS_TICK_SECONDS = 5;
    private static final int MIN_RESUME_SECONDS = 5;

    private final AppState state;
    private final AnimeRepository repository;
    private final DownloadService downloadService;
    private final CastService castService;
    private final AnalyticsService analyticsService;
    private final PlaybackProgressService progressService;
    private final Consumer<Anime> onOpenAnime;

    private final MediaView mediaView = new MediaView();
    private final Label titleLabel = new Label("Pilih anime dari Home/Search");
    private final Label statusLabel = new Label("Ready");
    private final Label timeLabel = new Label("00:00 / 00:00");
    private final JFXSlider timeline = new JFXSlider();
    private final ProgressBar downloadProgress = new ProgressBar(0);
    private final ComboBox<EpisodeInfo> episodeSelector = new ComboBox<>();
    private final TextArea descriptionArea = new TextArea();
    private final HBox relatedRow = new HBox(8);
    private final Label videoPlaceholder = new Label("Video player iOS-style");
    private final Button playPauseButton = new Button("Play");
    private final Button prevEpisodeButton = new Button("Prev");
    private final Button nextEpisodeButton = new Button("Next");

    private MediaPlayer mediaPlayer;
    private Anime currentAnime;
    private EpisodeInfo currentEpisode;
    private List<EpisodeInfo> currentEpisodes = new ArrayList<>();
    private EpisodeStream currentStream;
    private final List<String> streamCandidates = new ArrayList<>();
    private int activeStreamIndex = -1;
    private long detailRequestSerial = 0;
    private long episodeRequestSerial = 0;
    private boolean suppressEpisodeSelectorEvent = false;
    private int lastProgressTickBucket = -1;
    private String pendingResumeEpisodeSlug;
    private int pendingResumeSec = -1;

    public PlayerPage(AppState state,
                      AnimeRepository repository,
                      DownloadService downloadService,
                      CastService castService,
                      AnalyticsService analyticsService,
                      PlaybackProgressService progressService,
                      Consumer<Anime> onOpenAnime) {
        this.state = state;
        this.repository = repository;
        this.downloadService = downloadService;
        this.castService = castService;
        this.analyticsService = analyticsService;
        this.progressService = progressService;
        this.onOpenAnime = onOpenAnime;

        getStyleClass().add("page-player");

        setTop(buildHeader());
        setCenter(buildPlayerArea());
        setBottom(buildControls());
        setupTimelineControls();
        updateEpisodeNavigationState();
    }

    public void openAnime(Anime anime) {
        if (anime == null) {
            return;
        }

        saveProgressSnapshot("exit");

        long requestId = ++detailRequestSerial;
        episodeRequestSerial++;
        streamCandidates.clear();
        activeStreamIndex = -1;
        currentStream = null;
        currentEpisode = null;
        currentEpisodes = List.of();
        resetProgressTrackingState();
        episodeSelector.getItems().clear();
        updateEpisodeNavigationState();
        disposeMedia();

        currentAnime = anime;
        state.setCurrentPlayingAnime(anime);
        analyticsService.recordPlay(anime);

        titleLabel.setText(anime.getTitle());
        statusLabel.setText("Memuat detail anime...");
        descriptionArea.setText(anime.getDescription());

        repository.getAnimeDetail(anime.getSlug())
            .thenAccept(detail -> Platform.runLater(() -> {
                if (requestId != detailRequestSerial) {
                    return;
                }
                applyDetail(detail);
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    if (requestId == detailRequestSerial) {
                        statusLabel.setText("Gagal memuat detail");
                    }
                });
                return null;
            });
    }

    private VBox buildHeader() {
        VBox header = new VBox(8);
        header.setPadding(new Insets(18, 24, 10, 24));

        titleLabel.getStyleClass().add("player-title");
        statusLabel.getStyleClass().add("player-status");

        descriptionArea.setWrapText(true);
        descriptionArea.setEditable(false);
        descriptionArea.setPrefRowCount(3);
        descriptionArea.getStyleClass().add("description-box");

        Label relatedTitle = new Label("Related Anime");
        relatedTitle.getStyleClass().add("section-title");

        ScrollPane relatedScroll = new ScrollPane(relatedRow);
        relatedScroll.getStyleClass().add("transparent-scroll");
        relatedScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        relatedScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        relatedRow.setAlignment(Pos.CENTER_LEFT);

        header.getChildren().addAll(titleLabel, statusLabel, descriptionArea, relatedTitle, relatedScroll);
        return header;
    }

    private StackPane buildPlayerArea() {
        StackPane container = new StackPane();
        container.getStyleClass().add("video-container");
        container.setPadding(new Insets(0, 24, 12, 24));

        mediaView.setPreserveRatio(true);
        mediaView.fitWidthProperty().bind(container.widthProperty().subtract(48));
        mediaView.fitHeightProperty().bind(container.heightProperty().subtract(24));

        videoPlaceholder.getStyleClass().add("video-placeholder");

        container.getChildren().addAll(videoPlaceholder, mediaView);
        return container;
    }

    private VBox buildControls() {
        VBox controls = new VBox(12);
        controls.setPadding(new Insets(10, 24, 20, 24));
        controls.getStyleClass().add("player-controls-shell");

        timeline.setMin(0);
        timeline.setMax(100);
        timeline.setValue(0);

        playPauseButton.getStyleClass().add("pill-button");
        playPauseButton.setOnAction(event -> togglePlayPause());

        prevEpisodeButton.getStyleClass().add("pill-button");
        prevEpisodeButton.setOnAction(event -> navigateEpisode(false));

        nextEpisodeButton.getStyleClass().add("pill-button");
        nextEpisodeButton.setOnAction(event -> navigateEpisode(true));

        Button skipIntro = new Button("Skip Intro");
        skipIntro.getStyleClass().add("pill-button");
        skipIntro.setOnAction(event -> skipIntro());

        Button like = new Button("Like");
        like.getStyleClass().add("pill-button");
        like.setOnAction(event -> {
            if (currentAnime != null) {
                state.addToWatchlist(currentAnime);
                showInfo("Watchlist", currentAnime.getTitle() + " ditambahkan ke watchlist.");
            }
        });

        Button share = new Button("Share");
        share.getStyleClass().add("pill-button");
        share.setOnAction(event -> shareCurrent());

        Button download = new Button("Download");
        download.getStyleClass().add("pill-button");
        download.setOnAction(event -> downloadCurrent());

        Button cast = new Button("Cast");
        cast.getStyleClass().add("pill-button");
        cast.setOnAction(event -> castCurrent());

        episodeSelector.getStyleClass().add("chip-filter");
        episodeSelector.setOnAction(event -> {
            if (suppressEpisodeSelectorEvent) {
                return;
            }
            EpisodeInfo selected = episodeSelector.getValue();
            if (selected != null) {
                loadEpisode(selected);
            }
        });

        HBox row1 = new HBox(10, playPauseButton, prevEpisodeButton, nextEpisodeButton, skipIntro, episodeSelector, timeLabel);
        row1.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row2 = new HBox(10, like, share, download, cast, spacer);
        row2.setAlignment(Pos.CENTER_LEFT);

        downloadProgress.setVisible(false);

        controls.getChildren().addAll(timeline, row1, row2, downloadProgress);
        return controls;
    }

    private void applyDetail(AnimeDetail detail) {
        if (detail == null) {
            statusLabel.setText("Detail anime tidak tersedia");
            return;
        }

        if (detail.getAnime() != null) {
            currentAnime = detail.getAnime();
            titleLabel.setText(currentAnime.getTitle());
            descriptionArea.setText(currentAnime.getDescription());
        }

        currentEpisodes = detail.getEpisodeList() == null ? List.of() : detail.getEpisodeList().stream()
            .sorted(Comparator.comparingInt(EpisodeInfo::getEpisodeNumber).reversed())
            .toList();

        episodeSelector.getItems().setAll(currentEpisodes);
        if (!currentEpisodes.isEmpty()) {
            EpisodeInfo firstEpisode = currentEpisodes.get(0);
            selectEpisodeSilently(firstEpisode);
            loadEpisode(firstEpisode);
        } else {
            statusLabel.setText("Episode list kosong");
            currentEpisode = null;
            currentStream = null;
            streamCandidates.clear();
            activeStreamIndex = -1;
            resetProgressTrackingState();
            disposeMedia();
            updateEpisodeNavigationState();
        }

        renderRelated(detail.getRelatedAnime());
    }

    private void renderRelated(List<Anime> related) {
        relatedRow.getChildren().clear();
        if (related == null || related.isEmpty()) {
            return;
        }
        related.stream().limit(8).forEach(anime -> {
            Button pill = new Button(anime.getTitle());
            pill.getStyleClass().add("related-pill");
            pill.setOnAction(event -> onOpenAnime.accept(anime));
            relatedRow.getChildren().add(pill);
        });
    }

    private void loadEpisode(EpisodeInfo episode) {
        if (episode == null || episode.getSlug() == null || episode.getSlug().isBlank()) {
            statusLabel.setText("Episode tidak valid");
            return;
        }

        long requestId = ++episodeRequestSerial;
        currentEpisode = episode;
        currentStream = null;
        streamCandidates.clear();
        activeStreamIndex = -1;
        updateEpisodeNavigationState();

        if (episodeSelector.getValue() != episode) {
            episodeSelector.getSelectionModel().select(episode);
        }

        statusLabel.setText("Memuat stream: " + episode.getTitle());

        repository.getEpisodeStream(episode.getSlug())
            .thenAccept(stream -> Platform.runLater(() -> {
                if (requestId != episodeRequestSerial) {
                    return;
                }

                currentStream = stream;
                prepareStreamCandidates(stream);
                playCurrentCandidate();
                updateEpisodeNavigationState();

                if (stream != null && stream.getNextEpisodeSlug() != null) {
                    repository.prefetchNextEpisode(stream.getNextEpisodeSlug());
                }
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    if (requestId == episodeRequestSerial) {
                        statusLabel.setText("Gagal memuat stream episode");
                    }
                });
                return null;
            });
    }

    private void navigateEpisode(boolean next) {
        if (currentStream == null || currentEpisodes.isEmpty()) {
            return;
        }

        String targetSlug = next ? currentStream.getNextEpisodeSlug() : currentStream.getPrevEpisodeSlug();
        if (targetSlug == null || targetSlug.isBlank()) {
            return;
        }

        currentEpisodes.stream()
            .filter(ep -> targetSlug.equals(ep.getSlug()))
            .findFirst()
            .ifPresentOrElse(ep -> {
                selectEpisodeSilently(ep);
                loadEpisode(ep);
            }, () -> {
                EpisodeInfo synthetic = new EpisodeInfo(0, targetSlug, targetSlug, "", "");
                loadEpisode(synthetic);
            });
    }

    private void prepareStreamCandidates(EpisodeStream stream) {
        streamCandidates.clear();
        if (stream != null) {
            streamCandidates.addAll(normalizeUrls(stream.getStreamingUrls()));
            for (List<String> urls : stream.getDownloadUrls().values()) {
                streamCandidates.addAll(normalizeUrls(urls));
            }
        }

        streamCandidates.removeIf(url -> url == null || url.isBlank());
        if (streamCandidates.isEmpty()) {
            statusLabel.setText("Stream API kosong, fallback ke demo stream.");
            streamCandidates.add(DEMO_STREAM_URL);
        }
        activeStreamIndex = 0;
    }

    private List<String> normalizeUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            unique.add(url.trim());
        }
        return new ArrayList<>(unique);
    }

    private void playCurrentCandidate() {
        if (activeStreamIndex < 0 || activeStreamIndex >= streamCandidates.size()) {
            statusLabel.setText("Stream URL tidak tersedia");
            disposeMedia();
            return;
        }

        String url = streamCandidates.get(activeStreamIndex);
        String episodeTitle = currentEpisode != null ? currentEpisode.getTitle() : "Episode";
        String sourceHint = streamCandidates.size() > 1
            ? " (" + (activeStreamIndex + 1) + "/" + streamCandidates.size() + ")"
            : "";
        statusLabel.setText("Playing: " + episodeTitle + sourceHint);
        loadMedia(url);
    }

    private void loadMedia(String url) {
        disposeMedia();
        if (url == null || url.isBlank()) {
            statusLabel.setText("Stream URL tidak tersedia");
            return;
        }

        try {
            Media media = new Media(url);
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            videoPlaceholder.setVisible(false);

            mediaPlayer.setOnReady(() -> {
                Duration total = mediaPlayer.getTotalDuration();
                timeline.setMax(Math.max(1, total.toSeconds()));
                timeline.setValue(0);
                updateTime();
                analyticsService.startWatchSession();
                updatePlayPauseButtonLabel();
                mediaPlayer.play();
            });

            mediaPlayer.currentTimeProperty().addListener((obs, oldValue, newValue) -> {
                if (!timeline.isValueChanging()) {
                    timeline.setValue(newValue.toSeconds());
                }
                updateTime();
            });

            mediaPlayer.setOnPlaying(this::updatePlayPauseButtonLabel);
            mediaPlayer.setOnPaused(this::updatePlayPauseButtonLabel);
            mediaPlayer.setOnStopped(this::updatePlayPauseButtonLabel);
            mediaPlayer.setOnEndOfMedia(this::onMediaEnded);

            mediaPlayer.setOnError(() -> handleMediaError(mediaPlayer.getError() == null ? null : mediaPlayer.getError().getMessage()));
        } catch (Exception ex) {
            handleMediaError(ex.getMessage());
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            statusLabel.setText("Belum ada stream aktif");
            return;
        }
        MediaPlayer.Status status = mediaPlayer.getStatus();
        if (status == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
        updatePlayPauseButtonLabel();
    }

    private void skipIntro() {
        if (mediaPlayer == null) {
            statusLabel.setText("Belum ada stream aktif");
            return;
        }
        mediaPlayer.seek(mediaPlayer.getCurrentTime().add(Duration.seconds(90)));
    }

    private void shareCurrent() {
        if (currentAnime == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString("Nonton " + currentAnime.getTitle() + " di AniFlow");
        Clipboard.getSystemClipboard().setContent(content);
        showInfo("Share", "Info anime disalin ke clipboard.");
    }

    private void downloadCurrent() {
        if (currentStream == null) {
            showError("Download gagal", "Stream belum tersedia.");
            return;
        }

        String source = preferredSourceForActions();

        Window owner = getScene() != null ? getScene().getWindow() : null;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Simpan Episode Offline");
        chooser.setInitialFileName((currentAnime == null ? "episode" : currentAnime.getTitle()) + ".bin");
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }

        downloadProgress.setVisible(true);
        downloadProgress.setProgress(0);

        downloadService.downloadEpisode(source, Path.of(target.toURI()), progress ->
            Platform.runLater(() -> downloadProgress.setProgress(progress)))
            .thenAccept(path -> Platform.runLater(() -> {
                downloadProgress.setVisible(false);
                showInfo("Download", "Berhasil diunduh: " + path);
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    downloadProgress.setVisible(false);
                    showError("Download gagal", ex.getMessage());
                });
                return null;
            });
    }

    private void castCurrent() {
        if (currentStream == null) {
            return;
        }

        String source = preferredSourceForActions();

        boolean success = castService.castToChromecast(source);
        if (success) {
            showInfo("Cast", "Casting dimulai.");
        } else {
            showInfo("Cast", "Integrasi Chromecast masih opsional dan belum aktif di build ini.");
        }
    }

    private void updateTime() {
        if (mediaPlayer == null) {
            timeLabel.setText("00:00 / 00:00");
            return;
        }

        Duration current = mediaPlayer.getCurrentTime();
        Duration total = mediaPlayer.getTotalDuration();

        String currentText = formatDuration(current);
        String totalText = total != null && !total.isUnknown() ? formatDuration(total) : "--:--";
        timeLabel.setText(currentText + " / " + totalText);
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.lessThan(Duration.ZERO)) {
            return "--:--";
        }
        int seconds = (int) Math.floor(duration.toSeconds());
        int minutes = seconds / 60;
        int remain = seconds % 60;
        return String.format("%02d:%02d", minutes, remain);
    }

    private void disposeMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
            analyticsService.finishWatchSession();
        }
        mediaView.setMediaPlayer(null);
        videoPlaceholder.setVisible(true);
        timeline.setValue(0);
        updateTime();
        updatePlayPauseButtonLabel();
    }

    private void setupTimelineControls() {
        timeline.valueChangingProperty().addListener((obs, wasChanging, changing) -> {
            if (!changing) {
                seekFromTimeline();
            }
        });
        timeline.setOnMouseReleased(event -> seekFromTimeline());
    }

    private void seekFromTimeline() {
        if (mediaPlayer == null) {
            return;
        }
        mediaPlayer.seek(Duration.seconds(timeline.getValue()));
        updateTime();
    }

    private void updatePlayPauseButtonLabel() {
        if (mediaPlayer == null) {
            playPauseButton.setText("Play");
            return;
        }

        MediaPlayer.Status status = mediaPlayer.getStatus();
        playPauseButton.setText(status == MediaPlayer.Status.PLAYING ? "Pause" : "Play");
    }

    private void onMediaEnded() {
        if (currentStream != null
            && currentStream.getNextEpisodeSlug() != null
            && !currentStream.getNextEpisodeSlug().isBlank()) {
            navigateEpisode(true);
            return;
        }
        statusLabel.setText("Episode selesai");
        updatePlayPauseButtonLabel();
    }

    private void handleMediaError(String errorMessage) {
        String safeMessage = (errorMessage == null || errorMessage.isBlank()) ? "unknown error" : errorMessage;

        if (activeStreamIndex + 1 < streamCandidates.size()) {
            activeStreamIndex++;
            statusLabel.setText("Sumber stream gagal (" + safeMessage + "), mencoba sumber lain...");
            playCurrentCandidate();
            return;
        }

        if (!streamCandidates.contains(DEMO_STREAM_URL)) {
            streamCandidates.add(DEMO_STREAM_URL);
            activeStreamIndex = streamCandidates.size() - 1;
            statusLabel.setText("Semua sumber gagal (" + safeMessage + "), mencoba demo stream...");
            playCurrentCandidate();
            return;
        }

        disposeMedia();
        statusLabel.setText("Playback gagal: " + safeMessage);
    }

    private void updateEpisodeNavigationState() {
        boolean hasPrev = currentStream != null
            && currentStream.getPrevEpisodeSlug() != null
            && !currentStream.getPrevEpisodeSlug().isBlank();
        boolean hasNext = currentStream != null
            && currentStream.getNextEpisodeSlug() != null
            && !currentStream.getNextEpisodeSlug().isBlank();

        prevEpisodeButton.setDisable(!hasPrev);
        nextEpisodeButton.setDisable(!hasNext);
    }

    private String preferredSourceForActions() {
        if (activeStreamIndex >= 0 && activeStreamIndex < streamCandidates.size()) {
            return streamCandidates.get(activeStreamIndex);
        }

        if (currentStream != null) {
            String source = currentStream.firstPlayableUrl();
            if (source != null && !source.isBlank()) {
                return source;
            }
        }

        return DEMO_STREAM_URL;
    }

    private void showInfo(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.show();
    }

    private void showError(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.show();
    }
}
