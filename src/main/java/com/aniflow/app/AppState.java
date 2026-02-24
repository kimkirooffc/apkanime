package com.aniflow.app;

import com.aniflow.model.Anime;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class AppState {
    private final BooleanProperty darkMode = new SimpleBooleanProperty(false);
    private final BooleanProperty batteryEfficientMode = new SimpleBooleanProperty(false);
    private final BooleanProperty offlineMode = new SimpleBooleanProperty(false);
    private final BooleanProperty autoDownloadNewEpisode = new SimpleBooleanProperty(false);
    private final ObjectProperty<Page> currentPage = new SimpleObjectProperty<>(Page.HOME);
    private final ObjectProperty<Anime> currentPlayingAnime = new SimpleObjectProperty<>();
    private final ObservableList<Anime> watchlist = FXCollections.observableArrayList();
    private final ObservableList<Anime> history = FXCollections.observableArrayList();

    public BooleanProperty darkModeProperty() {
        return darkMode;
    }

    public boolean isDarkMode() {
        return darkMode.get();
    }

    public void setDarkMode(boolean enabled) {
        darkMode.set(enabled);
    }

    public BooleanProperty batteryEfficientModeProperty() {
        return batteryEfficientMode;
    }

    public boolean isBatteryEfficientMode() {
        return batteryEfficientMode.get();
    }

    public void setBatteryEfficientMode(boolean enabled) {
        batteryEfficientMode.set(enabled);
    }

    public BooleanProperty offlineModeProperty() {
        return offlineMode;
    }

    public boolean isOfflineMode() {
        return offlineMode.get();
    }

    public void setOfflineMode(boolean offline) {
        offlineMode.set(offline);
    }

    public BooleanProperty autoDownloadNewEpisodeProperty() {
        return autoDownloadNewEpisode;
    }

    public boolean isAutoDownloadNewEpisode() {
        return autoDownloadNewEpisode.get();
    }

    public void setAutoDownloadNewEpisode(boolean enabled) {
        autoDownloadNewEpisode.set(enabled);
    }

    public ObjectProperty<Page> currentPageProperty() {
        return currentPage;
    }

    public Page getCurrentPage() {
        return currentPage.get();
    }

    public void setCurrentPage(Page page) {
        currentPage.set(page);
    }

    public ObjectProperty<Anime> currentPlayingAnimeProperty() {
        return currentPlayingAnime;
    }

    public Anime getCurrentPlayingAnime() {
        return currentPlayingAnime.get();
    }

    public void setCurrentPlayingAnime(Anime anime) {
        currentPlayingAnime.set(anime);
        if (anime != null) {
            history.remove(anime);
            history.add(0, anime);
        }
    }

    public ObservableList<Anime> getWatchlist() {
        return watchlist;
    }

    public ObservableList<Anime> getHistory() {
        return history;
    }

    public void addToWatchlist(Anime anime) {
        if (anime != null && !watchlist.contains(anime)) {
            watchlist.add(anime);
        }
    }
}
