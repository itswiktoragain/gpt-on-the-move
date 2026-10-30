package net.kdt.pojavlaunch.jogger.classic;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.jogger.JoggerInstances;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Conservative, reversible options. No FPS claims or unsafe JVM flags. */
public final class MobileTuning {
    private MobileTuning() {}
    public static boolean enabled() { return LauncherPreferences.DEFAULT_PREF.getBoolean("jogger_classic", true); }
    public static void defaults(Context c) {
        SharedPreferences p = LauncherPreferences.DEFAULT_PREF;
        if (p.getBoolean("jogger_mobile_defaults_v1", false)) return;
        SharedPreferences.Editor e = p.edit();
        if (!p.contains("resolutionRatio")) { e.putInt("resolutionRatio", 75); LauncherPreferences.PREF_SCALE_FACTOR = .75f; }
        if (!p.contains("allocation")) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            ((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
            int mb = (int)Math.max(768, Math.min(2048, info.totalMem / 1048576 / 4));
            e.putInt("allocation", mb); LauncherPreferences.PREF_RAM_ALLOCATION = mb;
        }
        if (!p.contains("sustainedPerformance")) { e.putBoolean("sustainedPerformance", true); LauncherPreferences.PREF_SUSTAINED_PERFORMANCE = true; }
        e.putBoolean("jogger_mobile_defaults_v1", true).apply();
    }
    private static LinkedHashMap<String,String> read(File f) throws IOException {
        LinkedHashMap<String,String> map = new LinkedHashMap<>();
        if (!f.isFile()) return map;
        for (String s : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            int pos = s.indexOf(':'); if (pos > 0) map.put(s.substring(0, pos), s.substring(pos + 1));
        }
        return map;
    }
    private static void atomic(File f, String text) throws IOException {
        File tmp = new File(f.getPath() + ".jogger-part");
        try (FileOutputStream stream = new FileOutputStream(tmp)) {
            stream.write(text.getBytes(StandardCharsets.UTF_8)); stream.getFD().sync();
        }
        try { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }
    private static Map<String,String> preset(int mode) {
        Map<String,String> m = new LinkedHashMap<>();
        m.put("renderDistance", mode == 0 ? "4" : mode == 1 ? "6" : "10");
        m.put("simulationDistance", mode == 2 ? "6" : "5");
        m.put("maxFps", mode == 0 ? "30" : "60");
        m.put("graphicsMode", mode == 2 ? "1" : "0");
        m.put("fancyGraphics", mode == 2 ? "true" : "false");
        m.put("renderClouds", mode == 2 ? "fast" : "false");
        m.put("particles", mode == 0 ? "2" : "1");
        m.put("entityDistanceScaling", mode == 0 ? "0.5" : mode == 1 ? "0.75" : "1.0");
        m.put("entityShadows", mode == 2 ? "true" : "false");
        m.put("biomeBlendRadius", mode == 2 ? "2" : "0");
        m.put("mipmapLevels", mode == 0 ? "1" : "2");
        return m;
    }
    public static synchronized void apply(File dir, int mode) throws Exception {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create instance directory");
        File options = new File(dir, "options.txt"), backup = new File(dir, ".jogger-mobile-original.json");
        Map<String,String> values = read(options), changed = preset(Math.min(mode, 2));
        JSONObject old = backup.isFile() ? new JSONObject(new String(Files.readAllBytes(backup.toPath()), StandardCharsets.UTF_8)) : new JSONObject();
        if (mode == 3) {
            if (!backup.isFile()) return;
            Iterator<String> keys = old.keys();
            while (keys.hasNext()) { String key = keys.next(); if (old.isNull(key)) values.remove(key); else values.put(key, old.getString(key)); }
        } else {
            for (String key : changed.keySet()) if (!old.has(key)) old.put(key, values.containsKey(key) ? values.get(key) : JSONObject.NULL);
            atomic(backup, old.toString(2)); values.putAll(changed);
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String,String> entry : values.entrySet()) text.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        atomic(options, text.toString());
        atomic(new File(dir, ".jogger-mobile-preset"), String.valueOf(mode));
    }
    public static void firstRun(Context c, String path) {
        defaults(c); File dir = new File(path);
        if (new File(dir, ".jogger-mobile-preset").exists()) return;
        try { apply(dir, 1); } catch (Exception ex) { android.util.Log.w("Jogger", "Mobile preset was not applied", ex); }
    }
    public static void show(Context c) {
        LinearLayout form = new LinearLayout(c); form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * c.getResources().getDisplayMetrics().density); form.setPadding(pad, pad / 2, pad, pad / 2);
        CheckBox classic = new CheckBox(c); classic.setText("Classic directional-pad controls"); classic.setChecked(enabled()); form.addView(classic);
        CheckBox target = new CheckBox(c); target.setText("Target the touched block (supported game versions)"); target.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean("jogger_touch_target", true)); form.addView(target);
        TextView help = new TextView(c); help.setText("Centre button: crouch. Double-tap forward: sprint. Tap: use/place. Hold: mine. Drag: look. The ... beside the hotbar opens inventory.\n\nPresets affect this instance only, and preserve unrelated options. Graphics resolution and RAM are adjustable in Settings. Changes apply on the next game launch."); help.setPadding(0,pad/2,0,pad/2); form.addView(help);
        Spinner preset = new Spinner(c); preset.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, new String[]{"Keep current performance settings", "Battery: 4 chunks / 30 FPS cap", "Balanced: 6 chunks / 60 FPS cap", "Quality: 10 chunks / 60 FPS cap", "Restore original performance options"})); form.addView(preset);
        new AlertDialog.Builder(c).setTitle("Phone controls & performance").setView(form).setPositiveButton("Save", (d,w) -> {
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean("jogger_classic", classic.isChecked()).putBoolean("jogger_touch_target", target.isChecked()).apply();
            int n = preset.getSelectedItemPosition();
            if (n > 0) try { MinecraftProfile p = JoggerInstances.current(); if (p != null) apply(Tools.getGameDirPath(p), n - 1); }
            catch (Exception ex) { Toast.makeText(c, "Could not apply preset: " + ex.getMessage(), Toast.LENGTH_LONG).show(); return; }
            Toast.makeText(c, "Saved. Applies next launch.", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("Cancel", null).show();
    }
}
