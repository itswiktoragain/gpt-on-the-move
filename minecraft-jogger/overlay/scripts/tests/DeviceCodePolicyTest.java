package net.kdt.pojavlaunch.authenticator.microsoft;

/** Run with the JDK only; no account, network request, Android emulator or extra test dependency. */
public final class DeviceCodePolicyTest {
    private static int assertions;
    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but received " + actual);
    }
    public static void main(String[] args) {
        equal(DeviceCodePolicy.Action.WAIT, DeviceCodePolicy.classify(400, "authorization_pending"));
        equal(DeviceCodePolicy.Action.SLOW_DOWN, DeviceCodePolicy.classify(400, "slow_down"));
        equal(DeviceCodePolicy.Action.SLOW_DOWN, DeviceCodePolicy.classify(429, ""));
        equal(DeviceCodePolicy.Action.EXPIRED, DeviceCodePolicy.classify(400, "expired_token"));
        equal(DeviceCodePolicy.Action.EXPIRED, DeviceCodePolicy.classify(400, "code_expired"));
        equal(DeviceCodePolicy.Action.DECLINED, DeviceCodePolicy.classify(400, "authorization_declined"));
        equal(DeviceCodePolicy.Action.DECLINED, DeviceCodePolicy.classify(400, "access_denied"));
        equal(DeviceCodePolicy.Action.RETRY, DeviceCodePolicy.classify(503, ""));
        equal(DeviceCodePolicy.Action.FAIL, DeviceCodePolicy.classify(400, "invalid_grant"));
        equal(DeviceCodePolicy.Action.FAIL, DeviceCodePolicy.classify(403, ""));
        equal(DeviceCodePolicy.Action.FAIL, DeviceCodePolicy.classify(302, ""));
        equal(DeviceCodePolicy.Action.FAIL, DeviceCodePolicy.classify(200, "unexpected_error"));
        equal(DeviceCodePolicy.Action.ACCEPT, DeviceCodePolicy.classify(200, ""));
        equal(DeviceCodePolicy.Action.ACCEPT, DeviceCodePolicy.classify(200, null));
        equal(5000L, DeviceCodePolicy.initialIntervalMillis(0));
        equal(5000L, DeviceCodePolicy.initialIntervalMillis(-100));
        equal(15000L, DeviceCodePolicy.initialIntervalMillis(15));
        equal(1800000L, DeviceCodePolicy.initialIntervalMillis(Long.MAX_VALUE));
        equal(10000L, DeviceCodePolicy.slowerIntervalMillis(5000));
        equal(65000L, DeviceCodePolicy.slowerIntervalMillis(60000));
        equal(true, DeviceCodePolicy.isMicrosoftVerificationUri("https://www.microsoft.com/link"));
        equal(true, DeviceCodePolicy.isMicrosoftVerificationUri("https://microsoft.com/link"));
        equal(true, DeviceCodePolicy.isMicrosoftVerificationUri("https://login.microsoftonline.com/common/oauth2/deviceauth"));
        equal(true, DeviceCodePolicy.isMicrosoftVerificationUri("https://login.live.com/oauth20_remoteconnect.srf"));
        equal(false, DeviceCodePolicy.isMicrosoftVerificationUri("http://www.microsoft.com/link"));
        equal(false, DeviceCodePolicy.isMicrosoftVerificationUri("https://www.microsoft.com.evil.invalid/link"));
        equal(false, DeviceCodePolicy.isMicrosoftVerificationUri("https://www.microsoft.com@evil.invalid/link"));
        equal(false, DeviceCodePolicy.isMicrosoftVerificationUri("https://evil.invalid@www.microsoft.com/link"));
        equal(false, DeviceCodePolicy.isMicrosoftVerificationUri("https://www.microsoft.com:8443/link"));
        equal(false, DeviceCodePolicy.isMicrosoftVerificationUri("javascript:alert(1)"));
        equal(false, DeviceCodePolicy.isMicrosoftVerificationUri("not a uri"));
        equal(false, DeviceCodePolicy.isMicrosoftVerificationUri(null));
        System.out.println("PASS: " + assertions + " Microsoft device-code protocol and URI assertions");
    }
}
