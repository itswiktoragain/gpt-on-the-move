package com.eelauncber.launcher;

import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;

/** Moves a widget's WHOLE footprint, never only the cell below the finger. */
final class WidgetMove {
    static final class Ref {
        final HomeState.WidgetPlacement widget;
        boolean used;
        boolean ended;
        boolean noSpace;
        Ref(HomeState.WidgetPlacement widget) { this.widget = widget; }
    }

    static int bestCell(LauncherActivity a, HomeState.WidgetPlacement moving,
                        int targetPage, int hovered) {
        if (targetPage < 0 || targetPage >= 50) return -1;
        boolean[] occupied = new boolean[WidgetGridFit.CELLS];

        // Ignore the moving widget's old footprint (important for 4x5 widgets).
        // Other widgets occupy entire rectangles rather than just their origins.
        for (HomeState.WidgetPlacement other : a.home.widgets) {
            if (other == moving || other.page != targetPage) continue;
            int origin = HomePages.locate(occupied, other.cell,
                    other.spanX, other.spanY);
            if (origin >= 0)
                WidgetGridFit.occupy(occupied, origin, other.spanX, other.spanY);
        }
        for (HomeState.Item app : a.home.pages) {
            if (app.page == targetPage && app.cell >= 0 && app.cell < 20)
                occupied[app.cell] = true;
        }
        return WidgetGridFit.nearest(occupied, hovered, moving.spanX, moving.spanY);
    }

    static boolean drop(LauncherActivity a, Ref ref, int page, int hovered) {
        if (ref.used) return true;
        HomeState.WidgetPlacement widget = ref.widget;
        int target = bestCell(a, widget, page, hovered);
        if (target < 0) {
            ref.noSpace = true;
            return false;
        }
        ref.used = true;
        widget.page = page;
        widget.cell = target;
        a.home.save(a.prefs);
        a.root.post(() -> HomeScreen.draw(a));
        return true;
    }

    /** Widget surfaces can cover multiple rows/columns, so use drag coordinates. */
    static boolean dropOnWidget(LauncherActivity a, Ref ref, View receiver,
                                DragEvent event, HomeState.WidgetPlacement targetWidget) {
        ViewGroup parent = receiver.getParent() instanceof ViewGroup
                ? (ViewGroup) receiver.getParent() : null;
        if (parent == null) return false;
        int columnCell = WidgetGridFit.hoveredCell(
                receiver.getLeft() + event.getX(),
                receiver.getTop() + event.getY(),
                parent.getWidth(), parent.getHeight());
        return drop(a, ref, targetWidget.page, columnCell);
    }

    static boolean moveToPage(LauncherActivity a, HomeState.WidgetPlacement widget,
                              int page) {
        if (page < 0 || page >= 50) return false;
        int nearest = bestCell(a, widget, page, widget.cell);
        if (nearest < 0) {
            CupertinoNotice.show(a, "This page has no space for that size");
            return false;
        }
        widget.page = page;
        widget.cell = nearest;
        a.home.save(a.prefs);
        HomeScreen.draw(a);
        return true;
    }

    static void end(LauncherActivity a, Ref ref) {
        if (ref.ended) return;
        ref.ended = true;
        if (!ref.used) {
            if (ref.noSpace)
                CupertinoNotice.show(a, "No free area on this page");
            a.root.post(() -> HomeScreen.draw(a));
        }
    }
}
