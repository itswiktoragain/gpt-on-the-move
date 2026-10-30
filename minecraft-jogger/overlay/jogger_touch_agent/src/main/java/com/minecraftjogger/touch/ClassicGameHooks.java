package com.minecraftjogger.touch;

import org.objectweb.asm.*;
import java.io.*;
import java.lang.instrument.*;
import java.lang.reflect.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Version-aware picking and game-screen observation. No Mixins or Minecraft classes in the APK. */
public final class ClassicGameHooks {
    private ClassicGameHooks() {}
    private static final Properties names=new Properties();
    private static Instrumentation instrumentation;
    private static volatile long epoch=1;
    private static volatile boolean hasPick,hasView;
    private static volatile String screenState="SCREEN";
    private static Object client;
    private static Field screen,level;
    private static long nextScan;
    private static boolean matches(String value,String key,String... candidates) {
        if(value==null)return false;String mapped=names.getProperty(key);
        if(value.equals(mapped))return true;for(String candidate:candidates)if(value.equals(candidate))return true;return false;
    }
    public static void install(Instrumentation inst) {
        instrumentation=inst;
        String path=System.getProperty("jogger.game.mappings");
        if(path!=null)try(InputStream in=new FileInputStream(path)){names.load(in);}catch(IOException ignored){}
        TouchRay.configure(names);
        try{inst.addTransformer(new Transformer(),false);}catch(Throwable error){System.out.println("[Jogger] Touch ray hooks unavailable; crosshair controls remain enabled.");}
    }
    public static boolean line(String line) {
        String[] words=line.split(" ");
        try {
            if(words.length>1)epoch=Long.parseLong(words[1]);
            if(words[0].equals("R"))TouchRay.clear();
            if(words[0].equals("T")){
                if(words.length==6)TouchRay.target(Double.parseDouble(words[2]),Double.parseDouble(words[3]),Double.parseDouble(words[4]),words[5].equals("1"));
                return true;
            }
        }catch(RuntimeException ignored){TouchRay.clear();}
        return false;
    }
    private static final class Transformer implements ClassFileTransformer {
        @Override public byte[] transform(ClassLoader loader,String name,Class<?> redefined,ProtectionDomain domain,byte[] bytes) {
            if(name==null)return null;String dotted=name.replace('/','.');
            final boolean renderer=matches(dotted,"class.renderer","net.minecraft.client.renderer.GameRenderer","net.minecraft.client.render.GameRenderer","net.minecraft.class_757");
            final boolean entity=matches(dotted,"class.entity","net.minecraft.world.entity.Entity","net.minecraft.entity.Entity","net.minecraft.class_1297");
            if(!renderer&&!entity)return null;
            try {
                ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
                final boolean[] changed={false};
                reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
                    @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions) {
                        MethodVisitor delegate=super.visitMethod(access,method,desc,signature,exceptions);
                        final boolean pick=renderer&&desc.equals("(F)V")&&matches(method,"method.pick","pick","updateTargetedEntity","method_3190");
                        final boolean view=entity&&desc.startsWith("(F)L")&&matches(method,"method.view","getViewVector","getRotationVec","method_5828");
                        final boolean fov=renderer&&(desc.endsWith("Z)D")||desc.endsWith("Z)F"))&&matches(method,"method.fov","getFov","method_3196");
                        if(!pick&&!view&&!fov)return delegate;
                        changed[0]=true;if(pick)hasPick=true;if(view)hasView=true;
                        int slot=(access&Opcodes.ACC_STATIC)==0?1:0;for(Type arg:Type.getArgumentTypes(desc))slot+=arg.getSize();final int boolSlot=slot-1;
                        return new MethodVisitor(Opcodes.ASM9,delegate) {
                            @Override public void visitCode(){super.visitCode();if(pick)super.visitMethodInsn(Opcodes.INVOKESTATIC,"com/minecraftjogger/touch/TouchRay","enter","()V",false);}
                            @Override public void visitInsn(int opcode) {
                                if(pick&&(opcode==Opcodes.RETURN||opcode==Opcodes.ATHROW))super.visitMethodInsn(Opcodes.INVOKESTATIC,"com/minecraftjogger/touch/TouchRay","leave","()V",false);
                                if(view&&opcode==Opcodes.ARETURN){super.visitMethodInsn(Opcodes.INVOKESTATIC,"com/minecraftjogger/touch/TouchRay","adjust","(Ljava/lang/Object;)Ljava/lang/Object;",false);super.visitTypeInsn(Opcodes.CHECKCAST,Type.getReturnType(desc).getInternalName());}
                                if(fov&&(opcode==Opcodes.DRETURN||opcode==Opcodes.FRETURN)){
                                    super.visitInsn(opcode==Opcodes.DRETURN?Opcodes.DUP2:Opcodes.DUP);
                                    if(opcode==Opcodes.FRETURN)super.visitInsn(Opcodes.F2D);
                                    super.visitVarInsn(Opcodes.ILOAD,boolSlot);super.visitMethodInsn(Opcodes.INVOKESTATIC,"com/minecraftjogger/touch/TouchRay","rememberFov","(DZ)V",false);
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                },0);
                return changed[0]?writer.toByteArray():null;
            }catch(Throwable unsupported){System.out.println("[Jogger] No touch ray patch for this class/version.");return null;}
        }
    }
    private static Field findField(Class<?> cls,String key,String... candidates)throws Exception {
        String mapped=names.getProperty(key);
        for(Class<?> type=cls;type!=null;type=type.getSuperclass()){
            if(mapped!=null)try{Field f=type.getDeclaredField(mapped);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
            for(String name:candidates)try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        }throw new NoSuchFieldException(key);
    }
    private static void discover() {
        if(client!=null||instrumentation==null||System.nanoTime()<nextScan)return;
        nextScan=System.nanoTime()+3000000000L;
        for(Class<?> cls:instrumentation.getAllLoadedClasses()) {
            if(!matches(cls.getName(),"class.mc","net.minecraft.client.Minecraft","net.minecraft.client.MinecraftClient","net.minecraft.class_310"))continue;
            try {
                for(Field f:cls.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==cls){f.setAccessible(true);Object candidate=f.get(null);
                    if(candidate instanceof Executor){Field sf=findField(cls,"field.screen","screen","currentScreen","field_1755");Field lf=findField(cls,"field.level","level","world","field_1687");screen=sf;level=lf;client=candidate;return;}
                }
            }catch(Throwable ignored){}
        }
    }
    private static String state(Object value,boolean inWorld) {
        if(value==null)return inWorld?"PLAY":"SCREEN";
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass()){
            if(matches(type.getName(),"class.chat","net.minecraft.client.gui.screens.ChatScreen","net.minecraft.client.gui.screen.ChatScreen","net.minecraft.class_408"))return "CHAT";
        }return "SCREEN";
    }
    public static final class Session implements AutoCloseable {
        private volatile boolean alive=true;
        private final Thread worker;
        private final AtomicBoolean queued=new AtomicBoolean();
        public Session(final BufferedWriter writer) {
            worker=new Thread(()->{
                String last="";
                while(alive){
                    try {
                        discover();Object current=client;
                        if(current instanceof Executor&&queued.compareAndSet(false,true))((Executor)current).execute(()->{
                            try{screenState=state(screen.get(current),level.get(current)!=null);}catch(Throwable ignored){}finally{queued.set(false);}
                        });
                        if(current!=null){String message="S "+epoch+" "+screenState+" "+(hasPick&&hasView&&TouchRay.fovSeen?1:0);
                            if(!message.equals(last)){synchronized(writer){writer.write(message);writer.newLine();writer.flush();}last=message;}
                        }
                        Thread.sleep(180);
                    }catch(InterruptedException end){return;}catch(Throwable error){if(error instanceof IOException)return;}
                }
            },"Jogger game context");worker.setDaemon(true);worker.start();
        }
        @Override public void close(){alive=false;worker.interrupt();TouchRay.clear();}
    }
}
