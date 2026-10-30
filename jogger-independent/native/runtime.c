/* Original Jogger JVM host. MIT. The game runs in the :game process. */
#include <jni.h>
#include <dlfcn.h>
#include <dirent.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <android/log.h>
extern void jogger_surface(JNIEnv*,jobject,int,int);
extern void jogger_renderer(const char*);
extern void jogger_input(int,int,int,double,double);
extern int jogger_state(int*,double*);
extern void jogger_target(double,double,int,int);
extern void jogger_cancel_target(void);
static char *str(JNIEnv *e,jstring s){if(!s)return strdup("");const char *u=(*e)->GetStringUTFChars(e,s,NULL);char*p=strdup(u);(*e)->ReleaseStringUTFChars(e,s,u);return p;}
JNIEXPORT void JNICALL Java_com_wiktor_jogger_Native_surface(JNIEnv*e,jclass c,jobject s,jint w,jint h){jogger_surface(e,s,w,h);}
JNIEXPORT void JNICALL Java_com_wiktor_jogger_Native_input(JNIEnv*e,jclass c,jint t,jint a,jint b,jdouble x,jdouble y){jogger_input(t,a,b,x,y);}
JNIEXPORT jintArray JNICALL Java_com_wiktor_jogger_Native_state(JNIEnv*e,jclass c){int s[7];double v[2];jogger_state(s,v);jintArray a=(*e)->NewIntArray(e,9);jint out[9];for(int i=0;i<7;i++)out[i]=s[i];out[7]=(int)(v[0]*100);out[8]=(int)(v[1]*1000);(*e)->SetIntArrayRegion(e,a,0,9,out);return a;}
JNIEXPORT void JNICALL Java_com_wiktor_jogger_Native_target(JNIEnv*e,jclass c,jdouble x,jdouble y,jint b,jboolean hold){jogger_target(x,y,b,hold);}
JNIEXPORT void JNICALL Java_com_wiktor_jogger_Native_cancelTarget(JNIEnv*e,jclass c){jogger_cancel_target();}
static void preload(const char *dir){DIR*d=opendir(dir);if(!d)return;struct dirent*f;while((f=readdir(d))){if(!strstr(f->d_name,".so"))continue;char p[4096];snprintf(p,sizeof p,"%s/%s",dir,f->d_name);dlopen(p,RTLD_NOW|RTLD_GLOBAL);}closedir(d);}
JNIEXPORT jstring JNICALL Java_com_wiktor_jogger_Native_boot(JNIEnv*art,jclass c,jstring home,jstring natives,jstring game,jstring renderer,jobjectArray opts,jstring main,jobjectArray arguments){
 char*h=str(art,home),*n=str(art,natives),*g=str(art,game),*r=str(art,renderer),*mc=str(art,main);char p[8192];
 snprintf(p,sizeof p,"%s/latest-jogger.log",g);int fd=open(p,O_CREAT|O_TRUNC|O_WRONLY,0600);if(fd>=0){dup2(fd,STDOUT_FILENO);dup2(fd,STDERR_FILENO);close(fd);setbuf(stdout,NULL);setbuf(stderr,NULL);}
 printf("Minecraft Jogger independent JVM host\nJava home: %s\nNative path: %s\n",h,n);
 setenv("JAVA_HOME",h,1);setenv("HOME",g,1);setenv("TMPDIR",g,1);setenv("LIBGL_ES","3",1);setenv("LIBGL_GL","33",1);setenv("MESA_GL_VERSION_OVERRIDE","3.3",0);
 snprintf(p,sizeof p,"%s/lib/server:%s/lib:%s",h,h,n);setenv("LD_LIBRARY_PATH",p,1);chdir(g);
 for(int i=0;i<3;i++){preload(n);snprintf(p,sizeof p,"%s/lib",h);preload(p);snprintf(p,sizeof p,"%s/lib/server",h);preload(p);}
 jogger_renderer(r);snprintf(p,sizeof p,"%s/lib/server/libjvm.so",h);void*lib=dlopen(p,RTLD_NOW|RTLD_GLOBAL);
 if(!lib){const char*err=dlerror();fprintf(stderr,"JVM load failed: %s\n",err);return (*art)->NewStringUTF(art,err?err:"Could not load libjvm");}
 jint(*create)(JavaVM**,void**,void*)=dlsym(lib,"JNI_CreateJavaVM");if(!create)return (*art)->NewStringUTF(art,"OpenJDK JNI_CreateJavaVM not exported");
 int count=(*art)->GetArrayLength(art,opts);JavaVMOption*options=calloc(count,sizeof(JavaVMOption));for(int i=0;i<count;i++){jstring s=(*art)->GetObjectArrayElement(art,opts,i);options[i].optionString=str(art,s);(*art)->DeleteLocalRef(art,s);}
 JavaVMInitArgs init={.version=JNI_VERSION_1_8,.nOptions=count,.options=options,.ignoreUnrecognized=JNI_FALSE};JavaVM*vm=NULL;JNIEnv*env=NULL;int rc=create(&vm,(void**)&env,&init);
 for(int i=0;i<count;i++)free(options[i].optionString);free(options);if(rc!=JNI_OK){snprintf(p,sizeof p,"OpenJDK creation returned %d. See latest-jogger.log",rc);return (*art)->NewStringUTF(art,p);}
 printf("OpenJDK VM created successfully\n");for(char*q=mc;*q;q++)if(*q=='.')*q='/';jclass klass=(*env)->FindClass(env,mc);
 if(!klass){(*env)->ExceptionDescribe(env);return (*art)->NewStringUTF(art,"Game main class could not load; see latest-jogger.log");}
 jmethodID entry=(*env)->GetStaticMethodID(env,klass,"main","([Ljava/lang/String;)V");if(!entry){(*env)->ExceptionDescribe(env);return (*art)->NewStringUTF(art,"Game entrypoint missing");}
 int ac=(*art)->GetArrayLength(art,arguments);jclass stringClass=(*env)->FindClass(env,"java/lang/String");jobjectArray av=(*env)->NewObjectArray(env,ac,stringClass,NULL);
 for(int i=0;i<ac;i++){jstring a=(*art)->GetObjectArrayElement(art,arguments,i);jsize len=(*art)->GetStringLength(art,a);const jchar*u=(*art)->GetStringChars(art,a,NULL);jstring b=(*env)->NewString(env,u,len);(*env)->SetObjectArrayElement(env,av,i,b);(*env)->DeleteLocalRef(env,b);(*art)->ReleaseStringChars(art,a,u);(*art)->DeleteLocalRef(art,a);}
 (*env)->CallStaticVoidMethod(env,klass,entry,av);if((*env)->ExceptionCheck(env)){(*env)->ExceptionDescribe(env);return (*art)->NewStringUTF(art,"Minecraft stopped with an exception. See latest-jogger.log");}
 return (*art)->NewStringUTF(art,"Minecraft exited normally");
}
