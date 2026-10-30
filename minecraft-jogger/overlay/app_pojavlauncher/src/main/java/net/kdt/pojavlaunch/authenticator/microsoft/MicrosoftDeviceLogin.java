package net.kdt.pojavlaunch.authenticator.microsoft;

import android.os.SystemClock;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

/** Switch-style sign-in: Microsoft creates the code and the user approves it on microsoft.com/link. */
public final class MicrosoftDeviceLogin {
    private static final String DEVICE_ENDPOINT = "https://login.live.com/oauth20_connect.srf";
    private static final String TOKEN_ENDPOINT = "https://login.live.com/oauth20_token.srf";
    private volatile boolean cancelled;
    private volatile HttpURLConnection activeConnection;
    private volatile MicrosoftBackgroundLogin accountLogin;
    private Future<?> task;

    public interface Listener {
        void onCode(String userCode, String verificationUri, int expiresInSeconds);
        void onStatus(String message);
        void onSuccess(MinecraftAccount account);
        void onError(Throwable error);
    }

    /** Start once. The listener always runs on the Android main thread. */
    public synchronized void start(Listener listener) {
        if (task != null) throw new IllegalStateException("This device sign-in has already started.");
        task = PojavApplication.sExecutorService.submit(() -> run(listener));
    }

    public void cancel() {
        cancelled = true;
        MicrosoftBackgroundLogin login = accountLogin;
        if (login != null) login.cancel();
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        Future<?> future = task;
        if (future != null) future.cancel(true);
    }

    private void checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    private void post(Runnable callback) {
        Tools.runOnUiThread(() -> { if (!cancelled) callback.run(); });
    }

    private void run(Listener listener) {
        try {
            Response response = postForm(DEVICE_ENDPOINT, MicrosoftBackgroundLogin.form(
                    "client_id", MicrosoftBackgroundLogin.CLIENT_ID,
                    "scope", MicrosoftBackgroundLogin.SCOPE,
                    "response_type", "device_code"));
            if (!response.success()) throw serviceError(response, "Microsoft could not create a sign-in code");
            JSONObject code = response.json;
            String deviceCode = code.getString("device_code");
            String userCode = code.getString("user_code");
            String verificationUri = code.getString("verification_uri");
            validateVerificationUri(verificationUri);
            if (deviceCode.isEmpty() || userCode.isEmpty()) throw new IOException("Microsoft returned an empty sign-in code.");
            int lifetime = Math.max(1, Math.min(1800, code.optInt("expires_in", 900)));
            long interval = DeviceCodePolicy.initialIntervalMillis(code.optLong("interval", 5));
            long deadline = SystemClock.elapsedRealtime() + lifetime * 1000L;
            post(() -> listener.onCode(userCode, verificationUri, lifetime));
            while (true) {
                checkCancelled();
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) throw new IOException("This sign-in code has expired. Choose Sign in with a code to get a new one.");
                Thread.sleep(Math.min(interval, remaining));
                checkCancelled();
                if (SystemClock.elapsedRealtime() >= deadline) throw new IOException("This sign-in code has expired. Please request a new code.");
                Response token;
                try {
                    token = postForm(TOKEN_ENDPOINT, MicrosoftBackgroundLogin.form(
                            "client_id", MicrosoftBackgroundLogin.CLIENT_ID,
                            "scope", MicrosoftBackgroundLogin.SCOPE,
                            "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                            "device_code", deviceCode));
                } catch (IOException networkError) {
                    checkCancelled();
                    post(() -> listener.onStatus("Waiting for your connection. Your sign-in code is still active."));
                    interval = Math.min(30000, interval + 1000);
                    continue;
                }
                DeviceCodePolicy.Action action = DeviceCodePolicy.classify(token.status, token.json.optString("error", ""));
                if (action == DeviceCodePolicy.Action.WAIT || action == DeviceCodePolicy.Action.RETRY) continue;
                if (action == DeviceCodePolicy.Action.SLOW_DOWN) {
                    interval = DeviceCodePolicy.slowerIntervalMillis(interval);
                    continue;
                }
                if (action == DeviceCodePolicy.Action.EXPIRED) {
                    throw new IOException("This sign-in code has expired. Please request a new code.");
                }
                if (action == DeviceCodePolicy.Action.DECLINED) {
                    throw new IOException("You declined this Microsoft sign-in. No account was added.");
                }
                if (action != DeviceCodePolicy.Action.ACCEPT) throw serviceError(token, "Microsoft could not finish sign-in");
                String accessToken = token.json.getString("access_token");
                String refreshToken = token.json.getString("refresh_token");
                if (accessToken.isEmpty() || refreshToken.isEmpty()) throw new IOException("Microsoft returned an incomplete session. Please sign in again.");
                post(() -> listener.onStatus("Code accepted. Connecting your Minecraft profile…"));
                accountLogin = MicrosoftBackgroundLogin.fromDeviceTokens(accessToken, refreshToken);
                checkCancelled();
                MinecraftAccount account = accountLogin.loginBlocking(step -> {
                    String[] stages = {"", "Connecting to Microsoft…", "Signing in to Xbox Live…", "Authorizing Minecraft…", "Opening your Minecraft session…", "Loading your Minecraft profile…"};
                    post(() -> listener.onStatus(stages[Math.max(0, Math.min(5, step))]));
                });
                post(() -> listener.onSuccess(account));
                return;
            }
        } catch (CancellationException ignored) {
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (JSONException e) {
            post(() -> listener.onError(new IOException("Microsoft returned an unexpected sign-in response. Please try again.")));
        } catch (Exception e) {
            post(() -> listener.onError(e));
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE_MICROSOFT);
        }
    }

    private void validateVerificationUri(String value) throws IOException {
        if (!DeviceCodePolicy.isMicrosoftVerificationUri(value)) {
            throw new IOException("Microsoft returned an unexpected sign-in address.");
        }
    }

    private Response postForm(String endpoint, String form) throws IOException, JSONException {
        checkCancelled();
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        activeConnection = connection;
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        byte[] bytes = form.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try {
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String content = "";
            if (input != null) try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    checkCancelled();
                    if (output.size() + count > 1048576) throw new IOException("Microsoft returned an oversized response.");
                    output.write(buffer, 0, count);
                }
                content = output.toString("UTF-8");
            }
            checkCancelled();
            return new Response(status, content.isEmpty() ? new JSONObject() : new JSONObject(content));
        } finally {
            connection.disconnect();
            activeConnection = null;
        }
    }

    private IOException serviceError(Response response, String message) {
        // Never show raw service bodies, which may contain token or account details.
        String error = response.json.optString("error", "");
        if (response.status == 429) return new IOException("Microsoft is busy. Wait a moment, then request a new code.");
        if ("unauthorized_client".equals(error) || "invalid_client".equals(error)) {
            return new IOException("Microsoft did not accept code sign-in for this launcher. Use Browser sign-in instead.");
        }
        return new IOException(message + " (HTTP " + response.status + "). Try again or use Browser sign-in.");
    }

    private static final class Response {
        final int status;
        final JSONObject json;
        Response(int status, JSONObject json) { this.status = status; this.json = json; }
        boolean success() { return status >= 200 && status < 300; }
    }
}
