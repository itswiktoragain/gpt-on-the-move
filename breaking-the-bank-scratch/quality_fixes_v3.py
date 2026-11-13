#!/usr/bin/env python3
"""Patch the original native Flash converter for sharp, reliable vanilla Scratch output."""
from pathlib import Path

p=Path(__file__).with_name('build_native.py')
s=p.read_text()
def swap(old,new):
    global s
    if old not in s: raise RuntimeError('Converter patch anchor missing: '+old[:115])
    s=s.replace(old,new)

# Rotation and anti-aliasing.
swap('PIXEL_SCALE = 480/550', 'PIXEL_SCALE = 1.35')
swap('residual=(round(rx,2),round(vy,2),round(ry,2),round(sy,2),0,0)',
     'residual=(round(rx,4),round(vy,4),round(ry,4),round(sy,4),0,0)')
swap('if abs(residual[0]-1)<0.01 and abs(residual[1])<0.01 and abs(residual[2])<0.01 and abs(residual[3]-1)<0.01:',
     'if abs(residual[0]-1)<0.00015 and abs(residual[1])<0.00015 and abs(residual[2])<0.00015 and abs(residual[3]-1)<0.00015:')
swap("recs=[(chid,transform,cx[0][3],0,state) for flags,chid,depth2,transform,cx in sorted(btn['records'],key=lambda r:r[2]) if flags&state]",
     "recs=[(chid,transform,1.0,0,state) for flags,chid,depth2,transform,cx in sorted(btn['records'],key=lambda r:r[2]) if flags&state]")
swap('pad=(3.0 if crop is None else .4)', 'pad=(7.0 if crop is None else 3.5)')
swap('x0=max(0,x0-1);y0=max(0,y0-1);x1=min(w,x1+1);y1=min(h,y1+1)',
     'x0=max(0,x0-3);y0=max(0,y0-3);x1=min(w,x1+3);y1=min(h,y1+3)')
swap("        try:im=im.quantize(colors=256,method=Image.Quantize.FASTOCTREE,dither=Image.Dither.NONE)\n        except Exception:pass\n",
     "        # Preserve full RGBA transparency: palette quantization ruins edges.\n")
swap("        if parts>1:\n            lo,hi=bound[1],bound[3]\n            dy=(hi-lo)/parts\n            bound=(bound[0],lo+part*dy,bound[2],lo+(part+1)*dy)",
     "        if parts>1:\n            lo,hi=bound[1],bound[3]\n            dy=(hi-lo)/parts\n            seam=1.5/PIXEL_SCALE\n            bound=(bound[0],max(lo,lo+part*dy-seam),bound[2],min(hi,lo+(part+1)*dy+seam))")
swap("def png(self,cid,age,residual,button_state=1,crop=None,part=0,parts=1,min_skin=12):",
     "def png(self,cid,age,residual,button_state=1,crop=None,part=0,parts=1,min_skin=12,display_scale=1.0):")
swap("        key=(statehash,residual,tuple(round(x,4) for x in (minx,miny,maxx,maxy)),int(min_skin))",
     "        displayed_extent=max(maxx-minx,maxy-miny)*STAGE_SCALE*display_scale\n        preferred=1.0 if displayed_extent>350 else PIXEL_SCALE\n        raster_scale=min(preferred,1536/max(maxx-minx,maxy-miny))\n        key=(statehash,residual,tuple(round(x,4) for x in (minx,miny,maxx,maxy)),int(min_skin),round(raster_scale,7))")
swap('w=max(1,math.ceil((maxx-minx)*PIXEL_SCALE))','w=max(1,math.ceil((maxx-minx)*raster_scale))')
swap('h=max(1,math.ceil((maxy-miny)*PIXEL_SCALE))','h=max(1,math.ceil((maxy-miny)*raster_scale))')
swap('cx=-minx*PIXEL_SCALE-x0;cy=-miny*PIXEL_SCALE-y0',
     'cx=-minx*raster_scale-x0;cy=-miny*raster_scale-y0')
swap('result=(aid,filename,round(cx,3),round(cy,3),im.width,im.height)',
     'result=(aid,filename,round(cx,3),round(cy,3),im.width,im.height,raster_scale)')
swap('aid,fname,cx,cy,width,height=png','aid,fname,cx,cy,width,height,skin_scale=png')
swap('aid,fname,cx,cy,width,height=hover','aid,fname,cx,cy,width,height,hover_scale=hover')
swap('size=s*DEFAULT_SIZE','size=s*(STAGE_SCALE/skin_scale)*100')
swap('min_skin=math.ceil(5/max(s,.00001))+2)',
     'min_skin=math.ceil(5/max(s,.00001))+2,display_scale=s)')

