package com.aniflow.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.aniflow.db.entity.HistoryEntity;

import java.util.List;

@Dao
public interface HistoryDao {

    @Insert
    long insert(HistoryEntity entity);

    @Update
    int update(HistoryEntity entity);

    @Delete
    int delete(HistoryEntity entity);

    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    HistoryEntity getById(long id);

    @Query("SELECT * FROM history WHERE anime_slug = :animeSlug AND episode_slug = :episodeSlug LIMIT 1")
    HistoryEntity getByAnimeAndEpisode(String animeSlug, String episodeSlug);

    @Query("SELECT * FROM history WHERE anime_slug = :animeSlug ORDER BY timestamp DESC LIMIT 1")
    HistoryEntity getLatestByAnime(String animeSlug);

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    List<HistoryEntity> getAllOrderByLatest();

    @Query("DELETE FROM history WHERE id = :id")
    int deleteById(long id);

    @Query("DELETE FROM history")
    void clear();

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT :maxRows)")
    void trimToMaxRows(int maxRows);
}
