#!/usr/bin/env python3
"""Reproducible packaging and original native compilation. Never imports launcher code."""
import pathlib,os,sys,subprocess,urllib.request,zipfile,shutil,json,re,hashlib
P=pathlib.Path(__file__).resolve().parent;O=P/'dependency-output';B=P/'build-tools-local';B.mkdir(exist_ok=True)
N=pathlib.Path(os.environ['ANDROID_HOME'])/'ndk/27.3.13750724';T=N/'toolchains/llvm/prebuilt/linux-x86_64/bin'
AS=P/'app/src/main/assets';AS.mkdir(parents=True,exist_ok=True);JN=P/'app/src/main/jniLibs';JN.mkdir(parents=True,exist_ok=True)
def run(*args,**kw):subprocess.run(list(map(str,args)),check=True,**kw)
def jar(group,name,version):
 p=B/(name+'-'+version+'.jar');base='https://repo.maven.apache.org/maven2/'+group.replace('.','/')+'/'+name+'/'+version+'/'+p.name
 if not p.exists():
  with urllib.request.urlopen(base,timeout=60) as r,p.open('wb') as f:shutil.copyfileobj(r,f)
 with urllib.request.urlopen(base+'.sha1',timeout=30) as r:h=r.read().decode().strip().split()[0]
 if hashlib.sha1(p.read_bytes()).hexdigest()!=h:raise RuntimeError('Maven hash mismatch: '+p.name)
 return p
run(sys.executable,P/'integrate.py')
for abi in ('arm64-v8a','x86_64'):
 dest=JN/abi;dest.mkdir(exist_ok=True)
 for name in ('liblwjgl.so','liblwjgl_opengl.so','liblwjgl_stb.so','liblwjgl_tinyfd.so','libopenal.so','libmobileglues.so','libc++_shared.so'):
  src=O/'jniLibs'/abi/name
  if not src.is_file():raise RuntimeError('Native dependency missing: '+str(src))
  shutil.copy(src,dest/name)
 shutil.copy(O/('runtime-'+abi+'.zip'),AS/('runtime-'+abi+'.zip'))
shutil.copytree(O/'licenses',AS/'licenses',dirs_exist_ok=True)
shutil.copy(O/'DEPENDENCIES.json',AS/'DEPENDENCIES.json')
(AS/'engine.json').write_text(json.dumps({'renderer':'libmobileglues.so','jvm':21,'bridge':'Jogger original GLFW-compatible Android bridge'}))
(AS/'CREDITS.txt').write_text('''Minecraft Jogger 0.3 - independent implementation

Launcher, instance/download/authentication logic, Android controls, JVM host and Android GLFW-compatible window/input bridge: original Jogger code (MIT).
No Pojav or Amethyst launcher source, launcher APK, native launcher glue or app installation is included.

Separate dependencies:
OpenJDK 21 for Android: built by AngelAuraMC/angelauramc-openjdk-build. This is the Java runtime only. OpenJDK GPLv2 with Classpath Exception; runtime legal notices included in its archive.
LWJGL 3.3.3: lwjgl.org / LWJGL/lwjgl3, BSD license.
libffi 3.4.4: libffi/libffi, MIT license.
OpenAL Soft 1.23.1: kcat/openal-soft, LGPL.
MobileGlues 2.0.0: MobileGL-Dev/MobileGlues, LGPL-2.1. Only libmobileglues.so is extracted, not its plugin application.
GLFW 3.3.8 public API header: glfw/glfw, zlib license. Its desktop window implementation is not used.
ASM 9.7.1: OW2 ASM, BSD-3-Clause.
FreeType: the separately licensed FreeType library included in the OpenJDK runtime; its notices are retained there.
Android NDK C++ runtime: Android LLVM libc++, LLVM exceptions license.

Full dependency source locations/hashes: packaged DEPENDENCIES.json and repository build scripts. Source: https://github.com/itswiktoragain/gpt-on-the-move/tree/jogger-independent/jogger-independent

Minecraft is downloaded from Mojang's servers after installation. Game widget textures are loaded from the user's installed client, not redistributed here. A licensed Java account is needed for full play. The official unmodified demo remains available.

Microsoft sign-in requires a public-client application registration configured in Settings, unless supplied by the builder. This build does not borrow another launcher's OAuth identity.

Not an official Minecraft product. Not associated with Mojang or Microsoft.
''')
header=(O/'glfw3.h').read_text();source=(P/'native/glfw_jogger.c').read_text()
prototypes=re.findall(r'GLFWAPI\s+([^;]+\b(glfw\w+)\s*\([^;]*\));',header,re.S)
missing=['#define GLFW_INCLUDE_NONE\n#include "glfw3.h"\n#include <stddef.h>\nextern void jogger_error(const char*);\n']
for signature,name in prototypes:
 if re.search(r'\b'+re.escape(name)+r'\s*\(',source) or re.search(r'CB\('+re.escape(name)+r',',source):continue
 if 'Vk' in signature:continue
 signature=' '.join(signature.split());ret=signature[:signature.index(name)].strip()
 if name=='glfwGetRequiredInstanceExtensions':body='if(count)*count=0;return NULL;'
 else:body='jogger_error("Unsupported Android desktop API: '+name+'");'+('return;' if ret=='void' else 'return 0;')
 missing.append(signature+' {'+body+'}\n')
