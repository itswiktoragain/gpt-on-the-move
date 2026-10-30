package com.minecraftjogger.touch;

import java.lang.reflect.*;
import java.util.Properties;

/** Off-centre first-person picking. It changes the pick ray, not reach or camera rotation. */
public final class TouchRay {
    private TouchRay() {}
    private static volatile Target target = new Target(0,0,1,false);
    private static volatile double fov=70;
    public static volatile boolean fovSeen;
    private static Properties mappings=new Properties();
    private static final ThreadLocal<Integer> depth=new ThreadLocal<Integer>() { protected Integer initialValue(){return 0;} };
    private static Class<?> vectorClass;
    private static Field x,y,z;
    private static Constructor<?> constructor;
    private static final class Target {
        final double x,y,aspect; final boolean active;
        Target(double x,double y,double aspect,boolean active){this.x=x;this.y=y;this.aspect=aspect;this.active=active;}
    }
    public static void configure(Properties p){mappings=p;}
    public static void target(double nx,double ny,double aspect,boolean active) {
        if(!Double.isFinite(nx)||!Double.isFinite(ny)||!Double.isFinite(aspect)||Math.abs(nx)>1.01||Math.abs(ny)>1.01||aspect<.1||aspect>10){clear();return;}
        target=new Target(nx,ny,aspect,active);
    }
    public static void clear(){target=new Target(0,0,1,false);}
    public static void enter(){depth.set(depth.get()+1);}
    public static void leave(){depth.set(Math.max(0,depth.get()-1));}
    public static void rememberFov(double value,boolean world) {
        if(world&&Double.isFinite(value)&&value>1&&value<179){fov=value;fovSeen=true;}
    }
    public static double[] project(double fx,double fy,double fz,double nx,double ny,double aspect,double degrees) {
        double length=Math.sqrt(fx*fx+fy*fy+fz*fz);if(length<1e-12)return new double[]{fx,fy,fz};
        fx/=length;fy/=length;fz/=length;
        double horizontal=Math.sqrt(fx*fx+fz*fz);
        double rx=horizontal>1e-12?-fz/horizontal:-1,rz=horizontal>1e-12?fx/horizontal:0;
        double ux=-rz*fy,uy=rz*fx-rx*fz,uz=rx*fy;
        double tangent=Math.tan(Math.toRadians(degrees)*.5),sx=nx*aspect*tangent,sy=ny*tangent;
        double vx=fx+rx*sx+ux*sy,vy=fy+uy*sy,vz=fz+rz*sx+uz*sy;
        double norm=Math.sqrt(vx*vx+vy*vy+vz*vz);return new double[]{vx/norm,vy/norm,vz/norm};
    }
    private static Field field(Class<?> type,String key,String... alternatives) throws Exception {
        String mapped=mappings.getProperty("field."+key);
        if(mapped!=null)try{Field f=type.getDeclaredField(mapped);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        for(String name:alternatives)try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(key);
    }
    public static Object adjust(Object original) {
        Target t=target;
        if(!t.active||original==null||depth.get()==0||!fovSeen)return original;
        try {
            Class<?> type=original.getClass();
            if(vectorClass!=type){x=field(type,"x","x","field_1352");y=field(type,"y","y","field_1351");z=field(type,"z","z","field_1350");constructor=type.getConstructor(double.class,double.class,double.class);vectorClass=type;}
            double[] vector=project(x.getDouble(original),y.getDouble(original),z.getDouble(original),t.x,t.y,t.aspect,fov);
            return constructor.newInstance(vector[0],vector[1],vector[2]);
        } catch(Throwable unavailable){clear();return original;}
    }
}
