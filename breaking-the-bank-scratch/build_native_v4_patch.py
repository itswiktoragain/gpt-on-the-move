"""Apply v4 timing, transform, transparency and mask fixes to native converter."""
from pathlib import Path
import sys
p=Path(sys.argv[1] if len(sys.argv)>1 else Path(__file__).with_name('build_native.py'))
s=p.read_text()

def swap(a,b,where=None):
 global s
 n=s.count(a)
 if n!=1: raise RuntimeError(f'Missing or ambiguous anchor ({n}): {a[:130]!r}')
 s=s.replace(a,b,1)

# Keep 6 decimal places for SWF affine matrices without exploding asset count.
swap('residual=(round(rx,4),round(vy,4),round(ry,4),round(sy,4),0,0)',
     'residual=(round(rx,6),round(vy,6),round(ry,6),round(sy,6),0,0)')
swap('if abs(residual[0]-1)<0.00015 and abs(residual[1])<0.00015 and abs(residual[2])<0.00015 and abs(residual[3]-1)<0.00015:',
     'if abs(residual[0]-1)<0.0000005 and abs(residual[1])<0.0000005 and abs(residual[2])<0.0000005 and abs(residual[3]-1)<0.0000005:')

# SWF mask flag is a clipping instruction, never a visible shape.
swap('self.stop_clips={}', 'self.stop_clips={}\n        self.mask_defs={}')
start=s.index('    def contents(self,cid,age,state=1,depth=0):')
end=s.index('\n    def button_target(self,cid):',start)
original=s[start:end]
replacement='''    def contents(self,cid,age,state=1,depth=0):
        """Return nested symbol artwork, with Flash clipDepth masks and transforms."""
        if cid in self.swf.shapes:
            sh=self.swf.shapes[cid]
            x0,x1,y0,y1=sh['bounds']
            self.used_shapes.add(cid)
            return (f'<use href="#s{cid}"/>',{cid},(x0,y0,x1,y1))
        if depth>22:return ('',set(),None)
        if cid in self.swf.sprites:
            tl=self.swf.sprites[cid]
            idx=self.index(cid,age)
            key=('movie',cid,idx)
            if key in self.cache:return self.cache[key]
            recs=[(z,p.char,p.mat,p.cx[0][3],idx-p.start,1,p.clip)
                  for z,p in sorted(tl.frames[idx].items())]
        elif cid in self.swf.buttons:
            key=('button',cid,state)
            if key in self.cache:return self.cache[key]
            btn=self.swf.buttons[cid]
            recs=[(z,chid,transform,1.0,0,state,0) for flags,chid,z,transform,cx in
                  sorted(btn['records'],key=lambda r:r[2]) if flags&state]
            if not recs and state!=1:
                return self.contents(cid,age,1,depth+1)
        else:return ('',set(),None)
        shapes=set();bounds=None;pieces=[];active=[]
        for z,child,m,opacity,frame,chstate,clipdepth in recs:
            active=[item for item in active if item[0]>=z]
            if opacity<.001:continue
            body,used,box=self.contents(child,frame,chstate,depth+1)
            if not body or box is None:continue
            shapes|=used
            if clipdepth>z:
                # Flash clip depth: the mask does not draw; it clips only later
                # siblings with depth <= clipdepth. It can be nested.
                maskid=f'clip_{cid}_{idx if cid in self.swf.sprites else 0}_{z}'
                self.mask_defs[maskid]=(f'<clipPath id="{maskid}" clipPathUnits="userSpaceOnUse">'
                                        f'<g transform="{svg_matrix(m)}">{body}</g></clipPath>')
                active.append((clipdepth,maskid,compose_bounds(box,m)))
                continue
            geom=compose_bounds(box,m)
            ink=f'<g transform="{svg_matrix(m)}"'+(f' opacity="{fmt(opacity)}"' if opacity!=1 else '')+'>'+body+'</g>'
            for last,maskid,maskbounds in active:
                if maskbounds is not None:
                    geom=intersect(geom,maskbounds)
                ink=f'<g clip-path="url(#{maskid})">{ink}</g>'
            if geom is None:continue
            bounds=union(bounds,geom);pieces.append(ink)
        result=(''.join(pieces),shapes,bounds)
        self.cache[key]=result
        return result
'''
s=s[:start]+replacement+s[end:]
# intersection boxes used for masked clips
swap('def union(A,B):', '''def intersect(A,B):
    if A is None or B is None:return None
    result=(max(A[0],B[0]),max(A[1],B[1]),min(A[2],B[2]),min(A[3],B[3]))
    return result if result[0]<result[2] and result[1]<result[3] else None


def union(A,B):''')

