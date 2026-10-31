package net.kdt.pojavlaunch.jogger.mobile;

import java.util.Properties;

/**
 * Merge only screen, text widget and HUD mappings into a 1.5.2 game map.
 *
 * All of the 1.5.2 touch-ray, click, hit-classification and mining keys are
 * inherited unchanged. This is explicitly NOT the 1.5.3+ input overhaul.
 */
public final class UiMappingFilter {
    private UiMappingFilter() {}

    private static final String[] UI_CLASSES = {
        "class.mc", "class.hud", "class.graphics", "class.pose",
        "class.screen", "class.widget", "class.edit", "class.multiline",
        "class.chat", "class.chatComponent", "class.signEdit", "class.bookEdit"
    };
    private static final String[] UI_FIELDS = {
        "field.screen", "field.level", "field.player",
        "field.screen.width", "field.screen.height",
        "field.widget.x", "field.widget.y", "field.widget.width", "field.widget.height"
    };
    private static final String[] UI_PREFIXES = {
        "method.hud.", "desc.hud.", "method.graphics.", "desc.graphics.",
        "method.pose.", "desc.pose.", "method.gui.", "desc.gui.",
        "method.widget.", "desc.widget.", "method.edit.", "desc.edit.",
        "method.chat.", "desc.chat.", "method.mc.tick", "desc.mc.tick"
    };
    public static boolean uiKey(String key) {
        if (key == null) return false;
        for (String exact : UI_CLASSES) if (key.equals(exact)) return true;
        for (String exact : UI_FIELDS) if (key.equals(exact)) return true;
        for (String prefix : UI_PREFIXES) if (key.startsWith(prefix)) return true;
        return false;
    }
    public static Properties merge(Properties baseline, Properties known, String version) {
        Properties merged = new Properties();
        if (baseline != null) merged.putAll(baseline);
        if (!"1.21.4".equals(version) || known == null) return merged;
        for (String key : known.stringPropertyNames())
            if (uiKey(key)) merged.setProperty(key, known.getProperty(key));
        // Never import the renderer, ray, hitResult or click mappings:
        // their semantics must remain exactly as in Jogger Mobile 1.5.2.
        return merged;
    }
}
