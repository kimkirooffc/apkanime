package com.aniflow.util;

import com.aniflow.app.AppState;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public final class AnimationUtil {
    private AnimationUtil() {
    }

    public static void applyCardHover(Node node, AppState state) {
        node.setOnMouseEntered(event -> animateScale(node, state, 1.02));
        node.setOnMouseExited(event -> animateScale(node, state, 1.0));
    }

    public static void switchPage(StackPane host, Node nextPage, AppState state) {
        Duration duration = transitionDuration(state);

        if (host.getChildren().isEmpty()) {
            host.getChildren().setAll(nextPage);
            return;
        }

        Node current = host.getChildren().get(0);
        FadeTransition out = new FadeTransition(duration, current);
        out.setFromValue(1.0);
        out.setToValue(0.0);
        TranslateTransition outSlide = new TranslateTransition(duration, current);
        outSlide.setFromX(0);
        outSlide.setToX(-20);

        ParallelTransition hide = new ParallelTransition(out, outSlide);
        hide.setOnFinished(event -> {
            nextPage.setOpacity(0);
            nextPage.setTranslateX(20);
            host.getChildren().setAll(nextPage);

            FadeTransition in = new FadeTransition(duration, nextPage);
            in.setFromValue(0);
            in.setToValue(1);
            TranslateTransition inSlide = new TranslateTransition(duration, nextPage);
            inSlide.setFromX(20);
            inSlide.setToX(0);
            new ParallelTransition(in, inSlide).play();
        });
        hide.play();
    }

    public static Duration transitionDuration(AppState state) {
        return state != null && state.isBatteryEfficientMode() ? Duration.millis(160) : Duration.millis(300);
    }

    private static void animateScale(Node node, AppState state, double target) {
        ScaleTransition transition = new ScaleTransition(transitionDuration(state), node);
        transition.setToX(target);
        transition.setToY(target);
        transition.play();
    }
}
