package com.eelauncber.launcher;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.graphics.drawable.ColorDrawable;

/** Spotlight / App Library-inspired search. Entire layout replaces the old Android-style list sheet. */
final class SearchPanel {
    private static final int TEXT = 0xFFF7F7FA;
    private static final int MUTED = 0xFF9DA1AD;

    static void show(LauncherActivity a, String initial) {
        a.controller.closeShelf();
        FrameLayout overlay = new FrameLayout(a);
        overlay.setBackgroundColor(0x090C0E14);
        overlay.setOnClickListener(v -> dismiss(a));

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(GlassUi.sheet(a, 31));
        panel.setClickable(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        lp.topMargin = LauncherUi.dp(a, 53);
        overlay.addView(panel, lp);

        View handle = new View(a);
        handle.setBackground(LauncherUi.bg(a, 0x8A969BA7, 5));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LauncherUi.dp(a, 38), LauncherUi.dp(a, 5));
        hp.topMargin = LauncherUi.dp(a, 11);
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(handle, hp);

        LinearLayout titleBar = new LinearLayout(a);
        titleBar.setPadding(LauncherUi.dp(a, 24), LauncherUi.dp(a, 16),
                LauncherUi.dp(a, 20), LauncherUi.dp(a, 10));
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = LauncherUi.text(a, "App Library", 30, TEXT, true);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        titleBar.addView(title, new LinearLayout.LayoutParams(0, LauncherUi.dp(a, 50), 1f));

        ImageView dismiss = new ImageView(a);
        dismiss.setImageResource(R.drawable.f7_close);
        dismiss.setColorFilter(0xFFE1E2E8);
        dismiss.setPadding(LauncherUi.dp(a, 12), LauncherUi.dp(a, 12),
                LauncherUi.dp(a, 12), LauncherUi.dp(a, 12));
        dismiss.setBackground(GlassUi.chip(a, 22));
        titleBar.addView(dismiss, new LinearLayout.LayoutParams(
                LauncherUi.dp(a, 41), LauncherUi.dp(a, 41)));
        dismiss.setOnClickListener(v -> dismiss(a));
        panel.addView(titleBar);

        LinearLayout searchBar = new LinearLayout(a);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(LauncherUi.dp(a, 12), 0, LauncherUi.dp(a, 9), 0);
        searchBar.setBackground(GlassUi.input(a, 15));
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-1, LauncherUi.dp(a, 49));
        sbp.leftMargin = LauncherUi.dp(a, 22);
        sbp.rightMargin = LauncherUi.dp(a, 22);
        sbp.bottomMargin = LauncherUi.dp(a, 13);
        panel.addView(searchBar, sbp);
        ImageView lens = new ImageView(a);
        lens.setImageResource(R.drawable.f7_search);
        lens.setColorFilter(0xFFAFB0BB);
        searchBar.addView(lens, new LinearLayout.LayoutParams(
                LauncherUi.dp(a, 24), LauncherUi.dp(a, 24)));

        EditText field = new EditText(a);
        field.setTextSize(16);
        field.setTextColor(TEXT);
        field.setHintTextColor(0xFFA9A9B2);
        field.setHint("Search apps");
        field.setSingleLine(true);
        field.setBackgroundColor(Color.TRANSPARENT);
        field.setPadding(LauncherUi.dp(a, 10), 0, 0, 0);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBar.addView(field, new LinearLayout.LayoutParams(0, -1, 1));

        ImageView clear = new ImageView(a);
        clear.setImageResource(R.drawable.f7_close);
        clear.setColorFilter(0xFFE6E8EC);
        clear.setPadding(LauncherUi.dp(a, 7), LauncherUi.dp(a, 7),
                LauncherUi.dp(a, 7), LauncherUi.dp(a, 7));
        clear.setBackground(GlassUi.chip(a, 16));
        searchBar.addView(clear, new LinearLayout.LayoutParams(
                LauncherUi.dp(a, 27), LauncherUi.dp(a, 27)));
        clear.setOnClickListener(v -> field.setText(""));
        clear.setVisibility(View.GONE);

