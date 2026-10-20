package com.eelauncber.launcher;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
final class HomePages {
 static final int PAGE_SIZE=20;
 static void draw(LauncherActivity a){
  if(a.pager==null||a.dock==null)return;
  int last=a.pager.getPage();
  int pageCount=Math.max(1,(a.home.pages.size()+19)/20);
  for(HomeState.WidgetPlacement w:a.home.widgets)pageCount=Math.max(pageCount,Math.min(50,w.page+1));
  ArrayList<View> views=new ArrayList<>();
  int shortcut=0;
  for(int page=0;page<pageCount;page++){
   HomeGrid grid=new HomeGrid(a);
   boolean[] busy=new boolean[20];
   for(HomeState.WidgetPlacement w:a.home.widgets)if(w.page==page){
    int target=locate(busy,w.cell,w.spanX,w.spanY);
    if(target<0)continue;
    w.cell=target;
    occupy(busy,target,w.spanX,w.spanY);
    grid.addView(WidgetTile.create(a,w),new HomeGrid.Params(target,w.spanX,w.spanY));
   }
   for(int cell=0;cell<20&&shortcut<a.home.pages.size();cell++)if(!busy[cell]){
    grid.addView(AppTile.create(a,a.home.pages.get(shortcut),false,shortcut),new HomeGrid.Params(cell,1,1));
    shortcut++;
   }
   views.add(grid);
  }
  while(shortcut<a.home.pages.size()){
   HomeGrid grid=new HomeGrid(a);
   for(int cell=0;cell<20&&shortcut<a.home.pages.size();cell++,shortcut++)
    grid.addView(AppTile.create(a,a.home.pages.get(shortcut),false,shortcut),new HomeGrid.Params(cell,1,1));
   views.add(grid);
  }
  a.pager.replacePages(views);
  if(last>0)a.pager.post(()->a.pager.animateTo(Math.min(last,a.pager.getCount()-1)));
  a.dock.removeAllViews();
  int color=a.prefs.getBoolean("dark_dock",false)?0xAA1C2843:0xBAE8EDF5;
  a.dock.setBackground(LauncherUi.bg(a,color,31));
  for(int i=0;i<4;i++){
   HomeState.Item item=i<a.home.dock.size()?a.home.dock.get(i):null;
   View tile=AppTile.create(a,item,true,i);
   a.dock.addView(tile,new LinearLayout.LayoutParams(0,-1,1));
  }
  updateDots(a,0,views.size());
 }
 static int locate(boolean[] busy,int preferred,int sx,int sy){
  if(free(busy,preferred,sx,sy))return preferred;
  for(int c=0;c<20;c++)if(free(busy,c,sx,sy))return c;
  return -1;
 }
 static boolean free(boolean[] busy,int cell,int sx,int sy){
  if(cell<0||cell>=20||cell%4+sx>4||cell/4+sy>5)return false;
  for(int y=0;y<sy;y++)for(int x=0;x<sx;x++)if(busy[cell+y*4+x])return false;
  return true;
 }
 static void occupy(boolean[] busy,int cell,int sx,int sy){
  for(int y=0;y<sy;y++)for(int x=0;x<sx;x++)busy[cell+y*4+x]=true;
 }
 static void updateDots(LauncherActivity a,int page,int count){
  if(a.dots==null)return;a.dots.removeAllViews();
  for(int i=0;i<count;i++){
   View dot=new View(a);
   dot.setBackground(LauncherUi.bg(a,i==page?Color.WHITE:0x88FFFFFF,8));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LauncherUi.dp(a,7),LauncherUi.dp(a,7));
   lp.setMargins(LauncherUi.dp(a,4),0,LauncherUi.dp(a,4),0);a.dots.addView(dot,lp);
  }
 }
}
