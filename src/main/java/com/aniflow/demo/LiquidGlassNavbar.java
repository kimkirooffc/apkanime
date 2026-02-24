package com.aniflow.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public final class LiquidGlassNavbar extends StackPane {

    public static final class NavItem {
        private final String id;
        private final String label;
        private final String icon;

        public NavItem(String id, String label, String icon) {
            this.id = Objects.requireNonNull(id, "id");
            this.label = Objects.requireNonNull(label, "label");
            this.icon = Objects.requireNonNull(icon, "icon");
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public String icon() {
            return icon;
        }
    }

    private static final double NAVBAR_HEIGHT = 64.0;
    private static final double CONTENT_GAP = 8.0;
    private static final double LABEL_HIDE_BREAKPOINT = 560.0;

    private final DoubleProperty opacity = new SimpleDoubleProperty(this, "opacity", 0.30);
    private final DoubleProperty blurRadius = new SimpleDoubleProperty(this, "blurRadius", 18.0);
    private final DoubleProperty cornerRadius = new SimpleDoubleProperty(this, "cornerRadius", 20.0);
    private final DoubleProperty highlightStrength = new SimpleDoubleProperty(this, "highlightStrength", 0.36);
    private final BooleanProperty enableShimmer = new SimpleBooleanProperty(this, "enableShimmer", true);
    private final BooleanProperty highContrastMode = new SimpleBooleanProperty(this, "highContrastMode", false);
    private final ObjectProperty<Consumer<String>> onNavSelected = new SimpleObjectProperty<>(this, "onNavSelected");
    private final ObjectProperty<Node> backdropSource = new SimpleObjectProperty<>(this, "backdropSource");
    private final ObjectProperty<String> selectedId = new SimpleObjectProperty<>(this, "selectedId", "home");

    private final ObservableList<NavItem> items = FXCollections.observableArrayList();
    private final Map<String, NavButton> buttonMap = new LinkedHashMap<>();
    private final List<NavButton> orderedButtons = new ArrayList<>();

    private final ImageView backdropView = new ImageView();
    private final Rectangle glassBaseLayer = new Rectangle();
    private final Rectangle liquidLayer = new Rectangle();
    private final Rectangle borderLayer = new Rectangle();
    private final Rectangle indicator = new Rectangle();
    private final Rectangle shimmer = new Rectangle(180, NAVBAR_HEIGHT + 24);
    private final Rectangle topGloss = new Rectangle();
    private final Rectangle focusOutline = new Rectangle();
    private final Region spacerLeft = new Region();
    private final Region spacerRight = new Region();
    private final HBox itemRow = new HBox(CONTENT_GAP);
    private final HBox rowWrap = new HBox(8.0);

    private final PauseTransition backdropRefreshDebounce = new PauseTransition(Duration.millis(90));
    private final Timeline backdropPoller = new Timeline();
    private final Timeline shimmerTimeline = new Timeline();
    private final Timeline indicatorTimeline = new Timeline();
    private final Timeline liquidFollowTimeline = new Timeline();
    private final GaussianBlur backdropBlur = new GaussianBlur(18.0);
    private final ChangeListener<Number> sceneWidthListener = (o, ov, nv) -> updateResponsiveMode(getWidth());
    private final EventHandler<KeyEvent> sceneKeyHandler = this::onKeyPressed;

    private WritableImage cachedBackdrop;
    private double targetIndicatorX = 0.0;
    private double targetIndicatorWidth = 96.0;
    private double targetHighlightX = 0.0;
    private double targetHighlightY = 0.0;
    private double currentHighlightX = 0.0;
    private double currentHighlightY = 0.0;
    private boolean labelsVisible = true;
    private boolean keyboardSelecting = false;

    public LiquidGlassNavbar() {
        this(List.of(
            new NavItem("home", "Home", "home"),
            new NavItem("search", "Search", "search"),
            new NavItem("notifications", "Notifications", "notifications"),
            new NavItem("profile", "Profile", "profile")
        ));
    }

    public LiquidGlassNavbar(List<NavItem> navItems) {
        setPickOnBounds(false);
        setMinHeight(NAVBAR_HEIGHT);
        setPrefHeight(NAVBAR_HEIGHT);
        setMaxHeight(NAVBAR_HEIGHT);
        setPadding(new Insets(8, 16, 8, 16));
        setFocusTraversable(true);

        spacerLeft.setMinWidth(0);
        spacerRight.setMinWidth(0);
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        rowWrap.setAlignment(Pos.CENTER);
        rowWrap.setFillHeight(false);
        rowWrap.getChildren().setAll(spacerLeft, itemRow, spacerRight);

        glassBaseLayer.setMouseTransparent(true);
        liquidLayer.setMouseTransparent(true);
        borderLayer.setMouseTransparent(true);
        indicator.setMouseTransparent(true);
        shimmer.setMouseTransparent(true);
        topGloss.setMouseTransparent(true);
        focusOutline.setMouseTransparent(true);
        backdropView.setMouseTransparent(true);

        backdropView.setManaged(false);
        glassBaseLayer.setManaged(false);
        liquidLayer.setManaged(false);
        borderLayer.setManaged(false);
        indicator.setManaged(false);
        shimmer.setManaged(false);
        topGloss.setManaged(false);
        focusOutline.setManaged(false);

        getChildren().setAll(backdropView, glassBaseLayer, liquidLayer, indicator, topGloss, shimmer, rowWrap, borderLayer, focusOutline);
        setupStaticEffects();
        configureListeners();
        setItems(navItems);
        setSelectedId(selectedId.get());
        refreshThemePaints();
        requestBackdropRefresh();
        requestLayout();
    }

    public void setItems(List<NavItem> navItems) {
        items.setAll(navItems);
        buttonMap.clear();
        orderedButtons.clear();
        itemRow.getChildren().clear();

        for (NavItem item : items) {
            NavButton button = new NavButton(item);
            buttonMap.put(item.id(), button);
            orderedButtons.add(button);
            itemRow.getChildren().add(button);
        }
        if (!items.isEmpty() && !buttonMap.containsKey(selectedId.get())) {
            selectedId.set(items.get(0).id());
        }
        refreshButtonStates();
        requestLayout();
    }

    public void setItemDisabled(String id, boolean disabled) {
        NavButton button = buttonMap.get(id);
        if (button == null) {
            return;
        }
        button.setDisable(disabled);
        if (disabled && id.equals(selectedId.get())) {
            for (NavButton next : orderedButtons) {
                if (!next.isDisable()) {
                    select(next.getItem().id(), true);
                    break;
                }
            }
        }
    }

    public void setOnNavSelected(Consumer<String> listener) {
        onNavSelected.set(listener);
    }

    public Consumer<String> getOnNavSelected() {
        return onNavSelected.get();
    }

    public ObjectProperty<Consumer<String>> onNavSelectedProperty() {
        return onNavSelected;
    }

    public void setSelectedId(String id) {
        if (id == null || !buttonMap.containsKey(id)) {
            return;
        }
        select(id, false);
    }

    public String getSelectedId() {
        return selectedId.get();
    }

    public ObjectProperty<String> selectedIdProperty() {
        return selectedId;
    }

    public double getOpacityLevel() {
        return opacity.get();
    }

    public void setOpacityLevel(double value) {
        opacity.set(clamp(value, 0.08, 0.9));
    }

    public DoubleProperty opacityLevelProperty() {
        return opacity;
    }

    public double getBlurRadius() {
        return blurRadius.get();
    }

    public void setBlurRadius(double value) {
        blurRadius.set(clamp(value, 0.0, 40.0));
    }

    public DoubleProperty blurRadiusProperty() {
        return blurRadius;
    }

    public double getCornerRadius() {
        return cornerRadius.get();
    }

    public void setCornerRadius(double value) {
        cornerRadius.set(clamp(value, 8.0, 44.0));
    }

    public DoubleProperty cornerRadiusProperty() {
        return cornerRadius;
    }

    public double getHighlightStrength() {
        return highlightStrength.get();
    }

    public void setHighlightStrength(double value) {
        highlightStrength.set(clamp(value, 0.0, 1.0));
    }

    public DoubleProperty highlightStrengthProperty() {
        return highlightStrength;
    }

    public boolean isEnableShimmer() {
        return enableShimmer.get();
    }

    public void setEnableShimmer(boolean enabled) {
        enableShimmer.set(enabled);
    }

    public BooleanProperty enableShimmerProperty() {
        return enableShimmer;
    }

    public boolean isHighContrastMode() {
        return highContrastMode.get();
    }

    public void setHighContrastMode(boolean enabled) {
        highContrastMode.set(enabled);
    }

    public BooleanProperty highContrastModeProperty() {
        return highContrastMode;
    }

    public void setBackdropSource(Node source) {
        backdropSource.set(source);
    }

    public Node getBackdropSource() {
        return backdropSource.get();
    }

    public ObjectProperty<Node> backdropSourceProperty() {
        return backdropSource;
    }

    public void requestBackdropRefresh() {
        backdropRefreshDebounce.playFromStart();
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        double radius = cornerRadius.get();

        backdropView.setFitWidth(w);
        backdropView.setFitHeight(h);
        backdropView.relocate(0, 0);

        glassBaseLayer.setWidth(w);
        glassBaseLayer.setHeight(h);
        glassBaseLayer.setArcWidth(radius * 2.0);
        glassBaseLayer.setArcHeight(radius * 2.0);
        glassBaseLayer.relocate(0, 0);

        liquidLayer.setWidth(w);
        liquidLayer.setHeight(h);
        liquidLayer.setArcWidth(radius * 2.0);
        liquidLayer.setArcHeight(radius * 2.0);
        liquidLayer.relocate(0, 0);

        borderLayer.setWidth(w);
        borderLayer.setHeight(h);
        borderLayer.setArcWidth(radius * 2.0);
        borderLayer.setArcHeight(radius * 2.0);
        borderLayer.relocate(0, 0);

        focusOutline.setWidth(w);
        focusOutline.setHeight(h);
        focusOutline.setArcWidth(radius * 2.0);
        focusOutline.setArcHeight(radius * 2.0);
        focusOutline.relocate(0, 0);

        indicator.setHeight(Math.max(36.0, h - 16.0));
        indicator.setArcHeight((Math.max(36.0, h - 16.0)) - 2.0);
        indicator.setArcWidth((Math.max(36.0, h - 16.0)) - 2.0);
        indicator.setY((h - indicator.getHeight()) * 0.5);
        indicator.setX(clamp(targetIndicatorX, 0, Math.max(0, w - indicator.getWidth())));

        topGloss.setWidth(w - 4.0);
        topGloss.setHeight(Math.max(10.0, h * 0.45));
        topGloss.setArcWidth(Math.max(12, radius * 2.0));
        topGloss.setArcHeight(Math.max(12, radius * 2.0));
        topGloss.relocate(2.0, 1.0);

        shimmer.setHeight(h + 20.0);
        shimmer.setY(-10.0);

        super.layoutChildren();
        updateResponsiveMode(w);
        alignIndicatorToSelection(false);
        updateLiquidTarget(currentHighlightX, currentHighlightY);
    }

    private void setupStaticEffects() {
        backdropView.setEffect(backdropBlur);
        backdropView.setClip(new Rectangle());
        ((Rectangle) backdropView.getClip()).arcWidthProperty().bind(cornerRadius.multiply(2.0));
        ((Rectangle) backdropView.getClip()).arcHeightProperty().bind(cornerRadius.multiply(2.0));
        ((Rectangle) backdropView.getClip()).widthProperty().bind(widthProperty());
        ((Rectangle) backdropView.getClip()).heightProperty().bind(heightProperty());

        DropShadow drop = new DropShadow();
        drop.setRadius(22.0);
        drop.setOffsetY(8.0);
        drop.setSpread(0.08);
        drop.setColor(Color.rgb(0, 0, 0, 0.26));
        borderLayer.setEffect(drop);

        indicator.setFill(new LinearGradient(
            0.0, 0.0, 1.0, 1.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(255, 255, 255, 0.26)),
            new Stop(1.0, Color.rgb(198, 220, 255, 0.16))
        ));

        topGloss.setFill(new LinearGradient(
            0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(255, 255, 255, 0.30)),
            new Stop(1.0, Color.rgb(255, 255, 255, 0.02))
        ));

        shimmer.setFill(new LinearGradient(
            0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(255, 255, 255, 0.00)),
            new Stop(0.5, Color.rgb(255, 255, 255, 0.30)),
            new Stop(1.0, Color.rgb(255, 255, 255, 0.00))
        ));
        shimmer.setRotate(-14.0);
        shimmer.setBlendMode(BlendMode.SCREEN);
        shimmer.setOpacity(0.0);

        shimmerTimeline.getKeyFrames().setAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(shimmer.translateXProperty(), -320, Interpolator.LINEAR),
                new KeyValue(shimmer.opacityProperty(), 0.0, Interpolator.LINEAR)),
            new KeyFrame(Duration.seconds(0.7),
                new KeyValue(shimmer.opacityProperty(), 0.30, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.seconds(2.6),
                new KeyValue(shimmer.translateXProperty(), 780, Interpolator.EASE_BOTH),
                new KeyValue(shimmer.opacityProperty(), 0.0, Interpolator.EASE_IN))
        );
        shimmerTimeline.setCycleCount(Animation.INDEFINITE);
        shimmerTimeline.setDelay(Duration.millis(1400));

        backdropPoller.getKeyFrames().setAll(new KeyFrame(Duration.millis(180), e -> refreshBackdrop()));
        backdropPoller.setCycleCount(Animation.INDEFINITE);

        setBackground(Background.EMPTY);
        setManaged(true);
    }

    private void configureListeners() {
        opacity.addListener((obs, oldV, newV) -> refreshThemePaints());
        blurRadius.addListener((obs, oldV, newV) -> {
            backdropBlur.setRadius(newV.doubleValue());
            requestBackdropRefresh();
        });
        cornerRadius.addListener((obs, oldV, newV) -> {
            requestLayout();
            refreshThemePaints();
        });
        highlightStrength.addListener((obs, oldV, newV) -> updateLiquidTarget(currentHighlightX, currentHighlightY));
        highContrastMode.addListener((obs, oldV, newV) -> {
            refreshThemePaints();
            refreshButtonStates();
        });

        enableShimmer.addListener((obs, oldV, enabled) -> {
            if (enabled) {
                shimmerTimeline.play();
            } else {
                shimmerTimeline.stop();
                shimmer.setOpacity(0.0);
            }
        });

        selectedId.addListener((obs, oldV, newV) -> {
            refreshButtonStates();
            alignIndicatorToSelection(true);
        });

        backdropSource.addListener((obs, oldV, newV) -> requestBackdropRefresh());
        backdropRefreshDebounce.setOnFinished(e -> refreshBackdrop());

        widthProperty().addListener((obs, oldV, newV) -> requestBackdropRefresh());
        heightProperty().addListener((obs, oldV, newV) -> requestBackdropRefresh());
        localToSceneTransformProperty().addListener((obs, oldV, newV) -> requestBackdropRefresh());

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, sceneKeyHandler);
                oldScene.widthProperty().removeListener(sceneWidthListener);
                backdropPoller.stop();
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, sceneKeyHandler);
                newScene.widthProperty().addListener(sceneWidthListener);
                requestBackdropRefresh();
                backdropPoller.play();
            }
        });

        setOnMouseMoved(this::onMouseMoved);
        setOnMouseEntered(this::onMouseMoved);
        setOnMouseExited(e -> {
            updateLiquidTarget(getWidth() * 0.5, getHeight() * 0.22);
        });

        focusedProperty().addListener((obs, oldV, focused) -> {
            focusOutline.setVisible(focused);
            if (focused && !orderedButtons.isEmpty()) {
                orderedButtons.get(0).requestFocus();
            }
        });
        focusOutline.setVisible(false);

        liquidFollowTimeline.getKeyFrames().setAll(
            new KeyFrame(Duration.ZERO),
            new KeyFrame(Duration.millis(1000 / 60.0), e -> {
                currentHighlightX = lerp(currentHighlightX, targetHighlightX, 0.22);
                currentHighlightY = lerp(currentHighlightY, targetHighlightY, 0.22);
                liquidLayer.setFill(new RadialGradient(
                    0, 0,
                    currentHighlightX, currentHighlightY, Math.max(150, getWidth() * 0.45),
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(255, 255, 255, highContrastMode.get() ? 0.16 : 0.22 * clamp(highlightStrength.get(), 0, 1))),
                    new Stop(0.55, Color.rgb(255, 255, 255, highContrastMode.get() ? 0.05 : 0.08 * clamp(highlightStrength.get(), 0, 1))),
                    new Stop(1.0, Color.rgb(255, 255, 255, 0.00))
                ));
                borderLayer.setFill(Color.TRANSPARENT);
            })
        );
        liquidFollowTimeline.setCycleCount(Animation.INDEFINITE);
        liquidFollowTimeline.play();

        if (enableShimmer.get()) {
            shimmerTimeline.play();
        }
    }

    private void refreshThemePaints() {
        boolean hc = highContrastMode.get();

        Color glassBase = hc
            ? Color.rgb(5, 10, 22, 0.84)
            : Color.rgb(255, 255, 255, clamp(opacity.get(), 0.08, 0.90));
        Color borderColor = hc
            ? Color.rgb(255, 255, 255, 0.94)
            : Color.rgb(255, 255, 255, 0.38);
        Color focusColor = hc
            ? Color.rgb(255, 226, 94, 0.95)
            : Color.rgb(137, 188, 255, 0.85);

        glassBaseLayer.setFill(glassBase);
        borderLayer.setFill(Color.TRANSPARENT);
        borderLayer.setStroke(borderColor);
        borderLayer.setStrokeWidth(hc ? 1.7 : 1.1);

        focusOutline.setFill(Color.TRANSPARENT);
        focusOutline.setStroke(focusColor);
        focusOutline.setStrokeWidth(2.2);
        focusOutline.getStrokeDashArray().setAll(8.0, 6.0);

        indicator.setFill(hc
            ? new LinearGradient(
                0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(255, 255, 255, 0.22)),
                new Stop(1.0, Color.rgb(255, 240, 170, 0.26))
            )
            : new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(255, 255, 255, 0.24)),
                new Stop(1.0, Color.rgb(200, 220, 255, 0.14))
            )
        );
        topGloss.setOpacity(hc ? 0.12 : 0.28);

        for (NavButton button : orderedButtons) {
            button.refreshVisual();
        }
    }

    private void refreshBackdrop() {
        Node source = backdropSource.get();
        if (source == null || getScene() == null || getWidth() <= 2 || getHeight() <= 2) {
            return;
        }

        Bounds sceneBounds = localToScene(getLayoutBounds());
        if (sceneBounds == null || sceneBounds.getWidth() <= 1 || sceneBounds.getHeight() <= 1) {
            return;
        }

        Bounds srcLocal = source.sceneToLocal(sceneBounds);
        if (srcLocal.getWidth() <= 1 || srcLocal.getHeight() <= 1) {
            return;
        }

        Rectangle2D viewport = new Rectangle2D(
            Math.max(0, srcLocal.getMinX()),
            Math.max(0, srcLocal.getMinY()),
            Math.max(1, srcLocal.getWidth()),
            Math.max(1, srcLocal.getHeight())
        );

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        params.setViewport(viewport);

        int imageW = Math.max(1, (int) Math.ceil(viewport.getWidth()));
        int imageH = Math.max(1, (int) Math.ceil(viewport.getHeight()));
        if (cachedBackdrop == null || cachedBackdrop.getWidth() != imageW || cachedBackdrop.getHeight() != imageH) {
            cachedBackdrop = new WritableImage(imageW, imageH);
        }
        source.snapshot(params, cachedBackdrop);
        backdropView.setImage(cachedBackdrop);
    }

    private void onMouseMoved(MouseEvent event) {
        double x = clamp(event.getX(), 0, getWidth());
        double y = clamp(event.getY(), 0, getHeight());
        updateLiquidTarget(x, y);
    }

    private void updateLiquidTarget(double x, double y) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        targetHighlightX = clamp(x, 0, getWidth());
        targetHighlightY = clamp(y * 0.85, 0, getHeight());

        double strength = clamp(highlightStrength.get(), 0, 1);
        Paint fill = new RadialGradient(
            0, 0,
            targetHighlightX, targetHighlightY, Math.max(140, getWidth() * 0.44),
            false, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.rgb(255, 255, 255, 0.20 * strength)),
            new Stop(0.45, Color.rgb(255, 255, 255, 0.07 * strength)),
            new Stop(1.0, Color.rgb(255, 255, 255, 0.0))
        );
        liquidLayer.setFill(fill);
    }

    private void select(String id, boolean fireEvent) {
        if (id == null || !buttonMap.containsKey(id)) {
            return;
        }
        NavButton candidate = buttonMap.get(id);
        if (candidate.isDisable()) {
            return;
        }
        selectedId.set(id);
        if (fireEvent && onNavSelected.get() != null) {
            onNavSelected.get().accept(id);
        }
    }

    private void refreshButtonStates() {
        String current = selectedId.get();
        for (NavButton button : orderedButtons) {
            button.setActive(Objects.equals(button.getItem().id(), current));
        }
    }

    private void alignIndicatorToSelection(boolean animated) {
        NavButton selected = buttonMap.get(selectedId.get());
        if (selected == null) {
            return;
        }
        Bounds b = selected.getBoundsInParent();
        targetIndicatorX = b.getMinX() - 1.0;
        targetIndicatorWidth = b.getWidth() + 2.0;

        if (!animated) {
            indicator.setX(targetIndicatorX);
            indicator.setWidth(targetIndicatorWidth);
            return;
        }

        indicatorTimeline.stop();
        indicatorTimeline.getKeyFrames().setAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(indicator.xProperty(), indicator.getX(), Interpolator.EASE_OUT),
                new KeyValue(indicator.widthProperty(), indicator.getWidth(), Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(190),
                new KeyValue(indicator.xProperty(), targetIndicatorX, Interpolator.SPLINE(0.2, 0.8, 0.2, 1.0)),
                new KeyValue(indicator.widthProperty(), targetIndicatorWidth, Interpolator.SPLINE(0.2, 0.8, 0.2, 1.0)))
        );
        indicatorTimeline.playFromStart();
    }

    private void updateResponsiveMode(double availableWidth) {
        boolean shouldShowLabel = availableWidth >= LABEL_HIDE_BREAKPOINT;
        if (shouldShowLabel == labelsVisible) {
            return;
        }
        labelsVisible = shouldShowLabel;
        for (NavButton button : orderedButtons) {
            button.setShowLabel(shouldShowLabel);
        }
        requestLayout();
    }

    private void onKeyPressed(KeyEvent event) {
        if (keyboardSelecting) {
            return;
        }
        Node focused = getScene() == null ? null : getScene().getFocusOwner();
        int focusedIndex = -1;
        for (int i = 0; i < orderedButtons.size(); i++) {
            if (orderedButtons.get(i) == focused) {
                focusedIndex = i;
                break;
            }
        }
        if (focusedIndex < 0) {
            return;
        }

        if (event.getCode() == KeyCode.RIGHT) {
            focusRelative(focusedIndex, 1);
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.LEFT) {
            focusRelative(focusedIndex, -1);
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.HOME) {
            focusFirstEnabled();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.END) {
            focusLastEnabled();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
            keyboardSelecting = true;
            NavButton selected = orderedButtons.get(focusedIndex);
            if (!selected.isDisable()) {
                selected.playPressFeedback();
                select(selected.getItem().id(), true);
            }
            keyboardSelecting = false;
            event.consume();
        }
    }

    private void focusRelative(int baseIndex, int direction) {
        int size = orderedButtons.size();
        if (size == 0) {
            return;
        }
        for (int i = 1; i <= size; i++) {
            int idx = (baseIndex + i * direction + size) % size;
            NavButton candidate = orderedButtons.get(idx);
            if (!candidate.isDisable()) {
                candidate.requestFocus();
                break;
            }
        }
    }

    private void focusFirstEnabled() {
        for (NavButton button : orderedButtons) {
            if (!button.isDisable()) {
                button.requestFocus();
                return;
            }
        }
    }

    private void focusLastEnabled() {
        for (int i = orderedButtons.size() - 1; i >= 0; i--) {
            NavButton button = orderedButtons.get(i);
            if (!button.isDisable()) {
                button.requestFocus();
                return;
            }
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private final class NavButton extends StackPane {
        private final NavItem item;
        private final Rectangle bg = new Rectangle();
        private final Rectangle focusRing = new Rectangle();
        private final HBox content = new HBox(8.0);
        private final Canvas iconCanvas = new Canvas(18, 18);
        private final javafx.scene.control.Label label = new javafx.scene.control.Label();
        private final DoubleProperty hover = new SimpleDoubleProperty(0.0);
        private final BooleanProperty active = new SimpleBooleanProperty(false);

        private final Timeline hoverTimeline = new Timeline();
        private final ScaleTransition pressDown = new ScaleTransition(Duration.millis(120), this);
        private final ScaleTransition pressUp = new ScaleTransition(Duration.millis(120), this);
        private final FadeTransition disabledFade = new FadeTransition(Duration.millis(120), this);

        NavButton(NavItem item) {
            this.item = item;
            setMinHeight(44);
            setPrefHeight(44);
            setPadding(new Insets(10, 12, 10, 12));
            setFocusTraversable(true);
            setAccessibleText(item.label());

            bg.setArcWidth(28);
            bg.setArcHeight(28);
            bg.setManaged(false);
            bg.setMouseTransparent(true);

            focusRing.setArcWidth(28);
            focusRing.setArcHeight(28);
            focusRing.setManaged(false);
            focusRing.setMouseTransparent(true);
            focusRing.setFill(Color.TRANSPARENT);
            focusRing.setVisible(false);

            label.setText(item.label());
            label.setStyle("-fx-font-size: 13px; -fx-font-weight: 600;");

            content.setAlignment(Pos.CENTER);
            content.getChildren().setAll(iconCanvas, label);

            getChildren().setAll(bg, content, focusRing);
            setAlignment(Pos.CENTER);

            hoverTimeline.setCycleCount(1);
            pressDown.setInterpolator(Interpolator.EASE_OUT);
            pressDown.setToX(0.97);
            pressDown.setToY(0.97);
            pressUp.setInterpolator(Interpolator.EASE_BOTH);
            pressUp.setToX(1.0);
            pressUp.setToY(1.0);

            disabledFade.setInterpolator(Interpolator.EASE_BOTH);
            disabledFade.setToValue(0.42);

            hover.addListener((obs, ov, nv) -> refreshVisual());
            active.addListener((obs, ov, nv) -> refreshVisual());
            disabledProperty().addListener((obs, ov, disabled) -> {
                if (disabled) {
                    disabledFade.playFromStart();
                } else {
                    setOpacity(1.0);
                }
                refreshVisual();
            });
            focusedProperty().addListener((obs, ov, focused) -> focusRing.setVisible(focused));

            widthProperty().addListener((obs, ov, nv) -> requestLayout());
            heightProperty().addListener((obs, ov, nv) -> requestLayout());

            addEventHandler(MouseEvent.MOUSE_ENTERED, e -> animateHoverTo(1.0));
            addEventHandler(MouseEvent.MOUSE_EXITED, e -> animateHoverTo(0.0));
            addEventHandler(MouseEvent.MOUSE_PRESSED, e -> playPressFeedback());
            addEventHandler(MouseEvent.MOUSE_RELEASED, e -> pressUp.playFromStart());
            addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                if (!isDisable()) {
                    select(item.id(), true);
                }
            });

            setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                    if (!isDisable()) {
                        playPressFeedback();
                        select(item.id(), true);
                    }
                    event.consume();
                }
            });

            refreshVisual();
        }

        NavItem getItem() {
            return item;
        }

        void setActive(boolean enabled) {
            active.set(enabled);
        }

        void setShowLabel(boolean showLabel) {
            label.setManaged(showLabel);
            label.setVisible(showLabel);
            if (!showLabel) {
                content.setSpacing(0.0);
                setPadding(new Insets(10, 16, 10, 16));
            } else {
                content.setSpacing(8.0);
                setPadding(new Insets(10, 12, 10, 12));
            }
        }

        void playPressFeedback() {
            pressDown.playFromStart();
            pressUp.playFrom(Duration.millis(60));
        }

        void refreshVisual() {
            boolean hc = highContrastMode.get();
            double h = clamp(hover.get(), 0.0, 1.0);
            boolean selected = active.get();
            boolean disabled = isDisable();

            Color textColor;
            Color iconColor;

            if (disabled) {
                textColor = hc ? Color.rgb(235, 235, 235, 0.5) : Color.rgb(255, 255, 255, 0.52);
                iconColor = textColor;
            } else if (selected) {
                textColor = hc ? Color.rgb(255, 255, 255, 1.0) : Color.rgb(255, 255, 255, 0.98);
                iconColor = textColor;
            } else {
                textColor = hc ? Color.rgb(255, 255, 255, 0.95) : Color.rgb(255, 255, 255, 0.88);
                iconColor = textColor.interpolate(Color.web("#c7dcff"), h * 0.45);
            }

            Color hoverBg = hc
                ? Color.rgb(255, 255, 255, 0.10 + h * 0.10)
                : Color.rgb(255, 255, 255, 0.05 + h * 0.08);
            bg.setFill(selected ? Color.TRANSPARENT : hoverBg);

            label.setTextFill(textColor);
            drawIcon(iconCanvas.getGraphicsContext2D(), item.icon(), iconColor);

            focusRing.setStroke(hc
                ? Color.rgb(255, 235, 128, 0.95)
                : Color.rgb(120, 180, 255, 0.85));
            focusRing.setStrokeWidth(1.8);
        }

        private void animateHoverTo(double target) {
            hoverTimeline.stop();
            hoverTimeline.getKeyFrames().setAll(
                new KeyFrame(Duration.ZERO, new KeyValue(hover, hover.get())),
                new KeyFrame(Duration.millis(180), new KeyValue(hover, target, Interpolator.SPLINE(0.22, 0.82, 0.24, 1.0)))
            );
            hoverTimeline.playFromStart();
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();
            bg.setWidth(w);
            bg.setHeight(h);
            focusRing.setWidth(w);
            focusRing.setHeight(h);
            super.layoutChildren();
        }
    }

    private static void drawIcon(GraphicsContext gc, String iconName, Color color) {
        gc.clearRect(0, 0, 18, 18);
        gc.setLineWidth(1.8);
        gc.setStroke(color);
        gc.setFill(Color.TRANSPARENT);

        switch (iconName) {
            case "home" -> {
                gc.strokePolygon(new double[]{3, 9, 15}, new double[]{8, 3, 8}, 3);
                gc.strokeRect(4, 8, 10, 7);
                gc.strokeLine(8.2, 15, 8.2, 11);
                gc.strokeLine(9.8, 15, 9.8, 11);
            }
            case "search" -> {
                gc.strokeOval(3.2, 3.2, 8.6, 8.6);
                gc.strokeLine(10.3, 10.3, 14.6, 14.6);
            }
            case "notifications" -> {
                gc.strokeArc(4.0, 4.0, 10.0, 9.6, 200, 140, javafx.scene.shape.ArcType.OPEN);
                gc.strokeLine(4.8, 11.4, 13.2, 11.4);
                gc.strokeLine(5.1, 11.4, 4.3, 13.0);
                gc.strokeLine(12.9, 11.4, 13.7, 13.0);
                gc.strokeOval(8.0, 13.0, 2.0, 2.0);
            }
            case "profile" -> {
                gc.strokeOval(6.1, 2.8, 5.8, 5.8);
                gc.strokeArc(3.2, 8.4, 11.6, 7.5, 200, 140, javafx.scene.shape.ArcType.OPEN);
            }
            default -> gc.strokeOval(4.0, 4.0, 10.0, 10.0);
        }
    }
}
