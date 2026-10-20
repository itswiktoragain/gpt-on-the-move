package com.wiktor.cornerclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && Settings.canDrawOverlays(context)) {
            try {
                context.startForegroundService(new Intent(context, ClockService.class));
            } catch (RuntimeException e) {
                Log.w("CornerClock", "Startup blocked by Android", e);
            }
        }
    }
}
