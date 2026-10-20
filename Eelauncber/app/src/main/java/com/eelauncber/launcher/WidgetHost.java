package com.eelauncber.launcher;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

/** Real AppWidgetHost with custom gallery, provider configuration and saved sizes. */
final class WidgetHost {
    static final int HOST_ID = 9826, CONFIG = 813, BIND = 814;
    final LauncherActivity a;
    final AppWidgetHost host;
    final AppWidgetManager manager;
    private int pending = -1, pendingX = 2, pendingY = 2, pendingPage = 0;
    private AppWidgetProviderInfo selected;

    WidgetHost(LauncherActivity a) {
        this.a = a;
        host = new AppWidgetHost(a, HOST_ID);
        manager = AppWidgetManager.getInstance(a);
    }

    void start() {try {host.startListening();}catch(Exception ignored) {}}
    void stop() {try {host.stopListening();}catch(Exception ignored) {}}

    void pick() {WidgetGallery.show(a);}

    void beginAdd(AppWidgetProviderInfo info, int x, int y) {
        if (pending >= 0) cancel();
        selected = info;
        pendingX = Math.max(1, Math.min(4, x));
        pendingY = Math.max(1, Math.min(5, y));
        pendingPage = a.pager == null ? 0 : a.pager.getPage();
        try {
            pending = host.allocateAppWidgetId();
            if (manager.bindAppWidgetIdIfAllowed(pending, info.provider)) {
                configureOrPlace();
            } else {
                Intent consent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                consent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending);
                consent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                a.startActivityForResult(consent, BIND);
            }
        } catch(Exception e) {
            cancel();
            CupertinoNotice.show(a, "Cannot add this widget");
        }
    }

    boolean result(int request, int resultCode, Intent data) {
        if (request != BIND && request != CONFIG) return false;
        if (resultCode != Activity.RESULT_OK || pending < 0) {
            cancel();
            return true;
        }
        if (request == BIND) configureOrPlace();
        else place();
        return true;
    }

    private void configureOrPlace() {
        if (pending < 0) return;
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(pending);
        if (info == null) {
            cancel();
            CupertinoNotice.show(a, "Widget permission was not granted");
            return;
        }
        if (info.configure != null) {
            try {
                Intent configure = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                configure.setComponent(info.configure);
                configure.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending);
                a.startActivityForResult(configure, CONFIG);
                return;
            } catch(Exception e) {
                cancel();
                CupertinoNotice.show(a, "Widget setup unavailable");
                return;
            }
        }
        place();
    }

    private void place() {
        if (pending < 0 || selected == null) return;
        int position = -1, page = pendingPage;
        for (int p = pendingPage; p < 50; p++) {
            boolean[] used = new boolean[20];
            for (HomeState.WidgetPlacement w : a.home.widgets) {
                if (w.page != p) continue;
                int cell = HomePages.locate(used, w.cell, w.spanX, w.spanY);
                if (cell >= 0) HomePages.occupy(used, cell, w.spanX, w.spanY);
            }
            position = HomePages.locate(used, 0, pendingX, pendingY);
            if (position >= 0) {page = p; break;}
        }
        if (position < 0) {
            cancel();
            CupertinoNotice.show(a, "No space for this widget");
            return;
        }
        a.home.widgets.add(new HomeState.WidgetPlacement(pending,
                selected.provider.flattenToString(),
                page, position, pendingX, pendingY));
        pending = -1;
        selected = null;
        a.home.save(a.prefs);
        HomeScreen.draw(a);
    }

    private void cancel() {
        if (pending >= 0) {
            try {host.deleteAppWidgetId(pending);}catch(Exception ignored) {}
        }
        pending = -1;
        selected = null;
    }

    View view(HomeState.WidgetPlacement widget) {
        if(EelaWidgets.custom(widget))return EelaWidgets.view(a,widget);
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(widget.id);
        if (info == null) return placeholder();
        try {
            AppWidgetHostView view = host.createView(a, widget.id, info);
            view.setAppWidget(widget.id, info);
            // No launcher-added widget card, background, or padding.
            view.setBackgroundColor(Color.TRANSPARENT);
            view.setPadding(0, 0, 0, 0);
            view.setClipToPadding(false);
            view.post(() -> updateSize(widget, view));
            return view;
        } catch(Exception e) {
            return placeholder();
        }
    }

    private View placeholder() {
        TextView text = LauncherUi.text(a, "Widget unavailable", 13,
                0xFFB5B9C5, false);
        text.setBackgroundColor(Color.TRANSPARENT);
        return text;
    }

    void updateSize(HomeState.WidgetPlacement widget, AppWidgetHostView view) {
        if (!view.isAttachedToWindow()) return;
        int actualWidth = view.getWidth();
        int actualHeight = view.getHeight();
        if (actualWidth <= 0 || actualHeight <= 0) return;
        float density = a.getResources().getDisplayMetrics().density;
        int width = Math.max(1, Math.round(actualWidth / density));
        int height = Math.max(1, Math.round(actualHeight / density));
        Bundle options = manager.getAppWidgetOptions(widget.id);
        if (options == null) options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
        manager.updateAppWidgetOptions(widget.id, options);
    }

    void remove(HomeState.WidgetPlacement widget) {
        a.home.widgets.remove(widget);
        if(!EelaWidgets.custom(widget))try {host.deleteAppWidgetId(widget.id);}catch(Exception ignored) {}
        a.home.save(a.prefs);
        HomeScreen.draw(a);
    }
}
