#!/usr/bin/env python3
"""Exercise the actual vanilla demo through Android touch input."""
from pathlib import Path
import re,struct,subprocess,time,xml.etree.ElementTree as ET
OUT=Path('out/emulator');OUT.mkdir(parents=True,exist_ok=True)
def adb(*args,timeout=15):return subprocess.check_output(['adb',*args],timeout=timeout)
def text(*args):return adb(*args).decode('utf-8','replace').replace('\r','')
paths=text('shell','run-as','com.wiktor.jogger','find','files/jogger/instances','-name','latest-jogger.log').strip().splitlines()
if not paths:raise RuntimeError('Minecraft never produced a game log')
logpath=paths[0]
def log():
 value=text('exec-out','run-as','com.wiktor.jogger','cat',logpath);(OUT/'demo-engine.txt').write_text(value)
 if any(x in value for x in ('Minecraft Crash Report','A fatal error','Game crashed!')):raise RuntimeError('Minecraft crashed; inspect demo-engine.txt')
 return value
def state():
 m=re.findall(r'\[Jogger\] Screen=(\S+) gui=(\d+)x(\d+)',log())
 return (m[-1][0],int(m[-1][1]),int(m[-1][2])) if m else ('',0,0)
def shot(name):
 data=adb('exec-out','screencap','-p');(OUT/name).write_bytes(data)
 if data[:8]!=b'\x89PNG\r\n\x1a\n':raise RuntimeError('Invalid screenshot')
 return struct.unpack('>II',data[16:24])
def tap(x,y):adb('shell','input','tap',str(round(x)),str(round(y)))
def wait_screen(ending,seconds=15):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  s=state()
  if s[0].endswith(ending):return s
  time.sleep(.5)
 raise RuntimeError('Expected '+ending+', observed '+str(state()))
def run():
 title=wait_screen('TitleScreen',90);pw,ph=shot('minecraft-title.png')
 tap(pw/2,(title[2]//4+58)*ph/title[2])
 joined=False;dismissed=False;end=time.monotonic()+150
 while time.monotonic()<end:
  s=state();data=log()
  if s[0].endswith('DemoIntroScreen') and not dismissed:
   shot('demo-introduction.png');adb('shell','input','keyevent','4');dismissed=True
  if s[0]=='world' and ('logged in with entity id' in data or 'joined the game' in data):joined=True;break
  time.sleep(2)
 if not joined:shot('world-startup.png');raise RuntimeError('Demo world did not become playable within the test limit')
 time.sleep(8);pw,ph=shot('minecraft-world.png')
 nums=re.findall(r'density:\s*(\d+)',text('shell','wm','density'));density=int(nums[-1])/160 if nums else 2.625
 chatx=pw/2-25.5*density;topy=26*density
 tap(chatx,topy);wait_screen('ChatScreen');time.sleep(2);shot('minecraft-chat.png')
 tap(chatx,topy);wait_screen('world');time.sleep(1)
 s=state();scale=pw/s[1];slot=20*scale;left=min(pw-slot-3*density,pw/2+91*scale)
 tap(left+slot/2,ph-slot/2);wait_screen('InventoryScreen');time.sleep(2);shot('minecraft-inventory.png')
 adb('shell','input','keyevent','4');wait_screen('world')
 fit=min(1,ph/(340*density));cell=45*density*fit;x=16*density+1.5*cell;y=ph-20*density-2.5*cell
 adb('shell','input','swipe',str(round(x)),str(round(y)),str(round(x)),str(round(y)),'700')
 time.sleep(1);shot('minecraft-after-input.png')
 (OUT/'WORLD-RESULT.txt').write_text('PASS: official 1.21.4 demo world entered; touch Chat/Leave and hotbar inventory opened/closed; forward button exercised. Physical-phone performance and complete gesture feel are not established by this test.\n')
 tap(pw-31*density,topy);time.sleep(1);adb('shell','uiautomator','dump','/sdcard/jogger-dialog.xml')
 root=ET.fromstring(text('exec-out','cat','/sdcard/jogger-dialog.xml'))
 for node in root.iter('node'):
  if node.get('text')=='Save and leave':
   b=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
   if len(b)==4:tap((b[0]+b[2])/2,(b[1]+b[3])/2);break
 else:raise RuntimeError('Save-and-leave confirmation button was not found')
 time.sleep(15);data=log();shot('after-normal-exit.png')
 if 'Saving worlds' in data or 'All dimensions are saved' in data:
  with (OUT/'WORLD-RESULT.txt').open('a') as f:f.write('PASS: normal exit requested through Jogger; Minecraft recorded world saving.\n')
 else:raise RuntimeError('Normal-exit save was not confirmed in the game log')
try:run()
finally:
 try:shot('final-screen.png')
 except Exception:pass
 try:(OUT/'demo-logcat.txt').write_bytes(adb('logcat','-d'))
 except Exception:pass
