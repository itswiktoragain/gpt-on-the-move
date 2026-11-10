#!/usr/bin/env python3
"""Rebuild the Scratch world's appearance using real country borders and user-supplied Unity textures.

Usage:
    python visual_rebuild.py <previous.sb3> <Unity-textures.zip> <output.sb3>

Dependencies: Pillow, countryinfo, numpy.
All artwork is recovered from the user's legally supplied Unity game files.
"""
import sys, zipfile, json
from pathlib import Path
SOURCE = Path(sys.argv[1]) if len(sys.argv)>1 else Path("/mnt/data/Plague_Inc_Remastered_Scratch.sb3")
TEXTURES = Path(sys.argv[2]) if len(sys.argv)>2 else Path("/mnt/data/Plague_Inc_Unity_Textures_Extracted.zip")
DEST = Path(sys.argv[3]) if len(sys.argv)>3 else Path("/mnt/data/Plague_Inc_Country_Map_Rebuild.sb3")
with zipfile.ZipFile(SOURCE) as zin:
    pj=json.loads(zin.read("project.json"))
st=pj["targets"][0]
def li(name):
    return next(item[1] for item in st["lists"].values() if item[0]==name)
from PIL import Image,ImageDraw,ImageFont,ImageFilter,ImageOps,ImageEnhance
import numpy as np, math, random, io, zipfile, json, os, glob
from pathlib import Path
from collections import defaultdict
DZ=zipfile.ZipFile(TEXTURES)
png_names=DZ.namelist()
def source_prefix(prefix):
 n=next(n for n in png_names if n.startswith(prefix))
 return Image.open(io.BytesIO(DZ.read(n))).convert('RGBA')
TEX={k:source_prefix(k) for k in ['0343_','0410_','0443_','0587_','0851_','0949_','0968_','0970_','0536_','0593_']}
import countryinfo
based=Path(os.path.dirname(countryinfo.__file__))/ 'data'
idx={f.stem:f for f in based.glob('*.json')}
alias={'United States':'united_states_of_america','Congo':'congo_democratic_republic_of_the','Myanmar':'burma','South Korea':'korea_south'}
country_names=li("Names")
def country_file(name):
 return idx.get(alias.get(name,name.lower().replace(' ','_')))
def geo_rings(js):
 geo=js['geoJSON']['features'][0]['geometry']
 for poly in (geo['coordinates'] if geo['type']=='MultiPolygon' else [geo['coordinates']]):
  for j,ring in enumerate(poly):
   if j==0: yield ring
def project(lon,lat):
 return (27+(lon+180)/360*906, 102+(85-lat)/145*475)
def draw_polys(draw,js,fill,outline=None,width=1):
 for ring in geo_rings(js):
  xy=[project(*p) for p in ring]
  if len(xy)>=3:
   draw.polygon(xy,fill=fill)
   if outline:draw.line(xy+[xy[0]],fill=outline,width=width,joint='curve')
fontreg='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
fontbold='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
fnt=lambda n,b=False:ImageFont.truetype(fontbold if b else fontreg,n)
def rrect(dr,rect,fill,outline=None,r=14,width=2):
 dr.rounded_rectangle(rect,radius=r,fill=fill,outline=outline,width=width)
def txt(dr,xy,s,n=17,color=(255,255,255,255),bold=False,anchor=None):
 dr.text(xy,s,font=fnt(n,bold),fill=color,anchor=anchor)
# texture from user's Unity atlas
bg_tex=TEX['0851_'].crop((5,775,380,1020)).convert('RGB').resize((960,720),Image.Resampling.BILINEAR)
back=Image.blend(bg_tex,Image.new('RGB',(960,720),(10,27,43)),.77).convert('RGBA')
pixmap=Image.new('RGBA',(960,720));g=ImageDraw.Draw(pixmap)
for lon in range(-150,181,30):
 x,y=project(lon,0);g.line([(int(x),85),(int(x),590)],fill=(95,144,163,26),width=1)
