package com.aniflow.service;

import com.aniflow.app.AppState;

import java.util.prefs.Preferences;

public class SettingsService {
    private static final String PREF_DARK_MODE = "dark_mode";
    private static final String PREF_BATTERY_MODE = "battery_mode";
    private static final String PREF_AUTO_DOWNLOAD = "auto_download";

    private final Preferences preferences = Preferences.userRoot().node("com.aniflow");

    public void load(AppState state) {
        state.setDarkMode(preferences.getBoolean(PREF_DARK_MODE, false));
        state.setBatteryEfficientMode(preferences.getBoolean(PREF_BATTERY_MODE, false));
        state.setAutoDownloadNewEpisode(preferences.getBoolean(PREF_AUTO_DOWNLOAD, false));
    }

    public void bind(AppState state) {
        state.darkModeProperty().addListener((obs, oldValue, newValue) ->
            preferences.putBoolean(PREF_DARK_MODE, newValue));

        state.batteryEfficientModeProperty().addListener((obs, oldValue, newValue) ->
            preferences.putBoolean(PREF_BATTERY_MODE, newValue));

        state.autoDownloadNewEpisodeProperty().addListener((obs, oldValue, newValue) ->
            preferences.putBoolean(PREF_AUTO_DOWNLOAD, newValue));
    }
}
