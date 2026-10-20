package com.wiktor.cornerclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextClock;

public class ClockService extends Service {
    private static final String CHANNEL = "clock_overlay";
    private WindowManager windowManager;
    private View clockView;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Clock overlay", NotificationManager.IMPORTANCE_LOW));
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle("Corner Clock")
                .setContentText("Displaying the time over apps")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (clockView == null) {
            try {
                showClock();
            } catch (RuntimeException e) {
                android.util.Log.e("CornerClock", "Unable to create overlay", e);
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    private void showClock() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm a");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(dp(12), dp(7), dp(12), dp(7));
        clock.setBackgroundResource(R.drawable.clock_background);
        clock.setShadowLayer(dp(1.5f), 0, dp(1), 0x88000000);
        clock.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.x = dp(12);
        params.y = dp(14);
        // On Android 12+, keep pass-through overlays below the obscuring threshold.
        params.alpha = 0.75f;
        windowManager.addView(clock, params);
        clockView = clock;
    }

    private int dp(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        if (clockView != null && windowManager != null) {
            try { windowManager.removeView(clockView); }
            catch (RuntimeException ignored) {}
            clockView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