for lat in [-45,-30,0,30,60]:
 x,y=project(0,lat);g.line([(27,int(y)),(933,int(y))],fill=(95,144,163,26),width=1)
# draw countries with base varying shades
allinfo=[]
for p in based.glob('*.json'):
 try:
  js=json.loads(p.read_text()); next(geo_rings(js))
  allinfo.append(js)
 except Exception:pass
allinfo.sort(key=lambda a:-float(a.get('area') or 0))
for js in allinfo:
 name=js.get('name','')
 v=(sum(map(ord,name))%6)
 draw_polys(g,js,(37+v,53+v,63+v,244),(80,108,117,180),1)
# coast accent tiny
back=Image.alpha_composite(back,pixmap)
# small top and bottom decorative labels
under=Image.new('RGBA',(960,720));gd=ImageDraw.Draw(under,'RGBA')
for t,pos in [('NORTH AMERICA',(177,289)),('SOUTH AMERICA',(278,451)),('EUROPE',(478,195)),('AFRICA',(482,406)),('ASIA',(671,304)),('AUSTRALIA',(804,521))]:
 txt(gd,pos,t,16,(187,214,215,48),True,anchor='mm')
# overlay port/air route spots, small not giant circles
for lo,la in [(-74,40),(-118,34),(-3,51),(2,49),(28,-26),(139,35),(103,1),(151,-33),(-43,-23),(77,28),(100,14),(31,30)]:
 x,y=project(lo,la);gd.ellipse((x-3,y-3,x+3,y+3),fill=(131,211,220,200))
for lo,la in [(-0,51),(-77,38),(-46,-23),(31,30),(117,31),(103,1),(18,-34),(150,-33)]:
 x,y=project(lo,la);gd.rectangle((x-2,y-2,x+2,y+2),fill=(210,183,108,200))
back=Image.alpha_composite(back,under)
UI=Image.new('RGBA',(960,720));u=ImageDraw.Draw(UI,'RGBA')
# drop shadows
u.rectangle((0,0,960,88),fill=(5,9,16,240))
u.rectangle((0,0,960,4),fill=(176,42,52,255))
u.line((0,86,960,86),fill=(67,111,136,220),width=2)
u.rectangle((0,580,960,720),fill=(6,10,17,248))
u.line((0,579,960,579),fill=(76,121,142,255),width=2)
u.rectangle((0,582,960,596),fill=(11,24,33,230))
u.line((0,646,960,646),fill=(35,71,87,220),width=2)
# emblem panel
rrect(u,(18,15,84,76),(35,10,18,255),(152,38,54,255),14,2)
symbol=TEX['0343_'].resize((59,59),Image.Resampling.LANCZOS)
UI.alpha_composite(symbol,(22,17))
txt(u,(100,18),'PLAGUE INC.',24,(249,245,240,255),True)
txt(u,(100,49),'OUTBREAK SIMULATION',11,(156,173,183,255),True)
u.line((268,18,268,75),fill=(63,92,105,210),width=2)
# indicators area header
for label,x in [('DNA',291),('INFECTED',405),('DEAD',583),('CURE',742),('DAY',865)]:
 txt(u,(x,18),label,12,(166,195,205,255),True)
# cure research line
rrect(u,(744,64,841,71),(28,54,68,255),r=4)
# status dots
u.ellipse((917,21,929,33),fill=(34,177,119,255))
txt(u,(934,19),'LIVE',12,(172,207,202,255),True)
# bottom banner
txt(u,(24,594),'GLOBAL INFECTION',11,(156,185,194,255),True)
txt(u,(308,594),'WORLD RESEARCH',11,(156,185,194,255),True)
txt(u,(612,594),'WORLD STATUS',11,(156,185,194,255),True)
rrect(u,(24,615,270,629),(27,49,61,255),(69,102,115,255),5,1)
rrect(u,(308,615,567,629),(27,49,61,255),(69,102,115,255),5,1)
rrect(u,(613,611,936,635),(24,43,56,255),(61,100,123,255),4,1)
txt(u,(629,622),'Awaiting your first infection',13,(190,215,221,255),False,anchor='lm')
# bottom clickable controls same old hitbox positions, rebranded
buttons=[
 ((16,655,152,712),'MENU','≡',False),
 ((163,655,308,712),'PAUSE','Ⅱ',False),
 ((318,655,471,712),'SPEED','»',False),
 ((480,655,673,712),'WORLD','◎',False),
 ((680,655,945,712),'DISEASE','☣',True)]
