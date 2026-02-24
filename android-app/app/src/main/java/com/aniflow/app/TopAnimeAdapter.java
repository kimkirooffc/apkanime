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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TopAnimeAdapter extends RecyclerView.Adapter<TopAnimeAdapter.ViewHolder> {

    public interface OnAnimeClickListener {
        void onAnimeClick(Anime anime);
    }

    public interface OnAnimeLongClickListener {
        void onAnimeLongClick(Anime anime);
    }

    private static final Pattern YEAR_PATTERN = Pattern.compile("(19\\d{2}|20\\d{2})");

    private final Context context;
    private final OnAnimeClickListener clickListener;
    private final boolean showRank;
    private OnAnimeLongClickListener longClickListener;
    private final List<Anime> animeList = new ArrayList<>();

    public TopAnimeAdapter(Context context, OnAnimeClickListener clickListener) {
        this(context, clickListener, true);
    }

    public TopAnimeAdapter(Context context, OnAnimeClickListener clickListener, boolean showRank) {
        this.context = context;
        this.clickListener = clickListener;
        this.showRank = showRank;
    }

    public void setOnAnimeLongClickListener(OnAnimeLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setData(@Nullable List<Anime> data) {
        animeList.clear();
        if (data != null) {
            animeList.addAll(data);
        }
        notifyDataSetChanged();
    }

    public Anime getItemAt(int position) {
        if (position < 0 || position >= animeList.size()) {
            return null;
        }
        return animeList.get(position);
    }

    public boolean isEmpty() {
        return animeList.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_anime_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(animeList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return animeList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ImageView coverImage;
        private final TextView titleText;
        private final TextView metaText;
        private final TextView scoreBadge;
        private final TextView rankBadge;
        private final TextView genreChip;
        private final TextView imageFallbackText;
        private final TextView statusBadge;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            coverImage = itemView.findViewById(R.id.coverImage);
            titleText = itemView.findViewById(R.id.titleText);
            metaText = itemView.findViewById(R.id.metaText);
            scoreBadge = itemView.findViewById(R.id.scoreBadge);
            rankBadge = itemView.findViewById(R.id.rankBadge);
            genreChip = itemView.findViewById(R.id.genreChip);
            imageFallbackText = itemView.findViewById(R.id.imageFallbackText);
            statusBadge = itemView.findViewById(R.id.statusBadge);
        }

        void bind(Anime anime, int position) {
            titleText.setText(safe(anime.getTitle(), "Untitled"));

            String episodeText = resolveEpisodeText(anime);
            String releaseYear = extractYear(anime);
            metaText.setText(composeMeta(episodeText, releaseYear));

            String score = normalizedScore(anime);
            if (score == null) {
                scoreBadge.setVisibility(View.GONE);
            } else {
                scoreBadge.setVisibility(View.VISIBLE);
                scoreBadge.setText("★ " + score);
            }

            if (showRank) {
                rankBadge.setVisibility(View.VISIBLE);
                rankBadge.setText(String.valueOf(position + 1));
            } else {
                rankBadge.setVisibility(View.GONE);
            }

            String status = normalizedStatus(anime.getStatus());
            if (status == null) {
                statusBadge.setVisibility(View.GONE);
            } else {
                statusBadge.setVisibility(View.VISIBLE);
                statusBadge.setText(status);
                statusBadge.setBackgroundResource(statusBackground(status));
            }

            String chipText = firstMeaningfulGenre(anime);
            if (chipText == null) {
                genreChip.setVisibility(View.GONE);
            } else {
                genreChip.setVisibility(View.VISIBLE);
                genreChip.setText(chipText);
            }

            cardView.setCardElevation(baseCardElevation());
            loadGlideImage(pickImageUrl(anime));
            setupInteraction(anime);
        }

        private void loadGlideImage(String url) {
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
                if (clickListener != null) {
                    clickListener.onAnimeClick(anime);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onAnimeLongClick(anime);
                    return true;
                }
                return false;
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

        private int statusBackground(String status) {
            if ("Completed".equals(status)) {
                return R.drawable.badge_status_completed;
            }
            if ("Ongoing".equals(status)) {
                return R.drawable.badge_status_ongoing;
            }
            if ("Upcoming".equals(status)) {
                return R.drawable.badge_status_upcoming;
            }
            return R.drawable.badge_status_unknown;
        }
    }

    private String pickImageUrl(@NonNull Anime anime) {
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

    private String extractYear(Anime anime) {
        String year = cleanDisplayValue(anime.getReleaseYear());
        if (year != null) {
            return year;
        }

        Matcher matcher = YEAR_PATTERN.matcher(safe(anime.getReleaseInfo(), ""));
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private String resolveEpisodeText(@NonNull Anime anime) {
        int totalEpisodes = anime.getDisplayEpisodeCount();
        if (totalEpisodes > 0) {
            return totalEpisodes + " eps";
        }

        String label = cleanDisplayValue(anime.getEpisodeLabel());
        if (label != null) {
            String normalized = label.replaceAll("(?i)enienda", "Episode");
            if (normalized.matches("^\\d+$")) {
                return normalized + " eps";
            }
            return normalized;
        }

        return "? eps";
    }

    @Nullable
    private String normalizedScore(@NonNull Anime anime) {
        if (anime.getScore() > 0) {
            String fromText = cleanDisplayValue(anime.getScoreText());
            if (fromText != null) {
                return fromText;
            }
            return String.format(Locale.US, "%.1f", anime.getScore());
        }
        return cleanDisplayValue(anime.getScoreText());
    }

    @Nullable
    private String normalizedStatus(String raw) {
        String value = safe(raw, "").toLowerCase(Locale.US);
        if (value.isEmpty()) {
            return null;
        }
        if (value.contains("complete") || value.contains("finish")) {
            return "Completed";
        }
        if (value.contains("ongoing") || value.contains("release")) {
            return "Ongoing";
        }
        if (value.contains("upcoming") || value.contains("preview") || value.contains("soon")) {
            return "Upcoming";
        }
        return null;
    }

    private String composeMeta(@NonNull String episodeText, @Nullable String releaseYear) {
        if (releaseYear == null) {
            return episodeText;
        }
        return episodeText + " • " + releaseYear;
    }

    @Nullable
    private String firstMeaningfulGenre(@NonNull Anime anime) {
        if (anime.getGenres() == null || anime.getGenres().isEmpty()) {
            return null;
        }
        for (String genre : anime.getGenres()) {
            String normalized = cleanDisplayValue(genre);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    @Nullable
    private String cleanDisplayValue(@Nullable String raw) {
        String value = safe(raw, "");
        if (value.isEmpty()) {
            return null;
        }
        String lower = value.toLowerCase(Locale.US);
        if ("-".equals(value)
            || "unknown".equals(lower)
            || "tba".equals(lower)
            || "n/a".equals(lower)
            || "na".equals(lower)) {
            return null;
        }
        return value;
    }

    private static String safe(@Nullable String value, @NonNull String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
