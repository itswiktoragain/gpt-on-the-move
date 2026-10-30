package com.wiktor.jogger.agent;

import java.io.*;
import java.lang.instrument.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

/** Original Jogger agent. Uses the installed game's official mappings, not Mixins.
 * The pick hook changes the camera entity's rotations only while computing the ray,
 * then restores them. Looking and rendering do not rotate toward a tapped block.
 */
public final class MobileAgent {
 private static final String MC="net.minecraft.client.Minecraft",GR="net.minecraft.client.renderer.GameRenderer",ENT="net.minecraft.world.entity.Entity",OPT="net.minecraft.client.Options",OI="net.minecraft.client.OptionInstance",WIN="com.mojang.blaze3d.platform.Window";
 private static final Map<String,String> classes=new HashMap<>(),fields=new HashMap<>(),methods=new HashMap<>();
 private static volatile Object minecraft;private static volatile Field screen,options,window;
 private static volatile Method cameraMethod;private static Field yaw,pitch,oldYaw,oldPitch;
 private static Instrumentation instrumentation;private static boolean nativeLoaded;
 private static final ThreadLocal<AimState> local=ThreadLocal.withInitial(AimState::new);
 private static final class AimState {Object entity;final float[] old=new float[4];final double[] ray=new double[4];boolean active;}
 private static native void nativeHud(int screen,int width,int height,double fov,double sensitivity);
 private static native boolean nativeAim(double[] out);
 private static native void nativePicked();
 private static native void nativeHook(boolean ok);

