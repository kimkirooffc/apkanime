package com.aniflow.ui.pages;

import com.aniflow.app.AppState;
import com.aniflow.model.Anime;
import com.aniflow.service.AnimeRepository;
import com.aniflow.ui.components.AnimeCard;
import com.aniflow.util.ImageUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class HomePage extends StackPane {
    private final AppState state;
    private final AnimeRepository repository;
    private final Consumer<Anime> onPlay;

    private final ImageView bannerImage = new ImageView();
    private final Label bannerTitle = new Label("Loading...");
    private final Label bannerSubtitle = new Label("Menyiapkan anime populer");
    private final HBox continueRow = new HBox(14);
    private final HBox topAnimeRow = new HBox(14);
    private final FlowPane recommendationGrid = new FlowPane(14, 14);
    private final ProgressIndicator loading = new ProgressIndicator();
    private final Label pullHint = new Label("Pull to refresh");

    private double dragStart = -1;

    public HomePage(AppState state, AnimeRepository repository, Consumer<Anime> onPlay) {
        this.state = state;
        this.repository = repository;
        this.onPlay = onPlay;

        getStyleClass().add("page-home");

        VBox content = new VBox(22);
        content.setPadding(new Insets(18, 26, 24, 26));

        ScrollPane mainScroll = new ScrollPane(content);
        mainScroll.setFitToWidth(true);
        mainScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mainScroll.getStyleClass().add("transparent-scroll");

        content.getChildren().addAll(
            buildPullHint(),
            buildBanner(),
            buildHorizontalSection("Continue Watching", continueRow),
            buildHorizontalSection("Top Anime", topAnimeRow),
            buildRecommendationSection()
        );

        setupPullToRefresh(mainScroll);
        setupResponsiveGrid();
        setupHistorySync();

        loading.getStyleClass().add("ios-spinner");
        loading.setMaxSize(46, 46);
        loading.setVisible(false);
        StackPane.setAlignment(loading, Pos.CENTER);

        getChildren().addAll(mainScroll, loading);

        refresh();
    }

    public void refresh() {
        loading.setVisible(true);

        CompletableFuture<List<Anime>> trendingFuture = repository.getTrending();
        CompletableFuture<List<Anime>> topFuture = repository.getTopAnime();
        CompletableFuture<List<Anime>> recommendationFuture = repository.getRecommendations();

        CompletableFuture.allOf(trendingFuture, topFuture, recommendationFuture)
            .thenRun(() -> Platform.runLater(() -> {
                List<Anime> trending = trendingFuture.join();
                List<Anime> top = topFuture.join();
                List<Anime> recommendations = recommendationFuture.join();

                applyBanner(trending);
                fillCards(topAnimeRow, top, true);
                fillGrid(recommendationGrid, recommendations.stream().limit(6).toList(), false);
                fillContinueWatching(trending);
                trending.stream().limit(8).forEach(anime -> repository.prefetchDetail(anime.getSlug()));

                loading.setVisible(false);
                pullHint.setText("Pull to refresh");
                pullHint.setTranslateY(0);
            }));
    }

    private Label buildPullHint() {
        pullHint.getStyleClass().add("pull-hint");
        pullHint.setMaxWidth(Double.MAX_VALUE);
        pullHint.setAlignment(Pos.CENTER);
        return pullHint;
    }

    private StackPane buildBanner() {
        StackPane banner = new StackPane();
        banner.getStyleClass().add("trending-banner");
        banner.setPrefHeight(280);

        bannerImage.setPreserveRatio(false);
        bannerImage.fitWidthProperty().bind(banner.widthProperty());
        bannerImage.fitHeightProperty().bind(banner.heightProperty());

        VBox texts = new VBox(10);
        texts.setAlignment(Pos.BOTTOM_LEFT);
        texts.setPadding(new Insets(24));

        bannerTitle.getStyleClass().add("banner-title");
        bannerSubtitle.getStyleClass().add("banner-subtitle");
        texts.getChildren().addAll(bannerTitle, bannerSubtitle);

        Region shade = new Region();
        shade.getStyleClass().add("banner-shade");
        shade.prefWidthProperty().bind(banner.widthProperty());
        shade.prefHeightProperty().bind(banner.heightProperty());

        banner.setOnMouseMoved(event -> {
            double xOffset = (event.getX() / Math.max(1, banner.getWidth()) - 0.5) * 16;
            double yOffset = (event.getY() / Math.max(1, banner.getHeight()) - 0.5) * 10;
            bannerImage.setTranslateX(xOffset);
            bannerImage.setTranslateY(yOffset);
        });

        banner.setOnMouseExited(event -> {
            bannerImage.setTranslateX(0);
            bannerImage.setTranslateY(0);
        });

        banner.getChildren().addAll(bannerImage, shade, texts);
        StackPane.setAlignment(texts, Pos.BOTTOM_LEFT);
        return banner;
    }

    private VBox buildHorizontalSection(String title, HBox row) {
        row.setAlignment(Pos.CENTER_LEFT);

        Label sectionTitle = new Label(title);
        sectionTitle.getStyleClass().add("section-title");

        ScrollPane scroller = new ScrollPane(row);
        scroller.setFitToHeight(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.getStyleClass().add("horizontal-scroll");

        return new VBox(12, sectionTitle, scroller);
    }

    private VBox buildRecommendationSection() {
        recommendationGrid.setAlignment(Pos.TOP_LEFT);

        Label sectionTitle = new Label("Rekomendasi");
        sectionTitle.getStyleClass().add("section-title");

        VBox wrapper = new VBox(12, sectionTitle, recommendationGrid);
        VBox.setVgrow(recommendationGrid, Priority.ALWAYS);
        return wrapper;
    }

    private void setupPullToRefresh(ScrollPane scrollPane) {
        scrollPane.setOnMousePressed(event -> {
            if (scrollPane.getVvalue() <= 0.01) {
                dragStart = event.getSceneY();
            }
        });

        scrollPane.setOnMouseDragged(event -> {
            if (dragStart < 0 || scrollPane.getVvalue() > 0.01) {
                return;
            }
            double delta = event.getSceneY() - dragStart;
            if (delta > 0) {
                pullHint.setTranslateY(Math.min(42, delta / 3));
                pullHint.setText(delta > 80 ? "Release to refresh" : "Pull to refresh");
            }
        });

        scrollPane.setOnMouseReleased(event -> {
            if (pullHint.getTranslateY() > 20) {
                repository.clearCache();
                refresh();
            }
            dragStart = -1;
            pullHint.setTranslateY(0);
        });
    }

    private void setupResponsiveGrid() {
        widthProperty().addListener((obs, oldValue, newValue) -> {
            double width = newValue.doubleValue();
            recommendationGrid.setPrefWrapLength(width < 1000 ? 620 : 980);
        });
    }

    private void setupHistorySync() {
        state.getHistory().addListener((javafx.collections.ListChangeListener<Anime>) change -> fillContinueWatching(List.of()));
    }

    private void applyBanner(List<Anime> trending) {
        if (trending.isEmpty()) {
            bannerTitle.setText("Offline mode");
            bannerSubtitle.setText("Menampilkan cache lokal");
            bannerImage.setImage(null);
            return;
        }

        Anime top = trending.get(0);
        bannerTitle.setText(top.getTitle());

        String description = top.getDescription();
        if (description.length() > 140) {
            description = description.substring(0, 140) + "...";
        }
        bannerSubtitle.setText(description);

        Image image = ImageUtil.safeImage(top.getBannerImage());
        bannerImage.setImage(image);
    }

    private void fillContinueWatching(List<Anime> fallback) {
        List<Anime> data = state.getHistory().isEmpty()
            ? fallback.stream().limit(8).toList()
            : state.getHistory().stream().limit(8).toList();
        fillCards(continueRow, data, true);
    }

    private void fillCards(HBox row, List<Anime> list, boolean compact) {
        row.getChildren().clear();
        list.forEach(anime -> {
            AnimeCard card = new AnimeCard(anime, state, compact);
            card.setOnMouseEntered(event -> repository.prefetchDetail(anime.getSlug()));
            card.setOnMouseClicked(event -> onPlay.accept(anime));
            row.getChildren().add(card);
        });
    }

    private void fillGrid(FlowPane grid, List<Anime> list, boolean compact) {
        grid.getChildren().clear();
        list.forEach(anime -> {
            AnimeCard card = new AnimeCard(anime, state, compact);
            card.setOnMouseEntered(event -> repository.prefetchDetail(anime.getSlug()));
            card.setOnMouseClicked(event -> onPlay.accept(anime));
            grid.getChildren().add(card);
        });
    }
}
