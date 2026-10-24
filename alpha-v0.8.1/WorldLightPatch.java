package alphamodern;
import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
/** Patches Alpha 1.0.15 cj light scheduling without changing bytecode length. */
final class WorldLightPatch implements ClassFileTransformer {
 @Override public byte[] transform(ClassLoader loader,String name,Class<?> cls,ProtectionDomain pd,byte[] bytes) {
  if(!"cj".equals(name))return null;
  try {
   byte[] patched=patch(bytes);
   System.out.println(AlphaModernAgent.PREFIX+"Patched Alpha recursive light queue (cj)");
   return patched;
  }catch(Exception e){
   System.err.println(AlphaModernAgent.PREFIX+"World-light patch rejected: "+e);
   return null;
  }
 }
 static byte[] patch(byte[] original)throws IOException {
  DataInputStream in=new DataInputStream(new ByteArrayInputStream(original));
  ByteArrayOutputStream output=new ByteArrayOutputStream(original.length+160);
  DataOutputStream out=new DataOutputStream(output);
  if(in.readInt()!=0xcafebabe)throw new IOException("Not classfile");
  int minor=in.readUnsignedShort(),major=in.readUnsignedShort();
  if(major!=49)throw new IOException("Expected Alpha 1.0.15 Java 5 classfile");
  out.writeInt(0xcafebabe);out.writeShort(minor);out.writeShort(major);
  int oldCount=in.readUnsignedShort();
  if(oldCount>65529)throw new IOException("Constant pool exhausted");
  out.writeShort(oldCount+6);
  Map<Integer,String> utf=new HashMap<Integer,String>();
  for(int i=1;i<oldCount;i++) {
   int tag=in.readUnsignedByte();out.writeByte(tag);
   switch(tag){
    case 1:{
     int n=in.readUnsignedShort();byte[] b=new byte[n];in.readFully(b);
     utf.put(i,new String(b,StandardCharsets.UTF_8));
     out.writeShort(n);out.write(b);break;
    }
    case 3:case 4:case 9:case 10:case 11:case 12:case 17:case 18:copy(in,out,4);break;
    case 5:case 6:copy(in,out,8);i++;break;
    case 7:case 8:case 16:case 19:case 20:copy(in,out,2);break;
    case 15:copy(in,out,3);break;
    default:throw new IOException("Unknown constant type "+tag);
   }
  }
  utf8(out,"alphamodern/WorldLightGuard");
  out.writeByte(7);out.writeShort(oldCount);
  utf8(out,"offer");
  utf8(out,"(Ljava/util/List;Ljava/lang/Object;)Z");
  out.writeByte(12);out.writeShort(oldCount+2);out.writeShort(oldCount+3);
  out.writeByte(10);out.writeShort(oldCount+1);out.writeShort(oldCount+4);
  int offerRef=oldCount+5;
  copy(in,out,6);
  int interfaces=in.readUnsignedShort();out.writeShort(interfaces);copy(in,out,interfaces*2);
  int fields=in.readUnsignedShort();out.writeShort(fields);
  for(int i=0;i<fields;i++)member(in,out,utf,false,offerRef);
  int methods=in.readUnsignedShort();out.writeShort(methods);
  int patched=0;
  for(int i=0;i<methods;i++)patched+=member(in,out,utf,true,offerRef);
  if(patched!=1)throw new IOException("Expected exactly one Alpha lighting scheduler, got "+patched);
  int attrs=in.readUnsignedShort();out.writeShort(attrs);
  for(int i=0;i<attrs;i++)attribute(in,out,utf,false,offerRef);
  if(in.read()!=-1)throw new IOException("Trailing data after cj.class");
  out.flush();return output.toByteArray();
 }
 private static int member(DataInputStream in,DataOutputStream out,Map<Integer,String> utf,boolean method,int ref)throws IOException {
  out.writeShort(in.readUnsignedShort());
  int name=in.readUnsignedShort(),desc=in.readUnsignedShort();
  out.writeShort(name);out.writeShort(desc);
  boolean target=method&&"a".equals(utf.get(name))&&"(Lbu;IIIIIIZ)V".equals(utf.get(desc));
  int attrs=in.readUnsignedShort();out.writeShort(attrs);int hits=0;
  for(int i=0;i<attrs;i++)hits+=attribute(in,out,utf,target,ref);
  if(target&&hits!=1)throw new IOException("Cannot identify original lighting scheduler code");
  return hits;
 }
 private static int attribute(DataInputStream in,DataOutputStream out,Map<Integer,String> utf,boolean target,int ref)throws IOException {
  int name=in.readUnsignedShort(),size=in.readInt();
  if(size<0||size>10000000)throw new IOException("Invalid attribute size "+size);
  byte[] body=new byte[size];in.readFully(body);int changed=0;
  if(target&&"Code".equals(utf.get(name))){
   if(size<8)throw new IOException("Missing method code");
   int codeLength=((body[4]&255)<<24)|((body[5]&255)<<16)|((body[6]&255)<<8)|(body[7]&255);
   if(codeLength!=198||codeLength+8>size)throw new IOException("Unexpected cj.a code length "+codeLength);
   int i=8+155;
   if((body[i]&255)!=0xb9||(body[i+3]&255)!=2||(body[i+4]&255)!=0||
      (body[8+160]&255)!=0x57||(body[8+189]&255)!=0x2a||
      (body[8+190]&255)!=0xb6||(body[8+194]&255)!=0xa7)
       throw new IOException("Alpha lighting recursion signature mismatch");
   body[i]=(byte)0xb8;
   body[i+1]=(byte)(ref>>>8);body[i+2]=(byte)ref;
   body[i+3]=0;body[i+4]=0;changed=1;
  }
  out.writeShort(name);out.writeInt(size);out.write(body);return changed;
 }
 private static void utf8(DataOutputStream out,String s)throws IOException{
  byte[] b=s.getBytes(StandardCharsets.UTF_8);
  out.writeByte(1);out.writeShort(b.length);out.write(b);
 }
 private static void copy(DataInputStream in,DataOutputStream out,int n)throws IOException{
  byte[] b=new byte[n];in.readFully(b);out.write(b);
 }
}
