package com.aniflow.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.aniflow.model.Episode;
import com.aniflow.service.ApiClient;
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

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.ViewHolder> {

    public interface OnEpisodeClickListener {
        void onEpisodeClick(Episode episode);
    }

    private final Context context;
    private final OnEpisodeClickListener listener;
    private final List<Episode> episodes = new ArrayList<>();
    private String selectedEpisodeSlug;
    private String posterUrl;

    public EpisodeAdapter(Context context, OnEpisodeClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(@Nullable List<Episode> data) {
        episodes.clear();
        if (data != null) {
            episodes.addAll(data);
        }
        notifyDataSetChanged();
    }

    public void setSelectedEpisodeSlug(@Nullable String episodeSlug) {
        this.selectedEpisodeSlug = episodeSlug;
        notifyDataSetChanged();
    }

    public void setPosterUrl(@Nullable String posterUrl) {
        this.posterUrl = posterUrl;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_episode, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(episodes.get(position));
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ImageView thumbnailImage;
        private final ImageView playIcon;
        private final TextView episodeNumber;
        private final TextView episodeTitle;
        private final TextView episodeDate;
        private final TextView episodeMetaSeparator;
        private final TextView episodeDuration;
        private final TextView episodeStateLabel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            thumbnailImage = itemView.findViewById(R.id.episodeThumbnail);
            playIcon = itemView.findViewById(R.id.playIcon);
            episodeNumber = itemView.findViewById(R.id.episodeNumber);
            episodeTitle = itemView.findViewById(R.id.episodeTitle);
            episodeDate = itemView.findViewById(R.id.episodeDate);
            episodeMetaSeparator = itemView.findViewById(R.id.episodeMetaSeparator);
            episodeDuration = itemView.findViewById(R.id.episodeDuration);
            episodeStateLabel = itemView.findViewById(R.id.episodeStateLabel);
        }

        void bind(Episode episode) {
            String number = episode.getEpisodeNumber() > 0 ? "Episode " + episode.getEpisodeNumber() : "Episode ?";
            episodeNumber.setText(number);
            episodeTitle.setText(safe(episode.getTitle(), "Judul episode tidak tersedia"));

            String releaseDateText = normalizeDate(episode.getReleaseDate());
            String durationText = normalizeDuration(episode.getDuration());

            boolean hasDate = !releaseDateText.isEmpty();
            boolean hasDuration = !durationText.isEmpty();

            episodeDate.setVisibility(hasDate ? View.VISIBLE : View.GONE);
            episodeDate.setText(releaseDateText);

            episodeDuration.setVisibility(hasDuration ? View.VISIBLE : View.GONE);
            episodeDuration.setText(durationText);

            episodeMetaSeparator.setVisibility(hasDate && hasDuration ? View.VISIBLE : View.GONE);

            boolean released = episode.isReleased();
            boolean selected = selectedEpisodeSlug != null && selectedEpisodeSlug.equals(episode.getSlug());

            int strokeColor;
            if (!released) {
                strokeColor = ContextCompat.getColor(context, R.color.status_upcoming);
            } else if (selected) {
                strokeColor = ContextCompat.getColor(context, R.color.primary);
            } else {
                strokeColor = ContextCompat.getColor(context, R.color.stroke);
            }

            cardView.setStrokeWidth(dp(selected ? 2 : 1));
            cardView.setStrokeColor(strokeColor);

            episodeStateLabel.setVisibility(released ? View.GONE : View.VISIBLE);
            episodeStateLabel.setText(released ? "" : "Coming Soon");
            playIcon.setVisibility(released ? View.VISIBLE : View.INVISIBLE);
            playIcon.setColorFilter(ContextCompat.getColor(context,
                selected ? R.color.primary : R.color.onSurfaceVariant));

            itemView.setAlpha(released ? 1f : 0.82f);
            loadThumbnail(firstNonBlank(episode.getThumbnail(), posterUrl));

            itemView.setOnClickListener(v -> {
                if (!released) {
                    Toast.makeText(context, "Episode ini belum rilis", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (listener != null) {
                    listener.onEpisodeClick(episode);
                }
            });
        }

        private void loadThumbnail(@Nullable String url) {
            if (url == null || url.trim().isEmpty()) {
                thumbnailImage.setImageResource(R.drawable.placeholder_anime);
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
                        thumbnailImage.setImageResource(R.drawable.placeholder_anime);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(thumbnailImage);
        }
    }

    private int dp(int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
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
    private static String normalizeDate(@Nullable String raw) {
        String value = sanitizeDisplay(raw);
        if (value.isEmpty()) {
            return "";
        }
        String formatted = ApiClient.formatReleaseDate(value);
        if (isInvalid(formatted)) {
            return "";
        }
        return formatted;
    }

    @NonNull
    private static String normalizeDuration(@Nullable String raw) {
        String value = sanitizeDisplay(raw);
        if (value.isEmpty()) {
            return "";
        }
        return value;
    }

    @NonNull
    private static String sanitizeDisplay(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty() || isInvalid(value)) {
            return "";
        }
        return value;
    }

    private static boolean isInvalid(@Nullable String raw) {
        if (raw == null) {
            return true;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return true;
        }
        String lower = value.toLowerCase(Locale.US);
        return "-".equals(value)
            || "unknown".equals(lower)
            || "n/a".equals(lower)
            || "na".equals(lower)
            || "tba".equals(lower);
    }
}