for box,title,ic,active in buttons:
 rrect(u,box,(84,20,32,255) if active else (22,41,56,245),(220,67,66,255) if active else (74,119,145,255),9,2)
 u.line((box[0]+6,box[1]+2,box[2]-6,box[1]+2),fill=(200,91,92,180) if active else (135,179,198,150),width=1)
 if title=='DISEASE':
  sym=TEX['0443_'].resize((36,36),Image.Resampling.LANCZOS)
  UI.alpha_composite(sym,(699,667))
  txt(u,(806,683),title,19,(248,236,231,255),True,anchor='lm')
 else:
  txt(u,(box[0]+22,box[1]+23),title,18,(228,238,239,255),True,anchor='lm')
back=Image.alpha_composite(back,UI)
# preview 480px


def geo_rings(js):
 geo=js.get('geoJSON') or {}
 if not geo.get('features'):
  return
 geo=geo['features'][0]['geometry']
 for poly in (geo['coordinates'] if geo['type']=='MultiPolygon' else [geo['coordinates']]):
  for j,ring in enumerate(poly):
   if j==0:yield ring
missing_poly={
 'Myanmar':[(92,28),(95,27),(97,25),(98,24),(98,21),(101,20),(100,17),(98,15),(97,12),(98,9),(96,9),(94,17),(92,20),(92,28)],
 'Singapore':[(103.55,1.52),(103.55,1.17),(104.12,1.17),(104.12,1.52)]
}
def shapes_for(name):
 f=country_file(name)
 js=json.loads(f.read_text()) if f else {}
 mask=Image.new('L',(960,720),0);dd=ImageDraw.Draw(mask)
 rings=list(geo_rings(js))
 if not rings:rings=[missing_poly.get(name,[])]
 for ring in rings:
  xy=[project(float(lon),float(lat)) for lon,lat in ring]
  if len(xy)>2:
   dd.polygon(xy,fill=255)
   if len(xy)<5:dd.ellipse((xy[0][0]-3,xy[0][1]-3,xy[0][0]+3,xy[0][1]+3),fill=255)
 return mask
names=country_names
countries_shapes={}
for n in names:
 mask=shapes_for(n)
 bbox=mask.getbbox()
 countries_shapes[n]=(mask,bbox)


# Re-use the native Scratch compiler helpers from the previously delivered build source.
from hashlib import md5
import zipfile,io,json,os
with zipfile.ZipFile(str(SOURCE)) as z:
    project=json.loads(z.read('project.json'))
    pkgfiles={f.filename:z.read(f.filename) for f in z.infolist() if f.filename != 'project.json'}
stage=project['targets'][0]; targets=project['targets']
def sid(name,l=False):
    for k,val in (stage['lists'] if l else stage['variables']).items():
        if val[0]==name:return k
    raise KeyError(name)
def put_asset(blob,ext='png'):
    h=md5(blob).hexdigest();fname=f'{h}.{ext}';pkgfiles[fname]=blob
    return h,fname
def image_bytes(img):
    bio=io.BytesIO();img.save(bio,'PNG',compress_level=5);return bio.getvalue()
def install_costume(target,name,im,center=None,index=None):
    m,filename=put_asset(image_bytes(im))
    w,h=im.size
    desc={'assetId':m,'name':str(name),'bitmapResolution':2,'dataFormat':'png',
          'md5ext':filename,'rotationCenterX':center[0] if center else w/2,
          'rotationCenterY':center[1] if center else h/2}
    if index is None:target['costumes'].append(desc)
    else: target['costumes'][index]=desc
    return desc
