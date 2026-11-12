import os,uuid,json,hashlib,zipfile,io,math,collections,struct,datetime
from PIL import Image
ROOT='/mnt/data/bank_port';ASSETS=ROOT+'/assets';OUT='/mnt/data/Breaking_the_Bank_Flash_to_Scratch.sb3'
SHEET_COUNT=900
SCENES=[('Intro',0,153),('Tunnel',161,757),('Explosion',763,1250),('LazerDrill',1251,1640),('WreckingBall',1649,2357),('Teleporter',2358,2734),('Disguise',2735,5394)]
VARIABLE_NAMES=['Scene start','Scene end','Movie frame','Playing','Previous frame','Sheet','Previous sheet','Choice']
varids={name:'v'+str(i+1) for i,name in enumerate(VARIABLE_NAMES)}
broadcastIDs={name:'b'+str(i+1) for i,(name,_,_) in enumerate(SCENES)}

def E(op,*args):return (op,*args)
class Builder:
 def __init__(self):self.blocks={};self.count=0
 def new(self,op,parent=None,top=False,fields=None,shadow=False,x=0,y=0):
  self.count+=1;bid='blk'+str(self.count)
  obj={'opcode':op,'next':None,'parent':parent,'inputs':{},'fields':fields or {},'shadow':shadow,'topLevel':top}
  if top:obj.update(x=x,y=y)
  self.blocks[bid]=obj;return bid
 def inp(self,val,parent,bool_input=False):
  if isinstance(val,tuple):
   oid=self.expr(val,parent)
   return [2,oid] if bool_input else [3,oid,[4,'0']]
  if isinstance(val,(int,float)):return [1,[4,str(val)]]
  return [1,[10,str(val)]]
 def expr(self,t,parent):
  op=t[0];v=t[1:]
  if op=='var':
   return self.new('data_variable',parent,fields={'VARIABLE':[v[0],varids[v[0]]]})
  if op in ('timer','mx','my','mousedown'):
   return self.new({'timer':'sensing_timer','mx':'sensing_mousex','my':'sensing_mousey','mousedown':'sensing_mousedown'}[op],parent)
  mapping={'add':('operator_add','NUM1','NUM2'),'sub':('operator_subtract','NUM1','NUM2'),'mul':('operator_multiply','NUM1','NUM2'),'div':('operator_divide','NUM1','NUM2'),'mod':('operator_mod','NUM1','NUM2'),'eq':('operator_equals','OPERAND1','OPERAND2'),'gt':('operator_gt','OPERAND1','OPERAND2'),'lt':('operator_lt','OPERAND1','OPERAND2'),'and':('operator_and','OPERAND1','OPERAND2'),'or':('operator_or','OPERAND1','OPERAND2')}
  if op in mapping:
   opcode,first,second=mapping[op];bid=self.new(opcode,parent)
   self.blocks[bid]['inputs'][first]=self.inp(v[0],bid,bool_input=(op in ('and','or')))
   self.blocks[bid]['inputs'][second]=self.inp(v[1],bid,bool_input=(op in ('and','or')))
   return bid
  if op in ('floor','abs','ceiling'):
   bid=self.new('operator_mathop',parent,fields={'OPERATOR':[op,None]});self.blocks[bid]['inputs']['NUM']=self.inp(v[0],bid);return bid
  if op=='not':
   bid=self.new('operator_not',parent);self.blocks[bid]['inputs']['OPERAND']=self.inp(v[0],bid,bool_input=True);return bid
  raise ValueError('unknown expr '+repr(t))
 def seq(self,stmts,parent):
  start=None;prev=None
  for st in stmts:
   bid=self.stmt(st,parent if prev is None else prev)
   if start is None:start=bid
   if prev is not None:self.blocks[prev]['next']=bid
   prev=bid
  return start
 def stmt(self,t,parent):
  op=t[0];a=t[1:]
  if op=='set':
   bid=self.new('data_setvariableto',parent,fields={'VARIABLE':[a[0],varids[a[0]]]});self.blocks[bid]['inputs']['VALUE']=self.inp(a[1],bid);return bid
  if op=='change':
   bid=self.new('data_changevariableby',parent,fields={'VARIABLE':[a[0],varids[a[0]]]});self.blocks[bid]['inputs']['VALUE']=self.inp(a[1],bid);return bid
  if op=='if':
   bid=self.new('control_if',parent);self.blocks[bid]['inputs']['CONDITION']=self.inp(a[0],bid,bool_input=True)
   s=self.seq(a[1],bid)
   if s:self.blocks[bid]['inputs']['SUBSTACK']=[2,s]
   return bid
  if op=='ifelse':
   bid=self.new('control_if_else',parent);self.blocks[bid]['inputs']['CONDITION']=self.inp(a[0],bid,bool_input=True)
   for k,seq in [('SUBSTACK',a[1]),('SUBSTACK2',a[2])]:
    sid=self.seq(seq,bid)
    if sid:self.blocks[bid]['inputs'][k]=[2,sid]
   return bid
  if op=='forever':
   bid=self.new('control_forever',parent)
   sid=self.seq(a[0],bid)
   if sid:self.blocks[bid]['inputs']['SUBSTACK']=[2,sid]
   return bid
  if op=='wait':
   bid=self.new('control_wait',parent);self.blocks[bid]['inputs']['DURATION']=self.inp(a[0],bid);return bid
  if op=='resettimer':return self.new('sensing_resettimer',parent)
  if op=='stopsounds':return self.new('sound_stopallsounds',parent)
  if op=='show':return self.new('looks_show',parent)
  if op=='sound':
   bid=self.new('sound_play',parent)
   mid=self.new('sound_sounds_menu',bid,fields={'SOUND_MENU':[a[0],None]},shadow=True)
   self.blocks[bid]['inputs']['SOUND_MENU']=[1,mid]
   return bid
  if op=='switch':
   bid=self.new('looks_switchcostumeto',parent)
   mid=self.new('looks_costume',bid,fields={'COSTUME':['Film 0001-0006',None]},shadow=True)
   self.blocks[bid]['inputs']['COSTUME']=[3,self.expr(a[0],bid),mid]
   return bid
  if op=='goto':
   bid=self.new('motion_gotoxy',parent)
   self.blocks[bid]['inputs']['X']=self.inp(a[0],bid)
   self.blocks[bid]['inputs']['Y']=self.inp(a[1],bid)
   return bid
  if op=='broadcast':
   bid=self.new('event_broadcast',parent)
   mid=self.new('event_broadcast_menu',bid,fields={'BROADCAST_OPTION':[a[0],broadcastIDs[a[0]]]},shadow=True)
   self.blocks[bid]['inputs']['BROADCAST_INPUT']=[1,mid]
   return bid
  raise ValueError('unknown statement '+repr(t))
 def script(self,hat,body,fields=None,x=0,y=0):
  root=self.new(hat,None,True,fields,x=x,y=y)
  first=self.seq(body,root)
  if first:self.blocks[root]['next']=first
  return root

