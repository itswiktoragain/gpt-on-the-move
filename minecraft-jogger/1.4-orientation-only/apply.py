#!/usr/bin/env python3
"""ORIGINAL 1.4.0 + the THREE rendering-only changes from 1.5.1.

NEVER applies 1.5.0 features or ANY touch-control, keyboard, or icon patch.
Uses a new migration flag to ensure existing 1.5+/1.6+ installations actually
receive the orientation repair during an in-place rollback.
"""
from pathlib import Path
import sys,shutil
root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent
def substitute(path,old,new):
    p=root/path
    data=p.read_text()
    count=data.count(old)
    if count!=1:raise RuntimeError(f'1.4.0 orientation-only drift ({count}): {path}: {old[:120]!r}')
    p.write_text(data.replace(old,new,1))

# Higher versionCode is an Android package manager requirement for updating
# installations currently carrying 1.6.x. It does not mean 1.6.x source.
substitute('app_pojavlauncher/build.gradle',
           'versionCode 14000\n        versionName "1.4.0-mobile"',
           'versionCode 17000\n        versionName "1.4.0-orientation-only"')

# Exact 1.5.1 rendering policy, with a fresh migration flag to prevent the
# previous "v151" flag from suppressing this ONE-TIME orientation repair.
substitute('app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java',
'''        PREF_USE_ALTERNATE_SURFACE = DEFAULT_PREF.getBoolean("alternate_surface", isDevicePowerful);''',
'''        // 1.5.1 RENDERING FIX ONLY: Samsung Android 15+ compositor can
        // render SurfaceView sideways while the UI remains landscape.
        final boolean affectedSurface = net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy
                .needsTextureView(android.os.Build.VERSION.SDK_INT, android.os.Build.MANUFACTURER);
        final boolean alreadyMigrated = DEFAULT_PREF.getBoolean("jogger_surface_orientation_migrated_v140_only", false);
        if (net.kdt.pojavlaunch.jogger.mobile.RenderSurfacePolicy.migrateOnce(alreadyMigrated, affectedSurface)) {
            DEFAULT_PREF.edit()
                    .putBoolean("alternate_surface", false)
                    .putBoolean("jogger_surface_orientation_migrated_v140_only", true).apply();
            android.util.Log.i("Jogger", "1.4.0-only orientation fix: selecting TextureView");
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

policy=root/'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/RenderSurfacePolicy.java'
assert not policy.exists(),'Unmodified 1.4.0 cannot already contain 1.5.1 policy!'
shutil.copyfile(here/'RenderSurfacePolicy.java',policy)
print('ORIGINAL 1.4.0 with ONLY three 1.5.1 rendering changes and high Android versionCode.')
