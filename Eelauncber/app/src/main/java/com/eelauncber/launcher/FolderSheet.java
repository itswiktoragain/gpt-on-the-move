package com.eelauncber.launcher;

import java.util.ArrayList;

final class FolderSheet {
    static void open(LauncherActivity a, HomeState.Item folder) {
        ArrayList<CupertinoSheet.Action> actions = new ArrayList<>();
        for (String key : folder.apps) {
            AppCatalog.Entry entry = a.catalog.get(key);
            String name = entry == null ? "App" : entry.label;
            actions.add(CupertinoSheet.action("↗", name,
                    () -> LauncherActions.launch(a, key)));
        }
        actions.add(CupertinoSheet.action("✎", "Rename folder",
                () -> rename(a, folder)));
        actions.add(CupertinoSheet.danger("−", "Ungroup apps",
                () -> CupertinoSheet.confirm(a, "Ungroup Folder",
                        "All apps will be moved to your home screen.",
                        "Ungroup", () -> ungroup(a, folder))));
        CupertinoSheet.show(a, folder.name.isEmpty() ? "Folder" : folder.name,
                "Tap an app to open it", actions.toArray(new CupertinoSheet.Action[0]));
    }

    private static void ungroup(LauncherActivity a, HomeState.Item folder) {
        a.home.pages.remove(folder);
        a.home.dock.remove(folder);
        for (String key : folder.apps) {
            if (!a.home.includes(key)) a.home.pages.add(new HomeState.Item(key));
        }
        LauncherActions.save(a);
        CupertinoNotice.show(a, "Folder ungrouped");
    }

    static void rename(LauncherActivity a, HomeState.Item folder) {
        CupertinoSheet.textPrompt(a, "Rename Folder", folder.name, text -> {
            folder.name = text.isEmpty() ? "Folder" : text;
            LauncherActions.save(a);
        });
    }
}
