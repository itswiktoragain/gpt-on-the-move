package com.wiktor.bezelmotion;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public final class BezelSettings {
    public static final int POWER = 0;
    public static final int VOLUME_UP = 1;
    public static final int VOLUME_DOWN = 2;

    public static final String[] NAMES = {"Power", "Volume up", "Volume down"};
    public static final int[] MARKER_COLORS = {
            Color.rgb(187, 171, 249),
            Color.rgb(118, 212, 252),
            Color.rgb(116, 228, 191)
    };

    private static final float[] DEFAULT_Y = {0.46f, 0.25f, 0.33f};
    private static final String PREFS = "bezel_motion_settings";

    private BezelSettings() {}

    private static SharedPreferences pref(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getSide(Context context, int button) {
        return pref(context).getInt("side_" + button, 1);
    }

    public static float getY(Context context, int button) {
        return pref(context).getFloat("y_" + button, DEFAULT_Y[button]);
    }

    public static void setPosition(Context context, int button, int side, float y) {
        pref(context).edit()
                .putInt("side_" + button, side)
                .putFloat("y_" + button, Math.max(0.08f, Math.min(0.92f, y)))
                .apply();
    }

    public static boolean enabled(Context context) {
        return pref(context).getBoolean("effects_enabled", true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        pref(context).edit().putBoolean("effects_enabled", enabled).apply();
    }

    public static boolean wake(Context context) {
        return pref(context).getBoolean("power_wake", false);
    }

    public static void setWake(Context context, boolean enabled) {
        pref(context).edit().putBoolean("power_wake", enabled).apply();
    }

    public static int color(Context context) {
        return pref(context).getInt("effect_color", 0xFFBBABF9);
    }

    public static void setColor(Context context, int color) {
        pref(context).edit().putInt("effect_color", color).apply();
    }

    public static int style(Context context) {
        return pref(context).getInt("effect_style", 0);
    }

    public static void setStyle(Context context, int style) {
        pref(context).edit().putInt("effect_style", style).apply();
    }

    public static int size(Context context) {
        return pref(context).getInt("effect_size", 55);
    }

    public static void setSize(Context context, int size) {
        pref(context).edit().putInt("effect_size", size).apply();
    }

    public static int strength(Context context) {
        return pref(context).getInt("effect_strength", 60);
    }

    public static void setStrength(Context context, int strength) {
        pref(context).edit().putInt("effect_strength", strength).apply();
    }

    public static int speed(Context context) {
        return pref(context).getInt("effect_speed", 55);
    }

    public static void setSpeed(Context context, int speed) {
        pref(context).edit().putInt("effect_speed", speed).apply();
    }

    public static int animationDuration(Context context) {
        return 650 - 4 * speed(context);
    }
}
