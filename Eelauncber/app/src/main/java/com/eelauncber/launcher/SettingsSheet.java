package com.eelauncber.launcher;

import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import android.graphics.drawable.GradientDrawable;
import java.util.ArrayList;

/** iOS Settings-like grouped home customization, not a generic action dialog. */
final class SettingsSheet {
    static final int BG = 0x935B6B88;
    static final int GROUP = 0x82576682;
    static final int TEXT = 0xFFF8F8FA;
    static final int MUTED = 0xFF9EA2AE;

    static void open(LauncherActivity a) {
        a.controller.closeShelf();
        FrameLayout overlay = new FrameLayout(a);
        overlay.setBackgroundColor(0x090B1118);
        overlay.setOnClickListener(v -> a.controller.closeShelf());

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(GlassUi.sheet(a, 30));
        panel.setClickable(true);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -1);
        panelParams.topMargin = LauncherUi.dp(a, 56);
        overlay.addView(panel, panelParams);

        View grabber = new View(a);
        grabber.setBackground(LauncherUi.bg(a, 0xA5BCC0CA, 4));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(LauncherUi.dp(a, 37), LauncherUi.dp(a, 5));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = LauncherUi.dp(a, 10);
        panel.addView(grabber, gp);

        LinearLayout navigation = new LinearLayout(a);
        navigation.setPadding(LauncherUi.dp(a, 22), LauncherUi.dp(a, 21),
                LauncherUi.dp(a, 18), LauncherUi.dp(a, 10));
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = LauncherUi.text(a, "Customize", 29, TEXT, true);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        navigation.addView(title, new LinearLayout.LayoutParams(0, LauncherUi.dp(a, 52), 1));
        TextView done = LauncherUi.text(a, "Done", 16, 0xFF98B9FF, true);
        navigation.addView(done, new LinearLayout.LayoutParams(LauncherUi.dp(a, 65),
                LauncherUi.dp(a, 46)));
        done.setOnClickListener(v -> a.controller.closeShelf());
        panel.addView(navigation);

        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(LauncherUi.dp(a, 19), 0, LauncherUi.dp(a, 19),
                LauncherUi.dp(a, 35));
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        sectionTitle(a, content, "WIDGETS");
        LinearLayout widgets = group(a);
        content.addView(widgets);
        item(a, widgets, R.drawable.f7_widget, "Add Widgets", null,
                () -> { a.controller.closeShelf(); a.widgets.pick(); });

        sectionTitle(a, content, "HOME SCREEN");
        LinearLayout home = group(a);
        content.addView(home);
        item(a, home, R.drawable.f7_size, "Edit Home Screen", null,
                () -> { a.controller.closeShelf(); a.controller.setEditing(true); });
        toggle(a, home, R.drawable.f7_labels, "Show App Labels", a.prefs.getBoolean("labels", true),
                value -> { a.prefs.edit().putBoolean("labels", value).apply(); HomeScreen.draw(a); });
        toggle(a, home, R.drawable.f7_dock, "Dark Dock", a.prefs.getBoolean("dark_dock", false),
                value -> { a.prefs.edit().putBoolean("dark_dock", value).apply(); HomeScreen.draw(a); });
        item(a, home, R.drawable.f7_size, "Icon Size",
                a.prefs.getInt("icon_size", 58) > 60 ? "Large" : "Normal", () -> {
                    CupertinoSheet.show(a, "Icon Size", "",
                            CupertinoSheet.action("○", "Normal", () -> {
                                a.prefs.edit().putInt("icon_size", 58).apply();
                                HomeScreen.draw(a); a.controller.closeShelf(); open(a);
                            }),
                            CupertinoSheet.action("◎", "Large", () -> {
                                a.prefs.edit().putInt("icon_size", 65).apply();
                                HomeScreen.draw(a); a.controller.closeShelf(); open(a);
                            }));
                });

