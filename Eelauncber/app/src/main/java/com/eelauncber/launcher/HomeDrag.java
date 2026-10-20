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
        ref.used=true;
        if(ref.item==target){a.root.post(()->HomeScreen.draw(a));return;}
        ArrayList<HomeState.Item> from=ref.dock?a.home.dock:a.home.pages;
        if(!from.remove(ref.item))return;
        ArrayList<HomeState.Item> into=dock?a.home.dock:a.home.pages;
        if(target!=null&&into.contains(target)&&ref.item.apps.size()==1){
            if(target.apps.size()==1){
                HomeState.Item folder=new HomeState.Item();
                folder.apps.addAll(target.apps);
                folder.apps.addAll(ref.item.apps);
                folder.name="Folder";
                into.set(into.indexOf(target),folder);
            } else {
                target.apps.add(ref.item.apps.get(0));
            }
        }else{
            int destination=target==null?Math.min(index,into.size()):into.indexOf(target);
            if(destination<0)destination=into.size();
            into.add(destination,ref.item);
        }
        while(a.home.dock.size()>4){
            a.home.pages.add(0,a.home.dock.remove(a.home.dock.size()-1));
        }
        a.home.save(a.prefs);
        a.root.post(()->HomeScreen.draw(a));
    }
    static void end(LauncherActivity a,Ref ref){
        if(ref.ended)return;
        ref.ended=true;
        if(!ref.used)a.root.post(()->HomeScreen.draw(a));
    }
}
