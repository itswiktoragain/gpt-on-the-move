package net.kdt.pojavlaunch.jogger.mobile;

import java.util.Properties;

/** Connect the verified 1.21.4 touch-ray and block-hit mappings to Jogger's
 * restored 1.5.2 gesture router; never guess names for unsupported versions. */
public final class GameTouchMappings {
    private GameTouchMappings() {}

    public static final String[] REQUIRED = {
        "class.renderer", "class.entity", "class.vec",
        "class.hit", "class.blockHit", "class.entityHit",
        "field.renderer", "field.hitResult",
        "field.x", "field.y", "field.z",
        "method.pick", "method.view", "method.fov",
        "method.hit.getType", "field.blockHit.miss"
    };

    public static Properties complete(Properties old, Properties verified, String version) {
        Properties result = new Properties();
        if (old != null) result.putAll(old);
        if (!"1.21.4".equals(version) || verified == null) return result;
        for (String key : REQUIRED) {
            String name = verified.getProperty(key);
            if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Missing verified " + key);
            result.setProperty(key, name);
        }
        return result;
    }

    public static boolean ready(Properties p) {
        if (p == null) return false;
        for (String key : REQUIRED) if (p.getProperty(key) == null) return false;
        return true;
    }
}