def new_sprite(name):
    target={'isStage':False,'name':name,'variables':{},'lists':{},'broadcasts':{},'blocks':{},
            'comments':{},'currentCostume':0,'costumes':[],'sounds':[],'volume':100,
            'layerOrder':len(targets),'visible':False,'x':0,'y':0,'size':100,
            'direction':90,'draggable':False,'rotationStyle':'dont rotate'}
    targets.append(target);return target
class Expr:
    def __init__(self,op,inputs=None,fields=None,shadow=False):
        self.op=op;self.inputs=inputs or {};self.fields=fields or {};self.shadow=shadow
class Stmt:
    def __init__(self,op,inputs=None,fields=None,sub=None,sub2=None):
        self.op=op;self.inputs=inputs or {};self.fields=fields or {};self.sub=sub;self.sub2=sub2
class Lit:
    def __init__(self,value):self.value=value
def Q(op,**kwargs):return Expr(op,kwargs)
def bin(op,a,b):
    keys={'add':('NUM1','NUM2'),'subtract':('NUM1','NUM2'),'multiply':('NUM1','NUM2'),
          'divide':('NUM1','NUM2'),'mod':('NUM1','NUM2'),'random':('FROM','TO'),
          'lt':('OPERAND1','OPERAND2'),'gt':('OPERAND1','OPERAND2'),
          'equals':('OPERAND1','OPERAND2'),'and':('OPERAND1','OPERAND2'),
          'or':('OPERAND1','OPERAND2')}[op]
    return Expr('operator_'+op,{keys[0]:a,keys[1]:b})
