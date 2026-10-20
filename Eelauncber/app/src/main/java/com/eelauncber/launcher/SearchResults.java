package com.eelauncber.launcher;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Recycled alphabetical App Library rows with live filtering. */
final class SearchResults extends BaseAdapter {
    private final LauncherActivity activity;
    private final ArrayList<AppCatalog.Entry> results = new ArrayList<>();
    private String query = "";

    SearchResults(LauncherActivity activity) {
        this.activity = activity;
        filter("");
    }

    void filter(String text) {
        query = text.trim().toLowerCase(Locale.getDefault());
        results.clear();
        for (AppCatalog.Entry app : activity.catalog.all) {
            if (query.isEmpty()
                    || app.label.toLowerCase(Locale.getDefault()).contains(query)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                results.add(app);
            }
        }
        notifyDataSetChanged();
    }

    int matches() { return results.size(); }
    @Override public int getCount() { return results.size(); }
    @Override public AppCatalog.Entry getItem(int position) { return results.get(position); }
    @Override public long getItemId(int position) { return results.get(position).key.hashCode(); }

    private boolean isFirst(int position) {
        if (position == 0) return true;
        if (!query.isEmpty()) return false;
        return !letter(results.get(position).label).equals(letter(results.get(position - 1).label));
    }
    private String letter(String label) {
        if (label == null || label.isEmpty()) return "#";
        char ch = Character.toUpperCase(label.charAt(0));
        return Character.isLetter(ch) ? String.valueOf(ch) : "#";
    }

    @Override public View getView(int position, View previous, ViewGroup parent) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        if (isFirst(position)) {
            TextView section = LauncherUi.text(activity,
                    query.isEmpty() ? letter(getItem(position).label) : "RESULTS",
                    13, 0xFF9CA3B2, true);
            section.setGravity(Gravity.LEFT | Gravity.BOTTOM);
            section.setPadding(LauncherUi.dp(activity, 26), 0, 0, LauncherUi.dp(activity, 7));
            container.addView(section, new LinearLayout.LayoutParams(-1, LauncherUi.dp(activity, 31)));
        }
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(LauncherUi.dp(activity, 20), 0,
                LauncherUi.dp(activity, 20), 0);

        AppCatalog.Entry app = getItem(position);
        ImageView icon = LauncherUi.icon(activity, app.key, 42);
        row.addView(icon, new LinearLayout.LayoutParams(
                LauncherUi.dp(activity, 45), LauncherUi.dp(activity, 45)));

        TextView name = LauncherUi.text(activity, app.label, 16, 0xFFF8F8FA, false);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        name.setSingleLine();
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -1, 1f);
        nameParams.leftMargin = LauncherUi.dp(activity, 16);
        row.addView(name, nameParams);

        if (activity.home.includes(app.key)) {
            ImageView check = new ImageView(activity);
            check.setImageResource(R.drawable.f7_check);
            check.setColorFilter(0xFF9B9FA8);
            check.setPadding(LauncherUi.dp(activity, 5), LauncherUi.dp(activity, 5),
                    LauncherUi.dp(activity, 5), LauncherUi.dp(activity, 5));
            row.addView(check, new LinearLayout.LayoutParams(
                    LauncherUi.dp(activity, 25), LauncherUi.dp(activity, 25)));
        }

        row.setBackground(GlassUi.chip(activity, 13));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                -1, LauncherUi.dp(activity, 59));
        rowParams.setMargins(LauncherUi.dp(activity, 10), 0, LauncherUi.dp(activity, 10), 0);
        container.addView(row, rowParams);

        row.setOnClickListener(v -> {
            activity.controller.closeShelf();
            LauncherActions.launch(activity, app.key);
        });
        row.setOnLongClickListener(v -> {
            AppOptions.open(activity, app);
            return true;
        });
        return container;
    }
}
