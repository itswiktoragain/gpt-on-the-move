package net.kdt.pojavlaunch.jogger;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class JoggerUi {
    public static final int INK=0xff181a1b, MUTED=0xff686d70, PAPER=0xfffafafa, LINE=0xffe5e7e8, PANEL=0xfff0f2f2;
    private JoggerUi() {}
    public static int dp(Context c,float value){return (int)(value*c.getResources().getDisplayMetrics().density+0.5f);}
    public static GradientDrawable background(int color,int radius,Context c){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    public static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    public static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public static TextView text(Context c,String text,int sp,int color,boolean bold){TextView t=new TextView(c);t.setText(text);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;}
    public static Button button(Context c,String label,boolean dark,View.OnClickListener click){Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(dark?0xffffffff:INK);b.setBackgroundTintList(ColorStateList.valueOf(dark?INK:PANEL));b.setBackground(background(dark?INK:PANEL,12,c));b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));b.setPadding(dp(c,14),dp(c,8),dp(c,14),dp(c,8));b.setOnClickListener(click);return b;}
    public static LinearLayout card(Context c){LinearLayout v=column(c);v.setPadding(dp(c,18),dp(c,18),dp(c,18),dp(c,18));v.setBackground(background(0xffffffff,18,c));return v;}
    public static void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(parent.getContext(),top);parent.addView(child,p);}
    public static void weighted(LinearLayout row,View child,boolean gap){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(gap)p.leftMargin=dp(row.getContext(),10);row.addView(child,p);}
    public static EditText input(Context c,String hint){EditText e=new EditText(c);e.setHint(hint);e.setSingleLine(true);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setTextSize(16);return e;}
}
