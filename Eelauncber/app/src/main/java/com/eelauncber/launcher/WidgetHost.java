package com.eelauncber.launcher;
import android.appwidget.*;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
final class WidgetHost {
 static final int HOST_ID=9826, PICK=812, CONFIG=813;
 final LauncherActivity a;
 final AppWidgetHost host;
 final AppWidgetManager manager;
 int pending=-1;
 WidgetHost(LauncherActivity a){this.a=a;host=new AppWidgetHost(a,HOST_ID);manager=AppWidgetManager.getInstance(a);}
 void start(){try{host.startListening();}catch(Exception ignored){}}
 void stop(){try{host.stopListening();}catch(Exception ignored){}}
 void pick(){
  int id=host.allocateAppWidgetId();pending=id;
  Intent i=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
  i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
  try{a.startActivityForResult(i,PICK);}catch(Exception e){host.deleteAppWidgetId(id);pending=-1;CupertinoNotice.show(a,"Widget picker unavailable");}
 }
 boolean result(int request,int result,Intent data){
  if(request!=PICK&&request!=CONFIG)return false;
  int id=data==null?pending:data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pending);
  if(result!=Activity.RESULT_OK||id<0){if(pending>=0)host.deleteAppWidgetId(pending);pending=-1;return true;}
  AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
  if(info==null){host.deleteAppWidgetId(id);pending=-1;return true;}
  if(request==PICK&&info.configure!=null){
   Intent config=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
   config.setComponent(info.configure);config.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
   try{a.startActivityForResult(config,CONFIG);return true;}catch(Exception e){host.deleteAppWidgetId(id);pending=-1;return true;}
  }
  pending=-1;
  int sx=Math.max(2,Math.min(4,(info.minWidth+69)/70));
  int sy=Math.max(1,Math.min(5,(info.minHeight+85)/86));
  a.home.widgets.add(new HomeState.WidgetPlacement(id,info.provider.flattenToString(),Math.max(0,a.pager.getPage()),0,sx,sy));
  a.home.save(a.prefs);HomeScreen.draw(a);return true;
 }
 View view(HomeState.WidgetPlacement p){
  AppWidgetProviderInfo info=manager.getAppWidgetInfo(p.id);
  if(info==null)return placeholder(p);
  try{
   AppWidgetHostView v=host.createView(a,p.id,info);
   v.setAppWidget(p.id,info);
   return v;
  }catch(Exception ex){return placeholder(p);}
 }
 View placeholder(HomeState.WidgetPlacement p){
  TextView t=LauncherUi.text(a,"Widget unavailable",13,0xffffffff,false);
  t.setBackground(LauncherUi.bg(a,0x884F5970,17));return t;
 }
 void remove(HomeState.WidgetPlacement p){
  a.home.widgets.remove(p);host.deleteAppWidgetId(p.id);
  a.home.save(a.prefs);HomeScreen.draw(a);
 }
}
