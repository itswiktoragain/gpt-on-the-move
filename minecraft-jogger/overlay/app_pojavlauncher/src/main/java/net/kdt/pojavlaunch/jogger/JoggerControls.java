package net.kdt.pojavlaunch.jogger;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlButton;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlJoystick;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A resizable, editable layout with the familiar left-move/right-look arrangement. */
public final class JoggerControls {
    public static final String FILE_NAME = "jogger-touch.json";
    private JoggerControls() {}

    public static String ensureDefault(Context context) throws IOException {
        if (Tools.currentDisplayMetrics == null) Tools.currentDisplayMetrics = context.getResources().getDisplayMetrics();
        File file = new File(Tools.CTRLMAP_PATH, FILE_NAME);
        if (!file.isFile()) {
            if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) throw new IOException("Cannot create control directory");
            CustomControls controls = new CustomControls();
            ControlJoystickData joystick = new ControlJoystickData();
            joystick.name = "Move";
            joystick.dynamicX = "px(22)";
            joystick.dynamicY = "${bottom} - px(28)";
            joystick.setWidth(Tools.dpToPx(144));
            joystick.setHeight(Tools.dpToPx(144));
            joystick.bgColor = 0x59171717;
            joystick.strokeColor = 0xB3FFFFFF;
            joystick.strokeWidth = 1.5f;
            joystick.opacity = .85f;
            joystick.displayInGame = true;
            joystick.displayInMenu = false;
            joystick.absolute = true;
            joystick.forwardLock = false;
            controls.mJoystickDataList.add(joystick);
            controls.mControlDataList.add(button("Jump", 32, "${right} - px(24)", "${bottom} - px(42)", 64, 64, false));
            controls.mControlDataList.add(button("Mine", ControlData.SPECIALBTN_MOUSEPRI, "${right} - px(105)", "${bottom} - px(95)", 58, 58, false));
            controls.mControlDataList.add(button("Use", ControlData.SPECIALBTN_MOUSESEC, "${right} - px(24)", "${bottom} - px(126)", 58, 58, false));
            controls.mControlDataList.add(button("Sneak", 340, "${right} - px(108)", "${bottom} - px(24)", 54, 54, false));
            controls.mControlDataList.add(button("Sprint", 341, "px(60)", "${bottom} - px(187)", 62, 42, false));
            controls.mControlDataList.add(button("Bag", 69, "${right} - px(16)", "px(14)", 54, 42, true));
            controls.mControlDataList.add(button("Pause", 256, "0.5 * ${screen_width} - px(92)", "px(14)", 54, 38, true));
            controls.mControlDataList.add(button("Chat", 84, "0.5 * ${screen_width} - px(28)", "px(14)", 54, 38, true));
            controls.mControlDataList.add(button("Type", ControlData.SPECIALBTN_KEYBOARD, "0.5 * ${screen_width} + px(36)", "px(14)", 54, 38, true));
            controls.mControlDataList.add(button("Menu", ControlData.SPECIALBTN_MENU, "px(16)", "px(14)", 54, 38, true));
            // Android sizes are stored in dp; dynamic positions use px(dp) at render time.
            // Fit the initial layout to compact landscape screens without changing a saved layout.
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            float shortDp = Math.min(metrics.widthPixels, metrics.heightPixels) / metrics.density;
            float fit = Math.min(1f, shortDp / 360f);
            if (fit < 1f) {
                List<ControlData> all = new ArrayList<>(controls.mControlDataList);
                all.addAll(controls.mJoystickDataList);
                for (ControlData control : all) {
                    control.setWidth(control.getWidth() * fit);
                    control.setHeight(control.getHeight() * fit);
                    control.dynamicX = scaleOffsets(control.dynamicX, fit);
                    control.dynamicY = scaleOffsets(control.dynamicY, fit);
                }
            }
            controls.save(file.getAbsolutePath());
        }
        if (!LauncherPreferences.DEFAULT_PREF.contains("defaultCtrl")) {
            LauncherPreferences.DEFAULT_PREF.edit().putString("defaultCtrl", file.getAbsolutePath()).apply();
            LauncherPreferences.PREF_DEFAULTCTRL_PATH = file.getAbsolutePath();
        }
        return FILE_NAME;
    }

    private static String scaleOffsets(String expression, float scale) {
        Matcher values = Pattern.compile("px\\((\\d+)\\)").matcher(expression);
        StringBuffer output = new StringBuffer();
        while (values.find()) values.appendReplacement(output,
                Matcher.quoteReplacement("px(" + Float.parseFloat(values.group(1)) * scale + ")"));
        values.appendTail(output);
        return output.toString();
    }

    private static ControlData button(String name, int key, String x, String y, int width, int height, boolean menu) {
        return new ControlData(name, new int[]{key}, x, y, width, height, false, .82f,
                0xA6171717, 0xA6FFFFFF, 1f, 30f, true, menu, false, false);
    }

    public static void releaseWidgets(View view) {
        if (view instanceof ControlButton) ((ControlButton)view).cancelTouchState();
        else if (view instanceof ControlJoystick) ((ControlJoystick)view).cancelTouchState();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) releaseWidgets(group.getChildAt(i));
        }
    }
}
