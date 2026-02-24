package com.aniflow.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aniflow.model.Anime;
import com.aniflow.service.Repository;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends AppCompatActivity {

    private Repository repository;
    private EditText searchInput;
    private ChipGroup filterChipGroup;
    private Chip chipAll;
    private Chip chipOngoing;
    private Chip chipCompleted;
    private Chip chipGenre;
    private ProgressBar loadingProgress;
    private TextView emptyText;
    private RecyclerView searchRecycler;
    private BottomNavigationView bottomNav;

    private TopAnimeAdapter adapter;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final List<Anime> sourceData = new ArrayList<>();
    private Runnable searchDebounce;
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_AniFlow);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        ThemeManager.applyAmoledSurfaceIfNeeded(this);

        repository = new Repository(this);
        initViews();
        setupRecycler();
        setupSearchBar();
        setupFilters();
        NavigationHelper.bind(bottomNav, this, R.id.nav_search);

        String initialQuery = getIntent().getStringExtra("query");
        if (initialQuery != null) {
            currentQuery = initialQuery.trim();
            searchInput.setText(currentQuery);
            searchInput.setSelection(searchInput.getText().length());
        }
        fetchSearch(currentQuery);
    }

    private void initViews() {
        searchInput = findViewById(R.id.searchInput);
        filterChipGroup = findViewById(R.id.filterChipGroup);
        chipAll = findViewById(R.id.chipAll);
        chipOngoing = findViewById(R.id.chipOngoing);
        chipCompleted = findViewById(R.id.chipCompleted);
        chipGenre = findViewById(R.id.chipGenre);
        loadingProgress = findViewById(R.id.loadingProgress);
        emptyText = findViewById(R.id.emptyText);
        searchRecycler = findViewById(R.id.searchRecycler);
        bottomNav = findViewById(R.id.bottomNav);
    }

    private void setupRecycler() {
        adapter = new TopAnimeAdapter(this, this::openDetail, false);
        searchRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        searchRecycler.setAdapter(adapter);
        searchRecycler.addItemDecoration(new GridSpacingDecoration(dp(8), 2));
    }

    private void setupSearchBar() {
        searchInput.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                currentQuery = textOf(searchInput);
                fetchSearch(currentQuery);
                return true;
            }
            return false;
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch(s == null ? "" : s.toString());
            }
        });
    }

    private void setupFilters() {
        filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
    }

    private void scheduleSearch(String query) {
        currentQuery = query == null ? "" : query.trim();
        if (searchDebounce != null) {
            uiHandler.removeCallbacks(searchDebounce);
        }
        searchDebounce = () -> fetchSearch(currentQuery);
        uiHandler.postDelayed(searchDebounce, 400);
    }

    private void fetchSearch(String query) {
        loadingProgress.setVisibility(android.view.View.VISIBLE);
        emptyText.setVisibility(android.view.View.GONE);

        repository.search(query, new Repository.Callback<List<Anime>>() {
            @Override
            public void onSuccess(List<Anime> result) {
                loadingProgress.setVisibility(android.view.View.GONE);
                sourceData.clear();
                if (result != null) {
                    sourceData.addAll(result);
                }
                applyFilters();
            }

            @Override
            public void onFailure(Throwable error) {
                loadingProgress.setVisibility(android.view.View.GONE);
                sourceData.clear();
                adapter.setData(sourceData);
                emptyText.setText("Anime / donghua tidak ditemukan");
                emptyText.setVisibility(android.view.View.VISIBLE);
            }
        });
    }

    private void applyFilters() {
        List<Anime> filtered = new ArrayList<>();
        for (Anime anime : sourceData) {
            if (anime == null) {
                continue;
            }
            if (!matchesSelectedFilter(anime)) {
                continue;
            }
            filtered.add(anime);
        }

        adapter.setData(filtered);
        emptyText.setText("Anime / donghua tidak ditemukan");
        emptyText.setVisibility(filtered.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private boolean matchesSelectedFilter(@NonNull Anime anime) {
        int checkedId = filterChipGroup.getCheckedChipId();
        if (checkedId == chipOngoing.getId()) {
            return containsIgnoreCase(anime.getStatus(), "ongoing");
        }
        if (checkedId == chipCompleted.getId()) {
            return containsIgnoreCase(anime.getStatus(), "complete")
                || containsIgnoreCase(anime.getStatus(), "completed")
                || containsIgnoreCase(anime.getStatus(), "finish");
        }
        if (checkedId == chipGenre.getId()) {
            return anime.getGenres() != null && !anime.getGenres().isEmpty();
        }
        if (checkedId == chipAll.getId()) {
            return true;
        }
        return true;
    }

    private void openDetail(Anime anime) {
        Intent intent = new Intent(this, AnimeDetailActivity.class);
        intent.putExtra("anime_slug", anime.getSlug());
        intent.putExtra("anime_title", anime.getTitle());
        intent.putExtra("anime_cover", anime.getCoverImage());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_search);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private static String textOf(EditText input) {
        if (input == null || input.getText() == null) {
            return "";
        }
        return input.getText().toString().trim();
    }

    private static boolean containsIgnoreCase(String source, String needle) {
        if (source == null || needle == null) {
            return false;
        }
        return source.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US));
    }

    private static final class GridSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;
        private final int spanCount;

        GridSpacingDecoration(int spacing, int spanCount) {
            this.spacing = spacing;
            this.spanCount = spanCount;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull android.view.View view,
                                   @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
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
