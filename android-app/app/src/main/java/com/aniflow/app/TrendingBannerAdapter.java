package com.aniflow.app;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.aniflow.model.Anime;
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

public class TrendingBannerAdapter extends RecyclerView.Adapter<TrendingBannerAdapter.ViewHolder> {

    public interface OnAnimeClickListener {
        void onAnimeClick(Anime anime);
    }

    private final Context context;
    private final OnAnimeClickListener listener;
    private final List<Anime> animeList = new ArrayList<>();

    public TrendingBannerAdapter(Context context, OnAnimeClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(@Nullable List<Anime> data) {
        animeList.clear();
        if (data != null) {
            animeList.addAll(data);
        }
        notifyDataSetChanged();
    }

    public int size() {
        return animeList.size();
    }

    public boolean isEmpty() {
        return animeList.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_banner_trending, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(animeList.get(position));
    }

    @Override
    public int getItemCount() {
        return animeList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ImageView bannerImage;
        private final TextView imageFallbackText;
        private final TextView titleText;
        private final TextView infoText;
        private final TextView ratingBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            bannerImage = itemView.findViewById(R.id.bannerImage);
            imageFallbackText = itemView.findViewById(R.id.imageFallbackText);
            titleText = itemView.findViewById(R.id.titleText);
            infoText = itemView.findViewById(R.id.infoText);
            ratingBadge = itemView.findViewById(R.id.ratingBadge);
        }

        void bind(Anime anime) {
            titleText.setText(safe(anime.getTitle(), "Untitled"));

            String episode = resolveEpisodeText(anime);
            String info = composeInfo(normalizedStatus(anime.getStatus()), episode);
            infoText.setText(info);

            String score = normalizedScore(anime);
            if (score == null) {
                ratingBadge.setVisibility(View.GONE);
            } else {
                ratingBadge.setVisibility(View.VISIBLE);
                ratingBadge.setText("★ " + score);
            }

            String bannerUrl = anime.getBannerImage();
            if (bannerUrl == null || bannerUrl.trim().isEmpty()) {
                bannerUrl = anime.getCoverImage();
            }
            cardView.setCardElevation(baseCardElevation());
            loadBanner(bannerUrl);
            setupInteraction(anime);
        }

        private void loadBanner(@Nullable String url) {
            imageFallbackText.setVisibility(View.GONE);
            if (url == null || url.trim().isEmpty()) {
                bannerImage.setImageResource(R.drawable.placeholder_banner);
                imageFallbackText.setVisibility(View.VISIBLE);
                return;
            }

            Glide.with(context)
                .load(url)
                .placeholder(R.drawable.placeholder_banner)
                .error(R.drawable.placeholder_banner)
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
                .into(bannerImage);
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

    private static String safe(@Nullable String value, @NonNull String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    @NonNull
    private static String resolveEpisodeText(@NonNull Anime anime) {
        if (anime.getDisplayEpisodeCount() > 0) {
            return anime.getDisplayEpisodeCount() + " eps";
        }
        String label = cleanValue(anime.getEpisodeLabel());
        if (label == null) {
            return "? eps";
        }
        String normalized = label.replaceAll("(?i)enienda", "Episode");
        if (normalized.matches("^\\d+$")) {
            return normalized + " eps";
        }
        return normalized;
    }

    @NonNull
    private static String composeInfo(@Nullable String status, @NonNull String episode) {
        return status == null ? episode : status + " • " + episode;
    }

    @Nullable
    private static String normalizedStatus(@Nullable String raw) {
        String value = cleanValue(raw);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.US);
        if (normalized.contains("complete") || normalized.contains("finish")) {
            return "Completed";
        }
        if (normalized.contains("ongoing") || normalized.contains("release")) {
            return "Ongoing";
        }
        if (normalized.contains("upcoming") || normalized.contains("preview") || normalized.contains("soon")) {
            return "Upcoming";
        }
        return null;
    }

    @Nullable
    private static String normalizedScore(@NonNull Anime anime) {
        if (anime.getScore() > 0) {
            String value = cleanValue(anime.getScoreText());
            if (value != null) {
                return value;
            }
            return String.format(Locale.US, "%.1f", anime.getScore());
        }
        return cleanValue(anime.getScoreText());
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
}
