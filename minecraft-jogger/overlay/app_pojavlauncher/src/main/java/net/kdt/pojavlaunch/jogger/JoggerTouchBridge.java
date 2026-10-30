package net.kdt.pojavlaunch.jogger;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** ART half of the bundled JVM touch agent. Look movement stays on the native low-latency path. */
public final class JoggerTouchBridge {
    private static final String TAG = "JoggerTouch";
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Integer, int[]> HELD_KEYS = new LinkedHashMap<>();
    private static final Map<Integer, Integer> HELD_MOUSE = new LinkedHashMap<>();
    private static final Map<Integer, int[]> DELIVERED_KEYS = new LinkedHashMap<>();
    private static final Map<Integer, Integer> DELIVERED_MOUSE = new LinkedHashMap<>();
    private static volatile Connection connection;
    private static volatile ServerSocket listener;
    private static volatile long epoch = 1;

    private JoggerTouchBridge() {}

    public static void addAgentArguments(Context context, List<String> args) throws IOException {
        if (!LauncherPreferences.DEFAULT_PREF.getBoolean("jogger_touch_agent", true)) return;
        File folder = new File(context.getFilesDir(), "jogger");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create Jogger component directory");
        File jar = new File(folder, "jogger_touch_agent.jar");
        File temp = new File(folder, "jogger_touch_agent.jar.part");
        try (InputStream in = context.getAssets().open("components/jogger_touch_agent/jogger_touch_agent.jar");
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.getFD().sync();
        }
        if (!temp.renameTo(jar)) throw new IOException("Cannot install Jogger touch component");

        stop();
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        StringBuilder encoded = new StringBuilder();
        for (byte b : secret) encoded.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        String token = encoded.toString();
        ServerSocket server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
        listener = server;
        Thread acceptor = new Thread(() -> accept(server, token), "Jogger touch endpoint");
        acceptor.setDaemon(true);
        acceptor.start();
        args.add("-javaagent:" + jar.getAbsolutePath() + "=" + server.getLocalPort() + ":" + token);
    }

