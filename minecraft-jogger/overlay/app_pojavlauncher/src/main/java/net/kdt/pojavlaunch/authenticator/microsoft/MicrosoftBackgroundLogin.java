package net.kdt.pojavlaunch.authenticator.microsoft;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.util.ArrayMap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Microsoft -> Xbox Live -> XSTS -> Minecraft authentication. Tokens never enter logs. */
public class MicrosoftBackgroundLogin {
    public static final String CLIENT_ID = "00000000402b5328";
    public static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String MC_STORE_URL = "https://api.minecraftservices.com/entitlements/mcstore";

    private final boolean mIsRefresh;
    private final String mAuthCode;
    private final String mProvidedAccessToken;
    private volatile boolean mCancelled;
    private volatile HttpURLConnection mConnection;
    private static final Map<Long, Integer> XSTS_ERRORS = new ArrayMap<>();
    static {
        XSTS_ERRORS.put(2148916233L, R.string.xerr_no_account);
        XSTS_ERRORS.put(2148916235L, R.string.xerr_not_available);
        XSTS_ERRORS.put(2148916236L, R.string.xerr_adult_verification);
        XSTS_ERRORS.put(2148916237L, R.string.xerr_adult_verification);
        XSTS_ERRORS.put(2148916238L, R.string.xerr_child);
    }

    public String msRefreshToken;
    public String mcName;
    public String mcToken;
    public String mcUuid;
    public boolean doesOwnGame, hasProfile;
    public long expiresAt;

    public MicrosoftBackgroundLogin(boolean isRefresh, String authCode) {
        mIsRefresh = isRefresh;
        mAuthCode = authCode;
        mProvidedAccessToken = null;
    }

    private MicrosoftBackgroundLogin(String accessToken, String refreshToken) {
        mIsRefresh = false;
        mAuthCode = null;
        mProvidedAccessToken = accessToken;
        msRefreshToken = refreshToken;
    }

    /** Device flow uses the same legacy public client as the existing browser sign-in. */
    public static MicrosoftBackgroundLogin fromDeviceTokens(String accessToken, String refreshToken) {
        return new MicrosoftBackgroundLogin(accessToken, refreshToken);
    }

    public void cancel() {
        mCancelled = true;
        HttpURLConnection connection = mConnection;
        if (connection != null) connection.disconnect();
    }

    private void checkCancelled() {
        if (mCancelled || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    public void performLogin(@Nullable ProgressListener progressListener,
                             @Nullable DoneListener doneListener,
                             @Nullable ErrorListener errorListener) {
        // Register synchronously so home-screen shortcuts cannot race the queued refresh worker.
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE_MICROSOFT, 0);
        sExecutorService.execute(() -> {
            try {
                MinecraftAccount account = loginBlocking(progressListener);
                if (doneListener != null) Tools.runOnUiThread(() -> {
                    if (!mCancelled) doneListener.onLoginDone(account);
                });
            } catch (CancellationException ignored) {
                // A cancelled dialog must not turn into an error toast.
            } catch (Exception e) {
                // Do not log exception bodies: remote auth responses can contain credentials.
                Log.w("MicrosoftLogin", "Authentication did not complete (" + e.getClass().getSimpleName() + ")");
                if (errorListener != null) Tools.runOnUiThread(() -> {
                    if (!mCancelled) errorListener.onLoginError(e);
                });
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE_MICROSOFT);
            }
        });
    }

