package com.aniflow.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateChecker {

    private static final String PREFS = "update_checker_prefs";
    private static final String KEY_LAST_CHECK_AT = "last_check_at";
    private static final String KEY_LAST_PROMPTED_CODE = "last_prompted_code";
    private static final long CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateChecker() {
    }

    public interface Callback {
        void onUpdateAvailable(UpdateInfo info);
        void onNoUpdate();
        void onFailure(Throwable error);
    }

    public static final class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final boolean force;
        public final String title;
        public final String message;
        public final String apkUrl;

        UpdateInfo(int versionCode,
                   String versionName,
                   boolean force,
                   String title,
                   String message,
                   String apkUrl) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.force = force;
            this.title = title;
            this.message = message;
            this.apkUrl = apkUrl;
        }
    }

    public static void check(Context context, boolean force, Callback callback) {
        if (context == null || callback == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!force && now - prefs.getLong(KEY_LAST_CHECK_AT, 0L) < CHECK_INTERVAL_MS) {
            callback.onNoUpdate();
            return;
        }
        prefs.edit().putLong(KEY_LAST_CHECK_AT, now).apply();

        String endpoint = BuildConfig.UPDATE_CONFIG_URL == null ? "" : BuildConfig.UPDATE_CONFIG_URL.trim();
        if (endpoint.isEmpty() || endpoint.contains("USERNAME/REPO")) {
            callback.onFailure(new IllegalStateException("UPDATE_CONFIG_URL belum diset"));
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("HTTP " + status);
                }

                String body = readBody(connection);
                JSONObject json = new JSONObject(body);
                int remoteCode = json.optInt("versionCode", 0);
                if (remoteCode <= BuildConfig.VERSION_CODE) {
                    postNoUpdate(callback);
                    return;
                }

                UpdateInfo info = new UpdateInfo(
                    remoteCode,
                    safe(json.optString("versionName"), "latest"),
                    json.optBoolean("force", false),
                    safe(json.optString("title"), "Update tersedia"),
                    safe(json.optString("message"), "Versi baru AniFlow sudah tersedia."),
                    safe(json.optString("apkUrl"), "")
                );
                MAIN.post(() -> callback.onUpdateAvailable(info));
            } catch (Throwable error) {
                MAIN.post(() -> callback.onFailure(error));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public static int lastPromptedVersion(Context context) {
        if (context == null) {
            return 0;
        }
        SharedPreferences prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_LAST_PROMPTED_CODE, 0);
    }

    public static void markPromptedVersion(Context context, int versionCode) {
        if (context == null || versionCode <= 0) {
            return;
        }
        context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_PROMPTED_CODE, versionCode)
            .apply();
    }

    private static void postNoUpdate(Callback callback) {
        MAIN.post(callback::onNoUpdate);
    }

    private static String readBody(HttpURLConnection connection) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
             BufferedReader reader = new BufferedReader(new InputStreamReader(bis, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String safe(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
