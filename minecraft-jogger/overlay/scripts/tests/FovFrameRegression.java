import org.objectweb.asm.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.minecraftjogger.touch.TouchRay;

/** Regression for the 1.21.4 glq.a(Camera,float,boolean)F verifier crash. */
public final class FovFrameRegression implements Opcodes {
    static final String NAME="net/minecraft/client/renderer/GameRenderer";
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static class Loader extends ClassLoader {
        Loader(){super(FovFrameRegression.class.getClassLoader());}
        Class<?> define(byte[] bytes){return defineClass(NAME.replace('/','.'),bytes,0,bytes.length);}
    }
    static byte[] fixture(boolean wide,boolean stat,boolean reuse,boolean retainWide) {
        ClassWriter cw=new ClassWriter(0);
        cw.visit(V1_8,ACC_PUBLIC,NAME,null,"java/lang/Object",null);
        MethodVisitor init=cw.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);
        init.visitCode();init.visitVarInsn(ALOAD,0);init.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitInsn(RETURN);init.visitMaxs(1,1);init.visitEnd();
        int start=stat?0:1, value=start+1, bool=value+(wide?2:1), result=bool+1;
        MethodVisitor mv=cw.visitMethod(ACC_PUBLIC|(stat?ACC_STATIC:0),"getFov",wide?"(Ljava/lang/Object;DZ)D":"(Ljava/lang/Object;FZ)F",null,null);
        mv.visitCode();Label no=new Label(),end=new Label();
        mv.visitVarInsn(ILOAD,bool);mv.visitJumpInsn(IFEQ,no);
        mv.visitVarInsn(wide?DLOAD:FLOAD,value);mv.visitVarInsn(wide?DSTORE:FSTORE,result);mv.visitJumpInsn(GOTO,end);
        mv.visitLabel(no);
        Object[] arguments=stat?new Object[]{"java/lang/Object",wide?DOUBLE:FLOAT,INTEGER}:new Object[]{NAME,"java/lang/Object",wide?DOUBLE:FLOAT,INTEGER};
        mv.visitFrame(F_FULL,arguments.length,arguments,0,null);
        mv.visitLdcInsn(wide?(Object)Double.valueOf(42):(Object)Float.valueOf(42));mv.visitVarInsn(wide?DSTORE:FSTORE,result);
        mv.visitLabel(end);
        Object[] dead=new Object[result+1];for(int i=0;i<result;i++)dead[i]=TOP;dead[result]=wide?DOUBLE:FLOAT;
        if(retainWide)dead=stat?new Object[]{"java/lang/Object",DOUBLE,TOP,DOUBLE}:new Object[]{NAME,"java/lang/Object",DOUBLE,TOP,DOUBLE};
        mv.visitFrame(F_FULL,dead.length,dead,0,null);
        if(reuse){mv.visitLdcInsn("original boolean slot is now a reference");mv.visitVarInsn(ASTORE,bool);}
        mv.visitVarInsn(wide?DLOAD:FLOAD,result);mv.visitInsn(wide?DRETURN:FRETURN);mv.visitMaxs(wide?2:1,result+(wide?2:1));mv.visitEnd();cw.visitEnd();return cw.toByteArray();
    }
    static void runOne(ClassFileTransformer transformer,boolean wide,boolean stat,boolean reuse,boolean retainWide)throws Exception {
        byte[] input=fixture(wide,stat,reuse,retainWide);
        Class<?> original=new Loader().define(input);original.getDeclaredMethods();checks++;
        byte[] patched=transformer.transform(FovFrameRegression.class.getClassLoader(),NAME,null,null,input);
        check(patched!=null,"FOV method was not transformed");
        Class<?> cls=new Loader().define(patched);
        Method fov=cls.getMethod("getFov",Object.class,wide?double.class:float.class,boolean.class);
        Object receiver=stat?null:cls.getConstructor().newInstance();
        Object number=wide?(Object)Double.valueOf(70):(Object)Float.valueOf(70);
        Field seen=TouchRay.class.getField("fovSeen");Field stored=TouchRay.class.getDeclaredField("fov");stored.setAccessible(true);
        seen.setBoolean(null,false);
        Number r=(Number)fov.invoke(receiver,new Object(),number,true);
        check(r.doubleValue()==70,"World return changed");check(seen.getBoolean(null),"World flag lost");check(stored.getDouble(null)==70,"Wrong FOV observed");
        seen.setBoolean(null,false);
        r=(Number)fov.invoke(receiver,new Object(),number,false);
        check(r.doubleValue()==42,"Hand return changed");check(!seen.getBoolean(null),"Hand FOV incorrectly reported as world FOV");
    }
    public static void main(String[] args)throws Exception {
        Class<?> t=Class.forName("com.minecraftjogger.touch.ClassicGameHooks$Transformer");Constructor<?> ctor=t.getDeclaredConstructor();ctor.setAccessible(true);
        ClassFileTransformer transformer=(ClassFileTransformer)ctor.newInstance();
        boolean old=args.length>0&&args[0].equals("--expect-bug");int failures=0,cases=0;
        for(boolean wide:new boolean[]{false,true})for(boolean stat:new boolean[]{false,true})for(boolean reuse:new boolean[]{false,true}){
            cases++;try{runOne(transformer,wide,stat,reuse,false);}catch(VerifyError error){if(!old)throw error;failures++;}
        }
        for(boolean stat:new boolean[]{false,true}){cases++;try{runOne(transformer,true,stat,false,true);}catch(VerifyError error){if(!old)throw error;failures++;}}
        if(old){check(failures==cases,"Original hook did not reproduce every dropped-local error");System.out.println("Original 1.1.0 bug reproduced: "+failures+"/"+cases+" VerifyErrors.");}
        else {check(failures==0,"Verification failed");System.out.println("FOV regression PASS: "+cases+" optimized float/double, static/instance, reused/dropped/wide-local cases; "+checks+" checks.");}
    }
}
