/* Minecraft Jogger original Android single-window GLFW bridge. MIT licensed.
 * Uses the GLFW public ABI, not its desktop platform code or any launcher glue.
 */
#define GLFW_INCLUDE_NONE
#include "glfw3.h"
#include <jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include <unistd.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO,"JoggerWindow",__VA_ARGS__)
struct GLFWwindow { int width,height,close,focused,cursor; void *user; };
struct GLFWmonitor { int unused; };
static struct GLFWwindow win={1280,720,0,1,GLFW_CURSOR_NORMAL,0};
static struct GLFWmonitor monitor;
static GLFWmonitor *monitors[]={&monitor};
static GLFWvidmode mode={1280,720,8,8,8,60};
static GLFWerrorfun error_cb;
static GLFWkeyfun key_cb; static GLFWcharfun char_cb; static GLFWcharmodsfun charmods_cb;
static GLFWmousebuttonfun mouse_cb; static GLFWcursorposfun cursor_cb; static GLFWscrollfun scroll_cb;
static GLFWwindowfocusfun focus_cb;static GLFWframebuffersizefun fb_cb;static GLFWwindowsizefun size_cb;
static GLFWwindowclosefun close_cb;static GLFWwindowcontentscalefun scale_cb;
static GLFWcursorenterfun enter_cb;static GLFWwindowposfun pos_cb;static GLFWwindowrefreshfun refresh_cb;
static GLFWwindowiconifyfun iconify_cb;static GLFWwindowmaximizefun maximize_cb;static GLFWdropfun drop_cb;
static GLFWmonitorfun monitor_cb;static GLFWjoystickfun joystick_cb;
static pthread_mutex_t mutex=PTHREAD_MUTEX_INITIALIZER;
static ANativeWindow *surface;static EGLDisplay display=EGL_NO_DISPLAY;static EGLSurface egl_surface=EGL_NO_SURFACE;static EGLContext context=EGL_NO_CONTEXT;
static void *renderer;static int error_code;static const char *error_text="";static char *clipboard;
static double mouse_x,mouse_y,time_offset;static int keys[349],buttons[8];
static _Atomic int grabbed,screen_kind=3,gui_w=640,gui_h=360,hook_ready,dirty_surface;
static _Atomic double fovy=70,sensitivity=.5;
static _Atomic double aim_x=.5,aim_y=.5,aim_until;static _Atomic int pending_button=-1,pending_hold;
static _Atomic double aim_yaw,aim_pitch;
static int initialized;
static EGLDisplay (*eGetDisplay)(EGLNativeDisplayType);
static EGLBoolean (*eInitialize)(EGLDisplay,EGLint*,EGLint*);
static EGLBoolean (*eChooseConfig)(EGLDisplay,const EGLint*,EGLConfig*,EGLint,EGLint*);
static EGLBoolean (*eBindAPI)(EGLenum);
static EGLContext (*eCreateContext)(EGLDisplay,EGLConfig,EGLContext,const EGLint*);
static EGLSurface (*eCreateWindowSurface)(EGLDisplay,EGLConfig,EGLNativeWindowType,const EGLint*);
static EGLBoolean (*eMakeCurrent)(EGLDisplay,EGLSurface,EGLSurface,EGLContext);
static EGLBoolean (*eSwapBuffers)(EGLDisplay,EGLSurface);
static EGLBoolean (*eSwapInterval)(EGLDisplay,EGLint);
static EGLBoolean (*eDestroySurface)(EGLDisplay,EGLSurface);
static EGLBoolean (*eDestroyContext)(EGLDisplay,EGLContext);
static EGLConfig config;
static double now(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec+t.tv_nsec/1e9;}
void jogger_error(const char *s){error_code=GLFW_PLATFORM_ERROR;error_text=s;LOG("%s",s);if(error_cb)error_cb(error_code,s);}
void jogger_renderer(const char *path){renderer=dlopen(path,RTLD_NOW|RTLD_GLOBAL);if(!renderer)jogger_error(dlerror());}
void jogger_surface(JNIEnv *env,jobject s,int w,int h){
 pthread_mutex_lock(&mutex);ANativeWindow *n=s?ANativeWindow_fromSurface(env,s):NULL;
 if(surface)ANativeWindow_release(surface);surface=n;win.width=w>0?w:win.width;win.height=h>0?h:win.height;mode.width=win.width;mode.height=win.height;dirty_surface=1;pthread_mutex_unlock(&mutex);
}
typedef struct {int type,a,b;double x,y;} Event;
static Event queue[2048];static unsigned head,tail;
static void push(Event e){pthread_mutex_lock(&mutex);unsigned next=(head+1)%2048;if(next==tail){tail=head; e=(Event){5,0,0,0,0};next=(head+1)%2048;}queue[head]=e;head=next;pthread_mutex_unlock(&mutex);}
void jogger_input(int type,int a,int b,double x,double y){push((Event){type,a,b,x,y});}
int jogger_state(int *s,double *v){s[0]=grabbed;s[1]=screen_kind;s[2]=gui_w;s[3]=gui_h;s[4]=hook_ready;s[5]=win.width;s[6]=win.height;v[0]=fovy;v[1]=sensitivity;return 7;}
void jogger_target(double x,double y,int button,int hold){aim_x=x;aim_y=y;aim_until=now()+(hold?3600:.4);pending_button=button;pending_hold=hold;}
void jogger_cancel_target(void){pending_button=-1;aim_until=0;push((Event){1,0,0,0,0});push((Event){1,1,0,0,0});}
static void fire_pending(void){int b=atomic_exchange(&pending_button,-1);if(b<0)return;push((Event){1,b,1,0,0});if(!pending_hold)push((Event){1,b,0,0,0});}
JNIEXPORT void JNICALL Java_com_wiktor_jogger_agent_MobileAgent_nativeHud(JNIEnv *e,jclass c,jint s,jint w,jint h,jdouble f,jdouble sens){screen_kind=s;gui_w=w;gui_h=h;fovy=f;sensitivity=sens;}
JNIEXPORT jboolean JNICALL Java_com_wiktor_jogger_agent_MobileAgent_nativeAim(JNIEnv *e,jclass c,jdoubleArray out){double a[]={aim_x,aim_y,fovy,(double)win.width/(win.height?win.height:1)};(*e)->SetDoubleArrayRegion(e,out,0,4,a);return grabbed&&aim_until>now();}
JNIEXPORT void JNICALL Java_com_wiktor_jogger_agent_MobileAgent_nativePicked(JNIEnv *e,jclass c){hook_ready=1;fire_pending();}
JNIEXPORT void JNICALL Java_com_wiktor_jogger_agent_MobileAgent_nativeHook(JNIEnv *e,jclass c,jboolean ok){hook_ready=ok;}
#define LOAD_E(name) do { e##name=dlsym(renderer,"egl"#name);if(!e##name)e##name=dlsym(egl,"egl"#name);if(!e##name){jogger_error("Missing EGL entrypoint "#name);return GLFW_FALSE;} } while(0)
int glfwInit(void){if(initialized)return 1;void *egl=dlopen("libEGL.so",RTLD_NOW|RTLD_GLOBAL);if(!renderer){jogger_error("Renderer was not loaded");return 0;}LOAD_E(GetDisplay);LOAD_E(Initialize);LOAD_E(ChooseConfig);LOAD_E(BindAPI);LOAD_E(CreateContext);LOAD_E(CreateWindowSurface);LOAD_E(MakeCurrent);LOAD_E(SwapBuffers);LOAD_E(SwapInterval);LOAD_E(DestroySurface);LOAD_E(DestroyContext);display=eGetDisplay(EGL_DEFAULT_DISPLAY);EGLint a,b;if(!eInitialize(display,&a,&b)){jogger_error("EGL display initialization failed");return 0;}initialized=1;return 1;}
void glfwTerminate(void){if(display!=EGL_NO_DISPLAY){eMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);if(egl_surface!=EGL_NO_SURFACE)eDestroySurface(display,egl_surface);if(context!=EGL_NO_CONTEXT)eDestroyContext(display,context);}context=EGL_NO_CONTEXT;egl_surface=EGL_NO_SURFACE;initialized=0;}
void glfwGetVersion(int*a,int*b,int*c){if(a)*a=3;if(b)*b=3;if(c)*c=8;}
const char *glfwGetVersionString(void){return "3.3.8 Jogger Android independent bridge";}
GLFWerrorfun glfwSetErrorCallback(GLFWerrorfun f){GLFWerrorfun p=error_cb;error_cb=f;return p;}
int glfwGetError(const char **s){if(s)*s=error_text;int n=error_code;error_code=0;return n;}
void glfwDefaultWindowHints(void){} void glfwWindowHint(int k,int v){} void glfwWindowHintString(int k,const char*v){} void glfwInitHint(int k,int v){}
static int attach_surface(void){pthread_mutex_lock(&mutex);if(!surface){pthread_mutex_unlock(&mutex);return 0;}if(egl_surface!=EGL_NO_SURFACE){eMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eDestroySurface(display,egl_surface);}ANativeWindow_setBuffersGeometry(surface,win.width,win.height,0);egl_surface=eCreateWindowSurface(display,config,(EGLNativeWindowType)surface,NULL);dirty_surface=0;pthread_mutex_unlock(&mutex);return egl_surface!=EGL_NO_SURFACE;}
GLFWwindow *glfwCreateWindow(int w,int h,const char*t,GLFWmonitor*m,GLFWwindow*share){
 if(!initialized&&!glfwInit())return NULL;
 const EGLint cfg[]={EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_DEPTH_SIZE,24,EGL_STENCIL_SIZE,8,EGL_SURFACE_TYPE,EGL_WINDOW_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT, EGL_NONE};EGLint count;
 if(!eChooseConfig(display,cfg,&config,1,&count)||!count){jogger_error("No GLES3 window configuration");return NULL;}
 eBindAPI(EGL_OPENGL_ES_API);const EGLint ctx[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE};context=eCreateContext(display,config,EGL_NO_CONTEXT,ctx);
 if(context==EGL_NO_CONTEXT||!attach_surface()){jogger_error("Could not create EGL context/surface");return NULL;}win.close=0;return &win;
}
void glfwDestroyWindow(GLFWwindow*w){win.close=1;grabbed=0;}
void glfwMakeContextCurrent(GLFWwindow*w){if(w){if(dirty_surface&&!attach_surface())return;if(!eMakeCurrent(display,egl_surface,egl_surface,context))jogger_error("EGL makeCurrent failed");}else eMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);}
GLFWwindow*glfwGetCurrentContext(void){return &win;}
void glfwSwapBuffers(GLFWwindow*w){if(dirty_surface){if(!attach_surface()){usleep(16000);return;}eMakeCurrent(display,egl_surface,egl_surface,context);if(fb_cb)fb_cb(&win,win.width,win.height);}if(!eSwapBuffers(display,egl_surface))jogger_error("EGL swap failed");}
void glfwSwapInterval(int v){eSwapInterval(display,v);}
GLFWglproc glfwGetProcAddress(const char*n){void*p=dlsym(renderer,n);if(!p){void*(*lookup)(const char*)=dlsym(renderer,"glXGetProcAddress");if(!lookup)lookup=dlsym(renderer,"eglGetProcAddress");if(lookup)p=lookup(n);}return (GLFWglproc)p;}
int glfwExtensionSupported(const char*n){return 0;}
int glfwWindowShouldClose(GLFWwindow*w){return win.close;}void glfwSetWindowShouldClose(GLFWwindow*w,int v){win.close=v;}
void glfwSetWindowTitle(GLFWwindow*w,const char*t){}void glfwSetWindowIcon(GLFWwindow*w,int n,const GLFWimage*i){}
void glfwGetWindowSize(GLFWwindow*w,int*x,int*y){if(x)*x=win.width;if(y)*y=win.height;}
void glfwGetFramebufferSize(GLFWwindow*w,int*x,int*y){glfwGetWindowSize(w,x,y);}
void glfwSetWindowSize(GLFWwindow*w,int x,int y){if(size_cb)size_cb(&win,win.width,win.height);if(fb_cb)fb_cb(&win,win.width,win.height);}
void glfwGetWindowPos(GLFWwindow*w,int*x,int*y){if(x)*x=0;if(y)*y=0;}void glfwSetWindowPos(GLFWwindow*w,int x,int y){}
void glfwGetWindowFrameSize(GLFWwindow*w,int*l,int*t,int*r,int*b){if(l)*l=0;if(t)*t=0;if(r)*r=0;if(b)*b=0;}
void glfwGetWindowContentScale(GLFWwindow*w,float*x,float*y){if(x)*x=1;if(y)*y=1;}
void glfwSetWindowSizeLimits(GLFWwindow*w,int a,int b,int c,int d){}void glfwSetWindowAspectRatio(GLFWwindow*w,int a,int b){}
int glfwGetWindowAttrib(GLFWwindow*w,int a){switch(a){case GLFW_FOCUSED:return win.focused;case GLFW_VISIBLE:case GLFW_HOVERED:case GLFW_RESIZABLE:case GLFW_DOUBLEBUFFER:return 1;case GLFW_CONTEXT_VERSION_MAJOR:return 3;case GLFW_CONTEXT_VERSION_MINOR:return 3;case GLFW_CLIENT_API:return GLFW_OPENGL_API;case GLFW_OPENGL_PROFILE:return GLFW_OPENGL_CORE_PROFILE;case GLFW_CONTEXT_CREATION_API:return GLFW_EGL_CONTEXT_API;default:return 0;}}
void glfwSetWindowAttrib(GLFWwindow*w,int a,int v){}void glfwShowWindow(GLFWwindow*w){}void glfwHideWindow(GLFWwindow*w){}void glfwFocusWindow(GLFWwindow*w){win.focused=1;}void glfwIconifyWindow(GLFWwindow*w){}void glfwRestoreWindow(GLFWwindow*w){}void glfwMaximizeWindow(GLFWwindow*w){}void glfwRequestWindowAttention(GLFWwindow*w){}
float glfwGetWindowOpacity(GLFWwindow*w){return 1;}void glfwSetWindowOpacity(GLFWwindow*w,float f){}
void glfwSetWindowUserPointer(GLFWwindow*w,void*p){win.user=p;}void*glfwGetWindowUserPointer(GLFWwindow*w){return win.user;}
GLFWmonitor **glfwGetMonitors(int*n){if(n)*n=1;return monitors;}GLFWmonitor*glfwGetPrimaryMonitor(void){return &monitor;}
GLFWmonitor*glfwGetWindowMonitor(GLFWwindow*w){return &monitor;}void glfwSetWindowMonitor(GLFWwindow*w,GLFWmonitor*m,int x,int y,int width,int height,int hz){glfwSetWindowSize(w,width,height);}
const GLFWvidmode*glfwGetVideoMode(GLFWmonitor*m){return &mode;}const GLFWvidmode*glfwGetVideoModes(GLFWmonitor*m,int*n){if(n)*n=1;return &mode;}
const char*glfwGetMonitorName(GLFWmonitor*m){return "Android display";}void glfwGetMonitorPos(GLFWmonitor*m,int*x,int*y){if(x)*x=0;if(y)*y=0;}void glfwGetMonitorWorkarea(GLFWmonitor*m,int*x,int*y,int*w,int*h){glfwGetMonitorPos(m,x,y);glfwGetWindowSize(&win,w,h);}void glfwGetMonitorContentScale(GLFWmonitor*m,float*x,float*y){if(x)*x=1;if(y)*y=1;}void glfwGetMonitorPhysicalSize(GLFWmonitor*m,int*x,int*y){if(x)*x=150;if(y)*y=85;}
void glfwSetInputMode(GLFWwindow*w,int k,int v){if(k==GLFW_CURSOR){win.cursor=v;grabbed=(v==GLFW_CURSOR_DISABLED);if(grabbed)screen_kind=0;}}
int glfwGetInputMode(GLFWwindow*w,int k){return k==GLFW_CURSOR?win.cursor:0;}int glfwRawMouseMotionSupported(void){return 0;}
int glfwGetKey(GLFWwindow*w,int k){return k>=0&&k<349?keys[k]:0;}int glfwGetMouseButton(GLFWwindow*w,int b){return b>=0&&b<8?buttons[b]:0;}int glfwGetKeyScancode(int k){return k;}
const char*glfwGetKeyName(int k,int s){static char name[2];if(k>=32&&k<=126){name[0]=k;name[1]=0;return name;}return NULL;}
void glfwGetCursorPos(GLFWwindow*w,double*x,double*y){if(x)*x=mouse_x;if(y)*y=mouse_y;}
void glfwSetCursorPos(GLFWwindow*w,double x,double y){mouse_x=x;mouse_y=y;if(cursor_cb)cursor_cb(&win,x,y);}
GLFWcursor*glfwCreateStandardCursor(int shape){return (GLFWcursor*)1;}GLFWcursor*glfwCreateCursor(const GLFWimage*i,int x,int y){return (GLFWcursor*)1;}void glfwDestroyCursor(GLFWcursor*c){}void glfwSetCursor(GLFWwindow*w,GLFWcursor*c){}
void glfwSetClipboardString(GLFWwindow*w,const char*s){free(clipboard);clipboard=strdup(s?s:"");}const char*glfwGetClipboardString(GLFWwindow*w){return clipboard?clipboard:"";}
double glfwGetTime(void){return now()+time_offset;}void glfwSetTime(double t){time_offset=t-now();}uint64_t glfwGetTimerValue(void){return (uint64_t)(now()*1e9);}uint64_t glfwGetTimerFrequency(void){return 1000000000ULL;}
int glfwJoystickPresent(int j){return 0;}int glfwJoystickIsGamepad(int j){return 0;}int glfwGetGamepadState(int j,GLFWgamepadstate*s){return 0;}const char*glfwGetJoystickName(int j){return NULL;}const char*glfwGetJoystickGUID(int j){return NULL;}const char*glfwGetGamepadName(int j){return NULL;}const float*glfwGetJoystickAxes(int j,int*n){if(n)*n=0;return NULL;}const unsigned char*glfwGetJoystickButtons(int j,int*n){if(n)*n=0;return NULL;}const unsigned char*glfwGetJoystickHats(int j,int*n){if(n)*n=0;return NULL;}int glfwUpdateGamepadMappings(const char*s){return 1;}void glfwSetJoystickUserPointer(int j,void*p){}void*glfwGetJoystickUserPointer(int j){return NULL;}int glfwVulkanSupported(void){return 0;}
#define CB(name,type,var) type name(GLFWwindow*w,type f){type p=var;var=f;return p;}
CB(glfwSetKeyCallback,GLFWkeyfun,key_cb) CB(glfwSetCharCallback,GLFWcharfun,char_cb) CB(glfwSetCharModsCallback,GLFWcharmodsfun,charmods_cb) CB(glfwSetMouseButtonCallback,GLFWmousebuttonfun,mouse_cb) CB(glfwSetCursorPosCallback,GLFWcursorposfun,cursor_cb) CB(glfwSetScrollCallback,GLFWscrollfun,scroll_cb) CB(glfwSetWindowFocusCallback,GLFWwindowfocusfun,focus_cb) CB(glfwSetFramebufferSizeCallback,GLFWframebuffersizefun,fb_cb) CB(glfwSetWindowSizeCallback,GLFWwindowsizefun,size_cb) CB(glfwSetWindowCloseCallback,GLFWwindowclosefun,close_cb) CB(glfwSetWindowContentScaleCallback,GLFWwindowcontentscalefun,scale_cb) CB(glfwSetCursorEnterCallback,GLFWcursorenterfun,enter_cb) CB(glfwSetWindowPosCallback,GLFWwindowposfun,pos_cb) CB(glfwSetWindowRefreshCallback,GLFWwindowrefreshfun,refresh_cb) CB(glfwSetWindowIconifyCallback,GLFWwindowiconifyfun,iconify_cb) CB(glfwSetWindowMaximizeCallback,GLFWwindowmaximizefun,maximize_cb) CB(glfwSetDropCallback,GLFWdropfun,drop_cb)
GLFWmonitorfun glfwSetMonitorCallback(GLFWmonitorfun f){GLFWmonitorfun p=monitor_cb;monitor_cb=f;return p;}GLFWjoystickfun glfwSetJoystickCallback(GLFWjoystickfun f){GLFWjoystickfun p=joystick_cb;joystick_cb=f;return p;}
void glfwPollEvents(void){if(!hook_ready)fire_pending();for(;;){pthread_mutex_lock(&mutex);if(tail==head){pthread_mutex_unlock(&mutex);break;}Event e=queue[tail];tail=(tail+1)%2048;pthread_mutex_unlock(&mutex);
 switch(e.type){case 0:if(e.a>=0&&e.a<349){keys[e.a]=e.b;if(key_cb)key_cb(&win,e.a,e.a,e.b,(keys[340]?1:0)|(keys[341]?2:0));}break;
 case 1:if(e.a>=0&&e.a<8){buttons[e.a]=e.b;if(mouse_cb)mouse_cb(&win,e.a,e.b,0);}break;
 case 2:if(e.a){mouse_x+=e.x;mouse_y+=e.y;}else{mouse_x=e.x;mouse_y=e.y;}if(cursor_cb)cursor_cb(&win,mouse_x,mouse_y);break;
 case 3:if(char_cb)char_cb(&win,(unsigned)e.a);if(charmods_cb)charmods_cb(&win,(unsigned)e.a,0);break;
 case 4:if(scroll_cb)scroll_cb(&win,e.x,e.y);break;
 case 5:for(int k=0;k<349;k++)if(keys[k]){keys[k]=0;if(key_cb)key_cb(&win,k,k,0,0);}for(int b=0;b<8;b++)if(buttons[b]){buttons[b]=0;if(mouse_cb)mouse_cb(&win,b,0,0);}aim_until=0;pending_button=-1;break;
 case 6:win.focused=e.a;if(focus_cb)focus_cb(&win,e.a);break;
 case 7:win.close=1;if(close_cb)close_cb(&win);break;}}
}
void glfwWaitEvents(void){usleep(8000);glfwPollEvents();}void glfwWaitEventsTimeout(double t){usleep((unsigned)(fmin(fmax(t,0),.05)*1000000));glfwPollEvents();}void glfwPostEmptyEvent(void){}
/* Remaining uncommon desktop APIs are generated by prepare.py with explicit errors. */
