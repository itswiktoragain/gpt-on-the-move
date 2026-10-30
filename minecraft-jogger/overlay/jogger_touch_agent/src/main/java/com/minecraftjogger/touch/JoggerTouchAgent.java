package com.minecraftjogger.touch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.instrument.Instrumentation;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Touch input agent running inside the game's JVM. The launcher supplies a private loopback
 * connection into its native GLFW/SDL input queue, keeping this independent of Minecraft mappings.
 */
public final class JoggerTouchAgent {
    private JoggerTouchAgent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (arguments == null) return;
        final String[] parts = arguments.split(":", 2);
        if (parts.length != 2 || !parts[1].matches("[0-9a-f]{64}")) return;
        final int port;
        try {
            port = Integer.parseInt(parts[0]);
            if (port < 1024 || port > 65535) return;
        } catch (NumberFormatException invalid) { return; }
        System.setProperty("minecraftjogger.touch.agent", "1.0.0");
        Thread worker = new Thread(new Runnable() {
            @Override public void run() { connect(port, parts[1]); }
        }, "Jogger touch input agent");
        worker.setDaemon(true);
        worker.start();
    }

    private static void connect(int port, String token) {
        // A launcher's destroyed activity closes the endpoint; avoid spinning during shutdown.
        for (int retry = 0; retry < 8; retry++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1500);
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(1800);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                writer.write("JOGGER1 " + token + "\n");
                writer.flush();
                if (!"READY".equals(readBounded(reader))) return;
                System.out.println("[Minecraft Jogger] Touch input agent connected.");
                InputState state = new InputState();
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        String line = readBounded(reader);
                        if (line == null) return;
                        write(writer, state.accept(line));
                    } catch (SocketTimeoutException lostHeartbeat) {
                        write(writer, state.release());
                    }
                }
                return;
            } catch (IOException unavailable) {
                // The Android half falls back to native controls if this connection is unavailable.
                try { Thread.sleep(700); }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); return; }
            }
        }
        System.out.println("[Minecraft Jogger] Native touch input fallback active.");
    }

    private static void write(BufferedWriter writer, List<String> lines) throws IOException {
        for (String line : lines) {
            writer.write(line);
            writer.newLine();
        }
        if (!lines.isEmpty()) writer.flush();
    }

    private static String readBounded(BufferedReader reader) throws IOException {
        StringBuilder value = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') return value.toString();
            if (c == '\r') continue;
            if (value.length() >= 160) throw new IOException("Touch message too long");
            value.append((char)c);
        }
        return value.length() == 0 ? null : value.toString();
    }
}
