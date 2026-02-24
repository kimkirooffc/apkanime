package com.aniflow.service;

import com.aniflow.model.Anime;

import java.util.prefs.Preferences;

public class AnalyticsService {
    private final Preferences prefs = Preferences.userRoot().node("com.aniflow.analytics");
    private long watchSessionStart = -1;

    public void recordPlay(Anime anime) {
        if (anime == null) {
            return;
        }
        String key = "watch_count." + anime.getSlug();
        int current = prefs.getInt(key, 0);
        prefs.putInt(key, current + 1);
    }

    public void startWatchSession() {
        watchSessionStart = System.currentTimeMillis();
    }

    public void finishWatchSession() {
        if (watchSessionStart <= 0) {
            return;
        }
        long elapsedMs = System.currentTimeMillis() - watchSessionStart;
        long current = prefs.getLong("watch_time_ms", 0);
        prefs.putLong("watch_time_ms", current + Math.max(elapsedMs, 0));
        watchSessionStart = -1;
    }

    public void setPreferredResolution(String resolution) {
        if (resolution != null && !resolution.isBlank()) {
            prefs.put("preferred_resolution", resolution);
        }
    }
}
