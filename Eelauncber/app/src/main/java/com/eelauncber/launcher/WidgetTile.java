package com.eelauncber.launcher;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

final class WidgetTile {
    static View create(LauncherActivity a, HomeState.WidgetPlacement widget) {
        FrameLayout outer = new FrameLayout(a);
        View content = a.widgets.view(widget);
        outer.addView(content, new FrameLayout.LayoutParams(-1, -1));
        outer.setClipChildren(true);
        if (a.editing) {
            TextView control = LauncherUi.text(a, "⋯  Edit Widget", 13, 0xFFF5F8FF, true);
            control.setBackground(LauncherUi.bg(a, 0xE528344A, 15));
            control.setElevation(LauncherUi.dp(a, 6));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    LauncherUi.dp(a, 122), LauncherUi.dp(a, 37),
                    Gravity.TOP | Gravity.RIGHT);
            params.setMargins(0, LauncherUi.dp(a, 3), LauncherUi.dp(a, 3), 0);
            outer.addView(control, params);
            control.setOnClickListener(v -> edit(a, widget));
            outer.setOnLongClickListener(v -> { edit(a, widget); return true; });
        }
        return outer;
    }

    private static void edit(LauncherActivity a, HomeState.WidgetPlacement widget) {
        CupertinoSheet.show(a, "Customize Widget", "Adjust size or change pages",
                CupertinoSheet.action("→", "Move to next page", () -> {
                    widget.page = Math.min(49, widget.page + 1);
                    save(a);
                }),
                CupertinoSheet.action("←", "Move to previous page", () -> {
                    widget.page = Math.max(0, widget.page - 1);
                    save(a);
                }),
                CupertinoSheet.action("↔", "Make wider", () -> {
                    widget.spanX = Math.min(4, widget.spanX + 1);
                    save(a);
                }),
                CupertinoSheet.action("↔", "Make narrower", () -> {
                    widget.spanX = Math.max(1, widget.spanX - 1);
                    save(a);
                }),
                CupertinoSheet.action("↕", "Make taller", () -> {
                    widget.spanY = Math.min(5, widget.spanY + 1);
                    save(a);
                }),
                CupertinoSheet.action("↕", "Make shorter", () -> {
                    widget.spanY = Math.max(1, widget.spanY - 1);
                    save(a);
                }),
                CupertinoSheet.danger("−", "Remove widget",
                        () -> CupertinoSheet.confirm(a, "Remove Widget",
                                "You can add it again from the widget picker.",
                                "Remove", () -> a.widgets.remove(widget))));
    }

    private static void save(LauncherActivity a) {
        a.home.save(a.prefs);
        HomeScreen.draw(a);
    }
}
