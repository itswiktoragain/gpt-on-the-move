package com.eelauncber.launcher;
import android.os.BatteryManager;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
/** Native Eelauncber widgets using real device clock, date and battery data. */
final class EelaWidgets {
 static final String PREFIX="eela:";
 static final String[] TYPES={"Clock","Calendar","Battery","Date"};
 static void pick(LauncherActivity a){
  CupertinoSheet.Action[] options=new CupertinoSheet.Action[TYPES.length];
  for(int i=0;i<TYPES.length;i++){
   final int index=i;
   options[i]=CupertinoSheet.action("",""+TYPES[i]+" Widget",()->add(a,TYPES[index]));
  }
  CupertinoSheet.show(a,"Eelauncber Widgets","Choose your widget",options);
 }
 static void add(LauncherActivity a,String type){
  int id=10000000 + a.prefs.getInt("custom_widget_id",0);
  a.prefs.edit().putInt("custom_widget_id",id-9999999).apply();
  int page=a.pager==null?0:a.pager.getPage();
  a.home.widgets.add(new HomeState.WidgetPlacement(id,PREFIX+type,page,0,2,2));
  a.home.save(a.prefs);HomeScreen.draw(a);
 }
 static boolean custom(HomeState.WidgetPlacement w){return w.provider.startsWith(PREFIX);}
 static View view(LauncherActivity a,HomeState.WidgetPlacement item){
  LinearLayout body=new LinearLayout(a);
  body.setOrientation(LinearLayout.VERTICAL);
  body.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
  body.setPadding(LauncherUi.dp(a,17),LauncherUi.dp(a,8),LauncherUi.dp(a,10),LauncherUi.dp(a,8));
  body.setBackground(GlassUi.card(a,25));
  String type=item.provider.substring(PREFIX.length());
  TextView top=LauncherUi.text(a,type.toUpperCase(Locale.ROOT),11,0xFFAFDAFF,true);
  top.setGravity(Gravity.LEFT);body.addView(top,new LinearLayout.LayoutParams(-1,-2));
  TextView primary=LauncherUi.text(a,"",type.equals("Clock")?37:29,Color.WHITE,true);
  primary.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  LinearLayout.LayoutParams mainp=new LinearLayout.LayoutParams(-1,0,1);
  body.addView(primary,mainp);
  TextView bottom=LauncherUi.text(a,"",13,0xFFD2E2FA,false);
  bottom.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
  body.addView(bottom,new LinearLayout.LayoutParams(-1,-2));
  Runnable refresh=()->{
   Date now=new Date();
   if(type.equals("Clock")){
    primary.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now));
    bottom.setText(new SimpleDateFormat("EEEE",Locale.getDefault()).format(now));
   } else if(type.equals("Calendar")){
    primary.setText(new SimpleDateFormat("dd",Locale.getDefault()).format(now));
    bottom.setText(new SimpleDateFormat("EEEE, MMMM",Locale.getDefault()).format(now));
   } else if(type.equals("Battery")){
    BatteryManager battery=(BatteryManager)a.getSystemService(Context.BATTERY_SERVICE);
    int pct=battery==null?-1:battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    primary.setText(pct<0?"—":pct+"%");
    bottom.setText("Device battery");
   } else {
    primary.setText(new SimpleDateFormat("EEE, d",Locale.getDefault()).format(now));
    bottom.setText(new SimpleDateFormat("MMMM yyyy",Locale.getDefault()).format(now));
   }
  };
  refresh.run();body.setTag(refresh);
  return body;
 }
 static void refresh(View v){
  if(v==null)return;
  Object callback=v.getTag();
  if(callback instanceof Runnable)((Runnable)callback).run();
  if(v instanceof android.view.ViewGroup){
   android.view.ViewGroup group=(android.view.ViewGroup)v;
   for(int i=0;i<group.getChildCount();i++)refresh(group.getChildAt(i));
  }
 }
}
