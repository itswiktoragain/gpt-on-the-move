package com.eelauncber.launcher;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class HomeScreen {
    static void build(LauncherActivity a) {
        a.root=new FrameLayout(a);
        a.root.setBackgroundColor(Color.TRANSPARENT);
        a.setContentView(a.root);
        View shade=new View(a);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x4A12223C,0x02000000,0x560C142B}));
        a.root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        a.pager=new HomePager(a);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-1);
        pp.topMargin=LauncherUi.dp(a,65);
        pp.bottomMargin=LauncherUi.dp(a,185);
        a.root.addView(a.pager,pp);
        a.pager.setListener(new HomePager.Listener(){
            @Override public void onPageChanged(int p,int n){HomePages.updateDots(a,p,n);}
            @Override public void onVerticalSwipe(boolean up){AppDrawer.show(a,"");}
        });
        a.dots=new LinearLayout(a);
        a.dots.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dot=new FrameLayout.LayoutParams(-2,LauncherUi.dp(a,19),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        dot.bottomMargin=LauncherUi.dp(a,154);
        a.root.addView(a.dots,dot);
        a.dock=new LinearLayout(a);
        a.dock.setGravity(Gravity.CENTER_VERTICAL);
        a.dock.setPadding(LauncherUi.dp(a,12),0,LauncherUi.dp(a,12),0);
        a.dock.setBackground(LauncherUi.bg(a,0xB9E8ECF5,30));
        FrameLayout.LayoutParams dock=new FrameLayout.LayoutParams(-1,LauncherUi.dp(a,88),Gravity.BOTTOM);
        dock.leftMargin=LauncherUi.dp(a,17);
        dock.rightMargin=LauncherUi.dp(a,17);
        dock.bottomMargin=LauncherUi.dp(a,24);
        a.root.addView(a.dock,dock);
        HomeControls.add(a);

    }
    static void updateClock(LauncherActivity a) { EelaWidgets.refresh(a.root); }
    static void draw(LauncherActivity a){HomePages.draw(a);}
}
