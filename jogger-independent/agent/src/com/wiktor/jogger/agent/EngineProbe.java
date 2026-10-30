package com.wiktor.jogger.agent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
public final class EngineProbe {
 public static void main(String[]args)throws Exception{
  System.out.println("JOGGER_PROBE_JVM_OK "+System.getProperty("java.version"));
  RendererBootstrap.initialize();
  GLFWErrorCallback.createPrint(System.err).set();
  if(!GLFW.glfwInit())throw new IllegalStateException("GLFW initialization failed");
  long window=GLFW.glfwCreateWindow(1280,720,"Jogger engine check",0,0);
  if(window==0)throw new IllegalStateException("Android window creation failed");
  GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
  System.out.println("JOGGER_PROBE_GL_OK "+GL11.glGetString(GL11.GL_VERSION)+" / "+GL11.glGetString(GL11.GL_RENDERER));
  for(int i=0;i<120&&!GLFW.glfwWindowShouldClose(window);i++){
   GL11.glClearColor(.12f+(i%60)/600f,.22f,.28f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
   if(i==0){
    ByteBuffer pixel=MemoryUtil.memAlloc(4);
    try{
     GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
     int r=pixel.get(0)&255,g=pixel.get(1)&255,b=pixel.get(2)&255;
     int err=GL11.glGetError();
     if(err!=GL11.GL_NO_ERROR||Math.abs(r-31)>4||Math.abs(g-56)>4||Math.abs(b-71)>4)
      throw new IllegalStateException("Framebuffer readback failed: "+r+","+g+","+b+" GL error "+err);
     System.out.println("JOGGER_PROBE_PIXEL_OK "+r+","+g+","+b);
    }finally{MemoryUtil.memFree(pixel);}
   }
   GLFW.glfwSwapBuffers(window);GLFW.glfwPollEvents();Thread.sleep(16);
  }
  System.out.println("JOGGER_PROBE_RENDER_OK");GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
 }
}
