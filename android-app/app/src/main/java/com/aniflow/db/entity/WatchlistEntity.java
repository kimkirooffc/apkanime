package com.aniflow.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "watchlist",
    indices = {
        @Index(value = {"anime_slug"}, unique = true)
    }
)
public class WatchlistEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    @NonNull
    @ColumnInfo(name = "anime_slug")
    private String animeSlug = "";

    @NonNull
    @ColumnInfo(name = "judul")
    private String judul = "";

    @NonNull
    @ColumnInfo(name = "thumbnail")
    private String thumbnail = "";

    public WatchlistEntity() {
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
    public String getJudul() {
        return judul;
    }

    public void setJudul(@NonNull String judul) {
        this.judul = judul;
    }

    @NonNull
    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(@NonNull String thumbnail) {
        this.thumbnail = thumbnail;
    }
}
