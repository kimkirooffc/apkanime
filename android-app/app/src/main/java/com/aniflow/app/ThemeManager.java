package com.aniflow.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Locale;

public final class ThemeManager {

    public static final String PREFS_NAME = "aniflow_prefs";
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String KEY_ACCENT_COLOR = "accent_color";
    public static final String KEY_LEGACY_DARK_MODE = "dark_mode";

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_AMOLED = "amoled";

    public static final String ACCENT_BLUE = "blue";
    public static final String ACCENT_PURPLE = "purple";
    public static final String ACCENT_GREEN = "green";
    public static final String ACCENT_RED = "red";
    public static final String ACCENT_ORANGE = "orange";

    private ThemeManager() {
    }

    public static void applySavedTheme(@NonNull Context context) {
        AppCompatDelegate.setDefaultNightMode(resolveNightMode(getThemeMode(context)));
    }

    @NonNull
    public static String getThemeMode(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        String mode = normalizeThemeMode(prefs.getString(KEY_THEME_MODE, null));
        if (mode != null) {
            return mode;
        }

        boolean legacyDark = prefs.getBoolean(KEY_LEGACY_DARK_MODE, false);
        return legacyDark ? MODE_DARK : MODE_SYSTEM;
    }

    public static void setThemeMode(@NonNull Context context, @Nullable String mode) {
        String normalized = normalizeThemeMode(mode);
        if (normalized == null) {
            normalized = MODE_SYSTEM;
        }
        prefs(context).edit()
            .putString(KEY_THEME_MODE, normalized)
            .putBoolean(KEY_LEGACY_DARK_MODE, MODE_DARK.equals(normalized) || MODE_AMOLED.equals(normalized))
            .apply();
    }

    @NonNull
    public static String getAccentColor(@NonNull Context context) {
        String accent = normalizeAccent(prefs(context).getString(KEY_ACCENT_COLOR, null));
        return accent == null ? ACCENT_PURPLE : accent;
    }

    public static void setAccentColor(@NonNull Context context, @Nullable String accent) {
        String normalized = normalizeAccent(accent);
        if (normalized == null) {
            normalized = ACCENT_PURPLE;
        }
        prefs(context).edit().putString(KEY_ACCENT_COLOR, normalized).apply();
    }

    public static boolean isAmoledMode(@NonNull Context context) {
        return MODE_AMOLED.equals(getThemeMode(context));
    }

    public static void styleBottomNavigation(@Nullable BottomNavigationView bottomNav, @NonNull Context context) {
        if (bottomNav == null) {
            return;
        }

        int active = resolveAccentColor(context);
        int inactive = ContextCompat.getColor(context, R.color.bottom_nav_inactive);
        ColorStateList states = new ColorStateList(
            new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
            },
            new int[]{
                active,
                inactive
            }
        );

        bottomNav.setItemIconTintList(states);
        bottomNav.setItemTextColor(states);
        bottomNav.setItemActiveIndicatorEnabled(true);
        bottomNav.setItemActiveIndicatorColor(ColorStateList.valueOf(withAlpha(active, 0.26f)));
    }

    public static void applyAmoledSurfaceIfNeeded(@NonNull AppCompatActivity activity) {
        if (!isAmoledMode(activity)) {
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup contentGroup && contentGroup.getChildCount() > 0) {
            View root = contentGroup.getChildAt(0);
            root.setBackgroundColor(Color.BLACK);
        } else if (content != null) {
            content.setBackgroundColor(Color.BLACK);
        }

        Window window = activity.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(Color.BLACK);
                window.setNavigationBarColor(Color.BLACK);
            }
        }
    }

    public static void applyThemeAndRecreate(@NonNull AppCompatActivity activity) {
        applySavedTheme(activity);
        activity.recreate();
    }

    @ColorInt
    public static int resolveAccentColor(@NonNull Context context) {
        return switch (getAccentColor(context)) {
            case ACCENT_BLUE -> ContextCompat.getColor(context, R.color.accent_blue);
            case ACCENT_GREEN -> ContextCompat.getColor(context, R.color.accent_green);
            case ACCENT_RED -> ContextCompat.getColor(context, R.color.accent_red);
            case ACCENT_ORANGE -> ContextCompat.getColor(context, R.color.accent_orange);
            default -> ContextCompat.getColor(context, R.color.accent_purple);
        };
    }

    @ColorInt
    public static int resolveAccentGradientEnd(@NonNull Context context) {
        return switch (getAccentColor(context)) {
            case ACCENT_BLUE -> ContextCompat.getColor(context, R.color.accent_blue_end);
            case ACCENT_GREEN -> ContextCompat.getColor(context, R.color.accent_green_end);
            case ACCENT_RED -> ContextCompat.getColor(context, R.color.accent_red_end);
            case ACCENT_ORANGE -> ContextCompat.getColor(context, R.color.accent_orange_end);
            default -> ContextCompat.getColor(context, R.color.accent_purple_end);
        };
    }

    private static int resolveNightMode(@NonNull String mode) {
        return switch (mode) {
            case MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
            case MODE_DARK, MODE_AMOLED -> AppCompatDelegate.MODE_NIGHT_YES;
            default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
    }

    @Nullable
    private static String normalizeThemeMode(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.US);
        return switch (value) {
            case MODE_SYSTEM, MODE_LIGHT, MODE_DARK, MODE_AMOLED -> value;
            default -> null;
        };
    }

    @Nullable
    private static String normalizeAccent(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.US);
        return switch (value) {
            case ACCENT_BLUE, ACCENT_PURPLE, ACCENT_GREEN, ACCENT_RED, ACCENT_ORANGE -> value;
            default -> null;
        };
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static int withAlpha(int color, float alpha) {
        int bounded = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (color & 0x00FFFFFF) | (bounded << 24);
    }
}
