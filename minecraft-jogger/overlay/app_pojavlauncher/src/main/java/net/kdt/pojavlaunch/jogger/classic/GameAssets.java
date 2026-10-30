package net.kdt.pojavlaunch.jogger.classic;

import android.content.Context;
import android.graphics.*;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Reads the player's installed game/pack textures; no Minecraft artwork is redistributed. */
public final class GameAssets {
    private GameAssets() {}
    public static File versionJson() {
        MinecraftProfile p = LauncherProfiles.getCurrentProfile();
        String id = p == null ? "1.21.4" : p.lastVersionId;
        Set<String> visited = new HashSet<>(); File last = null;
        while (id != null && id.matches("[A-Za-z0-9._+\\-]+") && visited.add(id)) {
            File f = new File(Tools.DIR_GAME_NEW, "versions/" + id + "/" + id + ".json");
            if (!f.isFile()) break; last = f;
            try { JSONObject json = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)); id = json.optString("inheritsFrom", null); }
            catch (Exception ignored) { break; }
        }
        return last;
    }
    public static Bitmap[] textures(File dir) {
        List<File> sources = new ArrayList<>();
        try {
            String packs = MCOptionUtils.get("resourcePacks");
            JSONArray selected = new JSONArray(packs == null ? "[]" : packs);
            File root = new File(dir, "resourcepacks").getCanonicalFile();
            for (int i = selected.length() - 1; i >= 0; i--) {
                String s = selected.getString(i); if (!s.startsWith("file/")) continue;
                File pack = new File(root, s.substring(5)).getCanonicalFile();
                if (pack.getPath().startsWith(root.getPath() + File.separator) && pack.isFile()) sources.add(pack);
            }
        } catch (Exception ignored) {}
        File json = versionJson();
        if (json != null) sources.add(new File(json.getParentFile(), json.getName().replace(".json", ".jar")));
        Bitmap[] result = new Bitmap[2];
        for (File source : sources) {
            if (!source.isFile()) continue;
            try (ZipFile zip = new ZipFile(source)) {
                for (int i = 0; i < 2; i++) if (result[i] == null) result[i] = bitmap(zip, "assets/minecraft/textures/gui/sprites/widget/button" + (i == 1 ? "_highlighted" : "") + ".png");
                if (result[0] == null) {
                    Bitmap atlas = bitmap(zip, "assets/minecraft/textures/gui/widgets.png");
                    if (atlas != null && atlas.getWidth() >= 200 && atlas.getHeight() >= 106) {
                        float scale = atlas.getWidth() / 256f;
                        result[0] = Bitmap.createBitmap(atlas, 0, Math.round(66 * scale), Math.round(200 * scale), Math.round(20 * scale));
                        if (result[1] == null) result[1] = Bitmap.createBitmap(atlas, 0, Math.round(86 * scale), Math.round(200 * scale), Math.round(20 * scale));
                    }
                }
            } catch (Exception ignored) {}
            if (result[0] != null && result[1] != null) break;
        }
        return result;
    }
    private static Bitmap bitmap(ZipFile zip, String name) throws IOException {
        ZipEntry e = zip.getEntry(name); if (e == null || e.getSize() < 0 || e.getSize() > 4194304) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        try (InputStream in = zip.getInputStream(e)) { BitmapFactory.decodeStream(in, null, bounds); }
        if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 2048 || bounds.outHeight > 2048) return null;
        BitmapFactory.Options o = new BitmapFactory.Options(); o.inScaled = false;
        try (InputStream in = zip.getInputStream(e)) { return BitmapFactory.decodeStream(in, null, o); }
    }
    public static File mappings(Context context) {
        File jsonFile = versionJson(); if (jsonFile == null) return null;
        File output = new File(context.getFilesDir(), "jogger/" + jsonFile.getName() + ".touch.properties");
        if (output.isFile()) return output;
        try {
            JSONObject json = new JSONObject(new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8));
            JSONObject downloads = json.optJSONObject("downloads"); if (downloads == null || !downloads.has("client_mappings")) return null;
            URL url = new URL(downloads.getJSONObject("client_mappings").getString("url"));
            if (!"https".equals(url.getProtocol()) || !(url.getHost().equals("piston-data.mojang.com") || url.getHost().equals("launcher.mojang.com"))) return null;
            HttpURLConnection connection = (HttpURLConnection)url.openConnection(); connection.setConnectTimeout(6000); connection.setReadTimeout(12000); connection.setInstanceFollowRedirects(false);
            Properties props = new Properties(); String section = "";
            Map<String,String> classes = new HashMap<>();
            classes.put("net.minecraft.client.Minecraft", "mc"); classes.put("net.minecraft.client.renderer.GameRenderer", "renderer");
            classes.put("net.minecraft.world.entity.Entity", "entity"); classes.put("net.minecraft.world.phys.Vec3", "vec");
            classes.put("net.minecraft.client.gui.screens.ChatScreen", "chat"); classes.put("net.minecraft.client.gui.screens.PauseScreen", "pause");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line; long chars = 0;
                while ((line = reader.readLine()) != null) {
                    if ((chars += line.length()) > 48000000) throw new IOException("Mapping file too large");
                    int arrow = line.lastIndexOf(" -> "); if (arrow < 0) continue;
                    String source = line.substring(0, arrow).trim(), destination = line.substring(arrow + 4).trim();
                    if (!Character.isWhitespace(line.charAt(0))) {
                        section = classes.get(source);
                        if (section == null) section = "";
                        else props.setProperty("class." + section, destination.replace(":", ""));
                    } else if (section.equals("mc")) {
                        if (source.endsWith(" screen")) props.setProperty("field.screen", destination);
                        if (source.endsWith(" level")) props.setProperty("field.level", destination);
                    } else if (section.equals("vec")) {
                        for (String field : new String[]{"x", "y", "z"}) if (source.endsWith(" " + field)) props.setProperty("field." + field, destination);
                    } else if (section.equals("entity") && source.contains(" getViewVector(float)")) props.setProperty("method.view", destination);
                    else if (section.equals("renderer")) {
                        if (source.contains(" pick(float)")) props.setProperty("method.pick", destination);
                        if (source.contains(" getFov(")) props.setProperty("method.fov", destination);
                    }
                }
            } finally { connection.disconnect(); }
            if (!props.containsKey("class.mc")) return null;
            if (!output.getParentFile().isDirectory() && !output.getParentFile().mkdirs()) return null;
            File tmp = new File(output.getPath() + ".part");
            try (FileOutputStream stream = new FileOutputStream(tmp)) { props.store(stream, "Names only, extracted from the official client mapping"); stream.getFD().sync(); }
            if (!tmp.renameTo(output)) { tmp.delete(); return null; }
            return output;
        } catch (Exception ex) { android.util.Log.i("Jogger", "Using named/intermediary touch hooks; official mappings unavailable"); return null; }
    }
}
