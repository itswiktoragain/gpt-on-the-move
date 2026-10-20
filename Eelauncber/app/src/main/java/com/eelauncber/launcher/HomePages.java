package com.eelauncber.launcher;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
final class HomePages {
 static final int PAGE_SIZE=20;
 static void draw(LauncherActivity a){
  if(a.pager==null||a.dock==null)return;
  int previous=a.pager.getPage();
  a.home.normalize();
  int last=0;
  for(HomeState.Item item:a.home.pages)last=Math.max(last,item.page);
  for(HomeState.WidgetPlacement w:a.home.widgets)last=Math.max(last,w.page);
  int count=Math.min(50,Math.max(1,last+1+(a.editing?1:0)));
  ArrayList<View> views=new ArrayList<>();
  for(int page=0;page<count;page++){
   HomeGrid grid=new HomeGrid(a);
   final int pageNumber=page;
   grid.setOnLongClickListener(v->{a.controller.setEditing(true);return true;});
   grid.setOnDragListener((v,event)->{
    if(!(event.getLocalState() instanceof WidgetMove.Ref))return false;
    if(event.getAction()==android.view.DragEvent.ACTION_DROP){
     int hovered=WidgetGridFit.hoveredCell(event.getX(),event.getY(),grid.getWidth(),grid.getHeight());
     WidgetMove.drop(a,(WidgetMove.Ref)event.getLocalState(),pageNumber,hovered);
    }
    if(event.getAction()==android.view.DragEvent.ACTION_DRAG_ENDED)
     WidgetMove.end(a,(WidgetMove.Ref)event.getLocalState());
    return true;
   });
   boolean[] busy=new boolean[20];
   for(HomeState.WidgetPlacement w:a.home.widgets)if(w.page==page){
    int spot=locate(busy,w.cell,w.spanX,w.spanY);
    if(spot<0)continue;
    w.cell=spot;occupy(busy,spot,w.spanX,w.spanY);
    grid.addView(WidgetTile.create(a,w),new HomeGrid.Params(spot,w.spanX,w.spanY));
   }
   HomeState.Item[] slots=new HomeState.Item[20];
   for(HomeState.Item item:a.home.pages)if(item.page==page&&item.cell>=0&&item.cell<20)
    slots[item.cell]=item;
   for(int cell=0;cell<20;cell++)if(!busy[cell]){
    HomeState.Item item=slots[cell];
    grid.addView(AppTile.create(a,item,false,page*20+cell),new HomeGrid.Params(cell,1,1));
   }
   views.add(grid);
  }
  a.pager.replacePages(views);
  if(previous>0)a.pager.post(()->a.pager.animateTo(Math.min(previous,a.pager.getCount()-1)));
  a.dock.removeAllViews();
  int color=a.prefs.getBoolean("dark_dock",false)?0xAA1C2843:0xBAE8EDF5;
  a.dock.setBackground(LauncherUi.bg(a,color,31));
  for(int i=0;i<4;i++){
   HomeState.Item item=i<a.home.dock.size()?a.home.dock.get(i):null;
   a.dock.addView(AppTile.create(a,item,true,i),new LinearLayout.LayoutParams(0,-1,1));
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
