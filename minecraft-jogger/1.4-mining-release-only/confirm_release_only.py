#!/usr/bin/env python3
"""Byte-for-byte audit of baseline 1.4.0+orientation source versus mining-release patch."""
from pathlib import Path
import hashlib,json,sys
root=Path(sys.argv[1]).resolve()
mode=sys.argv[2]
snapshot=Path(sys.argv[3]).resolve()
overlay='app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java'
bridge='app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/JoggerTouchBridge.java'
allowed={
  'app_pojavlauncher/build.gradle',overlay,bridge
}
def compute():
    hashes={}
    for base in ('app_pojavlauncher','jogger_touch_agent','scripts'):
        for p in (root/base).rglob('*'):
            if not p.is_file():continue
            parts=p.relative_to(root).parts
            if any(x in ('.git','.gradle','build','mobile-test','mobile-test-source') for x in parts):continue
            if p.suffix in ('.class','.apk','.jar'):continue
            hashes[p.relative_to(root).as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
    return hashes
def block(t,a,z):
    first=t.index(a)
    return t[first:t.index(z,first)]
now=compute()
if mode=='before':
    assert overlay in now and bridge in now
    assert len(now)>700, 'Incomplete original 1.4.0 source'
    snapshot.write_text(json.dumps(now,sort_keys=True,indent=2)+'\n')
    (root.parent/'out'/'mining-original-overlay.java').write_text((root/overlay).read_text())
    (root.parent/'out'/'mining-original-bridge.java').write_text((root/bridge).read_text())
    print('Mining-fix baseline snapshotted:',len(now),'source files')
elif mode=='after':
    original=json.loads(snapshot.read_text())
    diff={p for p in set(original)|set(now) if original.get(p)!=now.get(p)}
    assert diff==allowed, 'UNAPPROVED changes: '+repr(sorted(diff^allowed))
    old=(root.parent/'out'/'mining-original-overlay.java').read_text()
    current=(root/overlay).read_text()
    oldbridge=(root.parent/'out'/'mining-original-bridge.java').read_text()
    newbridge=(root/bridge).read_text()
    # Keep all original camera and touch MOVE/target/game-mode routing verbatim.
    guarded=[
       ('    public static void report(String state, boolean capability) {','    public static void physicalKey('),
       ('    @Override public void onGrabState(boolean grabbed) {','    @Override public void onOptionChanged()'),
       ('    private boolean playing() {','    private float dp('),
       ('        if(action==MotionEvent.ACTION_MOVE) {','        if(action==MotionEvent.ACTION_CANCEL)'),
       ('    private boolean targetEnabled()','    public void release()')
    ]
    for first,last in guarded:
        assert block(old,first,last)==block(current,first,last),'CAMERA CHANGE forbidden: '+first
    assert old.count('CallbackBridge.sendCursorPos(')==current.count('CallbackBridge.sendCursorPos(')
    assert oldbridge.count('CallbackBridge.sendCursorPos(')==newbridge.count('CallbackBridge.sendCursorPos(')
    assert oldbridge.count('CallbackBridge.isGrabbing(')==newbridge.count('CallbackBridge.isGrabbing(')
    assert old.count('handler.postDelayed(hold,holdDelay())')==current.count('handler.postDelayed(hold,holdDelay())')
    # The whole 1.4 agent, inventory, keyboard, mappings and native libraries
    # must remain identical to the orientation-only baseline.
    for p in original:
        if p not in allowed: assert original[p]==now[p],'Not original 1.4 source: '+p
    for name in ('TouchRoutingPolicy.java','CameraLookPolicy.java','WorldGesturePolicy.java','MiningDragPolicy.java','PixelDirectionIcons.java','ClassicTopBarLayout.java'):
        assert not any(p.endswith('/'+name) for p in now),'Bad later control features: '+name
    report={
       'baseline':'Minecraft Jogger 1.4.0 + orientation correction ONLY',
       'new_version_code':17100,
       'audited_source_files':len(now),
       'unchanged_source_files':len(now)-len(diff),
       'only_changed_source_files':sorted(diff),
       'original_camera_move_grab_and_target_code_byte_identical':True,
       'original_game_agent_inventory_keyboard_and_controls_assets_identical':True,
       'later_1_5_to_1_6_patches_absent':True,
       'scope':'mining release and stale mouse ACK only'
    }
    (root.parent/'out'/'mining-release-source-audit.json').write_text(json.dumps(report,indent=2)+'\n')
    print('PASS: unchanged',len(now)-len(diff),'files; ONLY',len(diff),'mining-release/version files modified. Camera fully preserved.')
else:raise SystemExit('usage: confirm_release_only.py root before|after snapshot')