# Completely reset the state even when returning to the same scene.
swap("GLOBAL_VAR={'Scene':'global_scene','Frame':'global_frame','Clip Start':'global_clipstart','Clip End':'global_clipend'}",
     "GLOBAL_VAR={'Scene':'global_scene','Frame':'global_frame','Clip Start':'global_clipstart','Clip End':'global_clipend','Playback ID':'global_playback_id'}")
swap("('set','Scene',0),('set','Clip Start',0),('set','Clip End',153),('set','Frame',0),('stopall',),('timerreset',),('sound','Intro'),",
     "('set','Playback ID',0),('set','Scene',-1),('set','Clip Start',0),('set','Clip End',153),('set','Frame',0),('stopall',),('broadcast','Intro'),")
swap("        b.script('event_whenbroadcastreceived',[\n            ('set','Scene',i)",
     "        b.script('event_whenbroadcastreceived',[\n            ('change','Playback ID',1),('set','Scene',i)")
swap("local=['Current Scene','Event Index','End Index','Selected Action','Normal Costume','Hover Costume','Is Hovering']",
     "local=['Current Scene','Current Playback ID','Event Index','End Index','Selected Action','Normal Costume','Hover Costume','Is Hovering']")
swap("('hide',),('set','Current Scene',-1),('set','Selected Action',''),",
     "('hide',),('clearfx',),('set','Current Scene',-1),('set','Current Playback ID',-1),('set','Selected Action',''),")
swap("('if',E('not',E('eq',V('Current Scene'),V('Scene'))),[\n                ('set','Current Scene',V('Scene')),('hide',),('set','Selected Action',''),('set','Is Hovering',0),",
     "('if',E('or',E('not',E('eq',V('Current Scene'),V('Scene'))),E('not',E('eq',V('Current Playback ID'),V('Playback ID')))),[\n                ('hide',),('clearfx',),('goto',0,0),('heading',90),('size',100),\n                ('set','Current Scene',V('Scene')),('set','Current Playback ID',V('Playback ID')),\n                ('set','Selected Action',''),('set','Normal Costume',1),('set','Hover Costume',0),('set','Is Hovering',0),")
swap("('hide',),('set','Selected Action',''),\n                        ('set','Hover Costume',0),",
     "('hide',),('clearfx',),('set','Selected Action',''),\n                        ('set','Hover Costume',0),('set','Is Hovering',0),")

