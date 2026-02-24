package com.aniflow.ui.pages;

import com.aniflow.app.AppState;
import com.aniflow.model.Anime;
import com.aniflow.model.AnimeDetail;
import com.aniflow.model.Genre;
import com.aniflow.model.SearchFilter;
import com.aniflow.service.AnimeRepository;
import com.aniflow.ui.components.AnimeCard;
import com.aniflow.ui.components.GlassSearchBar;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

public class SearchPage extends StackPane {
    private final AppState state;
    private final AnimeRepository repository;
    private final Consumer<Anime> onPlay;

    private final GlassSearchBar searchBar;
    private final ComboBox<String> genreFilter;
    private final ComboBox<String> seasonFilter;
    private final ComboBox<String> statusFilter;
    private final FlowPane resultGrid;
    private final ProgressIndicator loading;
    private final PauseTransition debounce;

    public SearchPage(AppState state, AnimeRepository repository, Consumer<Anime> onPlay) {
        this.state = state;
        this.repository = repository;
        this.onPlay = onPlay;

        getStyleClass().add("page-search");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 24, 24));

        searchBar = new GlassSearchBar();

        genreFilter = makeChipCombo("Genre", "All");
        seasonFilter = makeChipCombo("Season", "Any", "Any", "WINTER", "SPRING", "SUMMER", "FALL");
        statusFilter = makeChipCombo("Status", "Any", "Any", "ONGOING", "FINISHED", "UPCOMING");

        HBox chips = new HBox(10, genreFilter, seasonFilter, statusFilter);
        chips.setAlignment(Pos.CENTER_LEFT);

        resultGrid = new FlowPane(14, 14);
        resultGrid.setPrefWrapLength(980);

        ScrollPane scrollPane = new ScrollPane(resultGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("transparent-scroll");

        Label hint = new Label("Cari anime atau pilih genre");
        hint.getStyleClass().add("subtle-text");

        content.getChildren().addAll(searchBar, chips, hint, scrollPane);

        loading = new ProgressIndicator();
        loading.getStyleClass().add("ios-spinner");
        loading.setVisible(false);

        debounce = new PauseTransition(Duration.millis(300));
        debounce.setOnFinished(event -> runSearch());

        setupSearchEvents();
        loadGenres();

        getChildren().addAll(content, loading);
        StackPane.setAlignment(loading, Pos.CENTER);

        widthProperty().addListener((obs, oldValue, newValue) -> {
            double w = newValue.doubleValue();
            resultGrid.setPrefWrapLength(w < 1000 ? 660 : 980);
        });

        runSearch();
    }

    private ComboBox<String> makeChipCombo(String prompt, String defaultValue, String... values) {
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(values));
        combo.setPromptText(prompt);
        combo.setValue(defaultValue);
        combo.getStyleClass().add("chip-filter");
        combo.setOnAction(event -> runSearch());
        return combo;
    }

    private void loadGenres() {
        repository.getGenres().thenAccept(genres -> Platform.runLater(() -> {
            List<String> names = genres.stream().map(Genre::name).toList();
            genreFilter.setItems(FXCollections.observableArrayList());
            genreFilter.getItems().add("All");
            genreFilter.getItems().addAll(names);
            genreFilter.setValue("All");
        }));
    }

    private void setupSearchEvents() {
        searchBar.getTextField().textProperty().addListener((obs, oldValue, newValue) -> debounce.playFromStart());
        searchBar.getTextField().setOnAction(event -> runSearch());
    }

    private void runSearch() {
        String query = searchBar.getTextField().getText();
        SearchFilter filter = new SearchFilter(genreFilter.getValue(), seasonFilter.getValue(), statusFilter.getValue());

        loading.setVisible(true);
        repository.search(query, filter)
            .thenAccept(list -> Platform.runLater(() -> {
                renderResults(list);
                loading.setVisible(false);
            }));
    }

    private void renderResults(List<Anime> data) {
        resultGrid.getChildren().clear();
        if (data == null || data.isEmpty()) {
            Label empty = new Label("Tidak ada hasil");
            empty.getStyleClass().add("subtle-text");
            resultGrid.getChildren().add(empty);
            return;
        }

        data.forEach(anime -> {
            AnimeCard card = new AnimeCard(anime, state, true);
            card.setOnMouseEntered(event -> previewDetail(card, anime));
            card.setOnMouseClicked(event -> onPlay.accept(anime));
            resultGrid.getChildren().add(card);
        });

        data.stream().limit(8).forEach(anime -> repository.prefetchDetail(anime.getSlug()));
    }

    private void previewDetail(AnimeCard card, Anime anime) {
        repository.getAnimeDetail(anime.getSlug()).thenAccept(detail -> Platform.runLater(() -> {
            AnimeDetail safe = detail;
            if (safe.getAnime() == null) {
                return;
            }

            String synopsis = safe.getAnime().getDescription();
            if (synopsis.length() > 180) {
                synopsis = synopsis.substring(0, 180) + "...";
            }

            String preview = safe.getAnime().getTitle() + "\n"
                + safe.getAnime().getStatus() + " • " + safe.getAnime().getScoreText() + "\n"
                + synopsis;
            card.setPreviewText(preview);
        }));
    }
}
