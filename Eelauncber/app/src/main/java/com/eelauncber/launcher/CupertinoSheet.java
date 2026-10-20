package com.eelauncber.launcher;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Eelauncber's own Cupertino-style action sheets.
 * Uses only standard Android Views, never system AlertDialogs.
 * These are inspired by iOS visuals and are not Apple assets.
 */
final class CupertinoSheet {
    static final int TEXT = 0xFFF5F7FD;
    static final int SECONDARY = 0xFFA7B4C9;
    static final int ACCENT = 0xFF96B8FF;
    static final int DANGER = 0xFFFF858C;
    private static final int PANEL = 0xF020293A;
    private static final int BORDER = 0x40FFFFFF;

    static final class Action {
        final String glyph;
        final String label;
        final String detail;
        final boolean dangerous;
        final Runnable callback;
        private Action(String glyph, String label, String detail,
                       boolean dangerous, Runnable callback) {
            this.glyph = glyph;
            this.label = label;
            this.detail = detail;
            this.dangerous = dangerous;
            this.callback = callback;
        }
    }

    static Action action(String glyph, String label, Runnable callback) {
        return new Action(glyph, label, "", false, callback);
    }

    static Action action(String glyph, String label, String detail, Runnable callback) {
        return new Action(glyph, label, detail, false, callback);
    }

    static Action danger(String glyph, String label, Runnable callback) {
        return new Action(glyph, label, "", true, callback);
    }

