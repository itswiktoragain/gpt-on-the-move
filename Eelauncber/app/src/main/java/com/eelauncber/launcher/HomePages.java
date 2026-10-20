package com.eelauncber.launcher;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import java.util.ArrayList;

final class HomePages {
    static final int PAGE_SIZE=20;
    static void draw(LauncherActivity a){
        if(a.pager==null||a.dock==null)return;
        int lastPage=a.pager.getPage();
        int count=Math.max(1,(a.home.pages.size()+PAGE_SIZE-1)/PAGE_SIZE);
        ArrayList<View> pages=new ArrayList<>();
        for(int page=0;page<count;page++){
            HomeGrid grid=new HomeGrid(a);
            for(int slot=0;slot<PAGE_SIZE;slot++){
                int index=page*PAGE_SIZE+slot;
                HomeState.Item item=index<a.home.pages.size()?a.home.pages.get(index):null;
                grid.addView(AppTile.create(a,item,false,index));
            }
            pages.add(grid);
        }
        a.pager.replacePages(pages);
        if(lastPage>0)a.pager.post(()->a.pager.animateTo(Math.min(lastPage,a.pager.getCount()-1)));
        a.dock.removeAllViews();
        int color=a.prefs!=null&&a.prefs.getBoolean("dark_dock",false)?0xAA1C2843:0xBAE8EDF5;
        a.dock.setBackground(LauncherUi.bg(a,color,31));
        for(int slot=0;slot<4;slot++){
            HomeState.Item item=slot<a.home.dock.size()?a.home.dock.get(slot):null;
            View tile=AppTile.create(a,item,true,slot);
            a.dock.addView(tile,new LinearLayout.LayoutParams(0,-1,1f));
        }
        updateDots(a,0,count);
    }
    static void updateDots(LauncherActivity a,int page,int count){
        if(a.dots==null)return;
        a.dots.removeAllViews();
        for(int i=0;i<count;i++){
            View dot=new View(a);
            dot.setBackground(LauncherUi.bg(a,i==page?Color.WHITE:0x88FFFFFF,9));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                LauncherUi.dp(a,i==page?8:6),LauncherUi.dp(a,i==page?8:6));
            lp.leftMargin=LauncherUi.dp(a,4);lp.rightMargin=LauncherUi.dp(a,4);
            a.dots.addView(dot,lp);
        }
    }
}
