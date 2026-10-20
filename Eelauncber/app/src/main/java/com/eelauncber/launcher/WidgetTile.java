package com.eelauncber.launcher;
import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
final class WidgetTile {
 static View create(LauncherActivity a,HomeState.WidgetPlacement w){
  FrameLayout outer=new FrameLayout(a);
  View widget=a.widgets.view(w);
  outer.addView(widget,new FrameLayout.LayoutParams(-1,-1));
  outer.setClipChildren(true);
  if(a.editing){
   TextView control=LauncherUi.text(a,"⋯  Widget",13,0xff26334a,true);
   control.setBackground(LauncherUi.bg(a,0xfff2f4f9,13));
   FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(LauncherUi.dp(a,100),LauncherUi.dp(a,34),Gravity.TOP|Gravity.RIGHT);
   outer.addView(control,lp);
   control.setOnClickListener(v->actions(a,w));
   outer.setOnLongClickListener(v->{actions(a,w);return true;});
  }
  return outer;
 }
 static void actions(LauncherActivity a,HomeState.WidgetPlacement w){
  new AlertDialog.Builder(a).setTitle("Home widget").setItems(new String[]{"Move to next page","Move to previous page","Increase width","Decrease width","Increase height","Decrease height","Remove widget"},(d,i)->{
   switch(i){
    case 0:w.page=Math.min(49,w.page+1);break;
    case 1:w.page=Math.max(0,w.page-1);break;
    case 2:w.spanX=Math.min(4,w.spanX+1);break;
    case 3:w.spanX=Math.max(1,w.spanX-1);break;
    case 4:w.spanY=Math.min(5,w.spanY+1);break;
    case 5:w.spanY=Math.max(1,w.spanY-1);break;
    case 6:a.widgets.remove(w);return;
   }
   a.home.save(a.prefs);HomeScreen.draw(a);
  }).show();
 }
}
