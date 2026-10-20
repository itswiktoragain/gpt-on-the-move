package com.eelauncber.launcher;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Actual Android 12+ cross-window backdrop blur plus layered, translucent glass. */
final class GlassUi {
    private GlassUi() {}

    static GradientDrawable sheet(LauncherActivity a, int radius) {
        return material(a, radius,
                new int[]{0xB04D5C78, 0x91354057, 0xA12B344A},
                0x89F4F7FF);
    }
    static GradientDrawable card(LauncherActivity a, int radius) {
        return material(a, radius,
                new int[]{0x8F647590, 0x73515D77, 0x8652617A},
                0x69F5F9FF);
    }
    static GradientDrawable input(LauncherActivity a, int radius) {
        return material(a, radius,
                new int[]{0x967B8BA4, 0x7B657590, 0x81727E97},
                0x68FFFFFF);
    }
    static GradientDrawable chip(LauncherActivity a, int radius) {
        return material(a, radius,
                new int[]{0x9D667998, 0x75647694},
                0x65FFFFFF);
    }
    private static GradientDrawable material(LauncherActivity a, int radius,
                                              int[] colors, int border) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(LauncherUi.dp(a, radius));
        drawable.setStroke(Math.max(1, LauncherUi.dp(a, 1)), border);
        return drawable;
    }

    static void configure(LauncherActivity a, Window window, boolean fullScreen) {
        if (window == null) return;
        // Translucent window backgrounds are required for the platform blur compositor.
        window.setBackgroundDrawable(new ColorDrawable(fullScreen ? 0x110B1220 : 0x150B1020));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(fullScreen ? .10f : .14f);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (fullScreen) {
            window.setGravity(Gravity.TOP);
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            window.setGravity(Gravity.BOTTOM);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // This uses the hardware/system window-blur compositor, not an opaque tint.
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.setBlurBehindRadius(LauncherUi.dp(a, fullScreen ? 70 : 58));
            window.setAttributes(params);
            window.setBackgroundBlurRadius(LauncherUi.dp(a, fullScreen ? 42 : 32));
        }
    }

    static void show(LauncherActivity a, FrameLayout overlay, LinearLayout panel) {
        if (a.isFinishing() || a.isDestroyed()) return;
        Dialog dialog = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setContentView(overlay);
        Window window = dialog.getWindow();
        configure(a, window, true);
        dialog.setOnDismissListener(d -> {
            if (a.glassDialog == dialog) a.glassDialog = null;
        });
        a.glassDialog = dialog;
        dialog.show();
        if (window != null) window.setLayout(-1, -1);
        panel.setAlpha(.82f);
        panel.setTranslationY(LauncherUi.dp(a, 85));
        panel.animate().alpha(1f).translationY(0).setDuration(285).start();
    }
}
