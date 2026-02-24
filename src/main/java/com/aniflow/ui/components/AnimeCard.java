package com.aniflow.ui.components;

import com.aniflow.app.AppState;
import com.aniflow.model.Anime;
import com.aniflow.util.AnimationUtil;
import com.aniflow.util.ImageUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class AnimeCard extends VBox {
    private final Anime anime;
    private final Tooltip tooltip;

    public AnimeCard(Anime anime, AppState state, boolean compact) {
        this.anime = anime;
        getStyleClass().add("anime-card");
        setSpacing(10);
        setPadding(new Insets(10));

        double width = compact ? 210 : 260;
        double height = compact ? 290 : 340;
        setPrefWidth(width);
        setMaxWidth(width);

        StackPane posterPane = new StackPane();
        posterPane.getStyleClass().add("poster-pane");
        posterPane.setPrefSize(width - 20, height - 90);

        Rectangle clip = new Rectangle(width - 20, height - 90);
        clip.setArcWidth(28);
        clip.setArcHeight(28);

        ImageView poster = new ImageView();
        poster.setFitWidth(width - 20);
        poster.setFitHeight(height - 90);
        poster.setPreserveRatio(false);
        poster.setClip(clip);

        Image image = ImageUtil.safeImage(anime.getCoverImage());
        if (image != null) {
            poster.setImage(image);
            posterPane.getChildren().add(poster);
        } else {
            Label fallback = new Label("ANIME");
            fallback.getStyleClass().add("poster-fallback");
            fallback.setAlignment(Pos.CENTER);
            fallback.setPrefSize(width - 20, height - 90);
            posterPane.getChildren().add(fallback);
        }

        Label scoreBadge = new Label(anime.getScoreText());
        scoreBadge.getStyleClass().add("score-badge");
        StackPane.setAlignment(scoreBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(scoreBadge, new Insets(12));

        posterPane.getChildren().add(scoreBadge);

        Label title = new Label(anime.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        String episodeText = anime.getEpisodeLabel() != null && !anime.getEpisodeLabel().isBlank()
            ? anime.getEpisodeLabel()
            : (anime.getEpisodes() > 0 ? anime.getEpisodes() + " eps" : "TBA");

        Label meta = new Label(anime.getStatus() + " • " + episodeText);
        meta.getStyleClass().add("card-meta");

        Label genres = new Label(String.join(" • ", anime.getGenres().stream().limit(2).toList()));
        genres.getStyleClass().add("card-meta-secondary");

        tooltip = new Tooltip(anime.getDescription());
        tooltip.setWrapText(true);
        tooltip.setPrefWidth(320);
        Tooltip.install(this, tooltip);
        getChildren().addAll(posterPane, title, meta, genres);

        AnimationUtil.applyCardHover(this, state);
    }

    public Anime getAnime() {
        return anime;
    }

    public void setPreviewText(String previewText) {
        if (previewText != null && !previewText.isBlank()) {
            tooltip.setText(previewText);
        }
    }
}
