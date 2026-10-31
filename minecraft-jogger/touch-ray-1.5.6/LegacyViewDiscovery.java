package com.minecraftjogger.touch;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Safely find an indirect look-vector calculation in old Minecraft bytecode. */
public final class LegacyViewDiscovery {
    private LegacyViewDiscovery() { }

    public static boolean trigonometricChain(Map<String, ClassNode> all, MethodNode candidate) {
        if (all == null || candidate == null || Type.getReturnType(candidate.desc).getSort() != Type.OBJECT)
            return false;
        return explore(all, candidate, Type.getReturnType(candidate.desc).getDescriptor(),
                new HashSet<String>(), 0);
    }

    private static boolean explore(Map<String, ClassNode> all, MethodNode method, String vectorDesc,
                                   Set<String> visited, int depth) {
        if (depth > 3 || method == null || !Type.getReturnType(method.desc).getDescriptor().equals(vectorDesc))
            return false;
        if (directTrig(method)) return true;
        for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) i;
            if (!Type.getReturnType(call.desc).getDescriptor().equals(vectorDesc)) continue;
            String key = call.owner + '#' + call.name + call.desc;
            if (!visited.add(key)) continue;
            MethodNode implementation = findMethod(all, call.owner, call.name, call.desc);
            if (explore(all, implementation, vectorDesc, visited, depth + 1)) return true;
        }
        return false;
    }

    private static MethodNode findMethod(Map<String, ClassNode> all, String owner, String name, String desc) {
        for (int depth = 0; owner != null && depth < 8; depth++) {
            ClassNode c = all.get(owner);
            if (c == null) return null;
            for (MethodNode m : c.methods)
                if (m.name.equals(name) && m.desc.equals(desc)) return m;
            owner = c.superName;
        }
        return null;
    }

    private static boolean directTrig(MethodNode m) {
        int floatingCalls = 0;
        boolean radians = false;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) i;
                if (call.desc.equals("(F)F") || call.name.equals("sin") || call.name.equals("cos"))
                    floatingCalls++;
            }
            if (i instanceof LdcInsnNode && ((LdcInsnNode) i).cst instanceof Number) {
                double n = ((Number) ((LdcInsnNode) i).cst).doubleValue();
                if (Math.abs(Math.abs(n) - 0.017453292) < .00001 ||
                        Math.abs(Math.abs(n) - Math.PI) < .00001) radians = true;
            }
        }
        return radians && floatingCalls >= 2;
    }
}
