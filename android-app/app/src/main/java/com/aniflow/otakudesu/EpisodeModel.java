package com.aniflow.otakudesu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EpisodeModel {
    private int episodeNumber;
    private String title;
    private String slug;
    private String releaseDate;
    private List<String> streamUrls;
    private Map<String, List<String>> downloadUrls;
    private String previousEpisodeSlug;
    private String nextEpisodeSlug;
    private String animeSlug;

    public EpisodeModel() {
        this.episodeNumber = 0;
        this.title = "";
        this.slug = "";
        this.releaseDate = "";
        this.streamUrls = new ArrayList<>();
        this.downloadUrls = new LinkedHashMap<>();
        this.previousEpisodeSlug = null;
        this.nextEpisodeSlug = null;
        this.animeSlug = null;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.episodeNumber = Math.max(episodeNumber, 0);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug == null ? "" : slug;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate == null ? "" : releaseDate;
    }

    public List<String> getStreamUrls() {
        return new ArrayList<>(streamUrls);
    }

    public void setStreamUrls(List<String> streamUrls) {
        if (streamUrls == null) {
            this.streamUrls = new ArrayList<>();
            return;
        }
        this.streamUrls = new ArrayList<>(streamUrls);
    }

    public Map<String, List<String>> getDownloadUrls() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : downloadUrls.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    public void setDownloadUrls(Map<String, List<String>> downloadUrls) {
        if (downloadUrls == null) {
            this.downloadUrls = new LinkedHashMap<>();
            return;
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : downloadUrls.entrySet()) {
            if (entry.getValue() != null) {
                copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        this.downloadUrls = copy;
    }

    public String getPreviousEpisodeSlug() {
        return previousEpisodeSlug;
    }

    public void setPreviousEpisodeSlug(String previousEpisodeSlug) {
        this.previousEpisodeSlug = previousEpisodeSlug;
    }

    public String getNextEpisodeSlug() {
        return nextEpisodeSlug;
    }

    public void setNextEpisodeSlug(String nextEpisodeSlug) {
        this.nextEpisodeSlug = nextEpisodeSlug;
    }

    public String getAnimeSlug() {
        return animeSlug;
    }

    public void setAnimeSlug(String animeSlug) {
        this.animeSlug = animeSlug;
    }

    public List<String> getStreamUrlsReadOnly() {
        return Collections.unmodifiableList(streamUrls);
    }
}