    /** Run only on a worker thread. Also used by the cancellable device-code controller. */
    public MinecraftAccount loginBlocking(@Nullable ProgressListener progressListener) throws IOException, JSONException {
        checkCancelled();
        notifyProgress(progressListener, 1);
        String accessToken = mProvidedAccessToken != null ? mProvidedAccessToken : acquireAccessToken(mIsRefresh, mAuthCode);
        notifyProgress(progressListener, 2);
        String xboxToken = acquireXBLToken(accessToken);
        notifyProgress(progressListener, 3);
        String[] xsts = acquireXsts(xboxToken);
        notifyProgress(progressListener, 4);
        String minecraftToken = acquireMinecraftToken(xsts[0], xsts[1]);
        notifyProgress(progressListener, 5);
        checkMcProfile(minecraftToken);
        if (!hasProfile) fetchOwnedItems(minecraftToken);
        if (!hasProfile && doesOwnGame) throw new PresentedException(R.string.minecraft_no_username_set);
        if (!doesOwnGame) {
            mcName = "Demo.Player";
            mcUuid = "00000000-0000-0000-0000-000000000000";
        }
        if (mcName == null || mcUuid == null) throw new IOException("Microsoft did not return a Minecraft profile.");
        checkCancelled();
        MinecraftAccount account = MinecraftAccount.load(mcName);
        if (account == null) account = new MinecraftAccount();
        account.xuid = xsts[0];
        account.clientToken = "0";
        account.accessToken = minecraftToken;
        account.username = mcName;
        account.profileId = mcUuid;
        account.isMicrosoft = true;
        account.msaRefreshToken = msRefreshToken;
        account.expiresAt = expiresAt;
        account.save();
        // Avatar download is cosmetic and must not delay the successful sign-in callback.
        MinecraftAccount saved = account;
        sExecutorService.execute(saved::updateSkinFace);
        return account;
    }

    public String acquireAccessToken(boolean isRefresh, String authcode) throws IOException, JSONException {
        if (authcode == null || authcode.isEmpty()) throw new IOException("The sign-in code is missing. Please sign in again.");
        String form = form("client_id", CLIENT_ID,
                isRefresh ? "refresh_token" : "code", authcode,
                "grant_type", isRefresh ? "refresh_token" : "authorization_code",
                "redirect_url", "https://login.live.com/oauth20_desktop.srf", "scope", SCOPE);
        Response result = request(TOKEN_URL, "application/x-www-form-urlencoded", form, null);
        if (!result.success()) throw responseError(result);
        msRefreshToken = result.json.optString("refresh_token", isRefresh ? authcode : "");
        if (msRefreshToken.isEmpty()) throw new IOException("Microsoft did not provide a refresh token. Please sign in again.");
        return result.json.getString("access_token");
    }

    private String acquireXBLToken(String accessToken) throws IOException, JSONException {
        JSONObject properties = new JSONObject().put("AuthMethod", "RPS")
                .put("SiteName", "user.auth.xboxlive.com").put("RpsTicket", accessToken);
        JSONObject data = new JSONObject().put("Properties", properties)
                .put("RelyingParty", "http://auth.xboxlive.com").put("TokenType", "JWT");
        Response result = request(XBL_URL, "application/json", data.toString(), null);
        if (!result.success()) throw responseError(result);
        return result.json.getString("Token");
    }

    private @NonNull String[] acquireXsts(String xboxToken) throws IOException, JSONException {
        JSONObject properties = new JSONObject().put("SandboxId", "RETAIL")
                .put("UserTokens", new JSONArray(Collections.singleton(xboxToken)));
        JSONObject data = new JSONObject().put("Properties", properties)
                .put("RelyingParty", "rp://api.minecraftservices.com/").put("TokenType", "JWT");
        Response result = request(XSTS_URL, "application/json", data.toString(), null);
        if (!result.success()) {
            long error = result.json.optLong("XErr", -1);
            Integer localized = XSTS_ERRORS.get(error);
            if (localized != null) throw new PresentedException(localized);
            if (error != -1) throw new PresentedException(R.string.xerr_unknown, error);
            throw responseError(result);
        }
        String hash = result.json.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");
        return new String[]{hash, result.json.getString("Token")};
    }

    private String acquireMinecraftToken(String hash, String xsts) throws IOException, JSONException {
        JSONObject data = new JSONObject().put("identityToken", "XBL3.0 x=" + hash + ";" + xsts);
        Response result = request(MC_LOGIN_URL, "application/json", data.toString(), null);
        if (!result.success()) throw responseError(result);
        long lifetime = Math.max(60, result.json.optLong("expires_in", 3600));
        expiresAt = System.currentTimeMillis() + (lifetime - 60) * 1000L;
        mcToken = result.json.getString("access_token");
        return mcToken;
    }

    private void checkMcProfile(String token) throws IOException, JSONException {
        Response result = requestWithRetry(MC_PROFILE_URL, token);
        if (result.status == 404) { hasProfile = false; return; }
        if (!result.success()) throw responseError(result);
        String name = result.json.getString("name");
        String id = result.json.getString("id").replace("-", "");
        if (!name.matches("[A-Za-z0-9_]{1,16}") || !id.matches("[A-Fa-f0-9]{32}")) {
            throw new IOException("Minecraft returned an invalid profile.");
        }
        mcName = name;
        mcUuid = id.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        hasProfile = true;
        doesOwnGame = true;
    }

