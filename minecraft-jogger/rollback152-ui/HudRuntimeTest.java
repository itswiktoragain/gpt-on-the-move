package com.minecraftjogger.touch;
import java.util.Properties;
/** Verifies real push/translate/pop behavior with obfuscated method names. */
public final class HudRuntimeTest {
    static int checks;
    static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);checks++;}
    public static final class Pose {
        int pushes,pops;double x,y;
        java.util.ArrayDeque<double[]> stack=new java.util.ArrayDeque<>();
        public void a(){stack.push(new double[]{x,y});pushes++;}
        public void b(){double[] p=stack.pop();x=p[0];y=p[1];pops++;}
        public void a(double x,double y,double z){this.x+=x;this.y+=y;}
    }
    public static final class Graphics {
        final Pose pose=new Pose();
        public int a(){return 560;}
        public int b(){return 315;}
        public Pose c(){return pose;}
    }
    public static void main(String[] args){
        Properties p=new Properties();
        p.setProperty("method.graphics.width","a");
        p.setProperty("method.graphics.height","b");
        p.setProperty("method.graphics.pose","c");
        p.setProperty("method.pose.push","a");
        p.setProperty("method.pose.pop","b");
        p.setProperty("method.pose.translate","a");
        MobileHooks.names=p;
        Graphics g=new Graphics();
        MobileHooks.begin(g,4);check(g.pose.pushes==1,"health push");check(g.pose.y==-220,"health top");
        MobileHooks.begin(g,5);check(g.pose.x==-180,"armor left");check(g.pose.y==-220,"nested vertical");
        MobileHooks.end();check(g.pose.x==0&&g.pose.y==-220,"pop restores armor");
        MobileHooks.begin(g,6);check(g.pose.x==180,"food right");MobileHooks.end();
        MobileHooks.end();check(g.pose.y==0,"outer pop");
        MobileHooks.begin(g,5);check(g.pose.y==-220,"standalone health fallback");MobileHooks.end();
        MobileHooks.begin(g,8);check(g.pose.y==-238,"XP top");MobileHooks.end();
        check(g.pose.pushes==g.pose.pops,"no stack leaks");
        System.out.println("Obfuscated HUD runtime reflection: "+checks+" checks passed");
    }
}
