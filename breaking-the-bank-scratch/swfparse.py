import os,struct,zlib,re,collections,html,sys,math,hashlib
from dataclasses import dataclass, field
from functools import lru_cache

class Reader:
 def __init__(self,b): self.d=b;self.p=0;self.bp=0
 def byte(self): self.align(); x=self.d[self.p];self.p+=1;return x
 def ui16(self):self.align();x=int.from_bytes(self.d[self.p:self.p+2],'little');self.p+=2;return x
 def ui32(self):self.align();x=int.from_bytes(self.d[self.p:self.p+4],'little');self.p+=4;return x
 def si16(self):x=self.ui16();return x-65536 if x>=32768 else x
 def raw(self,n):self.align();x=self.d[self.p:self.p+n];self.p+=n;return x
 def string(self):
  self.align();q=self.d.find(b'\x00',self.p);q=len(self.d) if q<0 else q;x=self.d[self.p:q];self.p=q+1;return x.decode('utf-8','replace')
 def align(self):
  if self.bp:self.p+=1;self.bp=0
 def ub(self,n):
  v=0
  for i in range(n):
   v=(v<<1)|((self.d[self.p]>>(7-self.bp))&1)
   self.bp+=1
   if self.bp==8:self.bp=0;self.p+=1
  return v
 def sb(self,n):
  if not n:return 0
  v=self.ub(n)
  if v&(1<<(n-1)):v-=1<<n
  return v
 def rect(self):
  n=self.ub(5);a=[self.sb(n)/20 for _ in range(4)];self.align();return a
 def matrix(self):
  hasScale=self.ub(1)
  if hasScale:
   n=self.ub(5);sx=self.sb(n)/65536;sy=self.sb(n)/65536
  else:sx=sy=1
  hasRot=self.ub(1)
  if hasRot:
   n=self.ub(5);rot0=self.sb(n)/65536;rot1=self.sb(n)/65536
  else:rot0=rot1=0
  n=self.ub(5);tx=self.sb(n)/20;ty=self.sb(n)/20;self.align()
  return (sx,rot1,rot0,sy,tx,ty)
 def cxform(self,alpha=True):
  hasAdd=self.ub(1);hasMul=self.ub(1);n=self.ub(4)
  mul=[self.sb(n)/256 for _ in range(4 if alpha else 3)] if hasMul else [1]*(4 if alpha else 3)
  add=[self.sb(n) for _ in range(4 if alpha else 3)] if hasAdd else [0]*(4 if alpha else 3)
  self.align();return (mul,add)
 def tags(self):
  while self.p<len(self.d):
   h=self.ui16();t=h>>6;s=h&63
   if s==63:s=self.ui32()
   b=self.raw(s)
   yield t,b
   if t==0:break

@dataclass
class Place:
 char: int = -1
 mat: tuple = (1,0,0,1,0,0)
 cx: tuple = ((1,1,1,1),(0,0,0,0))
 ratio: int = 0
 name: str = ''
 start: int = 0
 clip: int = 0
 blend: int = 0

@dataclass
class Timeline:
 frames:list=field(default_factory=list)
 labels:dict=field(default_factory=dict)
 actions:dict=field(default_factory=dict)
 sounds:dict=field(default_factory=dict)
 framecount:int=0
 charid:int=0

