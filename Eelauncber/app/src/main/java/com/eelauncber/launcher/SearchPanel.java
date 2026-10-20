package com.eelauncber.launcher;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class SearchPanel {
    static void show(LauncherActivity a,String initial){
        a.controller.closeShelf();
        FrameLayout overlay=new FrameLayout(a);
        overlay.setBackgroundColor(0xA9101729);
        overlay.setOnClickListener(v->a.controller.closeShelf());
        LinearLayout panel=new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(LauncherUi.dp(a,14),LauncherUi.dp(a,15),
                LauncherUi.dp(a,14),LauncherUi.dp(a,10));
        panel.setBackground(LauncherUi.bg(a,0xF3F5F6FB,27));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-1);
        p.leftMargin=LauncherUi.dp(a,7);
        p.rightMargin=LauncherUi.dp(a,7);
        p.topMargin=LauncherUi.dp(a,56);
        overlay.addView(panel,p);
        panel.setOnClickListener(v->{});
        LinearLayout bar=new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        EditText input=new EditText(a);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(0xFF24314C);
        input.setHintTextColor(0xFF748095);
        input.setHint("Search apps");
        input.setPadding(LauncherUi.dp(a,14),0,LauncherUi.dp(a,14),0);
        input.setBackground(LauncherUi.bg(a,0xFFE3E8F2,15));
        bar.addView(input,new LinearLayout.LayoutParams(0,LauncherUi.dp(a,48),1));
        TextView close=LauncherUi.text(a,"Cancel",14,0xFF416FD2,true);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(
                LauncherUi.dp(a,66),LauncherUi.dp(a,48));
        bar.addView(close,cp);
        close.setOnClickListener(v->a.controller.closeShelf());
        panel.addView(bar,new LinearLayout.LayoutParams(-1,LauncherUi.dp(a,54)));
        ScrollView scroller=new ScrollView(a);
        scroller.setFillViewport(true);
        LinearLayout list=new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(list);
        panel.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        input.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                SearchResults.render(a,list,s.toString());
            }
            public void afterTextChanged(Editable s){}
        });
        a.root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        a.shelf=overlay;
        input.setText(initial);
        SearchResults.render(a,list,initial);
    }
}
