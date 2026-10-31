#!/usr/bin/env python3
"""Prove the safe keyboard-only rollback doesn't modify any 1.5.1 gameplay source."""
from pathlib import Path
import hashlib,json,sys

root=Path(sys.argv[1]).resolve(); mode=sys.argv[2]; output=Path(sys.argv[3]).resolve()
allowed={
 'app_pojavlauncher/build.gradle',
 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicOverlay.java',
 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/MinecraftKeyboard.java',
 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicTopBarLayout.java',
}
prefixes=('app_pojavlauncher/','jogger_touch_agent/','scripts/')
snapshot={}
for p in root.rglob('*'):
 if not p.is_file():continue
 rel=p.relative_to(root).as_posix()
 if not rel.startswith(prefixes):continue
 if any(part in ('.git','.gradle','build','mobile-test') for part in p.parts):continue
 snapshot[rel]=hashlib.sha256(p.read_bytes()).hexdigest()
if mode=='snapshot':
 assert not (allowed & {x for x in snapshot if x.endswith('ClassicTopBarLayout.java')}), '1.5.1 unexpectedly includes keyboard helper'
 output.write_text(json.dumps(snapshot,sort_keys=True,indent=2)+'\n')
 print('Captured 1.5.1 baseline:',len(snapshot),'files')
elif mode=='verify':
 original=json.loads(output.read_text())
 all_paths=set(original)|set(snapshot)
 diffs={p for p in all_paths if original.get(p)!=snapshot.get(p)}
 unexpected=diffs-allowed
 assert not unexpected,'UNSAFE: non-keyboard 1.5.1 source changed: '+', '.join(sorted(unexpected)[:20])
 assert diffs==allowed,'Missing or extra keyboard-only changes: '+repr(diffs)
 for key in (
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/classic/ClassicLogic.java',
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/JoggerTouchBridge.java',
  'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/jogger/mobile/InventoryTouch.java',
  'jogger_touch_agent/src/main/java/com/minecraftjogger/touch/ClassicGameHooks.java',
 ):
  assert key in original and original[key]==snapshot[key],'Changed 1.5.1 gameplay engine: '+key
 print('SAFE ROLLBACK VERIFIED: '+str(len(snapshot)-len(diffs))+' files unchanged; ONLY keyboard UI files changed')
 print('Changes:', ', '.join(sorted(diffs)))
else:raise ValueError('pass snapshot or verify')
