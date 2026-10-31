#!/usr/bin/env python3
"""Jogger Mobile 1.5.6: real, camera-stationary ray targeting on older clients.

Runs after touch-safe-1.5.5/apply.py. Edits are guarded by exact anchors.
"""
from pathlib import Path
import shutil
import sys

root=Path(sys.argv[1]).resolve()
here=Path(__file__).resolve().parent

def replace(relative, old, new):
    path=root/relative
    content=path.read_text()
    count=content.count(old)
    if count!=1:
        raise RuntimeError(f"Jogger 1.5.6 source drift: {relative}, anchor {count}: {old[:80]!r}")
    path.write_text(content.replace(old,new,1))

replace("app_pojavlauncher/build.gradle",
        '        versionCode 15500\n        versionName "1.5.5-mobile"',
        '        versionCode 15600\n        versionName "1.5.6-mobile"')

legacy="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/LegacyIndex.java"
replace(legacy,
        'if(c.methods.stream().anyMatch(m->calls(m,"org/lwjgl/util/glu/GLU","gluPerspective")))renderer.add(c);',
        'if(c.methods.stream().anyMatch(m->calls(m,"org/lwjgl/util/glu/GLU","gluPerspective")||calls(m,"org/lwjgl/util/glu/Project","gluPerspective")))renderer.add(c);')
replace(legacy,
        'target!=null&&trigonometric(target)',
        'target!=null&&LegacyViewDiscovery.trigonometricChain(all,target)')

# LWJGL 2.x uses Project.gluPerspective, not always GLU.gluPerspective.
# The FOV wrapper now matches either signature, without changing camera yaw.
transformer="jogger_touch_agent/src/main/java/com/minecraftjogger/touch/MobileTransformer.java"
replace(transformer,
        'call.owner.equals("org/lwjgl/util/glu/GLU")',
        '(call.owner.equals("org/lwjgl/util/glu/GLU")||call.owner.equals("org/lwjgl/util/glu/Project"))')

target=root/"jogger_touch_agent/src/main/java/com/minecraftjogger/touch/LegacyViewDiscovery.java"
target.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(here/"LegacyViewDiscovery.java",target)
print("Jogger Mobile 1.5.6: real old-client Project perspective + delegated look vectors; camera remains fixed.")
