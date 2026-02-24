package com.aniflow.app;

import com.aniflow.model.Anime;
import com.aniflow.service.AnalyticsService;
import com.aniflow.service.AnimeRepository;
import com.aniflow.service.CastService;
import com.aniflow.service.DownloadService;
import com.aniflow.service.LocalLibraryService;
import com.aniflow.service.NotificationService;
import com.aniflow.service.OtakudesuApiService;
import com.aniflow.service.PlaybackProgressService;
import com.aniflow.service.SettingsService;
import com.aniflow.ui.MainLayout;
import com.aniflow.ui.theme.ThemeManager;
import com.aniflow.util.AppIconGenerator;
import com.aniflow.util.DesktopNotifier;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;

public class AniFlowApp extends Application {
    private AnimeRepository repository;
    private DownloadService downloadService;
    private NotificationService notificationService;
    private MainLayout mainLayout;
    private boolean shutdownTriggered;

    @Override
    public void start(Stage stage) {
        AppState state = new AppState();

        SettingsService settingsService = new SettingsService();
        settingsService.load(state);
        settingsService.bind(state);

        OtakudesuApiService apiService = new OtakudesuApiService();
        repository = new AnimeRepository(apiService, state);
        downloadService = new DownloadService();
        CastService castService = new CastService();
        AnalyticsService analyticsService = new AnalyticsService();
        PlaybackProgressService progressService = new PlaybackProgressService();

        LocalLibraryService localLibraryService = new LocalLibraryService();
        List<Anime> history = localLibraryService.loadHistory();
        List<Anime> watchlist = localLibraryService.loadWatchlist();
        state.getHistory().setAll(history);
        state.getWatchlist().setAll(watchlist);

        state.getHistory().addListener((ListChangeListener<Anime>) change ->
            localLibraryService.saveHistory(state.getHistory()));
        state.getWatchlist().addListener((ListChangeListener<Anime>) change ->
            localLibraryService.saveWatchlist(state.getWatchlist()));

        DesktopNotifier notifier = new DesktopNotifier();
        state.offlineModeProperty().addListener((obs, oldValue, offline) -> {
            if (offline) {
                notifier.notify("AniFlow", "Koneksi terputus. Menampilkan data dari cache.");
            }
        });
        notificationService = new NotificationService(state, repository, downloadService, notifier);
        notificationService.seedWatchlist(state.getWatchlist());
        notificationService.start();

        mainLayout = new MainLayout(state, repository, downloadService, castService, analyticsService, progressService);
        ThemeManager.bind(mainLayout, state);

        Scene scene = new Scene(mainLayout, 1366, 900);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("AniFlow - Anime Streaming");
        stage.getIcons().add(AppIconGenerator.createAppIcon());
        stage.setMinWidth(1080);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(event -> shutdown());
    }

    @Override
    public void stop() {
        shutdown();
    }

    private synchronized void shutdown() {
        if (shutdownTriggered) {
            return;
        }
        shutdownTriggered = true;

        if (mainLayout != null) {
            mainLayout.shutdown();
            mainLayout = null;
        }
        if (notificationService != null) {
            notificationService.stop();
        }
        if (downloadService != null) {
            downloadService.shutdown();
        }
        if (repository != null) {
            repository.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
