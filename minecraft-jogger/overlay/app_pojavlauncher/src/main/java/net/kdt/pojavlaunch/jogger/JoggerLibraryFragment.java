package net.kdt.pojavlaunch.jogger;

import static net.kdt.pojavlaunch.jogger.JoggerUi.*;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.kdt.mcgui.ProgressLayout;
import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.value.launcherprofiles.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class JoggerLibraryFragment extends Fragment {
    private LinearLayout content;
    private String kind="mods",instance;
    private MinecraftProfile profile;
    private File directory;
    private boolean busy;
    private AtomicBoolean cancellation;
    private AlertDialog running;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String[]> importer=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null)importFile(uri);});
    public JoggerLibraryFragment(){super();}
    @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup parent,@Nullable Bundle state){
        Bundle args=getArguments();if(args!=null){kind=args.getString("folder","mods");instance=args.getString("instance");}
        if(!"mods".equals(kind)&&!"resourcepacks".equals(kind))kind="mods";
        JoggerInstances.ensure();if(instance==null)instance=JoggerInstances.currentKey();
        profile=LauncherProfiles.mainProfileJson.profiles.get(instance);
        if(profile==null)profile=JoggerInstances.current();
        directory=new File(Tools.getGameDirPath(profile),kind);directory.mkdirs();
        ScrollView scroll=new ScrollView(requireContext());scroll.setBackgroundColor(PAPER);
        content=column(requireContext());content.setPadding(dp(requireContext(),22),dp(requireContext(),12),dp(requireContext(),22),dp(requireContext(),28));
        scroll.addView(content);render();return scroll;
    }
    @Override public void onDestroyView(){if(cancellation!=null)cancellation.set(true);if(running!=null)running.dismiss();running=null;content=null;super.onDestroyView();}
    private void render(){
        if(content==null||!isAdded())return;Context c=requireContext();content.removeAllViews();
        add(content,button(c,"← Back to Jogger",false,v->Tools.backToMainMenu(requireActivity())),0);
        add(content,text(c,"mods".equals(kind)?"Mods & downloads":"Resource packs",26,INK,true),24);
        add(content,text(c,JoggerInstances.displayName(profile)+" · "+JoggerInstances.gameVersion(profile),14,MUTED,false),8);
        LinearLayout actions=row(c);weighted(actions,button(c,"Browse Modrinth",true,v->searchDialog()),false);weighted(actions,button(c,"Import file",false,v->importer.launch(new String[]{"*/*"})),true);add(content,actions,18);
        add(content,button(c,"Download from an HTTPS URL",false,v->urlDialog()),10);
        if("mods".equals(kind)&&"vanilla".equals(JoggerInstances.loader(profile)))add(content,text(c,"This is a vanilla instance. Install Fabric, Quilt, Forge or NeoForge before using mods.",14,0xff9b5219,false),18);
        if("resourcepacks".equals(kind))add(content,text(c,"After installing a pack, enable it in Minecraft → Options → Resource Packs.",14,MUTED,false),18);
        File[] files=directory.listFiles(f->f.isFile()&&!f.getName().startsWith(".")&&!f.getName().endsWith(".part"));
        add(content,text(c,"Installed",20,INK,true),26);
        if(files==null||files.length==0){add(content,text(c,"Nothing here yet. Browse for a compatible download or import a file from your phone.",15,MUTED,false),14);return;}
        Arrays.sort(files,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));
        for(File f:files){LinearLayout card=card(c);add(card,text(c,f.getName(),15,INK,true),0);
            add(card,text(c,size(f.length())+(f.getName().endsWith(".disabled")?" · Disabled":" · Ready"),13,MUTED,false),9);
            card.setOnClickListener(v->fileActions(f));card.setFocusable(true);add(content,card,12);}
    }
    private void fileActions(File file){
        if(busy){toast("Wait for the current download to finish.");return;}
        boolean disabled=file.getName().endsWith(".disabled");
        new AlertDialog.Builder(requireContext()).setTitle(file.getName()).setItems(new String[]{disabled?"Enable":"Disable","Delete file"},(d,w)->{
            if(w==0){String name=file.getName();File target=new File(directory,disabled?name.substring(0,name.length()-9):name+".disabled");
                if(target.exists()){toast("A file with that name already exists.");return;}if(!file.renameTo(target))toast("Could not rename this file.");render();
            }else new AlertDialog.Builder(requireContext()).setTitle("Delete this file?").setMessage(file.getName()).setPositiveButton("Delete",(a,b)->{if(!file.delete())toast("Could not delete the file.");render();}).setNegativeButton("Cancel",null).show();
        }).show();
    }
    private void searchDialog(){
        if(busy)return;
        if("mods".equals(kind)&&"vanilla".equals(JoggerInstances.loader(profile))){new AlertDialog.Builder(requireContext()).setTitle("Choose a modded instance").setMessage("Modrinth downloads are filtered by your instance's loader. Install a mod loader from Jogger's home screen, then select that instance.").setPositiveButton("OK",null).show();return;}
        EditText query=input(requireContext(),"Search projects");query.setPadding(dp(requireContext(),24),dp(requireContext(),12),dp(requireContext(),24),dp(requireContext(),12));
        new AlertDialog.Builder(requireContext()).setTitle("Search Modrinth").setView(query).setPositiveButton("Search",(d,w)->search(query.getText().toString())).setNegativeButton("Cancel",null).show();
    }
    private void search(String query){
        work("Searching Modrinth",cancel->JoggerDownloads.search(query,type(),JoggerInstances.gameVersion(profile),JoggerInstances.loader(profile),cancel),value->{
            JSONArray hits=(JSONArray)value;if(hits.length()==0){message("No matching projects","Try another search, or check this instance's Minecraft version and mod loader.");return;}
            String[] labels=new String[hits.length()];for(int i=0;i<hits.length();i++){JSONObject h=hits.optJSONObject(i);labels[i]=h.optString("title")+"\nby "+h.optString("author")+" · "+h.optString("description");}
            new AlertDialog.Builder(requireContext()).setTitle("Compatible projects").setItems(labels,(d,w)->project(hits.optJSONObject(w))).setNegativeButton("Close",null).show();
        });
    }
    private void project(JSONObject hit){
        work("Loading versions",cancel->JoggerDownloads.versions(hit.getString("project_id"),type(),JoggerInstances.gameVersion(profile),JoggerInstances.loader(profile),cancel),value->{
            JSONArray versions=(JSONArray)value;if(versions.length()==0){message("No compatible files","This project has no downloadable version for the selected Minecraft version and loader.");return;}
            String[] labels=new String[versions.length()];for(int i=0;i<labels.length;i++){JSONObject v=versions.optJSONObject(i);labels[i]=v.optString("name")+"  ·  "+v.optString("version_type");}
            new AlertDialog.Builder(requireContext()).setTitle(hit.optString("title")).setItems(labels,(d,w)->confirmInstall(hit,versions.optJSONObject(w))).setNegativeButton("Close",null).show();
        });
    }
    private void confirmInstall(JSONObject hit,JSONObject version){
        new AlertDialog.Builder(requireContext()).setTitle("Install "+hit.optString("title")+"?")
            .setMessage("Version: "+version.optString("version_number")+"\nInstance: "+JoggerInstances.displayName(profile)+"\n\nRequired Modrinth dependencies will be included. Downloads are checked against their published SHA-512 checksums.")
            .setPositiveButton("Install",(d,w)->work("Installing "+hit.optString("title"),cancel->JoggerDownloads.install(version,directory,kind,JoggerInstances.gameVersion(profile),JoggerInstances.loader(profile),cancel,this::progress),result->{render();toast("Installed "+((List<?>)result).size()+" new file(s).");}))
            .setNegativeButton("Cancel",null).show();
    }
    private void urlDialog(){
        if(busy)return;Context c=requireContext();LinearLayout form=column(c);form.setPadding(dp(c,22),dp(c,8),dp(c,22),0);
        EditText url=input(c,"https://…");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        EditText name=input(c,"mods".equals(kind)?"filename.jar":"filename.zip");
        add(form,url,0);add(form,name,12);add(form,text(c,"Use a direct file download from a publisher you trust. Jogger checks the archive before adding it to this instance.",13,MUTED,false),14);
        AlertDialog d=new AlertDialog.Builder(c).setTitle("Download a file").setView(form).setPositiveButton("Download",null).setNegativeButton("Cancel",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String address=url.getText().toString().trim(),filename=name.getText().toString().trim();
            try{JoggerDownloads.safeName(filename,kind);if(!address.startsWith("https://"))throw new IOException("Enter an HTTPS URL.");}catch(Exception e){name.setError(e.getMessage());return;}
            d.dismiss();work("Downloading file",cancel->JoggerDownloads.download(address,directory,filename,kind,null,-1,cancel,this::progress),result->{render();toast("Download complete.");});
        }));d.show();
    }
    private void importFile(Uri uri){
        if(busy)return;Context context=requireContext().getApplicationContext();String name=null;
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}catch(Exception ignored){}
        if(name==null){message("Could not read file","The file provider did not return a filename.");return;}
        final String filename=name;
        work("Importing "+filename,cancel->{
            JoggerDownloads.safeName(filename,kind);File target=new File(directory,filename);
            if(target.exists())throw new IOException("That file already exists. Disable or remove it first.");
            File temp=new File(directory,".jogger-import-"+UUID.randomUUID()+".part");
            try{
                try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                    if(in==null)throw new IOException("The file provider could not open this file.");
                    byte[] b=new byte[65536];long total=0;int n;while((n=in.read(b))!=-1){if(cancel.get())throw new IOException("Cancelled");total+=n;if(total>1024L*1024*1024)throw new IOException("Files larger than 1 GB are not supported.");out.write(b,0,n);}
                }
                JoggerDownloads.validateArchive(temp,kind);if(cancel.get())throw new IOException("Cancelled");
                JoggerDownloads.publish(temp,target,cancel);return target;
            }finally{temp.delete();}
        },result->{render();toast("File imported.");});
    }
    private interface Job{Object run(AtomicBoolean cancel)throws Exception;}
    private interface Done{void ready(Object result);}
    private void work(String title,Job job,Done done){
        if(busy){toast("A download is already in progress.");return;}
        busy=true;cancellation=new AtomicBoolean(false);AtomicBoolean cancel=cancellation;String progressKey="jogger-content-"+UUID.randomUUID();
        running=new AlertDialog.Builder(requireContext()).setTitle(title).setMessage("Please wait…").setNegativeButton("Cancel",(d,w)->cancel.set(true)).setOnCancelListener(d->cancel.set(true)).create();running.show();
        ProgressLayout.setProgress(progressKey,0,title);
        PojavApplication.sExecutorService.execute(()->{
            Object result=null;Exception failure=null;try{result=job.run(cancel);}catch(Exception e){failure=e;}
            ProgressLayout.clearProgress(progressKey);final Object value=result;final Exception error=failure;
            ui.post(()->{
                busy=false;if(running!=null)running.dismiss();running=null;
                if(!isAdded()||content==null||cancel.get())return;
                if(error!=null)message("Could not complete this action",error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());else done.ready(value);
            });
        });
    }
    private void progress(String message,long done,long total){ui.post(()->{if(running!=null)running.setMessage(message+(total>0?"\n"+size(done)+" / "+size(total):""));});}
    private String type(){return "mods".equals(kind)?"mod":"resourcepack";}
    private void message(String title,String message){if(isAdded())new AlertDialog.Builder(requireContext()).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    private void toast(String message){if(isAdded())Toast.makeText(requireContext(),message,Toast.LENGTH_LONG).show();}
    private static String size(long bytes){return bytes>=1024*1024?String.format(Locale.ROOT,"%.1f MB",bytes/(1024.0*1024)):String.format(Locale.ROOT,"%.0f KB",bytes/1024.0);}
}
