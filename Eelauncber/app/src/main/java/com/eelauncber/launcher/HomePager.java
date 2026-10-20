package com.eelauncber.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.view.animation.DecelerateInterpolator;
import android.animation.ValueAnimator;

final class HomePager extends HorizontalScrollView {
    interface Listener { void onPageChanged(int page,int total); void onVerticalSwipe(boolean up); }
    private final LinearLayout strip;
    private Listener listener;
    private int page;
    private float x, y;
    private ValueAnimator anim;
    HomePager(Context c) {
        super(c);
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        strip=new LinearLayout(c);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        addView(strip,new LayoutParams(-2,-1));
    }
    void setListener(Listener l){listener=l;}
    int getPage(){return page;}
    int getCount(){return strip.getChildCount();}
    void replacePages(java.util.List<View> pages){
        if(anim!=null)anim.cancel();
        strip.removeAllViews();
        int w=getResources().getDisplayMetrics().widthPixels;
        for(View p:pages)strip.addView(p,new LinearLayout.LayoutParams(w,-1));
        page=0;scrollTo(0,0);notifyPage();
    }
    void notifyPage(){if(listener!=null)listener.onPageChanged(page,getCount());}
    void animateTo(int target){
        page=Math.max(0,Math.min(getCount()-1,target));
        notifyPage();
        int end=page*getWidth();
        if(anim!=null)anim.cancel();
        anim=ValueAnimator.ofInt(getScrollX(),end);
        anim.setDuration(255);
        anim.setInterpolator(new DecelerateInterpolator(1.4f));
        anim.addUpdateListener(v->scrollTo((Integer)v.getAnimatedValue(),0));
        anim.start();
    }
    @Override public void fling(int velocityX){
        if(Math.abs(velocityX)>250)animateTo(page+(velocityX>0?1:-1));
        else animateTo(Math.round((float)getScrollX()/Math.max(1,getWidth())));
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();}
        boolean result=super.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_UP){
            float dx=e.getX()-x,dy=e.getY()-y;
            if(Math.abs(dy)>Math.abs(dx)*1.4f&&Math.abs(dy)>70){
                if(listener!=null)listener.onVerticalSwipe(dy<0);
            } else postDelayed(()->animateTo(Math.round((float)getScrollX()/Math.max(1,getWidth()))),45);
        }
        return result;
    }
}
