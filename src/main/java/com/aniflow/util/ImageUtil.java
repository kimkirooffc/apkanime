package com.aniflow.util;

import javafx.scene.image.Image;

public final class ImageUtil {
    private ImageUtil() {
    }

    public static Image safeImage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return new Image(url, true);
        } catch (Exception ignored) {
            return null;
        }
    }
}
