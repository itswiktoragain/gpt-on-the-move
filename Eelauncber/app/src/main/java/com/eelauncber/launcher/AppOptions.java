package com.eelauncber.launcher;
import android.app.AlertDialog;

final class AppOptions {
    static void open(LauncherActivity a,AppCatalog.Entry app){
        new AlertDialog.Builder(a).setTitle(app.label)
            .setItems(new String[]{"Add to Home Screen","App info"},(dialog,index)->{
                if(index==0)LauncherActions.add(a,app.key);
                else LauncherActions.appInfo(a,app.key);
            }).show();
    }
}
