package com.aniflow.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.aniflow.db.entity.WatchlistEntity;

import java.util.List;

@Dao
public interface WatchlistDao {

    @Insert
    long insert(WatchlistEntity entity);

    @Update
    int update(WatchlistEntity entity);

    @Delete
    int delete(WatchlistEntity entity);

    @Query("SELECT * FROM watchlist WHERE id = :id LIMIT 1")
    WatchlistEntity getById(long id);

    @Query("SELECT * FROM watchlist WHERE anime_slug = :animeSlug LIMIT 1")
    WatchlistEntity getByAnimeSlug(String animeSlug);

    @Query("SELECT * FROM watchlist ORDER BY id DESC")
    List<WatchlistEntity> getAllOrderByLatest();

    @Query("SELECT COUNT(1) FROM watchlist")
    int count();

    @Query("DELETE FROM watchlist WHERE id = :id")
    int deleteById(long id);

    @Query("DELETE FROM watchlist WHERE anime_slug = :animeSlug")
    int deleteByAnimeSlug(String animeSlug);

    @Query("DELETE FROM watchlist WHERE id NOT IN (SELECT id FROM watchlist ORDER BY id DESC LIMIT :maxRows)")
    void trimToMaxRows(int maxRows);
}
