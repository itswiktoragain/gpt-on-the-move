import os,sys,collections,html,time,math
from swfparse import SWF,Place

def num(x):return ('%.4f'%x).rstrip('0').rstrip('.') if x else '0'
def mat(m):return 'matrix('+ ' '.join(num(x) for x in m)+')'
def rgb(c):return '#'+''.join('%02x'%v for v in c[:3])
def colorfill(c, attr='fill'):
 return f'{attr}="{rgb(c)}" '+(f'{attr}-opacity="{num(c[3]/255)}"' if c[3]!=255 else '')
def rev(cmd):
 if cmd[0]=='L':return ('L',cmd[3],cmd[4],cmd[1],cmd[2])
 return ('Q',cmd[5],cmd[6],cmd[3],cmd[4],cmd[1],cmd[2])
def pts(cmd):return (cmd[1],cmd[2]),(cmd[-2],cmd[-1])
def cmdsvg(cmd,starter):
 path=f'M {num(cmd[1]/20)} {num(cmd[2]/20)} ' if starter else ''
 if cmd[0]=='L':path+=f'L {num(cmd[3]/20)} {num(cmd[4]/20)} '
 else:path+=f'Q {num(cmd[3]/20)} {num(cmd[4]/20)} {num(cmd[5]/20)} {num(cmd[6]/20)} '
 return path

def stitched_path(segs,force_close=True):
 cs=[cmd if forward else rev(cmd) for cmd,forward in segs]
 if not cs:return ''
 ends=collections.defaultdict(list)
 for i,c in enumerate(cs):ends[pts(c)[0]].append(i)
 seen=set();paths=[]
 for i in range(len(cs)):
  if i in seen:continue
  c=cs[i];start,end=pts(c);seen.add(i);chunk=[c]
  while end!=start:
   cand=ends.get(end,[]);nexti=next((j for j in cand if j not in seen),None)
   if nexti is None:break
   seen.add(nexti);cc=cs[nexti];chunk.append(cc);end=pts(cc)[1]
  path=''.join(cmdsvg(c,j==0) for j,c in enumerate(chunk))
  paths.append(path+('Z' if force_close or end==start else ''))
 return ' '.join(paths)

class Converter:
 def __init__(self,s):
  self.s=s;self.all_defs={};self.need=set();self.grad_defs={};self.layer_cache={};self.stats=collections.Counter()
  for k in s.shapes:self.all_defs[k]=self.shape_svg(s.shapes[k])
  print('generated shape defs',len(self.all_defs),'gradients',len(self.grad_defs),flush=True)
 def fillref(self,fill,cid,j):
  if fill[0]=='solid':return colorfill(fill[1])
  if fill[0]=='bitmap':return 'fill="none"'
  _,tp,m,stops,focal=fill
  id=f'g{cid}_{j}'
  if id not in self.grad_defs:
   if tp==0x10:
    start=f'<linearGradient id="{id}" gradientUnits="userSpaceOnUse" x1="-819.2" y1="0" x2="819.2" y2="0" gradientTransform="{mat(m)}" spreadMethod="pad">'
    end='</linearGradient>'
   else:
    start=f'<radialGradient id="{id}" gradientUnits="userSpaceOnUse" cx="0" cy="0" r="819.2" fx="{num(focal*819.2)}" fy="0" gradientTransform="{mat(m)}" spreadMethod="pad">';end='</radialGradient>'
   c=[]
   for pos,col in stops:
    c.append(f'<stop offset="{num(pos)}" stop-color="{rgb(col)}" stop-opacity="{num(col[3]/255)}"/>')
   self.grad_defs[id]=start+''.join(c)+end
  return f'fill="url(#{id})"'
 def shape_svg(self,shape):
  parts=[];cid=shape['id'];
  for fid,segs in shape['edges'].items():
   if fid<1 or fid>len(shape['fills']):continue
   path=stitched_path(segs)
   if path:parts.append(f'<path d="{path}" {self.fillref(shape["fills"][fid-1],cid,fid)} fill-rule="nonzero"/>')
  for fid,segs in shape['lineseg'].items():
   if fid<1 or fid>len(shape['lines']):continue
   style=shape['lines'][fid-1];width=style[1];col=style[2]
   p=stitched_path(segs,False)
   if p:
    if col[0]=='solid':strokecolor=f'stroke="{rgb(col[1])}" stroke-opacity="{num(col[1][3]/255)}"'
    else:strokecolor='stroke="#777777"'
    parts.append(f'<path d="{p}" fill="none" {strokecolor} stroke-width="{num(width)}" stroke-linejoin="round" stroke-linecap="round"/>')
  return f'<g id="s{cid}">'+''.join(parts)+'</g>'
 def draw(self,tl,frame,depth=0,ids=None):
  if depth>16 or not tl.frames:return ''
  index=frame%len(tl.frames)
  layers=tl.frames[index];result=[]
  for dep,pl in sorted(layers.items()):
   if pl.char<0:continue
   cid=pl.char
   opacity=pl.cx[0][3] if pl.cx else 1
   if opacity<0.01:continue
   transform=mat(pl.mat)
   if cid in self.s.shapes:
    self.need.add(cid)
    result.append(f'<use href="#s{cid}" transform="{transform}"'+(f' opacity="{num(opacity)}"' if opacity!=1 else '')+'/>')
   elif cid in self.s.sprites:
    child=self.s.sprites[cid];body=self.draw(child,index-pl.start,depth+1)
    result.append(f'<g transform="{transform}"'+(f' opacity="{num(opacity)}"' if opacity!=1 else '')+'>'+body+'</g>')
   elif cid in self.s.buttons:
    btn=self.s.buttons[cid];children=[]
    for record in sorted(btn['records'],key=lambda x:x[2]):
     states,childid,depth2,mat2,cx2=record
     if not states&1:continue
     if childid in self.s.shapes:
      self.need.add(childid);children.append(f'<use href="#s{childid}" transform="{mat(mat2)}"/>')
     elif childid in self.s.sprites:
      children.append(f'<g transform="{mat(mat2)}">{self.draw(self.s.sprites[childid],0,depth+1)}</g>')
    result.append(f'<g transform="{transform}">'+''.join(children)+'</g>')
  return ''.join(result)
 def frame_svg(self,frame):
  self.need=set();body=self.draw(self.s.root,frame)
  defs=''.join(self.all_defs[i] for i in sorted(self.need))
  defs+=''.join(self.grad_defs.values())
  return f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="550" height="400" viewBox="0 0 550 400"><defs>{defs}</defs><rect width="550" height="400" fill="white"/>{body}</svg>'

if __name__=='__main__':
 import cairosvg
 s=SWF('/mnt/data/457209_Bank_NG.swf');v=Converter(s)
 print('shapes',len(s.shapes),'buttons',len(s.buttons),'errors',s.errors[:5])
 for frame in [0,75,135,153,154,160,165,200,400,757,763,1000,1250,1300,1649,2358,2735,3000,4000,4035,5000]:
  t=time.time()
  svg=v.frame_svg(frame)
  open(f'/mnt/data/bank_port/preview_{frame}.svg','w').write(svg)
  try:cairosvg.svg2png(bytestring=svg.encode(),write_to=f'/mnt/data/bank_port/preview_{frame}.png',output_width=550,output_height=400)
  except Exception as e:print('render error',frame,str(e))
  print('frame',frame,'bytes',len(svg),'used',len(v.need),'time',round(time.time()-t,2),flush=True)
