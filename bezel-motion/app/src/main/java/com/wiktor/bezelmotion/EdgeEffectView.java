package com.wiktor.bezelmotion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Draws a liquid-glass indentation at a configured side button location.
 * It is transparent and non-interactive. Used both for app previews and
 * the accessibility overlay above other applications.
 */
public final class EdgeEffectView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final Path hairline = new Path();
    private ValueAnimator animator;
    private float timeline = 1f;
    private int activeButton = BezelSettings.POWER;

    public EdgeEffectView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void trigger(int button) {
        if (animator != null) animator.cancel();
        activeButton = button;
        timeline = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(BezelSettings.animationDuration(getContext()));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            timeline = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private float dp(float n) {
        return n * getResources().getDisplayMetrics().density;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (timeline >= 1f) return;

        float pressure;
        if (timeline < 0.17f) {
            float t = timeline / 0.17f;
            pressure = 1f - (1f - t) * (1f - t);
        } else if (timeline < 0.43f) {
            pressure = 1f;
        } else {
            float t = (timeline - 0.43f) / 0.57f;
            pressure = (1f - t) * (1f - t);
        }
        if (pressure < 0.001f) return;

        boolean right = BezelSettings.getSide(getContext(), activeButton) == 1;
        float direction = right ? -1f : 1f;
        float edge = right ? getWidth() : 0f;
        float depth = dp(13f + BezelSettings.size(getContext()) * 0.47f) * pressure;
        float radius = dp(43f + BezelSettings.strength(getContext()) * 0.67f);
        float cy = getHeight() * BezelSettings.getY(getContext(), activeButton);
        cy = Math.max(radius + dp(3f), Math.min(getHeight() - radius - dp(3f), cy));
        float xTip = edge + direction * depth;
        int color = BezelSettings.color(getContext());
        int style = BezelSettings.style(getContext());

        outline.reset();
        outline.moveTo(edge, cy - radius);
        outline.cubicTo(edge + direction * depth * 0.12f, cy - radius * 0.89f,
                xTip - direction * depth * 0.10f, cy - radius * 0.56f, xTip, cy);
        outline.cubicTo(xTip - direction * depth * 0.10f, cy + radius * 0.56f,
                edge + direction * depth * 0.12f, cy + radius * 0.89f,
                edge, cy + radius);
        outline.close();

        int glow = style == 2 ? 110 : 45;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color, (int) (glow * pressure)));
        paint.setShadowLayer(dp(style == 2 ? 24f : 12f) * pressure,
                direction * dp(2f), 0, withAlpha(color, (int) (glow * pressure)));
        canvas.drawPath(outline, paint);
        paint.clearShadowLayer();

        paint.setShader(new LinearGradient(edge, cy, xTip, cy,
                new int[] {
                        withAlpha(Color.BLACK, style == 2 ? 170 : 228),
                        withAlpha(0xFF181A26, style == 0 ? 248 : 205),
                        withAlpha(0xFF252938, style == 2 ? 125 : 224)
                },
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(outline, paint);
        paint.setShader(null);

        // Soft tinted internal surface: visible even on otherwise dark screens.
        canvas.save();
        canvas.clipPath(outline);
        paint.setShader(new RadialGradient(xTip, cy, radius * 0.70f,
                new int[] {
                        withAlpha(color, (int) (pressure * (style == 2 ? 170 : 64))),
                        withAlpha(color, (int) (pressure * 18)),
                        Color.TRANSPARENT
                },
                new float[] {0f, .35f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(xTip, cy, radius * 0.70f, paint);
        paint.setShader(null);
        canvas.restore();

        highlight.reset();
        highlight.setAntiAlias(true);
        highlight.setStyle(Paint.Style.STROKE);
        highlight.setStrokeCap(Paint.Cap.ROUND);
        highlight.setStrokeWidth(dp(style == 2 ? 2.8f : 1.55f));
        highlight.setColor(withAlpha(color, (int) (pressure * (style == 2 ? 240 : 145))));
        hairline.reset();
        hairline.moveTo(edge + direction * depth * 0.06f, cy - radius * .72f);
        hairline.cubicTo(edge + direction * depth * .22f, cy - radius * .44f,
                xTip, cy - radius * .27f, xTip, cy);
        hairline.cubicTo(xTip, cy + radius * .27f,
                edge + direction * depth * .22f, cy + radius * .44f,
                edge + direction * depth * .06f, cy + radius * .72f);
        canvas.drawPath(hairline, highlight);

        if (style == 1) {
            // Subtle secondary wave following the indentation, not a static sticker.
            for (int i = 1; i <= 2; i++) {
                float inset = dp(i * 5f);
                float d = Math.max(0f, depth - inset);
                highlight.setColor(withAlpha(color, (int) (pressure * (95 - i * 30))));
                highlight.setStrokeWidth(dp(1.0f));
                hairline.reset();
                hairline.moveTo(edge + direction * dp(i * 2f), cy - radius * (0.7f - i * 0.14f));
                hairline.quadTo(edge + direction * d, cy, edge + direction * dp(i * 2f),
                        cy + radius * (0.7f - i * 0.14f));
                canvas.drawPath(hairline, highlight);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