# RGB fades reuse a large vector shape. Cache the original raster once,
# then recolor pixels rather than asking CairoSVG to redraw it for every frame.
swap('self.mask_defs={}', 'self.mask_defs={}\n        self.base_png_cache=collections.OrderedDict()')
swap('raw=cairosvg.svg2png(bytestring=svg.encode(),output_width=w,output_height=h)',
'''basekey=key[:-1]
        raw=self.base_png_cache.get(basekey)
        if raw is None:
            raw=cairosvg.svg2png(bytestring=svg.encode(),output_width=w,output_height=h)
            if rgb is not None:
                self.base_png_cache[basekey]=raw
                if len(self.base_png_cache)>60:self.base_png_cache.popitem(last=False)''')

# Better viewport guard is needed when the Flash raster edges have AA pixels.
swap('for wx in (-10,560):\n            for wy in (-10,410):',
     'for wx in (-36,586):\n            for wy in (-36,436):')
swap('pad=(7.0 if crop is None else 3.5)', 'pad=(9.0 if crop is None else 5.0)')

# Include color transform at the correct level of the display list.
swap('def png(self,cid,age,residual,button_state=1,crop=None,part=0,parts=1,min_skin=12,display_scale=1.0):',
     'def png(self,cid,age,residual,button_state=1,crop=None,part=0,parts=1,min_skin=12,display_scale=1.0,rgb=None):')
swap('round(raster_scale,7))', 'round(raster_scale,7),rgb)')
swap('defs+=\'\'.join(gradrefs.values())', '''defs+=''.join(gradrefs.values())
        used_masks=set(re.findall(r'url\\(#(clip_[^)]*)\\)',body))
        while used_masks:
            k=used_masks.pop()
            if k not in self.mask_defs or k in gradrefs:continue
            v=self.mask_defs[k]
            gradrefs[k]=v
            used_masks.update(re.findall(r'url\\(#(clip_[^)]*)\\)',v))
        defs+=''.join(v for k,v in gradrefs.items() if k.startswith('clip_'))''')
# Image recoloring follows Flash: OutRGB = mul * inRGB + add. Alpha is done by ghost effect.
swap("im=Image.open(io.BytesIO(raw)).convert('RGBA')", '''im=Image.open(io.BytesIO(raw)).convert('RGBA')
        if rgb is not None:
            multipliers,offsets=rgb
            if any(abs(v-1)>1e-8 for v in multipliers) or any(abs(v)>1e-8 for v in offsets):
                import numpy as np
                ar=np.asarray(im).copy()
                arr=ar[:,:,:3].astype('float32')
                ar[:,:,:3]=np.clip(arr*np.asarray(multipliers,dtype='float32')+
                                    np.asarray(offsets,dtype='float32'),0,255).astype('uint8')
                im=Image.fromarray(ar,'RGBA')''')
# Keep the pivot INSIDE the actual raster, moving the position along its affine
# vector. This prevents Scratch off-stage fencing from shifting remote pivots.
swap('''cx=-minx*raster_scale-x0;cy=-miny*raster_scale-y0
        result=(aid,filename,round(cx,3),round(cy,3),im.width,im.height,raster_scale)''', '''oldcx=-minx*raster_scale-x0;oldcy=-miny*raster_scale-y0
        newcx=im.width/2;newcy=im.height/2
        dx=(newcx-oldcx)/raster_scale;dy=(newcy-oldcy)/raster_scale
        result=(aid,filename,newcx,newcy,im.width,im.height,raster_scale,dx,dy)''')

# Enable in-engine frame barriers instead of each sprite independently lagging.
swap("BROADCAST_IDS={name:f'msg_{i}' for i,(name,_,_) in enumerate(SCENES)}", "BROADCAST_IDS={name:f'msg_{i}' for i,(name,_,_) in enumerate(SCENES)}\nBROADCAST_IDS.update({'Paint Frame':'msg_paint','Reset Graphics':'msg_reset'})")
swap("if op in ('set','change'):", '''if op=='call':
            return self.create('procedures_call',parent,mutation={'tagName':'mutation','children':[],
                'proccode':args[0],'argumentids':'[]','warp':'true'})
        if op in ('set','change'):''')
