package com.aniflow.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LiquidGlassNavbarView extends FrameLayout {

    public interface OnNavSelectedListener {
        void onNavSelected(@NonNull String id);
    }

    public static final class NavItem {
        public final String id;
        public final String label;
        @DrawableRes public final int iconRes;
        @DrawableRes public final int activeIconRes;
        public boolean enabled = true;

        public NavItem(@NonNull String id, @NonNull String label, @DrawableRes int iconRes, @DrawableRes int activeIconRes) {
            this.id = id;
            this.label = label;
            this.iconRes = iconRes;
            this.activeIconRes = activeIconRes;
        }
    }

    private static final long HOVER_ANIM_MS = 180L;
    private static final long PRESS_ANIM_MS = 120L;
    private static final long INDICATOR_ANIM_MS = 190L;

    private final FrameLayout glassContainer;
    private final LiquidEffectLayer liquidLayer;
    private final View selectedIndicator;
    private final LinearLayout itemRow;
    private final GradientDrawable containerBackground = new GradientDrawable();
    private final GradientDrawable indicatorBackground = new GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        new int[]{0x664158D0, 0x66C850C0}
    );
    private final Map<String, ItemHolder> itemMap = new LinkedHashMap<>();
    private final List<String> itemOrder = new ArrayList<>();

    private ValueAnimator indicatorAnimator;
    private float indicatorStartX = 0f;
    private float indicatorTargetX = 0f;
    private float indicatorStartW = 0f;
    private float indicatorTargetW = 0f;

    private OnNavSelectedListener onNavSelectedListener;
    private String selectedId = "";

    private float opacity = 0.28f;
    private float blurRadius = 20f;
    private float cornerRadiusDp = 20f;
    private float highlightStrength = 0.34f;
    private boolean enableShimmer = true;
    private boolean highContrastMode = false;

    public LiquidGlassNavbarView(@NonNull Context context) {
        this(context, null);
    }

    public LiquidGlassNavbarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);

        glassContainer = new FrameLayout(context);
        LayoutParams containerParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        glassContainer.setLayoutParams(containerParams);
        glassContainer.setClipToPadding(false);
        glassContainer.setClipChildren(false);
        glassContainer.setPadding(dp(8), dp(6), dp(8), dp(6));
        glassContainer.setElevation(dp(8));
        glassContainer.setFocusable(false);
        glassContainer.setBackground(containerBackground);

        selectedIndicator = new View(context);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(dp(72), dp(42));
        indicatorParams.leftMargin = dp(8);
        indicatorParams.topMargin = dp(11);
        selectedIndicator.setLayoutParams(indicatorParams);
        selectedIndicator.setBackground(indicatorBackground);
        selectedIndicator.setAlpha(0f);
        selectedIndicator.setFocusable(false);
        selectedIndicator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        liquidLayer = new LiquidEffectLayer(context);
        liquidLayer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        liquidLayer.setFocusable(false);
        liquidLayer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        itemRow = new LinearLayout(context);
        itemRow.setOrientation(LinearLayout.HORIZONTAL);
        itemRow.setGravity(android.view.Gravity.CENTER);
        itemRow.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        itemRow.setFocusable(false);

        glassContainer.addView(selectedIndicator);
        glassContainer.addView(liquidLayer);
        glassContainer.addView(itemRow);
        addView(glassContainer);

        setupPointerTracking();
        updateContainerVisuals();
        setItems(defaultItems());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        liquidLayer.setEnableShimmer(enableShimmer);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        liquidLayer.setEnableShimmer(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = dp(64);
        int resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec);
        int fixedHeightSpec = MeasureSpec.makeMeasureSpec(resolvedHeight, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, fixedHeightSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateResponsiveLabels(w);
        post(this::moveIndicatorToSelectedNoAnim);
    }

    public void setItems(@NonNull List<NavItem> items) {
        itemMap.clear();
        itemOrder.clear();
        itemRow.removeAllViews();

        for (int i = 0; i < items.size(); i++) {
            NavItem item = items.get(i);
            ItemHolder holder = createItem(item, i);
            itemMap.put(item.id, holder);
            itemOrder.add(item.id);
            itemRow.addView(holder.root);
        }

        if (items.isEmpty()) {
            selectedId = "";
            selectedIndicator.setAlpha(0f);
            return;
        }
        if (!itemMap.containsKey(selectedId)) {
            selectedId = items.get(0).id;
        }
        syncSelectionVisuals(false);
        updateResponsiveLabels(getWidth());
    }

    public void setOnNavSelectedListener(@Nullable OnNavSelectedListener listener) {
        this.onNavSelectedListener = listener;
    }

    public void setSelectedId(@Nullable String id) {
        if (id == null || !itemMap.containsKey(id)) {
            return;
        }
        if (Objects.equals(selectedId, id)) {
            syncSelectionVisuals(false);
            return;
        }
        selectedId = id;
        syncSelectionVisuals(true);
    }

    @NonNull
    public String getSelectedId() {
        return selectedId;
    }

    public void setItemEnabled(@NonNull String id, boolean enabled) {
        ItemHolder holder = itemMap.get(id);
        if (holder == null) {
            return;
        }
        holder.item.enabled = enabled;
        holder.root.setEnabled(enabled);
        holder.root.setAlpha(enabled ? 1f : 0.45f);
        if (!enabled && Objects.equals(id, selectedId)) {
            for (String nextId : itemOrder) {
                ItemHolder next = itemMap.get(nextId);
                if (next != null && next.item.enabled) {
                    selectedId = nextId;
                    syncSelectionVisuals(true);
                    break;
                }
            }
        }
    }

    public void setOpacity(float opacity) {
        this.opacity = clamp(opacity, 0.08f, 0.90f);
        updateContainerVisuals();
    }

    public void setBlurRadius(float blurRadius) {
        this.blurRadius = clamp(blurRadius, 0f, 48f);
        liquidLayer.setBlurHint(this.blurRadius);
        updateContainerVisuals();
    }

    public void setCornerRadius(float cornerRadius) {
        this.cornerRadiusDp = clamp(cornerRadius, 8f, 44f);
        updateContainerVisuals();
        requestLayout();
    }

    public void setHighlightStrength(float highlightStrength) {
        this.highlightStrength = clamp(highlightStrength, 0f, 1f);
        liquidLayer.setHighlightStrength(this.highlightStrength);
    }

    public void setEnableShimmer(boolean enableShimmer) {
        this.enableShimmer = enableShimmer;
        liquidLayer.setEnableShimmer(enableShimmer);
    }

    public void setHighContrastMode(boolean highContrastMode) {
        this.highContrastMode = highContrastMode;
        updateContainerVisuals();
        updateItemColors();
    }

    private void updateContainerVisuals() {
        int baseColor = highContrastMode ? Color.rgb(8, 10, 15) : Color.WHITE;
        int fillColor = ColorUtils.setAlphaComponent(baseColor, Math.round(255f * opacity));
        int strokeColor = highContrastMode
            ? ColorUtils.setAlphaComponent(Color.WHITE, 238)
            : ColorUtils.setAlphaComponent(Color.WHITE, 132);

        containerBackground.setColor(fillColor);
        containerBackground.setCornerRadius(dpF(cornerRadiusDp));
        containerBackground.setStroke(dp(1), strokeColor);

        indicatorBackground.setCornerRadius(dpF(Math.max(16f, cornerRadiusDp - 3f)));
        indicatorBackground.setColors(highContrastMode
            ? new int[]{0x99FFFFFF, 0x99FFE7A8}
            : new int[]{0x664158D0, 0x66C850C0});

        liquidLayer.setCornerRadius(dpF(cornerRadiusDp));
        liquidLayer.setHighlightStrength(highlightStrength);
        liquidLayer.setBlurHint(blurRadius);
    }

    private List<NavItem> defaultItems() {
        List<NavItem> defaults = new ArrayList<>();
        defaults.add(new NavItem("home", "Home", R.drawable.ic_home_outline, R.drawable.ic_home_filled));
        defaults.add(new NavItem("search", "Search", R.drawable.ic_search_outline, R.drawable.ic_search_filled));
        defaults.add(new NavItem("notifications", "Notifications", R.drawable.ic_notifications_outline, R.drawable.ic_notifications_filled));
        defaults.add(new NavItem("profile", "Profile", R.drawable.ic_account_outline, R.drawable.ic_account_filled));
        return defaults;
    }

    private ItemHolder createItem(NavItem item, int index) {
        Context context = getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setFocusable(true);
        root.setClickable(true);
        root.setId(View.generateViewId());
        root.setEnabled(item.enabled);
        root.setAlpha(item.enabled ? 1f : 0.45f);
        root.setMinimumHeight(dp(46));
        root.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.setTag(item.id);
        root.setContentDescription(item.label);
        root.setBackground(createItemStateBackground());
        root.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        ImageView icon = new ImageView(context);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        icon.setLayoutParams(iconParams);
        icon.setImageResource(item.iconRes);
        icon.setImageTintList(ColorStateList.valueOf(colorInactive()));

        TextView label = new TextView(context);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(2);
        label.setLayoutParams(labelParams);
        label.setText(item.label);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setTextColor(colorInactive());

        root.addView(icon);
        root.addView(label);

        root.setOnClickListener(v -> handleItemClick(item.id));
        root.setOnHoverListener((v, event) -> {
            if (!v.isEnabled()) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                animateHover(v, true);
                liquidLayer.setPointer(event.getX() + v.getX(), event.getY() + v.getY());
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) {
                liquidLayer.setPointer(event.getX() + v.getX(), event.getY() + v.getY());
            } else if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) {
                animateHover(v, false);
            }
            return false;
        });
        root.setOnTouchListener((v, event) -> {
            if (!v.isEnabled()) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                animatePress(v, true);
                liquidLayer.setPointer(event.getX() + v.getX(), event.getY() + v.getY());
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                liquidLayer.setPointer(event.getX() + v.getX(), event.getY() + v.getY());
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                animatePress(v, false);
            }
            return false;
        });
        root.setOnFocusChangeListener((v, hasFocus) -> {
            v.setSelected(hasFocus || Objects.equals(selectedId, item.id));
            if (hasFocus) {
                animateHover(v, true);
            } else {
                animateHover(v, false);
            }
        });

        return new ItemHolder(item, root, icon, label);
    }

    private void handleItemClick(@NonNull String id) {
        ItemHolder holder = itemMap.get(id);
        if (holder == null || !holder.item.enabled) {
            return;
        }
        if (!Objects.equals(selectedId, id)) {
            selectedId = id;
            syncSelectionVisuals(true);
        } else {
            syncSelectionVisuals(false);
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (onNavSelectedListener != null) {
            onNavSelectedListener.onNavSelected(id);
        }
    }

    private void syncSelectionVisuals(boolean animateIndicator) {
        updateItemColors();
        if (animateIndicator) {
            animateIndicatorToSelected();
        } else {
            moveIndicatorToSelectedNoAnim();
        }
    }

    private void updateItemColors() {
        for (Map.Entry<String, ItemHolder> entry : itemMap.entrySet()) {
            String id = entry.getKey();
            ItemHolder holder = entry.getValue();
            boolean selected = Objects.equals(selectedId, id);
            boolean enabled = holder.item.enabled;

            int iconRes = selected ? holder.item.activeIconRes : holder.item.iconRes;
            holder.icon.setImageResource(iconRes);

            int color;
            if (!enabled) {
                color = highContrastMode ? 0x90FFFFFF : 0x80FFFFFF;
            } else if (selected) {
                color = highContrastMode ? Color.WHITE : Color.WHITE;
            } else {
                color = colorInactive();
            }
            holder.icon.setImageTintList(ColorStateList.valueOf(color));
            holder.label.setTextColor(color);
            holder.root.setSelected(selected);
            holder.root.setBackground(createItemStateBackground());
        }
    }

    private void animateIndicatorToSelected() {
        ItemHolder holder = itemMap.get(selectedId);
        if (holder == null) {
            selectedIndicator.setAlpha(0f);
            return;
        }
        float targetX = holder.root.getX() + dp(4);
        float targetW = holder.root.getWidth() - dp(8);
        if (targetW <= 0f) {
            post(this::animateIndicatorToSelected);
            return;
        }
        if (indicatorAnimator != null) {
            indicatorAnimator.cancel();
        }
        indicatorStartX = selectedIndicator.getX();
        indicatorTargetX = targetX;
        indicatorStartW = selectedIndicator.getLayoutParams().width;
        indicatorTargetW = targetW;

        selectedIndicator.setAlpha(1f);
        indicatorAnimator = ValueAnimator.ofFloat(0f, 1f);
        indicatorAnimator.setDuration(INDICATOR_ANIM_MS);
        indicatorAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        indicatorAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            float x = indicatorStartX + (indicatorTargetX - indicatorStartX) * t;
            float w = indicatorStartW + (indicatorTargetW - indicatorStartW) * t;
            ViewGroup.LayoutParams lp = selectedIndicator.getLayoutParams();
            lp.width = Math.max(dp(44), Math.round(w));
            selectedIndicator.setLayoutParams(lp);
            selectedIndicator.setX(x);
            selectedIndicator.setY((glassContainer.getHeight() - selectedIndicator.getHeight()) * 0.5f);
        });
        indicatorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                indicatorAnimator = null;
            }
        });
        indicatorAnimator.start();
    }

    private void moveIndicatorToSelectedNoAnim() {
        ItemHolder holder = itemMap.get(selectedId);
        if (holder == null || holder.root.getWidth() <= 0) {
            selectedIndicator.setAlpha(0f);
            return;
        }
        ViewGroup.LayoutParams lp = selectedIndicator.getLayoutParams();
        lp.width = Math.max(dp(44), holder.root.getWidth() - dp(8));
        selectedIndicator.setLayoutParams(lp);
        selectedIndicator.setX(holder.root.getX() + dp(4));
        selectedIndicator.setY((glassContainer.getHeight() - selectedIndicator.getHeight()) * 0.5f);
        selectedIndicator.setAlpha(1f);
    }

    private void setupPointerTracking() {
        glassContainer.setOnTouchListener((v, event) -> {
            liquidLayer.setPointer(event.getX(), event.getY());
            return false;
        });
        glassContainer.setOnHoverListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER || event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) {
                liquidLayer.setPointer(event.getX(), event.getY());
            }
            return false;
        });
    }

    private void updateResponsiveLabels(int widthPx) {
        boolean showLabels = widthPx >= dp(520);
        for (ItemHolder holder : itemMap.values()) {
            holder.label.setVisibility(showLabels ? VISIBLE : GONE);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.icon.getLayoutParams();
            lp.topMargin = showLabels ? 0 : dp(2);
            holder.icon.setLayoutParams(lp);
        }
    }

    private int colorInactive() {
        if (highContrastMode) {
            return 0xE6FFFFFF;
        }
        return 0xCCFFFFFF;
    }

    private DrawableStateBackground createItemStateBackground() {
        return new DrawableStateBackground(getContext(), highContrastMode);
    }

    private void animateHover(View v, boolean entered) {
        v.animate()
            .scaleX(entered ? 1.02f : 1f)
            .scaleY(entered ? 1.02f : 1f)
            .setDuration(HOVER_ANIM_MS)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    private void animatePress(View v, boolean pressed) {
        v.animate()
            .scaleX(pressed ? 0.97f : 1f)
            .scaleY(pressed ? 0.97f : 1f)
            .setDuration(PRESS_ANIM_MS)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
        ));
    }

    private float dpF(float value) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
        );
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class ItemHolder {
        final NavItem item;
        final LinearLayout root;
        final ImageView icon;
        final TextView label;

        ItemHolder(NavItem item, LinearLayout root, ImageView icon, TextView label) {
            this.item = item;
            this.root = root;
            this.icon = icon;
            this.label = label;
        }
    }

    private static final class DrawableStateBackground extends RippleDrawable {
        DrawableStateBackground(Context context, boolean highContrast) {
            super(
                ColorStateList.valueOf(highContrast ? 0x33FFFFFF : 0x22FFFFFF),
                createContentDrawable(context, highContrast),
                null
            );
        }

        private static GradientDrawable createContentDrawable(Context context, boolean highContrast) {
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics()
            ));
            shape.setColor(0x00000000);
            shape.setStroke(
                Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics()
                )),
                highContrast ? 0xAAFFFFFF : 0x66FFFFFF
            );
            return shape;
        }
    }

    private static final class LiquidEffectLayer extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private float pointerX = 0f;
        private float pointerY = 0f;
        private float cornerRadius = 24f;
        private float highlightStrength = 0.34f;
        private float blurHint = 20f;
        private boolean shimmerEnabled = true;
        private float shimmerProgress = -0.5f;
        private long lastFrameTime = 0L;

        LiquidEffectLayer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            rect.set(0, 0, w, h);

            if (pointerX <= 0f && pointerY <= 0f) {
                pointerX = w * 0.5f;
                pointerY = h * 0.25f;
            }

            float radius = Math.max(w * 0.35f, dp(120));
            int coreAlpha = Math.round(120f * highlightStrength);
            int outerAlpha = Math.round(32f * highlightStrength);

            RadialGradient glow = new RadialGradient(
                pointerX,
                pointerY,
                radius,
                new int[]{
                    Color.argb(coreAlpha, 255, 255, 255),
                    Color.argb(outerAlpha, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
                },
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
            );
            paint.setShader(glow);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
            paint.setShader(null);

            if (shimmerEnabled) {
                long now = SystemClock.uptimeMillis();
                if (lastFrameTime == 0L) {
                    lastFrameTime = now;
                }
                float dt = (now - lastFrameTime) / 1000f;
                lastFrameTime = now;
                shimmerProgress += dt * 0.20f;
                if (shimmerProgress > 1.5f) {
                    shimmerProgress = -0.4f;
                }

                float sx = w * shimmerProgress - w * 0.2f;
                int shimmerAlpha = Math.round(54f + blurHint);
                LinearGradient shimmer = new LinearGradient(
                    sx, 0f, sx + w * 0.28f, h,
                    new int[]{
                        Color.argb(0, 255, 255, 255),
                        Color.argb(shimmerAlpha, 255, 255, 255),
                        Color.argb(0, 255, 255, 255)
                    },
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
                );
                shimmerPaint.setShader(shimmer);
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, shimmerPaint);
                shimmerPaint.setShader(null);
                postInvalidateOnAnimation();
            }
        }

        void setPointer(float x, float y) {
            pointerX = x;
            pointerY = y;
            postInvalidateOnAnimation();
        }

        void setCornerRadius(float cornerRadius) {
            this.cornerRadius = cornerRadius;
            invalidate();
        }

        void setHighlightStrength(float highlightStrength) {
            this.highlightStrength = clamp(highlightStrength, 0f, 1f);
            invalidate();
        }

        void setEnableShimmer(boolean enableShimmer) {
            shimmerEnabled = enableShimmer;
            if (!enableShimmer) {
                shimmerProgress = -0.4f;
            }
            lastFrameTime = 0L;
            postInvalidateOnAnimation();
        }

        void setBlurHint(float blurHint) {
            this.blurHint = clamp(blurHint, 0f, 48f);
            invalidate();
        }

        private int dp(int value) {
            return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
            ));
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("androidx.appcompat.widget.Toolbar");
        info.setContentDescription("Liquid navigation bar");
    }
}
