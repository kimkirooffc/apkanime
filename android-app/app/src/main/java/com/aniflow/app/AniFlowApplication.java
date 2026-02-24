package com.aniflow.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class AniFlowApplication extends Application {

    public static final String CHANNEL_DOWNLOADS = "downloads";
    public static final String CHANNEL_NEW_EPISODES = "new_episodes";
    public static final String CHANNEL_APP_UPDATES = "app_updates";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedTheme(this);
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            
            // Downloads channel
            NotificationChannel downloadChannel = new NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            );
            downloadChannel.setDescription("Progress download episode");
            downloadChannel.setShowBadge(false);
            
            // New episodes channel
            NotificationChannel episodeChannel = new NotificationChannel(
                CHANNEL_NEW_EPISODES,
                "Episode Baru",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            episodeChannel.setDescription("Notifikasi episode baru dari watchlist");
            episodeChannel.setShowBadge(true);

            NotificationChannel updateChannel = new NotificationChannel(
                CHANNEL_APP_UPDATES,
                "Update Aplikasi",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            updateChannel.setDescription("Notifikasi versi terbaru AniFlow");
            updateChannel.setShowBadge(true);
            
            manager.createNotificationChannel(downloadChannel);
            manager.createNotificationChannel(episodeChannel);
            manager.createNotificationChannel(updateChannel);
        }
    }
}