swap("if op=='broadcast':\n            bid=self.create('event_broadcast',parent)", "if op in ('broadcast','broadcastwait'):\n            bid=self.create('event_broadcastandwait' if op=='broadcastwait' else 'event_broadcast',parent)")
swap('    def script(self,hat,stmts,fields=None,x=0,y=0):', '''    def warp_procedure(self,name,stmts,x=0,y=0):
        definition=self.create('procedures_definition',None,top=True,x=x,y=y)
        proto=self.create('procedures_prototype',definition,shadow=True,
            mutation={'tagName':'mutation','children':[], 'proccode':name,
                      'argumentids':'[]','argumentnames':'[]','argumentdefaults':'[]','warp':'true'})
        self.blocks[definition]['inputs']['custom_block']=[1,proto]
        self.blocks[definition]['next']=self.sequence(stmts,definition)
        return definition

    def script(self,hat,stmts,fields=None,x=0,y=0):''')

# Stage drives a single clock and waits until every sprite has painted before
# rendering frame. No per-sprite async clocks, tearing, or accumulated delays.
start=s.index("    b.script('event_whenflagclicked',[",s.index('def stage_script(assets):'))
end=s.index("    for i,(name,start,end) in enumerate(SCENES):",start)
s=s[:start]+'''    b.script('event_whenflagclicked',[
        ('set','Playback ID',0),('set','Scene',-1),('set','Clip Start',0),('set','Clip End',153),
        ('set','Frame',-1),('stopall',),('broadcastwait','Reset Graphics'),
        ('broadcastwait','Intro'),
        ('forever',[
            ('set','Frame',E('add',V('Clip Start'),E('floor',E('mul',E('timer'),FRAME_RATE)))),
            ('if',E('gt',V('Frame'),V('Clip End')),[('set','Frame',V('Clip End'))]),
            ('broadcastwait','Paint Frame'),
            ('wait',0.009),
        ]),
    ],x=45,y=55)
'''+s[end:]

# Named action and sprite palettes stay unchanged, with RGB tinting added.
swap('def extract_groups(s,art,frame):', '''def combine_color(outer,inner):
    om,oa=outer;im,ia=inner
    mul=tuple(om[i]*im[i] for i in range(3))
    add=tuple(om[i]*ia[i]+oa[i] for i in range(3))
    return (mul,add)


def extract_groups(s,art,frame):''')
swap("groups[key]=(child.char,i-child.start,M,alpha)",
     "groups[key]=(child.char,i-child.start,M,alpha,combine_color(place.cx,child.cx))")
swap("groups[(rootdepth,0)]=(place.char,0,place.mat,place.cx[0][3])",
     "groups[(rootdepth,0)]=(place.char,0,place.mat,place.cx[0][3],(tuple(place.cx[0][:3]),tuple(place.cx[1][:3])))")
s=s.replace('cid,age,M,alpha=state', 'cid,age,M,alpha,rgb=state')
# fix second unpack because above replace() replaces all occurrences
swap('''round(direction,3),round(scale,4),round(alpha,3),cid if cid in art.swf.buttons else 0)''',
     '''round(direction,5),round(scale,6),round(alpha,5),rgb,cid if cid in art.swf.buttons else 0)''')
# Dynamic masked/transform sprites need img key including root color matrix.
s=s.replace('min_skin=math.ceil(5/max(s*(STAGE_SCALE/PIXEL_SCALE),.00001))+3,display_scale=s)', 'min_skin=math.ceil(5/max(s*(STAGE_SCALE/PIXEL_SCALE),.00001))+3,display_scale=s,rgb=rgb)')
swap('''aid,fname,cx,cy,width,height,skin_scale=png''', '''aid,fname,cx,cy,width,height,skin_scale,pivotdx,pivotdy=png''')
swap('''aid,fname,cx,cy,width,height,hover_scale=hover''', '''aid,fname,cx,cy,width,height,hover_scale,_,_=hover''')
swap('''x=(M[4]-275)*STAGE_SCALE
                    y=(200-M[5])*STAGE_SCALE''', '''co=math.cos(math.radians(direction));sn=math.sin(math.radians(direction))
                    fx=M[4]+s*(co*pivotdx-sn*pivotdy)
                    fy=M[5]+s*(sn*pivotdx+co*pivotdy)
                    x=(fx-275)*STAGE_SCALE
                    y=(200-fy)*STAGE_SCALE''')
