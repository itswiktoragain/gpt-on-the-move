package net.kdt.pojavlaunch.authenticator.microsoft;

import java.net.URI;
import java.net.URISyntaxException;

/** Protocol decisions separated from Android so expiry, errors and link restrictions are testable. */
public final class DeviceCodePolicy {
    private DeviceCodePolicy() {}

    public enum Action { ACCEPT, WAIT, SLOW_DOWN, EXPIRED, DECLINED, RETRY, FAIL }

    public static Action classify(int status, String error) {
        if ("authorization_pending".equals(error)) return Action.WAIT;
        if ("slow_down".equals(error) || status == 429) return Action.SLOW_DOWN;
        if ("expired_token".equals(error) || "code_expired".equals(error)) return Action.EXPIRED;
        if ("access_denied".equals(error) || "authorization_declined".equals(error)) return Action.DECLINED;
        if (status >= 500 && status < 600) return Action.RETRY;
        if (status >= 200 && status < 300 && (error == null || error.isEmpty())) return Action.ACCEPT;
        return Action.FAIL;
    }

    public static long initialIntervalMillis(long seconds) {
        return Math.max(5, Math.min(1800, seconds)) * 1000L;
    }

    public static long slowerIntervalMillis(long millis) {
        return Math.min(1800000L, Math.min(1800000L, Math.max(5000L, millis)) + 5000L);
    }

    public static boolean isMicrosoftVerificationUri(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null &&
                    (uri.getPort() == -1 || uri.getPort() == 443) && host != null &&
                    (host.equalsIgnoreCase("microsoft.com") || host.equalsIgnoreCase("www.microsoft.com") ||
                            host.equalsIgnoreCase("login.live.com") || host.equalsIgnoreCase("login.microsoftonline.com"));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