def A(a,b):return bin('add',a,b)
def S(a,b):return bin('subtract',a,b)
def M(a,b):return bin('multiply',a,b)
def D(a,b):return bin('divide',a,b)
def R(a,b):return bin('random',a,b)
def GT(a,b):return bin('gt',a,b)
def LT(a,b):return bin('lt',a,b)
def EQ(a,b):return bin('equals',a,b)
def AND(a,b):return bin('and',a,b)
def OR(a,b):return bin('or',a,b)
def MOD(a,b):return bin('mod',a,b)
def NOT(a):return Expr('operator_not',{'OPERAND':a})
def ROUND(a):return Expr('operator_round',{'NUM':a})
def V(name):return Expr('data_variable',fields={'VARIABLE':name})
def ITEM(lst,index):return Expr('data_itemoflist',{'INDEX':index},fields={'LIST':lst})
def SET(name,value):return Stmt('data_setvariableto',{'VALUE':value},{'VARIABLE':name})
def CHG(name,value):return Stmt('data_changevariableby',{'VALUE':value},{'VARIABLE':name})
def REPL(name,index,value):return Stmt('data_replaceitemoflist',{'INDEX':index,'ITEM':value},{'LIST':name})
def IF(test,yes,no=None):return Stmt('control_if_else' if no is not None else 'control_if',{'CONDITION':test},sub=yes,sub2=no)
def REPEAT(n,body):return Stmt('control_repeat',{'TIMES':n},sub=body)
def FOREVER(body):return Stmt('control_forever',sub=body)
def WAIT(t):return Stmt('control_wait',{'DURATION':t})
def BCAST(name):return Stmt('event_broadcast',{'BROADCAST_INPUT':Expr('event_broadcast_menu',fields={'BROADCAST_OPTION':[name,'bc_'+name]},shadow=True)})
def SOUND(name):return Stmt('sound_play',{'SOUND_MENU':Expr('sound_sounds_menu',fields={'SOUND_MENU':[name,None]},shadow=True)})
def STMT(op,inputs=None,fields=None):return Stmt(op,inputs,fields)
def FLAG():return Stmt('event_whenflagclicked')
def WHEN(name):return Stmt('event_whenbroadcastreceived',fields={'BROADCAST_OPTION':[name,'bc_'+name]})
def CLICK():return Stmt('event_whenthisspriteclicked')
def GO(x,y):return Stmt('motion_gotoxy',{'X':x,'Y':y})
def SHOW():return STMT('looks_show')
def HIDE():return STMT('looks_hide')
def COST(name):return Stmt('looks_switchcostumeto',{'COSTUME':Expr('looks_costume',fields={'COSTUME':[str(name),None]},shadow=True)})
def CREATE():return Stmt('control_create_clone_of',{'CLONE_OPTION':Expr('control_create_clone_of_menu',fields={'CLONE_OPTION':['_myself_',None]},shadow=True)})
def DELETE():return STMT('control_delete_this_clone')
def WHENCLONE():return STMT('control_start_as_clone')
def GLIDE(seconds,x,y):return Stmt('motion_glidesecstoxy',{'SECS':seconds,'X':x,'Y':y})
class Compiler:
    def __init__(self,target):
        self.t=target;self.count=0;self.local={}
    def localvar(self,name,val=0):
        ident='lv_'+self.t['name'].replace(' ','')+'_'+name
        self.t['variables'][ident]=[name,val]
        self.local[name]=ident
    def id(self):
        self.count+=1
        return 'new_'+self.t['name'].replace(' ','')+'_'+f'{self.count:06d}'
    def resolve(self,name,kind):
        if kind=='VARIABLE':
            if name in self.local:return self.local[name]
            return sid(name,False)
        return sid(name,True)
    def field(self,key,content):
        if key in ('VARIABLE','LIST'):
            mid=self.resolve(content,key)
            return [content,mid]
        return content
    def input(self,value,parent):
        if isinstance(value,Lit):value=value.value
        if isinstance(value,(str,int,float)):
            return [1,[4,str(value)]] if isinstance(value,(int,float)) else [1,[10,value]]
        if isinstance(value,Expr):
            key=self.compile_expr(value,parent)
            return [1,key] if value.shadow else [3,key,[4,'0']]
        raise TypeError((type(value),value))
    def compile_expr(self,node,parent):
        ident=self.id()
        n={'opcode':node.op,'next':None,'parent':parent,'inputs':{},'fields':{k:self.field(k,v) for k,v in node.fields.items()},
           'shadow':node.shadow,'topLevel':False}
        self.t['blocks'][ident]=n
        for k,v in node.inputs.items():n['inputs'][k]=self.input(v,ident)
        return ident
    def compile_stmt(self,node,parent):
        ident=self.id()
        n={'opcode':node.op,'next':None,'parent':parent,'inputs':{},'fields':{k:self.field(k,v) for k,v in node.fields.items()},
           'shadow':False,'topLevel':False}
        self.t['blocks'][ident]=n
        for k,v in node.inputs.items():n['inputs'][k]=self.input(v,ident)
        if node.sub:n['inputs']['SUBSTACK']=[2,self.compile_chain(node.sub,ident)]
        if node.sub2:n['inputs']['SUBSTACK2']=[2,self.compile_chain(node.sub2,ident)]
        return ident
    def compile_chain(self,stmts,parent):
        head=None;before=None
        for n in stmts:
            if n is None:continue
            ident=self.compile_stmt(n,before or parent)
            if before:self.t['blocks'][before]['next']=ident
            if head is None:head=ident
            before=ident
        return head
    def script(self,hat,content,x=40,y=40):
        head=self.compile_stmt(hat,None)
        self.t['blocks'][head].update({'topLevel':True,'x':x,'y':y})
        if content:self.t['blocks'][head]['next']=self.compile_chain(content,head)
        return head
# Replace only the existing world backdrop, preserving other gameplay screens.
desc=next(c for c in stage['costumes'] if c['name']=='world')
new=install_costume(stage,'world',back,index=stage['costumes'].index(desc))
print('stage world graphic',new['md5ext'],len(pkgfiles))

