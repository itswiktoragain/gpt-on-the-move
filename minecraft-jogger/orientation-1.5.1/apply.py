#!/usr/bin/env python3
"""Jogger Mobile 1.5.1: migrate Samsung Android 15/16 gameplay to TextureView once."""
import pathlib, shutil, sys
root=pathlib.Path(sys.argv[1]).resolve()
here=pathlib.Path(__file__).resolve().parent

def substitute(name, old, new):
    p=root/name
    data=p.read_text()
    if data.count(old)!=1:
        raise RuntimeError(f"1.5.1 source anchor count {data.count(old)} in {p}: expected 1")
    p.write_text(data.replace(old,new,1))

substitute('app_pojavlauncher/build.gradle',
           '        versionCode 15000\n        versionName "1.5.0-mobile"',
           '        versionCode 15100\n        versionName "1.5.1-mobile"')
substitute('app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java',
           '        PREF_USE_ALTERNATE_SURFACE = DEFAULT_PREF.getBoolean("alternate_surface", isDevicePowerful);',
           '''        // On some recent Samsung builds the SurfaceView buffer can be
        // compositor-rotated while Jogger's Android control overlay stays
        // landscape. Move existing installations to TextureView ONCE, then
        // respect any subsequent user choice in Video settings.
        final boolean affectedSurface = net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy
                .needsTextureView(android.os.Build.VERSION.SDK_INT, android.os.Build.MANUFACTURER);
        final boolean alreadyMigrated = DEFAULT_PREF.getBoolean("jogger_surface_orientation_migrated_v151", false);
        if (net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy.migrateOnce(alreadyMigrated, affectedSurface)) {
            DEFAULT_PREF.edit()
                    .putBoolean("alternate_surface", false)
                    .putBoolean("jogger_surface_orientation_migrated_v151", true).apply();
            android.util.Log.i("Jogger", "Orientation repair: switching game surface to TextureView");
        }
        PREF_USE_ALTERNATE_SURFACE = DEFAULT_PREF.getBoolean("alternate_surface", isDevicePowerful);''')
surface='app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java'
substitute(surface,
           '''                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    mNativeSurface = new Surface(surface);''',
           '''                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    final int w = net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy.evenScaled(
                            width, LauncherPreferences.PREF_SCALE_FACTOR);
                    final int h = net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy.evenScaled(
                            height, LauncherPreferences.PREF_SCALE_FACTOR);
                    if (w > 0 && h > 0) surface.setDefaultBufferSize(w, h);
                    Log.i(TAG, "TextureView EGL surface " + width + "x" + height
                            + " (render buffer " + w + "x" + h + ")");
                    mNativeSurface = new Surface(surface);''')
substitute(surface,
           '''                    if(isCalled) {
                        JREUtils.setupBridgeWindow(mNativeSurface);
                        if (sdlEnabled) SDLSurface.setNativeSurface(mNativeSurface);
                        return;
                    }''',
           '''                    if(isCalled) {
                        JREUtils.setupBridgeWindow(mNativeSurface);
                        if (sdlEnabled) SDLSurface.setNativeSurface(mNativeSurface);
                        refreshSize(true);
                        return;
                    }''')
substitute(surface,
           '''                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                    // Don't use the scaled resolution, SDL doesn't work like that,''',
           '''                public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                    if (width > 0 && height > 0)
                        surface.setDefaultBufferSize(
                                net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy.evenScaled(width, LauncherPreferences.PREF_SCALE_FACTOR),
                                net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy.evenScaled(height, LauncherPreferences.PREF_SCALE_FACTOR));
                    // Don't use the scaled resolution, SDL doesn't work like that,''')
p=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/RenderSurfacePolicy.java'
p.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2(here/'RenderSurfacePolicy.java',p)
print('Applied Jogger Mobile 1.5.1: compositor orientation repair')
