#!/usr/bin/env python3
"""Apply Jogger 1.5.3 fixes after the verified 1.5.2 overlay. Fails on anchor drift."""
import pathlib, shutil, sys
R=pathlib.Path(sys.argv[1]).resolve()
HERE=pathlib.Path(__file__).resolve().parent
def change(filename, old, new):
    path=R/filename
    value=path.read_text()
    count=value.count(old)
    if count!=1:raise RuntimeError(f"Jogger 1.5.3 anchor count {count}: {filename} :: {old[:64]!r}")
    path.write_text(value.replace(old,new,1))

# Offline mapping for the exact 1.21.4 client: no guessing about old/new obfuscation.
assets=R/"app_pojavlauncher/src/main/assets"
assets.mkdir(parents=True,exist_ok=True)
shutil.copy2(HERE/"known-1.21.4.properties",assets/"jogger-known-1.21.4.properties")
version="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/VersionAssets.java"
change(version,
'if(key.equals(cached.getProperty("metadata.sha256")))return out;',
'if(key.equals(cached.getProperty("metadata.sha256")) && (!"1.21.4".equals(v.optString("id")) || (cached.containsKey("method.hud.health") && cached.containsKey("class.edit"))))return out;')
change(version,
'            p.setProperty("metadata.sha256",key);',
'''            if ("1.21.4".equals(v.optString("id"))) {
                Properties verified=new Properties();
                try(InputStream backup=c.getAssets().open("jogger-known-1.21.4.properties")){verified.load(backup);}
                for(String name:verified.stringPropertyNames())if(!p.containsKey(name))p.setProperty(name,verified.getProperty(name));
            }
            p.setProperty("metadata.sha256",key);''')
change(version,
'''        }catch(Exception e){android.util.Log.w("Jogger","Version mapping unavailable; retaining guarded named/legacy adapters");return null;}''',
'''        }catch(Exception e){
            android.util.Log.w("Jogger","Official mapping retrieval failed; using conservative local adapter",e);
            // Preserve the ID and game-JAR path even without official mappings.
            // Otherwise LegacyIndex cannot identify Beta/1.8 at all.
            try {
                JSONObject v=new JSONObject(new String(Files.readAllBytes(json.toPath()),StandardCharsets.UTF_8));
                Properties offline=new Properties();
                if("1.21.4".equals(v.optString("id")))
                    try(InputStream input=c.getAssets().open("jogger-known-1.21.4.properties")){offline.load(input);}
                offline.setProperty("version.id",v.optString("id",json.getName()));
                offline.setProperty("version.type",v.optString("type","unknown"));
                offline.setProperty("metadata.sha256",hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(json.toPath()))));
                File jar=new File(json.getParentFile(),json.getName().replace(".json",".jar"));
                if(jar.isFile())offline.setProperty("game.jar",jar.getAbsolutePath());
                File output=new File(c.getFilesDir(),"jogger/"+json.getName()+".mobile-v4.properties");
                if(!output.getParentFile().isDirectory()&&!output.getParentFile().mkdirs())return null;
                try(FileOutputStream save=new FileOutputStream(output)){offline.store(save,"Jogger offline version adapter");save.getFD().sync();}
                return output;
            }catch(Exception unavailable){return null;}
        }''')

overlay="app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java"
change(overlay,"float controlsTop=playing()?top+dp(44):top;","float controlsTop=top+dp(2);")
change(overlay,
'''            if(JoggerTouchBridge.requestTap(request))handler.postDelayed(()->finishHold(request,1),350);
            else finishHold(request,1);''',
'''            boolean rotated=!raySupported&&aimLegacy(gesture.startX,gesture.startY);
            handler.postDelayed(()->{
                if(request!=pendingHold||worldPointer<0||!playing())return;
                if(JoggerTouchBridge.requestTap(request))handler.postDelayed(()->finishHold(request,1),300);
                else finishHold(request,1);
            },rotated?85:0);''')