# sprites should no longer have global green-flag tick loops. One per-frame
# receiving script triggers a warp-mode procedure, then stage continues.
start=s.index("    b.script('event_whenflagclicked',[",s.index('def sprite_script('))
end=s.index("    b.script('event_whenthisspriteclicked',[",start)
s=s[:start]+'''    update=[
        ('if',E('or',E('not',E('eq',V('Current Scene'),V('Scene'))),E('not',E('eq',V('Current Playback ID'),V('Playback ID')))),[
            ('hide',),('clearfx',),('goto',0,0),('heading',90),('size',100),
            ('set','Current Scene',V('Scene')),('set','Current Playback ID',V('Playback ID')),
            ('set','Selected Action',''),('set','Normal Costume',1),('set','Hover Costume',0),('set','Is Hovering',0),
            ('set','Event Index',I('Start Index',E('add',V('Scene'),1))),
            ('set','End Index',I('End Index',E('add',V('Scene'),1))),
        ]),
        ('set','Last Index',0),
        ('if',E('and',E('gt',V('Event Index'),0),E('lt',V('Event Index'),E('add',V('End Index'),1))),[
            ('repeatuntil',E('or',E('gt',V('Event Index'),V('End Index')),
                                E('gt',I('At',V('Event Index')),V('Frame'))),[
                ('set','Last Index',V('Event Index')),('change','Event Index',1),
            ]),
        ]),
        ('if',E('gt',V('Last Index'),0),[
            ('ifelse',E('gt',I('Costume',V('Last Index')),0),[
                ('switch',I('Costume',V('Last Index'))),
                ('goto',I('X',V('Last Index')),I('Y',V('Last Index'))),
                ('heading',I('Direction',V('Last Index'))),
                ('size',I('Size',V('Last Index'))),
                ('ghost',I('Ghost',V('Last Index'))),
                ('set','Selected Action',I('Action',V('Last Index'))),
                ('set','Normal Costume',I('Costume',V('Last Index'))),
                ('set','Hover Costume',I('Hover',V('Last Index'))),
                ('set','Is Hovering',0),('show',),
            ],[
                ('hide',),('clearfx',),('set','Selected Action',''),
                ('set','Hover Costume',0),('set','Is Hovering',0),
            ]),
        ]),
        ('if',E('gt',V('Hover Costume'),0),[
            ('ifelse',E('touchmouse'),[
                ('if',E('eq',V('Is Hovering'),0),[('switch',V('Hover Costume')),('set','Is Hovering',1)]),
            ],[
                ('if',E('eq',V('Is Hovering'),1),[('switch',V('Normal Costume')),('set','Is Hovering',0)]),
            ]),
        ]),
    ]
    b.warp_procedure('paint object',update,x=420,y=40)
    b.script('event_whenbroadcastreceived',[('call','paint object')],
             fields={'BROADCAST_OPTION':['Paint Frame',BROADCAST_IDS['Paint Frame']]},x=40,y=40)
    b.script('event_whenbroadcastreceived',[
        ('hide',),('clearfx',),('set','Current Scene',-1),('set','Current Playback ID',-1),
        ('set','Event Index',0),('set','End Index',0),('set','Last Index',0),
        ('set','Selected Action',''),('set','Hover Costume',0),('set','Is Hovering',0),
    ],fields={'BROADCAST_OPTION':['Reset Graphics',BROADCAST_IDS['Reset Graphics']]},x=40,y=160)
'''+s[end:]
swap("local=['Current Scene','Current Playback ID','Event Index','End Index','Selected Action','Normal Costume','Hover Costume','Is Hovering']",
     "local=['Current Scene','Current Playback ID','Event Index','End Index','Last Index','Selected Action','Normal Costume','Hover Costume','Is Hovering']")
# filename / metadata marker
swap("'agent':'Breaking the Bank native display-list conversion (no extensions)'",
     "'agent':'Breaking the Bank native objects Quality v4 - synchronized transforms, clipping and color (no extensions)'")
p.write_text(s)
print('Patched:',p,'size',len(s))