(P/'native/desktop_unsupported.c').write_text(''.join(missing))
print('Explicit desktop-only exports:',len(missing)-1)
for abi,tr in [('arm64-v8a','aarch64-linux-android'),('x86_64','x86_64-linux-android')]:
 cc=T/(tr+'29-clang');out=JN/abi
 common=['-shared','-fPIC','-O2','-std=c11','-D_GNU_SOURCE','-Wl,-z,max-page-size=16384','-I'+str(O)]
 run(cc,*common,P/'native/glfw_jogger.c',P/'native/desktop_unsupported.c','-Wl,-soname,libglfw_jogger.so','-o',out/'libglfw_jogger.so','-landroid','-llog','-ldl','-lm','-latomic')
 run(cc,*common,P/'native/runtime.c','-Wl,-soname,libjogger.so','-o',out/'libjogger.so','-L'+str(out),'-lglfw_jogger','-landroid','-llog','-ldl','-lm')
 run(T/'llvm-readelf','-d',out/'libjogger.so')
 bindings=(P/'dependencies/lwjgl/modules/lwjgl/glfw/src/generated/java/org/lwjgl/glfw/GLFW.java').read_text()
 required=set(re.findall(r'apiGetFunctionAddress\(GLFW,\s*"(glfw\w+)"',bindings))
 exported=subprocess.check_output([str(T/'llvm-nm'),'-D','--defined-only',str(out/'libglfw_jogger.so')],text=True)
 symbols={line.split()[-1] for line in exported.splitlines() if line.strip()}
 if required-symbols:raise RuntimeError('Missing GLFW ABI exports: '+', '.join(sorted(required-symbols)))
 print('PASS:',abi,len(required),'required GLFW ABI exports')
asm=[jar('org.ow2.asm',m,'9.7.1') for m in ('asm','asm-commons','asm-tree')]
lwjgl=[jar('org.lwjgl',m,'3.3.3') for m in ('lwjgl','lwjgl-glfw','lwjgl-opengl')]
classes=B/'classes';classes.mkdir(exist_ok=True)
cp=os.pathsep.join(map(str,asm+lwjgl));sources=list((P/'agent/src').rglob('*.java'))
run('javac','--release','8','-cp',cp,'-d',classes,*sources)
manifest='Manifest-Version: 1.0\nPremain-Class: com.wiktor.jogger.agent.MobileAgent\nCan-Retransform-Classes: true\nImplementation-Title: Minecraft Jogger original touch agent\nImplementation-Version: 0.3.0\n\n'
with zipfile.ZipFile(AS/'jogger-agent.jar','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('META-INF/MANIFEST.MF',manifest)
 for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes))
 seen=set()
 for a in asm:
  with zipfile.ZipFile(a) as src:
   for name in src.namelist():
    if 'license' in name.lower() or 'notice' in name.lower():
     dest=AS/'licenses'/a.stem/pathlib.Path(name).name;dest.parent.mkdir(parents=True,exist_ok=True)
     if not name.endswith('/'):dest.write_bytes(src.read(name))
    if not name.startswith('org/objectweb/asm/') or name in seen:continue
    z.writestr(name,src.read(name));seen.add(name)
(AS/'probe-libs').mkdir(exist_ok=True)
for f in lwjgl:shutil.copy(f,AS/'probe-libs'/f.name)
tests=B/'tests';tests.mkdir(exist_ok=True)
run('javac','--release','8','-cp',str(AS/'jogger-agent.jar'),'-d',tests,P/'app/src/main/java/com/wiktor/jogger/TouchGesture.java',P/'tests/JoggerTests.java',P/'tests/AgentTransformTest.java')
run('java','-cp',os.pathsep.join([str(tests),str(AS/'jogger-agent.jar')]),'JoggerTests')
run('java','-Xverify:all','-cp',os.pathsep.join([str(tests),str(AS/'jogger-agent.jar')]),'AgentTransformTest')
print('Original native engine and agent packaged successfully')