# Convert each country border into six genuine Scratch color/interaction costumes.
node=next(t for t in targets if t['name']=='Country Nodes')
node['blocks']={};node['variables']={};node['costumes']=[]
node['x']=node['y']=0;node['visible']=False;node['size']=100;node['rotationStyle']='dont rotate'
region_colors=[(33,57,67,10),(244,184,68,185),(247,139,53,202),(227,84,52,214),(189,46,53,220),(124,19,48,230)]
region_details=[]
for number,name in enumerate(country_names,1):
    mask,bbox=countries_shapes[name]
    if bbox is None:
        print('unmapped',name);continue
    left,top,right,bottom=bbox
    pad=2
    box=(max(0,left-pad),max(0,top-pad),min(960,right+pad),min(720,bottom+pad))
    small=mask.crop(box)
    outline_mask=small.filter(ImageFilter.MaxFilter(3))
    for lev,(r,g,b,a) in enumerate(region_colors):
        W,H=small.size
        rgba=Image.new('RGBA',(W,H),(r,g,b,0))
        rgba.putalpha(small.point(lambda v:int(v*a/255)))
        stroke=Image.new('RGBA',(W,H),(231,198,105,0) if lev else (141,208,217,0))
        stroke.putalpha(outline_mask.point(lambda v:int(v*(142 if lev else 40)/255)))
        stroke.alpha_composite(rgba)
        install_costume(node,f'{number}-{lev}',stroke,center=(480-box[0],360-box[1]))
    region_details.append((name,bbox))
print('Region fill costumes',len(node['costumes']),'file assets',len(pkgfiles))

comp=Compiler(node)
comp.localvar('cid',1)
comp.localvar('level',0)
comp.script(FLAG(),[HIDE(),SET('cid',1),REPEAT(72,[CREATE(),CHG('cid',1)])],x=40,y=30)
ratio=D(ITEM('Infected',V('cid')),ITEM('Population',V('cid')))
levels=[
 IF(GT(ITEM('Infected',V('cid')),0),[SET('level',1)]),
 IF(GT(ratio,0.002),[SET('level',2)]),
 IF(GT(ratio,0.03),[SET('level',3)]),
 IF(GT(ratio,0.20),[SET('level',4)]),
 IF(GT(ratio,0.65),[SET('level',5)])
]
costume_num=A(A(M(S(V('cid'),1),6),V('level')),1)
comp.script(WHENCLONE(),[
 FOREVER([
  IF(EQ(V('Screen'),3),
   [GO(0,0), SET('level',0)]+levels+[
    STMT('looks_switchcostumeto',{'COSTUME':costume_num}),SHOW()
   ],[HIDE()]),
  WAIT(0.50)
 ])
],x=35,y=175)
comp.script(CLICK(),[
 IF(EQ(V('Screen'),3),[
  IF(EQ(V('Started'),0),[
   SET('Started',1),SET('StartCountry',V('cid')),
   SET('SelectedCountry',V('cid')),
   REPL('Infected',V('cid'),15),CHG('DNA',5),
   SOUND('select')
  ],[
   SET('SelectedCountry',V('cid')),
   SET('Screen',5),BCAST('refresh'),SOUND('select')
  ])
 ])
],x=35,y=345)
print('Country sprite blocks',len(node['blocks']),"top scripts",len([b for b in node['blocks'].values() if b.get('topLevel')]))

# Old HUD is kept for the evolution and country-detail screens, but disabled on world map.
oldhud=next(t for t in targets if t['name']=='Live HUD')
oldhud['blocks']['liv_000016']['inputs']['OPERAND2']=[1,[4,'-999']]
# Make new crisp UI numerals, not the prior 128-clone debug-style screen display.
readout=new_sprite('World HUD Readouts')
colors=['#F1CE79','#FF9A86','#E6EBEA','#74C1D0','#F7F8FA']
alphabet="0123456789.KMB%+-"
for n,col in enumerate(colors,1):
 for ch in alphabet:
  glyph=Image.new('RGBA',(26,34),(0,0,0,0))
  g=ImageDraw.Draw(glyph)
  g.text((13,17),ch,font=fnt(25,True),fill=col,anchor='mm',stroke_width=0)
  install_costume(readout,f'{n}_{ch}',glyph)
