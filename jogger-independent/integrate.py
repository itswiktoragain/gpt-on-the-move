#!/usr/bin/env python3
"""Small, asserted integration patches applied before packaging the original source."""
from pathlib import Path
P=Path(__file__).resolve().parent
J=P/'app/src/main/java/com/wiktor/jogger'
def patch(path,old,new):
 s=path.read_text()
 if new in s:return
 if s.count(old)!=1:raise RuntimeError('Integration anchor mismatch in '+str(path))
 path.write_text(s.replace(old,new))
patch(J/'Core.java',
 'File runtime=runtime(s.c,log);File agent=',
 '''if(!loader.equals("Vanilla")&&mappings.isFile()){
    log.update("Preparing mobile controls for "+loader);
    File tiny=inside(s.cache,"versions/"+version+"-intermediary-v2.jar");
    if(!tiny.isFile())download("https://maven.fabricmc.net/net/fabricmc/intermediary/"+version+"/intermediary-"+version+"-v2.jar",tiny,null,"SHA-256");
    Mappings.write(mappings,tiny,new File(dir,"jogger-intermediary.properties"));
   }else new File(dir,"jogger-intermediary.properties").delete();
   File runtime=runtime(s.c,log);File agent=''')
# Activity rotation does not reset a running input stream; window focus loss does.
patch(J/'TouchOverlay.java',
 'else if(b.key>0)Native.press(b.key);',
 'else if(b.key>0&&!b.id.equals("jump"))Native.press(b.key);')
# No fake game/account is used for smoke tests: this debug-only entry launches --demo.
patch(J/'JoggerActivity.java',
 'home();if(BuildConfig.DEBUG&&getIntent().getBooleanExtra("probe",false))',
 'home();if(BuildConfig.DEBUG&&getIntent().getBooleanExtra("smokeDemo",false)){play(true);return;}if(BuildConfig.DEBUG&&getIntent().getBooleanExtra("probe",false))')
print('Original source integration checks passed')
