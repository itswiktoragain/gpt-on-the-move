package com.eelauncber.launcher;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

final class HomeGrid extends ViewGroup {
    HomeGrid(Context context){super(context);setClipChildren(false);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);
        int cellW=Math.max(1,w/4),cellH=Math.max(1,h/5);
        for(int i=0;i<getChildCount();i++){
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cellW,MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(cellH,MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w,h);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int cw=(r-l)/4,ch=(b-t)/5;
        for(int i=0;i<getChildCount();i++){
            View v=getChildAt(i);
            int x=i%4*cw,y=i/4*ch;
            v.layout(x,y,x+cw,y+ch);
        }
    }
}
