package com.aniflow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EpisodeStream {
    private final String title;
    private final String episodeSlug;
    private final List<String> streamingUrls;
    private final Map<String, List<String>> downloadUrls;
    private final String prevEpisodeSlug;
    private final String nextEpisodeSlug;
    private final String animeSlug;

    public EpisodeStream(String title,
                         String episodeSlug,
                         List<String> streamingUrls,
                         Map<String, List<String>> downloadUrls,
                         String prevEpisodeSlug,
                         String nextEpisodeSlug,
                         String animeSlug) {
        this.title = title;
        this.episodeSlug = episodeSlug;
        this.streamingUrls = streamingUrls == null ? new ArrayList<>() : new ArrayList<>(streamingUrls);
        this.downloadUrls = downloadUrls == null ? Collections.emptyMap() : downloadUrls;
        this.prevEpisodeSlug = prevEpisodeSlug;
        this.nextEpisodeSlug = nextEpisodeSlug;
        this.animeSlug = animeSlug;
    }

    public String getTitle() {
        return title;
    }

    public String getEpisodeSlug() {
        return episodeSlug;
    }

    public List<String> getStreamingUrls() {
        return new ArrayList<>(streamingUrls);
    }

    public Map<String, List<String>> getDownloadUrls() {
        return downloadUrls;
    }

    public String getPrevEpisodeSlug() {
        return prevEpisodeSlug;
    }

    public String getNextEpisodeSlug() {
        return nextEpisodeSlug;
    }

    public String getAnimeSlug() {
        return animeSlug;
    }

    public String firstPlayableUrl() {
        if (!streamingUrls.isEmpty()) {
            return streamingUrls.get(0);
        }

        for (List<String> urls : downloadUrls.values()) {
            if (!urls.isEmpty()) {
                return urls.get(0);
            }
        }
        return null;
    }
}
