package com.aniflow.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class WatchHistoryItem {
    private int animeId;
    private String animeSlug;
    private String animeTitle;
    private String animeCover;
    private String animeBanner;
    private String animeDescription;
    private String animeStatus;
    private double animeScore;
    private String animeScoreText;
    private List<String> animeGenres;

    private String episodeSlug;
    private String episodeTitle;
    private int episodeNumber;

    private long progressMs;
    private long durationMs;
    private long updatedAt;

    public WatchHistoryItem() {
        this.animeGenres = new ArrayList<>();
    }

    public WatchHistoryItem(@NonNull Anime anime, @Nullable Episode episode, long progressMs, long durationMs, long updatedAt) {
        this();
        this.animeId = anime.getId();
        this.animeSlug = anime.getSlug();
        this.animeTitle = anime.getTitle();
        this.animeCover = anime.getCoverImage();
        this.animeBanner = anime.getBannerImage();
        this.animeDescription = anime.getDescription();
        this.animeStatus = anime.getStatus();
        this.animeScore = anime.getScore();
        this.animeScoreText = anime.getScoreText();
        this.animeGenres = anime.getGenres() != null ? new ArrayList<>(anime.getGenres()) : new ArrayList<>();

        if (episode != null) {
            this.episodeSlug = episode.getSlug();
            this.episodeTitle = episode.getTitle();
            this.episodeNumber = episode.getEpisodeNumber();
        }

        this.progressMs = progressMs;
        this.durationMs = durationMs;
        this.updatedAt = updatedAt;
    }

    public int getAnimeId() { return animeId; }
    public String getAnimeSlug() { return animeSlug; }
    public String getAnimeTitle() { return animeTitle; }
    public String getAnimeCover() { return animeCover; }
    public String getAnimeBanner() { return animeBanner; }
    public String getAnimeDescription() { return animeDescription; }
    public String getAnimeStatus() { return animeStatus; }
    public double getAnimeScore() { return animeScore; }
    public String getAnimeScoreText() { return animeScoreText; }
    public List<String> getAnimeGenres() { return animeGenres != null ? animeGenres : new ArrayList<>(); }
    public String getEpisodeSlug() { return episodeSlug; }
    public String getEpisodeTitle() { return episodeTitle; }
    public int getEpisodeNumber() { return episodeNumber; }
    public long getProgressMs() { return progressMs; }
    public long getDurationMs() { return durationMs; }
    public long getUpdatedAt() { return updatedAt; }

    public void setProgressMs(long progressMs) { this.progressMs = progressMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public void setEpisodeSlug(String episodeSlug) { this.episodeSlug = episodeSlug; }
    public void setEpisodeTitle(String episodeTitle) { this.episodeTitle = episodeTitle; }
    public void setEpisodeNumber(int episodeNumber) { this.episodeNumber = episodeNumber; }

    public int getProgressPercent() {
        if (durationMs > 0) {
            int value = (int) ((progressMs * 100L) / durationMs);
            if (value < 0) return 0;
            return Math.min(value, 100);
        }
        if (progressMs <= 0) return 0;
        int rough = (int) (progressMs / 1000L) * 2;
        return Math.max(1, Math.min(rough, 95));
    }

    public Anime toAnime() {
        String episodeLabel = episodeNumber > 0 ? "Episode " + episodeNumber : "Episode terbaru";
        return new Anime(
            animeId,
            animeSlug,
            animeTitle,
            animeCover,
            animeBanner,
            animeDescription,
            0,
            animeStatus,
            animeScore,
            animeScoreText,
            getAnimeGenres(),
            episodeLabel,
            ""
        );
    }
}
