import com.minecraftjogger.touch.ClassicGameHooks;
import com.minecraftjogger.touch.MobileTransformer;
import com.minecraftjogger.touch.TouchRay;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** End-to-end check: distinct finger pixels select distinct BLOCKS with an
 * obfuscated, transformed Minecraft renderer. Camera direction never changes.
 * Uses the packaged production agent and both production transformers.
 */
public final class FingerBlockPickingTest {
    private static int checks;
    private static void check(boolean good,String caseName){
        if(!good)throw new AssertionError(caseName);
        checks++;
    }
    static final class GameClasses extends ClassLoader {
        final Path classes;
        final ClassFileTransformer mobile,ray;
        GameClasses(Path dir,ClassFileTransformer m,ClassFileTransformer r){
            super(FingerBlockPickingTest.class.getClassLoader());
            classes=dir;mobile=m;ray=r;
        }
        @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
            if(!Arrays.asList("fbb","bum","fks","glq").contains(name))
                throw new ClassNotFoundException(name);
            try {
                byte[] original=Files.readAllBytes(classes.resolve(name+".class"));
                byte[] wrapped=mobile.transform(this,name,null,null,original);
                byte[] transformed=ray.transform(this,name,null,null,
                        wrapped==null?original:wrapped);
                byte[] result=transformed==null?(wrapped==null?original:wrapped):transformed;
                return defineClass(name,result,0,result.length);
            } catch (Exception error) {
                throw new ClassNotFoundException("Cannot patch "+name,error);
            }
        }
    }
    private static int intField(Object instance,String name)throws Exception {
        return instance.getClass().getField(name).getInt(instance);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Supply compiled obfuscated fixtures directory");
        Properties p=new Properties();
        p.setProperty("class.renderer","glq");
        p.setProperty("class.entity","bum");
        p.setProperty("class.vec","fbb");
        p.setProperty("method.pick","a");
        p.setProperty("method.view","g");
        p.setProperty("method.fov","a");
        p.setProperty("field.x","d");
        p.setProperty("field.y","e");
        p.setProperty("field.z","f");
        TouchRay.configure(p);
        Field mappings=ClassicGameHooks.class.getDeclaredField("names");
        mappings.setAccessible(true);
        Properties loaded=(Properties)mappings.get(null);
        loaded.clear();loaded.putAll(p);
        Class<?> transformerClass=Class.forName("com.minecraftjogger.touch.ClassicGameHooks$Transformer");
        Constructor<?> ctor=transformerClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        ClassFileTransformer ray=(ClassFileTransformer)ctor.newInstance();
        MobileTransformer mobile=new MobileTransformer(p);
        GameClasses classes=new GameClasses(Paths.get(args[0]),mobile,ray);
        Class<?> type=classes.loadClass("glq"),vec=classes.loadClass("fbb"),camera=classes.loadClass("fks");
        Object renderer=type.getConstructor().newInstance();
        Method pick=type.getMethod("a",float.class);
        Method fov=type.getMethod("a",camera,float.class,boolean.class);
        Object fakeCamera=camera.getConstructor().newInstance();
        // Load entity and renderer hooks before the first touch; unlike the
        // previous agent, picking must not wait for its first FOV callback.
        classes.loadClass("bum");
        check(!TouchRay.fovSeen,"No frame-derived FOV yet");
        check(ClassicGameHooks.rayReady(),"Finger targeting enabled once both real ray hooks exist");
        TouchRay.target(0,0,1.8,true);
        pick.invoke(renderer,1f);
        check(intField(renderer,"hitBlockX")==0,"Centre touch hits central block");
        TouchRay.target(-.80,0,1.8,true);
        pick.invoke(renderer,1f);
        int left=intField(renderer,"hitBlockX");
        check(left>0,"Left finger hits a different left-side block");
        TouchRay.target(.80,0,1.8,true);
        pick.invoke(renderer,1f);
        int right=intField(renderer,"hitBlockX");
        check(right<0,"Right finger hits the opposite side block");
        check(left!=right,"Two finger locations resolve to distinct blocks");
        TouchRay.target(0,.75,1.8,true);
        pick.invoke(renderer,1f);
        int top=intField(renderer,"hitBlockY");
        TouchRay.target(0,-.75,1.8,true);
        pick.invoke(renderer,1f);
        int bottom=intField(renderer,"hitBlockY");
        check(top!=bottom,"Vertical finger movement changes which block is targeted");
        // Deliberate hold: repeated picks stay on the chosen block while the
        // player's look vector remains completely unchanged.
        TouchRay.target(-.80,0,1.8,true);
        for(int k=0;k<20;k++){
            pick.invoke(renderer,1f);
            check(intField(renderer,"hitBlockX")==left,"Mining hold stays under the finger");
        }
        Object player=type.getField("player").get(renderer);
        Object cameraDirection=player.getClass().getMethod("g",float.class).invoke(player,1f);
        check(vec.getField("d").getDouble(cameraDirection)==0,"Camera yaw unchanged after touch mining");
        check(vec.getField("e").getDouble(cameraDirection)==0,"Camera pitch unchanged after touch mining");
        check(vec.getField("f").getDouble(cameraDirection)==1,"Camera forward direction unchanged");
        TouchRay.clear();
        pick.invoke(renderer,1f);
        check(intField(renderer,"hitBlockX")==0,"Lifting finger resets target to crosshair");
        check(fov.invoke(renderer,fakeCamera,1f,true) instanceof Float,"Observed FOV callback callable");
        check(TouchRay.fovSeen,"Actual world FOV replaces launch-time default");
        System.out.println("FingerBlockPickingTest: "+checks+" checks passed: real obfuscated bytecode picking, two block hits, hold/release, no camera rotation");
    }
}