change(overlay,
'''                    if(place) {
                        pendingTap=++tapSequence;pendingGeneration=generation;
                        boolean sent=JoggerTouchBridge.requestTap(pendingTap);
                        long request=pendingTap;
                        if(sent)handler.postDelayed(()->finishTap(request,1),350);else finishTap(request,1);
                    } else handler.postDelayed(() -> {if(gestureGeneration==generation&&worldPointer<0)target(0,0,false);},100);''',
'''                    if(place) {
                        pendingTap=++tapSequence;pendingGeneration=generation;
                        long request=pendingTap;
                        boolean rotated=!raySupported&&aimLegacy(gesture.startX,gesture.startY);
                        handler.postDelayed(()->{
                            if(pendingTap!=request||pendingGeneration!=gestureGeneration)return;
                            boolean sent=JoggerTouchBridge.requestTap(request);
                            if(sent)handler.postDelayed(()->finishTap(request,0),300);
                            else finishTap(request,0); // Unknown legacy hit: attack/mining.
                        },rotated?85:0);
                    } else handler.postDelayed(() -> {if(gestureGeneration==generation&&worldPointer<0)target(0,0,false);},100);''')
change(overlay,
'''    private void pulse(int code){int source=pulseOwner--;inputs.set(source,code);handler.postDelayed(()->inputs.set(source),85);}''',
'''    /** Best-effort camera steering for Alpha/Beta/1.8 when no pixel ray patch exists.
     * This is intentionally visible camera movement, not an unsupported claim
     * of off-centre hit detection. Drag-to-look is unchanged. */
    private boolean aimLegacy(float x,float y){
        if(raySupported||!playing()||getHeight()<1||getWidth()<1)return false;
        float factor=680f/Math.max(1f,getHeight())*LauncherPreferences.PREF_MOUSESPEED;
        float dx=Math.max(-340f,Math.min(340f,(x-getWidth()*.5f)*factor));
        float dy=Math.max(-240f,Math.min(240f,(y-getHeight()*.5f)*factor));
        if(Math.abs(dx)<1f&&Math.abs(dy)<1f)return false;
        CallbackBridge.mouseX+=dx;CallbackBridge.mouseY+=dy;
        CallbackBridge.deltaX=dx;CallbackBridge.deltaY=dy;
        CallbackBridge.sendCursorPos(CallbackBridge.mouseX,CallbackBridge.mouseY);
        return true;
    }
    private void pulse(int code){int source=pulseOwner--;inputs.set(source,code);handler.postDelayed(()->inputs.set(source),85);}''')

mobile="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/MobileHooks.java"
change(mobile,
'    public static boolean hideCrosshair(){boolean yes=ClassicGameHooks.rayReady();if(yes)capabilities|=4;return yes;}',
'''    public static boolean hideCrosshair(){
        // The crosshair is a visual element; removing it must not depend on
        // a particular version's off-centre ray transform succeeding.
        boolean hide=Boolean.parseBoolean(System.getProperty("jogger.mobile.hide_crosshair","true"));
        if(hide)capabilities|=4;
        return hide;
    }''')
change(mobile,
'        int button=2; // Unknown or MISS: interact/use the held item.',
'        int button=0; // Unknown legacy hit: attack or mine, not place in empty air.')
change(mobile,
'''            Object hit=value(mc,"hitResult","hitResult","crosshairTarget","objectMouseOver","field_1765");
            button=HitClassifier.kind(hit,names);''',
'''            Object hit=value(mc,"hitResult","hitResult","crosshairTarget","objectMouseOver","field_1765");
            Field f=field(mc.getClass(),"hitResult","hitResult","crosshairTarget","objectMouseOver","field_1765");
            if(f==null){f=findLegacyHitField(mc.getClass());if(f!=null)hit=f.get(mc);}
            if(f!=null)button=HitClassifier.kind(hit,names);''')
change(mobile,"    private static void enableTouchscreen(Object mc){",
'''    /** Find one unambiguous old MovingObjectPosition-style field by enum. */
    static Field findLegacyHitField(Class<?> type){
        Field found=null;
        for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Field candidate:c.getDeclaredFields()){
            if(Modifier.isStatic(candidate.getModifiers())||candidate.getType().isPrimitive())continue;
            boolean hitType=false;
            for(Field entry:candidate.getType().getDeclaredFields())if(entry.getType().isEnum()){
                Object[] values=entry.getType().getEnumConstants();
                boolean block=false,entity=false;
                if(values!=null)for(Object item:values){block|="BLOCK".equals(item.toString());entity|="ENTITY".equals(item.toString());}
                if(block&&entity){hitType=true;break;}
            }
            if(hitType){if(found!=null)return null;try{candidate.setAccessible(true);found=candidate;}catch(RuntimeException ignored){}}
        }
        return found;
    }
    private static void enableTouchscreen(Object mc){''')
