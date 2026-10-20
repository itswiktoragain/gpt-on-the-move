package com.eelauncber.launcher;
import android.view.DragEvent;
import android.view.View;

/** Position-preserving move targets for real and custom widgets. */
final class WidgetMove {
 static final class Ref {
  final HomeState.WidgetPlacement widget;
  boolean used;
  Ref(HomeState.WidgetPlacement widget){this.widget=widget;}
 }
 static boolean drop(LauncherActivity a,Ref ref,int page,int cell) {
  if(ref.used)return false;
  HomeState.WidgetPlacement widget=ref.widget;
  if(cell<0||cell>=20||page<0||page>=50)return false;
  boolean[] occupied=new boolean[20];
  for(HomeState.WidgetPlacement other:a.home.widgets) {
   if(other==widget||other.page!=page)continue;
   int place=HomePages.locate(occupied,other.cell,other.spanX,other.spanY);
   if(place>=0)HomePages.occupy(occupied,place,other.spanX,other.spanY);
  }
  for(HomeState.Item app:a.home.pages)
   if(app.page==page&&app.cell>=0&&app.cell<20)occupied[app.cell]=true;
  if(!HomePages.free(occupied,cell,widget.spanX,widget.spanY)) {
   CupertinoNotice.show(a,"No space for widget here");
   return false;
  }
  ref.used=true;
  widget.page=page;widget.cell=cell;
  a.home.save(a.prefs);
  a.root.post(()->HomeScreen.draw(a));
  return true;
 }
 static void end(LauncherActivity a,Ref ref) {
  if(!ref.used)a.root.post(()->HomeScreen.draw(a));
 }
}
