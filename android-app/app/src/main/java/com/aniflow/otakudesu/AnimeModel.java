package com.aniflow.otakudesu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnimeModel {
    private int id;
    private String slug;
    private String title;
    private String thumbnail;
    private String synopsis;
    private String status;
    private String releaseDate;
    private String episodeLabel;
    private int totalEpisodes;
    private double score;
    private String studio;
    private String producer;
    private String duration;
    private List<String> genres;
    private List<EpisodeModel> episodes;

    public AnimeModel() {
        this.id = 0;
        this.slug = "";
        this.title = "";
        this.thumbnail = "";
        this.synopsis = "";
        this.status = "Unknown";
        this.releaseDate = "";
        this.episodeLabel = "";
        this.totalEpisodes = 0;
        this.score = 0.0;
        this.studio = "";
        this.producer = "";
        this.duration = "";
        this.genres = new ArrayList<>();
        this.episodes = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug == null ? "" : slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail == null ? "" : thumbnail;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public void setSynopsis(String synopsis) {
        this.synopsis = synopsis == null ? "" : synopsis;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "Unknown" : status;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate == null ? "" : releaseDate;
    }

    public String getEpisodeLabel() {
        return episodeLabel;
    }

    public void setEpisodeLabel(String episodeLabel) {
        this.episodeLabel = episodeLabel == null ? "" : episodeLabel;
    }

    public int getTotalEpisodes() {
        return totalEpisodes;
    }

    public void setTotalEpisodes(int totalEpisodes) {
        this.totalEpisodes = Math.max(totalEpisodes, 0);
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score < 0 ? 0 : score;
    }

    public String getStudio() {
        return studio;
    }

    public void setStudio(String studio) {
        this.studio = studio == null ? "" : studio;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer == null ? "" : producer;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration == null ? "" : duration;
    }

    public List<String> getGenres() {
        return new ArrayList<>(genres);
    }

    public void setGenres(List<String> genres) {
        if (genres == null) {
            this.genres = new ArrayList<>();
            return;
        }
        this.genres = new ArrayList<>(genres);
    }

    public List<EpisodeModel> getEpisodes() {
        return new ArrayList<>(episodes);
    }

    public void setEpisodes(List<EpisodeModel> episodes) {
        if (episodes == null) {
            this.episodes = new ArrayList<>();
            return;
        }
        this.episodes = new ArrayList<>(episodes);
    }

    public List<EpisodeModel> getEpisodesReadOnly() {
        return Collections.unmodifiableList(episodes);
    }
}
