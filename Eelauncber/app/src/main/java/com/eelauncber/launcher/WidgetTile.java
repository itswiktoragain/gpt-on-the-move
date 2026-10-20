package com.eelauncber.launcher;

import android.appwidget.AppWidgetProviderInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Transparent widget host surface; blue edge and drag handle appear only in edit mode. */
final class WidgetTile {
    static View create(LauncherActivity a, HomeState.WidgetPlacement widget) {
        FrameLayout outer = new FrameLayout(a);
        outer.setBackgroundColor(Color.TRANSPARENT);
        outer.setClipChildren(false);
        outer.setClipToPadding(false);
        View content = a.widgets.view(widget);
        outer.addView(content, new FrameLayout.LayoutParams(-1, -1));
        if (!a.editing) {
            View.OnLongClickListener edit = v -> {a.controller.setEditing(true);return true;};
            outer.setOnLongClickListener(edit);
            content.setOnLongClickListener(edit);
            return outer;
        }

        View border = new View(a);
        GradientDrawable stroke = new GradientDrawable();
        stroke.setColor(Color.TRANSPARENT);
        stroke.setCornerRadius(LauncherUi.dp(a, 16));
        stroke.setStroke(LauncherUi.dp(a, 2), 0xFF0A84FF);
        border.setBackground(stroke);
        outer.addView(border, new FrameLayout.LayoutParams(-1, -1));

        TextView edit = LauncherUi.text(a, "Edit Widget", 13, 0xFFFFFFFF, true);
        edit.setGravity(Gravity.CENTER);
        edit.setBackground(LauncherUi.bg(a, 0xEB31343D, 16));
        edit.setElevation(LauncherUi.dp(a, 5));
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(
                LauncherUi.dp(a, 112), LauncherUi.dp(a, 34),
                Gravity.TOP | Gravity.LEFT);
        ep.leftMargin = LauncherUi.dp(a, 5);
        ep.topMargin = LauncherUi.dp(a, 5);
        outer.addView(edit, ep);
        edit.setOnClickListener(v -> options(a, widget));

        TextView indicator = LauncherUi.text(a,
                widget.spanX + " × " + widget.spanY, 12, 0xFFFFFFFF, true);
        indicator.setGravity(Gravity.CENTER);
        indicator.setBackground(LauncherUi.bg(a, 0xDE0A84FF, 14));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                LauncherUi.dp(a, 53), LauncherUi.dp(a, 27),
                Gravity.TOP | Gravity.RIGHT);
        ip.topMargin = LauncherUi.dp(a, 6);
        ip.rightMargin = LauncherUi.dp(a, 5);
        outer.addView(indicator, ip);

