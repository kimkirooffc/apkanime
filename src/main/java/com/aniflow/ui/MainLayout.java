package com.aniflow.ui;

import com.aniflow.app.AppState;
import com.aniflow.app.Page;
import com.aniflow.model.Anime;
import com.aniflow.service.AnalyticsService;
import com.aniflow.service.AnimeRepository;
import com.aniflow.service.CastService;
import com.aniflow.service.DownloadService;
import com.aniflow.service.PlaybackProgressService;
import com.aniflow.ui.components.DockButton;
import com.aniflow.ui.pages.HomePage;
import com.aniflow.ui.pages.PlayerPage;
import com.aniflow.ui.pages.ProfilePage;
import com.aniflow.ui.pages.SearchPage;
import com.aniflow.util.AnimationUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;

public class MainLayout extends BorderPane {
    private final AppState state;
    private final StackPane contentHost = new StackPane();
    private final Map<Page, Node> pages = new EnumMap<>(Page.class);
    private final Map<Page, DockButton> navButtons = new EnumMap<>(Page.class);

    private final Label largeTitle = new Label("Home");
    private final Label nowPlaying = new Label("No track playing");
    private final Label offlineChip = new Label();

    private final HomePage homePage;
    private final SearchPage searchPage;
    private final PlayerPage playerPage;
    private final ProfilePage profilePage;
    private boolean shutdownTriggered;

    public MainLayout(AppState state,
                      AnimeRepository repository,
                      DownloadService downloadService,
                      CastService castService,
                      AnalyticsService analyticsService,
                      PlaybackProgressService progressService) {
        this.state = state;

        getStyleClass().add("main-shell");

        homePage = new HomePage(state, repository, this::openPlayerForAnime);
        searchPage = new SearchPage(state, repository, this::openPlayerForAnime);
        playerPage = new PlayerPage(state, repository, downloadService, castService, analyticsService, progressService, this::openPlayerForAnime);
        profilePage = new ProfilePage(state);

        pages.put(Page.HOME, homePage);
        pages.put(Page.SEARCH, searchPage);
        pages.put(Page.PLAYER, playerPage);
        pages.put(Page.PROFILE, profilePage);

        setLeft(buildSidebar());
        setTop(buildTopBar());
        setCenter(contentHost);
        setBottom(buildBottomBar());

        contentHost.getChildren().setAll(homePage);

        setupStateBindings();
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(12);
        sidebar.setPadding(new Insets(20, 10, 20, 10));
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.getStyleClass().add("sidebar");

        Label logo = new Label("A");
        logo.getStyleClass().add("logo-badge");

        DockButton home = navButton(Page.HOME, "fas-home", "Home");
        DockButton search = navButton(Page.SEARCH, "fas-search", "Search");
        DockButton player = navButton(Page.PLAYER, "fas-play", "Player");
        DockButton profile = navButton(Page.PROFILE, "fas-user", "Profile");

        sidebar.getChildren().addAll(logo, home, search, player, profile);
        return sidebar;
    }

    private HBox buildTopBar() {
        HBox top = new HBox(12);
        top.setPadding(new Insets(22, 22, 14, 22));
        top.setAlignment(Pos.CENTER_LEFT);

        largeTitle.getStyleClass().add("large-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        offlineChip.getStyleClass().add("offline-chip");
        offlineChip.setVisible(false);

        Button toggleTheme = new Button("Light/Dark");
        toggleTheme.getStyleClass().add("pill-button");
        toggleTheme.setOnAction(event -> state.setDarkMode(!state.isDarkMode()));

        top.getChildren().addAll(largeTitle, spacer, offlineChip, toggleTheme);
        return top;
    }

    private HBox buildBottomBar() {
        HBox bottom = new HBox(12);
        bottom.setPadding(new Insets(12, 16, 12, 16));
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.getStyleClass().add("bottom-player-bar");

        Label icon = new Label("♪");
        icon.getStyleClass().add("player-mini-icon");

        nowPlaying.getStyleClass().add("player-mini-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button openPlayer = new Button("Open Player");
        openPlayer.getStyleClass().add("pill-button");
        openPlayer.setOnAction(event -> state.setCurrentPage(Page.PLAYER));

        bottom.getChildren().addAll(icon, nowPlaying, spacer, openPlayer);
        return bottom;
    }

    private DockButton navButton(Page page, String icon, String tooltip) {
        DockButton button = new DockButton(icon, tooltip, () -> state.setCurrentPage(page));
        navButtons.put(page, button);
        return button;
    }

    private void setupStateBindings() {
        state.currentPageProperty().addListener((obs, oldPage, newPage) -> {
            Node next = pages.get(newPage);
            if (next != null) {
                AnimationUtil.switchPage(contentHost, next, state);
                largeTitle.setText(titleFor(newPage));
                updateNavState(newPage);
            }
        });

        state.currentPlayingAnimeProperty().addListener((obs, oldAnime, anime) -> {
            nowPlaying.setText(anime == null ? "No track playing" : anime.getTitle());
        });

        state.offlineModeProperty().addListener((obs, oldValue, offline) -> {
            offlineChip.setVisible(offline);
            offlineChip.setText(offline ? "Offline (cache)" : "");
        });

        updateNavState(state.getCurrentPage());
        offlineChip.setVisible(state.isOfflineMode());
        if (state.isOfflineMode()) {
            offlineChip.setText("Offline (cache)");
        }
    }

    private String titleFor(Page page) {
        return switch (page) {
            case HOME -> "Home";
            case SEARCH -> "Search";
            case PLAYER -> "Player";
            case PROFILE -> "Profile";
        };
    }

    private void updateNavState(Page active) {
        navButtons.forEach((page, button) -> button.setActive(page == active));
    }

    private void openPlayerForAnime(Anime anime) {
        if (anime == null) {
            return;
        }
        playerPage.openAnime(anime);
        state.setCurrentPage(Page.PLAYER);
    }

    public void shutdown() {
        if (shutdownTriggered) {
            return;
        }
        shutdownTriggered = true;
        playerPage.shutdown();
    }
}
