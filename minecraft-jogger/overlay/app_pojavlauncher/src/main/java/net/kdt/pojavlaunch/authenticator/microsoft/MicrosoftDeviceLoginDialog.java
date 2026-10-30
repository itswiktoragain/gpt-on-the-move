package net.kdt.pojavlaunch.authenticator.microsoft;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.value.MinecraftAccount;

/** Native account dialog shared by Jogger Home and the original account picker. */
public final class MicrosoftDeviceLoginDialog {
    private MicrosoftDeviceLoginDialog() {}

    public static void show(FragmentActivity activity, DoneListener doneListener) {
        show(activity, doneListener, () -> Tools.swapFragment(activity,
                MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
    }

    /** Browser fallback can open the launcher's existing Microsoft login screen. */
    public static void show(FragmentActivity activity, DoneListener doneListener, Runnable browserFallback) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        int margin = dp(activity, 24);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(margin, dp(activity, 8), margin, dp(activity, 6));
        content.setBackgroundColor(Color.WHITE);

        TextView instructions = text(activity, 15, "Open Microsoft's sign-in page, enter this code, then sign in using the Microsoft email that owns Minecraft: Java Edition.");
        instructions.setTextColor(Color.rgb(60, 64, 67));
        content.addView(instructions);

        TextView codeView = text(activity, 30, "··· ··· ···");
        codeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeView.setGravity(Gravity.CENTER);
        codeView.setTextIsSelectable(true);
        codeView.setPadding(0, dp(activity, 18), 0, dp(activity, 18));
        GradientDrawable codeBackground = new GradientDrawable();
        codeBackground.setColor(Color.rgb(244, 245, 245));
        codeBackground.setCornerRadius(dp(activity, 16));
        codeView.setBackground(codeBackground);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(-1, -2);
        codeParams.topMargin = dp(activity, 20);
        content.addView(codeView, codeParams);

        TextView addressView = text(activity, 15, "microsoft.com/link");
        addressView.setGravity(Gravity.CENTER);
        addressView.setPadding(0, dp(activity, 12), 0, dp(activity, 2));
        content.addView(addressView);

        TextView countdown = text(activity, 13, "");
        countdown.setTextColor(Color.DKGRAY);
        countdown.setGravity(Gravity.CENTER);
        content.addView(countdown);

        Button copy = new Button(activity);
        copy.setText("Copy code");
        copy.setAllCaps(false);
        copy.setTextColor(Color.BLACK);
        copy.setEnabled(false);
        content.addView(copy);

        TextView status = text(activity, 14, "Requesting your sign-in code…");
        status.setPadding(0, dp(activity, 12), 0, dp(activity, 4));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);

        TextView help = text(activity, 12, "The code is created by Microsoft. Jogger never asks for your Microsoft password. Minecraft is downloaded after sign-in.");
        help.setTextColor(Color.rgb(90, 94, 98));
        help.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
        content.addView(help);

        MicrosoftDeviceLogin login = new MicrosoftDeviceLogin();
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Sign in to Minecraft")
                .setView(content)
                .setPositiveButton("Open Microsoft", null)
                .setNeutralButton("Browser sign-in", (d, which) -> {
                    login.cancel();
                    if (browserFallback != null) browserFallback.run();
                })
                .setNegativeButton("Cancel", (d, which) -> login.cancel())
                .create();
        dialog.setCanceledOnTouchOutside(false);

        Handler handler = new Handler(Looper.getMainLooper());
        final String[] code = {null};
        final String[] uri = {null};
        final long[] expiry = {0};
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing() || expiry[0] == 0) return;
                long seconds = Math.max(0, (expiry[0] - SystemClock.elapsedRealtime() + 999) / 1000);
                countdown.setText(seconds > 0 ? "Code expires in " + (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60) : "Code expired");
                if (seconds > 0) handler.postDelayed(this, 1000);
            }
        };
        LifecycleEventObserver lifecycle = (owner, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) {
                login.cancel();
                dialog.dismiss();
            }
        };
        activity.getLifecycle().addObserver(lifecycle);
        dialog.setOnDismissListener(d -> {
            login.cancel();
            handler.removeCallbacks(tick);
            activity.getLifecycle().removeObserver(lifecycle);
        });
        dialog.setOnShowListener(d -> {
            Button open = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            open.setEnabled(false);
            open.setOnClickListener(v -> {
                if (uri[0] == null) return;
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri[0])));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(activity, "Open microsoft.com/link in a browser on this phone or another device.", Toast.LENGTH_LONG).show();
                }
            });
            copy.setOnClickListener(v -> {
                if (code[0] == null) return;
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Microsoft sign-in code", code[0]));
                Toast.makeText(activity, "Sign-in code copied", Toast.LENGTH_SHORT).show();
            });
            login.start(new MicrosoftDeviceLogin.Listener() {
                @Override public void onCode(String userCode, String verificationUri, int expiresInSeconds) {
                    if (!dialog.isShowing()) return;
                    code[0] = userCode;
                    uri[0] = verificationUri;
                    codeView.setText(userCode);
                    addressView.setText(Uri.parse(verificationUri).getHost() + (Uri.parse(verificationUri).getPath() == null ? "" : Uri.parse(verificationUri).getPath()));
                    expiry[0] = SystemClock.elapsedRealtime() + expiresInSeconds * 1000L;
                    open.setEnabled(true);
                    copy.setEnabled(true);
                    status.setText("Waiting for you to approve the code on Microsoft's page…");
                    handler.post(tick);
                }

                @Override public void onStatus(String message) {
                    if (dialog.isShowing()) status.setText(message);
                }

                @Override public void onSuccess(MinecraftAccount account) {
                    if (!dialog.isShowing()) return;
                    PojavProfile.setCurrentProfile(activity, account.username);
                    dialog.dismiss();
                    Toast.makeText(activity, "Signed in as " + account.username, Toast.LENGTH_SHORT).show();
                    if (doneListener != null) doneListener.onLoginDone(account);
                }

                @Override public void onError(Throwable error) {
                    if (!dialog.isShowing()) return;
                    handler.removeCallbacks(tick);
                    String message = error instanceof PresentedException
                            ? ((PresentedException) error).toString(activity)
                            : error.getMessage();
                    status.setText(message == null ? "Sign-in did not complete. Please try again." : message);
                    status.setTextColor(Color.rgb(167, 34, 34));
                    copy.setEnabled(false);
                    codeView.setText("—");
                    countdown.setText("");
                    open.setText("Try again");
                    open.setEnabled(true);
                    open.setOnClickListener(v -> {
                        dialog.dismiss();
                        show(activity, doneListener, browserFallback);
                    });
                }
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
    }

    private static TextView text(Context context, float size, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(20, 23, 26));
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
