package com.aniflow.app;

import android.content.Intent;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public final class NavigationHelper {

    public static final String TOP_HOME = "home";
    public static final String TOP_SEARCH = "search";
    public static final String TOP_NOTIFICATIONS = "notifications";
    public static final String TOP_PROFILE = "profile";

    private NavigationHelper() {
    }

    public static void bind(BottomNavigationView bottomNav, AppCompatActivity activity, @IdRes int selectedId) {
        if (bottomNav == null || activity == null) {
            return;
        }
        ThemeManager.styleBottomNavigation(bottomNav, activity);
        bottomNav.setSelectedItemId(selectedId);
        bottomNav.setOnItemSelectedListener(item -> handleSelection(activity, item.getItemId()));
    }

    public static void bind(LiquidGlassNavbarView topNav, AppCompatActivity activity, String selectedId) {
        if (topNav == null || activity == null) {
            return;
        }
        topNav.setItems(java.util.List.of(
            new LiquidGlassNavbarView.NavItem(TOP_HOME, "Home", R.drawable.ic_home_outline, R.drawable.ic_home_filled),
            new LiquidGlassNavbarView.NavItem(TOP_SEARCH, "Search", R.drawable.ic_search_outline, R.drawable.ic_search_filled),
            new LiquidGlassNavbarView.NavItem(TOP_NOTIFICATIONS, "Notifications", R.drawable.ic_notifications_outline, R.drawable.ic_notifications_filled),
            new LiquidGlassNavbarView.NavItem(TOP_PROFILE, "Profile", R.drawable.ic_account_outline, R.drawable.ic_account_filled)
        ));
        topNav.setSelectedId(selectedId);
        topNav.setOnNavSelectedListener(id -> handleTopSelection(activity, id));
    }

    public static boolean handleSelection(AppCompatActivity activity, @IdRes int itemId) {
        if (itemId == R.id.nav_home) {
            return open(activity, MainActivity.class);
        }
        if (itemId == R.id.nav_search) {
            return open(activity, SearchActivity.class);
        }
        if (itemId == R.id.nav_watchlist) {
            return open(activity, WatchlistActivity.class);
        }
        if (itemId == R.id.nav_profile) {
            return open(activity, ProfileActivity.class);
        }
        return false;
    }

    public static boolean handleTopSelection(AppCompatActivity activity, String itemId) {
        if (TOP_HOME.equals(itemId)) {
            return open(activity, MainActivity.class);
        }
        if (TOP_SEARCH.equals(itemId)) {
            return open(activity, SearchActivity.class);
        }
        if (TOP_NOTIFICATIONS.equals(itemId)) {
            // Existing app destination for alerts/watch queue.
            return open(activity, WatchlistActivity.class);
        }
        if (TOP_PROFILE.equals(itemId)) {
            return open(activity, ProfileActivity.class);
        }
        return false;
    }

    private static boolean open(AppCompatActivity activity, Class<?> target) {
        if (activity.getClass() == target) {
            return true;
        }
        Intent intent = new Intent(activity, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
        activity.finish();
        return true;
    }
}
