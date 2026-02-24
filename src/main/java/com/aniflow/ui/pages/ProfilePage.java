package com.aniflow.ui.pages;

import com.aniflow.app.AppState;
import com.aniflow.model.Anime;
import com.jfoenix.controls.JFXToggleButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

public class ProfilePage extends VBox {
    public ProfilePage(AppState state) {
        getStyleClass().add("page-profile");
        setPadding(new Insets(20, 24, 24, 24));
        setSpacing(18);

        Label offlineLabel = new Label(state.isOfflineMode() ? "Koneksi terputus - menampilkan cache" : "Online");
        offlineLabel.getStyleClass().add("offline-banner");
        state.offlineModeProperty().addListener((obs, oldValue, offline) ->
            offlineLabel.setText(offline ? "Koneksi terputus - menampilkan cache" : "Online"));

        getChildren().addAll(
            buildHeader(),
            offlineLabel,
            buildSettings(state),
            buildLibrarySection("Watchlist", state.getWatchlist()),
            buildLibrarySection("History", state.getHistory())
        );
    }

    private HBox buildHeader() {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);

        Circle avatar = new Circle(26);
        avatar.setFill(Color.web("#8B8B8B"));
        avatar.getStyleClass().add("profile-avatar");

        VBox names = new VBox(4);
        Label username = new Label("AniFlow User");
        username.getStyleClass().add("profile-name");

        Label subtitle = new Label("Premium iOS Experience");
        subtitle.getStyleClass().add("profile-subtitle");

        names.getChildren().addAll(username, subtitle);
        row.getChildren().addAll(avatar, names);
        return row;
    }

    private VBox buildSettings(AppState state) {
        VBox box = new VBox(12);
        box.getStyleClass().add("settings-box");
        box.setPadding(new Insets(14));

        Label title = new Label("Settings");
        title.getStyleClass().add("section-title");

        HBox darkRow = toggleRow("Dark mode", state.isDarkMode(), state::setDarkMode);
        HBox batteryRow = toggleRow("Battery efficient mode", state.isBatteryEfficientMode(), state::setBatteryEfficientMode);
        HBox autoDlRow = toggleRow("Auto-download episode baru", state.isAutoDownloadNewEpisode(), state::setAutoDownloadNewEpisode);

        box.getChildren().addAll(title, darkRow, batteryRow, autoDlRow);
        return box;
    }

    private HBox toggleRow(String labelText, boolean selected, java.util.function.Consumer<Boolean> consumer) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText);
        JFXToggleButton toggle = new JFXToggleButton();
        toggle.setSelected(selected);
        toggle.selectedProperty().addListener((obs, oldValue, newValue) -> consumer.accept(newValue));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(label, spacer, toggle);
        return row;
    }

    private VBox buildLibrarySection(String title, javafx.collections.ObservableList<Anime> data) {
        VBox box = new VBox(10);

        Label label = new Label(title);
        label.getStyleClass().add("section-title");

        ListView<Anime> listView = new ListView<>(data);
        listView.setPrefHeight(180);
        listView.getStyleClass().add("profile-list");
        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Anime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getTitle() + " • " + item.getStatus());
                }
            }
        });

        box.getChildren().addAll(label, listView);
        return box;
    }
}
