/* Minecraft Jogger original process-wide engine state. MIT.
 * Android ART and an embedded OpenJDK can dlopen the same ELF in different
 * linker namespaces. Their static variables are then distinct. A process-local
 * registry publishes one state allocation so both copies use the same window,
 * event queue, renderer, and callbacks. No private linker APIs, namespace
 * bypass library, other launcher, or cross-process pointer is involved.
 * ART loads this library before starting OpenJDK. Later namespace copies reuse it.
 */
#include <stdint.h>
#include <inttypes.h>
typedef struct { int type, a, b; double x, y; } Event;
typedef struct {
 uint64_t magic; pid_t pid; size_t size;
 struct GLFWwindow win;
 struct GLFWmonitor monitor;
 GLFWmonitor *monitors[1];
 GLFWvidmode mode;
 GLFWerrorfun error_cb;
 GLFWkeyfun key_cb;
 GLFWcharfun char_cb;
 GLFWcharmodsfun charmods_cb;
 GLFWmousebuttonfun mouse_cb;
 GLFWcursorposfun cursor_cb;
 GLFWscrollfun scroll_cb;
 GLFWwindowfocusfun focus_cb;
 GLFWframebuffersizefun fb_cb;
 GLFWwindowsizefun size_cb;
 GLFWwindowclosefun close_cb;
 GLFWwindowcontentscalefun scale_cb;
 GLFWcursorenterfun enter_cb;
 GLFWwindowposfun pos_cb;
 GLFWwindowrefreshfun refresh_cb;
 GLFWwindowiconifyfun iconify_cb;
 GLFWwindowmaximizefun maximize_cb;
 GLFWdropfun drop_cb;
 GLFWmonitorfun monitor_cb;
 GLFWjoystickfun joystick_cb;
 pthread_mutex_t mutex;
 ANativeWindow *surface;
 EGLDisplay display;
 EGLSurface egl_surface;
 EGLContext context;
 EGLConfig config;
 void *renderer;
 int error_code;
 const char *error_text;
 char *clipboard;
 double mouse_x, mouse_y, time_offset;
 int keys[349], buttons[8];
 _Atomic int grabbed, screen_kind, gui_w, gui_h, hook_ready, dirty_surface;
 _Atomic double fovy, sensitivity, aim_x, aim_y, aim_until, aim_yaw, aim_pitch;
 _Atomic int pending_button, pending_hold;
 int initialized;
 Event queue[2048]; unsigned head, tail;
 EGLDisplay (*eGetDisplay)(EGLNativeDisplayType);
 EGLBoolean (*eInitialize)(EGLDisplay, EGLint *, EGLint *);
 EGLBoolean (*eChooseConfig)(EGLDisplay, const EGLint *, EGLConfig *, EGLint, EGLint *);
 EGLBoolean (*eBindAPI)(EGLenum);
 EGLContext (*eCreateContext)(EGLDisplay, EGLConfig, EGLContext, const EGLint *);
 EGLSurface (*eCreateWindowSurface)(EGLDisplay, EGLConfig, EGLNativeWindowType, const EGLint *);
 EGLBoolean (*eMakeCurrent)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
 EGLBoolean (*eSwapBuffers)(EGLDisplay, EGLSurface);
 EGLBoolean (*eSwapInterval)(EGLDisplay, EGLint);
 EGLBoolean (*eDestroySurface)(EGLDisplay, EGLSurface);
 EGLBoolean (*eDestroyContext)(EGLDisplay, EGLContext);
} JoggerWindowState;
static JoggerWindowState *J;
#define JOGGER_STATE_MAGIC UINT64_C(0x4a6f676765723034)
static int readable_registry(uintptr_t address) {
 if (!address || address > UINTPTR_MAX - sizeof(JoggerWindowState)) return 0;
 FILE *maps = fopen("/proc/self/maps", "r");
 if (!maps) return 0;
 char line[512], perms[5]; uintptr_t first, last; int valid = 0;
 while (fgets(line, sizeof line, maps)) {
  if (sscanf(line, "%" SCNxPTR "-%" SCNxPTR " %4s", &first, &last, perms) == 3
      && perms[0] == 'r' && perms[1] == 'w' && address >= first
      && address + sizeof(JoggerWindowState) <= last) { valid = 1; break; }
 }
 fclose(maps); return valid;
}
__attribute__((constructor)) static void attach_jogger_state(void) {
 const char *registry = getenv("JOGGER_WINDOW_STATE_V1");
 long pid = 0; uintptr_t address = 0; size_t bytes = 0;
 if (registry && sscanf(registry, "%ld:%" SCNxPTR ":%zu", &pid, &address, &bytes) == 3
     && pid == (long)getpid() && bytes == sizeof(JoggerWindowState) && readable_registry(address)) {
  JoggerWindowState *existing = (JoggerWindowState *)address;
  if (existing->magic == JOGGER_STATE_MAGIC && existing->pid == getpid()
      && existing->size == sizeof(JoggerWindowState)) {
   J = existing; LOG("Attached to the existing Jogger process-wide window state"); return;
  }
 }
 J = calloc(1, sizeof *J); if (!J) abort();
 J->magic = JOGGER_STATE_MAGIC; J->pid = getpid(); J->size = sizeof *J;
 J->win = (struct GLFWwindow){1280, 720, 0, 1, GLFW_CURSOR_NORMAL, NULL};
 J->monitors[0] = &J->monitor; J->mode = (GLFWvidmode){1280, 720, 8, 8, 8, 60};
 J->display = EGL_NO_DISPLAY; J->egl_surface = EGL_NO_SURFACE; J->context = EGL_NO_CONTEXT;
 J->error_text = ""; pthread_mutex_init(&J->mutex, NULL);
 atomic_init(&J->grabbed, 0); atomic_init(&J->screen_kind, 3);
 atomic_init(&J->gui_w, 640); atomic_init(&J->gui_h, 360);
 atomic_init(&J->hook_ready, 0); atomic_init(&J->dirty_surface, 0);
 atomic_init(&J->fovy, 70); atomic_init(&J->sensitivity, .5);
 atomic_init(&J->aim_x, .5); atomic_init(&J->aim_y, .5); atomic_init(&J->aim_until, 0);
 atomic_init(&J->aim_yaw, 0); atomic_init(&J->aim_pitch, 0);
 atomic_init(&J->pending_button, -1); atomic_init(&J->pending_hold, 0);
 char value[96];
 snprintf(value, sizeof value, "%ld:%" PRIxPTR ":%zu", (long)getpid(), (uintptr_t)J, sizeof *J);
 if (setenv("JOGGER_WINDOW_STATE_V1", value, 1) != 0) abort();
 LOG("Created Jogger process-wide window state");
}
#define win (J->win)
#define monitor (J->monitor)
#define monitors (J->monitors)
#define mode (J->mode)
#define error_cb (J->error_cb)
#define key_cb (J->key_cb)
#define char_cb (J->char_cb)
#define charmods_cb (J->charmods_cb)
#define mouse_cb (J->mouse_cb)
#define cursor_cb (J->cursor_cb)
#define scroll_cb (J->scroll_cb)
#define focus_cb (J->focus_cb)
#define fb_cb (J->fb_cb)
#define size_cb (J->size_cb)
#define close_cb (J->close_cb)
#define scale_cb (J->scale_cb)
#define enter_cb (J->enter_cb)
#define pos_cb (J->pos_cb)
#define refresh_cb (J->refresh_cb)
#define iconify_cb (J->iconify_cb)
#define maximize_cb (J->maximize_cb)
#define drop_cb (J->drop_cb)
#define monitor_cb (J->monitor_cb)
#define joystick_cb (J->joystick_cb)
#define mutex (J->mutex)
#define surface (J->surface)
#define display (J->display)
#define egl_surface (J->egl_surface)
#define context (J->context)
#define config (J->config)
#define renderer (J->renderer)
#define error_code (J->error_code)
#define error_text (J->error_text)
#define clipboard (J->clipboard)
#define mouse_x (J->mouse_x)
#define mouse_y (J->mouse_y)
#define time_offset (J->time_offset)
#define keys (J->keys)
#define buttons (J->buttons)
#define grabbed (J->grabbed)
#define screen_kind (J->screen_kind)
#define gui_w (J->gui_w)
#define gui_h (J->gui_h)
#define hook_ready (J->hook_ready)
#define dirty_surface (J->dirty_surface)
#define fovy (J->fovy)
#define sensitivity (J->sensitivity)
#define aim_x (J->aim_x)
#define aim_y (J->aim_y)
#define aim_until (J->aim_until)
#define pending_button (J->pending_button)
#define pending_hold (J->pending_hold)
#define aim_yaw (J->aim_yaw)
#define aim_pitch (J->aim_pitch)
#define initialized (J->initialized)
#define queue (J->queue)
#define head (J->head)
#define tail (J->tail)
#define eGetDisplay (J->eGetDisplay)
#define eInitialize (J->eInitialize)
#define eChooseConfig (J->eChooseConfig)
#define eBindAPI (J->eBindAPI)
#define eCreateContext (J->eCreateContext)
#define eCreateWindowSurface (J->eCreateWindowSurface)
#define eMakeCurrent (J->eMakeCurrent)
#define eSwapBuffers (J->eSwapBuffers)
#define eSwapInterval (J->eSwapInterval)
#define eDestroySurface (J->eDestroySurface)
#define eDestroyContext (J->eDestroyContext)
