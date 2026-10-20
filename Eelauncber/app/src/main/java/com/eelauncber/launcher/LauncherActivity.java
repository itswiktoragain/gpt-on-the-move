package com.eelauncber.launcher;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

class LauncherActivity extends Activity {
    AppCatalog catalog=new AppCatalog();
    HomeState home=new HomeState();
    SharedPreferences prefs;
    FrameLayout root;
    HomePager pager;
    LinearLayout dock,dots;
    TextView clock,date,done;
    View shelf;
    boolean editing,scanning;
    long lastScan;
    LauncherController controller;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        controller=new LauncherController(this);
        controller.start();
    }
    @Override protected void onResume(){
        super.onResume();
        if(controller!=null)controller.resume();
    }
    @Override public void onBackPressed(){
        if(controller!=null&&controller.back())return;
        super.onBackPressed();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        if(controller!=null)controller.home();
    }
    @Override protected void onDestroy(){
        if(controller!=null)controller.stop();
        super.onDestroy();
    }
}
