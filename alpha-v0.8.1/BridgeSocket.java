package alphamodern;
import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Alpha socket swap-in: activates the light guard only during bridged multiplayer. */
public class BridgeSocket extends Socket {
 private final AtomicBoolean guarded=new AtomicBoolean();
 public BridgeSocket(InetAddress remote,int port)throws IOException {
  super(InetAddress.getLoopbackAddress(),Gateway.port());
  try {
   if(port<1||port>65535)throw new IOException("Invalid target port");
   setTcpNoDelay(true);
   DataOutputStream out=new DataOutputStream(getOutputStream());
   out.writeUTF(remote.getHostName());out.writeInt(port);out.flush();
   WorldLightGuard.beginBridge();guarded.set(true);
  } catch(IOException e){close();throw e;}
 }
 @Override public void close()throws IOException {
  try{super.close();}finally{
   if(guarded.compareAndSet(true,false))WorldLightGuard.endBridge();
  }
 }
}
