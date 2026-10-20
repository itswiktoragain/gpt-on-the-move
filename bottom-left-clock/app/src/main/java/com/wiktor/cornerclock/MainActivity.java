package com.wiktor.cornerclock;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int OVERLAY_REQUEST = 1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Settings.canDrawOverlays(this)) {
            startClock();
            finish();
        } else {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                startClock();
            } else {
                Toast.makeText(this, "Allow Display over other apps to show the clock.",
                        Toast.LENGTH_LONG).show();
            }
            finish();
        }
    }

    private void startClock() {
        startForegroundService(new Intent(this, ClockService.class));
    }
}
