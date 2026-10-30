package com.wiktor.jogger.agent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
public final class EngineProbe {
 public static void main(String[]args)throws Exception{
  System.out.println("JOGGER_PROBE_JVM_OK "+System.getProperty("java.version"));
  GLFWErrorCallback.createPrint(System.err).set();
  if(!GLFW.glfwInit())throw new IllegalStateException("GLFW initialization failed");
  long window=GLFW.glfwCreateWindow(1280,720,"Jogger engine check",0,0);
  if(window==0)throw new IllegalStateException("Android window creation failed");
  GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
  System.out.println("JOGGER_PROBE_GL_OK "+GL11.glGetString(GL11.GL_VERSION)+" / "+GL11.glGetString(GL11.GL_RENDERER));
  for(int i=0;i<120&&!GLFW.glfwWindowShouldClose(window);i++){
   GL11.glClearColor(.12f+(i%60)/600f,.22f,.28f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
   GLFW.glfwSwapBuffers(window);GLFW.glfwPollEvents();Thread.sleep(16);
  }
  System.out.println("JOGGER_PROBE_RENDER_OK");GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
 }
}
