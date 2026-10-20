package com.eelauncber.launcher;
import android.content.ClipData;
import android.view.HapticFeedbackConstants;
import android.view.View;
import java.util.ArrayList;

final class HomeDrag {
    static final class Ref {
        final HomeState.Item item;
        final boolean dock;
        final int index;
        boolean used,ended;
        Ref(HomeState.Item item,boolean dock,int index){
            this.item=item;this.dock=dock;this.index=index;
        }
    }
    static void start(LauncherActivity a,View tile,HomeState.Item item,boolean dock,int index){
        a.editing=true;
        if(a.done!=null)a.done.setVisibility(View.VISIBLE);
        tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Ref ref=new Ref(item,dock,index);
        tile.startDragAndDrop(ClipData.newPlainText("Eelauncber","shortcut"),
                new View.DragShadowBuilder(tile),ref,0);
    }
    static void drop(LauncherActivity a,Ref ref,boolean dock,int index,HomeState.Item target){
        if(ref.used)return;
        if(ref.item==target){ref.used=true;return;}
        int page=index/20,cell=index%20;
        if(!dock){
            for(HomeState.WidgetPlacement w:a.home.widgets)if(w.page==page &&
                cell%4>=w.cell%4 && cell%4<w.cell%4+w.spanX &&
                cell/4>=w.cell/4 && cell/4<w.cell/4+w.spanY)return;
        }
        ArrayList<HomeState.Item> from=ref.dock?a.home.dock:a.home.pages;
        if(!from.contains(ref.item))return;
        ArrayList<HomeState.Item> into=dock?a.home.dock:a.home.pages;
        if(dock && target==null && a.home.dock.size()>=4 && !ref.dock)return;
        ref.used=true;
        from.remove(ref.item);
        if(target!=null && into.contains(target) && ref.item.apps.size()==1){
            if(target.apps.size()==1){
                HomeState.Item folder=new HomeState.Item();
                folder.apps.addAll(target.apps);
                folder.apps.addAll(ref.item.apps);
                folder.name="Folder";
                folder.page=target.page;folder.cell=target.cell;
                into.set(into.indexOf(target),folder);
            }else target.apps.add(ref.item.apps.get(0));
        }else if(dock){
            into.add(Math.max(0,Math.min(index,into.size())),ref.item);
            ref.item.page=-1;ref.item.cell=-1;
        }else{
            ref.item.page=page;ref.item.cell=cell;
            into.add(ref.item);
        }
        while(a.home.dock.size()>4){
            HomeState.Item extra=a.home.dock.remove(a.home.dock.size()-1);
            extra.page=-1;extra.cell=-1;a.home.pages.add(extra);
        }
        a.home.normalize();
        a.home.save(a.prefs);
        a.root.post(()->HomeScreen.draw(a));
    }
    static void end(LauncherActivity a,Ref ref){
        if(ref.ended)return;
        ref.ended=true;
        if(!ref.used)a.root.post(()->HomeScreen.draw(a));
    }
}
