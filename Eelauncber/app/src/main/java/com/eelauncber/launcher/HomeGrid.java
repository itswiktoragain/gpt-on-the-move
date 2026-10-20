package com.eelauncber.launcher;
import android.content.Context;
import android.view.*;
final class HomeGrid extends ViewGroup {
 static class Params extends ViewGroup.LayoutParams{
  int cell,sx,sy;
  Params(int cell,int sx,int sy){super(-1,-1);this.cell=cell;this.sx=sx;this.sy=sy;}
 }
 HomeGrid(Context c){super(c);setClipChildren(false);}
 @Override protected void onMeasure(int ws,int hs){
  int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs),cw=w/4,ch=h/5;
  for(int i=0;i<getChildCount();i++){
   View v=getChildAt(i);Params p=(Params)v.getLayoutParams();
   v.measure(MeasureSpec.makeMeasureSpec(cw*p.sx,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch*p.sy,MeasureSpec.EXACTLY));
  }setMeasuredDimension(w,h);
 }
 @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
  int cw=(r-l)/4,ch=(b-t)/5;
  for(int i=0;i<getChildCount();i++){
   View v=getChildAt(i);Params p=(Params)v.getLayoutParams();
   int x=p.cell%4*cw,y=p.cell/4*ch;
   v.layout(x,y,x+cw*p.sx,y+ch*p.sy);
  }
 }
}
