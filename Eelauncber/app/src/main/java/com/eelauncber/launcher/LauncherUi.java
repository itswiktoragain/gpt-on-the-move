package com.eelauncber.launcher;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.Gravity;

final class LauncherUi {
    static int dp(Activity a,float n){ return (int)(n*a.getResources().getDisplayMetrics().density+0.5f); }
    static GradientDrawable bg(Activity a,int color,int radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(a,radius));return g;
    }
    static TextView text(Activity a,String value,int size,int color,boolean bold){
        TextView v=new TextView(a);
        v.setText(value);v.setTextColor(color);v.setTextSize(size);
        v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    static ImageView icon(MainActivity a,String key,int size){
        ImageView v=new ImageView(a);
        AppCatalog.Entry e=a.catalog.get(key);
        if(e==null||e.icon==null)v.setImageResource(android.R.drawable.sym_def_app_icon);
        else v.setImageDrawable(e.icon.getConstantState()==null?e.icon:e.icon.getConstantState().newDrawable());
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }
}
