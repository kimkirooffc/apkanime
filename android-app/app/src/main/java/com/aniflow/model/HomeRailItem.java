package com.aniflow.model;

import androidx.annotation.NonNull;

public class HomeRailItem {
    private final Anime anime;
    private final String subtitle;
    private final int progressPercent;
    private final boolean showProgress;

    public HomeRailItem(@NonNull Anime anime, @NonNull String subtitle, int progressPercent, boolean showProgress) {
        this.anime = anime;
        this.subtitle = subtitle;
        this.progressPercent = Math.max(0, Math.min(progressPercent, 100));
        this.showProgress = showProgress;
    }

    public Anime getAnime() { return anime; }
    public String getSubtitle() { return subtitle; }
    public int getProgressPercent() { return progressPercent; }
    public boolean isShowProgress() { return showProgress; }
}
