#!/usr/bin/env python3
"""Apply frame-safe FOV capture after classic-integrate.py. Keeps original frames/types."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
p=root/'jogger_touch_agent/src/main/java/com/minecraftjogger/touch/ClassicGameHooks.java'
s=p.read_text()
def change(old,new):
 global s
 if old not in s: raise RuntimeError('FOV hotfix anchor absent: '+old[:90])
 s=s.replace(old,new)
change('                final boolean[] changed={false};', '''                // Optimizers can discard or reuse argument slots before a return.
                // Keep the world-FOV flag in a fresh local, after ALL original locals.
                // Extend expanded stack maps rather than resolving Minecraft's hierarchy.
                final Map<String,Integer> maxLocals = new HashMap<>();
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions) {
                        if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return null;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMaxs(int stack,int locals) { maxLocals.put(method + desc, locals); }
                        };
                    }
                },ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                final boolean[] changed={false};''')
change('final boolean fov=renderer&&','final boolean fov=maxLocals.containsKey(method+desc)&&renderer&&')
change('final int boolSlot=slot-1;','final int boolSlot=slot-1;\n                        final int savedBoolSlot=fov?maxLocals.get(method+desc):-1;')
change('@Override public void visitCode(){super.visitCode();if(pick)', '''@Override public void visitCode(){super.visitCode();
                                if(fov){super.visitVarInsn(Opcodes.ILOAD,boolSlot);super.visitVarInsn(Opcodes.ISTORE,savedBoolSlot);}
                                if(pick)''')
change('                            @Override public void visitInsn(int opcode)', '''                            @Override public void visitFrame(int type,int nLocal,Object[] local,int nStack,Object[] stack) {
                                if(!fov){super.visitFrame(type,nLocal,local,nStack,stack);return;}
                                if(type!=Opcodes.F_NEW)throw new IllegalArgumentException("Expanded frames required");
                                int usedSlots=0;
                                for(int i=0;i<nLocal;i++)usedSlots+=(local[i]==Opcodes.LONG||local[i]==Opcodes.DOUBLE)?2:1;
                                if(usedSlots>savedBoolSlot)throw new IllegalArgumentException("Invalid original local count");
                                int count=nLocal+(savedBoolSlot-usedSlots)+1;
                                Object[] locals=Arrays.copyOf(local,count);
                                Arrays.fill(locals,nLocal,count,Opcodes.TOP);
                                locals[count-1]=Opcodes.INTEGER;
                                super.visitFrame(type,count,locals,nStack,stack);
                            }
                            @Override public void visitMaxs(int stack,int locals) {super.visitMaxs(stack,fov?Math.max(locals,savedBoolSlot+1):locals);}
                            @Override public void visitInsn(int opcode)''')
change('super.visitVarInsn(Opcodes.ILOAD,boolSlot);super.visitMethodInsn','super.visitVarInsn(Opcodes.ILOAD,savedBoolSlot);super.visitMethodInsn')
change('                },0);','                },ClassReader.EXPAND_FRAMES);')
p.write_text(s)
# Separate APK identity because 1.1.0 was signed with a temporary CI key.
# Existing installs are repaired in place with the accompanying repair installer,
# not by uninstalling or bypassing Android's signature checks.
for folder in [root/'app_pojavlauncher']:
 for path in folder.rglob('*'):
  if path.is_file() and path.suffix in ('.java','.xml','.gradle'):
   text=path.read_text().replace('com.wiktor.minecraftjogger.classic','com.wiktor.minecraftjogger.classic.repaired')
   text=text.replace('Minecraft Jogger Classic"','Minecraft Jogger Classic Fixed"')
   text=text.replace('versionCode 11000','versionCode 11001').replace('1.1.0-classic','1.1.1-classic')
   path.write_text(text)
print('Applied frame-safe FOV capture. All native Minecraft verification remains enabled.')
