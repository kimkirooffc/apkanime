package com.aniflow.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "history",
    indices = {
        @Index(value = {"anime_slug", "episode_slug"}, unique = true),
        @Index(value = {"anime_slug"}),
        @Index(value = {"timestamp"})
    }
)
public class HistoryEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "anime_slug")
    private String animeSlug = "";

    @NonNull
    @ColumnInfo(name = "episode_slug")
    private String episodeSlug = "";

    @ColumnInfo(name = "progress")
    private long progress;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    public HistoryEntity() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getAnimeSlug() {
        return animeSlug;
    }

    public void setAnimeSlug(@NonNull String animeSlug) {
        this.animeSlug = animeSlug;
    }

    @NonNull
    public String getEpisodeSlug() {
        return episodeSlug;
    }

    public void setEpisodeSlug(@NonNull String episodeSlug) {
        this.episodeSlug = episodeSlug;
    }

    public long getProgress() {
        return progress;
    }

    public void setProgress(long progress) {
        this.progress = progress;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
