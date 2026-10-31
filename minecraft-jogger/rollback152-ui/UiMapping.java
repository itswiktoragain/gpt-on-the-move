package net.kdt.pojavlaunch.jogger.mobile;

import android.content.Context;
import android.util.Log;
import net.kdt.pojavlaunch.jogger.classic.GameAssets;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * UI-only supplement to the original Jogger 1.5.2 GameAssets mapping loader.
 * The original touch mappings are read first and are never modified.
 */
public final class UiMapping {
    private UiMapping() {}
    public static File mappings(Context context) {
        final File baseline = GameAssets.mappings(context);
        final File versionJson = GameAssets.versionJson();
        if (versionJson == null) return baseline;
        final String id = versionJson.getName().replaceFirst("\\.json$", "");
        if (!"1.21.4".equals(id)) return baseline;
        try {
            Properties old = new Properties(), known = new Properties();
            if (baseline != null && baseline.isFile())
                try (InputStream in = new FileInputStream(baseline)) { old.load(in); }
            try (InputStream in = context.getAssets().open("jogger-ui-only-1.21.4.properties")) {
                known.load(in);
            }
            Properties merged = UiMappingFilter.merge(old, known, id);
            File result = new File(context.getFilesDir(),
                "jogger/" + versionJson.getName() + ".v152-ui-only-v1.properties");
            File parent = result.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("UI mapping directory");
            File tmp = new File(result.getPath() + ".part");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                merged.store(out, "Jogger 1.5.2 UI-only overlay; original touch mapping retained");
                out.getFD().sync();
            }
            Files.move(tmp.toPath(), result.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Log.i("Jogger", "1.5.2 UI-only mapping active: world search and status HUD");
            return result;
        } catch (Exception error) {
            Log.w("Jogger", "UI supplement unavailable; using original 1.5.2 mapping", error);
            return baseline;
        }
    }
}