change(mobile,
'''                HudLayout.Shift shift=HudLayout.shift(kind,width,height);
                dx=shift.x;dy=shift.y;''',
'''                HudLayout.Shift shift=HudLayout.shift(kind,width,height);
                dx=shift.x;dy=shift.y;
                if(kind==5||kind==6){
                    boolean insideHealth=false;
                    for(Transform parent:transforms.get())if(parent!=frame&&parent.kind==4){insideHealth=true;break;}
                    if(!insideHealth)dy=-height+95;
                }''')

hud="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/HudLayout.java"
for old,new in [("-height+56","-height+95"),("-height+44","-height+77"),("-height+73","-height+107")]:
    file=R/hud;file.write_text(file.read_text().replace(old,new))
legacy="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/LegacyIndex.java"
change(legacy,
'''ClassNode mc=all.get("net/minecraft/client/Minecraft");if(mc==null)return;p.setProperty("class.mc",mc.name.replace('/','.'));''',
'''ClassNode mc=all.get("net/minecraft/client/Minecraft");
            if(mc==null)mc=findOldClient(all);
            if(mc==null){System.out.println("[Jogger] Legacy Minecraft client unavailable: "+id);return;}
            p.setProperty("class.mc",mc.name.replace('/','.'));''')
change(legacy,
'''            List<FieldNode> screenFields=new ArrayList<>();''',
'''            final ClassNode mainClient=mc;
            List<FieldNode> screenFields=new ArrayList<>();''')
change(legacy,'a.desc.equals("L"+mc.name+";")','a.desc.equals("L"+mainClient.name+";")')
change(legacy,'if(hasString(c,"/gui/icons.png")&&hasString(c,"/gui/gui.png"))hud.add(c);',
'''if((hasString(c,"/gui/icons.png")&&(hasString(c,"/gui/gui.png")||hasString(c,"/gui/widgets.png"))) ||
                   (hasString(c,"textures/gui/icons.png")&&(hasString(c,"textures/gui/widgets.png")||hasString(c,"textures/gui/gui.png"))))hud.add(c);''')
change(legacy,'if(hasString(m,"/gui/icons.png")&&Type.getReturnType(m.desc).getSort()==Type.VOID)renders.add(m);',
'if((hasString(m,"/gui/icons.png")||hasString(m,"textures/gui/icons.png"))&&Type.getReturnType(m.desc).getSort()==Type.VOID)renders.add(m);')
change(legacy,
'''    static void put(Properties p,String key,MethodNode m){''',
'''    /** Identify the unique LWJGL-2 game loop without assuming a class name. */
    static ClassNode findOldClient(Map<String,ClassNode> all){
        List<ClassNode> hits=new ArrayList<>();
        for(ClassNode c:all.values()){
            boolean run=false,mouse=false,keys=false;
            for(MethodNode m:c.methods){
                run|=m.name.equals("run")&&m.desc.equals("()V")&&(m.access&Opcodes.ACC_STATIC)==0;
                mouse|=calls(m,"org/lwjgl/input/Mouse","next");
                keys|=calls(m,"org/lwjgl/input/Keyboard","next");
            }
            if(run&&mouse&&keys)hits.add(c);
        }
        return hits.size()==1?hits.get(0):null;
    }
    static void put(Properties p,String key,MethodNode m){''')

build="app_pojavlauncher/build.gradle"
change(build,"versionCode 15200","versionCode 15300")
change(build,'versionName "1.5.2-mobile"','versionName "1.5.3-mobile"')
# Test expectations follow the new top control clearance.
test="scripts/mobile16-tests/HudLayoutTest.java"
for old,new in [("-259","-220"),("-271","-238"),("-242","-208")]:
    file=R/test;file.write_text(file.read_text().replace(old,new))
print("Jogger 1.5.3: modern offline mappings, legacy input, HUD and top-controls update applied.")