 public static void premain(String ignored,Instrumentation inst){instrumentation=inst;
  try{System.load(System.getProperty("jogger.native"));nativeLoaded=true;loadMappings(Paths.get(System.getProperty("jogger.mappings","")));inst.addTransformer(new PickTransformer(),true);System.out.println("[Jogger] Original mobile agent active");}
  catch(Throwable t){System.err.println("[Jogger] Agent setup: "+t);}
  Thread t=new Thread(MobileAgent::watch,"Jogger screen observer");t.setDaemon(true);t.start();
 }
 private static void loadMappings(Path p)throws Exception{if(!Files.isRegularFile(p))return;String owner="";for(String line:Files.readAllLines(p,StandardCharsets.UTF_8)){if(line.startsWith("#")||line.trim().isEmpty())continue;if(!Character.isWhitespace(line.charAt(0))&&line.endsWith(":")){String[]a=line.substring(0,line.length()-1).split(" -> ");if(a.length==2){owner=a[0];classes.put(a[0],a[1]);}}else{String s=line.trim().replaceFirst("^\\d+:\\d+:","");int arrow=s.lastIndexOf(" -> ");if(arrow<0)continue;String left=s.substring(0,arrow),right=s.substring(arrow+4);int space=left.indexOf(' ');if(space<0)continue;String member=left.substring(space+1);if(member.contains("(")){int end=member.indexOf(')');if(end>0)methods.put(owner+"|"+member.substring(0,end+1),right);}else fields.put(owner+"|"+member,right);}}
  Path extra=Paths.get(System.getProperty("jogger.dir","."),"jogger-intermediary.properties");if(Files.isRegularFile(extra)){Properties q=new Properties();try(InputStream in=Files.newInputStream(extra)){q.load(in);}for(String k:q.stringPropertyNames()){String val=q.getProperty(k);if(k.startsWith("C:"))classes.put(k.substring(2),val);else if(k.startsWith("F:"))fields.put(k.substring(2),val);else if(k.startsWith("M:"))methods.put(k.substring(2),val);}}
 }
 private static String cn(String name){return classes.getOrDefault(name,name);}
 private static Field f(Class<?> c,String owner,String name)throws Exception{String mapped=fields.getOrDefault(owner+"|"+name,name);for(Class<?>k=c;k!=null;k=k.getSuperclass())try{Field a=k.getDeclaredField(mapped);a.setAccessible(true);return a;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(owner+"."+name);}
 private static Method m(Class<?> c,String owner,String name)throws Exception{String mapped=methods.getOrDefault(owner+"|"+name+"()",name);for(Class<?>k=c;k!=null;k=k.getSuperclass())try{Method a=k.getDeclaredMethod(mapped);a.setAccessible(true);return a;}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(owner+"."+name);}
 private static void findClient()throws Exception{for(Class<?> c:instrumentation.getAllLoadedClasses())if(c.getName().equals(cn(MC))||c.getName().equals(MC)){Object v=null;for(Field ff:c.getDeclaredFields())if(Modifier.isStatic(ff.getModifiers())&&ff.getType()==c){ff.setAccessible(true);v=ff.get(null);if(v!=null)break;}if(v==null)return;Field sc=f(c,MC,"screen"),op=f(c,MC,"options"),wi=f(c,MC,"window");Method ca=m(c,MC,"getCameraEntity");screen=sc;options=op;window=wi;cameraMethod=ca;minecraft=v;System.out.println("[Jogger] Connected to Minecraft screen state");return;}}
 private static double option(Object opts,String name,double fallback){try{Object o=f(opts.getClass(),OPT,name).get(opts);Object value=f(o.getClass(),OI,"value").get(o);return ((Number)value).doubleValue();}catch(Throwable ignored){return fallback;}}
 private static int size(Object w,String method,int fallback){try{return ((Number)m(w.getClass(),WIN,method).invoke(w)).intValue();}catch(Throwable ignored){return fallback;}}
 private static void watch(){while(true)try{if(minecraft==null){findClient();Thread.sleep(500);continue;}Object sc=screen.get(minecraft),op=options.get(minecraft),w=window.get(minecraft);int kind=0;if(sc!=null){String c=sc.getClass().getName();kind=c.equals(cn("net.minecraft.client.gui.screens.ChatScreen"))?1:c.equals(cn("net.minecraft.client.gui.screens.PauseScreen"))?2:3;}if(nativeLoaded)nativeHud(kind,size(w,"getGuiScaledWidth",640),size(w,"getGuiScaledHeight",360),option(op,"fov",70),option(op,"sensitivity",.5));Thread.sleep(100);}catch(InterruptedException done){return;}catch(Throwable t){try{Thread.sleep(1000);}catch(InterruptedException done){return;}}}
 public static void beforePick(){if(!nativeLoaded||minecraft==null)return;AimState a=local.get();if(a.active)afterPick();try{if(screen.get(minecraft)!=null||!nativeAim(a.ray))return;Object e=cameraMethod.invoke(minecraft);if(e==null)return;if(yaw==null){yaw=f(e.getClass(),ENT,"yRot");pitch=f(e.getClass(),ENT,"xRot");oldYaw=f(e.getClass(),ENT,"yRotO");oldPitch=f(e.getClass(),ENT,"xRotO");}a.entity=e;a.old[0]=yaw.getFloat(e);a.old[1]=pitch.getFloat(e);a.old[2]=oldYaw.getFloat(e);a.old[3]=oldPitch.getFloat(e);double[]angles=rayAngles(a.old[0],a.old[1],a.ray[0],a.ray[1],a.ray[2],a.ray[3]);a.active=true;float dy=(float)(angles[0]-a.old[0]),dp=(float)(angles[1]-a.old[1]);yaw.setFloat(e,a.old[0]+dy);pitch.setFloat(e,a.old[1]+dp);oldYaw.setFloat(e,a.old[2]+dy);oldPitch.setFloat(e,a.old[3]+dp);}catch(Throwable t){restore(a);}}
 public static void afterPick(){AimState a=local.get();restore(a);if(nativeLoaded)nativePicked();}
 private static void restore(AimState a){if(!a.active)return;try{yaw.setFloat(a.entity,a.old[0]);pitch.setFloat(a.entity,a.old[1]);oldYaw.setFloat(a.entity,a.old[2]);oldPitch.setFloat(a.entity,a.old[3]);}catch(Throwable t){System.err.println("[Jogger] Could not restore pick orientation: "+t);}finally{a.active=false;a.entity=null;}}
 /** Pure projection math, separately tested. Minecraft yaw 0 points along +Z. */
 public static double[] rayAngles(double yaw,double pitch,double nx,double ny,double fov,double aspect){double y=Math.toRadians(yaw),p=Math.toRadians(pitch);double t=Math.tan(Math.toRadians(Math.max(30,Math.min(120,fov)))/2),rx=(2*nx-1)*aspect*t,ry=(1-2*ny)*t;double x=-Math.sin(y)*Math.cos(p)-rx*Math.cos(y)-ry*Math.sin(y)*Math.sin(p),v=-Math.sin(p)+ry*Math.cos(p),z=Math.cos(y)*Math.cos(p)-rx*Math.sin(y)+ry*Math.cos(y)*Math.sin(p);double targetYaw=Math.toDegrees(Math.atan2(-x,z));while(targetYaw-yaw>180)targetYaw-=360;while(targetYaw-yaw< -180)targetYaw+=360;return new double[]{targetYaw,Math.toDegrees(Math.atan2(-v,Math.hypot(x,z)))};}
 private static final class PickTransformer implements ClassFileTransformer {
  @Override public byte[] transform(ClassLoader loader,String name,Class<?> redefined,ProtectionDomain domain,byte[] bytes) {
   if (!name.equals(cn(GR).replace('.', '/')) && !name.equals(GR.replace('.', '/'))) return null;
   try {
    String target=methods.getOrDefault(GR+"|pick(float)","pick");
    final boolean[] found={false};ClassReader reader=new ClassReader(bytes);
    ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
    reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
     @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions) {
      MethodVisitor next=super.visitMethod(access,method,desc,signature,exceptions);
      if (!method.equals(target)||!desc.equals("(F)V"))return next;
      found[0]=true;
      return new AdviceAdapter(Opcodes.ASM9,next,access,method,desc) {
       final Label start=new Label(),end=new Label(),handler=new Label();
       private void hook(String method){visitMethodInsn(INVOKESTATIC,"com/wiktor/jogger/agent/MobileAgent",method,"()V",false);}
       @Override protected void onMethodEnter(){hook("beforePick");visitLabel(start);}
       @Override protected void onMethodExit(int opcode){if(opcode!=ATHROW)hook("afterPick");}
       @Override public void visitMaxs(int maxStack,int maxLocals) {
        visitLabel(end);visitTryCatchBlock(start,end,handler,null);visitLabel(handler);
        Object[] locals=(access&ACC_STATIC)==0?new Object[]{name,Opcodes.FLOAT}:new Object[]{Opcodes.FLOAT};
        visitFrame(F_NEW,locals.length,locals,1,new Object[]{"java/lang/Throwable"});
        hook("afterPick");mv.visitInsn(ATHROW);super.visitMaxs(Math.max(maxStack,1),maxLocals);
       }
      };
     }
    },ClassReader.EXPAND_FRAMES);
    if(found[0]){System.out.println("[Jogger] Touch ray hook installed");return writer.toByteArray();}
    System.err.println("[Jogger] No matching pick(float) method; controls fall back to centre targeting");
   }catch(Throwable problem){System.err.println("[Jogger] Pick transformer failed: "+problem);}
   return null;
  }
 }
}
