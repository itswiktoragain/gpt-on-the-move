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
        final ArrayList<String> apps = new ArrayList<>();
        Item() { }
        Item(String key) { apps.add(key); }
        boolean isFolder() { return apps.size() > 1; }
        JSONObject toJson() throws Exception {
            JSONObject obj = new JSONObject();
            JSONArray arr = new JSONArray();
            for (String app : apps) arr.put(app);
            obj.put("id", id); obj.put("name", name); obj.put("apps", arr);
            return obj;
        }
        static Item fromJson(JSONObject obj) {
            Item item = new Item();
            item.id = obj.optString("id", UUID.randomUUID().toString());
            item.name = obj.optString("name", "");
            JSONArray arr = obj.optJSONArray("apps");
            if (arr != null) for (int i=0;i<arr.length();i++) {
                String key = arr.optString(i, "");
                if (!key.isEmpty() && !item.apps.contains(key)) item.apps.add(key);
            }
            return item;
        }
    }

    final ArrayList<Item> pages = new ArrayList<>();
    final ArrayList<Item> dock = new ArrayList<>();

    boolean includes(String key) {
        for (Item item:pages) if (item.apps.contains(key)) return true;
        for (Item item:dock) if (item.apps.contains(key)) return true;
        return false;
    }

    static HomeState read(SharedPreferences prefs, AppCatalog apps) {
        HomeState home = new HomeState();
        String raw = prefs.getString("layout", "");
        if (!raw.isEmpty()) {
            try {
                JSONObject root = new JSONObject(raw);
                load(root.optJSONArray("pages"), home.pages, apps);
                load(root.optJSONArray("dock"), home.dock, apps);
            } catch (Exception ignored) { }
        }
        if (raw.isEmpty() || (home.pages.isEmpty() && home.dock.isEmpty())) {
            for (AppCatalog.Entry e : apps.all) {
                String n = e.packageName.toLowerCase(Locale.ROOT);
                int score = 999;
                if (n.contains("dialer") || n.contains("phone")) score = 0;
                else if (n.contains("messag")) score = 1;
                else if (n.contains("chrome") || n.contains("browser")) score = 2;
                else if (n.contains("camera")) score = 3;
                if (score < 4 && home.dock.size() < 4 && !home.includes(e.key)) home.dock.add(new Item(e.key));
            }
            for (AppCatalog.Entry e : apps.all) {
                if (home.dock.size() >= 4) break;
                if (!home.includes(e.key)) home.dock.add(new Item(e.key));
            }
        }
        for (AppCatalog.Entry e : apps.all) if (!home.includes(e.key)) home.pages.add(new Item(e.key));
        while (home.dock.size() > 4) home.pages.add(0, home.dock.remove(home.dock.size()-1));
        return home;
    }
    private static void load(JSONArray source, ArrayList<Item> into, AppCatalog catalog) {
        if (source == null) return;
        for(int i=0;i<source.length();i++) {
            JSONObject object = source.optJSONObject(i);
            if(object == null) continue;
            Item item=Item.fromJson(object);
            item.apps.removeIf(key -> catalog.get(key)==null);
            if(!item.apps.isEmpty()) into.add(item);
        }
    }

    void save(SharedPreferences prefs) {
        try {
            JSONArray home = new JSONArray();
            JSONArray bottom = new JSONArray();
            for (Item item : pages) home.put(item.toJson());
            for (Item item : dock) bottom.put(item.toJson());
            JSONObject root = new JSONObject();
            root.put("pages", home); root.put("dock", bottom);
            prefs.edit().putString("layout", root.toString()).apply();
        } catch (Exception ignored) { }
    }
}
