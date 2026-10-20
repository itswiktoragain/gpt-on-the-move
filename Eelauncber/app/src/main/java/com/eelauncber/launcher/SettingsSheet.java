package com.eelauncber.launcher;

import android.app.WallpaperManager;
import android.content.Intent;
import java.util.ArrayList;

final class SettingsSheet {
    static void open(LauncherActivity a) {
        if (a.prefs == null) return;
        boolean labels = a.prefs.getBoolean("labels", true);
        boolean darkDock = a.prefs.getBoolean("dark_dock", false);
        boolean bigIcons = a.prefs.getInt("icon_size", 58) > 60;
        CupertinoSheet.show(a, "Customize Home Screen", "Your home, your way",
                CupertinoSheet.action("＋", "Add system widget", () -> a.widgets.pick()),
                CupertinoSheet.action("Aa", "App labels", labels ? "On" : "Off", () -> {
                    a.prefs.edit().putBoolean("labels", !labels).apply();
                    HomeScreen.draw(a);
                    open(a);
                }),
                CupertinoSheet.action("◐", "Dock appearance", darkDock ? "Dark" : "Light", () -> {
                    a.prefs.edit().putBoolean("dark_dock", !darkDock).apply();
                    HomeScreen.draw(a);
                    open(a);
                }),
                CupertinoSheet.action("◎", "Icon size", bigIcons ? "Large" : "Normal", () -> {
                    a.prefs.edit().putInt("icon_size", bigIcons ? 58 : 65).apply();
                    HomeScreen.draw(a);
                    open(a);
                }),
                CupertinoSheet.action("◇", "Change wallpaper", () -> {
                    try {
                        a.startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                    } catch (Exception ignored) {
                        try {
                            a.startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
                        } catch (Exception failure) {
                            CupertinoNotice.show(a, "Wallpaper picker unavailable");
                        }
                    }
                }),
                CupertinoSheet.action("−", "Hide a home app", () -> chooseHidden(a)));
    }

    private static void chooseHidden(LauncherActivity a) {
        ArrayList<CupertinoSheet.Action> choices = new ArrayList<>();
        for (AppCatalog.Entry app : a.catalog.all) {
            if (!a.home.includes(app.key)) continue;
            choices.add(CupertinoSheet.action("−", app.label, () -> {
                a.home.hidden.add(app.key);
                for (HomeState.Item item : a.home.pages) item.apps.remove(app.key);
                for (HomeState.Item item : a.home.dock) item.apps.remove(app.key);
                a.home.pages.removeIf(item -> item.apps.isEmpty());
                a.home.dock.removeIf(item -> item.apps.isEmpty());
                LauncherActions.save(a);
                CupertinoNotice.show(a, "Removed from Home Screen");
            }));
        }
        if (choices.isEmpty()) {
            CupertinoNotice.show(a, "Your home has no apps to hide");
            return;
        }
        CupertinoSheet.searchable(a, "Remove a Home App",
                choices.toArray(new CupertinoSheet.Action[0]));
    }
}