        sectionTitle(a, content, "PERSONALIZATION");
        LinearLayout extra = group(a);
        content.addView(extra);
        item(a, extra, R.drawable.f7_wallpaper, "Wallpaper", null, () -> {
            a.controller.closeShelf();
            try { a.startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); }
            catch(Exception e) {
                try { a.startActivity(new Intent(Intent.ACTION_SET_WALLPAPER)); }
                catch(Exception ignored) { CupertinoNotice.show(a, "Wallpaper picker unavailable"); }
            }
        });
        item(a, extra, R.drawable.f7_hide, "Remove Home App", null,
                () -> { a.controller.closeShelf(); chooseHidden(a); });

        GlassUi.show(a, overlay, panel);
    }

    private interface Change { void onChanged(boolean value); }

    private static void sectionTitle(LauncherActivity a, LinearLayout content, String title) {
        TextView header = LauncherUi.text(a, title, 12, MUTED, false);
        header.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, LauncherUi.dp(a, 44));
        header.setPadding(LauncherUi.dp(a, 17), 0, 0, LauncherUi.dp(a, 9));
        content.addView(header, p);
    }

    private static LinearLayout group(LauncherActivity a) {
        LinearLayout group = new LinearLayout(a);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(GlassUi.card(a, 19));
        group.setClipToOutline(true);
        return group;
    }

    private static void item(LauncherActivity a, LinearLayout group, int icon,
                             String title, String detail, Runnable click) {
        row(a, group, icon, title, detail, null, click);
    }

    private static void toggle(LauncherActivity a, LinearLayout group, int icon,
                               String title, boolean on, Change callback) {
        row(a, group, icon, title, null, on, null).setOnClickListener(v -> {});
        LinearLayout row = (LinearLayout) group.getChildAt(group.getChildCount() - 1);
        final FrameLayout switchTrack = (FrameLayout) row.getChildAt(row.getChildCount() - 1);
        switchTrack.setOnClickListener(v -> {
            boolean current = Boolean.TRUE.equals(switchTrack.getTag());
            updateSwitch(a, switchTrack, !current);
            callback.onChanged(!current);
        });
        row.setOnClickListener(v -> switchTrack.performClick());
    }

    private static View row(LauncherActivity a, LinearLayout group, int icon, String title,
                            String detail, Boolean toggle, Runnable click) {
        if (group.getChildCount() > 0) {
            View separator = new View(a);
            separator.setBackgroundColor(0x30FFFFFF);
            LinearLayout.LayoutParams sep = new LinearLayout.LayoutParams(-1, LauncherUi.dp(a, 1));
            sep.leftMargin = LauncherUi.dp(a, 64);
            group.addView(separator, sep);
        }
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(LauncherUi.dp(a, 15), 0, LauncherUi.dp(a, 14), 0);
        ImageView image = new ImageView(a);
        image.setImageResource(icon);
        image.setColorFilter(0xFFFAFAFF);
        image.setPadding(LauncherUi.dp(a, 7), LauncherUi.dp(a, 7),
                LauncherUi.dp(a, 7), LauncherUi.dp(a, 7));
        image.setBackground(GlassUi.chip(a, 10));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                LauncherUi.dp(a, 34), LauncherUi.dp(a, 34));
        iconParams.rightMargin = LauncherUi.dp(a, 12);
        row.addView(image, iconParams);
        TextView label = LauncherUi.text(a, title, 16, TEXT, false);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, -1, 1f));
        if (toggle != null) {
            FrameLayout track = new FrameLayout(a);
            updateSwitch(a, track, toggle);
            row.addView(track, new LinearLayout.LayoutParams(LauncherUi.dp(a, 51),
                    LauncherUi.dp(a, 31)));
        } else {
            if (detail != null) {
                TextView detailView = LauncherUi.text(a, detail, 14, MUTED, false);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -1);
                dp.rightMargin = LauncherUi.dp(a, 7);
                row.addView(detailView, dp);
            }
            ImageView chevron = new ImageView(a);
            chevron.setImageResource(R.drawable.f7_chevron);
            chevron.setColorFilter(0xFF8F95A3);
            row.addView(chevron, new LinearLayout.LayoutParams(LauncherUi.dp(a, 16),
                    LauncherUi.dp(a, 16)));
            row.setOnClickListener(v -> { if(click != null)click.run(); });
        }
        group.addView(row, new LinearLayout.LayoutParams(-1, LauncherUi.dp(a, 57)));
        return row;
    }

    private static void updateSwitch(LauncherActivity a, FrameLayout track, boolean state) {
        track.setTag(state);
        track.removeAllViews();
        track.setBackground(LauncherUi.bg(a, state ? 0xFF32D75C : 0xFF585B64, 17));
        View thumb = new View(a);
        thumb.setBackground(LauncherUi.bg(a, Color.WHITE, 15));
        thumb.setElevation(LauncherUi.dp(a, 2));
        FrameLayout.LayoutParams t = new FrameLayout.LayoutParams(LauncherUi.dp(a, 27),
                LauncherUi.dp(a, 27), state ? Gravity.CENTER_VERTICAL | Gravity.RIGHT :
                Gravity.CENTER_VERTICAL | Gravity.LEFT);
        if (state) t.rightMargin = LauncherUi.dp(a, 2);
        else t.leftMargin = LauncherUi.dp(a, 2);
        track.addView(thumb, t);
    }

    private static void chooseHidden(LauncherActivity a) {
        ArrayList<CupertinoSheet.Action> options = new ArrayList<>();
        for (AppCatalog.Entry app : a.catalog.all) {
            if (!a.home.includes(app.key)) continue;
            options.add(CupertinoSheet.action("", app.label, () -> {
                a.home.hidden.add(app.key);
                for (HomeState.Item item : a.home.pages) item.apps.remove(app.key);
                for (HomeState.Item item : a.home.dock) item.apps.remove(app.key);
                a.home.pages.removeIf(item -> item.apps.isEmpty());
                a.home.dock.removeIf(item -> item.apps.isEmpty());
                LauncherActions.save(a);
            }));
        }
        if (options.isEmpty()) {
            CupertinoNotice.show(a, "No apps on the Home Screen");
            return;
        }
        CupertinoSheet.searchable(a, "Remove App",
                options.toArray(new CupertinoSheet.Action[0]));
    }
}
