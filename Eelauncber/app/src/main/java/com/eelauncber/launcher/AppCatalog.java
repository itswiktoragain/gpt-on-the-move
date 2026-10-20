package com.eelauncber.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

final class AppCatalog {
    static final class Entry {
        final String key;
        final String label;
        final String packageName;
        final Drawable icon;
        Entry(String key, String label, String packageName, Drawable icon) {
            this.key = key; this.label = label; this.packageName = packageName; this.icon = icon;
        }
    }

    final ArrayList<Entry> all = new ArrayList<>();
    final HashMap<String, Entry> byKey = new HashMap<>();

    static AppCatalog scan(Context context) {
        AppCatalog catalog = new AppCatalog();
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        HashSet<String> seen = new HashSet<>();
        for (ResolveInfo info : found) {
            if (info.activityInfo == null) continue;
            ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            String key = component.flattenToString();
            if (!seen.add(key)) continue;
            try {
                CharSequence label = info.loadLabel(pm);
                Drawable icon = info.loadIcon(pm);
                catalog.all.add(new Entry(key, label == null ? info.activityInfo.name : label.toString(),
                        info.activityInfo.packageName, icon));
            } catch (RuntimeException ignored) { }
        }
        final Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(catalog.all, (a, b) -> {
            int result = collator.compare(a.label, b.label);
            return result != 0 ? result : a.key.compareTo(b.key);
        });
        for (Entry entry : catalog.all) catalog.byKey.put(entry.key, entry);
        return catalog;
    }
    Entry get(String key) { return byKey.get(key); }
}
