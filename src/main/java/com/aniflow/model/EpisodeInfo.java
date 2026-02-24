package com.aniflow.model;

public class EpisodeInfo {
    private final int episodeNumber;
    private final String title;
    private final String slug;
    private final String releaseDate;
    private final String streamEndpoint;

    public EpisodeInfo(int episodeNumber, String title, String slug, String releaseDate, String streamEndpoint) {
        this.episodeNumber = episodeNumber;
        this.title = title;
        this.slug = slug;
        this.releaseDate = releaseDate;
        this.streamEndpoint = streamEndpoint;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public String getStreamEndpoint() {
        return streamEndpoint;
    }

    @Override
    public String toString() {
        if (episodeNumber > 0) {
            return "Episode " + episodeNumber;
        }
        return title;
    }
}
