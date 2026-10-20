package com.eelauncber.launcher;
import java.util.ArrayList;
/** Let users long-press any vacant grid square to place or move an app there. */
final class GridPlacement {
 static void choose(LauncherActivity a,int index){
  int page=index/20,cell=index%20;
  for(HomeState.WidgetPlacement w:a.home.widgets)if(w.page==page &&
     cell%4>=w.cell%4&&cell%4<w.cell%4+w.spanX &&
     cell/4>=w.cell/4&&cell/4<w.cell/4+w.spanY)return;
  ArrayList<CupertinoSheet.Action> actions=new ArrayList<>();
  for(AppCatalog.Entry entry:a.catalog.all){
   actions.add(CupertinoSheet.action("",entry.label,()->place(a,entry.key,page,cell)));
  }
  CupertinoSheet.searchable(a,"Place App",
      actions.toArray(new CupertinoSheet.Action[0]));
 }
 static void place(LauncherActivity a,String key,int page,int cell){
  HomeState.Item selected=null;
  for(HomeState.Item item:a.home.pages)if(item.apps.size()==1&&item.apps.contains(key)){
   selected=item;break;
  }
  if(selected==null){
   for(HomeState.Item item:a.home.dock)if(item.apps.size()==1&&item.apps.contains(key)){
    selected=item;a.home.dock.remove(item);break;
   }
  }
  if(selected==null){
   if(a.home.includes(key)){
    CupertinoNotice.show(a,"App is in a folder");return;
   }
   selected=new HomeState.Item(key);
  }
  HomeState.Item occupant=null;
  for(HomeState.Item item:a.home.pages)if(item.page==page&&item.cell==cell&&item!=selected){
   occupant=item;break;
  }
  if(occupant!=null){
   CupertinoNotice.show(a,"Choose an empty cell");return;
  }
  selected.page=page;selected.cell=cell;
  if(!a.home.pages.contains(selected))a.home.pages.add(selected);
  a.home.normalize();
  a.home.save(a.prefs);
  HomeScreen.draw(a);
 }
}
