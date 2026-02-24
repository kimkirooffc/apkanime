package com.aniflow.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aniflow.model.Anime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnimeAdapter extends RecyclerView.Adapter<AnimeAdapter.ViewHolder> {
    
    private final Context context;
    private List<Anime> animeList;
    private final OnAnimeClickListener listener;
    
    public interface OnAnimeClickListener {
        void onAnimeClick(Anime anime);
    }
    
    public AnimeAdapter(Context context, List<Anime> animeList, OnAnimeClickListener listener) {
        this.context = context;
        this.animeList = animeList != null ? animeList : new ArrayList<>();
        this.listener = listener;
    }
    
    public void setData(List<Anime> newList) {
        animeList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_anime_card, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Anime anime = animeList.get(position);
        holder.bind(anime);
    }
    
    @Override
    public int getItemCount() {
        return animeList.size();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView coverImage;
        private final TextView titleText;
        private final TextView metaText;
        private final TextView scoreBadge;
        
        ViewHolder(View itemView) {
            super(itemView);
            coverImage = itemView.findViewById(R.id.coverImage);
            titleText = itemView.findViewById(R.id.titleText);
            metaText = itemView.findViewById(R.id.metaText);
            scoreBadge = itemView.findViewById(R.id.scoreBadge);
        }
        
        void bind(Anime anime) {
            titleText.setText(anime.getTitle());

            String episodeText = resolveEpisodeText(anime);
            String status = normalizedStatus(anime.getStatus());
            metaText.setText(status == null ? episodeText : status + " • " + episodeText);

            String score = normalizedScore(anime);
            if (score == null) {
                scoreBadge.setVisibility(View.GONE);
            } else {
                scoreBadge.setVisibility(View.VISIBLE);
                scoreBadge.setText("★ " + score);
            }
            
            // Load cover image
            // Glide.with(context).load(anime.getCoverImage()).into(coverImage);
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAnimeClick(anime);
                }
            });
        }
    }

    private static String resolveEpisodeText(@NonNull Anime anime) {
        if (anime.getDisplayEpisodeCount() > 0) {
            return anime.getDisplayEpisodeCount() + " eps";
        }
        String raw = cleanValue(anime.getEpisodeLabel());
        if (raw == null) {
            return "? eps";
        }
        String normalized = raw.replaceAll("(?i)enienda", "Episode");
        if (normalized.matches("^\\d+$")) {
            return normalized + " eps";
        }
        return normalized;
    }

    private static String normalizedStatus(String raw) {
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

    private static String cleanValue(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
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
