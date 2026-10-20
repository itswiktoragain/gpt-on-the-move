package com.eelauncber.launcher;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

class LauncherActivity extends Activity {
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
}
