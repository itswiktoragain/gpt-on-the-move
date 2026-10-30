package com.wiktor.jogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Joins Mojang's official class/member mappings with Fabric's intermediary names. */
public final class Mappings {
 public static void write(File official,File intermediary,File output)throws Exception{
  List<String> lines=Files.readAllLines(official.toPath(),StandardCharsets.UTF_8);
  Map<String,String> classes=new HashMap<>(),tinyClasses=new HashMap<>(),tinyMembers=new HashMap<>();
  for(String line:lines)if(!line.isEmpty()&&!Character.isWhitespace(line.charAt(0))&&line.endsWith(":")){
   String[]a=line.substring(0,line.length()-1).split(" -> ");if(a.length==2)classes.put(a[0],a[1]);
  }
  try(ZipFile z=new ZipFile(intermediary)){
   ZipEntry entry=z.getEntry("mappings/mappings.tiny");if(entry==null)throw new IOException("Intermediary archive has no mappings");
   try(BufferedReader r=new BufferedReader(new InputStreamReader(z.getInputStream(entry),StandardCharsets.UTF_8))){String header=r.readLine();if(header==null||!header.startsWith("tiny\t2\t"))throw new IOException("Expected Tiny v2 intermediary mapping");String owner="",line;while((line=r.readLine())!=null){String[]v=line.split("\t",-1);if(v.length>=3&&v[0].equals("c")){owner=v[1];tinyClasses.put(owner,v[2]);}else if(v.length>=5&&v[0].isEmpty()&&(v[1].equals("f")||v[1].equals("m"))){tinyMembers.put(v[1]+"|"+owner+"|"+v[3]+"|"+v[2],v[4]);}}}
  }
  Properties out=new Properties();for(Map.Entry<String,String>c:classes.entrySet()){String mapped=tinyClasses.get(c.getValue().replace('.','/'));if(mapped!=null)out.setProperty("C:"+c.getKey(),mapped.replace('/','.'));}
  String owner="";for(String line:lines){if(line.isEmpty()||line.startsWith("#"))continue;if(!Character.isWhitespace(line.charAt(0))&&line.endsWith(":")){owner=line.substring(0,line.indexOf(" -> "));continue;}String s=line.trim().replaceFirst("^\\d+:\\d+:","");int arrow=s.lastIndexOf(" -> "),space=s.indexOf(' ');if(arrow<0||space<0||space>arrow)continue;String type=s.substring(0,space),member=s.substring(space+1,arrow),obf=s.substring(arrow+4),obfOwner=classes.get(owner);if(obfOwner==null)continue;String mapped;
   if(member.contains("(")){int end=member.indexOf(')');if(end<0)continue;String signature=member.substring(0,end+1),args=signature.substring(signature.indexOf('(')+1,signature.length()-1);StringBuilder desc=new StringBuilder("(");if(!args.isEmpty())for(String a:args.split(","))desc.append(descriptor(a,classes));desc.append(')').append(descriptor(type,classes));mapped=tinyMembers.get("m|"+obfOwner.replace('.','/')+"|"+obf+"|"+desc);if(mapped!=null)out.setProperty("M:"+owner+"|"+signature,mapped);
   }else{mapped=tinyMembers.get("f|"+obfOwner.replace('.','/')+"|"+obf+"|"+descriptor(type,classes));if(mapped!=null)out.setProperty("F:"+owner+"|"+member,mapped);}
  }
  output.getParentFile().mkdirs();File tmp=new File(output.getParentFile(),output.getName()+".tmp");try(OutputStream o=new FileOutputStream(tmp)){out.store(o,"Jogger official-to-intermediary touch-agent mapping");}Files.move(tmp.toPath(),output.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
 }
 static String descriptor(String type,Map<String,String>classes){if(type.endsWith("[]"))return "["+descriptor(type.substring(0,type.length()-2),classes);switch(type){case "void":return "V";case "boolean":return "Z";case "byte":return "B";case "char":return "C";case "short":return "S";case "int":return "I";case "long":return "J";case "float":return "F";case "double":return "D";default:return "L"+classes.getOrDefault(type,type).replace('.','/')+";";}}
 private Mappings(){}
}
