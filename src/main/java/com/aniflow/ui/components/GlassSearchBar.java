package com.aniflow.ui.components;

import com.jfoenix.controls.JFXTextField;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

public class GlassSearchBar extends HBox {
    private final JFXTextField textField;

    public GlassSearchBar() {
        getStyleClass().add("glass-search");
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);

        FontIcon searchIcon = new FontIcon("fas-search");
        searchIcon.setIconSize(16);
        searchIcon.getStyleClass().add("muted-icon");

        textField = new JFXTextField();
        textField.setPromptText("Cari anime, studio, atau karakter...");
        textField.getStyleClass().add("search-input");

        getChildren().addAll(searchIcon, textField);
    }

    public JFXTextField getTextField() {
        return textField;
    }
}