        ImageView handle = new ImageView(a);
        handle.setImageResource(R.drawable.f7_resize);
        handle.setColorFilter(0xFFFFFFFF);
        handle.setPadding(LauncherUi.dp(a, 10), LauncherUi.dp(a, 10),
                LauncherUi.dp(a, 10), LauncherUi.dp(a, 10));
        handle.setBackground(LauncherUi.bg(a, 0xFF0A84FF, 21));
        handle.setElevation(LauncherUi.dp(a, 9));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                LauncherUi.dp(a, 43), LauncherUi.dp(a, 43),
                Gravity.BOTTOM | Gravity.RIGHT);
        hp.bottomMargin = LauncherUi.dp(a, 3);
        hp.rightMargin = LauncherUi.dp(a, 3);
        outer.addView(handle, hp);
        resize(a, outer, handle, indicator, widget);
        outer.setOnLongClickListener(v -> { options(a, widget); return true; });
        return outer;
    }

    private static void resize(LauncherActivity a, FrameLayout outer, View handle,
                               TextView indicator, HomeState.WidgetPlacement item) {
        final float[] start = new float[2];
        final int[] initial = new int[2];
        final int[] next = new int[2];

        handle.setOnTouchListener((view, event) -> {
            if (outer.getParent() == null) return false;
            ViewGroup grid = (ViewGroup) outer.getParent();
            HomeGrid.Params p = (HomeGrid.Params) outer.getLayoutParams();
            int cellW = Math.max(1, grid.getWidth() / 4);
            int cellH = Math.max(1, grid.getHeight() / 5);
            AppWidgetProviderInfo info = a.widgets.manager.getAppWidgetInfo(item.id);
            int mode = info == null ? AppWidgetProviderInfo.RESIZE_BOTH : info.resizeMode;
            boolean horiz = (mode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
            boolean vert = (mode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
            int minX = 1, minY = 1;
            if (info != null) {
                int minWidth = horiz && info.minResizeWidth > 0 ?
                        Math.min(info.minWidth, info.minResizeWidth) : info.minWidth;
                int minHeight = vert && info.minResizeHeight > 0 ?
                        Math.min(info.minHeight, info.minResizeHeight) : info.minHeight;
                minX = Math.max(1, Math.min(4,
                        (int)Math.ceil((double)Math.max(1, minWidth) / cellW)));
                minY = Math.max(1, Math.min(5,
                        (int)Math.ceil((double)Math.max(1, minHeight) / cellH)));
            }
            int maxX = 4 - item.cell % 4;
            int maxY = 5 - item.cell / 4;
            minX = Math.min(minX, Math.max(1, maxX));
            minY = Math.min(minY, Math.max(1, maxY));

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = event.getRawX();
                    start[1] = event.getRawY();
                    initial[0] = item.spanX;
                    initial[1] = item.spanY;
                    next[0] = item.spanX;
                    next[1] = item.spanY;
                    grid.requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int x = horiz ? initial[0] +
                            Math.round((event.getRawX() - start[0]) / cellW) : initial[0];
                    int y = vert ? initial[1] +
                            Math.round((event.getRawY() - start[1]) / cellH) : initial[1];
                    x = clamp(x, minX, Math.max(minX, maxX));
                    y = clamp(y, minY, Math.max(minY, maxY));
                    boolean[] occupied = new boolean[20];
                    for (HomeState.WidgetPlacement w : a.home.widgets) {
                        if (w == item || w.page != item.page) continue;
                        int spot = HomePages.locate(occupied, w.cell, w.spanX, w.spanY);
                        if (spot >= 0) HomePages.occupy(occupied, spot, w.spanX, w.spanY);
                    }
                    if (!HomePages.free(occupied, item.cell, x, y)) return true;
                    next[0] = x;
                    next[1] = y;
                    p.sx = x;
                    p.sy = y;
                    indicator.setText(x + " × " + y);
                    grid.requestLayout();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    grid.requestDisallowInterceptTouchEvent(false);
                    item.spanX = next[0];
                    item.spanY = next[1];
                    a.home.save(a.prefs);
                    HomeScreen.draw(a);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    grid.requestDisallowInterceptTouchEvent(false);
                    p.sx = initial[0];
                    p.sy = initial[1];
                    grid.requestLayout();
                    return true;
            }
            return false;
        });
    }

    private static void options(LauncherActivity a, HomeState.WidgetPlacement widget) {
        CupertinoSheet.show(a, "Edit Widget", "Drag the blue corner to resize",
                CupertinoSheet.action("↔", "Move to Next Page", () -> {
                    widget.page = Math.min(49, widget.page + 1);
                    a.home.save(a.prefs); HomeScreen.draw(a);
                }),
                CupertinoSheet.action("↔", "Move to Previous Page", () -> {
                    widget.page = Math.max(0, widget.page - 1);
                    a.home.save(a.prefs); HomeScreen.draw(a);
                }),
                CupertinoSheet.danger("−", "Remove Widget",
                        () -> CupertinoSheet.confirm(a, "Remove Widget",
                                "You can add it again from the widget gallery.",
                                "Remove", () -> a.widgets.remove(widget))));
    }

    private static int clamp(int value,int low,int high) {
        return Math.max(low, Math.min(high, value));
    }
}
