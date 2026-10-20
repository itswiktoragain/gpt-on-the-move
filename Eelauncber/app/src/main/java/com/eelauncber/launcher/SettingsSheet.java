package com.eelauncber.launcher;
import android.app.AlertDialog;

final class SettingsSheet {
    static void open(LauncherActivity a){
        String[] names={"Show icon labels","Dark dock","Large icons"};
        boolean[] enabled={
            a.prefs.getBoolean("labels",true),
            a.prefs.getBoolean("dark_dock",false),
            a.prefs.getInt("icon_size",58)>60
        };
        new AlertDialog.Builder(a).setTitle("Eelauncber")
            .setMultiChoiceItems(names,enabled,(dialog,index,value)->{
                if(index==0)a.prefs.edit().putBoolean("labels",value).apply();
                if(index==1)a.prefs.edit().putBoolean("dark_dock",value).apply();
                if(index==2)a.prefs.edit().putInt("icon_size",value?65:58).apply();
                HomeScreen.draw(a);
            })
            .setPositiveButton("Done",null).show();
    }
}
