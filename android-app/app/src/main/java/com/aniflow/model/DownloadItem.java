package com.aniflow.model;

public class DownloadItem {
    private final long id;
    private final String episodeSlug;
    private final String path;
    private final String resolusi;

    public DownloadItem(long id, String episodeSlug, String path, String resolusi) {
        this.id = id;
        this.episodeSlug = episodeSlug;
        this.path = path;
        this.resolusi = resolusi;
    }

    public long getId() {
        return id;
    }

    public String getEpisodeSlug() {
        return episodeSlug;
    }

    public String getPath() {
        return path;
    }

    public String getResolusi() {
        return resolusi;
    }
}
