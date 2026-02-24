package com.aniflow.ui.components;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

public class DockButton extends Button {
    public DockButton(String iconLiteral, String tooltip, Runnable onClick) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(20);
        getStyleClass().add("dock-button");
        setGraphic(icon);
        setTooltip(new Tooltip(tooltip));
        setOnAction(e -> onClick.run());
    }

    public void setActive(boolean active) {
        if (active) {
            if (!getStyleClass().contains("active")) {
                getStyleClass().add("active");
            }
        } else {
            getStyleClass().remove("active");
        }
    }
}
