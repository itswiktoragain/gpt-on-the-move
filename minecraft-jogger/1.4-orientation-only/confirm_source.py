#!/usr/bin/env python3
"""Fail closed if ANY 1.4.0 source beyond rendering and version is changed."""
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1]).resolve()
operation=sys.argv[2]
report=Path(sys.argv[3]).resolve()
allowed={
  'app_pojavlauncher/build.gradle',
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java',
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java',
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/RenderSurfacePolicy.java',
}
original_touch=[
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java',
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicLogic.java',
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/JoggerTouchBridge.java',
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/MinecraftKeyboard.java',
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/InventoryTouch.java',
  'jogger_touch_agent/src/main/java/com/minecraftjogger/touch/ClassicGameHooks.java',
]
def collect():
    result={}
    for folder in ['app_pojavlauncher','jogger_touch_agent','scripts']:
        d=root/folder
        if not d.is_dir():continue
        for p in d.rglob('*'):
            if not p.is_file() or any(part in ('.git','.gradle','build','mobile-test','mobile-test-source','test-classes') for part in p.relative_to(root).parts):continue
            if p.suffix in ('.class','.jar','.apk'):continue
            rel=p.relative_to(root).as_posix()
            result[rel]=hashlib.sha256(p.read_bytes()).hexdigest()
    return result
now=collect()
if operation=='before':
    for p in original_touch:assert p in now, 'Missing original 1.4 source '+p
    for p in allowed:
        if p.endswith('RenderSurfacePolicy.java'):assert p not in now,'1.5+ policy already in original 1.4'
        else:assert p in now,'Original 1.4 source missing '+p
    assert len(now)>500,'Incomplete original 1.4 checkout'
    report.write_text(json.dumps({'original':now},indent=2,sort_keys=True)+'\n')
    print('1.4 baseline fingerprinted:',len(now),'files; original touch/keyboard classes present.')
elif operation=='after':
    prior=json.loads(report.read_text())['original']
    changed={p for p in set(prior)|set(now) if prior.get(p)!=now.get(p)}
    assert changed==allowed,f'UNSAFE: expected ONLY rendering+version; actual differences: {sorted(changed)}'
    for p in original_touch:
        assert prior[p]==now[p],'UNSAFE: gameplay file changed '+p
    for p in now:
        if p.endswith(('TouchRoutingPolicy.java','WorldGesturePolicy.java','MiningDragPolicy.java','CameraLookPolicy.java','PixelDirectionIcons.java','PixelIconLayout.java','ClassicTopBarLayout.java')):
            raise AssertionError('UNSAFE: post-1.4 source included: '+p)
    result={
       'original_version':'Minecraft Jogger 1.4.0',
       'baseline_git_commit':'d3e2e9c25852f8fe1011051a80a1a97206b9f8f8',
       'version_code':'17000 (Android install upgrade only)',
       'unchanged_files':len(now)-len(allowed),
       'total_audited_files':len(now),
       'changed_files':sorted(changed),
       'original_gameplay_touch_and_keyboard_byte_identical':True,
       'post_1_4_input_features_absent':True,
    }
    report.with_name('orientation-only-source-audit.json').write_text(json.dumps(result,indent=2)+'\n')
    print('PASS:',result['unchanged_files'],'ORIGINAL 1.4 files byte-identical; ONLY version, preference, rendering and new orientation policy.')
else:raise RuntimeError('operation must be before or after')
