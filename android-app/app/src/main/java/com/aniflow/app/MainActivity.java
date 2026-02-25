package com.aniflow.app;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.aniflow.model.Anime;
import com.aniflow.model.HomeRailItem;
import com.aniflow.model.WatchHistoryItem;
import com.aniflow.service.Repository;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final int UPDATE_NOTIFICATION_ID = 4201;

    private Repository repository;

    private TrendingBannerAdapter bannerAdapter;
    private ContinueWatchingAdapter continueWatchingAdapter;
    private ContinueWatchingAdapter animeQuickAdapter;
    private TopAnimeAdapter topAnimeAdapter;
    private TopAnimeAdapter mixFeedAdapter;

    private SwipeRefreshLayout swipeRefresh;
    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNav;
    private EditText searchInput;
    private RecyclerView bannerRecycler;
    private RecyclerView continueWatchingRecycler;
    private RecyclerView animeQuickRecycler;
    private RecyclerView topAnimeRecycler;
    private RecyclerView mixFeedRecycler;
    private TextView clockText;
    private TextView homeGreetingText;
    private TextView homeDateText;
    private TextView continueSectionTitle;
    private TextView animeQuickSectionTitle;
    private TextView mixFeedSectionTitle;
    private ImageView continueSectionIcon;

    private View loadingState;
    private View emptyState;
    private View errorState;
    private Button retryButton;

    private final List<Anime> trendingList = new ArrayList<>();
    private final List<Anime> topList = new ArrayList<>();
    private final List<Anime> mixedCatalogList = new ArrayList<>();

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private ValueAnimator loadingAnimator;
    private LinearLayoutManager bannerLayoutManager;
    private int currentBannerPosition = 0;
    private int pendingRequests = 0;
    private boolean hasRequestError = false;
    private boolean updateDialogVisible = false;
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault());

    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            refreshHeaderInfo();
            uiHandler.postDelayed(this, 30_000);
        }
    };

    private final Runnable bannerAutoSlide = new Runnable() {
        @Override
        public void run() {
            if (bannerAdapter != null && bannerAdapter.size() > 1) {
                currentBannerPosition = (currentBannerPosition + 1) % bannerAdapter.size();
                bannerRecycler.smoothScrollToPosition(currentBannerPosition);
                uiHandler.postDelayed(this, 4500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_AniFlow);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeManager.applyAmoledSurfaceIfNeeded(this);

        repository = new Repository(this);

        initViews();
        setupRecyclerViews();
        setupListeners();
        loadData();
        checkForAppUpdate(false);
    }

    private void initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        bottomNav = findViewById(R.id.bottomNav);
        searchInput = findViewById(R.id.searchInput);
        bannerRecycler = findViewById(R.id.bannerRecycler);
        continueWatchingRecycler = findViewById(R.id.continueWatchingRecycler);
        animeQuickRecycler = findViewById(R.id.animeQuickRecycler);
        topAnimeRecycler = findViewById(R.id.topAnimeRecycler);
        mixFeedRecycler = findViewById(R.id.mixFeedRecycler);
        clockText = findViewById(R.id.clockText);
        homeGreetingText = findViewById(R.id.homeGreetingText);
        homeDateText = findViewById(R.id.homeDateText);
        continueSectionTitle = findViewById(R.id.continueSectionTitle);
        animeQuickSectionTitle = findViewById(R.id.animeQuickSectionTitle);
        mixFeedSectionTitle = findViewById(R.id.mixFeedSectionTitle);
        continueSectionIcon = findViewById(R.id.continueSectionIcon);

        loadingState = findViewById(R.id.loadingState);
        emptyState = findViewById(R.id.emptyState);
        errorState = findViewById(R.id.errorState);
        retryButton = findViewById(R.id.retryButton);
        refreshHeaderInfo();
    }

    private void setupRecyclerViews() {
        bannerAdapter = new TrendingBannerAdapter(this, this::openDetail);
        bannerLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        bannerRecycler.setLayoutManager(bannerLayoutManager);
        bannerRecycler.setAdapter(bannerAdapter);
        bannerRecycler.setHasFixedSize(true);

        PagerSnapHelper bannerSnapHelper = new PagerSnapHelper();
        bannerSnapHelper.attachToRecyclerView(bannerRecycler);
        bannerRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int firstVisible = bannerLayoutManager.findFirstCompletelyVisibleItemPosition();
                    if (firstVisible == RecyclerView.NO_POSITION) {
                        firstVisible = bannerLayoutManager.findFirstVisibleItemPosition();
                    }
                    if (firstVisible != RecyclerView.NO_POSITION) {
                        currentBannerPosition = firstVisible;
                    }
                }
            }
        });

        continueWatchingAdapter = new ContinueWatchingAdapter(this, this::openDetail);
        continueWatchingRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        continueWatchingRecycler.setAdapter(continueWatchingAdapter);

        animeQuickAdapter = new ContinueWatchingAdapter(this, this::openDetail);
        animeQuickRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        animeQuickRecycler.setAdapter(animeQuickAdapter);

        topAnimeAdapter = new TopAnimeAdapter(this, this::openDetail, true);
        topAnimeRecycler.setLayoutManager(new LinearLayoutManager(this));
        topAnimeRecycler.setAdapter(topAnimeAdapter);
        topAnimeRecycler.addItemDecoration(new GridSpacingDecoration(dp(10), 1));

        mixFeedAdapter = new TopAnimeAdapter(this, this::openDetail, false);
        mixFeedRecycler.setLayoutManager(new LinearLayoutManager(this));
        mixFeedRecycler.setAdapter(mixFeedAdapter);
        mixFeedRecycler.addItemDecoration(new GridSpacingDecoration(dp(10), 1));
    }

    private void setupListeners() {
        swipeRefresh.setOnRefreshListener(this::loadData);
        retryButton.setOnClickListener(v -> loadData());
        NavigationHelper.bind(bottomNav, this, R.id.nav_home);

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            String query = searchInput.getText().toString().trim();
            openSearch(query);
            return true;
        });

        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                openSearch(searchInput.getText().toString().trim());
            }
        });
    }

    private void loadData() {
        pendingRequests = 3;
        hasRequestError = false;
        showLoading(true);
        hideStateViews();

        repository.getTrending(new Repository.Callback<List<Anime>>() {
            @Override
            public void onSuccess(List<Anime> result) {
                trendingList.clear();
                if (result != null) {
                    trendingList.addAll(result);
                }

                bannerAdapter.setData(trendingList);
                updateContinueSection();
                updateExtraSections();
                markRequestComplete(false);
            }

            @Override
            public void onFailure(Throwable error) {
                markRequestComplete(true);
            }
        });

        repository.getTopRated(new Repository.Callback<List<Anime>>() {
            @Override
            public void onSuccess(List<Anime> result) {
                topList.clear();
                if (result != null) {
                    topList.addAll(result);
                }
                topAnimeAdapter.setData(topList);
                updateContinueSection();
                updateExtraSections();
                markRequestComplete(false);
            }

            @Override
            public void onFailure(Throwable error) {
                markRequestComplete(true);
            }
        });

        repository.search("", new Repository.Callback<List<Anime>>() {
            @Override
            public void onSuccess(List<Anime> result) {
                mixedCatalogList.clear();
                if (result != null) {
                    mixedCatalogList.addAll(result);
                }
                updateExtraSections();
                markRequestComplete(false);
            }

            @Override
            public void onFailure(Throwable error) {
                markRequestComplete(true);
            }
        });
    }

    private void updateContinueSection() {
        List<WatchHistoryItem> historyItems = repository.getContinueWatchingItems(8);
        List<HomeRailItem> rail = new ArrayList<>();

        if (!historyItems.isEmpty()) {
            continueSectionTitle.setText("Continue Watching");
            continueSectionIcon.setImageResource(R.drawable.ic_play_circle);
            continueSectionIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_continue)));

            for (WatchHistoryItem item : historyItems) {
                Anime anime = item.toAnime();
                String subtitle = item.getEpisodeNumber() > 0
                    ? "Episode " + item.getEpisodeNumber()
                    : normalizeEpisodeLabel(item.getEpisodeTitle(), "Latest Episode");
                rail.add(new HomeRailItem(anime, subtitle, item.getProgressPercent(), true));
            }
        } else {
            continueSectionTitle.setText("Recommended");
            continueSectionIcon.setImageResource(R.drawable.ic_star);
            continueSectionIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.icon_recommend)));

            List<Anime> freshAnime = !topList.isEmpty() ? limit(topList, 8) : limit(trendingList, 8);
            for (Anime anime : freshAnime) {
                String subtitle = normalizeEpisodeLabel(anime.getEpisodeLabel(), "Latest Episode");
                rail.add(new HomeRailItem(anime, subtitle, 0, false));
            }
        }

        continueWatchingAdapter.setData(rail);
        continueSectionTitle.setText(continueSectionTitle.getText() + " (" + rail.size() + ")");
    }

    private void updateExtraSections() {
        List<HomeRailItem> animeRail = toRailItems(limit(trendingList, 12), "Latest Episode");
        animeQuickAdapter.setData(animeRail);
        animeQuickSectionTitle.setText("Ongoing Anime (" + animeRail.size() + ")");

        List<Anime> mergedFeed = mergeUniqueAnime(trendingList, topList, mixedCatalogList);
        List<Anime> visibleFeed = limit(mergedFeed, 20);
        mixFeedAdapter.setData(visibleFeed);
        mixFeedSectionTitle.setText("Mixed Feed (" + visibleFeed.size() + ")");
    }

    private void markRequestComplete(boolean failed) {
        if (failed) {
            hasRequestError = true;
        }

        pendingRequests = Math.max(0, pendingRequests - 1);
        if (pendingRequests > 0) {
            return;
        }

        updateContinueSection();
        swipeRefresh.setRefreshing(false);
        showLoading(false);

        boolean hasData = !bannerAdapter.isEmpty()
            || !continueWatchingAdapter.isEmpty()
            || !animeQuickAdapter.isEmpty()
            || !topAnimeAdapter.isEmpty()
            || !mixFeedAdapter.isEmpty();
        if (hasData) {
            hideStateViews();
            startAutoSlide();
            return;
        }

        if (hasRequestError) {
            errorState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.VISIBLE);
        }
        stopAutoSlide();
    }

    private void hideStateViews() {
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
    }

    private void showLoading(boolean show) {
        loadingState.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            startLoadingAnimation();
        } else {
            stopLoadingAnimation();
        }
    }

    private void startLoadingAnimation() {
        if (loadingAnimator != null && loadingAnimator.isRunning()) {
            return;
        }

        loadingAnimator = ValueAnimator.ofFloat(0.55f, 1f);
        loadingAnimator.setDuration(850);
        loadingAnimator.setRepeatMode(ValueAnimator.REVERSE);
        loadingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        loadingAnimator.addUpdateListener(animation -> loadingState.setAlpha((float) animation.getAnimatedValue()));
        loadingAnimator.start();
    }

    private void stopLoadingAnimation() {
        if (loadingAnimator != null) {
            loadingAnimator.cancel();
            loadingAnimator = null;
        }
        loadingState.setAlpha(1f);
    }

    private void openDetail(Anime anime) {
        Intent intent = new Intent(this, AnimeDetailActivity.class);
        intent.putExtra("anime_slug", anime.getSlug());
        intent.putExtra("anime_title", anime.getTitle());
        intent.putExtra("anime_cover", anime.getCoverImage());
        startActivity(intent);
    }

    private void openSearch(String query) {
        Intent intent = new Intent(this, SearchActivity.class);
        intent.putExtra("query", query == null ? "" : query);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiHandler.post(clockTicker);
        startAutoSlide();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
        updateContinueSection();
        updateExtraSections();
        checkForAppUpdate(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(clockTicker);
        stopAutoSlide();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLoadingAnimation();
        uiHandler.removeCallbacksAndMessages(null);
    }

    private void startAutoSlide() {
        stopAutoSlide();
        if (!bannerAdapter.isEmpty() && bannerAdapter.size() > 1) {
            uiHandler.postDelayed(bannerAutoSlide, 4500);
        }
    }

    private void stopAutoSlide() {
        uiHandler.removeCallbacks(bannerAutoSlide);
    }

    private List<Anime> limit(List<Anime> source, int max) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(source.subList(0, Math.min(max, source.size())));
    }

    private List<HomeRailItem> toRailItems(List<Anime> source, String fallbackSubtitle) {
        List<HomeRailItem> rail = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return rail;
        }

        for (Anime anime : source) {
            if (anime == null) {
                continue;
            }
            String subtitle = normalizeEpisodeLabel(anime.getEpisodeLabel(), fallbackSubtitle);
            rail.add(new HomeRailItem(anime, subtitle, 0, false));
        }
        return rail;
    }

    @SafeVarargs
    private final List<Anime> mergeUniqueAnime(List<Anime>... groups) {
        Map<String, Anime> merged = new LinkedHashMap<>();
        if (groups == null) {
            return new ArrayList<>();
        }

        for (List<Anime> group : groups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            for (Anime anime : group) {
                if (anime == null) {
                    continue;
                }
                String slug = safe(anime.getSlug(), "");
                String key = slug.isEmpty()
                    ? safe(anime.getTitle(), "untitled").toLowerCase(Locale.US)
                    : slug.toLowerCase(Locale.US);
                if (!merged.containsKey(key)) {
                    merged.put(key, anime);
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String normalizeEpisodeLabel(String value, String fallback) {
        String cleaned = safe(value, "")
            .replaceAll("(?i)enienda", "Episode");
        if (cleaned.isEmpty()) {
            return fallback;
        }
        String lower = cleaned.toLowerCase(Locale.US);
        if ("-".equals(cleaned)
            || "unknown".equals(lower)
            || "tba".equals(lower)
            || "n/a".equals(lower)
            || "na".equals(lower)) {
            return fallback;
        }
        return cleaned;
    }

    private void refreshHeaderInfo() {
        Date now = new Date();
        if (clockText != null) {
            clockText.setText(clockFormat.format(now));
        }
        if (homeDateText != null) {
            homeDateText.setText(dateFormat.format(now));
        }
        if (homeGreetingText != null) {
            homeGreetingText.setText(buildGreetingText(now));
        }
    }

    private String buildGreetingText(Date now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        String period;
        if (hour < 11) {
            period = getString(R.string.home_period_morning);
        } else if (hour < 15) {
            period = getString(R.string.home_period_afternoon);
        } else if (hour < 19) {
            period = getString(R.string.home_period_evening);
        } else {
            period = getString(R.string.home_period_night);
        }

        return getString(
            R.string.home_greeting_format,
            period,
            getString(R.string.home_greeting_default_user)
        );
    }

    private void checkForAppUpdate(boolean force) {
        UpdateChecker.check(this, force, new UpdateChecker.Callback() {
            @Override
            public void onUpdateAvailable(UpdateChecker.UpdateInfo info) {
                if (info == null) {
                    return;
                }
                postUpdateNotification(info);

                int lastPrompted = UpdateChecker.lastPromptedVersion(MainActivity.this);
                if (!info.force && lastPrompted >= info.versionCode) {
                    return;
                }
                UpdateChecker.markPromptedVersion(MainActivity.this, info.versionCode);
                showUpdateDialog(info);
            }

            @Override
            public void onNoUpdate() {
            }

            @Override
            public void onFailure(Throwable error) {
            }
        });
    }

    private void showUpdateDialog(UpdateChecker.UpdateInfo info) {
        if (info == null || updateDialogVisible || isFinishing() || isDestroyed()) {
            return;
        }
        updateDialogVisible = true;

        String title = safe(info.title, "Update tersedia");
        String message = safe(info.message, "Versi baru AniFlow tersedia.")
            + "\n\nVersi: " + safe(info.versionName, "latest")
            + " (" + info.versionCode + ")";

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(!info.force)
            .setPositiveButton("Update", (dialog, which) -> openUpdateUrl(info.apkUrl))
            .setOnDismissListener(dialog -> updateDialogVisible = false);

        if (!info.force) {
            builder.setNegativeButton("Nanti", null);
        }
        builder.show();
    }

    private void postUpdateNotification(UpdateChecker.UpdateInfo info) {
        if (info == null || safe(info.apkUrl, "").isEmpty()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent updateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl));
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            2001,
            updateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, AniFlowApplication.CHANNEL_APP_UPDATES)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(safe(info.title, "Update AniFlow tersedia"))
            .setContentText("Versi " + safe(info.versionName, "latest") + " siap di-install")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this).notify(UPDATE_NOTIFICATION_ID, builder.build());
    }

    private void openUpdateUrl(String url) {
        String target = safe(url, "");
        if (target.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private static final class GridSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;
        private final int spanCount;

        GridSpacingDecoration(int spacing, int spanCount) {
            this.spacing = spacing;
            this.spanCount = spanCount;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            int column = position % spanCount;
            outRect.left = column == 0 ? 0 : spacing / 2;
            outRect.right = column == spanCount - 1 ? 0 : spacing / 2;
            outRect.bottom = spacing;
        }
    }
}
