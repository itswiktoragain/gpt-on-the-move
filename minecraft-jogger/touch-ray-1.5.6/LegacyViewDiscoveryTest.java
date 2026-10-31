import com.minecraftjogger.touch.LegacyViewDiscovery;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.*;

public final class LegacyViewDiscoveryTest {
    private static int tests;
    private static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);tests++;}
    private static MethodNode method(String name,String desc){return new MethodNode(Opcodes.ACC_PUBLIC,name,desc,null,null);}
    private static ClassNode node(String name){ClassNode c=new ClassNode();c.name=name;c.superName="java/lang/Object";c.version=52;return c;}
    public static void main(String[] args){
        String vec="Llegacy/Vec;";
        ClassNode entity=node("legacy/Actor");
        MethodNode look=method("getLook","(F)"+vec);
        look.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"legacy/Actor","calculate","(FF)"+vec,false));
        look.instructions.add(new InsnNode(Opcodes.ARETURN));
        MethodNode calc=method("calculate","(FF)"+vec);
        calc.instructions.add(new LdcInsnNode(Float.valueOf(.017453292f)));
        calc.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"legacy/Trig","sin","(F)F",false));
        calc.instructions.add(new LdcInsnNode(Float.valueOf(.017453292f)));
        calc.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"legacy/Trig","cos","(F)F",false));
        calc.instructions.add(new InsnNode(Opcodes.ARETURN));
        MethodNode position=method("getPositionEyes","(F)"+vec);
        position.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"legacy/Actor","interpolate","(FF)"+vec,false));
        position.instructions.add(new InsnNode(Opcodes.ARETURN));
        MethodNode interpolate=method("interpolate","(FF)"+vec);
        interpolate.instructions.add(new InsnNode(Opcodes.ARETURN));
        MethodNode cycle=method("loop","(F)"+vec);
        cycle.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"legacy/Actor","loop","(F)"+vec,false));
        cycle.instructions.add(new InsnNode(Opcodes.ARETURN));
        entity.methods.add(look);entity.methods.add(calc);entity.methods.add(position);entity.methods.add(interpolate);entity.methods.add(cycle);
        Map<String,ClassNode> all=new HashMap<>();all.put(entity.name,entity);
        check(LegacyViewDiscovery.trigonometricChain(all,look),"delegated 1.8/Beta look vector recognized");
        check(LegacyViewDiscovery.trigonometricChain(all,calc),"direct trig recognized");
        check(!LegacyViewDiscovery.trigonometricChain(all,position),"position interpolation excluded");
        check(!LegacyViewDiscovery.trigonometricChain(all,interpolate),"no trig excluded");
        check(!LegacyViewDiscovery.trigonometricChain(all,cycle),"recursive cycle excluded");
        check(!LegacyViewDiscovery.trigonometricChain(Collections.<String,ClassNode>emptyMap(),look),"unresolved call excluded");
        check(!LegacyViewDiscovery.trigonometricChain(null,look),"null map safe");
        check(!LegacyViewDiscovery.trigonometricChain(all,null),"null method safe");
        System.out.println("LegacyViewDiscoveryTest: "+tests+" checks passed");
    }
}
