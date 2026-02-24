package com.aniflow.app;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.aniflow.model.Anime;
import com.aniflow.model.HomeRailItem;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ContinueWatchingAdapter extends RecyclerView.Adapter<ContinueWatchingAdapter.ViewHolder> {

    public interface OnAnimeClickListener {
        void onAnimeClick(Anime anime);
    }

    private final Context context;
    private final OnAnimeClickListener listener;
    private final List<HomeRailItem> items = new ArrayList<>();

    public ContinueWatchingAdapter(Context context, OnAnimeClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(@Nullable List<HomeRailItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_continue_watching, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ImageView coverImage;
        private final TextView imageFallbackText;
        private final TextView titleText;
        private final TextView metaText;
        private final TextView episodeChip;
        private final ProgressBar watchProgress;
        private final TextView progressText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            coverImage = itemView.findViewById(R.id.coverImage);
            imageFallbackText = itemView.findViewById(R.id.imageFallbackText);
            titleText = itemView.findViewById(R.id.titleText);
            metaText = itemView.findViewById(R.id.metaText);
            episodeChip = itemView.findViewById(R.id.episodeChip);
            watchProgress = itemView.findViewById(R.id.watchProgress);
            progressText = itemView.findViewById(R.id.progressText);
        }

        void bind(HomeRailItem item) {
            Anime anime = item.getAnime();
            titleText.setText(formatTitle(anime.getTitle()));

            String meta = composeMeta(normalizedStatus(anime.getStatus()), normalizedScore(anime));
            if (meta == null) {
                metaText.setVisibility(View.GONE);
            } else {
                metaText.setVisibility(View.VISIBLE);
                metaText.setText(meta);
            }

            episodeChip.setText(normalizeEpisodeLabel(item.getSubtitle()));

            if (item.isShowProgress()) {
                int progress = Math.max(0, Math.min(item.getProgressPercent(), 100));
                watchProgress.setVisibility(View.VISIBLE);
                progressText.setVisibility(View.VISIBLE);
                watchProgress.setProgress(progress);
                progressText.setText(buildProgressBar(progress) + " " + progress + "% watched");
            } else {
                watchProgress.setVisibility(View.GONE);
                progressText.setVisibility(View.GONE);
            }

            cardView.setCardElevation(baseCardElevation());
            loadCover(pickImageUrl(anime));
            setupInteraction(anime);
        }

        private void loadCover(@Nullable String url) {
            imageFallbackText.setVisibility(View.GONE);
            if (url == null || url.trim().isEmpty()) {
                coverImage.setImageResource(R.drawable.placeholder_anime);
                imageFallbackText.setVisibility(View.VISIBLE);
                return;
            }

            Glide.with(context)
                .load(url)
                .placeholder(R.drawable.placeholder_anime)
                .error(R.drawable.placeholder_anime)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        imageFallbackText.setVisibility(View.VISIBLE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        imageFallbackText.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(coverImage);
        }

        private void setupInteraction(Anime anime) {
            final float baseElevation = baseCardElevation();
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAnimeClick(anime);
                }
            });

            itemView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    cardView.animate().scaleX(1.02f).scaleY(1.02f).setDuration(120).start();
                    cardView.setCardElevation(baseElevation + 6f);
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    cardView.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    cardView.setCardElevation(baseElevation);
                }
                return false;
            });
        }

        private float baseCardElevation() {
            int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return nightMode == Configuration.UI_MODE_NIGHT_YES ? 10f : 6f;
        }
    }

    private static String pickImageUrl(@NonNull Anime anime) {
        String cover = safe(anime.getCoverImage(), "");
        if (!cover.isEmpty()) {
            return cover;
        }
        String banner = safe(anime.getBannerImage(), "");
        if (!banner.isEmpty()) {
            return banner;
        }
        return "";
    }

    private static String safe(@Nullable String value, @NonNull String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    @NonNull
    private static String formatTitle(@Nullable String raw) {
        return safe(raw, "Untitled").replaceAll("\\s+", " ").trim();
    }

    @Nullable
    private static String composeMeta(@Nullable String status, @Nullable String score) {
        if (status == null && score == null) {
            return null;
        }
        if (status == null) {
            return "★ " + score;
        }
        if (score == null) {
            return status;
        }
        return status + " • ★ " + score;
    }

    @Nullable
    private static String normalizedStatus(@Nullable String raw) {
        String status = cleanValue(raw);
        if (status == null) {
            return null;
        }
        String value = status.toLowerCase(Locale.US);
        if (value.contains("complete") || value.contains("finish")) {
            return "Completed";
        }
        if (value.contains("ongoing") || value.contains("release")) {
            return "Ongoing";
        }
        if (value.contains("upcoming") || value.contains("soon") || value.contains("preview")) {
            return "Upcoming";
        }
        return null;
    }

    @Nullable
    private static String normalizedScore(@NonNull Anime anime) {
        if (anime.getScore() > 0) {
            String scoreText = cleanValue(anime.getScoreText());
            if (scoreText != null) {
                return scoreText;
            }
            return String.format(Locale.US, "%.1f", anime.getScore());
        }
        return cleanValue(anime.getScoreText());
    }

    @NonNull
    private static String normalizeEpisodeLabel(@Nullable String raw) {
        String subtitle = safe(raw, "")
            .replaceAll("(?i)enienda", "Episode")
            .replaceAll("\\s+", " ")
            .trim();
        if (subtitle.isEmpty()) {
            return "Latest Episode";
        }

        String clean = cleanValue(subtitle);
        if (clean == null) {
            return "Latest Episode";
        }

        if (clean.matches("^\\d+$")) {
            return "Episode " + clean;
        }
        return clean;
    }

    @Nullable
    private static String cleanValue(@Nullable String raw) {
        String value = safe(raw, "");
        if (value.isEmpty()) {
            return null;
        }
        String lower = value.toLowerCase(Locale.US);
        if ("-".equals(value)
            || "unknown".equals(lower)
            || "n/a".equals(lower)
            || "na".equals(lower)
            || "tba".equals(lower)) {
            return null;
        }
        return value;
    }

    @NonNull
    private static String buildProgressBar(int progress) {
        int bounded = Math.max(0, Math.min(progress, 100));
        int filled = Math.round(bounded / 10f);
        StringBuilder builder = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            builder.append(i < filled ? '▰' : '▱');
        }
        return builder.toString();
    }
}
