package com.wiktor.bezelmotion;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Standalone native interface: no WebView or third-party runtime. */
public final class MainActivity extends Activity {
    private static final int BG = 0xFF0B0C12;
    private static final int PANEL = 0xFF171922;
    private static final int PANEL_BRIGHT = 0xFF20222F;
    private static final int WHITE = 0xFFF4F2FC;
    private static final int MUTED = 0xFF9B9BAD;
    private static final int STROKE = 0xFF353543;
    private static final int ACCENT = 0xFFBBABF9;

    private TextView serviceStatus;
    private TextView serviceButton;
    private TextView selectedPosition;
    private PhonePreview phonePreview;
    private EdgeEffectView previewEffect;
    private final List<TextView> styleChoices = new ArrayList<>();
    private final List<View> colorChoices = new ArrayList<>();

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setFontFeatureSettings("kern");
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable panel(int color, int radius, int border) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) shape.setStroke(dp(1f), border);
        return shape;
    }

    private LinearLayout linear(boolean vertical) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        return view;
    }

    private void spacer(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private LinearLayout card(LinearLayout root) {
        LinearLayout c = linear(true);
        c.setPadding(dp(16), dp(17), dp(16), dp(17));
        c.setBackground(panel(PANEL, 22, STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        root.addView(c, lp);
        return c;
    }

    private void sectionTitle(LinearLayout c, String label) {
        TextView t = text(label, 18f, WHITE, true);
        c.addView(t, new LinearLayout.LayoutParams(-1, dp(30)));
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);

        FrameLayout main = new FrameLayout(this);
        main.setBackgroundColor(BG);
        setContentView(main);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setClipToPadding(false);
        scroller.setVerticalScrollBarEnabled(false);
        main.addView(scroller, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = linear(true);
        root.setPadding(dp(18), dp(22), dp(18), dp(38));
        scroller.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = linear(false);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(66)));

        TextView symbol = text("◖", 30f, ACCENT, true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(panel(PANEL_BRIGHT, 17, STROKE));
        header.addView(symbol, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout heading = linear(true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(14), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(-1, -1));
        heading.addView(text("Bezel Motion", 26f, WHITE, true));
        heading.addView(text("Animated side-button feedback", 13f, MUTED, false));
        spacer(root, 17);

        LinearLayout accessCard = card(root);
        LinearLayout accessTop = linear(false);
        accessTop.setGravity(Gravity.CENTER_VERTICAL);
        accessCard.addView(accessTop, new LinearLayout.LayoutParams(-1, dp(44)));

        serviceStatus = text("● Checking accessibility…", 14f, ACCENT, true);
        accessTop.addView(serviceStatus, new LinearLayout.LayoutParams(0, -1, 1f));
        serviceButton = text("Set up  →", 13f, WHITE, true);
        serviceButton.setGravity(Gravity.CENTER);
        serviceButton.setPadding(dp(10), 0, dp(10), 0);
        serviceButton.setBackground(panel(PANEL_BRIGHT, 12, STROKE));
        accessTop.addView(serviceButton, new LinearLayout.LayoutParams(-2, dp(38)));
        serviceButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {
                // Some manufacturer builds disable this shortcut.
            }
        });

        TextView accessInfo = text(
                "Enable the accessibility service once to detect volume buttons in other apps.",
                13f, MUTED, false);
        accessInfo.setPadding(0, dp(7), 0, 0);
        accessCard.addView(accessInfo);

        LinearLayout positioning = card(root);
        LinearLayout positionTitle = linear(false);
        positionTitle.setGravity(Gravity.CENTER_VERTICAL);
        positioning.addView(positionTitle, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView placeTitle = text("Button placement", 18f, WHITE, true);
        positionTitle.addView(placeTitle, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView reset = text("Reset", 13f, ACCENT, true);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(8), 0, dp(8), 0);
        positionTitle.addView(reset, new LinearLayout.LayoutParams(-2, -1));
        reset.setOnClickListener(v -> {
            BezelSettings.setPosition(this, 0, 1, .46f);
            BezelSettings.setPosition(this, 1, 1, .25f);
            BezelSettings.setPosition(this, 2, 1, .33f);
            phonePreview.choose(BezelSettings.POWER);
            updatePositionInfo(BezelSettings.POWER);
        });
        TextView hint = text("Drag the dots to the real button locations. Both edges work.",
                13f, MUTED, false);
        hint.setPadding(0, dp(5), 0, 0);
        positioning.addView(hint);

        phonePreview = new PhonePreview();
        positioning.addView(phonePreview, new LinearLayout.LayoutParams(-1, dp(354)));

        LinearLayout legends = linear(false);
        legends.setGravity(Gravity.CENTER);
        positioning.addView(legends, new LinearLayout.LayoutParams(-1, dp(35)));
        addLegend(legends, BezelSettings.POWER, "Power");
        addLegend(legends, BezelSettings.VOLUME_UP, "Vol +");
        addLegend(legends, BezelSettings.VOLUME_DOWN, "Vol −");

        selectedPosition = text("", 13f, MUTED, false);
        selectedPosition.setGravity(Gravity.CENTER);
        positioning.addView(selectedPosition,
                new LinearLayout.LayoutParams(-1, dp(29)));
        updatePositionInfo(BezelSettings.POWER);

        LinearLayout testing = card(root);
        sectionTitle(testing, "Try the effect");
        spacer(testing, 9);
        LinearLayout tester = linear(false);
        testing.addView(tester, new LinearLayout.LayoutParams(-1, dp(48)));
        addTestButton(tester, BezelSettings.POWER, "Power");
        addTestButton(tester, BezelSettings.VOLUME_UP, "Volume +");
        addTestButton(tester, BezelSettings.VOLUME_DOWN, "Volume −");

        LinearLayout appearance = card(root);
        sectionTitle(appearance, "Appearance");
        spacer(appearance, 11);

        LinearLayout styles = linear(false);
        appearance.addView(styles, new LinearLayout.LayoutParams(-1, dp(44)));
        addStyle(styles, "Glass", 0);
        addStyle(styles, "Ripple", 1);
        addStyle(styles, "Glow", 2);
        refreshStyles();

        spacer(appearance, 17);
        TextView accentLabel = text("Effect color", 14f, WHITE, true);
        appearance.addView(accentLabel);
        spacer(appearance, 12);

        LinearLayout palette = linear(false);
        palette.setGravity(Gravity.CENTER_VERTICAL);
        appearance.addView(palette, new LinearLayout.LayoutParams(-1, dp(46)));
        int[] colors = {0xFFBBABF9, 0xFF80D8FF, 0xFF78E8BF, 0xFFFFB4A8, 0xFFF4F4FF};
        for (int color : colors) {
            View swatch = new View(this);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(37), dp(37));
            slp.rightMargin = dp(13);
            swatch.setTag(color);
            palette.addView(swatch, slp);
            colorChoices.add(swatch);
            swatch.setOnClickListener(v -> {
                BezelSettings.setColor(this, color);
                refreshColors();
                phonePreview.invalidate();
                showDemo(BezelSettings.POWER);
            });
        }
        refreshColors();

        spacer(appearance, 14);
        addSlider(appearance, "Effect size", BezelSettings.size(this),
                value -> BezelSettings.setSize(this, value));
        addSlider(appearance, "Indentation depth", BezelSettings.strength(this),
                value -> BezelSettings.setStrength(this, value));
        addSlider(appearance, "Animation speed", BezelSettings.speed(this),
                value -> BezelSettings.setSpeed(this, value));

        LinearLayout behavior = card(root);
        sectionTitle(behavior, "Behavior");
        spacer(behavior, 9);
        addSwitch(behavior, "Enable effects",
                "Turn all button animations on or off", BezelSettings.enabled(this),
                BezelSettings::setEnabled);
        spacer(behavior, 17);
        addSwitch(behavior, "Animate when screen wakes",
                "Show an effect at the Power marker when the display turns on",
                BezelSettings.wake(this), BezelSettings::setWake);

        LinearLayout limitation = card(root);
        TextView title = text("About the Power button", 15f, WHITE, true);
        limitation.addView(title);
        spacer(limitation, 8);
        TextView info = text(
                "Android does not normally allow apps to intercept the Power button. " +
                "You can test its effect here, or enable the screen-wake option as a fallback. " +
                "The animation cannot appear while the screen is off.\n\n" +
                "No screen content is read and volume changes remain untouched.",
                13f, MUTED, false);
        limitation.addView(info);

        previewEffect = new EdgeEffectView(this);
        FrameLayout.LayoutParams effectLayout = new FrameLayout.LayoutParams(-1, -1);
        main.addView(previewEffect, effectLayout);
        previewEffect.setClickable(false);
    }

    private void addLegend(LinearLayout row, int which, String name) {
        LinearLayout item = linear(false);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        row.addView(item, lp);
        View dot = new View(this);
        dot.setBackground(panel(BezelSettings.MARKER_COLORS[which], 8, Color.TRANSPARENT));
        item.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView label = text(name, 12f, WHITE, false);
        label.setPadding(dp(5), 0, 0, 0);
        item.addView(label);
        item.setOnClickListener(v -> {
            phonePreview.choose(which);
            updatePositionInfo(which);
        });
    }

    private void addTestButton(LinearLayout row, int which, String label) {
        TextView button = text(label, 13f, WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(panel(PANEL_BRIGHT, 13, STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        row.addView(button, lp);
        button.setOnClickListener(v -> showDemo(which));
    }

    private void showDemo(int which) {
        phonePreview.choose(which);
        updatePositionInfo(which);
        previewEffect.trigger(which);
        phonePreview.pulse(which);
    }

    private void updatePositionInfo(int which) {
        if (selectedPosition == null) return;
        String side = BezelSettings.getSide(this, which) == 1 ? "Right" : "Left";
        selectedPosition.setText(String.format(Locale.US, "%s  ·  %s side  ·  %d%%",
                BezelSettings.NAMES[which], side,
                Math.round(BezelSettings.getY(this, which) * 100f)));
    }

    private void addStyle(LinearLayout row, String title, int index) {
        TextView button = text(title, 14f, WHITE, true);
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        row.addView(button, lp);
        styleChoices.add(button);
        button.setOnClickListener(v -> {
            BezelSettings.setStyle(this, index);
            refreshStyles();
            showDemo(BezelSettings.POWER);
        });
    }

    private void refreshStyles() {
        int selected = BezelSettings.style(this);
        for (int index = 0; index < styleChoices.size(); index++) {
            TextView view = styleChoices.get(index);
            boolean active = index == selected;
            view.setBackground(panel(active ? 0xFF39314D : PANEL_BRIGHT, 12,
                    active ? ACCENT : STROKE));
            view.setTextColor(active ? 0xFFE4D9FF : MUTED);
        }
    }

    private void refreshColors() {
        int current = BezelSettings.color(this);
        for (View view : colorChoices) {
            int c = (int) view.getTag();
            GradientDrawable background = panel(c, 22,
                    c == current ? WHITE : 0xFF2B2D3A);
            background.setStroke(dp(c == current ? 3 : 1), c == current ? WHITE : STROKE);
            view.setBackground(background);
        }
    }

    private interface SliderChange {
        void changed(int value);
    }

    private void addSlider(LinearLayout root, String label, int initial, SliderChange listener) {
        LinearLayout heading = linear(false);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(31)));
        TextView name = text(label, 14f, WHITE, false);
        heading.addView(name, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView number = text(initial + "%", 13f, MUTED, false);
        number.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        heading.addView(number, new LinearLayout.LayoutParams(dp(48), -1));
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(initial);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(39)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int progress, boolean user) {
                if (!user) return;
                number.setText(progress + "%");
                listener.changed(progress);
                phonePreview.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {
                showDemo(BezelSettings.POWER);
            }
        });
        spacer(root, 7);
    }

    private interface BooleanChange {
        void changed(android.content.Context context, boolean value);
    }

    private void addSwitch(LinearLayout container, String title, String detail, boolean initial,
                           BooleanChange listener) {
        LinearLayout row = linear(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(row, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout labels = linear(true);
        labels.addView(text(title, 15f, WHITE, true));
        TextView subtitle = text(detail, 12f, MUTED, false);
        subtitle.setPadding(0, dp(3), dp(12), 0);
        labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(initial);
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        toggle.setOnCheckedChangeListener((button, value) -> listener.changed(this, value));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
    }

    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName target = new ComponentName(this, SideKeyService.class);
        String full = target.flattenToString();
        String shortName = target.flattenToShortString();
        for (String entry : enabled.split(":")) {
            if (entry.equalsIgnoreCase(full) || entry.equalsIgnoreCase(shortName)) return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (serviceStatus == null) return;
        boolean ready = serviceEnabled();
        serviceStatus.setText(ready ? "● Button detection enabled" : "● Setup required");
        serviceStatus.setTextColor(ready ? 0xFF82E7C0 : 0xFFFFC28F);
        serviceButton.setText(ready ? "Settings  →" : "Enable  →");
    }

    private final class PhonePreview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lettering = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float left, top, right, bottom;
        private int chosen = BezelSettings.POWER;
        private int dragging = -1;
        private int pulsing = -1;

        PhonePreview() {
            super(MainActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setContentDescription("Phone preview. Drag the colored button markers along either edge.");
        }

        void choose(int button) {
            chosen = button;
            invalidate();
        }

        void pulse(int button) {
            pulsing = button;
            invalidate();
            postDelayed(() -> {
                pulsing = -1;
                invalidate();
            }, 350L);
        }

        private float mx(int index) {
            return BezelSettings.getSide(MainActivity.this, index) == 0 ?
                    left - dp(13) : right + dp(13);
        }

        private float my(int index) {
            return top + (bottom - top) * BezelSettings.getY(MainActivity.this, index);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float h = getHeight();
            float w = getWidth();
            float phoneWidth = Math.min(w * .52f, dp(178));
            float phoneHeight = Math.min(h - dp(23), phoneWidth * 2.06f);
            left = (w - phoneWidth) / 2f;
            right = left + phoneWidth;
            top = (h - phoneHeight) / 2f;
            bottom = top + phoneHeight;

            // Subtle phone shadow and metallic frame.
            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(0xFF08080B);
            paint.setShadowLayer(dp(17), 0, dp(9), 0xAD000000);
            rect.set(left - dp(3), top - dp(3), right + dp(3), bottom + dp(3));
            canvas.drawRoundRect(rect, dp(30), dp(30), paint);
            paint.clearShadowLayer();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xFF77738D);
            canvas.drawRoundRect(rect, dp(30), dp(30), paint);
            paint.setStyle(Paint.Style.FILL);

            rect.set(left, top, right, bottom);
            paint.setShader(new LinearGradient(left, top, right, bottom,
                    new int[] {0xFF171C3C, 0xFF392C63, 0xFF1E183A, 0xFF0F1526},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, dp(26), dp(26), paint);
            paint.setShader(null);

            // Soft background circles add depth instead of a flat mockup.
            paint.setColor(0x263DAEE8);
            canvas.drawCircle(right - phoneWidth * .09f, top + phoneHeight * .57f,
                    phoneWidth * .58f, paint);
            paint.setColor(0x236E58B1);
            canvas.drawCircle(left + phoneWidth * .05f, bottom - phoneHeight * .16f,
                    phoneWidth * .48f, paint);

            paint.setColor(0xE9080913);
            rect.set(w / 2f - dp(29), top + dp(9), w / 2f + dp(29), top + dp(19));
            canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            lettering.reset();
            lettering.setAntiAlias(true);
            lettering.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            lettering.setTextAlign(Paint.Align.CENTER);
            lettering.setTextSize(dp(37));
            lettering.setColor(0xFFF8F2FF);
            canvas.drawText("09:41", w / 2f, top + phoneHeight * .38f, lettering);
            lettering.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            lettering.setTextSize(dp(10));
            lettering.setColor(0xD6EEE5FF);
            canvas.drawText("WED, OCT 21", w / 2f, top + phoneHeight * .43f, lettering);

            paint.setColor(0xC9FFFFFF);
            rect.set(w / 2f - dp(23), bottom - dp(12), w / 2f + dp(23),
                    bottom - dp(9));
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);

            // Hardware button positions and large, clearly selectable handles.
            for (int i = 0; i < 3; i++) {
                float x = mx(i);
                float y = my(i);
                int color = BezelSettings.MARKER_COLORS[i];
                float r = dp(i == chosen ? 9f : 7.5f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor((color & 0x00FFFFFF) | 0x3A000000);
                canvas.drawCircle(x, y, r + dp(9), paint);
                if (i == chosen || i == pulsing) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(i == pulsing ? 3 : 1.5f));
                    paint.setColor((color & 0x00FFFFFF) | 0xC9000000);
                    canvas.drawCircle(x, y, r + dp(6), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                paint.setColor(color);
                canvas.drawCircle(x, y, r, paint);
                paint.setColor(0xF4FFFFFF);
                canvas.drawCircle(x - dp(1.4f), y - dp(1.6f), dp(2f), paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    int nearest = -1;
                    float best = dp(38) * dp(38);
                    for (int i = 0; i < 3; i++) {
                        float dx = event.getX() - mx(i);
                        float dy = event.getY() - my(i);
                        float distance = dx * dx + dy * dy;
                        if (distance < best) {
                            best = distance;
                            nearest = i;
                        }
                    }
                    if (nearest == -1) return false;
                    dragging = nearest;
                    chosen = nearest;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    updatePositionInfo(chosen);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (dragging == -1) return false;
                    float fraction = (event.getY() - top) / (bottom - top);
                    int side = event.getX() < (left + right) / 2f ? 0 : 1;
                    BezelSettings.setPosition(MainActivity.this, dragging, side, fraction);
                    updatePositionInfo(dragging);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging == -1) return false;
                    dragging = -1;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    performClick();
                    invalidate();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = -1;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    invalidate();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
