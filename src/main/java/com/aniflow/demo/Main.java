package com.aniflow.demo;

import java.util.LinkedHashMap;
import java.util.Map;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public final class Main extends Application {

    @Override
    public void start(Stage stage) {
        StackPane animatedBackdrop = createAnimatedBackdrop();
        Label pageTitle = new Label("Home");
        pageTitle.setStyle("-fx-font-size: 42px; -fx-font-weight: 800; -fx-text-fill: white;");

        Label pageDescription = new Label("Demo halaman Home dengan komponen Liquid Glass Navbar.");
        pageDescription.setWrapText(true);
        pageDescription.setMaxWidth(560);
        pageDescription.setStyle("-fx-font-size: 16px; -fx-text-fill: rgba(255,255,255,0.92);");

        VBox pageCard = new VBox(14, pageTitle, pageDescription);
        pageCard.setPadding(new Insets(22));
        pageCard.setMaxWidth(640);
        pageCard.setAlignment(Pos.CENTER_LEFT);
        pageCard.setBackground(new Background(new BackgroundFill(
            Color.rgb(255, 255, 255, 0.14),
            new CornerRadii(20),
            Insets.EMPTY
        )));
        pageCard.setStyle("-fx-border-color: rgba(255,255,255,0.34); -fx-border-width: 1; -fx-border-radius: 20;");

        StackPane pageLayer = new StackPane(pageCard);
        pageLayer.setPadding(new Insets(110, 24, 24, 24));
        StackPane.setAlignment(pageCard, Pos.TOP_CENTER);

        StackPane backdropSource = new StackPane(animatedBackdrop, pageLayer);

        LiquidGlassNavbar navbar = new LiquidGlassNavbar();
        navbar.setBackdropSource(backdropSource);
        navbar.setOpacityLevel(0.30);
        navbar.setBlurRadius(18.0);
        navbar.setCornerRadius(20.0);
        navbar.setHighlightStrength(0.38);
        navbar.setEnableShimmer(true);
        navbar.setHighContrastMode(false);

        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("home", "Demo halaman Home dengan komponen Liquid Glass Navbar.");
        descriptions.put("search", "Pencarian konten. Fokus keyboard: Tab lalu panah kiri/kanan.");
        descriptions.put("notifications", "Notifikasi pembaruan episode terbaru dari watchlist.");
        descriptions.put("profile", "Profile, settings, dan preferensi tema.");

        navbar.setOnNavSelected(id -> {
            pageTitle.setText(capitalize(id));
            pageDescription.setText(descriptions.getOrDefault(id, "Halaman " + capitalize(id)));
        });

        CheckBox shimmerToggle = new CheckBox("Shimmer");
        shimmerToggle.setSelected(true);
        shimmerToggle.setTextFill(Color.WHITE);
        shimmerToggle.selectedProperty().addListener((obs, oldV, enabled) -> navbar.setEnableShimmer(enabled));

        CheckBox contrastToggle = new CheckBox("High Contrast");
        contrastToggle.setSelected(false);
        contrastToggle.setTextFill(Color.WHITE);
        contrastToggle.selectedProperty().addListener((obs, oldV, enabled) -> navbar.setHighContrastMode(enabled));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controlBar = new HBox(12, shimmerToggle, contrastToggle, spacer);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setPadding(new Insets(0, 24, 24, 24));
        StackPane.setAlignment(controlBar, Pos.BOTTOM_LEFT);

        StackPane root = new StackPane(backdropSource, controlBar, navbar);
        StackPane.setAlignment(navbar, Pos.TOP_CENTER);
        StackPane.setMargin(navbar, new Insets(16, 16, 0, 16));
        navbar.prefWidthProperty().bind(root.widthProperty().subtract(32));
        navbar.maxWidthProperty().bind(root.widthProperty().subtract(32));
        navbar.minWidthProperty().set(320);

        Scene scene = new Scene(root, 1100, 720);
        stage.setTitle("Liquid Glass Navbar Demo");
        stage.setScene(scene);
        stage.show();
    }

    private static StackPane createAnimatedBackdrop() {
        StackPane layer = new StackPane();
        layer.setBackground(new Background(new BackgroundFill(
            new javafx.scene.paint.LinearGradient(
                0, 0, 1, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0.0, Color.web("#0F172A")),
                new javafx.scene.paint.Stop(1.0, Color.web("#172554"))
            ),
            CornerRadii.EMPTY,
            Insets.EMPTY
        )));

        Circle blobA = new Circle(190, Color.web("#93C5FD55"));
        Circle blobB = new Circle(160, Color.web("#C4B5FD50"));
        Circle blobC = new Circle(130, Color.web("#67E8F955"));

        blobA.setTranslateX(-320);
        blobA.setTranslateY(-180);
        blobB.setTranslateX(280);
        blobB.setTranslateY(-120);
        blobC.setTranslateX(80);
        blobC.setTranslateY(220);

        Rectangle grain = new Rectangle();
        grain.setOpacity(0.05);
        grain.setFill(Color.WHITE);
        grain.widthProperty().bind(layer.widthProperty());
        grain.heightProperty().bind(layer.heightProperty());

        layer.getChildren().addAll(blobA, blobB, blobC, grain);

        animateBlob(blobA, 90, 70, 13.0);
        animateBlob(blobB, -70, 90, 16.0);
        animateBlob(blobC, 120, -80, 11.0);
        return layer;
    }

    private static void animateBlob(Circle node, double dx, double dy, double seconds) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.translateXProperty(), node.getTranslateX(), Interpolator.EASE_BOTH),
                new KeyValue(node.translateYProperty(), node.getTranslateY(), Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.seconds(seconds),
                new KeyValue(node.translateXProperty(), node.getTranslateX() + dx, Interpolator.EASE_BOTH),
                new KeyValue(node.translateYProperty(), node.getTranslateY() + dy, Interpolator.EASE_BOTH))
        );
        timeline.setAutoReverse(true);
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private static String capitalize(String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
