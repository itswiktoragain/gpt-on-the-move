package com.eelauncber.launcher;

final class AppOptions {
    static void open(LauncherActivity a, AppCatalog.Entry app) {
        boolean added = a.home.includes(app.key);
        CupertinoSheet.show(a, app.label, "App shortcuts",
                CupertinoSheet.action("＋", added ? "Already on Home Screen" : "Add to Home Screen",
                        () -> {
                            if (added) CupertinoNotice.show(a, "Already on Home Screen");
                            else LauncherActions.add(a, app.key);
                        }),
                CupertinoSheet.action("ⓘ", "App information",
                        () -> LauncherActions.appInfo(a, app.key)));
    }
}
