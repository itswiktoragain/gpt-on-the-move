#!/usr/bin/env python3
"""Apply the classic controls to the pinned original Jogger/Amethyst source."""
from pathlib import Path
import sys
root=Path(sys.argv[1]); java=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch'
def replace(path, old, new, count=None):
    s=path.read_text()
    if old not in s: raise RuntimeError('Patch anchor missing in '+str(path)+': '+old[:80])
    path.write_text(s.replace(old,new) if count is None else s.replace(old,new,count))

p=root/'app_pojavlauncher/build.gradle'
replace(p,'versionCode 10000','versionCode 11000')
replace(p,'versionName "1.0.0-preview"','versionName "1.1.0-classic"')
# Separate identity avoids a signing mismatch with the recovered original APK and protects its worlds.
for folder in [root/'app_pojavlauncher',root/'app_pojavlauncher/src/main']:
    for path in folder.rglob('*'):
        if path.is_file() and path.suffix in ('.java','.xml','.gradle'):
            s=path.read_text()
            if 'com.wiktor.minecraftjogger' in s and 'com.wiktor.minecraftjogger.classic' not in s:
                path.write_text(s.replace('com.wiktor.minecraftjogger','com.wiktor.minecraftjogger.classic'))
replace(p,'"app_name", "Minecraft Jogger"','"app_name", "Minecraft Jogger Classic"')
replace(p,'"app_short_name", "Minecraft Jogger"','"app_short_name", "Minecraft Jogger Classic"')

p=java/'MainActivity.java'
replace(p,'    private FrameLayout contentFrame;','    private FrameLayout contentFrame;\n    private net.kdt.pojavlaunch.jogger.classic.ClassicOverlay mClassicOverlay;')
replace(p,'        MCOptionUtils.load(gameDirPath);','        net.kdt.pojavlaunch.jogger.classic.MobileTuning.firstRun(this, gameDirPath);\n        MCOptionUtils.load(gameDirPath);')
replace(p,'        initLayout(R.layout.activity_basemain);','''        initLayout(R.layout.activity_basemain);
        if (net.kdt.pojavlaunch.jogger.classic.MobileTuning.enabled()) {
            mClassicOverlay = new net.kdt.pojavlaunch.jogger.classic.ClassicOverlay(this, minecraftProfile, this::onClickedMenu);
            contentFrame.addView(mClassicOverlay, new FrameLayout.LayoutParams(-1, -1));
            loggerView.bringToFront();
        }''')
replace(p,'        mControlLayout.toggleControlVisible();','''        if (mClassicOverlay != null) {
            mControlLayout.setControlVisible(false);
            mDrawerPullButton.setVisibility(View.GONE);
        } else mControlLayout.toggleControlVisible();''')
replace(p,'net.kdt.pojavlaunch.jogger.JoggerControls.releaseWidgets(mControlLayout);','if (mClassicOverlay != null) mClassicOverlay.release();\n            net.kdt.pojavlaunch.jogger.JoggerControls.releaseWidgets(mControlLayout);')
replace(p,'    private void openCustomControls() {','    private void openCustomControls() {\n        if (mClassicOverlay != null) { mClassicOverlay.release(); mClassicOverlay.setVisibility(View.GONE); }')
replace(p,'        isInEditor = false;','''        isInEditor = false;
        if (mClassicOverlay != null) {
            mClassicOverlay.setVisibility(View.VISIBLE);
            mControlLayout.setControlVisible(false);
            mDrawerPullButton.setVisibility(View.GONE);
        }''')
replace(p,'    public boolean dispatchKeyEvent(KeyEvent event) {','''    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN)
            net.kdt.pojavlaunch.jogger.classic.ClassicOverlay.physicalKey(event.getKeyCode());''')

p=java/'jogger/JoggerHomeFragment.java'
s=p.read_text();a=s.index('    private void controls(){');b=s.index('    private void openLibrary(',a)
s=s[:a]+'    private void controls(){net.kdt.pojavlaunch.jogger.classic.MobileTuning.show(requireContext());}\n'+s[b:]
s=s.replace('Minecraft Jogger 1.0 preview','Minecraft Jogger 1.1 Classic').replace('Version 1.0 preview','Version 1.1 Classic')
s=s.replace('Move, look, mine and build','Classic D-pad and mobile presets').replace('An independent Minecraft: Java Edition launcher','An Amethyst/Pojav-based Minecraft: Java Edition launcher')
s=s.replace('tree/minecraft-jogger/minecraft-jogger','tree/minecraft-jogger-classic/minecraft-jogger')
p.write_text(s)

p=java/'jogger/JoggerTouchBridge.java'
replace(p,'        stop();','''        File mapping = net.kdt.pojavlaunch.jogger.classic.GameAssets.mappings(context);
        if (mapping != null) args.add("-Djogger.game.mappings=" + mapping.getAbsolutePath());
        stop();''',1)
replace(p,'    private static void dispatch(Connection current, String line) {','''    private static String lastTouchTarget = "";
    public static void sendTouchTarget(float x, float y, float aspect, boolean active) {
        synchronized (LOCK) {
            Connection current = connection;
            if (current == null) return;
            String message = "T " + epoch + " " + x + " " + y + " " + aspect + " " + (active ? 1 : 0);
            if (message.equals(lastTouchTarget)) return;
            lastTouchTarget = message;
            if (!current.queue.offer(message)) disconnect(current);
        }
    }

    private static void dispatch(Connection current, String line) {''')
replace(p,'            long incomingEpoch = Long.parseLong(f[1]);','''            long incomingEpoch = Long.parseLong(f[1]);
            if ("S".equals(f[0]) && f.length == 4 && ("PLAY".equals(f[2]) || "CHAT".equals(f[2]) || "SCREEN".equals(f[2]))) {
                MAIN.post(() -> {
                    if (connection == current && epoch == incomingEpoch)
                        net.kdt.pojavlaunch.jogger.classic.ClassicOverlay.report(f[2], "1".equals(f[3]));
                });
                return;
            }''')

# Small input correctness fixes, kept explicit for reproducibility.
p=java/'jogger/classic/ClassicOverlay.java'
replace(p,'private final ClassicLogic.Inputs inputs;','private ClassicLogic.Inputs inputs;')
replace(p,'press(id,r);','press(id,r,true);')
replace(p,'if(next!=route.button) {','if(next!=route.button || !route.valid) {')
replace(p,'press(pointer,route);','press(pointer,route,false);')
replace(p,'private void press(int pointer,Route route) {','private void press(int pointer,Route route,boolean fresh) {')
replace(p,'case UP: if(sprint.down(SystemClock.uptimeMillis()))','case UP: if(fresh && sprint.down(SystemClock.uptimeMillis()))')
replace(p,'if(route.kind==UP)sprint.up(event.getEventTime());','if(route.kind==UP)sprint.up(event.getEventTime());\n                if(route.kind==HOTBAR)lastSlot=-1;')
p=root/'jogger_touch_agent/src/main/java/com/minecraftjogger/touch/ClassicGameHooks.java'
replace(p,'import java.lang.reflect.*;','import java.lang.reflect.Field;\nimport java.lang.reflect.Modifier;')
replace(p,'catch(Throwable error){if(error instanceof IOException)return;}','catch(Throwable error){if(error instanceof IOException)return;try{Thread.sleep(500);}catch(InterruptedException end){return;}}')
print('Classic Jogger integration complete: original Amethyst/Pojav engine retained and credited.')
