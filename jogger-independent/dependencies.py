#!/usr/bin/env python3
"""Compile upstream libraries, not a launcher. Source/license inventory is packaged."""
import os,json,pathlib,subprocess,urllib.request,hashlib,tarfile,zipfile,shutil
P=pathlib.Path(__file__).resolve().parent; D=P/'dependencies'; O=P/'dependency-output'
D.mkdir(exist_ok=True);O.mkdir(exist_ok=True)
N=pathlib.Path(os.environ['ANDROID_HOME'])/'ndk/27.3.13750724';T=N/'toolchains/llvm/prebuilt/linux-x86_64/bin';records=[]
def run(*a,**kw):subprocess.run(list(map(str,a)),check=True,**kw)
def get(url,p,sha=None):
 p=pathlib.Path(p);p.parent.mkdir(parents=True,exist_ok=True)
 if not p.exists():
  with urllib.request.urlopen(url,timeout=180) as r,p.open('wb') as f:shutil.copyfileobj(r,f)
 h=hashlib.sha256(p.read_bytes()).hexdigest()
 if sha and h!=sha:raise RuntimeError('Checksum mismatch: '+str(p))
 records.append(dict(url=url,file=p.name,sha256=h));return p

def clone(repo,tag,name):
 p=D/name
 if not p.exists():run('git','clone','--depth=1','--branch',tag,'https://github.com/'+repo+'.git',p)
 sha=subprocess.check_output(['git','-C',str(p),'rev-parse','HEAD'],text=True).strip();records.append(dict(repository=repo,ref=tag,commit=sha))
 for pattern in ['*LICENSE*','COPYING*']:
  for f in p.glob(pattern):
   if f.is_file():q=O/'licenses'/name/f.name;q.parent.mkdir(parents=True,exist_ok=True);shutil.copy(f,q)
 return p
L=clone('LWJGL/lwjgl3','3.3.3','lwjgl');F=clone('libffi/libffi','v3.4.4','libffi');A=clone('kcat/openal-soft','1.23.1','openal');G=clone('glfw/glfw','3.3.8','glfw-headers')
shutil.copy(G/'include/GLFW/glfw3.h',O/'glfw3.h')
M=get('https://github.com/MobileGL-Dev/MobileGlues-release/releases/download/V2.0.0/MobileGlues_2.0.0.apk',D/'renderer-container.apk','a7e1eb29731fdece7ab3af7fb00692b856f1808a7544436e7b3eb6c08355581a')
get('https://raw.githubusercontent.com/MobileGL-Dev/MobileGlues/main/LICENSE',O/'licenses/mobileglues/LICENSE')
with zipfile.ZipFile(M) as z:
 for n in z.namelist():
  if n in ('lib/arm64-v8a/libmobileglues.so','lib/x86_64/libmobileglues.so'):
   p=O/'jniLibs'/pathlib.Path(n).relative_to('lib');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n));print('Renderer library:',n,flush=True)
