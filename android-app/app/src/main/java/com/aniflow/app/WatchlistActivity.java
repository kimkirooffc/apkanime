package com.aniflow.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aniflow.model.Anime;
import com.aniflow.service.Repository;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class WatchlistActivity extends AppCompatActivity {

    private Repository repository;
    private RecyclerView watchlistRecycler;
    private LinearLayout emptyState;
    private MaterialButton exploreButton;
    private BottomNavigationView bottomNav;

    private TopAnimeAdapter adapter;
    private final List<Anime> watchlistItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_AniFlow);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watchlist);
        ThemeManager.applyAmoledSurfaceIfNeeded(this);

        repository = new Repository(this);
        initViews();
        setupRecycler();
        setupListeners();
        NavigationHelper.bind(bottomNav, this, R.id.nav_watchlist);
    }

    private void initViews() {
        watchlistRecycler = findViewById(R.id.watchlistRecycler);
        emptyState = findViewById(R.id.emptyState);
        exploreButton = findViewById(R.id.exploreButton);
        bottomNav = findViewById(R.id.bottomNav);
    }

    private void setupRecycler() {
        adapter = new TopAnimeAdapter(this, this::openDetail, false);
        adapter.setOnAnimeLongClickListener(this::removeFromWatchlist);
        watchlistRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        watchlistRecycler.setAdapter(adapter);
        watchlistRecycler.addItemDecoration(new GridSpacingDecoration(dp(8), 2));
    }

    private void setupListeners() {
        exploreButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void openDetail(Anime anime) {
        Intent intent = new Intent(this, AnimeDetailActivity.class);
        intent.putExtra("anime_slug", anime.getSlug());
        intent.putExtra("anime_title", anime.getTitle());
        intent.putExtra("anime_cover", anime.getCoverImage());
        startActivity(intent);
    }

    private void removeFromWatchlist(Anime anime) {
        repository.removeFromWatchlist(anime);
        Toast.makeText(this, "Dihapus dari watchlist", Toast.LENGTH_SHORT).show();
        loadWatchlist();
    }

    private void loadWatchlist() {
        watchlistItems.clear();
        watchlistItems.addAll(repository.getWatchlist());
        adapter.setData(watchlistItems);
        emptyState.setVisibility(watchlistItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_watchlist);
        }
        loadWatchlist();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private static final class GridSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int spacing;
        private final int spanCount;

        GridSpacingDecoration(int spacing, int spanCount) {
            this.spacing = spacing;
            this.spanCount = spanCount;
        }

        @Override
        public void getItemOffsets(@NonNull android.graphics.Rect outRect, @NonNull View view,
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
