package com.aniflow.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.aniflow.db.dao.DownloadsDao;
import com.aniflow.db.dao.HistoryDao;
import com.aniflow.db.dao.WatchlistDao;
import com.aniflow.db.entity.DownloadEntity;
import com.aniflow.db.entity.HistoryEntity;
import com.aniflow.db.entity.WatchlistEntity;

@Database(
    entities = {
        WatchlistEntity.class,
        HistoryEntity.class,
        DownloadEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class AniFlowDatabase extends RoomDatabase {

    private static volatile AniFlowDatabase INSTANCE;

    public abstract HistoryDao historyDao();
    public abstract WatchlistDao watchlistDao();
    public abstract DownloadsDao downloadsDao();

    public static AniFlowDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AniFlowDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AniFlowDatabase.class,
                            "aniflow.db"
                        )
                        .fallbackToDestructiveMigration()
                        .allowMainThreadQueries()
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}
