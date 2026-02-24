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
    private final String streamUrl;
    private final Integer nextAiringEpisode;
    private final String detailEndpoint;
    private final String episodeLabel;
    private final String releaseInfo;
    private final String studio;

    public Anime(int id,
                 String title,
                 String coverImage,
                 String bannerImage,
                 String description,
                 int episodes,
                 String status,
                 double score,
                 List<String> genres,
                 String streamUrl,
                 Integer nextAiringEpisode) {
        this(
            id,
            String.valueOf(id),
            title,
            coverImage,
            bannerImage,
            description,
            episodes,
            status,
            score,
            score <= 0 ? "-" : String.format("%.2f", score),
            genres,
            streamUrl,
            nextAiringEpisode,
            "",
            "",
            "",
            ""
        );
    }

    public Anime(int id,
                 String slug,
                 String title,
                 String coverImage,
                 String bannerImage,
                 String description,
                 int episodes,
                 String status,
                 double score,
                 String scoreText,
                 List<String> genres,
                 String streamUrl,
                 Integer nextAiringEpisode,
                 String detailEndpoint,
                 String episodeLabel,
                 String releaseInfo,
                 String studio) {
        this.id = id;
        this.slug = slug == null || slug.isBlank() ? String.valueOf(id) : slug;
        this.title = title;
        this.coverImage = coverImage;
        this.bannerImage = bannerImage;
        this.description = description;
        this.episodes = episodes;
        this.status = status;
        this.score = score;
        this.scoreText = scoreText;
        this.genres = genres == null ? new ArrayList<>() : new ArrayList<>(genres);
        this.streamUrl = streamUrl;
        this.nextAiringEpisode = nextAiringEpisode;
        this.detailEndpoint = detailEndpoint;
        this.episodeLabel = episodeLabel;
        this.releaseInfo = releaseInfo;
        this.studio = studio;
    }

    public int getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public String getBannerImage() {
        return bannerImage;
    }

    public String getDescription() {
        return description;
    }

    public int getEpisodes() {
        return episodes;
    }

    public String getStatus() {
        return status;
    }

    public double getScore() {
        return score;
    }

    public String getScoreText() {
        return scoreText;
    }

    public List<String> getGenres() {
        return new ArrayList<>(genres);
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public Integer getNextAiringEpisode() {
        return nextAiringEpisode;
    }

    public String getDetailEndpoint() {
        return detailEndpoint;
    }

    public String getEpisodeLabel() {
        return episodeLabel;
    }

    public String getReleaseInfo() {
        return releaseInfo;
    }

    public String getStudio() {
        return studio;
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Anime anime)) {
            return false;
        }
        return Objects.equals(slug, anime.slug);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug);
    }
}