    private static void accept(ServerSocket server, String token) {
        while (listener == server && !server.isClosed()) {
            Connection current = null;
            Socket accepted = null;
            try {
                Socket socket = accepted = server.accept();
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(2000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String hello = readBounded(reader);
                if (hello == null || !MessageDigest.isEqual(("JOGGER1 " + token).getBytes(StandardCharsets.UTF_8),
                        hello.getBytes(StandardCharsets.UTF_8))) {
                    socket.close();
                    continue;
                }
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                writer.write("READY\n");
                writer.flush();
                socket.setSoTimeout(0);
                current = new Connection(socket, writer);
                connection = current;
                final Connection connected = current;
                Thread sender = new Thread(() -> sendLoop(connected), "Jogger touch writer");
                sender.setDaemon(true);
                sender.start();
                Log.i(TAG, "JVM touch agent connected");
                String line;
                while ((line = readBounded(reader)) != null) dispatch(connected, line);
            } catch (IOException ignored) {
                // Controls remain usable via the direct native fallback.
            } finally {
                if (current != null) disconnect(current);
                else if (accepted != null) try { accepted.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void sendLoop(Connection current) {
        try {
            while (connection == current) {
                String message = current.queue.poll(350, TimeUnit.MILLISECONDS);
                if (message == null) message = "P " + epoch;
                current.writer.write(message);
                current.writer.newLine();
                current.writer.flush();
            }
        } catch (IOException | InterruptedException stopped) {
            if (stopped instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            disconnect(current);
        }
    }

    public static void sendKey(int key, char character, int scancode, int mods, boolean down) {
        if (key < 32 || key > 348) {
            CallbackBridge.sendKeycode(key, character, scancode, mods, down);
            return;
        }
        synchronized (LOCK) {
            if (down) HELD_KEYS.put(key, new int[]{character, scancode});
            else HELD_KEYS.remove(key);
            Connection current = connection;
            if (current != null) {
                if (current.queue.offer("K " + epoch + " " + key + " " + (int)character + " " + scancode + " " + mods + " " + (down ? 1 : 0))) return;
                disconnect(current);
                return;
            }
        }
        synchronized (LOCK) { deliverKey(key, character, scancode, mods, down); }
    }

    public static void sendMouse(int button, boolean down) {
        int mods = CallbackBridge.getCurrentMods();
        synchronized (LOCK) {
            if (down) HELD_MOUSE.put(button, mods);
            else HELD_MOUSE.remove(button);
            Connection current = connection;
            if (current != null) {
                if (current.queue.offer("M " + epoch + " " + button + " " + mods + " " + (down ? 1 : 0))) return;
                disconnect(current);
                return;
            }
        }
        synchronized (LOCK) { deliverMouse(button, mods, down); }
    }

    private static void dispatch(Connection current, String line) {
        try {
            String[] f = line.split(" ");
            if (f.length < 2) return;
            long incomingEpoch = Long.parseLong(f[1]);
            if ("K".equals(f[0]) && f.length == 7) {
                int key = Integer.parseInt(f[2]), character = Integer.parseInt(f[3]);
                int scan = Integer.parseInt(f[4]), mods = Integer.parseInt(f[5]), state = Integer.parseInt(f[6]);
                if (key < 32 || key > 348 || character < 0 || character > 65535 || state < 0 || state > 1) return;
                MAIN.post(() -> {
                    synchronized (LOCK) {
                        if (connection != current || epoch != incomingEpoch) return;
                        deliverKey(key, (char)character, scan, mods, state == 1);
                    }
                });
            } else if ("M".equals(f[0]) && f.length == 5) {
                int button = Integer.parseInt(f[2]), mods = Integer.parseInt(f[3]), state = Integer.parseInt(f[4]);
                if (button < 0 || button > 7 || state < 0 || state > 1) return;
                MAIN.post(() -> {
                    synchronized (LOCK) {
                        if (connection == current && epoch == incomingEpoch) deliverMouse(button, mods, state == 1);
                    }
                });
            }
        } catch (NumberFormatException ignored) {}
    }

    private static void deliverKey(int key, char character, int scan, int mods, boolean down) {
        if (down) DELIVERED_KEYS.put(key, new int[]{character, scan});
        else DELIVERED_KEYS.remove(key);
        CallbackBridge.sendKeycode(key, character, scan, mods, down);
        CallbackBridge.setModifiers(key, down);
    }

    private static void deliverMouse(int button, int mods, boolean down) {
        if (down) DELIVERED_MOUSE.put(button, mods);
        else DELIVERED_MOUSE.remove(button);
        CallbackBridge.sendMouseKeycode(button, mods, down);
    }

    /** Invalidates queued events as well as releasing the native state immediately. */
    public static void releaseAll() {
        final Map<Integer, int[]> keys;
        final Map<Integer, Integer> mouse;
        synchronized (LOCK) {
            epoch++;
            keys = new LinkedHashMap<>(HELD_KEYS);
            mouse = new LinkedHashMap<>(HELD_MOUSE);
            keys.putAll(DELIVERED_KEYS);
            mouse.putAll(DELIVERED_MOUSE);
            HELD_KEYS.clear();
            HELD_MOUSE.clear();
            DELIVERED_KEYS.clear();
            DELIVERED_MOUSE.clear();
            Connection current = connection;
            if (current != null) {
                current.queue.clear();
                current.queue.offer("R " + epoch);
            }
        }
        Runnable release = () -> {
            synchronized (LOCK) {
                for (Map.Entry<Integer, int[]> key : keys.entrySet()) {
                    if (DELIVERED_KEYS.containsKey(key.getKey())) continue;
                    CallbackBridge.sendKeycode(key.getKey(), (char)key.getValue()[0], key.getValue()[1], 0, false);
                    CallbackBridge.setModifiers(key.getKey(), false);
                }
                for (Integer button : mouse.keySet()) {
                    if (!DELIVERED_MOUSE.containsKey(button)) CallbackBridge.sendMouseKeycode(button, 0, false);
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) release.run();
        else MAIN.post(release);
    }

    public static void stop() {
        ServerSocket server = listener;
        listener = null;
        if (server != null) try { server.close(); } catch (IOException ignored) {}
        Connection current = connection;
        if (current != null) disconnect(current);
        else releaseAll();
    }

    private static void disconnect(Connection current) {
        synchronized (LOCK) {
            if (connection == current) {
                connection = null;
                releaseAll();
            }
        }
        try { current.socket.close(); } catch (IOException ignored) {}
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

    private static final class Connection {
        final Socket socket;
        final BufferedWriter writer;
        final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(512);
        Connection(Socket socket, BufferedWriter writer) { this.socket = socket; this.writer = writer; }
    }
}
