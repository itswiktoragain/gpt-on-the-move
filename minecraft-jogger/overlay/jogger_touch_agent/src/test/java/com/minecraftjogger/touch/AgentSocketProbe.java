package com.minecraftjogger.touch;

/** Starts only from the integration test, after the JVM has loaded the packaged premain agent. */
public final class AgentSocketProbe {
    public static void main(String[] args) throws Exception {
        if (!"1.0.0".equals(System.getProperty("minecraftjogger.touch.agent"))) {
            throw new AssertionError("The packaged premain agent did not start");
        }
        Thread.sleep(5000);
    }
}
