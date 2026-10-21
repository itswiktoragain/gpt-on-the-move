package com.wiktor.bezelmotion;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility is only used to observe supported hardware key events.
 * Does not inspect screen content, inject taps, or swallow volume changes.
 */
public final class SideKeyService extends AccessibilityService {
    private WindowManager windowManager;
    private EdgeEffectView overlay;
    private BroadcastReceiver wakeReceiver;
    private long lastPowerEvent = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        overlay = new EdgeEffectView(this);
        overlay.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("Bezel Motion animation overlay");
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        try {
            windowManager.addView(overlay, params);
        } catch (Exception e) {
            overlay = null;
        }

        wakeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Intent.ACTION_SCREEN_ON.equals(intent.getAction())) return;
                if (!BezelSettings.wake(SideKeyService.this)) return;
                if (SystemClock.elapsedRealtime() - lastPowerEvent < 1200L) return;
                // The display may not be ready at the exact wake broadcast.
                handler.postDelayed(() -> show(BezelSettings.POWER), 230L);
            }
        };
        registerReceiver(wakeReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    private void show(int button) {
        if (!BezelSettings.enabled(this)) return;
        if (overlay != null) overlay.trigger(button);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                    show(BezelSettings.VOLUME_UP);
                    break;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    show(BezelSettings.VOLUME_DOWN);
                    break;
                case KeyEvent.KEYCODE_POWER:
                    // Most stock Android builds never forward Power to Accessibility.
                    lastPowerEvent = SystemClock.elapsedRealtime();
                    show(BezelSettings.POWER);
                    break;
                default:
                    break;
            }
        }
        return false; // Keep volume changes and system behavior intact.
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally ignore accessibility events and all app content.
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (wakeReceiver != null) {
            try { unregisterReceiver(wakeReceiver); } catch (Exception ignored) {}
            wakeReceiver = null;
        }
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
            overlay = null;
        }
        super.onDestroy();
    }
}
