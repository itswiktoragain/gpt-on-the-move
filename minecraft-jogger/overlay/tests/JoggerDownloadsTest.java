import net.kdt.pojavlaunch.jogger.JoggerDownloads;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Executes production validation/publication code without an Android runtime or network. */
public final class JoggerDownloadsTest {
    interface Checked { void run() throws Exception; }
    private static void rejects(Checked action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("An invalid operation was accepted");
    }
    private static void equal(Object actual,Object expected) {
        if(!expected.equals(actual))throw new AssertionError(actual+" != "+expected);
    }
    private static File archive(File folder,String name,String entry) throws Exception {
        File result=new File(folder,name);
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(result.toPath()))){
            out.putNextEntry(new ZipEntry(entry));
            if(!entry.endsWith("/"))out.write("{}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        File folder=Files.createTempDirectory("jogger-download-tests-").toFile();
        try{
            equal(JoggerDownloads.safeName("Łódź + 1.0.JAR","mods"),"Łódź + 1.0.JAR");
            for(String invalid:new String[]{"../escape.jar","sub/a.jar","sub\\a.jar",".hidden.jar","a..b.jar","a\u0000.jar","https:bad.jar","","pack.zip"})
                rejects(()->JoggerDownloads.safeName(invalid,"mods"));
            rejects(()->JoggerDownloads.safeName("mod.jar","resourcepacks"));
            File jar=archive(folder,"mod.jar","fabric.mod.json");
            JoggerDownloads.validateArchive(jar,"mods");
            JoggerDownloads.validateArchive(archive(folder,"pack.zip","pack.mcmeta"),"resourcepacks");
            rejects(()->JoggerDownloads.validateArchive(archive(folder,"nested.zip","nested/pack.mcmeta"),"resourcepacks"));
            rejects(()->JoggerDownloads.validateArchive(archive(folder,"directory.zip","pack.mcmeta/"),"resourcepacks"));
            File wrong=new File(folder,"wrong.jar");Files.write(wrong.toPath(),"<html>not a jar</html>".getBytes(StandardCharsets.UTF_8));
            rejects(()->JoggerDownloads.validateArchive(wrong,"mods"));
            File vector=new File(folder,"vector");Files.write(vector.toPath(),"abc".getBytes(StandardCharsets.US_ASCII));
            equal(JoggerDownloads.digest(vector),"ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f");

            File staged=archive(folder,"staged.part","fabric.mod.json"),target=new File(folder,"published.jar");
            rejects(()->JoggerDownloads.publish(staged,target,new AtomicBoolean(true)));
            if(!staged.exists()||target.exists())throw new AssertionError("Cancelled publish changed files");
            JoggerDownloads.publish(staged,target,new AtomicBoolean(false));
            if(staged.exists()||!target.exists())throw new AssertionError("Published file was not moved");
            String original=JoggerDownloads.digest(target);
            File conflicting=archive(folder,"conflict.part","different-entry");
            rejects(()->JoggerDownloads.publish(conflicting,target,new AtomicBoolean(false)));
            equal(JoggerDownloads.digest(target),original);
            if(!conflicting.exists())throw new AssertionError("Failed publish consumed its staged file");
            rejects(()->JoggerDownloads.download("https://invalid.invalid/file",folder,"cancel.jar","mods",null,-1,new AtomicBoolean(true),(m,d,t)->{}));
            if(new File(folder,"cancel.jar").exists())throw new AssertionError("Cancelled download published a file");

            JSONObject incompatible=new JSONObject().put("id","root").put("project_id","project")
                .put("game_versions",new JSONArray().put("1.20.1")).put("loaders",new JSONArray().put("fabric"));
            rejects(()->JoggerDownloads.install(incompatible,folder,"mods","1.21.4","fabric",new AtomicBoolean(false),(m,d,t)->{}));
            rejects(()->JoggerDownloads.versions("project","mod","latest-release","fabric",new AtomicBoolean(false)));

            // A diamond dependency graph can pin two versions of the same project. Reject the
            // second version before any network request or file mutation.
            LinkedHashMap<String,JSONObject> planned=new LinkedHashMap<>();
            planned.put("v1",new JSONObject().put("id","v1").put("project_id","shared"));
            JSONObject other=new JSONObject().put("id","v2").put("project_id","shared");
            Method plan=JoggerDownloads.class.getDeclaredMethod("plan",JSONObject.class,String.class,String.class,String.class,LinkedHashMap.class,AtomicBoolean.class);
            plan.setAccessible(true);
            rejects(()->{
                try{plan.invoke(null,other,"mod","1.21.4","fabric",planned,new AtomicBoolean(false));}
                catch(InvocationTargetException wrapped){if(wrapped.getCause() instanceof IOException)throw (IOException)wrapped.getCause();throw wrapped;}
            });
            equal(planned.size(),1);
            System.out.println("Production download checks passed: names, real ZIP/JAR structure, SHA-512 vector, cancellation, no-replace publication, version compatibility, dependency conflicts.");
        }finally{
            File[] files=folder.listFiles();if(files!=null)for(File f:files)Files.deleteIfExists(f.toPath());
            Files.deleteIfExists(folder.toPath());
        }
    }
}
