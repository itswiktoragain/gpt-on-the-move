package alphamodern;
import java.io.*;
import java.lang.instrument.Instrumentation;
import java.util.zip.*;
/** Adds the safe world-light transformer to the intact AlphaModern 0.8.0 agent. */
public final class AlphaModernShim {
 public static void premain(String args,Instrumentation inst){
  inst.addTransformer(new WorldLightPatch());
  AlphaModernAgent.premain(args,inst);
  System.out.println(AlphaModernAgent.PREFIX+"v0.8.1: bounded Alpha lighting queue patch enabled");
 }
 public static void main(String[] args)throws Exception {
  if(args.length==3&&"patch-world-dump".equals(args[0])){
   byte[] original;
   try(ZipFile z=new ZipFile(args[1])){
    ZipEntry e=z.getEntry("cj.class");
    if(e==null)throw new IOException("Alpha 1.0.15 world class cj.class not found");
    try(InputStream in=z.getInputStream(e)){original=AlphaModernAgent.readAll(in);}
   }
   byte[] changed=WorldLightPatch.patch(original);
   try(OutputStream out=new FileOutputStream(args[2])){out.write(changed);}
   System.out.println("Patched Alpha lighting class saved to "+args[2]);
  } else AlphaModernAgent.main(args);
 }
}
