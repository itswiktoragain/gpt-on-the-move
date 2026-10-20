package com.eelauncber.launcher;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;

final class HomeState {
    static final class Item {
        String id = UUID.randomUUID().toString();
        String name = "";
        int page = -1, cell = -1;
        final ArrayList<String> apps = new ArrayList<>();
        Item() {}
        Item(String key) { apps.add(key); }
        boolean isFolder() { return apps.size() > 1; }
        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            JSONArray arr = new JSONArray();
            for (String app : apps) arr.put(app);
            obj.put("id", id); obj.put("name", name); obj.put("apps", arr);
            obj.put("page", page); obj.put("cell", cell);
            return obj;
        }
        static Item fromJson(JSONObject obj) {
            Item item = new Item();
            item.id = obj.optString("id", UUID.randomUUID().toString());
            item.name = obj.optString("name", "");
            item.page = obj.optInt("page", -1);
            item.cell = obj.optInt("cell", -1);
            JSONArray arr = obj.optJSONArray("apps");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                String key = arr.optString(i, "");
                if (!key.isEmpty() && !item.apps.contains(key)) item.apps.add(key);
            }
            return item;
        }
    }

    static final class WidgetPlacement {
        int id;
        String provider;
        int page, cell, spanX, spanY;
        WidgetPlacement(int id, String provider, int page, int cell, int spanX, int spanY) {
            this.id = id;
            this.provider = provider;
            this.page = page;
            this.cell = cell;
            this.spanX = spanX;
            this.spanY = spanY;
        }
        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("provider", provider);
            o.put("page", page); o.put("cell", cell);
            o.put("spanX", spanX); o.put("spanY", spanY);
            return o;
        }
        static WidgetPlacement fromJson(JSONObject o) {
            int id = o.optInt("id", -1);
            String provider = o.optString("provider", "");
            if (id <= 0 || provider.isEmpty()) return null;
            return new WidgetPlacement(id, provider,
                    Math.max(0, Math.min(49, o.optInt("page", 0))),
                    Math.max(0, Math.min(19, o.optInt("cell", 0))),
                    Math.max(1, Math.min(4, o.optInt("spanX", 2))),
                    Math.max(1, Math.min(5, o.optInt("spanY", 2))));
        }
    }

    final ArrayList<Item> pages = new ArrayList<>();
    final ArrayList<Item> dock = new ArrayList<>();
    final ArrayList<WidgetPlacement> widgets = new ArrayList<>();
    final HashSet<String> hidden = new HashSet<>();

    boolean includes(String key) {
        for (Item item : pages) if (item.apps.contains(key)) return true;
        for (Item item : dock) if (item.apps.contains(key)) return true;
        return false;
    }

    static HomeState read(SharedPreferences prefs, AppCatalog apps) {
        HomeState home = new HomeState();
        String raw = prefs.getString("layout", "");
        if (!validLayout(raw)) raw = prefs.getString("layout_backup", "");
        if (!raw.isEmpty()) {
            try {
                JSONObject root = new JSONObject(raw);
                load(root.optJSONArray("pages"), home.pages, apps);
                load(root.optJSONArray("dock"), home.dock, apps);
                JSONArray hidden = root.optJSONArray("hidden");
                if (hidden != null) for (int i = 0; i < hidden.length(); i++) {
                    String key = hidden.optString(i, "");
                    if (!key.isEmpty()) home.hidden.add(key);
                }
                JSONArray widgetItems = root.optJSONArray("widgets");
                if (widgetItems != null) for (int i = 0; i < widgetItems.length(); i++) {
                    JSONObject obj = widgetItems.optJSONObject(i);
                    if (obj == null) continue;
                    WidgetPlacement widget = WidgetPlacement.fromJson(obj);
                    if (widget != null) home.widgets.add(widget);
                }
            } catch (Exception ignored) {}
        }
        // Intentionally empty on first launch: never auto-populate the dock or home.
        // Apps can be added explicitly using the swipe-up app library.
        while (home.dock.size() > 4) home.pages.add(0, home.dock.remove(home.dock.size() - 1));
        home.normalize();
        return home;
    }

    /** Keep explicit gaps while migrating old sequential layouts and avoiding widgets. */
    void normalize() {
        boolean[][] used = new boolean[50][20];
        for (WidgetPlacement widget : widgets) {
            if (widget.page >= 50) continue;
            if (!HomePages.free(used[widget.page], widget.cell, widget.spanX, widget.spanY)) continue;
            HomePages.occupy(used[widget.page], widget.cell, widget.spanX, widget.spanY);
        }
        for (Item item : pages) {
            if (item.page >= 0 && item.page < 50 && item.cell >= 0 && item.cell < 20 && !used[item.page][item.cell]) {
                used[item.page][item.cell] = true;
            } else {
                item.page = -1; item.cell = -1;
            }
        }
        for (Item item : pages) if (item.page < 0) {
            for (int p = 0; p < 50 && item.page < 0; p++)
                for (int c = 0; c < 20; c++) if (!used[p][c]) {
                    item.page = p; item.cell = c; used[p][c] = true; break;
                }
        }
    }

    private static boolean validLayout(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        try {
            JSONObject object = new JSONObject(raw);
            return object.optJSONArray("pages") != null && object.optJSONArray("dock") != null;
        } catch (Exception ignored) { return false; }
    }

    private static void load(JSONArray array, ArrayList<Item> result, AppCatalog catalog) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) continue;
            Item item = Item.fromJson(obj);
            // Keep unavailable package IDs: the PackageManager can return an incomplete
            // app list immediately after boot, and clearing them would erase the layout.
            if (!item.apps.isEmpty()) result.add(item);
        }
    }
    void save(SharedPreferences prefs) {
        try {
            JSONArray apps = new JSONArray();
            JSONArray bottom = new JSONArray();
            JSONArray widgetArray = new JSONArray();
            JSONArray hiddenArray = new JSONArray();
            for (Item item : pages) apps.put(item.toJson());
            for (Item item : dock) bottom.put(item.toJson());
            for (WidgetPlacement widget : widgets) widgetArray.put(widget.toJson());
            for (String key : hidden) hiddenArray.put(key);
            JSONObject root = new JSONObject();
            root.put("pages", apps); root.put("dock", bottom);
            root.put("widgets", widgetArray); root.put("hidden", hiddenArray);
            String json = root.toString();
            // commit() writes to persistent storage before returning, including when the
            // launcher is immediately stopped, restarted or the device shuts down.
            boolean saved = prefs.edit()
                    .putString("layout", json)
                    .putString("layout_backup", json)
                    .commit();
            if (!saved) android.util.Log.e("Eelauncber", "Could not persist home layout");
        } catch (Exception error) {
            android.util.Log.e("Eelauncber", "Saving home layout failed", error);
        }
    }
}
