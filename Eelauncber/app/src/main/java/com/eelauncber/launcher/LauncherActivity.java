package com.eelauncber.launcher;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
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
 WidgetHost widgets;
 @Override public void onCreate(Bundle b){super.onCreate(b);controller=new LauncherController(this);controller.start();}
 @Override protected void onResume(){super.onResume();if(controller!=null)controller.resume();}
 @Override protected void onStart(){super.onStart();if(widgets!=null)widgets.start();}
 @Override protected void onStop(){if(widgets!=null)widgets.stop();super.onStop();}
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(widgets!=null)widgets.result(req,result,data);
 }
 @Override public void onBackPressed(){if(controller!=null&&controller.back())return;super.onBackPressed();}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);if(controller!=null)controller.home();}
 @Override protected void onDestroy(){if(controller!=null)controller.stop();super.onDestroy();}
}
