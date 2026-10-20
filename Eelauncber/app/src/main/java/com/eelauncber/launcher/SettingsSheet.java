package com.eelauncber.launcher;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.Intent;
final class SettingsSheet {
 static void open(LauncherActivity a){
  String[] names={"Add system widget","Show icon labels","Dark dock","Larger icons","Choose wallpaper","Hide an app"};
  new AlertDialog.Builder(a).setTitle("Customize Home Screen")
   .setItems(names,(d,i)->{
    if(i==0){a.widgets.pick();return;}
    if(i==4){
     try{a.startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));}
     catch(Exception ex){a.startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));}
     return;
    }
    if(i==5){
     String[] labels=new String[a.catalog.all.size()];
     for(int k=0;k<labels.length;k++)labels[k]=a.catalog.all.get(k).label;
     new AlertDialog.Builder(a).setTitle("Hide from Home Screen").setItems(labels,(dialog,k)->{
      String key=a.catalog.all.get(k).key;
      a.home.hidden.add(key);
      a.home.pages.removeIf(it->it.apps.contains(key));
      LauncherActions.save(a);
     }).show();return;
    }
    String key=i==1?"labels":i==2?"dark_dock":"icon_size";
    if(i==3)a.prefs.edit().putInt(key,a.prefs.getInt(key,58)>60?58:65).apply();
    else a.prefs.edit().putBoolean(key,!a.prefs.getBoolean(key,i==1)).apply();
    HomeScreen.draw(a);
   }).setPositiveButton("Done",null).show();
 }
}
