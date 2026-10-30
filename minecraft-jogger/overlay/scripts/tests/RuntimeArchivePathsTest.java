package net.kdt.pojavlaunch.multirt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real filesystem cases, including a pre-existing malicious symlink and sibling-prefix escape. */
public final class RuntimeArchivePathsTest {
    private static int assertions;
    private interface Checked { void run() throws IOException; }
    private static void reject(Checked call) throws IOException {
        assertions++;
        try { call.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Unsafe archive path was accepted");
    }
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("jogger-archive-policy-");
        File root = Files.createDirectory(base.resolve("runtime")).toFile();
        File sibling = Files.createDirectory(base.resolve("runtime-elsewhere")).toFile();
        File lib = new File(root, "lib");
        lib.mkdir();
        RuntimeArchivePaths.entry(root, "lib/server/libjvm.so"); assertions++;
        RuntimeArchivePaths.entry(root, "./"); assertions++;
        RuntimeArchivePaths.symbolicLinkTarget(root, new File(lib, "libfreetype.so"), "libfreetype.so.6"); assertions++;
        reject(() -> RuntimeArchivePaths.entry(root, "../runtime-elsewhere/secret"));
        reject(() -> RuntimeArchivePaths.entry(root, "lib/../../secret"));
        reject(() -> RuntimeArchivePaths.entry(root, "/tmp/secret"));
        reject(() -> RuntimeArchivePaths.entry(root, ""));
        reject(() -> RuntimeArchivePaths.entry(root, "bad\0name"));
        reject(() -> RuntimeArchivePaths.symbolicLinkTarget(root, new File(lib, "escape"), "../../secret"));
        reject(() -> RuntimeArchivePaths.symbolicLinkTarget(root, new File(lib, "escape"), "/tmp/secret"));
        Files.createSymbolicLink(new File(root, "existing-link").toPath(), sibling.toPath());
        reject(() -> RuntimeArchivePaths.entry(root, "existing-link/secret"));
        Files.delete(new File(root, "existing-link").toPath());
        lib.delete(); root.delete(); sibling.delete(); Files.delete(base);
        System.out.println("PASS: " + assertions + " runtime archive path assertions");
    }
}
