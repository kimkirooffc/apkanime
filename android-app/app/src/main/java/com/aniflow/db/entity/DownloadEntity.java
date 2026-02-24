package com.aniflow.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "downloads",
    indices = {
        @Index(value = {"episode_slug", "resolusi"}, unique = true),
        @Index(value = {"episode_slug"})
    }
)
public class DownloadEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "episode_slug")
    private String episodeSlug = "";

    @NonNull
    @ColumnInfo(name = "path")
    private String path = "";

    @NonNull
    @ColumnInfo(name = "resolusi")
    private String resolusi = "";

    public DownloadEntity() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getEpisodeSlug() {
        return episodeSlug;
    }

    public void setEpisodeSlug(@NonNull String episodeSlug) {
        this.episodeSlug = episodeSlug;
    }

    @NonNull
    public String getPath() {
        return path;
    }

    public void setPath(@NonNull String path) {
        this.path = path;
    }

    @NonNull
    public String getResolusi() {
        return resolusi;
    }

    public void setResolusi(@NonNull String resolusi) {
        this.resolusi = resolusi;
    }
}