C=r'''cmake_minimum_required(VERSION 3.22)
project(jogger_deps C)
set(M "${LWJGL}/modules/lwjgl")
include_directories("${M}/core/src/main/c" "${M}/core/src/main/c/linux" "${M}/core/src/main/c/libffi")
add_compile_definitions(LWJGL_LINUX _GNU_SOURCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
 add_compile_definitions(LWJGL_ARM64)
 include_directories("${M}/core/src/main/c/libffi/aarch64")
else()
 add_compile_definitions(LWJGL_X64 X86_64)
 include_directories("${M}/core/src/main/c/libffi/x86")
endif()
add_compile_options(-O2 -fPIC -Wno-incompatible-function-pointer-types -Wno-implicit-function-declaration -D__errno_location=__errno)
file(GLOB CORE "${M}/core/src/main/c/*.c" "${M}/core/src/generated/c/*.c")
file(GLOB DL "${M}/core/src/generated/c/linux/*DynamicLinkLoader.c")
add_library(lwjgl SHARED ${CORE} ${DL})
target_link_libraries(lwjgl "${FFI}/lib/libffi.a" dl m)
foreach(mod opengl stb tinyfd)
 file(GLOB CS "${M}/${mod}/src/generated/c/*.c")
 list(FILTER CS EXCLUDE REGEX "WGL\\.c$")
 if(mod STREQUAL "tinyfd")
  file(GLOB EXTRA "${M}/${mod}/src/main/c/*.c")
 else()
  set(EXTRA "")
 endif()
 add_library(lwjgl_${mod} SHARED ${CS} ${EXTRA})
 target_include_directories(lwjgl_${mod} PRIVATE "${M}/${mod}/src/main/c")
 target_link_libraries(lwjgl_${mod} dl m)
endforeach()
'''
S=D/'cmake';S.mkdir(exist_ok=True);(S/'CMakeLists.txt').write_text(C)
for abi,arch,h in [('arm64-v8a','arm64','8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe'),('x86_64','x86_64','cb88723961f5f9ad63afa1f212eb199816c27cabfd7dc66567bde1d8fb69713b')]:
 print('BUILD',abi,flush=True)
 p=get('https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre21/jre21-android-'+arch+'.tar.xz',D/('jre-'+arch+'.tar.xz'),h)
 if not (O/('runtime-'+abi+'.zip')).exists():
  with tarfile.open(p) as t,zipfile.ZipFile(O/('runtime-'+abi+'.zip'),'w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
   ms=t.getmembers();rs=[m.name for m in ms if m.isfile() and (m.name=='release' or m.name.endswith('/release'))];root=rs[0][:-7] if rs else ''
   for m in ms:
    name=m.name[len(root):] if m.name.startswith(root) else m.name
    while name.startswith('./'):name=name[2:]
    if not name or name.startswith('/') or '..' in pathlib.PurePosixPath(name).parts:continue
    if m.isfile() or m.issym() or m.islnk():
     f=t.extractfile(m)
     if f:z.writestr(name,f.read())
 out=O/'jniLibs'/abi;out.mkdir(parents=True,exist_ok=True);tr='aarch64-linux-android' if abi=='arm64-v8a' else 'x86_64-linux-android';prefix=D/('ffi-'+abi)
 if not (prefix/'lib/libffi.a').exists():
  if not (F/'configure').exists():run('./autogen.sh',cwd=F)
  b=D/('ffi-build-'+abi);b.mkdir(exist_ok=True);e=dict(os.environ,CC=str(T/(tr+'29-clang')),CXX=str(T/(tr+'29-clang++')),AR=str(T/'llvm-ar'),RANLIB=str(T/'llvm-ranlib'),NM=str(T/'llvm-nm'),OBJDUMP=str(T/'llvm-objdump'),READELF=str(T/'llvm-readelf'),STRIP=str(T/'llvm-strip'),CFLAGS='-O2 -fPIC')
  run(F/'configure','--host='+tr,'--prefix='+str(prefix),'--disable-shared','--enable-static','--with-pic','--disable-exec-static-tramp','--disable-docs',cwd=b,env=e);run('make','-j3',cwd=b,env=e);run('make','install',cwd=b,env=e)
 common=['-DCMAKE_TOOLCHAIN_FILE='+str(N/'build/cmake/android.toolchain.cmake'),'-DANDROID_ABI='+abi,'-DANDROID_PLATFORM=android-29','-DCMAKE_BUILD_TYPE=Release','-DANDROID_STL=c++_shared','-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384']
 b=D/('lwjgl-build-'+abi);run('cmake','-S',S,'-B',b,*common,'-DLWJGL='+str(L),'-DFFI='+str(prefix));run('cmake','--build',b,'--parallel','3')
 for f in b.glob('liblwjgl*.so'):shutil.copy(f,out/f.name)
 b=D/('al-build-'+abi);run('cmake','-S',A,'-B',b,*common,'-DALSOFT_UTILS=OFF','-DALSOFT_EXAMPLES=OFF','-DALSOFT_TESTS=OFF','-DALSOFT_BACKEND_OPENSL=ON','-DALSOFT_BACKEND_OBOE=OFF','-DALSOFT_REQUIRE_OPENSL=ON');run('cmake','--build',b,'--parallel','3')
 for f in b.glob('libopenal.so*'):
  if f.is_file() and not f.is_symlink():shutil.copy(f,out/'libopenal.so');break
 shutil.copy(N/('toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/'+tr+'/libc++_shared.so'),out/'libc++_shared.so')
 for f in out.glob('*.so'):run(T/'llvm-readelf','-d',f)
(O/'DEPENDENCIES.json').write_text(json.dumps(records,indent=2))
