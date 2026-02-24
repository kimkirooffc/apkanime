package com.aniflow.app;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aniflow.model.Anime;
import com.aniflow.model.Episode;
import com.aniflow.model.StreamInfo;
import com.aniflow.model.WatchHistoryItem;
import com.aniflow.service.ApiClient;
import com.aniflow.service.Repository;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerActivity extends AppCompatActivity {

    private static final int MAX_EXO_RETRY_COUNT = 2;
    private static final String STATE_FULLSCREEN = "state_fullscreen";
    private static final long DEFAULT_EPISODE_DURATION_MS = 24 * 60 * 1000L;
    private static final Pattern RESOLUTION_PATTERN =
        Pattern.compile("(2160|1440|1080|720|540|480|360|240|144)");
    private static final Pattern EPISODE_NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private PlayerView playerView;
    private WebView webPlayerView;
    private FrameLayout playerContainer;
    private TextView titleText;
    private TextView statusText;
    private TextView currentEpisodeText;
    private TextView errorText;
    private TextView currentTimeText;
    private TextView durationText;
    private ProgressBar loadingProgress;
    private ImageButton playPauseBtn;
    private ImageButton nextBtn;
    private ImageButton prevBtn;
    private ImageButton downloadBtn;
    private Button retryBtn;
    private Spinner resolutionSpinner;
    private SeekBar seekBar;
    private ImageView backBtn;
    private ImageView fullscreenBtn;
    private RecyclerView episodeRecycler;
    private View infoSection;
    private View controlsSection;
    private View episodeHeader;

    private ExoPlayer player;
    private Repository repository;
    private EpisodeAdapter episodeAdapter;
    private ResolutionOptionAdapter resolutionAdapter;
    private DownloadManager downloadManager;

    private String animeSlug;
    private String requestedEpisodeSlug;
    private Anime currentAnime;
    private final List<Episode> episodes = new ArrayList<>();
    private Episode currentEpisode;
    private StreamInfo streamInfo;
    private final List<ResolutionItem> resolutionItems = new ArrayList<>();
    private boolean updatingSpinnerInternally = false;
    private boolean usingWebPlayer = false;
    private String lastRequestedStreamUrl;
    private boolean webFallbackAttempted = false;
    private long webProgressMs = 0L;
    private boolean isFullscreen = false;
    private int defaultPlayerHeightPx = 0;
    private int exoRetryCount = 0;
    private boolean isSeekDragging = false;
    private long lastKnownDurationMs = 0L;
    private long activeDownloadId = -1L;
    private String activeDownloadEpisodeSlug = "";
    private String activeDownloadResolution = "";
    private boolean downloadReceiverRegistered = false;
    private int selectedResolutionIndex = 0;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            reportProgress(false);
            updateSeekbarUi();
            uiHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (downloadId <= 0L) {
                return;
            }
            if (activeDownloadId > 0L && activeDownloadId != downloadId) {
                return;
            }
            onDownloadCompleted(downloadId);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_AniFlow_Player);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        ThemeManager.applyAmoledSurfaceIfNeeded(this);

        repository = new Repository(this);
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        animeSlug = getIntent().getStringExtra("anime_slug");
        requestedEpisodeSlug = getIntent().getStringExtra("episode_slug");

        initViews();
        setupPlayer();
        setupEpisodeList();
        registerDownloadReceiver();

        if (savedInstanceState != null) {
            isFullscreen = savedInstanceState.getBoolean(STATE_FULLSCREEN, false);
            applyFullscreenUiState();
        }

        loadAnime();
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        webPlayerView = findViewById(R.id.webPlayerView);
        playerContainer = findViewById(R.id.playerContainer);
        titleText = findViewById(R.id.titleText);
        statusText = findViewById(R.id.statusText);
        currentEpisodeText = findViewById(R.id.currentEpisodeText);
        errorText = findViewById(R.id.errorText);
        currentTimeText = findViewById(R.id.currentTimeText);
        durationText = findViewById(R.id.durationText);
        loadingProgress = findViewById(R.id.loadingProgress);
        playPauseBtn = findViewById(R.id.playPauseBtn);
        nextBtn = findViewById(R.id.nextBtn);
        prevBtn = findViewById(R.id.prevBtn);
        downloadBtn = findViewById(R.id.downloadBtn);
        retryBtn = findViewById(R.id.retryBtn);
        resolutionSpinner = findViewById(R.id.resolutionSpinner);
        seekBar = findViewById(R.id.seekBar);
        backBtn = findViewById(R.id.backBtn);
        fullscreenBtn = findViewById(R.id.fullscreenBtn);
        episodeRecycler = findViewById(R.id.episodeRecycler);
        infoSection = findViewById(R.id.infoSection);
        controlsSection = findViewById(R.id.controlsSection);
        episodeHeader = findViewById(R.id.episodeHeader);

        defaultPlayerHeightPx = dp(220);

        backBtn.setOnClickListener(v -> finish());
        fullscreenBtn.setOnClickListener(v -> setFullscreenMode(!isFullscreen));
        playPauseBtn.setOnClickListener(v -> togglePlayPause());
        nextBtn.setOnClickListener(v -> playNextEpisode());
        prevBtn.setOnClickListener(v -> playPrevEpisode());
        downloadBtn.setOnClickListener(v -> downloadEpisode());
        retryBtn.setOnClickListener(v -> retryCurrentEpisode());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                updateTimelineText(progress, seekBar.getMax());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeekDragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!usingWebPlayer && player != null) {
                    player.seekTo(seekBar.getProgress());
                }
                isSeekDragging = false;
                updateSeekbarUi();
            }
        });

        resolutionAdapter = new ResolutionOptionAdapter();
        resolutionSpinner.setAdapter(resolutionAdapter);
        resolutionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingSpinnerInternally || streamInfo == null || currentEpisode == null) {
                    return;
                }
                ResolutionItem selected = resolutionAt(position);
                if (selected == null || selected.url == null || selected.url.trim().isEmpty()) {
                    int fallbackIndex = selectedResolutionIndex;
                    if (fallbackIndex < 0 || fallbackIndex >= resolutionItems.size()) {
                        fallbackIndex = firstSelectableResolutionIndex();
                    }
                    if (fallbackIndex >= 0) {
                        updatingSpinnerInternally = true;
                        resolutionSpinner.setSelection(fallbackIndex, false);
                        updatingSpinnerInternally = false;
                    }
                    resolutionAdapter.setSelectedPosition(Math.max(0, fallbackIndex));
                    resolutionAdapter.notifyDataSetChanged();
                    Toast.makeText(PlayerActivity.this, "Resolusi tidak tersedia untuk episode ini", Toast.LENGTH_SHORT).show();
                    return;
                }

                resolutionAdapter.setSelectedPosition(position);
                resolutionAdapter.notifyDataSetChanged();

                String url = selected.url;
                if (lastRequestedStreamUrl != null && lastRequestedStreamUrl.equals(url)) {
                    selectedResolutionIndex = position;
                    return;
                }
                boolean shouldAutoPlay = player != null && player.isPlaying();
                selectedResolutionIndex = position;
                preparePlayback(url, shouldAutoPlay);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        setupWebPlayer();
        applyFullscreenUiState();
        updateSeekbarUi();
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (usingWebPlayer) {
                    return;
                }
                if (playbackState == Player.STATE_BUFFERING) {
                    setLoading(true);
                } else if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    setLoading(false);
                }

                if (playbackState == Player.STATE_READY && player != null) {
                    long duration = player.getDuration();
                    if (duration > 0L) {
                        lastKnownDurationMs = duration;
                    }
                }
                updateSeekbarUi();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseState();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (usingWebPlayer) {
                    return;
                }

                String reason = describePlaybackError(error);
                if (lastRequestedStreamUrl != null
                    && isDirectStream(lastRequestedStreamUrl)
                    && exoRetryCount < MAX_EXO_RETRY_COUNT) {
                    exoRetryCount++;
                    showError("Gangguan stream (" + reason + "), retry " + exoRetryCount + "/" + MAX_EXO_RETRY_COUNT);
                    uiHandler.postDelayed(() -> startExoPlayback(lastRequestedStreamUrl, true), 700);
                    return;
                }

                if (!webFallbackAttempted && lastRequestedStreamUrl != null && !lastRequestedStreamUrl.trim().isEmpty()) {
                    webFallbackAttempted = true;
                    showError("ExoPlayer gagal (" + reason + "), pindah ke web stream");
                    startWebPlayback(lastRequestedStreamUrl);
                    return;
                }
                showError("Video gagal diputar: " + reason);
            }
        });
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupWebPlayer() {
        WebSettings settings = webPlayerView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        webPlayerView.setWebChromeClient(new WebChromeClient());
        webPlayerView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (usingWebPlayer) {
                    setLoading(false);
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    setLoading(false);
                    showError("Stream web gagal dimuat");
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                setLoading(false);
                showError("Stream web gagal dimuat");
            }
        });
    }

    private void setupEpisodeList() {
        episodeAdapter = new EpisodeAdapter(this, episode -> playEpisode(episode, true));
        episodeRecycler.setLayoutManager(new LinearLayoutManager(this));
        episodeRecycler.setAdapter(episodeAdapter);
    }

    private void registerDownloadReceiver() {
        if (downloadReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }
        downloadReceiverRegistered = true;
    }

    private void unregisterDownloadReceiver() {
        if (!downloadReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {
        }
        downloadReceiverRegistered = false;
    }

    private void loadAnime() {
        if (animeSlug == null || animeSlug.trim().isEmpty()) {
            Toast.makeText(this, "Anime tidak valid", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setLoading(true);
        repository.getAnimeDetail(animeSlug, new Repository.Callback<ApiClient.AnimeDetail>() {
            @Override
            public void onSuccess(ApiClient.AnimeDetail result) {
                setLoading(false);
                if (result == null || result.anime == null) {
                    showError("Detail anime tidak tersedia");
                    return;
                }
                currentAnime = result.anime;
                episodes.clear();
                if (result.episodes != null) {
                    episodes.addAll(result.episodes);
                }
                episodeAdapter.setPosterUrl(currentAnime.getCoverImage());
                episodeAdapter.setData(episodes);

                String cleanTitle = cleanMetadataValue(currentAnime.getTitle());
                titleText.setText(cleanTitle.isEmpty() ? "Anime" : cleanTitle);
                renderStatusLine(null);

                Episode initial = resolveInitialEpisode();
                if (initial == null) {
                    showError("Episode belum tersedia");
                    return;
                }
                playEpisode(initial, true);
            }

            @Override
            public void onFailure(Throwable error) {
                setLoading(false);
                showError("Gagal memuat data player");
            }
        });
    }

    private Episode resolveInitialEpisode() {
        Episode fromIntent = findEpisodeBySlug(requestedEpisodeSlug);
        if (fromIntent != null) {
            return fromIntent;
        }
        WatchHistoryItem latest = repository.getLatestHistoryForAnime(animeSlug);
        if (latest != null) {
            Episode fromHistory = findEpisodeBySlug(latest.getEpisodeSlug());
            if (fromHistory != null) {
                return fromHistory;
            }
        }
        return episodes.isEmpty() ? null : episodes.get(0);
    }

    private void playEpisode(Episode episode, boolean autoPlay) {
        if (episode == null || episode.getSlug() == null || episode.getSlug().trim().isEmpty()) {
            showError("Episode tidak valid");
            return;
        }

        currentEpisode = episode;
        currentEpisodeText.setText(labelEpisode(episode));
        episodeAdapter.setSelectedEpisodeSlug(episode.getSlug());
        streamInfo = null;
        updateNavigationButtons();
        showError(null);

        setLoading(true);
        repository.getStreamInfo(episode.getSlug(), new Repository.Callback<StreamInfo>() {
            @Override
            public void onSuccess(StreamInfo result) {
                setLoading(false);
                streamInfo = result;
                bindResolutionOptions(result);
                String url = selectedPlaybackUrl();
                if (url == null) {
                    showError("URL stream tidak tersedia");
                    return;
                }
                preparePlayback(url, autoPlay);
            }

            @Override
            public void onFailure(Throwable error) {
                setLoading(false);
                String detail = error != null ? safe(error.getMessage(), "") : "";
                if (detail.isEmpty()) {
                    showError("Gagal memuat stream episode");
                    return;
                }
                showError("Gagal memuat stream episode: " + detail);
            }
        });
    }

    private void bindResolutionOptions(StreamInfo info) {
        resolutionItems.clear();
        List<String> labels = new ArrayList<>();
        labels.add("360p");
        labels.add("480p");
        labels.add("720p");
        labels.add("1080p");
        labels.add("Auto");

        String autoUrl = null;
        Map<String, String> qualityByLabel = new LinkedHashMap<>();

        if (info != null) {
            autoUrl = firstNonEmpty(info.getStreamingUrls());
            if (autoUrl == null) {
                autoUrl = info.firstPlayableUrl();
            }

            for (Map.Entry<String, List<String>> entry : info.getDownloadUrls().entrySet()) {
                String url = firstNonEmpty(entry.getValue());
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }
                String normalizedLabel = normalizeResolutionLabel(entry.getKey());
                if (isSelectableResolutionLabel(normalizedLabel) && !"Auto".equals(normalizedLabel) && !qualityByLabel.containsKey(normalizedLabel)) {
                    qualityByLabel.put(normalizedLabel, url);
                }
            }
        }

        resolutionItems.add(new ResolutionItem("360p", qualityByLabel.get("360p")));
        resolutionItems.add(new ResolutionItem("480p", qualityByLabel.get("480p")));
        resolutionItems.add(new ResolutionItem("720p", qualityByLabel.get("720p")));
        resolutionItems.add(new ResolutionItem("1080p", qualityByLabel.get("1080p")));
        resolutionItems.add(new ResolutionItem("Auto", autoUrl));

        updatingSpinnerInternally = true;
        resolutionAdapter.clear();
        resolutionAdapter.addAll(labels);
        int fallbackSelectableIndex = firstSelectableResolutionIndex();
        resolutionSpinner.setEnabled(fallbackSelectableIndex >= 0);

        int selectedIndex = fallbackSelectableIndex >= 0 ? fallbackSelectableIndex : 0;
        int autoIndex = resolutionItems.size() - 1;
        ResolutionItem autoItem = resolutionAt(autoIndex);
        if (autoItem != null && autoItem.url != null && !autoItem.url.trim().isEmpty()) {
            selectedIndex = autoIndex;
        }
        if (lastRequestedStreamUrl != null) {
            for (int i = 0; i < resolutionItems.size(); i++) {
                ResolutionItem item = resolutionItems.get(i);
                if (item != null && item.url != null && lastRequestedStreamUrl.equals(item.url)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        selectedResolutionIndex = selectedIndex;
        resolutionAdapter.setSelectedPosition(selectedIndex);
        resolutionAdapter.notifyDataSetChanged();
        resolutionSpinner.setSelection(selectedIndex, false);
        updatingSpinnerInternally = false;
        updateNavigationButtons();
    }

    private String selectedPlaybackUrl() {
        if (resolutionItems.isEmpty()) {
            return streamInfo != null ? streamInfo.firstPlayableUrl() : null;
        }
        ResolutionItem selected = resolutionAt(resolutionSpinner.getSelectedItemPosition());
        if (selected != null && selected.url != null && !selected.url.trim().isEmpty()) {
            return selected.url;
        }
        int fallback = firstSelectableResolutionIndex();
        if (fallback >= 0) {
            return resolutionItems.get(fallback).url;
        }
        return streamInfo != null ? streamInfo.firstPlayableUrl() : null;
    }

    private void preparePlayback(String url, boolean autoPlay) {
        if (url == null || url.trim().isEmpty() || player == null) {
            showError("URL stream tidak valid");
            return;
        }

        lastRequestedStreamUrl = url;
        exoRetryCount = 0;
        webFallbackAttempted = false;

        if (isDirectStream(url)) {
            startExoPlayback(url, autoPlay);
        } else {
            startWebPlayback(url);
        }
    }

    private void startExoPlayback(String url, boolean autoPlay) {
        try {
            switchToExoMode();
            showError(null);
            setLoading(true);

            MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
            String mime = inferMimeType(url);
            if (mime != null) {
                builder.setMimeType(mime);
            }
            MediaItem mediaItem = builder.build();

            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(autoPlay);

            updatePlayPauseState();
            updateSeekbarUi();
            startProgressTicker();
        } catch (Exception e) {
            if (!webFallbackAttempted && lastRequestedStreamUrl != null) {
                webFallbackAttempted = true;
                startWebPlayback(lastRequestedStreamUrl);
            } else {
                showError("Video gagal diputar");
            }
        }
    }

    private void startWebPlayback(String url) {
        switchToWebMode();
        setLoading(true);
        showError(null);
        webProgressMs = 0L;
        updateSeekbarUi();
        webPlayerView.loadUrl(url);
        startProgressTicker();
    }

    private void switchToExoMode() {
        usingWebPlayer = false;
        playerView.setVisibility(View.VISIBLE);
        webPlayerView.setVisibility(View.GONE);

        if (webPlayerView != null) {
            webPlayerView.stopLoading();
            webPlayerView.loadUrl("about:blank");
        }

        playPauseBtn.setEnabled(true);
        seekBar.setEnabled(true);
        renderStatusLine("ExoPlayer");
    }

    private void switchToWebMode() {
        usingWebPlayer = true;
        playerView.setVisibility(View.GONE);
        webPlayerView.setVisibility(View.VISIBLE);

        if (player != null) {
            player.pause();
            player.clearMediaItems();
        }

        playPauseBtn.setEnabled(false);
        seekBar.setEnabled(false);
        renderStatusLine("Web Stream");

        updatePlayPauseState();
    }

    private void togglePlayPause() {
        if (usingWebPlayer) {
            Toast.makeText(this, "Gunakan kontrol di player web", Toast.LENGTH_SHORT).show();
            return;
        }
        if (player == null) {
            return;
        }

        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        updatePlayPauseState();
    }

    private void playNextEpisode() {
        Episode next = episodeAt(currentEpisodeIndex() + 1);
        if (next != null) {
            playEpisode(next, true);
            return;
        }

        Episode fromNavigation = findEpisodeBySlug(streamInfo != null ? streamInfo.getNextEpisodeSlug() : null);
        if (fromNavigation != null) {
            playEpisode(fromNavigation, true);
            return;
        }

        Toast.makeText(this, "Episode selanjutnya tidak tersedia", Toast.LENGTH_SHORT).show();
    }

    private void playPrevEpisode() {
        Episode prev = episodeAt(currentEpisodeIndex() - 1);
        if (prev != null) {
            playEpisode(prev, true);
            return;
        }

        Episode fromNavigation = findEpisodeBySlug(streamInfo != null ? streamInfo.getPrevEpisodeSlug() : null);
        if (fromNavigation != null) {
            playEpisode(fromNavigation, true);
            return;
        }

        Toast.makeText(this, "Episode sebelumnya tidak tersedia", Toast.LENGTH_SHORT).show();
    }

    private void downloadEpisode() {
        List<DownloadItem> downloadItems = buildDownloadItems();
        if (downloadItems.isEmpty()) {
            Toast.makeText(this, "Link download tidak tersedia", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUrl = selectedPlaybackUrl();
        String[] labels = new String[downloadItems.size()];
        int selectedIndex = 0;

        for (int i = 0; i < downloadItems.size(); i++) {
            DownloadItem item = downloadItems.get(i);
            labels[i] = item.label + " (" + inferFileTypeTag(item.url) + ")";
            if (currentUrl != null && currentUrl.equals(item.url)) {
                selectedIndex = i;
            }
        }

        final int fallbackSelection = selectedIndex;
        new AlertDialog.Builder(this)
            .setTitle("Pilih resolusi download")
            .setSingleChoiceItems(labels, selectedIndex, null)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Download", (dialog, which) -> {
                AlertDialog selectedDialog = (AlertDialog) dialog;
                int checked = selectedDialog.getListView().getCheckedItemPosition();
                if (checked < 0 || checked >= downloadItems.size()) {
                    checked = fallbackSelection;
                }
                enqueueDownload(downloadItems.get(checked));
            })
            .show();
    }

    private List<DownloadItem> buildDownloadItems() {
        if (streamInfo == null) {
            return Collections.emptyList();
        }

        LinkedHashMap<String, String> uniqueByUrl = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : streamInfo.getDownloadUrls().entrySet()) {
            String url = firstNonEmpty(entry.getValue());
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            uniqueByUrl.put(url, normalizeResolutionLabel(entry.getKey()));
        }

        for (ResolutionItem item : resolutionItems) {
            if (item == null || item.url == null || item.url.trim().isEmpty()) {
                continue;
            }
            if (!uniqueByUrl.containsKey(item.url)) {
                uniqueByUrl.put(item.url, normalizeResolutionLabel(item.label));
            }
        }

        if (uniqueByUrl.isEmpty()) {
            String fallback = selectedPlaybackUrl();
            if (fallback != null && !fallback.trim().isEmpty()) {
                uniqueByUrl.put(fallback, "Default");
            }
        }

        List<DownloadItem> downloadItems = new ArrayList<>();
        for (Map.Entry<String, String> entry : uniqueByUrl.entrySet()) {
            downloadItems.add(new DownloadItem(entry.getValue(), entry.getKey()));
        }

        downloadItems.sort((left, right) -> {
            int leftRes = parseResolution(left.label);
            int rightRes = parseResolution(right.label);
            boolean leftKnown = leftRes != Integer.MAX_VALUE;
            boolean rightKnown = rightRes != Integer.MAX_VALUE;

            if (leftKnown && rightKnown) {
                return Integer.compare(rightRes, leftRes);
            }
            if (leftKnown) {
                return -1;
            }
            if (rightKnown) {
                return 1;
            }
            return left.label.compareToIgnoreCase(right.label);
        });

        return downloadItems;
    }

    private void enqueueDownload(DownloadItem item) {
        if (downloadManager == null || item == null || item.url == null || item.url.trim().isEmpty()) {
            Toast.makeText(this, "Download Manager tidak tersedia", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri uri = Uri.parse(item.url);
            DownloadManager.Request request = new DownloadManager.Request(uri);

            String title = safe(currentAnime != null ? currentAnime.getTitle() : "Anime", "Anime");
            String episodeLabel = currentEpisode != null ? labelEpisode(currentEpisode) : "Episode";
            String fileName = buildDownloadFilename(item);

            request.setTitle(title + " • " + episodeLabel);
            request.setDescription("Resolusi " + item.label);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setVisibleInDownloadsUi(true);

            String mime = inferDownloadMimeType(item.url);
            if (mime != null) {
                request.setMimeType(mime);
            }

            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            activeDownloadId = downloadManager.enqueue(request);
            activeDownloadEpisodeSlug = safe(currentEpisode != null ? currentEpisode.getSlug() : "", "");
            activeDownloadResolution = safe(item.label, "Auto");

            String destinationPath = Environment.DIRECTORY_DOWNLOADS + "/" + fileName;
            repository.saveDownload(activeDownloadEpisodeSlug, destinationPath, activeDownloadResolution);
            Toast.makeText(this, "Download dimulai: " + item.label, Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException e) {
            showError("Link download tidak valid");
        } catch (Exception e) {
            showError("Gagal memulai download");
        }
    }

    private void onDownloadCompleted(long downloadId) {
        if (downloadManager == null) {
            return;
        }

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = null;
        try {
            cursor = downloadManager.query(query);
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }

            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                String localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                Toast.makeText(this, "Download selesai", Toast.LENGTH_SHORT).show();
                if (localUri != null && !localUri.trim().isEmpty()) {
                    repository.saveDownload(activeDownloadEpisodeSlug, localUri, activeDownloadResolution);
                    showError(null);
                }
            } else if (status == DownloadManager.STATUS_FAILED) {
                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                String reasonText = mapDownloadFailureReason(reason);
                showError("Download gagal: " + reasonText);
                Toast.makeText(this, "Download gagal", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            showError("Gagal membaca status download");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            activeDownloadId = -1L;
            activeDownloadEpisodeSlug = "";
            activeDownloadResolution = "";
        }
    }

    private String mapDownloadFailureReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "Tidak bisa melanjutkan file";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "Storage tidak ditemukan";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "File sudah ada";
            case DownloadManager.ERROR_FILE_ERROR:
                return "Gagal menulis file";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "Kesalahan data HTTP";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "Storage penuh";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "Terlalu banyak redirect";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "Kode HTTP tidak didukung";
            case DownloadManager.ERROR_UNKNOWN:
            default:
                return "Unknown error";
        }
    }

    private void updateNavigationButtons() {
        int index = currentEpisodeIndex();
        boolean hasPrevByIndex = index > 0;
        boolean hasPrevByNav = streamInfo != null && streamInfo.getPrevEpisodeSlug() != null;
        prevBtn.setEnabled(hasPrevByIndex || hasPrevByNav);

        boolean hasNextByIndex = index >= 0 && index < episodes.size() - 1;
        boolean hasNextByNav = streamInfo != null && streamInfo.getNextEpisodeSlug() != null;
        nextBtn.setEnabled(hasNextByIndex || hasNextByNav);
    }

    private int currentEpisodeIndex() {
        if (currentEpisode == null || currentEpisode.getSlug() == null) {
            return -1;
        }
        for (int i = 0; i < episodes.size(); i++) {
            Episode episode = episodes.get(i);
            if (episode != null && currentEpisode.getSlug().equals(episode.getSlug())) {
                return i;
            }
        }
        return -1;
    }

    private Episode episodeAt(int index) {
        if (index < 0 || index >= episodes.size()) {
            return null;
        }
        return episodes.get(index);
    }

    private Episode findEpisodeBySlug(String slug) {
        if (slug == null || slug.trim().isEmpty()) {
            return null;
        }
        for (Episode episode : episodes) {
            if (episode != null && slug.equals(episode.getSlug())) {
                return episode;
            }
        }
        return null;
    }

    private void retryCurrentEpisode() {
        if (currentEpisode == null) {
            return;
        }
        playEpisode(currentEpisode, true);
    }

    private void updatePlayPauseState() {
        if (usingWebPlayer) {
            playPauseBtn.setImageResource(R.drawable.ic_play_arrow);
            playPauseBtn.setContentDescription("Play");
            return;
        }
        boolean isPlaying = player != null && player.isPlaying();
        playPauseBtn.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        playPauseBtn.setContentDescription(isPlaying ? "Pause" : "Play");
    }

    private void setLoading(boolean loading) {
        loadingProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        if (message == null || message.trim().isEmpty()) {
            errorText.setVisibility(View.GONE);
            retryBtn.setVisibility(View.GONE);
            return;
        }
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
        retryBtn.setVisibility(currentEpisode != null ? View.VISIBLE : View.GONE);
    }

    private void startProgressTicker() {
        uiHandler.removeCallbacks(progressTicker);
        uiHandler.postDelayed(progressTicker, 1000);
    }

    private void stopProgressTicker() {
        uiHandler.removeCallbacks(progressTicker);
    }

    private void reportProgress(boolean force) {
        if (currentAnime == null || currentEpisode == null) {
            return;
        }

        if (usingWebPlayer) {
            if (!force) {
                webProgressMs += 1000L;
            }
            if (webProgressMs > 0L) {
                repository.recordWatchProgress(currentAnime, currentEpisode, webProgressMs, 24 * 60 * 1000L);
            }
            return;
        }

        if (player == null) {
            return;
        }
        if (!force && !player.isPlaying()) {
            return;
        }

        long position = Math.max(0L, player.getCurrentPosition());
        long duration = Math.max(0L, player.getDuration());
        if (position <= 0L) {
            return;
        }
        repository.recordWatchProgress(currentAnime, currentEpisode, position, duration);
    }

    private void updateSeekbarUi() {
        if (seekBar == null || currentTimeText == null) {
            return;
        }

        if (usingWebPlayer) {
            seekBar.setEnabled(false);
            long fallbackDuration = resolveEpisodeDurationMs();
            int max = fallbackDuration > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1L, fallbackDuration);
            seekBar.setMax(max);
            if (!isSeekDragging) {
                seekBar.setProgress((int) Math.min(webProgressMs, max));
            }
            updateTimelineText(webProgressMs, fallbackDuration);
            return;
        }

        if (player == null) {
            seekBar.setEnabled(false);
            if (!isSeekDragging) {
                seekBar.setProgress(0);
            }
            updateTimelineText(0L, resolveEpisodeDurationMs());
            return;
        }

        long position = Math.max(0L, player.getCurrentPosition());
        long duration = player.getDuration();
        if (duration > 0L) {
            lastKnownDurationMs = duration;
        }
        if (duration <= 0L) {
            duration = lastKnownDurationMs;
        }

        if (duration > 0L) {
            int max = duration > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) duration;
            if (max <= 0) {
                max = 1;
            }
            seekBar.setEnabled(true);
            seekBar.setMax(max);
            if (!isSeekDragging) {
                seekBar.setProgress((int) Math.min(position, max));
            }
        } else {
            seekBar.setEnabled(false);
            if (!isSeekDragging) {
                seekBar.setProgress(0);
            }
        }

        long total = duration > 0L ? duration : resolveEpisodeDurationMs();
        if (!isSeekDragging) {
            updateTimelineText(position, total);
        }
    }

    private String describePlaybackError(PlaybackException error) {
        if (error == null) {
            return "unknown error";
        }

        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return "koneksi jaringan";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
            case PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE:
                return "server stream";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FAILED:
                return "decoder video";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED:
                return "format stream";
            default:
                break;
        }

        String message = safe(error.getMessage(), "");
        if (message.isEmpty()) {
            return "error " + error.errorCode;
        }
        if (message.length() > 72) {
            return message.substring(0, 72) + "...";
        }
        return message;
    }

    private String buildDownloadFilename(DownloadItem item) {
        String animePart = sanitizeFilePart(safe(currentAnime != null ? currentAnime.getTitle() : "anime", "anime"));
        String episodePart = sanitizeFilePart(currentEpisode != null ? labelEpisode(currentEpisode) : "episode");
        String qualityPart = sanitizeFilePart(normalizeResolutionLabel(item.label));
        String extension = inferFileExtension(item.url);

        String base = animePart + "-" + episodePart + "-" + qualityPart;
        if (base.length() > 110) {
            base = base.substring(0, 110);
        }
        return base + "." + extension;
    }

    private static String inferFileTypeTag(String url) {
        if (url == null) {
            return "STREAM";
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) {
            return "HLS";
        }
        if (lower.contains(".mp4")) {
            return "MP4";
        }
        if (lower.contains(".mpd")) {
            return "DASH";
        }
        return "STREAM";
    }

    private static String inferDownloadMimeType(String url) {
        if (url == null) {
            return null;
        }

        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (lower.contains(".mp4") || lower.contains("mime=video/mp4")) {
            return "video/mp4";
        }
        if (lower.contains(".mpd")) {
            return "application/dash+xml";
        }
        return null;
    }

    private static String inferFileExtension(String url) {
        if (url == null) {
            return "bin";
        }

        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) {
            return "m3u8";
        }
        if (lower.contains(".mp4") || lower.contains("mime=video/mp4")) {
            return "mp4";
        }
        if (lower.contains(".mpd")) {
            return "mpd";
        }

        Uri uri = Uri.parse(url);
        String segment = uri.getLastPathSegment();
        if (segment != null) {
            int dot = segment.lastIndexOf('.');
            if (dot >= 0 && dot < segment.length() - 1) {
                String ext = segment.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
                if (!ext.isEmpty() && ext.length() <= 5) {
                    return ext.toLowerCase(Locale.US);
                }
            }
        }

        return "bin";
    }

    private static String sanitizeFilePart(String value) {
        String raw = safe(value, "file");
        String sanitized = raw
            .replaceAll("[^A-Za-z0-9._-]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^[_.-]+", "")
            .replaceAll("[_.-]+$", "");

        if (sanitized.isEmpty()) {
            return "file";
        }
        return sanitized;
    }

    private static String normalizeResolutionLabel(String label) {
        String value = safe(label, "Auto");
        if (value.equalsIgnoreCase("auto")) {
            return "Auto";
        }

        Matcher matcher = RESOLUTION_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group(1) + "p";
        }
        return value;
    }

    private static String formatTime(long millis) {
        if (millis < 0L) {
            millis = 0L;
        }

        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startProgressTicker();
        updatePlayPauseState();
        updateSeekbarUi();
        applyFullscreenUiState();
        if (webPlayerView != null) {
            webPlayerView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        reportProgress(true);
        stopProgressTicker();
        if (player != null) {
            player.pause();
        }
        if (webPlayerView != null) {
            webPlayerView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressTicker();
        reportProgress(true);
        unregisterDownloadReceiver();

        if (player != null) {
            player.release();
            player = null;
        }
        if (webPlayerView != null) {
            webPlayerView.stopLoading();
            webPlayerView.loadUrl("about:blank");
            webPlayerView.destroy();
            webPlayerView = null;
        }
        uiHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_FULLSCREEN, isFullscreen);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode(isFullscreen);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig == null) {
            return;
        }

        boolean shouldFullscreen = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (shouldFullscreen != isFullscreen) {
            isFullscreen = shouldFullscreen;
            applyFullscreenUiState();
            return;
        }

        applyPlayerResizeMode();
        applyImmersiveMode(isFullscreen);
    }

    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            setFullscreenMode(false);
            return;
        }
        super.onBackPressed();
    }

    private void setFullscreenMode(boolean enabled) {
        if (isFullscreen == enabled) {
            return;
        }
        isFullscreen = enabled;
        applyFullscreenUiState();
    }

    private void applyFullscreenUiState() {
        if (playerContainer == null) {
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), !isFullscreen);

        ViewGroup.LayoutParams params = playerContainer.getLayoutParams();
        params.height = isFullscreen ? ViewGroup.LayoutParams.MATCH_PARENT : defaultPlayerHeightPx;
        playerContainer.setLayoutParams(params);

        int contentVisibility = isFullscreen ? View.GONE : View.VISIBLE;
        if (infoSection != null) {
            infoSection.setVisibility(contentVisibility);
        }
        if (controlsSection != null) {
            controlsSection.setVisibility(contentVisibility);
        }
        if (episodeHeader != null) {
            episodeHeader.setVisibility(contentVisibility);
        }
        if (episodeRecycler != null) {
            episodeRecycler.setVisibility(contentVisibility);
        }

        if (fullscreenBtn != null) {
            fullscreenBtn.setImageResource(isFullscreen ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
        }

        applyPlayerResizeMode();

        setRequestedOrientation(isFullscreen
            ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        applyImmersiveMode(isFullscreen);
    }

    private void applyPlayerResizeMode() {
        if (playerView == null) {
            return;
        }
        playerView.setResizeMode(
            isFullscreen
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FIT
        );
    }

    private void applyImmersiveMode(boolean immersive) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (immersive) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            }
            return;
        }

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (immersive) {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private static boolean isDirectStream(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".mpd")) {
            return true;
        }
        if (lower.contains("mime=video")) {
            return true;
        }
        return !lower.contains("desustream") && !lower.contains("blogger.com/video.g") && !lower.contains("index.php?id=");
    }

    private static String inferMimeType(String url) {
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".m3u8")) {
            return MimeTypes.APPLICATION_M3U8;
        }
        if (lower.contains(".mpd")) {
            return MimeTypes.APPLICATION_MPD;
        }
        if (lower.contains(".mp4") || lower.contains("mime=video/mp4")) {
            return MimeTypes.VIDEO_MP4;
        }
        return null;
    }

    private static int parseResolution(String label) {
        if (label == null) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = RESOLUTION_PATTERN.matcher(label);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
                return Integer.MAX_VALUE;
            }
        }
        try {
            String digits = label.replaceAll("\\D+", "");
            if (digits.isEmpty()) {
                return Integer.MAX_VALUE;
            }
            return Integer.parseInt(digits);
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static String firstNonEmpty(List<String> urls) {
        if (urls == null) {
            return null;
        }
        for (String url : urls) {
            if (url != null && !url.trim().isEmpty()) {
                return url;
            }
        }
        return null;
    }

    private static String labelEpisode(Episode episode) {
        if (episode == null) {
            return "Episode";
        }
        if (episode.getEpisodeNumber() > 0) {
            return "Episode " + episode.getEpisodeNumber();
        }
        String title = safe(episode.getTitle(), "");
        if (!title.isEmpty()) {
            Matcher matcher = EPISODE_NUMBER_PATTERN.matcher(title);
            if (matcher.find()) {
                return "Episode " + matcher.group(1);
            }
        }
        return "Episode ?";
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private ResolutionItem resolutionAt(int index) {
        if (index < 0 || index >= resolutionItems.size()) {
            return null;
        }
        return resolutionItems.get(index);
    }

    private int firstSelectableResolutionIndex() {
        for (int i = 0; i < resolutionItems.size(); i++) {
            ResolutionItem item = resolutionItems.get(i);
            if (item != null && item.url != null && !item.url.trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSelectableResolutionLabel(String label) {
        return "Auto".equals(label)
            || "360p".equals(label)
            || "480p".equals(label)
            || "720p".equals(label)
            || "1080p".equals(label);
    }

    private long resolveEpisodeDurationMs() {
        if (lastKnownDurationMs > 0L) {
            return lastKnownDurationMs;
        }
        String rawDuration = currentEpisode != null ? currentEpisode.getDuration() : null;
        String digits = rawDuration != null ? rawDuration.replaceAll("\\D+", "") : "";
        if (!digits.isEmpty()) {
            try {
                long minutes = Long.parseLong(digits);
                if (minutes > 0L) {
                    return minutes * 60L * 1000L;
                }
            } catch (Exception ignored) {
            }
        }
        return DEFAULT_EPISODE_DURATION_MS;
    }

    private void updateTimelineText(long positionMs, long totalMs) {
        long safePosition = Math.max(0L, positionMs);
        long safeTotal = Math.max(0L, totalMs);
        if (safeTotal < safePosition) {
            safeTotal = safePosition;
        }
        currentTimeText.setText(formatTime(safePosition) + " / " + formatTime(safeTotal));
    }

    private void renderStatusLine(String playbackMode) {
        if (statusText == null) {
            return;
        }
        List<String> parts = new ArrayList<>();

        String status = cleanMetadataValue(currentAnime != null ? currentAnime.getStatus() : null);
        if (!status.isEmpty()) {
            parts.add(status);
        }

        String rating = cleanMetadataValue(currentAnime != null ? currentAnime.getScoreText() : null);
        if (!rating.isEmpty()) {
            parts.add("Rating " + rating);
        }

        String mode = cleanMetadataValue(playbackMode);
        if (!mode.isEmpty()) {
            parts.add(mode);
        }

        if (parts.isEmpty()) {
            statusText.setVisibility(View.GONE);
            statusText.setText("");
            return;
        }

        statusText.setVisibility(View.VISIBLE);
        statusText.setText(String.join(" • ", parts));
    }

    private static String cleanMetadataValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.US);
        if ("-".equals(trimmed)
            || "unknown".equals(lower)
            || "n/a".equals(lower)
            || "null".equals(lower)) {
            return "";
        }
        return trimmed;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private class ResolutionOptionAdapter extends ArrayAdapter<String> {
        private int selectedPosition = 0;

        ResolutionOptionAdapter() {
            super(PlayerActivity.this, android.R.layout.simple_spinner_item, new ArrayList<>());
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        void setSelectedPosition(int selectedPosition) {
            this.selectedPosition = Math.max(0, selectedPosition);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                styleSpinnerText(textView, position, true);
            }
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                styleSpinnerText(textView, position, false);
            }
            return view;
        }

        private void styleSpinnerText(TextView textView, int position, boolean collapsed) {
            boolean selected = position == selectedPosition;
            ResolutionItem item = resolutionAt(position);
            boolean available = item != null && item.url != null && !item.url.trim().isEmpty();

            textView.setGravity(Gravity.CENTER_VERTICAL);
            textView.setPadding(dp(10), dp(8), dp(10), dp(8));

            if (collapsed) {
                textView.setTextColor(ContextCompat.getColor(PlayerActivity.this, android.R.color.white));
                textView.setBackgroundResource(R.drawable.resolution_selected_bg);
                textView.setAlpha(1f);
                return;
            }

            if (selected) {
                textView.setBackgroundResource(R.drawable.resolution_option_selected_bg);
                textView.setTextColor(ContextCompat.getColor(PlayerActivity.this, android.R.color.white));
                textView.setAlpha(1f);
                return;
            }

            textView.setBackgroundResource(R.drawable.resolution_option_bg);
            textView.setTextColor(ContextCompat.getColor(PlayerActivity.this,
                available ? R.color.onSurface : R.color.onSurfaceVariant));
            textView.setAlpha(available ? 1f : 0.65f);
        }
    }

    private static class ResolutionItem {
        private final String label;
        private final String url;

        private ResolutionItem(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }

    private static class DownloadItem {
        private final String label;
        private final String url;

        private DownloadItem(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }
}
