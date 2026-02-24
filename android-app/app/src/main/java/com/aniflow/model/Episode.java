package com.aniflow.model;

public class Episode {
    private final int episodeNumber;
    private final String title;
    private final String slug;
    private final String releaseDate;
    private final String streamEndpoint;
    private final String thumbnail;
    private final String duration;
    private final boolean released;

    public Episode(int episodeNumber, String title, String slug, String releaseDate, String streamEndpoint) {
        this(episodeNumber, title, slug, releaseDate, streamEndpoint, "", "", true);
    }

    public Episode(int episodeNumber, String title, String slug, String releaseDate,
                   String streamEndpoint, String thumbnail, String duration, boolean released) {
        this.episodeNumber = episodeNumber;
        this.title = title;
        this.slug = slug;
        this.releaseDate = releaseDate;
        this.streamEndpoint = streamEndpoint;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.released = released;
    }

    public int getEpisodeNumber() { return episodeNumber; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getReleaseDate() { return releaseDate; }
    public String getStreamEndpoint() { return streamEndpoint; }
    public String getThumbnail() { return thumbnail; }
    public String getDuration() { return duration; }
    public boolean isReleased() { return released; }

    @Override
    public String toString() {
        return "Episode " + episodeNumber + (title != null && !title.isEmpty() ? ": " + title : "");
    }
}
