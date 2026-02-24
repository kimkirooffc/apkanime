package com.aniflow.ui.theme;

import com.aniflow.app.AppState;
import javafx.scene.Parent;

public final class ThemeManager {
    private ThemeManager() {
    }

    public static void bind(Parent root, AppState state) {
        apply(root, state.isDarkMode());
        state.darkModeProperty().addListener((obs, oldValue, newValue) -> apply(root, newValue));
    }

    private static void apply(Parent root, boolean darkMode) {
        if (darkMode) {
            if (!root.getStyleClass().contains("dark-mode")) {
                root.getStyleClass().add("dark-mode");
            }
        } else {
            root.getStyleClass().remove("dark-mode");
        }
    }
}