class SWF:
 def __init__(self,path):
  b=open(path,'rb').read()
  if b[:3]==b'CWS':b=b'FWS'+b[3:8]+zlib.decompress(b[8:])
  self.raw=b
  r=Reader(b);r.p=8;self.rect=r.rect();self.fps=r.ui16()/256;self.nframes=r.ui16()
  self.shapes={};self.buttons={};self.sprites={};self.sounds={};self.errors=[];self.defn={};self.root=self.parse_timeline(r,0)
 def parse_timeline(self,r,charid):
  tl=Timeline(charid=charid)
  layers={};fi=0
  for t,p in r.tags():
   try:
    if t in (2,22,32,83):
     ir=Reader(p);cid=ir.ui16()
     shape=self.parse_shape(t,ir,cid)
     self.shapes[cid]=shape;self.defn[cid]='shape'
    elif t==39:
     sr=Reader(p);cid=sr.ui16();n=sr.ui16()
     self.sprites[cid]=self.parse_timeline(sr,cid);self.defn[cid]='sprite'
    elif t==34:
     ir=Reader(p);cid=ir.ui16();self.buttons[cid]=self.parse_button(ir,cid);self.defn[cid]='button'
    elif t==14:
     ir=Reader(p);cid=ir.ui16();fmt=ir.byte();samples=ir.ui32();audio=ir.raw(len(ir.d)-ir.p)
     self.sounds[cid]=(fmt,samples,audio)
    elif t in (26,70):
     ir=Reader(p);f=ir.byte();f2=ir.byte() if t==70 else 0;depth=ir.ui16()
     move=bool(f&1);hasChar=bool(f&2);hasMat=bool(f&4);hasCx=bool(f&8)
     if not move or depth not in layers:pl=Place(start=fi)
     else:
      prev=layers[depth];pl=Place(prev.char,prev.mat,prev.cx,prev.ratio,prev.name,prev.start,prev.clip,prev.blend)
     if t==70 and (f2&8 or (f2&16 and hasChar)):
      cname=ir.string()
     if hasChar:
      cid=ir.ui16()
      if pl.char!=cid:pl.start=fi
      pl.char=cid
     if hasMat:pl.mat=ir.matrix()
     if hasCx:pl.cx=ir.cxform()
     if f&16:pl.ratio=ir.ui16()
     if f&32:pl.name=ir.string()
     if f&64:pl.clip=ir.ui16()
     if t==70:
      if f2&1: # filter list: skip unparsed
       pass
      if f2&2:pl.blend=ir.byte()
      if f2&4:pl.blend=pl.blend
     layers[depth]=pl
    elif t==28:
     ir=Reader(p);depth=ir.ui16();layers.pop(depth,None)
    elif t==43:tl.labels[fi]=p.split(b'\0')[0].decode('latin1','replace')
    elif t in (12,59):tl.actions.setdefault(fi,[]).append((t,p))
    elif t==15:tl.sounds.setdefault(fi,[]).append(('start',p))
    elif t==19:tl.sounds.setdefault(fi,[]).append(('stream',p))
    elif t==18:tl.sounds.setdefault(fi,[]).append(('head',p))
    elif t==1:
     tl.frames.append({d:Place(**vars(v)) for d,v in layers.items()});fi+=1
   except Exception as e:
    self.errors.append((t,charid,fi,str(e)))
  tl.framecount=len(tl.frames)
  return tl
 def parse_shape(self,t,r,cid):
  bounds=r.rect(); edge_bounds=None;shape4=(t==83);alpha=t>=32
  if shape4:
   edge_bounds=r.rect(); flags=r.byte()
  fills,lines=self.styles(r,t)
  nfill=r.ub(4);nline=r.ub(4)
  f0=f1=ln=0;x=y=0;fbase=lbase=0;edges=collections.defaultdict(list);lineseg=collections.defaultdict(list)
  rct=0;newct=0
  while r.p<len(r.d) and rct<200000:
   rct+=1
   if r.ub(1):
    straight=r.ub(1);n=r.ub(4)+2
    ox,oy=x,y
    if straight:
     if r.ub(1):dx=r.sb(n);dy=r.sb(n)
     else:
      vert=r.ub(1);dx=0 if vert else r.sb(n);dy=r.sb(n) if vert else 0
     x+=dx;y+=dy;cmd=('L',ox,oy,x,y)
    else:
     cdx=r.sb(n);cdy=r.sb(n);adx=r.sb(n);ady=r.sb(n)
     cx=x+cdx;cy=y+cdy;x=cx+adx;y=cy+ady;cmd=('Q',ox,oy,cx,cy,x,y)
    if f0:edges[f0].append((cmd,True))
    if f1:edges[f1].append((cmd,False))
    if ln:lineseg[ln].append((cmd,True))
   else:
    st=r.ub(5)
    if st==0:break
    if st&1:
     n=r.ub(5);x=r.sb(n);y=r.sb(n)
    if st&2:
     z=r.ub(nfill);f0=fbase+z if z else 0
    if st&4:
     z=r.ub(nfill);f1=fbase+z if z else 0
    if st&8:
     z=r.ub(nline);ln=lbase+z if z else 0
    if st&16:
     r.align();nf,nl=self.styles(r,t)
     # TODO shape array extensible, local index styles: append
     of=len(fills);ol=len(lines);fbase=of;lbase=ol;fills+=nf;lines+=nl
     nfill=r.ub(4);nline=r.ub(4);newct+=1
  return {'id':cid,'t':t,'bounds':bounds,'fills':fills,'lines':lines,'edges':edges,'lineseg':lineseg,'nrecords':rct,'nnew':newct}
 def styles(self,r,t):
  sf=lambda: r.ui16() if (k:=r.byte())==255 and t>=22 else k
  nf=sf();fills=[self.fill(r,t) for _ in range(nf)]
  nl=sf();lines=[self.line(r,t) for _ in range(nl)]
  return fills,lines
 def color(self,r,t):
  a=r.raw(4 if t>=32 else 3)
  if len(a)==3:return tuple(a)+(255,)
  return tuple(a)
 def fill(self,r,t):
  tp=r.byte()
  if tp==0:return ('solid',self.color(r,t))
  if tp in (0x10,0x12,0x13):
   m=r.matrix(); hdr=r.byte();n=hdr&15;stops=[(r.byte()/255,self.color(r,t)) for _ in range(n)];focal=r.si16()/256 if tp==0x13 else 0
   return ('gradient',tp,m,stops,focal)
  if tp in (0x40,0x41,0x42,0x43):
   id=r.ui16();m=r.matrix();return ('bitmap',tp,id,m)
  raise ValueError('unknown fill type '+hex(tp))
 def line(self,r,t):
  w=r.ui16()/20
  if t!=83:return ('line',w,self.color(r,t))
  capstart=r.ub(2);join=r.ub(2);hasfill=r.ub(1);noH=r.ub(1);noV=r.ub(1);pixelHint=r.ub(1);r.ub(5);noClose=r.ub(1);capend=r.ub(2);r.align()
  miter=r.ui16() if join==2 else 0
  style=self.fill(r,t) if hasfill else ('solid',self.color(r,t))
  return ('line',w,style,capstart,join,capend)
 def parse_button(self,r,cid):
  flags=r.byte();off=r.ui16();records=[];action_start=3+off if off else len(r.d)
  while r.p<action_start:
   a=r.byte()
   if a==0:break
   char=r.ui16();depth=r.ui16();mat=r.matrix();cx=r.cxform()
   records.append((a,char,depth,mat,cx))
   # SWF8 ButtonRecord may add complex FilterList and BlendMode.
   # Only UP state is necessary for rendering normal buttons; stop after the filtered record.
   if a&0x30:break
  acts=r.d[action_start:]
  return {'id':cid,'records':records,'actions':acts,'flags':flags}

if __name__=='__main__':
 s=SWF('/mnt/data/457209_Bank_NG.swf')
 print('stage',s.rect,'fps',s.fps,'frames',s.nframes,'root',s.root.framecount)
 print('defs',len(s.shapes),len(s.buttons),len(s.sprites),'errors',len(s.errors));print('errors',s.errors[:30])
 print('shape records',sum(v['nrecords'] for v in s.shapes.values()),'newstyle',sum(v['nnew'] for v in s.shapes.values()))
 print('shape types',collections.Counter(v['t'] for v in s.shapes.values()))
 print('fill types',collections.Counter(fill[0] for v in s.shapes.values() for fill in v['fills']))
 print('labels',s.root.labels)
 print('sample button',[(k, len(v['records']), v['actions'][:10]) for k,v in list(s.buttons.items())[:20]])