# The original SWF only names two placed objects, so describe symbols using
# scene context, visible colors and identifiable interactive objects.
NAMED = {
 (1,0):'Sky and backdrop',(2,1):'Left cloud',(4,1):'Right cloud',
 (9,0):'Desert and bank scenery',(11,0):'Henry Stickmin',
 (12,0):'Bank exterior overlay',
 (13,0):'Tunnel — shovel choice',(17,0):'Explosion — TNT choice',
 (21,0):'Laser Drill choice',(25,0):'Wrecking Ball choice',
 (29,0):'Teleporter choice',(33,0):'Disguise — money sack choice',
 (10,0):'Foreground effects',(15,1):'Shovel',(17,1):'Shovel and handle',
 (75,0):'Top wall and sign',(8,0):'Sandy desert terrain',
 (11,12):'Crane cab and wrecking ball',(24,2):'Henry — construction scene',
 (10,15):'Crane machinery',(13,1):'Bank delivery truck',
 (36,1):'Credits scrolling panel',(38,0):'Credits artwork',
 (19,54):'Sand debris and rubble',(14,1):'Explosion detail',
}
SYMBOLS = {
 3:'Left cloud',4:'Left cloud',5:'Right cloud',6:'Right cloud',
 9:'Bank exterior',73:'Sky, background and distant details',74:'Bank wall and sandy ground',
 100:'Henry Stickmin',278:'Henry Stickmin',205:'Shovel',249:'Sand scoop',
 346:'Bank entry gate',380:'Flying dirt fragment',397:'Henry digging',
 724:'Blue sky',725:'Desert dunes',726:'Bank wall',727:'Henry Stickmin',
 728:'Laser drill controller',729:'Laser drill machine',
 917:'Damaged bank wall',919:'Laser drill rotor',920:'Wall masonry',
 925:'Henry with laser controller',988:'Moving cloud',991:'Desert ridge',
 992:'Desert ridge',994:'Sand dunes',995:'Sand dunes',
 997:'Cactus and desert plants',998:'Cactus and desert plants',
 1001:'Yellow wrecking-ball crane',1008:'Henry Stickmin',
 1159:'Wrecking ball and cable',1161:'Crane cable',1163:'Yellow crane boom',
 1164:'Yellow tracked crane',1166:'Henry Stickmin',
 1409:'Bank wall and desert',1410:'Henry and teleporter',
 1587:'Desert road',1588:'Desert road',1604:'Bank delivery truck',
 1611:'Henry carrying the money sack',1954:'Credits scrolling art',
 1956:'Credits background',1973:'Credits artwork',1989:'Credits graphic',
}
def describe_sprite(layer,events_by_scene,art,costumes,assets):
    if layer in NAMED:return NAMED[layer]
    active=[];chars=[];actions=[]
    for si,evs in enumerate(events_by_scene):
        for _,state in evs:
            if state is None:continue
            cid=state[0];active.append(si);chars.append(cid)
            if cid in art.swf.buttons:
                label=art.button_target(cid)
                if label:actions.append(label)
    if actions:
        label=collections.Counter(actions).most_common(1)[0][0]
        return label.replace('LazerDrill','Laser Drill').replace('WreckingBall','Wrecking Ball')+' choice button'
    hints=[SYMBOLS[c] for c in chars if c in SYMBOLS]
    if hints:return collections.Counter(hints).most_common(1)[0][0]
    scene=SCENES[collections.Counter(active).most_common(1)[0][0]][0].replace('LazerDrill','Laser Drill').replace('WreckingBall','Wrecking Ball')
    im=Image.open(io.BytesIO(assets.data[costumes[0]['md5ext']])).convert('RGBA')
    im.thumbnail((64,64),Image.Resampling.LANCZOS)
    pix=[(r,g,b) for r,g,b,a in im.getdata() if a>180]
    if not pix:return f'{scene} — translucent detail'
    r=sum(a for a,b,c in pix)/len(pix)
    g=sum(b for a,b,c in pix)/len(pix)
    b=sum(c for a,b,c in pix)/len(pix)
    w,h=im.size
    dark=max(r,g,b)<105; gray=max(r,g,b)-min(r,g,b)<22
    if dark and h>w*1.6:kind='Character outline or cable'
    elif dark and w>h*2.5:kind='Outline and shadow'
    elif gray and r>190 and w>h*2.5:kind='Cloud or light panel'
    elif gray and r<150:kind='Metal fixture or bank masonry'
    elif r>g*1.35 and r>b*1.35:kind='Red effect or equipment'
    elif g>r*1.15 and g>b*1.15:kind='Green vegetation or device'
    elif b>r*1.25 and b>g*1.08:kind='Blue equipment or glass'
    elif r>140 and g>110 and b<120:kind='Sand or yellow machinery'
    elif dark:kind='Ink outlines and moving details'
    elif w>h*2.5:kind='Scenery panel'
    elif h>w*1.6:kind='Figure or vertical prop'
    else:kind='Animated prop and effects'
    return f'{scene} — {kind}'

swap('def sprite_script(assets,layer,events_by_scene,art,part=0,parts=1):',
     'def sprite_script(assets,layer,events_by_scene,art,part=0,parts=1):')
# Insert the helper right before the sprite builder.
s=s.replace('def sprite_script(assets,layer,events_by_scene,art,part=0,parts=1):',
            'def sprite_script(assets,layer,events_by_scene,art,part=0,parts=1):',1)
# Source file receives the helper as top-level globals.
s=s.replace("def sprite_script(assets,layer,events_by_scene,art,part=0,parts=1):",
            "def sprite_script(assets,layer,events_by_scene,art,part=0,parts=1):",1)
swap("'name':f'SWF depth {root:02d}.{inner:02d}'+suffix,",
     "'name':describe_sprite(layer,events_by_scene,art,costumes,assets)+suffix,")
swap("                targets.append(sprite)\n                assetcostumes+=len(sprite['costumes']);events+=nevents",
     "                existing={t['name'] for t in targets[1:]}\n                base=sprite['name'];number=2\n                while sprite['name'] in existing:\n                    sprite['name']=f'{base} ({number})';number+=1\n                targets.append(sprite)\n                assetcostumes+=len(sprite['costumes']);events+=nevents")

# Prepend description functions to module so no source-index rewriting is needed.
s=s.replace('def sprite_script(assets,layer,events_by_scene,art,part=0,parts=1):',
            '# Automatic semantic naming enabled\\n'
            'def sprite_script(assets,layer,events_by_scene,art,part=0,parts=1):',1)
s=s.replace('                    before[g]=signature\n                    before[g]=signature','                    before[g]=signature')
# A single 'exec' of this module's definitions is simpler than rewriting the
# original parser; insert the definitions just above sprite_script.
# The code above defines NAMED, SYMBOLS and describe_sprite in this patch process
# only, so take their source from a standalone module alongside the converter.
p.write_text(s)
print('Applied high-quality native Scratch patches to',p)
