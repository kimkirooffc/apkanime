package com.aniflow.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Anime {
    private final int id;
    private final String slug;
    private final String title;
    private final String coverImage;
    private final String bannerImage;
    private final String description;
    private final int episodes;
    private final String status;
    private final double score;
    private final String scoreText;
    private final List<String> genres;
    private final String episodeLabel;
    private final String releaseInfo;
    private final int imageWidth;
    private final int imageHeight;
    private final String studio;
    private final String producer;
    private final String duration;
    private final String releaseYear;
    private final int totalEpisodes;
    private final String trailerUrl;
    private final String sourceUrl;

    public Anime(int id, String slug, String title, String coverImage, String bannerImage,
                 String description, int episodes, String status, double score, String scoreText,
                 List<String> genres, String episodeLabel, String releaseInfo) {
        this(id, slug, title, coverImage, bannerImage, description, episodes, status, score, scoreText,
            genres, episodeLabel, releaseInfo, 0, 0,
            "", "", "", "", 0, "", "");
    }

    public Anime(int id, String slug, String title, String coverImage, String bannerImage,
                 String description, int episodes, String status, double score, String scoreText,
                 List<String> genres, String episodeLabel, String releaseInfo, int imageWidth, int imageHeight) {
        this(id, slug, title, coverImage, bannerImage, description, episodes, status, score, scoreText,
            genres, episodeLabel, releaseInfo, imageWidth, imageHeight,
            "", "", "", "", 0, "", "");
    }

    public Anime(int id, String slug, String title, String coverImage, String bannerImage,
                 String description, int episodes, String status, double score, String scoreText,
                 List<String> genres, String episodeLabel, String releaseInfo, int imageWidth, int imageHeight,
                 String studio, String producer, String duration, String releaseYear, int totalEpisodes,
                 String trailerUrl, String sourceUrl) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.coverImage = coverImage;
        this.bannerImage = bannerImage;
        this.description = description;
        this.episodes = episodes;
        this.status = status;
        this.score = score;
        this.scoreText = scoreText;
        this.genres = genres != null ? new ArrayList<>(genres) : new ArrayList<>();
        this.episodeLabel = episodeLabel;
        this.releaseInfo = releaseInfo;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.studio = studio;
        this.producer = producer;
        this.duration = duration;
        this.releaseYear = releaseYear;
        this.totalEpisodes = totalEpisodes;
        this.trailerUrl = trailerUrl;
        this.sourceUrl = sourceUrl;
    }

    public int getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getCoverImage() { return coverImage; }
    public String getBannerImage() { return bannerImage; }
    public String getDescription() { return description; }
    public int getEpisodes() { return episodes; }
    public String getStatus() { return status; }
    public double getScore() { return score; }
    public String getScoreText() { return scoreText; }
    public List<String> getGenres() { return new ArrayList<>(genres); }
    public String getEpisodeLabel() { return episodeLabel; }
    public String getReleaseInfo() { return releaseInfo; }
    public int getImageWidth() { return imageWidth; }
    public int getImageHeight() { return imageHeight; }
    public String getStudio() { return studio; }
    public String getProducer() { return producer; }
    public String getDuration() { return duration; }
    public String getReleaseYear() { return releaseYear; }
    public int getTotalEpisodes() { return totalEpisodes; }
    public String getTrailerUrl() { return trailerUrl; }
    public String getSourceUrl() { return sourceUrl; }

    public int getDisplayEpisodeCount() {
        if (episodes > 0) {
            return episodes;
        }
        return Math.max(totalEpisodes, 0);
    }

    public boolean hasTrailer() {
        return trailerUrl != null && !trailerUrl.trim().isEmpty();
    }

    public float getAspectRatio() {
        if (imageWidth > 0 && imageHeight > 0) {
            return (float) imageWidth / (float) imageHeight;
        }
        return 0.75f;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Anime)) return false;
        Anime anime = (Anime) o;
        return Objects.equals(slug, anime.slug);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug);
    }
}
