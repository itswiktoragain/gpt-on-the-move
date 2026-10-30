package com.wiktor.jogger;
import android.view.Surface;
public final class Native {
 static {System.loadLibrary("glfw_jogger");System.loadLibrary("jogger");}
 public static native void surface(Surface surface,int width,int height);
 public static native void input(int type,int a,int b,double x,double y);
 public static native int[] state();
 public static native void target(double x,double y,int button,boolean hold);
 public static native void cancelTarget();
 public static native String boot(String runtime,String natives,String game,String renderer,String[] options,String main,String[] args);
 public static void key(int key,boolean down){input(0,key,down?1:0,0,0);}
 public static void press(int key){key(key,true);key(key,false);}
 public static void mouse(int button,boolean down){input(1,button,down?1:0,0,0);}
 public static void release(){cancelTarget();input(5,0,0,0,0);}
 private Native(){}
}