    private void fetchOwnedItems(String token) throws IOException, JSONException {
        Response result = requestWithRetry(MC_STORE_URL, token);
        if (!result.success()) throw responseError(result);
        JSONArray items = result.json.optJSONArray("items");
        if (items != null) for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && isMinecraftEntitlement(item.optString("name"))) doesOwnGame = true;
        }
        // Newer entitlement responses include the item names inside the server's JWT.
        // This response was obtained directly from Minecraft over authenticated TLS.
        if (!doesOwnGame && result.json.has("signature")) {
            String[] jwt = result.json.optString("signature").split("\\.");
            if (jwt.length > 1) {
                try {
                    JSONObject body = new JSONObject(new String(Base64.decode(jwt[1], Base64.URL_SAFE), StandardCharsets.UTF_8));
                    JSONArray entitlements = body.optJSONArray("entitlements");
                    if (entitlements != null) for (int i = 0; i < entitlements.length(); i++) {
                        if (isMinecraftEntitlement(entitlements.optString(i))) doesOwnGame = true;
                    }
                } catch (IllegalArgumentException | JSONException ignored) {
                    // A malformed entitlement must never grant ownership.
                }
            }
        }
    }

    private boolean isMinecraftEntitlement(String item) {
        return "product_minecraft".equals(item) || "game_minecraft".equals(item);
    }

    private Response requestWithRetry(String url, String token) throws IOException, JSONException {
        Response response = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            response = request(url, null, null, token);
            if (response.status < 500 && response.status != 429) return response;
            if (attempt < 2) {
                try { Thread.sleep(500L << attempt); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancellationException(); }
            }
        }
        return response;
    }

    private Response request(String endpoint, String contentType, String body, String bearer) throws IOException, JSONException {
        checkCancelled();
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        mConnection = connection;
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            if (body != null) {
                byte[] data = body.getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", contentType + "; charset=utf-8");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream stream = connection.getOutputStream()) { stream.write(data); }
            }
            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String text = "";
            if (responseStream != null) try (InputStream stream = responseStream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    checkCancelled();
                    if (bytes.size() + count > 1048576) throw new IOException("The authentication response was too large.");
                    bytes.write(buffer, 0, count);
                }
                text = bytes.toString("UTF-8");
            }
            checkCancelled();
            JSONObject json;
            try { json = text.isEmpty() ? new JSONObject() : new JSONObject(text); }
            catch (JSONException e) { throw new IOException("The sign-in service returned an unexpected response (HTTP " + status + ")."); }
            return new Response(status, json);
        } finally {
            connection.disconnect();
            mConnection = null;
        }
    }

    private RuntimeException responseError(Response response) {
        if (response.status == 429) return new PresentedException(R.string.microsoft_login_retry_later);
        String error = response.json.optString("error", "");
        if ("invalid_grant".equals(error)) return new RuntimeException("Your Microsoft session has expired. Sign in again with a new code.");
        if ("access_denied".equals(error) || "authorization_declined".equals(error)) return new RuntimeException("Microsoft sign-in was declined.");
        if (response.status == 401) return new RuntimeException("Microsoft could not authorize this session. Please sign in again.");
        if (response.status == 403) return new RuntimeException("Minecraft or Xbox rejected access. Check that this account owns Minecraft: Java Edition and can use Xbox services.");
        return new RuntimeException("The sign-in service returned HTTP " + response.status + ". Please try again.");
    }

    private void notifyProgress(@Nullable ProgressListener listener, int step) {
        checkCancelled();
        if (listener != null) Tools.runOnUiThread(() -> { if (!mCancelled) listener.onLoginProgress(step); });
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE_MICROSOFT, step * 20);
    }

    static String form(String... data) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < data.length; i += 2) {
            if (result.length() > 0) result.append('&');
            result.append(URLEncoder.encode(data[i], "UTF-8")).append('=')
                    .append(URLEncoder.encode(data[i + 1], "UTF-8"));
        }
        return result.toString();
    }

    private static final class Response {
        final int status;
        final JSONObject json;
        Response(int status, JSONObject json) { this.status = status; this.json = json; }
        boolean success() { return status >= 200 && status < 300; }
    }
}
