package com.eelauncber.launcher;
import android.graphics.Color;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

final class HomeControls {
    static void add(LauncherActivity a){
        ImageView gear=new ImageView(a);
        gear.setImageResource(R.drawable.f7_gear);
        gear.setColorFilter(Color.WHITE);
        gear.setPadding(LauncherUi.dp(a,11),LauncherUi.dp(a,11),
                LauncherUi.dp(a,11),LauncherUi.dp(a,11));
        gear.setBackground(GlassUi.chip(a,21));
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(LauncherUi.dp(a,43),LauncherUi.dp(a,43),
                Gravity.TOP|Gravity.RIGHT);
        gp.topMargin=LauncherUi.dp(a,52);
        gp.rightMargin=LauncherUi.dp(a,19);
        a.root.addView(gear,gp);
        gear.setOnClickListener(v->SettingsSheet.open(a));
        a.done=LauncherUi.text(a,"Done",14,Color.WHITE,true);
        a.done.setBackground(LauncherUi.bg(a,0x975970AB,15));
        FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(LauncherUi.dp(a,72),
                LauncherUi.dp(a,35),Gravity.TOP|Gravity.RIGHT);
        dp.topMargin=LauncherUi.dp(a,100);dp.rightMargin=LauncherUi.dp(a,18);
        a.root.addView(a.done,dp);
        a.done.setVisibility(View.GONE);
        a.done.setOnClickListener(v->a.controller.setEditing(false));
        a.dock.setOnDragListener((v,event)->{
            if(!(event.getLocalState() instanceof HomeDrag.Ref))return false;
            if(event.getAction()==DragEvent.ACTION_DROP){
                HomeDrag.drop(a,(HomeDrag.Ref)event.getLocalState(),true,a.home.dock.size(),null);
            }
            return true;
        });
    }
}
