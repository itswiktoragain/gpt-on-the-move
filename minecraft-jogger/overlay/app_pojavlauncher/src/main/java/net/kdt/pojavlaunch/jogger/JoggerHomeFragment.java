package net.kdt.pojavlaunch.jogger;

import static net.kdt.pojavlaunch.jogger.JoggerUi.*;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.fragments.*;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.*;
import java.util.*;

/** Native home screen; real launch/install operations remain in the Amethyst backend. */
public class JoggerHomeFragment extends Fragment {
    private LinearLayout content;
    private String tab="Play";
    public JoggerHomeFragment(){super();}
    @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle saved){
        if(saved!=null)tab=saved.getString("tab","Play");
        ScrollView scroll=new ScrollView(requireContext());scroll.setFillViewport(true);scroll.setBackgroundColor(PAPER);
        content=column(requireContext());content.setPadding(dp(requireContext(),22),dp(requireContext(),12),dp(requireContext(),22),dp(requireContext(),28));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));return scroll;
    }
    @Override public void onResume(){
        super.onResume();JoggerInstances.ensure();
        Object selected=ExtraCore.consumeValue(ExtraConstants.REFRESH_VERSION_SPINNER);
        if(selected instanceof String)JoggerInstances.select((String)selected);
        render();
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out){super.onSaveInstanceState(out);out.putString("tab",tab);}
    @Override public void onDestroyView(){content=null;super.onDestroyView();}
    private void render(){
        if(content==null||!isAdded())return;
        Context c=requireContext();content.removeAllViews();
        LinearLayout title=row(c);TextView brand=text(c,"Minecraft Jogger",28,INK,true);
        weighted(title,brand,false);TextView badge=text(c,"J",20,0xffffffff,true);badge.setGravity(Gravity.CENTER);badge.setBackground(background(INK,12,c));
        title.addView(badge,new LinearLayout.LayoutParams(dp(c,42),dp(c,42)));add(content,title,4);
        add(content,text(c,"Java Edition, wherever you go.",15,MUTED,false),8);
        LinearLayout tabs=row(c);
        for(String label:new String[]{"Play","Instances","Library"}){
            Button b=button(c,label,tab.equals(label),v->{tab=label;render();});weighted(tabs,b,tabs.getChildCount()>0);
        }add(content,tabs,22);
        if("Instances".equals(tab)){instances();return;}
        if("Library".equals(tab)){library();return;}
        MinecraftProfile p=JoggerInstances.current();
        if(!Tools.hasOnlineProfile()){
            LinearLayout sign=card(c);
            add(sign,text(c,"Bring your Minecraft account",19,INK,true),0);
            add(sign,text(c,"Sign in with Microsoft to download Java Edition and play online.",14,MUTED,false),8);
            add(sign,button(c,"Sign in with a code",true,v->signIn()),14);add(content,sign,20);
        }
        LinearLayout hero=card(c);hero.setBackground(background(INK,20,c));
        add(hero,text(c,JoggerInstances.displayName(p),25,0xffffffff,true),0);
        add(hero,text(c,p==null?"Create an instance to get started":p.lastVersionId+"  ·  "+capitalize(JoggerInstances.loader(p)),14,0xffd5d9db,false),9);
        add(hero,text(c,"Your worlds, mods and packs stay together in this instance.",14,0xffc6cccf,false),20);
        Button play=button(c,"Play Minecraft  →",false,v->play());play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffffffff));
        add(hero,play,20);add(content,hero,18);
        LinearLayout actions=row(c);weighted(actions,button(c,"Switch instance",false,v->{tab="Instances";render();}),false);
        weighted(actions,button(c,"+ New instance",false,v->createDialog()),true);add(content,actions,12);
        LinearLayout counts=row(c);
        addStat(counts,"Worlds",JoggerInstances.count(p,"saves"));addStat(counts,"Mods",JoggerInstances.count(p,"mods"));addStat(counts,"Packs",JoggerInstances.count(p,"resourcepacks"));add(content,counts,20);
        add(content,text(c,"Make it yours",21,INK,true),26);
        toolRow("Touch controls","Move, look, mine and build",v->controls(),"Mod loaders","Fabric, Quilt, Forge, NeoForge",v->loaders());
        toolRow("Resource packs","Browse and import textures",v->openLibrary("resourcepacks"),"Mods & downloads","Browse Modrinth or import a JAR",v->openLibrary("mods"));
        toolRow("Home screen shortcut","Launch this instance directly",v->JoggerInstances.pin(c,JoggerInstances.currentKey()),"Settings","Graphics, memory, Java and more",v->settings());
        add(content,text(c,"Minecraft Jogger 1.0 preview · Built on Amethyst / Pojav",12,MUTED,false),28);
        TextView about=text(c,"About, licenses & help",13,INK,true);about.setPadding(0,dp(c,14),0,dp(c,12));about.setOnClickListener(v->about());add(content,about,0);
    }
    private void addStat(LinearLayout row,String label,int count){Context c=requireContext();LinearLayout box=card(c);add(box,text(c,String.valueOf(count),23,INK,true),0);add(box,text(c,label,12,MUTED,false),7);weighted(row,box,row.getChildCount()>0);}
    private void toolRow(String a,String subA,View.OnClickListener onA,String b,String subB,View.OnClickListener onB){LinearLayout r=row(requireContext());weighted(r,tile(a,subA,onA),false);weighted(r,tile(b,subB,onB),true);add(content,r,12);}
    private View tile(String name,String sub,View.OnClickListener click){Context c=requireContext();LinearLayout tile=card(c);tile.setMinimumHeight(dp(c,108));add(tile,text(c,name,15,INK,true),0);add(tile,text(c,sub,12,MUTED,false),8);tile.setOnClickListener(click);tile.setFocusable(true);tile.setContentDescription(name+". "+sub);return tile;}
    private void instances(){Context c=requireContext();
        add(content,text(c,"Your instances",24,INK,true),24);add(content,text(c,"Each instance has its own worlds, settings, mods and resource packs.",14,MUTED,false),9);
        add(content,button(c,"+ Create instance",true,v->createDialog()),18);
        for(String key:JoggerInstances.orderedKeys()){
            MinecraftProfile p=LauncherProfiles.mainProfileJson.profiles.get(key);LinearLayout card=card(c);
            add(card,text(c,JoggerInstances.displayName(p)+(key.equals(JoggerInstances.currentKey())?"  ✓":""),20,INK,true),0);
            add(card,text(c,p.lastVersionId+"  ·  "+capitalize(JoggerInstances.loader(p)),14,MUTED,false),8);
            add(card,text(c,JoggerInstances.count(p,"saves")+" worlds  ·  "+JoggerInstances.count(p,"mods")+" mods",13,MUTED,false),12);
            LinearLayout r=row(c);weighted(r,button(c,"Play",true,v->{JoggerInstances.select(key);play();}),false);
            weighted(r,button(c,"Manage",false,v->{JoggerInstances.select(key);manage(key);}),true);add(card,r,15);
            card.setOnClickListener(v->{JoggerInstances.select(key);render();});add(content,card,15);
        }
    }
    private void library(){Context c=requireContext();
        add(content,text(c,"Your library",24,INK,true),24);add(content,text(c,"For "+JoggerInstances.displayName(JoggerInstances.current()),14,MUTED,false),9);
        add(content,button(c,"Mods · browse, import and manage",false,v->openLibrary("mods")),18);
        add(content,button(c,"Resource packs · browse and import",false,v->openLibrary("resourcepacks")),10);
        add(content,button(c,"Install a mod loader",false,v->loaders()),10);
        add(content,button(c,"Run a JAR installer",false,v->Tools.installMod(requireActivity(),false)),10);
        add(content,button(c,"Import a modpack (.mrpack or .zip)",false,v->((LauncherActivity)requireActivity()).modpackImportLauncher.launch(null)),10);
        add(content,button(c,"Java runtimes & memory",false,v->Tools.swapFragment(requireActivity(),LauncherPreferenceJavaFragment.class,"JOGGER_JAVA",null)),10);
        add(content,button(c,"Open instance files",false,v->openFiles()),10);
        add(content,button(c,"Share launcher log",false,v->Tools.shareLog(requireContext())),10);
        add(content,text(c,"Game versions and libraries download when you press Play. Mod downloads are filtered for the selected instance; a desktop mod may still require hardware or APIs unavailable on Android.",14,MUTED,false),24);
    }
    private void createDialog(){Context c=requireContext();LinearLayout form=column(c);form.setPadding(dp(c,22),dp(c,8),dp(c,22),0);
        EditText name=input(c,"Instance name");name.setText("New adventure");add(form,name,0);
        final String[] chosen={"1.21.4"};Button version=button(c,"Minecraft 1.21.4  ▾",false,null);
        version.setOnClickListener(v->VersionSelectorDialog.open(c,false,(id,snapshot)->{chosen[0]=id;version.setText("Minecraft "+id+"  ▾");}));add(form,version,14);
        add(form,text(c,"Start with vanilla, or use a loader installer. Instances keep their worlds and content separate.",14,MUTED,false),14);
        AlertDialog d=new AlertDialog.Builder(c).setTitle("New instance").setView(form).setPositiveButton("Create",null).setNeutralButton("Modded instance",(a,b)->loaders()).setNegativeButton("Cancel",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("Give your instance a name");return;}if(n.length()>80){name.setError("Use 80 characters or fewer");return;}JoggerInstances.create(n,chosen[0]);d.dismiss();tab="Instances";render();}));d.show();
    }
    private void manage(String key){MinecraftProfile p=LauncherProfiles.mainProfileJson.profiles.get(key);if(p==null)return;
        String[] options={"Select this instance","Edit version, renderer, memory or name","Add to home screen","Manage mods","Manage resource packs","Open files","Remove instance from launcher"};
        new AlertDialog.Builder(requireContext()).setTitle(JoggerInstances.displayName(p)).setItems(options,(d,n)->{
            JoggerInstances.select(key);
            switch(n){case 0:render();break;case 1:Tools.swapFragment(requireActivity(),ProfileEditorFragment.class,ProfileEditorFragment.TAG,null);break;
            case 2:JoggerInstances.pin(requireContext(),key);break;case 3:openLibrary("mods");break;case 4:openLibrary("resourcepacks");break;case 5:openFiles();break;
            case 6:if(LauncherProfiles.mainProfileJson.profiles.size()<2){toast("Keep at least one instance. Create another first.");return;}
                new AlertDialog.Builder(requireContext()).setTitle("Remove "+JoggerInstances.displayName(p)+"?").setMessage("This removes its launcher entry and disables its home screen shortcut. World and content files remain in the instance folder.").setPositiveButton("Remove",(a,b)->{JoggerInstances.remove(requireContext(),key);render();}).setNegativeButton("Cancel",null).show();break;
            }
        }).show();
    }
    private void play(){
        if(ProgressKeeper.getTaskCount()!=0){toast("Please wait for the current download or setup to finish.");return;}
        JoggerInstances.used();ExtraCore.setValue(ExtraConstants.LAUNCH_GAME,true);
    }
    private void signIn(){
        androidx.fragment.app.FragmentActivity activity=requireActivity();
        net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftDeviceLoginDialog.show(activity,account->{
            if(activity.isFinishing()||activity.isDestroyed())return;
            com.kdt.mcgui.mcAccountSpinner accounts=activity.findViewById(R.id.account_spinner);
            if(accounts!=null)accounts.acceptAccount(account);
            if(isAdded()&&content!=null)render();
        });
    }
    private void loaders(){Tools.swapFragment(requireActivity(),ProfileTypeSelectFragment.class,ProfileTypeSelectFragment.TAG,null);}
    private void controls(){
        new AlertDialog.Builder(requireContext()).setTitle("Touch controls").setMessage("Move with the left joystick and swipe the world to look. Use the right-side Mine, Use, Jump, Sneak and Sprint buttons. Tap the hotbar to select an item. You can resize and reposition controls in the editor.")
            .setPositiveButton("Edit layout",(d,w)->startActivity(new Intent(requireContext(),CustomControlsActivity.class)))
            .setNeutralButton("Touch agent",(d,w)->{CheckBox toggle=new CheckBox(requireContext());toggle.setText("Enable Jogger Java touch agent");toggle.setPadding(dp(requireContext(),20),dp(requireContext(),12),dp(requireContext(),20),dp(requireContext(),12));toggle.setChecked(LauncherPreferences.DEFAULT_PREF.getBoolean("jogger_touch_agent",true));new AlertDialog.Builder(requireContext()).setTitle("Java touch bridge").setView(toggle).setMessage("The bundled agent keeps held inputs synchronized and releases them when the game loses focus. Changes apply next launch.").setPositiveButton("Save",(a,b)->LauncherPreferences.DEFAULT_PREF.edit().putBoolean("jogger_touch_agent",toggle.isChecked()).apply()).setNegativeButton("Cancel",null).show();})
            .setNegativeButton("Close",null).show();
    }
    private void openLibrary(String folder){Bundle b=new Bundle();b.putString("folder",folder);b.putString("instance",JoggerInstances.currentKey());Tools.swapFragment(requireActivity(),JoggerLibraryFragment.class,"JOGGER_LIBRARY",b);}
    private void openFiles(){MinecraftProfile p=JoggerInstances.current();if(p!=null)Tools.openPath(requireContext(),Tools.getGameDirPath(p),false);}
    private void settings(){Tools.swapFragment(requireActivity(),LauncherPreferenceFragment.class,LauncherActivity.SETTING_FRAGMENT_TAG,null);}
    private void about(){new AlertDialog.Builder(requireContext()).setTitle("Minecraft Jogger").setMessage("Version 1.0 preview\n\nAn independent Minecraft: Java Edition launcher for Android 10 and later. A Microsoft account owning Java Edition is required for the full game.\n\nBased on Amethyst-Android and PojavLauncher (LGPL-3.0), with their OpenJDK, LWJGL and graphics components. Jogger adds its instance interface, downloads, shortcuts and Java touch agent.\n\nNot an official Minecraft product. Not approved by or associated with Mojang or Microsoft.\n\nMinecraft versions, mods and graphics drivers have different device requirements. Logs are available from Library if a game fails to launch.").setPositiveButton("Source & licenses",(d,w)->Tools.openURL(requireActivity(),"https://github.com/itswiktoragain/gpt-on-the-move/tree/minecraft-jogger/minecraft-jogger")).setNegativeButton("Close",null).show();}
    private void toast(String s){Toast.makeText(requireContext(),s,Toast.LENGTH_LONG).show();}
    private static String capitalize(String s){return s.isEmpty()?s:s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1);}
}
