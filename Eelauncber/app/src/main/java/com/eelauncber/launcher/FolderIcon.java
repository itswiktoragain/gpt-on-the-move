package com.eelauncber.launcher;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;

final class FolderIcon {
    static View create(LauncherActivity a,HomeState.Item item,int size){
        FrameLayout frame=new FrameLayout(a);
        frame.setBackground(LauncherUi.bg(a,0xB0E0E5F0,15));
        GridLayout grid=new GridLayout(a);
        grid.setColumnCount(2);grid.setRowCount(2);
        int pad=LauncherUi.dp(a,5);
        grid.setPadding(pad,pad,pad,pad);
        frame.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        int mini=(size-10)/2;
        for(int i=0;i<Math.min(4,item.apps.size());i++){
            ImageView image=LauncherUi.icon(a,item.apps.get(i),mini);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(i/2),
                    GridLayout.spec(i%2));
            lp.width=LauncherUi.dp(a,mini);lp.height=LauncherUi.dp(a,mini);
            grid.addView(image,lp);
        }
        return frame;
    }
}