b=Builder()
V=lambda x:E('var',x)
ADD=lambda x,y:E('add',x,y)
DIV=lambda x,y:E('div',x,y)
MUL=lambda x,y:E('mul',x,y)
SUB=lambda x,y:E('sub',x,y)
FLOOR=lambda x:E('floor',x)
EQ=lambda x,y:E('eq',x,y)
GT=lambda x,y:E('gt',x,y)
LT=lambda x,y:E('lt',x,y)
AND=lambda x,y:E('and',x,y)
ABS=lambda x:E('abs',x)
MOD=lambda x,y:E('mod',x,y)

b.script('event_whenflagclicked',[
 ('show',),('set','Previous sheet',-1),('set','Previous frame',-1),('broadcast','Intro'),
 ('forever',[
  ('if',EQ(V('Playing'),1),[
   ('set','Movie frame',ADD(V('Scene start'),FLOOR(MUL(E('timer'),30)))),
   ('if',GT(V('Movie frame'),V('Scene end')),[
    ('set','Movie frame',V('Scene end')),
    ('ifelse',EQ(V('Scene end'),5394), [('broadcast','Intro')], [('set','Playing',0),('stopsounds',)]),
   ]),
   ('if',E('not',EQ(V('Movie frame'),V('Previous frame'))),[
    ('set','Sheet',ADD(FLOOR(DIV(V('Movie frame'),6)),1)),
    ('if',E('not',EQ(V('Sheet'),V('Previous sheet'))),[
     ('switch',V('Sheet')),('set','Previous sheet',V('Sheet')),
    ]),
    ('goto',SUB(480,MUL(480,MOD(V('Movie frame'),3))), ADD(-180,MUL(360,FLOOR(DIV(MOD(V('Movie frame'),6),3))))),
    ('set','Previous frame',V('Movie frame')),
   ]),
  ]),
  ('wait',0),
 ]),
],x=40,y=40)

b.script('event_whenthisspriteclicked',[
 ('if',EQ(V('Playing'),0),[
  ('if',AND(LT(ABS(E('mx')),225),LT(ABS(E('my')),175)),[
   ('set','Choice',1),
   ('if',GT(E('mx'),-75),[('set','Choice',2)]),
   ('if',GT(E('mx'),75),[('set','Choice',3)]),
   ('if',LT(E('my'),0),[('change','Choice',3)]),
   *[('if',EQ(V('Choice'),i),[('broadcast',name)]) for i,(name,_,_) in enumerate(SCENES[1:],1)],
  ]),
 ]),
],x=500,y=40)

