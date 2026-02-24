package com.aniflow.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aniflow.model.Anime;
import com.aniflow.model.Episode;
import com.aniflow.model.WatchHistoryItem;
import com.aniflow.service.ApiClient;
import com.aniflow.service.Repository;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AnimeDetailActivity extends AppCompatActivity {

    private static final int INITIAL_EPISODE_VISIBLE_COUNT = 12;
    private static final int SYNOPSIS_COLLAPSED_LENGTH = 340;

    private Repository repository;

    private ImageView detailBanner;
    private ImageView detailPoster;
    private ImageButton backButton;
    private ImageButton headerWatchlistButton;
    private ImageButton headerShareButton;

    private TextView detailTitle;
    private TextView detailRating;
    private TextView detailStatusBadge;
    private TextView detailExtraInfo;
    private TextView detailStudio;
    private TextView detailProducer;
    private TextView detailSynopsis;
    private TextView synopsisToggle;
    private TextView episodeHeaderTitle;
    private TextView errorText;

    private ProgressBar detailLoading;
    private ChipGroup genreChipGroup;
    private RecyclerView episodeRecycler;

    private MaterialButton playPrimaryButton;
    private MaterialButton watchlistButton;
    private MaterialButton shareButton;
    private MaterialButton downloadButton;
    private MaterialButton trailerButton;
    private MaterialButton viewAllEpisodesButton;

    private EpisodeAdapter episodeAdapter;

    private String animeSlug;
    private Anime currentAnime;

    private final List<Episode> fullEpisodes = new ArrayList<>();
    private boolean showingAllEpisodes = false;
    private boolean synopsisExpanded = false;
    private String fullSynopsis = "";
    private String collapsedSynopsis = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_AniFlow);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_anime_detail);
        ThemeManager.applyAmoledSurfaceIfNeeded(this);

        repository = new Repository(this);
        animeSlug = getIntent().getStringExtra("anime_slug");

        initViews();
        setupEpisodeList();
        setupListeners();
        loadDetail();
    }

    private void initViews() {
        detailBanner = findViewById(R.id.detailBanner);
        detailPoster = findViewById(R.id.detailPoster);
        backButton = findViewById(R.id.backButton);
        headerWatchlistButton = findViewById(R.id.headerWatchlistButton);
        headerShareButton = findViewById(R.id.headerShareButton);

        detailTitle = findViewById(R.id.detailTitle);
        detailRating = findViewById(R.id.detailRating);
        detailStatusBadge = findViewById(R.id.detailStatusBadge);
        detailExtraInfo = findViewById(R.id.detailExtraInfo);
        detailStudio = findViewById(R.id.detailStudio);
        detailProducer = findViewById(R.id.detailProducer);
        detailSynopsis = findViewById(R.id.detailSynopsis);
        synopsisToggle = findViewById(R.id.synopsisToggle);
        episodeHeaderTitle = findViewById(R.id.episodeHeaderTitle);
        errorText = findViewById(R.id.errorText);

        detailLoading = findViewById(R.id.detailLoading);
        genreChipGroup = findViewById(R.id.genreChipGroup);
        episodeRecycler = findViewById(R.id.episodeRecycler);

        playPrimaryButton = findViewById(R.id.playPrimaryButton);
        watchlistButton = findViewById(R.id.watchlistButton);
        shareButton = findViewById(R.id.shareButton);
        downloadButton = findViewById(R.id.downloadButton);
        trailerButton = findViewById(R.id.trailerButton);
        viewAllEpisodesButton = findViewById(R.id.viewAllEpisodesButton);

        String title = getIntent().getStringExtra("anime_title");
        if (!isBlank(title)) {
            detailTitle.setText(title);
        }

        String cover = getIntent().getStringExtra("anime_cover");
        if (!isBlank(cover)) {
            loadBanner(cover);
            loadPoster(cover);
        }
    }

    private void setupEpisodeList() {
        episodeAdapter = new EpisodeAdapter(this, this::openPlayerEpisode);
        episodeRecycler.setLayoutManager(new LinearLayoutManager(this));
        episodeRecycler.setAdapter(episodeAdapter);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());

        headerWatchlistButton.setOnClickListener(v -> toggleWatchlist());
        watchlistButton.setOnClickListener(v -> toggleWatchlist());

        headerShareButton.setOnClickListener(v -> shareCurrentAnime());
        shareButton.setOnClickListener(v -> shareCurrentAnime());
        downloadButton.setOnClickListener(v -> openDownloadFlow());
        trailerButton.setOnClickListener(v -> openTrailer());

        playPrimaryButton.setOnClickListener(v -> openPrimaryPlayback());
        synopsisToggle.setOnClickListener(v -> toggleSynopsis());
        viewAllEpisodesButton.setOnClickListener(v -> toggleEpisodeList());
    }

    private void loadDetail() {
        if (isBlank(animeSlug)) {
            showError("Anime tidak valid");
            return;
        }

        detailLoading.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);

        String titleHint = getIntent().getStringExtra("anime_title");
        repository.getAnimeDetail(animeSlug, safe(titleHint, ""), new Repository.Callback<ApiClient.AnimeDetail>() {
            @Override
            public void onSuccess(ApiClient.AnimeDetail result) {
                detailLoading.setVisibility(View.GONE);
                if (result == null || result.anime == null) {
                    showError("Detail anime tidak ditemukan");
                    return;
                }
                currentAnime = result.anime;
                animeSlug = safe(currentAnime.getSlug(), animeSlug);
                bindAnime(result.anime, result.episodes == null ? new ArrayList<>() : result.episodes);
            }

            @Override
            public void onFailure(Throwable error) {
                detailLoading.setVisibility(View.GONE);
                showError("Gagal memuat detail anime. Coba lagi.");
            }
        });
    }

    private void bindAnime(Anime anime, List<Episode> episodeList) {
        detailTitle.setText(safe(anime.getTitle(), "-"));
        detailRating.setText(buildRating(anime));

        String status = normalizeStatus(anime.getStatus());
        detailStatusBadge.setText(status);
        detailStatusBadge.setBackgroundResource(statusBackground(status));

        int totalEpisode = anime.getDisplayEpisodeCount() > 0 ? anime.getDisplayEpisodeCount() : episodeList.size();
        String year = safe(anime.getReleaseYear(), "-");
        String duration = safe(anime.getDuration(), "-");
        detailExtraInfo.setText("Tahun " + year + " • " + duration + " • " + totalEpisode + " Episode");

        detailStudio.setText("Studio: " + safe(anime.getStudio(), "-"));
        String producer = safe(anime.getProducer(), "-");
        detailProducer.setText("Produser: " + producer);
        detailProducer.setVisibility("-".equals(producer) ? View.GONE : View.VISIBLE);

        loadBanner(preferredBanner(anime));
        loadPoster(anime.getCoverImage());

        bindGenres(anime.getGenres());
        bindSynopsis(anime.getDescription());

        fullEpisodes.clear();
        fullEpisodes.addAll(episodeList);
        showingAllEpisodes = false;
        applyEpisodeList();
        episodeAdapter.setPosterUrl(anime.getCoverImage());

        trailerButton.setVisibility(anime.hasTrailer() ? View.VISIBLE : View.GONE);
        updateWatchlistButton();

        WatchHistoryItem latest = repository.getLatestHistoryForAnime(anime.getSlug());
        if (latest != null && latest.getEpisodeNumber() > 0) {
            playPrimaryButton.setText("Lanjutkan Episode " + latest.getEpisodeNumber());
        } else {
            playPrimaryButton.setText("Tonton Episode 1");
        }

        errorText.setVisibility(View.GONE);
    }

    private void bindGenres(List<String> genres) {
        genreChipGroup.removeAllViews();

        if (genres == null || genres.isEmpty()) {
            genreChipGroup.setVisibility(View.GONE);
            return;
        }

        genreChipGroup.setVisibility(View.VISIBLE);
        int max = Math.min(5, genres.size());
        for (int i = 0; i < max; i++) {
            String genre = safe(genres.get(i), "-");
            if ("-".equals(genre)) {
                continue;
            }
            genreChipGroup.addView(createGenreChip(genre));
        }
    }

    private Chip createGenreChip(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setClickable(false);
        chip.setCheckable(false);
        chip.setChipBackgroundColorResource(R.color.genre_chip_bg);
        chip.setTextColor(ContextCompat.getColor(this, R.color.genre_chip_text));
        return chip;
    }

    private void bindSynopsis(String synopsis) {
        String normalized = safe(buildTwoParagraphSynopsis(synopsis), "Sinopsis belum tersedia dari API.");
        fullSynopsis = normalized;

        if (normalized.length() > SYNOPSIS_COLLAPSED_LENGTH) {
            collapsedSynopsis = normalized.substring(0, SYNOPSIS_COLLAPSED_LENGTH).trim() + "...";
            synopsisExpanded = false;
            detailSynopsis.setText(collapsedSynopsis);
            synopsisToggle.setVisibility(View.VISIBLE);
            synopsisToggle.setText("Baca Selengkapnya");
        } else {
            collapsedSynopsis = normalized;
            synopsisExpanded = true;
            detailSynopsis.setText(normalized);
            synopsisToggle.setVisibility(View.GONE);
        }
    }

    private void toggleSynopsis() {
        if (isBlank(fullSynopsis) || fullSynopsis.length() <= SYNOPSIS_COLLAPSED_LENGTH) {
            return;
        }

        synopsisExpanded = !synopsisExpanded;
        detailSynopsis.setText(synopsisExpanded ? fullSynopsis : collapsedSynopsis);
        synopsisToggle.setText(synopsisExpanded ? "Tampilkan Ringkas" : "Baca Selengkapnya");
    }

    private void applyEpisodeList() {
        int total = fullEpisodes.size();
        episodeHeaderTitle.setText("Daftar Episode (" + total + " Episode)");

        if (total == 0) {
            episodeAdapter.setData(Collections.emptyList());
            viewAllEpisodesButton.setVisibility(View.GONE);
            return;
        }

        if (total <= INITIAL_EPISODE_VISIBLE_COUNT) {
            episodeAdapter.setData(fullEpisodes);
            viewAllEpisodesButton.setVisibility(View.GONE);
            return;
        }

        if (showingAllEpisodes) {
            episodeAdapter.setData(fullEpisodes);
            viewAllEpisodesButton.setText("Tampilkan Lebih Sedikit");
        } else {
            episodeAdapter.setData(fullEpisodes.subList(0, INITIAL_EPISODE_VISIBLE_COUNT));
            viewAllEpisodesButton.setText("Lihat Semua Episode");
        }

        viewAllEpisodesButton.setVisibility(View.VISIBLE);
    }

    private void toggleEpisodeList() {
        showingAllEpisodes = !showingAllEpisodes;
        applyEpisodeList();
    }

    private void loadBanner(String url) {
        Glide.with(this)
            .load(url)
            .placeholder(R.drawable.placeholder_banner)
            .error(R.drawable.placeholder_banner)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
            .into(detailBanner);
    }

    private void loadPoster(String url) {
        Glide.with(this)
            .load(url)
            .placeholder(R.drawable.placeholder_anime)
            .error(R.drawable.placeholder_anime)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
            .into(detailPoster);
    }

    private void toggleWatchlist() {
        if (currentAnime == null) {
            return;
        }

        if (repository.isInWatchlist(currentAnime)) {
            repository.removeFromWatchlist(currentAnime);
            Toast.makeText(this, "Dihapus dari watchlist", Toast.LENGTH_SHORT).show();
        } else {
            repository.addToWatchlist(currentAnime);
            Toast.makeText(this, "Ditambahkan ke watchlist", Toast.LENGTH_SHORT).show();
        }
        updateWatchlistButton();
    }

    private void updateWatchlistButton() {
        if (currentAnime == null) {
            watchlistButton.setText("+ Watchlist");
            headerWatchlistButton.setImageResource(R.drawable.ic_bookmark_outline);
            return;
        }

        boolean inWatchlist = repository.isInWatchlist(currentAnime);
        watchlistButton.setText(inWatchlist ? "Hapus Watchlist" : "+ Watchlist");
        headerWatchlistButton.setImageResource(inWatchlist ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_outline);
    }

    private void openPrimaryPlayback() {
        Episode target = findBestEpisodeToPlay();
        if (target == null) {
            Toast.makeText(this, "Episode belum tersedia", Toast.LENGTH_SHORT).show();
            return;
        }
        openPlayer(target.getSlug());
    }

    private Episode findBestEpisodeToPlay() {
        if (fullEpisodes.isEmpty()) {
            return null;
        }

        if (currentAnime != null) {
            WatchHistoryItem latest = repository.getLatestHistoryForAnime(currentAnime.getSlug());
            if (latest != null && !isBlank(latest.getEpisodeSlug())) {
                for (Episode item : fullEpisodes) {
                    if (latest.getEpisodeSlug().equals(item.getSlug()) && item.isReleased()) {
                        return item;
                    }
                }
            }
        }

        for (Episode item : fullEpisodes) {
            if (item.isReleased()) {
                return item;
            }
        }

        return null;
    }

    private void openPlayerEpisode(Episode episode) {
        if (episode == null || isBlank(episode.getSlug())) {
            return;
        }
        if (!episode.isReleased()) {
            Toast.makeText(this, "Episode ini belum rilis", Toast.LENGTH_SHORT).show();
            return;
        }
        openPlayer(episode.getSlug());
    }

    private void openPlayer(String episodeSlug) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("anime_slug", safe(currentAnime != null ? currentAnime.getSlug() : animeSlug, animeSlug));
        intent.putExtra("episode_slug", episodeSlug);
        startActivity(intent);
    }

    private void openDownloadFlow() {
        Episode target = findBestEpisodeToPlay();
        if (target == null) {
            Toast.makeText(this, "Episode belum tersedia untuk diunduh", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Membuka player. Gunakan tombol Download di halaman player.", Toast.LENGTH_SHORT).show();
        openPlayer(target.getSlug());
    }

    private void shareCurrentAnime() {
        if (currentAnime == null) {
            return;
        }

        String shareUrl = safe(currentAnime.getSourceUrl(), "");
        if (isBlank(shareUrl) && !isBlank(currentAnime.getSlug())) {
            shareUrl = "https://otakudesu.best/anime/" + currentAnime.getSlug() + "/";
        }

        String text = "Nonton " + safe(currentAnime.getTitle(), "anime") + " di AniFlow"
            + (isBlank(shareUrl) ? "" : "\n" + shareUrl);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, "Bagikan anime"));
    }

    private void openTrailer() {
        if (currentAnime == null || !currentAnime.hasTrailer()) {
            Toast.makeText(this, "Trailer belum tersedia", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentAnime.getTrailerUrl()));
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Aplikasi pemutar trailer tidak ditemukan", Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWatchlistButton();
    }

    private String buildRating(Anime anime) {
        if (anime == null || anime.getScore() <= 0) {
            return "★ -/10";
        }
        return "★ " + safe(anime.getScoreText(), "-") + "/10";
    }

    private String buildTwoParagraphSynopsis(String raw) {
        String text = safe(raw, "").replaceAll("\\n{3,}", "\n\n").trim();
        if (text.isEmpty()) {
            return "Sinopsis belum tersedia dari API.";
        }

        if (text.contains("\n\n")) {
            return text;
        }

        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (sentences.length < 4) {
            return text;
        }

        int splitIndex = Math.max(1, sentences.length / 2);
        String first = TextUtils.join(" ", java.util.Arrays.asList(sentences).subList(0, splitIndex)).trim();
        String second = TextUtils.join(" ", java.util.Arrays.asList(sentences).subList(splitIndex, sentences.length)).trim();

        if (first.isEmpty() || second.isEmpty()) {
            return text;
        }

        return first + "\n\n" + second;
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

    private String normalizeStatus(String raw) {
        String value = safe(raw, "Unknown").toLowerCase(Locale.US);
        if (value.contains("complete") || value.contains("finish")) {
            return "Completed";
        }
        if (value.contains("ongoing") || value.contains("release")) {
            return "Ongoing";
        }
        if (value.contains("upcoming") || value.contains("soon") || value.contains("preview")) {
            return "Upcoming";
        }
        return "Unknown";
    }

    private static String preferredBanner(@NonNull Anime anime) {
        if (!isBlank(anime.getBannerImage())) {
            return anime.getBannerImage();
        }
        return anime.getCoverImage();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value, String fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        return value.trim();
    }
}