        LinearLayout browsing = new LinearLayout(a);
        browsing.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.leftMargin = LauncherUi.dp(a, 22);
        bp.rightMargin = LauncherUi.dp(a, 22);
        bp.bottomMargin = LauncherUi.dp(a, 10);
        panel.addView(browsing, bp);
        makeBrowse(a, browsing);

        TextView section = LauncherUi.text(a, "ALL APPS", 12, MUTED, true);
        section.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        section.setPadding(LauncherUi.dp(a, 28), 0, 0, 0);
        panel.addView(section, new LinearLayout.LayoutParams(-1, LauncherUi.dp(a, 34)));

        SearchResults adapter = new SearchResults(a);
        ListView list = new ListView(a);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setPadding(LauncherUi.dp(a, 11), 0, LauncherUi.dp(a, 11), LauncherUi.dp(a, 20));
        list.setAdapter(adapter);
        panel.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView count = LauncherUi.text(a, "", 12, MUTED, false);
        count.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, LauncherUi.dp(a, 35));
        cp.bottomMargin = LauncherUi.dp(a, 9);
        panel.addView(count, cp);
        Runnable refresh = () -> {
            String query = field.getText().toString();
            adapter.filter(query);
            boolean searching = !query.trim().isEmpty();
            browsing.setVisibility(searching ? View.GONE : View.VISIBLE);
            clear.setVisibility(searching ? View.VISIBLE : View.GONE);
            section.setText(searching ? "SEARCH RESULTS" : "ALL APPS");
            count.setText(adapter.matches() + (adapter.matches() == 1 ? " app" : " apps"));
        };
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh.run();
                list.setSelection(0);
            }
            public void afterTextChanged(Editable e) {}
        });

        GlassUi.show(a, overlay, panel);
        field.setText(initial);
        refresh.run();
    }

    private static void makeBrowse(LauncherActivity a, LinearLayout into) {
        if (a.catalog.all.isEmpty()) return;
        TextView heading = LauncherUi.text(a, "BROWSE", 12, MUTED, true);
        heading.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        into.addView(heading, new LinearLayout.LayoutParams(-1, LauncherUi.dp(a, 29)));

        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(LauncherUi.dp(a, 6), LauncherUi.dp(a, 9),
                LauncherUi.dp(a, 6), LauncherUi.dp(a, 6));
        grid.setBackground(GlassUi.card(a, 20));
        into.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        int visible = Math.min(8, a.catalog.all.size());
        for (int row = 0; row < (visible + 3) / 4; row++) {
            LinearLayout line = new LinearLayout(a);
            line.setGravity(Gravity.TOP);
            for (int col = 0; col < 4; col++) {
                int index = row * 4 + col;
                LinearLayout tile = new LinearLayout(a);
                tile.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setPadding(0, LauncherUi.dp(a, 5), 0, LauncherUi.dp(a, 6));
                line.addView(tile, new LinearLayout.LayoutParams(0, LauncherUi.dp(a, 99), 1f));
                if (index >= visible) continue;
                AppCatalog.Entry app = a.catalog.all.get(index);
                ImageView icon = LauncherUi.icon(a, app.key, 49);
                tile.addView(icon, new LinearLayout.LayoutParams(LauncherUi.dp(a, 50),
                        LauncherUi.dp(a, 50)));
                TextView label = LauncherUi.text(a, app.label, 11, TEXT, false);
                label.setMaxLines(2);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1,
                        LauncherUi.dp(a, 35));
                tlp.topMargin = LauncherUi.dp(a, 6);
                tile.addView(label, tlp);
                tile.setOnClickListener(v -> {
                    a.controller.closeShelf();
                    LauncherActions.launch(a, app.key);
                });
                tile.setOnLongClickListener(v -> {
                    AppOptions.open(a, app);
                    return true;
                });
            }
            grid.addView(line, new LinearLayout.LayoutParams(-1, LauncherUi.dp(a, 100)));
        }
    }

    private static void dismiss(LauncherActivity a) {
        InputMethodManager imm =
                (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && a.glassDialog != null)
            imm.hideSoftInputFromWindow(a.glassDialog.getWindow().getDecorView().getWindowToken(), 0);
        a.controller.closeShelf();
    }
}
