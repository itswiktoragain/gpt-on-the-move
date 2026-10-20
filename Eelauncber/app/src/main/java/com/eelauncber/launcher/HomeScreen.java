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
        LinearLayout header=new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        a.clock=LauncherUi.text(a,"",43,Color.WHITE,true);
        a.clock.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        header.addView(a.clock,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,57)));
        a.date=LauncherUi.text(a,"",15,Color.WHITE,true);
        a.date.setGravity(Gravity.LEFT);
        header.addView(a.date,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,27)));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,LauncherUi.dp(a,95),Gravity.TOP);
        hp.topMargin=LauncherUi.dp(a,43);
        hp.leftMargin=LauncherUi.dp(a,28);
        hp.rightMargin=LauncherUi.dp(a,60);
        a.root.addView(header,hp);
        a.pager=new HomePager(a);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-1);
        pp.topMargin=LauncherUi.dp(a,151);
        pp.bottomMargin=LauncherUi.dp(a,185);
        a.root.addView(a.pager,pp);
        a.pager.setListener(new HomePager.Listener(){
            @Override public void onPageChanged(int p,int n){HomePages.updateDots(a,p,n);}
            @Override public void onVerticalSwipe(boolean up){SearchPanel.show(a,"");}
        });
        a.dots=new LinearLayout(a);
        a.dots.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dot=new FrameLayout.LayoutParams(-2,LauncherUi.dp(a,19),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        dot.bottomMargin=LauncherUi.dp(a,154);
        a.root.addView(a.dots,dot);
        TextView search=LauncherUi.text(a,"⌕   Search",13,Color.WHITE,true);
        search.setBackground(LauncherUi.bg(a,0x80434D65,17));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(LauncherUi.dp(a,112),LauncherUi.dp(a,31),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        sp.bottomMargin=LauncherUi.dp(a,114);
        a.root.addView(search,sp);
        search.setOnClickListener(v->SearchPanel.show(a,""));
        a.dock=new LinearLayout(a);
        a.dock.setGravity(Gravity.CENTER);
        a.dock.setPadding(LauncherUi.dp(a,6),0,LauncherUi.dp(a,6),0);
        a.dock.setBackground(LauncherUi.bg(a,0xB9E8ECF5,30));
        FrameLayout.LayoutParams dock=new FrameLayout.LayoutParams(-1,LauncherUi.dp(a,88),Gravity.BOTTOM);
        dock.leftMargin=LauncherUi.dp(a,17);
        dock.rightMargin=LauncherUi.dp(a,17);
        dock.bottomMargin=LauncherUi.dp(a,24);
        a.root.addView(a.dock,dock);
        HomeControls.add(a);
        updateClock(a);
    }
    static void updateClock(LauncherActivity a) {
        if(a.clock==null||a.date==null)return;
        Date now=new Date();
        a.clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now));
        a.date.setText(new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(now));
    }
    static void draw(LauncherActivity a){HomePages.draw(a);}
}
