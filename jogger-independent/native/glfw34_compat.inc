/* Original Jogger GLFW 3.4 ABI additions. MIT.
 * Android is represented by the non-desktop/null platform identifier; all
 * actual window/context operations continue through Jogger's Android bridge.
 */
#ifndef GLFW_PLATFORM_NULL
#define GLFW_PLATFORM_NULL 0x00060005
#endif
#ifndef GLFW_ANY_PLATFORM
#define GLFW_ANY_PLATFORM 0x00060000
#endif

/* Jogger has one statically allocated window. Allocator replacement is not
 * supported, rather than pretending the supplied callbacks are being used. */
void glfwInitAllocator(const void *allocator) {
    if (allocator != NULL) jogger_error("Custom GLFW allocators are not supported by Jogger's Android window bridge");
}
int glfwGetPlatform(void) { return GLFW_PLATFORM_NULL; }
int glfwPlatformSupported(int platform) { return platform == GLFW_PLATFORM_NULL; }
