package net.kdt.pojavlaunch.jogger;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.widget.Toast;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.TestStorageActivity;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import org.json.JSONObject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Instances use the backend's real profiles and distinct Minecraft game directories. */
public final class JoggerInstances {
    public static final String EXTRA_INSTANCE = "jogger.instance";
    public static final String EXTRA_QUICK_PLAY = "jogger.quick_play";
    private JoggerInstances() {}

    public static synchronized void ensure() {
        LauncherProfiles.load();
        boolean first = !LauncherPreferences.DEFAULT_PREF.getBoolean("jogger.initialized", false);
        boolean changed = false;
        for (Map.Entry<String, MinecraftProfile> e : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile p = e.getValue();
            if (p == null) continue;
            if (first && ("(Default)".equals(p.name) || "Default".equals(p.name))) {
                p.name = "My first world";
                p.lastVersionId = "1.21.4";
                changed = true;
            }
            if (p.gameDir == null || p.gameDir.trim().isEmpty()) {
                p.gameDir = "instances/" + e.getKey();
                changed = true;
            }
            File dir = Tools.getGameDirPath(p);
            dir.mkdirs();
            new File(dir, "mods").mkdirs();
            new File(dir, "resourcepacks").mkdirs();
        }
        if (changed) LauncherProfiles.write();
        if (first) LauncherPreferences.DEFAULT_PREF.edit().putBoolean("jogger.initialized", true).apply();
        String key = currentKey();
        if (!LauncherProfiles.mainProfileJson.profiles.containsKey(key) && !LauncherProfiles.mainProfileJson.profiles.isEmpty())
            select(orderedKeys().get(0));
    }

    public static String currentKey() {
        return LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
    }
    public static MinecraftProfile current() {
        if (LauncherProfiles.mainProfileJson == null) ensure();
        return LauncherProfiles.mainProfileJson.profiles.get(currentKey());
    }
    public static void select(String key) {
        if (LauncherProfiles.mainProfileJson != null && LauncherProfiles.mainProfileJson.profiles.containsKey(key))
            LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, key).apply();
    }
    public static List<String> orderedKeys() {
        ArrayList<String> keys = new ArrayList<>(LauncherProfiles.mainProfileJson.profiles.keySet());
        keys.sort((a,b) -> {
            MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(a), q = LauncherProfiles.mainProfileJson.profiles.get(b);
            return displayName(p).compareToIgnoreCase(displayName(q));
        });
        return keys;
    }
    public static String displayName(MinecraftProfile p) {
        return p == null || p.name == null || p.name.trim().isEmpty() ? "Minecraft instance" : p.name;
    }
    public static String create(String name, String version) {
        String key = UUID.randomUUID().toString();
        MinecraftProfile p = MinecraftProfile.createTemplate();
        p.name = name.trim();
        p.lastVersionId = version;
        p.gameDir = "instances/" + key;
        p.controlFile = "jogger-touch.json";
        p.created = now();
        LauncherProfiles.mainProfileJson.profiles.put(key, p);
        LauncherProfiles.write();
        select(key);
        ensure();
        return key;
    }
    public static void used() {
        MinecraftProfile p = current();
        if (p != null) { p.lastUsed = now(); LauncherProfiles.write(); }
    }
    private static String now() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
    public static String loader(MinecraftProfile p) {
        String id = p == null || p.lastVersionId == null ? "" : p.lastVersionId.toLowerCase(Locale.ROOT);
        if (id.contains("neoforge")) return "neoforge";
        if (id.contains("fabric")) return "fabric";
        if (id.contains("quilt")) return "quilt";
        if (id.contains("forge")) return "forge";
        return "vanilla";
    }
    public static String gameVersion(MinecraftProfile p) {
        String id = p == null ? "1.21.4" : p.lastVersionId;
        if (id == null) return "1.21.4";
        String resolved=net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader.normalizeVersionId(id);
        if(resolved!=null)id=resolved;
        try {
            for (int depth=0; depth<8; depth++) {
                if (id.contains("/") || id.contains("\\") || id.contains("..")) break;
                File json = new File(Tools.DIR_HOME_VERSION, id + "/" + id + ".json");
                if (!json.isFile()) break;
                JSONObject version = new JSONObject(Tools.read(json.getAbsolutePath()));
                if (!version.has("inheritsFrom")) return id;
                id = version.getString("inheritsFrom");
            }
        } catch (Exception ignored) { }
        if ("vanilla".equals(loader(p))) return id;
        Matcher m = Pattern.compile("(?<![\\d.])(?:1\\.\\d+(?:\\.\\d+)?|2[6-9]\\.\\d+(?:[-.][a-z]+[-.]?\\d+)?)").matcher(id);
        String found = id;
        while (m.find()) found = m.group();
        return found;
    }
    public static int count(MinecraftProfile p, String folder) {
        if (p == null) return 0;
        File[] files = new File(Tools.getGameDirPath(p), folder).listFiles();
        if (files == null) return 0;
        int n=0;
        for(File f:files) if (!f.getName().startsWith(".") && !f.getName().endsWith(".disabled") && !f.getName().endsWith(".part")) n++;
        return n;
    }
    public static void pin(Context context, String key) {
        MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.get(key);
        if (p == null) return;
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            Toast.makeText(context, "Your home screen does not support pinned shortcuts.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(context, TestStorageActivity.class).setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_INSTANCE, key).putExtra(EXTRA_QUICK_PLAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        String name=displayName(p);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, "instance-"+key)
            .setShortLabel(name.length()>30 ? name.substring(0,30) : name)
            .setLongLabel("Play " + name)
            .setIcon(Icon.createWithResource(context, R.drawable.jogger_icon))
            .setIntent(intent).build();
        try {
            manager.updateShortcuts(Collections.singletonList(shortcut));
            boolean requested=manager.requestPinShortcut(shortcut, null);
            Toast.makeText(context, requested ? "Confirm the shortcut on your home screen." : "Your home screen could not add the shortcut.", Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(context, "Could not add shortcut: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    public static void remove(Context context, String key) {
        LauncherProfiles.mainProfileJson.profiles.remove(key);
        LauncherProfiles.write();
        ShortcutManager manager=context.getSystemService(ShortcutManager.class);
        if(manager!=null) manager.disableShortcuts(Collections.singletonList("instance-"+key), "This Jogger instance was removed.");
        ensure();
    }
}
