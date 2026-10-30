import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.*;
import org.objectweb.asm.*;

/** Actual transformer verification, including obfuscated frames that erase arguments. */
public final class AgentTransformTest implements Opcodes {
 private static final String GAME="net/minecraft/client/renderer/GameRenderer";
 private static final String AGENT="com/wiktor/jogger/agent/MobileAgent";
 private static final class Loader extends ClassLoader {
  Loader(){super(AgentTransformTest.class.getClassLoader());}
  Class<?> define(String name,byte[] bytes){return defineClass(name,bytes,0,bytes.length);}
 }
 private static byte[] fixture(){
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
  w.visit(V1_8,ACC_PUBLIC,GAME,null,"java/lang/Object",null);
  MethodVisitor m=w.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);
  m.visitCode();m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
  m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();
  m=w.visitMethod(ACC_PUBLIC,"pick","(F)V",null,null);m.visitCode();
  Label done=new Label(),explicit=new Label();
  m.visitVarInsn(FLOAD,1);m.visitInsn(FCONST_0);m.visitInsn(FCMPG);m.visitJumpInsn(IFGE,done);
  m.visitVarInsn(FLOAD,1);m.visitLdcInsn(-1.0f);m.visitInsn(FCMPG);m.visitJumpInsn(IFLT,explicit);
  m.visitLdcInsn("not-a-number");m.visitMethodInsn(INVOKESTATIC,"java/lang/Integer","parseInt","(Ljava/lang/String;)I",false);
  m.visitInsn(POP);m.visitJumpInsn(GOTO,done);m.visitLabel(explicit);
  m.visitFrame(F_FULL,2,new Object[]{GAME,Opcodes.FLOAT},0,new Object[0]);
  m.visitTypeInsn(NEW,"java/lang/IllegalStateException");m.visitInsn(DUP);
  m.visitMethodInsn(INVOKESPECIAL,"java/lang/IllegalStateException","<init>","()V",false);m.visitInsn(ATHROW);
  m.visitLabel(done);m.visitFrame(F_FULL,0,new Object[0],0,new Object[0]);
  m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
 }
 private static byte[] observer(){
  ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(V1_8,ACC_PUBLIC,AGENT,null,"java/lang/Object",null);
  w.visitField(ACC_PUBLIC|ACC_STATIC,"before","I",null,null).visitEnd();
  w.visitField(ACC_PUBLIC|ACC_STATIC,"after","I",null,null).visitEnd();
  for(String field:new String[]{"before","after"}){
   MethodVisitor m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,field+"Pick","()V",null,null);m.visitCode();
   m.visitFieldInsn(GETSTATIC,AGENT,field,"I");m.visitInsn(ICONST_1);m.visitInsn(IADD);m.visitFieldInsn(PUTSTATIC,AGENT,field,"I");m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();
  }
  w.visitEnd();return w.toByteArray();
 }
 public static void main(String[] args)throws Exception{
  Class<?> transformerClass=Class.forName("com.wiktor.jogger.agent.MobileAgent$PickTransformer");
  Constructor<?> ctor=transformerClass.getDeclaredConstructor();ctor.setAccessible(true);
  ClassFileTransformer transformer=(ClassFileTransformer)ctor.newInstance();
  byte[] result=transformer.transform(AgentTransformTest.class.getClassLoader(),GAME,null,null,fixture());
  if(result==null)throw new AssertionError("Transformer rejected a valid pick method");
  Loader loader=new Loader();Class<?> counters=loader.define(AGENT.replace('/','.'),observer());
  Class<?> game=loader.define(GAME.replace('/','.'),result);Object instance=game.getConstructor().newInstance();Method pick=game.getMethod("pick",float.class);
  pick.invoke(instance,1f);
  for(float input:new float[]{-1f,-2f}){
   try{pick.invoke(instance,input);throw new AssertionError("Expected original exception");}
   catch(InvocationTargetException expected){if(!(expected.getCause() instanceof RuntimeException))throw expected;}
  }
  if(counters.getField("before").getInt(null)!=3||counters.getField("after").getInt(null)!=3)throw new AssertionError("Touch orientation was not restored exactly once on every exit");
  if(transformer.transform(loader,"unrelated/Class",null,null,fixture())!=null)throw new AssertionError("Unrelated classes must not be modified");
  System.out.println("PASS: original transformer handles erased argument frames and restores on all normal and exceptional exits");
 }
}
