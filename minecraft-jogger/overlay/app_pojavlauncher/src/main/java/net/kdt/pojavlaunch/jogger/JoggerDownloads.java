package net.kdt.pojavlaunch.jogger;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/** Bounded HTTPS downloads, verified Modrinth files, and required dependency resolution. */
public final class JoggerDownloads {
    public static final String API="https://api.modrinth.com/v2";
    private static final long MAX_FILE=1024L*1024*1024;
    private static final Object PUBLISH_LOCK=new Object();
    public interface Progress {void update(String message,long done,long total);}
    private JoggerDownloads(){}
    private static void check(AtomicBoolean cancel)throws IOException{if(cancel.get()||Thread.currentThread().isInterrupted())throw new IOException("Cancelled");}
    public static String encode(String s)throws IOException{return URLEncoder.encode(s,"UTF-8");}
    private static HttpURLConnection open(String address)throws IOException{
        URL url=new URL(address);
        for(int i=0;i<6;i++){
            if(!"https".equalsIgnoreCase(url.getProtocol())||url.getUserInfo()!=null)throw new IOException("Use a public HTTPS download URL without embedded credentials.");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","MinecraftJogger/1.0 (github.com/itswiktoragain/gpt-on-the-move)");
            int status=c.getResponseCode();
            if(status>=300&&status<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Download redirect has no destination.");url=new URL(url,location);continue;}
            if(status!=200){c.disconnect();throw new IOException(status==429?"The download service is busy. Please try again in a moment.":"Download service returned HTTP "+status+".");}
            return c;
        }throw new IOException("Too many download redirects.");
    }
    public static String json(String address,AtomicBoolean cancel)throws IOException{
        check(cancel);HttpURLConnection c=open(address);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){check(cancel);if(out.size()+n>8*1024*1024)throw new IOException("Metadata response is too large.");out.write(b,0,n);}
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }finally{c.disconnect();}
    }
    public static JSONArray search(String query,String type,String gameVersion,String loader,AtomicBoolean cancel)throws Exception{
        requireGameVersion(gameVersion);
        JSONArray facets=new JSONArray();facets.put(new JSONArray().put("project_type:"+type));
        if(gameVersion!=null&&!gameVersion.startsWith("latest-"))facets.put(new JSONArray().put("versions:"+gameVersion));
        if("mod".equals(type)&&!"vanilla".equals(loader))facets.put(new JSONArray().put("categories:"+loader));
        return new JSONObject(json(API+"/search?query="+encode(query)+"&limit=30&facets="+encode(facets.toString()),cancel)).getJSONArray("hits");
    }
    public static JSONArray versions(String project,String type,String gameVersion,String loader,AtomicBoolean cancel)throws Exception{
        requireGameVersion(gameVersion);
        String url=API+"/project/"+encode(project)+"/version?include_changelog=false";
        if(gameVersion!=null&&!gameVersion.startsWith("latest-"))url+="&game_versions="+encode(new JSONArray().put(gameVersion).toString());
        if("mod".equals(type)&&!"vanilla".equals(loader))url+="&loaders="+encode(new JSONArray().put(loader).toString());
        return new JSONArray(json(url,cancel));
    }
    public static String safeName(String s,String kind)throws IOException{
        if(s==null||s.length()>180||s.contains("/")||s.contains("\\")||s.contains("\u0000")||s.startsWith(".")||s.contains("..")||!s.matches("[\\p{L}\\p{N} _+.()@,=\\-]+"))
            throw new IOException("Choose a simple filename without folders.");
        String lower=s.toLowerCase(Locale.ROOT);
        if("mods".equals(kind)&&!lower.endsWith(".jar"))throw new IOException("Mods must be .jar files.");
        if("resourcepacks".equals(kind)&&!lower.endsWith(".zip"))throw new IOException("Resource packs must be .zip files.");
        return s;
    }
    public static void validateArchive(File f,String kind)throws IOException{
        try(ZipFile zip=new ZipFile(f)){
            if(zip.size()==0)throw new IOException("The archive is empty.");
            if("resourcepacks".equals(kind)&&(zip.getEntry("pack.mcmeta")==null||zip.getEntry("pack.mcmeta").isDirectory()))
                throw new IOException("This ZIP has no pack.mcmeta at its root. Choose a Minecraft resource pack.");
        }catch(java.util.zip.ZipException e){throw new IOException("This is not a valid "+("mods".equals(kind)?"JAR":"ZIP")+" file.",e);}
    }
    public static File download(String address,File folder,String name,String kind,String hash,long expected,AtomicBoolean cancel,Progress progress)throws Exception{
        check(cancel);
        safeName(name,kind);if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create the instance content folder.");
        File target=new File(folder,name);
        if(!target.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile()))throw new IOException("Invalid filename.");
        if(target.exists()){
            if(hash!=null&&hash.equalsIgnoreCase(digest(target))){check(cancel);return target;}
            throw new IOException(name+" already exists. Disable or remove it before installing another copy.");
        }
        if(expected>MAX_FILE)throw new IOException("This file exceeds the 1 GB download limit.");
        File temp=new File(folder,".jogger-"+UUID.randomUUID()+".part");
        HttpURLConnection c=open(address);
        try{
            long total=c.getContentLengthLong();if(total>MAX_FILE)throw new IOException("This file exceeds the 1 GB download limit.");
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(temp)){
                byte[] b=new byte[65536];long done=0,last=0;int n;
                while((n=in.read(b))!=-1){check(cancel);done+=n;if(done>MAX_FILE)throw new IOException("Download exceeds 1 GB.");out.write(b,0,n);long now=System.currentTimeMillis();if(now-last>120){progress.update(name,done,total);last=now;}}
                if(expected>0&&done!=expected)throw new IOException("File size did not match the publisher's metadata.");
            }
            check(cancel);validateArchive(temp,kind);
            if(hash!=null&&!hash.equalsIgnoreCase(digest(temp)))throw new IOException("Checksum mismatch. The downloaded file was discarded.");
            publish(temp,target,cancel);
            return target;
        }finally{c.disconnect();temp.delete();}
    }
    /** Same-filesystem staged move, with no replacement of an existing user file. */
    public static void publish(File staged,File target,AtomicBoolean cancel)throws IOException{
        synchronized(PUBLISH_LOCK){
            check(cancel);
            // ATOMIC_MOVE permits replacing an existing target on some providers. Default move
            // preserves the no-replace contract, while same-filesystem files remain staged.
            if(target.exists())throw new IOException(target.getName()+" already exists. It was not replaced.");
            Files.move(staged.toPath(),target.toPath());
        }
    }
    public static String digest(File f)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-512");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        StringBuilder result=new StringBuilder(128);for(byte b:d.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
    }
    public static JSONObject primary(JSONObject version)throws JSONException{
        JSONArray files=version.getJSONArray("files");if(files.length()==0)throw new JSONException("This version contains no files.");
        for(int i=0;i<files.length();i++)if(files.getJSONObject(i).optBoolean("primary"))return files.getJSONObject(i);return files.getJSONObject(0);
    }
    private static void plan(JSONObject v,String type,String gameVersion,String loader,LinkedHashMap<String,JSONObject> planned,AtomicBoolean cancel)throws Exception{
        check(cancel);
        String id=v.getString("id");if(planned.containsKey(id))return;
        String project=v.optString("project_id","");
        if(!project.isEmpty())for(JSONObject existing:planned.values()){
            if(project.equals(existing.optString("project_id","")))
                throw new IOException("Required dependencies request conflicting versions of the same project. Choose a different release.");
        }
        if(planned.size()>=32)throw new IOException("This project needs more than 32 dependencies. Import its modpack instead.");
        planned.put(id,v);
        if(!"mod".equals(type))return;
        JSONArray deps=v.optJSONArray("dependencies");if(deps==null)return;
        for(int i=0;i<deps.length();i++){
            JSONObject d=deps.getJSONObject(i);if(!"required".equals(d.optString("dependency_type")))continue;
            JSONObject dep;
            String ver=d.isNull("version_id")?"":d.optString("version_id");
            String dependencyProject=d.isNull("project_id")?"":d.optString("project_id");
            if(!ver.isEmpty())dep=new JSONObject(json(API+"/version/"+encode(ver),cancel));
            else if(!dependencyProject.isEmpty()){
                JSONArray compatible=versions(dependencyProject,type,gameVersion,loader,cancel);
                if(compatible.length()==0)throw new IOException("A required dependency has no compatible version for this instance.");
                dep=compatible.getJSONObject(0);
            }else throw new IOException("A required dependency must be installed manually: "+d.optString("file_name","unknown file"));
            if(!supports(dep,gameVersion,loader))throw new IOException("A required dependency does not match the instance version and loader.");
            plan(dep,type,gameVersion,loader,planned,cancel);
        }
    }
    private static boolean supports(JSONObject v,String gameVersion,String loader){
        JSONArray games=v.optJSONArray("game_versions"),loaders=v.optJSONArray("loaders");
        boolean game=gameVersion.startsWith("latest-"),load="vanilla".equals(loader);
        if(games!=null)for(int i=0;i<games.length();i++)game|=gameVersion.equals(games.optString(i));
        if(loaders!=null)for(int i=0;i<loaders.length();i++)load|=loader.equals(loaders.optString(i));
        return game&&load;
    }
    private static void requireGameVersion(String gameVersion)throws IOException{
        if(gameVersion==null||gameVersion.trim().isEmpty()||gameVersion.startsWith("latest-"))
            throw new IOException("Choose a specific Minecraft version, or wait for the version list to finish loading.");
    }
    public static List<File> install(JSONObject version,File folder,String kind,String gameVersion,String loader,AtomicBoolean cancel,Progress progress)throws Exception{
        check(cancel);requireGameVersion(gameVersion);
        LinkedHashMap<String,JSONObject> plan=new LinkedHashMap<>();
        String type="mods".equals(kind)?"mod":"resourcepack";
        if(!supports(version,gameVersion,"mod".equals(type)?loader:"vanilla"))
            throw new IOException("This download does not match the instance's Minecraft version and loader.");
        plan(version,type,gameVersion,loader,plan,cancel);
        File staging=new File(folder,".jogger-staging-"+UUID.randomUUID());staging.mkdirs();
        List<File> installed=new ArrayList<>();List<File> staged=new ArrayList<>();
        Set<String> filenames=new HashSet<>();
        try{
            int index=0;
            for(JSONObject v:plan.values()){
                JSONObject f=primary(v);String filename=safeName(f.getString("filename"),kind);
                if(!filenames.add(filename))throw new IOException("Two required files use the same name: "+filename);
                String hash=f.getJSONObject("hashes").getString("sha512");
                File existing=new File(folder,filename);
                if(existing.exists()){
                    if(hash.equalsIgnoreCase(digest(existing)))continue;
                    throw new IOException(filename+" is already installed with different contents. Disable or remove it first.");
                }
                progress.update("Downloading "+(++index)+" of "+plan.size(),0,0);
                staged.add(download(f.getString("url"),staging,filename,kind,hash,f.optLong("size",-1),cancel,progress));
            }
            check(cancel);
            synchronized(PUBLISH_LOCK){
                try{
                    // Recheck every target before publishing; a second content screen may have
                    // finished a download while this transaction was staged.
                    for(File f:staged)if(new File(folder,f.getName()).exists())
                        throw new IOException(f.getName()+" was installed by another operation. Nothing was replaced.");
                    for(File f:staged){
                        check(cancel);File target=new File(folder,f.getName());publish(f,target,cancel);installed.add(target);
                    }
                    check(cancel);
                }catch(Exception failure){
                    for(File f:installed)f.delete();installed.clear();throw failure;
                }
            }
            return installed;
        }finally{
            File[] leftovers=staging.listFiles();if(leftovers!=null)for(File f:leftovers)f.delete();staging.delete();
        }
    }
}