rc=Compiler(readout)
for v,initial in [('row',1),('pos',1),('out',''),('letter','')]:
 rc.localvar(v,initial)
rc.script(FLAG(),[
 HIDE(),SET('row',1),
 REPEAT(5,[SET('pos',1),REPEAT(9,[CREATE(),CHG('pos',1)]),CHG('row',1)])
],x=35,y=20)
get_letter=Expr('operator_letter_of',{'LETTER':V('pos'),'STRING':V('out')})
make_costume=Expr('operator_join',{'STRING1':Expr('operator_join',{'STRING1':V('row'),'STRING2':'_'}),'STRING2':V('letter')})
print('costume test',make_costume.op)
rows=[
 IF(EQ(V('row'),1),[SET('out',ROUND(V('DNA')))]),
 IF(EQ(V('row'),2),[SET('out',V('DisplayCases'))]),
 IF(EQ(V('row'),3),[SET('out',V('DisplayDead'))]),
 IF(EQ(V('row'),4),[SET('out',Expr('operator_join',{'STRING1':ROUND(V('Cure')),'STRING2':'%'}))]),
 IF(EQ(V('row'),5),[SET('out',ROUND(V('Day')))])
]
locations=[]
for r,x in enumerate([-89,-31,58,137,195],1):
 locations.append(IF(EQ(V('row'),r),[GO(A(x,M(S(V('pos'),1),9.1)),152)]))
rc.script(WHENCLONE(),[
 FOREVER([
  IF(EQ(V('Screen'),3),[
   SET('out','')]+rows+[
   SET('letter',get_letter)
   ]+locations+[
   IF(AND(NOT(EQ(V('letter'),'')),NOT(EQ(V('letter'),' '))),[
    STMT('looks_switchcostumeto',{'COSTUME':make_costume}),SHOW()
   ],[HIDE()])
  ],[HIDE()]),
  WAIT(0.28)
 ])
],x=35,y=115)
print('Readout costumes',len(readout['costumes']),'blocks',len(readout['blocks']))

news=next(t for t in targets if t['name']=='World News Alerts')
newsmsgs=[
 'Awaiting the first case','Border transmission detected','New region infected',
 'Air travel carries infection','Cargo vessel contaminated','Research efforts begin',
 'Outbreak spreads worldwide','Global emergency declared','Hospitals under pressure',
 'Millions infected','Disease mutates','Fatality rate increases','Spores reach new region'
]
news['costumes']=[]
for i,s in enumerate(newsmsgs):
    pill=Image.new('RGBA',(323,28),(0,0,0,0));ds=ImageDraw.Draw(pill)
    ds.rounded_rectangle((0,0,322,27),radius=5,fill=(23,44,56,255),outline=(49,84,104,255),width=1)
    ds.text((12,14),s,font=fnt(14,False),fill=(218,235,238),anchor='lm')
    install_costume(news,str(i),pill)
nb=news['blocks']['new_WorldNewsAlerts_000002']
nb['inputs']['X']=[1,[4,'146']]
nb['inputs']['Y']=[1,[4,'-131']]
start=next(t for t in targets if t['name']=='Start Prompt')
prompt=Image.new('RGBA',(560,58),(0,0,0,0));prdr=ImageDraw.Draw(prompt)
rrect(prdr,(0,0,559,56),(12,26,35,238),(103,157,182,252),10,2)
prdr.ellipse((18,17,40,39),fill=(149,47,62),outline=(235,161,166),width=2)
txt(prdr,(52,27),'Choose a country to begin your outbreak',23,(238,249,249),True,anchor='lm')
install_costume(start,'Select country',prompt,index=0)
start['blocks']['sta_000002']['inputs']['Y']=[1,[4,'117']]
print('News and prompt restyled')