for i,(name,fr0,fr1) in enumerate(SCENES[1:],1):
 b.script('event_whenkeypressed', [('if',EQ(V('Playing'),0),[('broadcast',name)])],
  {'KEY_OPTION':[str(i),None]},x=770+(i-1)//3*220,y=40+(i-1)%3*90)

b.script('event_whenkeypressed',[('broadcast','Intro')],{'KEY_OPTION':['r',None]},x=1200,y=350)

for i,(name,fr0,fr1) in enumerate(SCENES):
 b.script('event_whenbroadcastreceived',[
  ('set','Playing',0),('set','Scene start',fr0),('set','Scene end',fr1),
  ('set','Previous frame',-1),('stopsounds',),('resettimer',),('sound',name),('set','Playing',1),
 ],{'BROADCAST_OPTION':[name,broadcastIDs[name]]},x=40+(i%4)*270,y=480+(i//4)*230)

white_svg=b'<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360" viewBox="0 0 480 360"><rect width="480" height="360" fill="white"/></svg>'

def assetinfo(data,ext):
 h=hashlib.md5(data).hexdigest();return h, h+'.'+ext
assets={}
def register(path,ext):
 data=open(path,'rb').read();md5,fname=assetinfo(data,ext)
 assets[fname]=data;return md5,fname

scostumes=[]
for i in range(SHEET_COUNT):
 path=f'{ASSETS}/sheet_{i:04d}.png'
 if not os.path.exists(path):raise FileNotFoundError(path)
 md5,file=register(path,'png')
 scostumes.append({'assetId':md5,'name':f'Film {i*6+1:04d}-{min(i*6+6,5395):04d}',
                  'bitmapResolution':1,'md5ext':file,'dataFormat':'png','rotationCenterX':720,'rotationCenterY':360})

sounds=[]
for name,fr0,fr1 in SCENES:
 md5,file=register(f'{ROOT}/{name}.mp3','mp3')
 sounds.append({'assetId':md5,'name':name,'dataFormat':'mp3','format':'','rate':44100,
                'sampleCount':int(round((fr1-fr0+1)/30*44100)),'md5ext':file})

sm,fn=assetinfo(white_svg,'svg');assets[fn]=white_svg
stage={'isStage':True,'name':'Stage','variables':{},'lists':{},'broadcasts':{v:k for k,v in broadcastIDs.items()},
       'blocks':{},'comments':{},'currentCostume':0,'costumes':[{'assetId':sm,'name':'Backdrop','md5ext':fn,'dataFormat':'svg','rotationCenterX':240,'rotationCenterY':180}],
       'sounds':[],'volume':100,'layerOrder':0,'tempo':60,'videoTransparency':50,'videoState':'off','textToSpeechLanguage':None}
projector={'isStage':False,'name':'Bank - original Flash animation','variables':{varids[k]:[k,0] for k in VARIABLE_NAMES},
 'lists':{},'broadcasts':{},'blocks':b.blocks,'comments':{},'currentCostume':0,'costumes':scostumes,'sounds':sounds,
 'volume':100,'layerOrder':1,'visible':True,'x':480,'y':-180,'size':100,'direction':90,'draggable':False,'rotationStyle':'don\'t rotate'}
project={'targets':[stage,projector],'monitors':[],'extensions':[],
         'meta':{'semver':'3.0.0','vm':'11.3.0','agent':'SWF Frame-by-Frame Conversion'}}

with zipfile.ZipFile(OUT,'w',compression=zipfile.ZIP_STORED,allowZip64=True) as z:
 z.writestr('project.json',json.dumps(project,separators=(',',':'),ensure_ascii=False))
 for key,data in assets.items():z.writestr(key,data)
print('PROJECT',OUT,'sizeMB',round(os.stat(OUT).st_size/1048576,2),'costumes',len(scostumes),'sounds',len(sounds),'assets',len(assets),'blocks',len(b.blocks))

with zipfile.ZipFile(OUT) as z:
 j=json.loads(z.read('project.json'))
 assert len(j['targets'][1]['costumes'])==SHEET_COUNT
 assert len(j['targets'][1]['sounds'])==7
 assert len(j['targets'][1]['blocks'])>70
 assert len(j['targets'][1]['blocks'])==len(b.blocks)
 assert all(c['md5ext'] in z.namelist() for t in j['targets'] for c in t['costumes'])
 assert all(s['md5ext'] in z.namelist() for s in j['targets'][1]['sounds'])
 for blockid,block in b.blocks.items():
  for p in (block.get('parent'),block.get('next')):
   assert p is None or p in b.blocks,(blockid,'bad link',p)
  for k,v in block.get('inputs',{}).items():
   for child in v[1:]:
    if isinstance(child,str):assert child in b.blocks,(blockid,'missing expr',child)
 print('VALIDATED project.json and assets')