    static void show(LauncherActivity a, String title, String subtitle, Action... actions) {
        if (a.isFinishing() || a.isDestroyed()) return;
        Dialog dialog = makeDialog(a);
        LinearLayout panel = makePanel(a);
        addTitle(a, panel, title, subtitle);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        addActions(a, list, dialog, actions);
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        int maxHeight = (int)(a.getResources().getDisplayMetrics().heightPixels * .60f);
        int desired = dp(a, 57) * actions.length;
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1,
                Math.min(desired, maxHeight));
        panel.addView(scroll, slp);
        present(a, dialog, panel);
    }

    static void searchable(LauncherActivity a, String title, Action... actions) {
        if (a.isFinishing() || a.isDestroyed()) return;
        Dialog dialog = makeDialog(a);
        LinearLayout panel = makePanel(a);
        addTitle(a, panel, title, "Search to find the item you want");
        EditText field = editField(a, "Search");
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, dp(a, 49));
        fieldParams.setMargins(dp(a, 18), dp(a, 4), dp(a, 18), dp(a, 11));
        panel.addView(field, fieldParams);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(dp(a, 385), (int)(a.getResources().getDisplayMetrics().heightPixels * .49f))));
        Runnable render = () -> {
            list.removeAllViews();
            String filter = field.getText().toString().trim().toLowerCase(Locale.ROOT);
            int count = 0;
            for (Action option : actions) {
                if (!option.label.toLowerCase(Locale.ROOT).contains(filter)) continue;
                if (count > 0) divider(a, list);
                list.addView(actionRow(a, dialog, option));
                count++;
            }
            if (count == 0) {
                TextView empty = label(a, "No matching items", 14, SECONDARY, false);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty, new LinearLayout.LayoutParams(-1, dp(a, 75)));
            }
        };
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        render.run();
        present(a, dialog, panel);
    }

    static void textPrompt(LauncherActivity a, String title, String current,
                           Consumer<String> onSave) {
        if (a.isFinishing() || a.isDestroyed()) return;
        Dialog dialog = makeDialog(a);
        LinearLayout panel = makePanel(a);
        addTitle(a, panel, title, "Choose a name");
        EditText field = editField(a, "Name");
        field.setText(current);
        field.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, dp(a, 54));
        fp.setMargins(dp(a, 18), dp(a, 8), dp(a, 18), dp(a, 15));
        panel.addView(field, fp);
        TextView save = label(a, "Save", 17, ACCENT, true);
        save.setGravity(Gravity.CENTER);
        save.setBackground(ripple(a, dp(a, 19)));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(a, 55));
        saveParams.setMargins(dp(a, 14), 0, dp(a, 14), dp(a, 8));
        panel.addView(save, saveParams);
        save.setOnClickListener(v -> {
            String entered = field.getText().toString().trim();
            dialog.dismiss();
            onSave.accept(entered);
        });
        present(a, dialog, panel);
        field.requestFocus();
        field.postDelayed(() -> {
            if (!dialog.isShowing()) return;
            InputMethodManager imm =
                    (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    static void confirm(LauncherActivity a, String title, String description,
                        String destructiveLabel, Runnable action) {
        show(a, title, description,
                danger("−", destructiveLabel, action));
    }

    private static Dialog makeDialog(LauncherActivity a) {
        Dialog dialog = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private static LinearLayout makePanel(LauncherActivity a) {
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(glass(a, PANEL, 27));
        panel.setElevation(dp(a, 24));
        panel.setClipToOutline(true);
        return panel;
    }

    private static void addTitle(LauncherActivity a, LinearLayout panel,
                                 String title, String subtitle) {
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(a, 21), dp(a, 22), dp(a, 21), dp(a, 15));
        TextView heading = label(a, title, 17, TEXT, true);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView caption = label(a, subtitle, 13, SECONDARY, false);
            caption.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(a, 7);
            header.addView(caption, cp);
        }
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
        divider(a, panel);
    }

    private static void addActions(LauncherActivity a, LinearLayout list,
                                   Dialog dialog, Action[] actions) {
        for (int i = 0; i < actions.length; i++) {
            if (i > 0) divider(a, list);
            list.addView(actionRow(a, dialog, actions[i]));
        }
    }

    private static View actionRow(LauncherActivity a, Dialog dialog, Action choice) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 18), 0, dp(a, 20), 0);
        row.setMinimumHeight(dp(a, 56));
        row.setBackground(ripple(a, dp(a, 17)));

        android.widget.ImageView icon = new android.widget.ImageView(a);
        icon.setImageResource(symbolFor(choice.label));
        icon.setColorFilter(choice.dangerous ? DANGER : TEXT);
        icon.setPadding(dp(a, 7), dp(a, 7), dp(a, 7), dp(a, 7));
        LinearLayout.LayoutParams ilp =
                new LinearLayout.LayoutParams(dp(a, 34), dp(a, 34));
        ilp.rightMargin = dp(a, 13);
        row.addView(icon, ilp);

        TextView name = label(a, choice.label, 16,
                choice.dangerous ? DANGER : TEXT, false);
        name.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(a, 55), 1f));

        if (choice.detail != null && !choice.detail.isEmpty()) {
            TextView note = label(a, choice.detail, 13, SECONDARY, false);
            note.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, dp(a, 55));
            np.leftMargin = dp(a, 7);
            row.addView(note, np);
        } else {
            TextView arrow = label(a, "›", 24, 0xFF7387A5, false);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(a, 16), dp(a, 55)));
        }

        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (choice.callback != null) choice.callback.run();
        });
        return row;
    }

    private static int symbolFor(String title) {
        String s = title.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("widget")) return R.drawable.f7_widget;
        if (s.contains("label") || s.contains("name") || s.contains("rename")) return R.drawable.f7_labels;
        if (s.contains("dock")) return R.drawable.f7_dock;
        if (s.contains("icon") || s.contains("size")) return R.drawable.f7_size;
        if (s.contains("wallpaper")) return R.drawable.f7_wallpaper;
        if (s.contains("hide") || s.contains("remove") || s.contains("ungroup")) return R.drawable.f7_hide;
        return R.drawable.f7_widget;
    }

    private static void present(LauncherActivity a, Dialog dialog, LinearLayout panel) {
        LinearLayout outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(a, 14), dp(a, 8), dp(a, 14), dp(a, 12));
        outer.addView(panel, new LinearLayout.LayoutParams(-1, -2));

        TextView cancel = label(a, "Cancel", 17, TEXT, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(glass(a, 0xF0273449, 21));
        cancel.setElevation(dp(a, 8));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(a, 56));
        cp.topMargin = dp(a, 9);
        outer.addView(cancel, cp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(outer);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.54f);
            window.setGravity(Gravity.BOTTOM);
            window.setNavigationBarColor(0xFF101725);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        if (window != null) window.setLayout(-1, -2);
        outer.setTranslationY(dp(a, 70));
        outer.setAlpha(0.5f);
        outer.animate().translationY(0).alpha(1f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }

    private static void divider(LauncherActivity a, LinearLayout holder) {
        View divider = new View(a);
        divider.setBackgroundColor(0x28FFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(a, 1));
        params.leftMargin = dp(a, 62);
        params.rightMargin = dp(a, 16);
        holder.addView(divider, params);
    }

    private static EditText editField(LauncherActivity a, String hint) {
        EditText field = new EditText(a);
        field.setTextSize(16f);
        field.setSingleLine(true);
        field.setTextColor(TEXT);
        field.setHintTextColor(SECONDARY);
        field.setHint(hint);
        field.setPadding(dp(a, 17), 0, dp(a, 17), 0);
        field.setBackground(glass(a, 0xDA3F4B61, 15));
        return field;
    }

    private static GradientDrawable glass(LauncherActivity a, int color, int radius) {
        GradientDrawable background = LauncherUi.bg(a, color, radius);
        background.setStroke(dp(a, 1), BORDER);
        return background;
    }

    private static RippleDrawable ripple(LauncherActivity a, int radius) {
        GradientDrawable mask = LauncherUi.bg(a, 0xFFFFFFFF,  radius / 
                Math.max(1, dp(a, 1)));
        return new RippleDrawable(ColorStateList.valueOf(0x35FFFFFF), null, mask);
    }

    private static TextView label(LauncherActivity a, String text, int size,
                                   int color, boolean bold) {
        return LauncherUi.text(a, text, size, color, bold);
    }

    private static int dp(LauncherActivity a, float v) { return LauncherUi.dp(a, v); }
}