def meter_sprite(name,geom,tint,expression):
 target=new_sprite(name)
 span=geom[2]-geom[0];height=geom[3]-geom[1]
 for percent in range(101):
  im=Image.new('RGBA',(span,height),(0,0,0,0))
  draw=ImageDraw.Draw(im)
  width=round(span*percent/100)
  if width:
   draw.rounded_rectangle((0,0,max(1,width-1),height-1),radius=min(4,height//2),fill=(*tint,241))
   draw.line((2,1,max(2,width-3),1),fill=(255,255,255,115),width=1)
  install_costume(target,str(percent),im)
 code=Compiler(target)
 top=V(expression) if isinstance(expression,str) else expression
 code.script(FLAG(),[
  FOREVER([
   IF(EQ(V('Screen'),3),[
    GO((geom[0]+geom[2]-960)/4,(720-geom[1]-geom[3])/4),
    STMT('looks_switchcostumeto',{'COSTUME':A(1,Expr('operator_mathop',
         {'NUM':top},{'OPERATOR':['floor',None]}))}),SHOW()
   ],[HIDE()]),WAIT(0.35)
  ])
 ])
 return target
# infection ratio never exceeds 100%, and cure from game engine is clamped to 100.
infection_pct=Expr('operator_mathop',{'NUM':M(D(V('GlobalInfected'),V('WorldPopulation')),100)},
                   {'OPERATOR':['floor',None]})
cure_pct=Expr('operator_mathop',{'NUM':V('Cure')},{'OPERATOR':['floor',None]})
meter_sprite('Infection Bar',(24,615,270,629),(214,61,65),infection_pct)
meter_sprite('Research Bar',(308,615,567,629),(89,201,220),cure_pct)
meter_sprite('Cure Mini Bar',(744,64,841,71),(89,201,220),cure_pct)
print('Meters built',len(targets),len(pkgfiles))

# Ensure each sprite has a deterministic layer order and that all assets referenced in Scratch are embedded.
for i, target in enumerate(targets):
    target['layerOrder']=i
project.setdefault('meta',{})['agent']='Country-border Scratch rebuild (native Scratch, original Unity imagery)'
project['meta']['version']='3.0'
needed={v['md5ext'] for t in targets for v in t['costumes']+t['sounds']}
missing=needed-set(pkgfiles)
print('missing',missing,'targets',len(targets),'blocks',sum(len(t['blocks']) for t in targets),
      'region costumes',len(node['costumes']),'assets',len(needed))
assert not missing
out=DEST
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=5) as z:
 z.writestr('project.json',json.dumps(project,separators=(',',':'),ensure_ascii=False))
 for fn in sorted(needed): z.writestr(fn,pkgfiles[fn])
print(out,out.stat().st_size)

# Reserve enough clone slots for traffic animation and late-game particles.
oldhud=next(t for t in targets if t['name']=='Live HUD')
oldhud['blocks']['liv_000004']['inputs']['TIMES']=[1,[4,'6']]
# Update in-memory SB3 and validate Scratch's forward/reverse block links.
from collections import Counter
invalid=[]
for target in targets:
    ids=target['blocks']
    for bid,block in ids.items():
        for field in ('next','parent'):
            link=block.get(field)
            if link and link not in ids:
                invalid.append((target['name'],bid,field,link))
        for key,value in block.get('inputs',{}).items():
            link=value[1] if isinstance(value,list) and len(value)>1 else None
            if isinstance(link,str) and link not in ids:
                invalid.append((target['name'],bid,key,link))
print('invalid block links',invalid[:10],len(invalid))
print('country costumes',len(node['costumes']),'blocks',len(node['blocks']))
assert not invalid
# Save revised output after clone-budget fix.
needed={v['md5ext'] for t in targets for v in t['costumes']+t['sounds']}
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=5) as z:
    z.writestr('project.json',json.dumps(project,separators=(',',':'),ensure_ascii=False))
    for fn in sorted(needed):z.writestr(fn,pkgfiles[fn])
with zipfile.ZipFile(out) as z:
    assert z.testzip() is None
    data=json.loads(z.read('project.json'))
    assert len(data['targets'])==20
    print('SB3 CHECK:',out.stat().st_size,'bytes',len(z.namelist()),'files',
          sum(len(t['blocks']) for t in data['targets']),'blocks')