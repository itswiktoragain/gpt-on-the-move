package com.minecraftjogger.touch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Version-independent touch state machine. No Minecraft classes or mappings are loaded. */
public final class InputState {
    private long epoch;
    private final Map<Integer, int[]> keys = new LinkedHashMap<Integer, int[]>();
    private final Map<Integer, Integer> buttons = new LinkedHashMap<Integer, Integer>();

    public List<String> accept(String line) {
        List<String> result = new ArrayList<String>();
        if (line == null || line.length() > 160) return result;
        String[] fields = line.split(" ");
        try {
            if (fields.length < 2) return result;
            long incomingEpoch = Long.parseLong(fields[1]);
            if (incomingEpoch < 0 || incomingEpoch < epoch) return result;
            if ("K".equals(fields[0]) && fields.length == 7) {
                int key = Integer.parseInt(fields[2]);
                int character = Integer.parseInt(fields[3]);
                int scan = Integer.parseInt(fields[4]);
                int mods = Integer.parseInt(fields[5]);
                int down = Integer.parseInt(fields[6]);
                if (key < 32 || key > 348 || character < 0 || character > 65535 ||
                        scan < -1 || scan > 65535 || mods < 0 || mods > 63 || down < 0 || down > 1) return result;
                advance(incomingEpoch, result);
                if (down == 1) {
                    if (!keys.containsKey(key)) {
                        keys.put(key, new int[]{character, scan});
                        result.add(keyEvent(key, character, scan, mods, 1));
                    }
                } else {
                    // Always forward releases: a press may have used startup's native fallback.
                    keys.remove(key);
                    result.add(keyEvent(key, character, scan, mods, 0));
                }
            } else if ("M".equals(fields[0]) && fields.length == 5) {
                int button = Integer.parseInt(fields[2]);
                int mods = Integer.parseInt(fields[3]);
                int down = Integer.parseInt(fields[4]);
                if (button < 0 || button > 7 || mods < 0 || mods > 63 || down < 0 || down > 1) return result;
                advance(incomingEpoch, result);
                if (down == 1) {
                    if (!buttons.containsKey(button)) {
                        buttons.put(button, mods);
                        result.add(mouseEvent(button, mods, 1));
                    }
                } else {
                    buttons.remove(button);
                    result.add(mouseEvent(button, mods, 0));
                }
            } else if (("R".equals(fields[0]) || "P".equals(fields[0])) && fields.length == 2) {
                advance(incomingEpoch, result);
                if ("R".equals(fields[0])) result.addAll(release());
            }
        } catch (NumberFormatException ignored) {
            // Malformed input is ignored; the heartbeat watchdog still releases held inputs.
        }
        return result;
    }

    private void advance(long next, List<String> out) {
        if (next > epoch) {
            epoch = next;
            out.addAll(release());
        }
    }

    public List<String> release() {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<Integer, int[]> key : keys.entrySet()) {
            result.add(keyEvent(key.getKey(), key.getValue()[0], key.getValue()[1], 0, 0));
        }
        for (Integer button : buttons.keySet()) result.add(mouseEvent(button, 0, 0));
        keys.clear();
        buttons.clear();
        return result;
    }

    private String keyEvent(int key, int character, int scan, int mods, int down) {
        return "K " + epoch + " " + key + " " + character + " " + scan + " " + mods + " " + down;
    }

    private String mouseEvent(int button, int mods, int down) {
        return "M " + epoch + " " + button + " " + mods + " " + down;
    }
}
