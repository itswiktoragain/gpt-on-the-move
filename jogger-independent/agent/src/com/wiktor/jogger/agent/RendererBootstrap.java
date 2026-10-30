package com.wiktor.jogger.agent;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

/** Original bridge wiring. The window bridge owns the single renderer instance. */
public final class RendererBootstrap {
    private RendererBootstrap() {}
    public static synchronized void initialize() {
        // A second dlopen in another linker namespace could probe and clear
        // the active EGL context. Route all GL lookups through the window owner.
        System.setProperty("org.lwjgl.opengl.explicitInit", "true");
        if (GL.getFunctionProvider() == null) {
            GL.create(GLFW::glfwGetProcAddress);
            System.out.println("[Jogger] OpenGL functions use the original Android window bridge");
        }
    }
}
