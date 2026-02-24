package com.aniflow.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.aniflow.db.entity.DownloadEntity;

import java.util.List;

@Dao
public interface DownloadsDao {

    @Insert
    long insert(DownloadEntity entity);

    @Update
    int update(DownloadEntity entity);

    @Delete
    int delete(DownloadEntity entity);

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    DownloadEntity getById(long id);

    @Query("SELECT * FROM downloads WHERE episode_slug = :episodeSlug ORDER BY id DESC")
    List<DownloadEntity> getByEpisodeSlug(String episodeSlug);

    @Query("SELECT * FROM downloads WHERE episode_slug = :episodeSlug AND resolusi = :resolusi LIMIT 1")
    DownloadEntity getByEpisodeAndResolution(String episodeSlug, String resolusi);

    @Query("SELECT * FROM downloads ORDER BY id DESC")
    List<DownloadEntity> getAllOrderByLatest();

    @Query("SELECT COUNT(1) FROM downloads")
    int count();

    @Query("DELETE FROM downloads WHERE id = :id")
    int deleteById(long id);
}
