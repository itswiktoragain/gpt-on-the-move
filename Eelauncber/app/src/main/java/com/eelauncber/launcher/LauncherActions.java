package com.eelauncber.launcher;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

final class LauncherActions {
    static void launch(LauncherActivity a,String key){
        ComponentName component=ComponentName.unflattenFromString(key);
        if(component==null)return;
        try{
            Intent intent=new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(component);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            a.startActivity(intent);
        }catch(Exception ignored){
            CupertinoNotice.show(a,"Could not open this app");
        }
    }
    static void open(LauncherActivity a,HomeState.Item item){
        if(item==null)return;
        if(item.isFolder())FolderSheet.open(a,item);
        else if(!item.apps.isEmpty())launch(a,item.apps.get(0));
    }
    static void add(LauncherActivity a,String key){
        if(!a.home.includes(key)){
            a.home.pages.add(new HomeState.Item(key));
            save(a);
        }
        CupertinoNotice.show(a,"Added to Home Screen");
    }
    static void remove(LauncherActivity a,HomeState.Item item,boolean dock){
        if(dock)a.home.dock.remove(item);else a.home.pages.remove(item);
        save(a);
    }
    static void save(LauncherActivity a){
        a.home.save(a.prefs);
        HomeScreen.draw(a);
    }
    static void appInfo(LauncherActivity a,String key){
        ComponentName c=ComponentName.unflattenFromString(key);
        if(c==null)return;
        Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:"+c.getPackageName()));
        a.startActivity(i);
    }
}
