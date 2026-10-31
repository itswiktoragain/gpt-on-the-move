#!/usr/bin/env python3
"""Strict no-post-1.5.1 audit: no gameplay source or resource may change."""
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1]).resolve();action=sys.argv[2];report=Path(sys.argv[3]).resolve()
allowed_changes={
    'app_pojavlauncher/build.gradle',
    'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java',
    'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/PixelDirectionIcons.java',
    'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/PixelIconLayout.java'
}
base_files={
    'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java':'c8ce1cee9db942049117f2438fa3d9c7fca4c41f44a2cb41448bacc375b81c3c',
    'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicLogic.java':'5e22e85d6c2b26536fb1a9a3b048ef4bc71eb269fd1b8eba57486e5d5b02c8e7',
    'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/JoggerTouchBridge.java':'4bb7c8be597246110f114abfefd4574971708696a36f06e7d6a904d17b72589e',
    'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/MinecraftKeyboard.java':'b17640e25f72046d184f12ee2e6bf2874e69d0002c5ef2926780779aa4613af6',
    'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/InventoryTouch.java':'7345e5bfbe2336d6977e79e7d5e80c503cbb6c225dcc5a3b70cfae78b79d0bb9',
    'jogger_touch_agent/src/main/java/com/minecraftjogger/touch/ClassicGameHooks.java':'995d633ba95e9daad8687e83fb7ef6b562b61e09ffbc3fedd72644d48e40495d',
    'jogger_touch_agent/src/main/java/com/minecraftjogger/touch/TouchRay.java':'90a6ae0208852d4ab4f1388c63d6d2f3c86ecbcdff5c35df8a45337e69cae702'
}

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def collect():
    d={}
    for folder in ['app_pojavlauncher','jogger_touch_agent','scripts']:
        for p in (root/folder).rglob('*'):
            if not p.is_file():continue
            rel=p.relative_to(root).as_posix()
            if set(p.parts)&{'.git','.gradle','build','mobile-test','mobile-test-source','test-classes'}:continue
            if rel.endswith(('.class','.jar','.apk')):continue
            d[rel]=sha(p)
    rel='app_pojavlauncher/build.gradle';d[rel]=sha(root/rel)
    return d
now=collect()
if action=='before':
    for p,known in base_files.items():
        assert now.get(p)==known,f'NOT ORIGINAL 1.5.1! {p}: {now.get(p)} (expected {known})'
    for p in allowed_changes:
        if p.endswith(('PixelDirectionIcons.java','PixelIconLayout.java')):assert p not in now, 'Later icon source already present in baseline: '+p
    assert len(now)>650,'Incomplete 1.5.1 source checkout'
    report.write_text(json.dumps({'baseline_sha256':now,'original_verified':True,'known_baseline_checks':len(base_files)},indent=2,sort_keys=True))
    print('BEFORE: exactly historical 1.5.1 source,',len(now),'files,',len(base_files),'known code hashes verified')
elif action=='after':
    baseline=json.loads(report.read_text())['baseline_sha256']
    delta={p for p in set(baseline)|set(now) if baseline.get(p)!=now.get(p)}
    assert delta==allowed_changes, 'UNEXPECTED CHANGE from 1.5.1: '+repr(sorted(delta^allowed_changes))
    for p,expected in base_files.items():
        if p.endswith('ClassicOverlay.java'):continue
        assert now[p]==expected,'CHANGED 1.5.1 GAMEPLAY CODE: '+p
    for later in ['TouchRoutingPolicy.java','MiningDragPolicy.java','CameraLookPolicy.java','WorldGesturePolicy.java','MiningLookPolicy.java']:
        assert not any(p.endswith('/'+later) for p in now),'Later input feature present: '+later
    result={'baseline':'exact original 1.5.1','original_commit':'2f4e18b4bb60708e1cfdeaedc43422437e565476',
        'source_files_audited':len(now),'modified_files':sorted(delta),'unchanged_files':len(now)-len(delta),
        'allowed_changes_only':True,'gameplay_classes_identical_to_original':True,'later_input_patches_absent':True}
    report.with_name('clean151-audit-result.json').write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
    print('AFTER: EXACTLY',len(delta),'allowed visual/version source changes; ALL',len(now)-len(delta),'other source files IDENTICAL to 1.5.1')
else:raise SystemExit('Usage audit.py <root> before|after <snapshot.json>')
