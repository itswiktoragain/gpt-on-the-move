package net.kdt.pojavlaunch.multirt;

import java.io.File;
import java.io.IOException;

/** Keep all runtime archive files and link targets inside the selected runtime directory. */
public final class RuntimeArchivePaths {
    private RuntimeArchivePaths() {}

    public static File entry(File root, String name) throws IOException {
        requireRelative(name);
        return inside(root, new File(root, name));
    }

    public static File symbolicLinkTarget(File root, File link, String target) throws IOException {
        requireRelative(target);
        return inside(root, new File(link.getParentFile(), target));
    }

    private static void requireRelative(String name) throws IOException {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0 || new File(name).isAbsolute()) {
            throw new IOException("A Java runtime archive contains an invalid absolute or empty path.");
        }
    }

    private static File inside(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String path = file.getCanonicalPath();
        if (!path.equals(rootPath) && !path.startsWith(rootPath + File.separator)) {
            throw new IOException("A Java runtime archive tried to write outside its runtime directory.");
        }
        // Preserve the named location when creating links; canonicalization above is the safety check.
        return file.getAbsoluteFile();
    }
}
