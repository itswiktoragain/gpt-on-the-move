package com.wiktor.jogger;

/** Pure Java gesture recognizer; cancellation can never place a block. */
public final class TouchGesture {
 public interface Sink {void look(float dx,float dy);void mine(boolean down,float x,float y);void place(float x,float y);}
 private final Sink out;private final float slop;private long began;private float sx,sy,lastX,lastY;private boolean active,drag,mining;
 public TouchGesture(float slop,Sink sink){this.slop=slop;out=sink;}
 public void down(float x,float y,long now){cancel();active=true;sx=lastX=x;sy=lastY=y;began=now;}
 public void move(float x,float y,long now){if(!active)return;float dx=x-lastX,dy=y-lastY;if(!drag&&Math.hypot(x-sx,y-sy)>slop){drag=true;if(mining){out.mine(false,lastX,lastY);mining=false;}dx=x-sx;dy=y-sy;}lastX=x;lastY=y;if(drag)out.look(dx,dy);}
 public void tick(long now){if(active&&!drag&&!mining&&now-began>=300){mining=true;out.mine(true,lastX,lastY);}}
 public void up(float x,float y,long now){if(!active)return;move(x,y,now);boolean wasMining=mining;active=false;if(mining){out.mine(false,x,y);mining=false;}if(!drag&&!wasMining&&now-began<=240)out.place(x,y);}
 public void cancel(){if(mining)out.mine(false,lastX,lastY);active=drag=mining=false;}
 public boolean isActive(){return active;}
}
