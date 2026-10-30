package com.minecraftjogger.touch;

/** Keeps the fixture JVM alive while the real packaged premain/socket path is tested. */
public final class AgentSocketProbe {
    public static void main(String[] args) throws Exception {
        String version = System.getProperty("minecraftjogger.touch.agent");
        if (!"1.1.0".equals(version)) {
            throw new AssertionError("Expected Classic touch agent 1.1.0, found: " + version);
        }
        Thread.sleep(5000);
    }
}
