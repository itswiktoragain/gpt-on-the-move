#!/usr/bin/env python3
"""Plague Inc. Scratch remaster: replace prototype simulation with geographic SIR engine.
Usage: python remaster.py source.sb3 destination.sb3
Assets are sourced from the user's own Plague Inc. Unity asset export, when supplied.
"""
import os, sys, json, zipfile, math, hashlib, random, io
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageEnhance, ImageFilter
ROOT=Path(__file__).resolve().parent
IN=Path(sys.argv[1]) if len(sys.argv)>1 else Path("/mnt/data/Plague_Inc_Unity_Assets_Scratch.sb3")
OUT=Path(sys.argv[2]) if len(sys.argv)>2 else Path("/mnt/data/Plague_Inc_Remastered_Scratch.sb3")
TEX=Path(os.environ.get("PLAGUE_TEXTURES","/mnt/data/Plague_Inc_Unity_Textures_Extracted.zip"))
with zipfile.ZipFile(IN) as z:
    proj=json.loads(z.read("project.json"))
    files={i.filename:z.read(i.filename) for i in z.infolist() if i.filename!='project.json'}
stage=proj['targets'][0]
stage['name']='Stage'
targets=proj['targets']
# Preserve all original game assets referenced by the Unity export, not screenshots.
orig_assets={}
if TEX.exists():
    with zipfile.ZipFile(TEX) as z:
        for code in ['0410_','0536_','0949_','0343_','0968_','0830_','0433_','0360_','0443_','0970_','0578_','0851_']:
            key=next((n for n in z.namelist() if n.startswith(code) and n.lower().endswith('.png')),None)
            if key:
                orig_assets[code[:4]]=Image.open(io.BytesIO(z.read(key))).convert("RGBA")
print('Unity graphics:',list(orig_assets))
def put_asset(blob,ext='png'):
    mid=hashlib.md5(blob).hexdigest()
    nm=f'{mid}.{ext}'
    files[nm]=blob
    return mid,nm
def image_bytes(im):
    out=io.BytesIO(); im.save(out,format='PNG',optimize=True);return out.getvalue()
def install_costume(t,name,im,index=None,bitmap=2):
    raw=image_bytes(im)
    md5,filename=put_asset(raw)
    desc={'assetId':md5,'name':name,'bitmapResolution':bitmap,'dataFormat':'png','md5ext':filename,
          'rotationCenterX':im.width/2,'rotationCenterY':im.height/2}
    if index is None:t['costumes'].append(desc)
    else:t['costumes'][index]=desc
    return desc
def add_stage_var(name,val=0):
    key='v_Stage_'+name
    stage['variables'][key]=[name,val]
    return key
def add_stage_list(name,data):
    key='l_Stage_'+name
    stage['lists'][key]=[name,data]
    return key
def sid(name,l=False):
    pool=stage['lists'] if l else stage['variables']
    for key,(k,_) in pool.items():
        if k==name:return key
    raise KeyError(name)
def new_sprite(name):
    obj={'isStage':False,'name':name,'variables':{},'lists':{},'broadcasts':{},'blocks':{},'comments':{},
        'currentCostume':0,'costumes':[],'sounds':[],'volume':70,'layerOrder':len(targets),
        'visible':False,'x':0,'y':0,'size':100,'direction':90,'draggable':False,'rotationStyle':'all around'}
    targets.append(obj)
    return obj
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
# real geographical neighbors, not arbitrary array offsets
coords=list(zip(stage['lists'][sid('X',True)][1],stage['lists'][sid('Y',True)][1]))
# X/Y are map-screen coordinates, and nearest neighbors preserve geography
def geographical_neighbors(i):
    a,b=coords[i]
    distances=[]
    for j,(x,y) in enumerate(coords):
        if i==j:continue
        dx=abs(x-a);dx=min(dx,460-dx);dy=abs(y-b)
        # X is longitude-projected; moderate adjustments for latitude
        d=dx*(0.53 if abs(b)>65 else .75) + dy*1.45
        distances.append((d,j+1))
    return [x for _,x in sorted(distances)[:5]]
for ni in range(5):
    add_stage_list('Adjacent'+str(ni+1),[geographical_neighbors(i)[ni] for i in range(len(coords))])
N=len(coords)
for key,initial in {
 'WorldRecovered':0,'WorldHealthy':0,'GlobalTotalCases':0,'InfectedNations':0,
 'ActiveFlights':0,'ActiveShips':0,'NewsCode':0,'PreviousNews':0,
 'DiscoveryDay':0,'AirSource':0,'AirDestination':0,'ShipSource':0,'ShipDestination':0,
 'WorldCured':0,'CureRate':0,'ResponseStrength':0,'OpenBorders':0,'OutbreakAge':0,'RecoveryRate':0,
 'WorldTotalInfected':0,'GovernmentAvg':0,'GlobeAlert':0
}.items():add_stage_var(key,initial)
for k,v in {
 'Recovered':[0]*N,'GovResponse':[0]*N,'Hospitals':[0]*N,
 'DailyCases':[0]*N,'DailyFatalities':[0]*N,'CumulativeCases':[0]*N,
 'ResearchCenters':[0]*N,'Immunity':[0]*N,'CountryAlerts':[0]*N
}.items():add_stage_list(k,v)
for name in ['route_air','route_sea','headline']:
 stage['broadcasts']['bc_'+name]=name
# disable old simplified country ticker and fictional generated ambient soundtrack
for hatid in ['sta_001596','sta_002110']:
    block=stage['blocks'].get(hatid)
    if block:
        block['topLevel']=False
        block.pop('x',None);block.pop('y',None)
# Native Unity sounds for controls, rather than the old synthesised cues.
sound_index={s['name']:s for s in stage['sounds']}
for alias,source in [('select','Unity: pause_accept'),('evolve','Unity: ape_bubble_press'),('warning','Unity: immune_shock')]:
    if source in sound_index and alias in sound_index:
        target=sound_index[alias];src=sound_index[source]
        for k in ['assetId','md5ext','dataFormat','rate','sampleCount']:target[k]=src[k]
stage['sounds']=[s for s in stage['sounds'] if s['name']!='ambient']
# prerequisites for evolved transmission/symptom/ability branches:
PREREQS=[0]*36
for later,previous in [(7,1),(8,2),(9,3),(10,4),(11,5),(12,6),
 (15,13),(17,16),(19,15),(20,14),(21,17),(22,19),(23,22),(24,23),
 (29,25),(30,26),(31,27),(32,28),(33,32),(34,33),(35,31),(36,35)]:
 PREREQS[later-1]=previous
add_stage_list('Requires',PREREQS)
# edit the original evolution purchase condition by inserting prerequisite check.
blocks=stage['blocks']
buy=blocks['sta_001004']
old=buy['inputs']['CONDITION'][1]
seq=10000
def extend_expr(x,parent):
 global seq
 seq+=1
 ident=f'patch_{seq}'
 node={'opcode':x.op,'next':None,'parent':parent,'inputs':{},'fields':{},
       'shadow':x.shadow,'topLevel':False}
 blocks[ident]=node
 for k,v in x.fields.items():
    node['fields'][k]=[v,sid(v,k=='LIST')] if k in ('VARIABLE','LIST') else v
 for k,v in x.inputs.items():
    if isinstance(v,Expr):
       child=extend_expr(v,ident)
       node['inputs'][k]=[3,child,[4,'0']]
    elif isinstance(v,(int,float)):node['inputs'][k]=[1,[4,str(v)]]
    else:node['inputs'][k]=[1,[10,str(v)]]
 return ident
expr=OR(EQ(ITEM('Requires',V('upgrade')),0),EQ(ITEM('Upgrades',ITEM('Requires',V('upgrade'))),1))
gate=extend_expr(expr,'sta_001004')
seq+=1
newid=f'patch_{seq}'
blocks[newid]={'opcode':'operator_and','next':None,'parent':'sta_001004','inputs':{
 'OPERAND1':[3,old,[4,'0']],'OPERAND2':[3,gate,[4,'0']]},'fields':{},'shadow':False,'topLevel':False}
blocks[old]['parent']=newid
blocks[gate]['parent']=newid
buy['inputs']['CONDITION']=[3,newid,[4,'0']]
# Proper SIR epidemic engine built entirely from native Scratch data/list/logic blocks.
engine=new_sprite('Epidemic Simulation')
blank=Image.new('RGBA',(4,4))
install_costume(engine,'engine',blank)
c=Compiler(engine)
for key in ['p','case','dead','recovered','susceptible','growth','loss','heal',
            'defense','climateMult','wealthMult','rate','target','chance','totalPrev',
            'g','risk','i','neighbors','dest','seed','flightChance','seaChance',
            'newImmune','alert','diseased','neighborN','tries']:
    c.localvar(key,0)
# Startup synchronization: wait until the stage finishes resetting its original per-country arrays.
start_reset=[
 WAIT(.18),
 SET('WorldRecovered',0),SET('WorldHealthy',V('WorldPopulation')),SET('GlobalTotalCases',0),
 SET('InfectedNations',0),SET('ActiveFlights',0),SET('ActiveShips',0),
 SET('NewsCode',0),SET('PreviousNews',0),SET('DiscoveryDay',0),
 SET('CureRate',0),SET('ResponseStrength',0),SET('GlobeAlert',0),
 SET('i',1),REPEAT(N,[
   REPL('Recovered',V('i'),0),REPL('GovResponse',V('i'),0),
   REPL('DailyCases',V('i'),0),REPL('DailyFatalities',V('i'),0),
   REPL('CumulativeCases',V('i'),0),REPL('Immunity',V('i'),0),REPL('CountryAlerts',V('i'),0),
   REPL('Hospitals',V('i'),A(1,ITEM('Wealth',V('i')))),
   CHG('i',1)])
]
c.script(WHEN('newgame'),start_reset,x=430,y=40)
daily=[
 CHG('Day',1),
 SET('GlobalInfected',0),SET('GlobalDead',0),SET('WorldRecovered',0),
 SET('NewCases',0),SET('NewDeaths',0),SET('InfectedNations',0),SET('ActiveFlights',0),
 SET('ActiveShips',0),SET('ResponseStrength',0),SET('WorldHealthy',0),
 SET('i',1),
 REPEAT(N,[
   SET('p',ITEM('Population',V('i'))),
   SET('case',ITEM('Infected',V('i'))),
   SET('dead',ITEM('Dead',V('i'))),
   SET('recovered',ITEM('Recovered',V('i'))),
   SET('susceptible',S(S(S(V('p'),V('case')),V('dead')),V('recovered'))),
   IF(LT(V('susceptible'),0),[SET('susceptible',0)]),
   # Non-immune residents are susceptible, immunity can slowly fade on long runs.
   IF(GT(V('Day'),130),[
       SET('newImmune',ROUND(M(V('recovered'),.0015))),
       SET('recovered',S(V('recovered'),V('newImmune'))),
       CHG('susceptible',V('newImmune'))]),
   SET('growth',0),SET('loss',0),SET('heal',0),
   IF(GT(V('case'),0),[
      CHG('InfectedNations',1),
      SET('climateMult',1),
      IF(AND(EQ(ITEM('Climate',V('i')),0),LT(V('ColdBonus'),2)),[SET('climateMult',.42)]),
      IF(AND(EQ(ITEM('Climate',V('i')),2),LT(V('HeatBonus'),2)),[SET('climateMult',.54)]),
      IF(AND(EQ(V('Scenario'),2),EQ(ITEM('Climate',V('i')),0)),[SET('climateMult',M(V('climateMult'),.55))]),
      IF(AND(EQ(V('Scenario'),3),EQ(ITEM('Climate',V('i')),2)),[SET('climateMult',M(V('climateMult'),.58))]),
      SET('defense',D(1,A(1,M(.27,ITEM('GovResponse',V('i')))))),
      IF(AND(GT(ITEM('Wealth',V('i')),2),LT(V('DrugBonus'),2)),[SET('defense',M(V('defense'),.70))]),
      IF(AND(EQ(ITEM('Closed',V('i')),1),EQ(ITEM('Cheats',4),0)),[
          SET('defense',M(V('defense'),.73))]),
      SET('rate',A(.045,M(V('Infectivity'),.026))),
      IF(EQ(V('Type'),5),[SET('rate',M(V('rate'),.78))]),
      IF(EQ(V('Type'),4),[SET('rate',M(V('rate'),1.10))]),
      IF(AND(EQ(V('Scenario'),6),GT(V('p'),50000000)),[SET('rate',M(V('rate'),1.32))]),
      IF(GT(V('susceptible'),0),[
          SET('growth',ROUND(M(M(M(V('case'),V('rate')),M(V('climateMult'),V('defense'))),D(V('susceptible'),V('p'))))),
          IF(AND(LT(V('growth'),1),GT(V('case'),5)),[SET('growth',1)]),
          IF(GT(V('growth'),V('susceptible')),[SET('growth',V('susceptible'))])]),
      IF(GT(V('Lethality'),0),[
          SET('loss',ROUND(M(M(V('case'),V('Lethality')),.00084))),
          SET('loss',ROUND(D(V('loss'),A(1,M(.19,ITEM('Hospitals',V('i'))))))),
          IF(EQ(V('Type'),7),[SET('loss',ROUND(M(V('loss'),1.18)))]),
          IF(GT(V('loss'),V('case')),[SET('loss',V('case'))])]),
      SET('heal',ROUND(M(V('case'),A(.0012,M(.000045,V('Cure')))))),
      IF(GT(V('Cure'),82),[SET('heal',A(V('heal'),ROUND(M(V('case'),.11))))]),
      IF(GT(A(V('loss'),V('heal')),V('case')),[SET('heal',S(V('case'),V('loss')))]),
      # Response is local: severity, case load, wealth, and public alert matter.
      IF(AND(GT(V('Severity'),0),GT(V('case'),D(V('p'),300))),[
         IF(LT(R(1,100),A(A(2,M(V('Difficulty'),1.5)),M(V('Severity'),1.8))),[
            IF(LT(ITEM('GovResponse',V('i')),4),[
                REPL('GovResponse',V('i'),A(ITEM('GovResponse',V('i')),1))])])]),
      IF(AND(AND(AND(GT(ITEM('GovResponse',V('i')),2),GT(V('Severity'),3)),EQ(ITEM('Cheats',4),0)),NOT(EQ(V('Scenario'),10))),[
          REPL('Closed',V('i'),1)]),
      # Quarantine reduces the cross-border spread, but cannot stop local transmission.
      REPL('DailyCases',V('i'),V('growth')),
      REPL('DailyFatalities',V('i'),V('loss')),
      REPL('CumulativeCases',V('i'),A(ITEM('CumulativeCases',V('i')),V('growth'))),
      SET('case',S(A(V('case'),V('growth')),A(V('loss'),V('heal')))),
      IF(LT(V('case'),0),[SET('case',0)]),
      SET('dead',A(V('dead'),V('loss'))),
      SET('recovered',A(V('recovered'),V('heal'))),
      REPL('Infected',V('i'),V('case')),
      REPL('Dead',V('i'),V('dead')),
      REPL('Recovered',V('i'),V('recovered')),
      CHG('NewCases',V('growth')),CHG('NewDeaths',V('loss')),
      # Land corridors: 5 geographically nearby countries per origin.
      IF(GT(V('case'),D(V('p'),700)),[
        IF(LT(R(1,100),A(2,M(V('Infectivity'),.8))),[
           SET('neighborN',R(1,5)),
           SET('target',ITEM('Adjacent1',V('i'))),
           IF(EQ(V('neighborN'),2),[SET('target',ITEM('Adjacent2',V('i')))]),
           IF(EQ(V('neighborN'),3),[SET('target',ITEM('Adjacent3',V('i')))]),
           IF(EQ(V('neighborN'),4),[SET('target',ITEM('Adjacent4',V('i')))]),
           IF(EQ(V('neighborN'),5),[SET('target',ITEM('Adjacent5',V('i')))]),
           IF(AND(EQ(ITEM('Infected',V('target')),0),EQ(ITEM('Closed',V('target')),0)),[
             REPL('Infected',V('target'),R(60,400)),
             CHG('DNA',2),SET('NewsCode',2)])])]),
      # Air travel can bridge continents but requires both airport connections open.
      SET('flightChance',A(A(1,M(V('AirBonus'),1.3)),D(M(V('case'),280),V('p')))),
      IF(EQ(V('Scenario'),7),[SET('flightChance',M(V('flightChance'),2.1))]),
      IF(AND(AND(GT(ITEM('Airports',V('i')),0),EQ(ITEM('Closed',V('i')),0)),LT(R(1,100),V('flightChance'))),[
           SET('target',R(1,N)),
           IF(AND(AND(GT(ITEM('Airports',V('target')),0),EQ(ITEM('Closed',V('target')),0)),EQ(ITEM('Infected',V('target')),0)),[
              REPL('Infected',V('target'),R(80,750)),CHG('DNA',3),
              SET('AirSource',V('i')),SET('AirDestination',V('target')),
              CHG('ActiveFlights',1),SET('NewsCode',3),BCAST('route_air')])]),
      # Sea ports independently transmit via shipping.
      SET('seaChance',A(.7,A(M(1.2,V('WaterBonus')),D(M(V('case'),210),V('p'))))),
      IF(AND(AND(GT(ITEM('Ports',V('i')),0),EQ(ITEM('Closed',V('i')),0)),LT(R(1,100),V('seaChance'))),[
           SET('target',R(1,N)),
           IF(AND(AND(GT(ITEM('Ports',V('target')),0),EQ(ITEM('Closed',V('target')),0)),EQ(ITEM('Infected',V('target')),0)),[
               REPL('Infected',V('target'),R(50,520)),CHG('DNA',3),
               SET('ShipSource',V('i')),SET('ShipDestination',V('target')),
               CHG('ActiveShips',1),SET('NewsCode',4),BCAST('route_sea')])])
   ]),
   CHG('GlobalInfected',ITEM('Infected',V('i'))),
   CHG('GlobalDead',ITEM('Dead',V('i'))),
   CHG('WorldRecovered',ITEM('Recovered',V('i'))),
   CHG('ResponseStrength',ITEM('GovResponse',V('i'))),
   CHG('i',1)
 ]),
 SET('WorldHealthy',S(S(S(V('WorldPopulation'),V('GlobalInfected')),V('GlobalDead')),V('WorldRecovered'))),
 IF(LT(V('WorldHealthy'),0),[SET('WorldHealthy',0)]),
 CHG('GlobalTotalCases',V('NewCases')),
 # Research begins after detection threshold, not automatically every game.
 IF(AND(EQ(V('CureActive'),0),OR(OR(GT(V('GlobalInfected'),D(V('WorldPopulation'),250)),GT(V('Severity'),6)),GT(V('Day'),90))),[
    SET('CureActive',1),SET('DiscoveryDay',V('Day')),SET('NewsCode',5),SOUND('warning')]),
 IF(AND(EQ(V('CureActive'),1),AND(EQ(ITEM('Cheats',2),0),LT(V('Mode'),4))),[
    SET('CureRate',D(M(A(.06,A(M(V('Difficulty'),.10),A(M(V('Severity'),.018),D(V('ResponseStrength'),1000)))),V('ResearchFactor')),A(1,M(V('CureResist'),.14)))),
    IF(EQ(V('Type'),6),[SET('CureRate',M(V('CureRate'),1.35))]),
    IF(EQ(V('Type'),5),[SET('CureRate',M(V('CureRate'),.70))]),
    CHG('Cure',V('CureRate')),
 ]),
 # DNA earned from global milestones, route events, periodic funding, and mutations.
 IF(EQ(MOD(V('Day'),5),0),[CHG('DNA',1)]),
 IF(AND(EQ(MOD(V('Day'),5),0),EQ(ITEM('Genes',1),2)),[CHG('DNA',2)]),
 IF(AND(EQ(MOD(V('Day'),6),0),EQ(ITEM('Genes',1),4)),[CHG('DNA',1)]),
 IF(GT(V('NewCases'),2000000),[CHG('DNA',1)]),
 IF(GT(V('NewCases'),20000000),[CHG('DNA',1)]),
 IF(AND(EQ(V('Milestone'),0),GT(V('GlobalInfected'),D(V('WorldPopulation'),1000))),[SET('Milestone',1),CHG('DNA',8),SET('NewsCode',6)]),
 IF(AND(EQ(V('Milestone'),1),GT(V('GlobalInfected'),D(V('WorldPopulation'),100))),[SET('Milestone',2),CHG('DNA',12),SET('NewsCode',7)]),
 IF(AND(EQ(V('Milestone'),2),GT(V('GlobalInfected'),D(V('WorldPopulation'),5))),[SET('Milestone',3),CHG('DNA',18),SET('NewsCode',8)]),
 IF(AND(EQ(V('Milestone'),3),GT(V('GlobalInfected'),D(V('WorldPopulation'),2))),[SET('Milestone',4),CHG('DNA',24),SET('NewsCode',9)]),
 # Real mutation changes every relevant disease stat, not only transmission.
 IF(LT(R(1,1000),M(V('MutationRate'),3)),[
    SET('target',R(13,24)),
    IF(EQ(ITEM('Genes',4),3),[SET('target',R(1,12))]),
    IF(EQ(ITEM('Upgrades',V('target')),0),[
       REPL('Upgrades',V('target'),1),
       CHG('Infectivity',ITEM('UpInf',V('target'))),
       CHG('Severity',ITEM('UpSev',V('target'))),
       CHG('Lethality',ITEM('UpLeth',V('target'))),
       CHG('AirBonus',ITEM('UpAir',V('target'))),
       CHG('WaterBonus',ITEM('UpWater',V('target'))),
       CHG('ColdBonus',ITEM('UpCold',V('target'))),
       CHG('HeatBonus',ITEM('UpHeat',V('target'))),
       CHG('DrugBonus',ITEM('UpDrug',V('target'))),
       CHG('CureResist',ITEM('UpCure',V('target'))),
       SET('NewsCode',10)])]),
 # Type-specific ongoing mechanics.
 IF(AND(EQ(V('Type'),7),EQ(MOD(V('Day'),9),0)),[
    CHG('Lethality',1),SET('NewsCode',11)]),
 IF(AND(AND(EQ(V('Type'),3),EQ(ITEM('Upgrades',35),1)),EQ(MOD(V('Day'),19),0)),[
    SET('target',R(1,N)),
    IF(EQ(ITEM('Infected',V('target')),0),[REPL('Infected',V('target'),R(250,1100)),CHG('DNA',2),SET('NewsCode',12)])]),
 IF(EQ(ITEM('Cheats',1),1),[SET('DNA',999)]),
 IF(AND(EQ(ITEM('Cheats',6),1),LT(V('GlobalInfected'),25)),[
    REPL('Infected',V('StartCountry'),25)]),
 IF(GT(V('Cure'),99.99),[SET('Outcome',2),SET('Screen',6),BCAST('refresh'),SOUND('warning')]),
 IF(GT(V('GlobalDead'),M(V('WorldPopulation'),.90)),[SET('Outcome',1),SET('Screen',6),BCAST('refresh'),SOUND('evolve')]),
 IF(AND(EQ(V('Outcome'),0),AND(LT(V('GlobalInfected'),1),GT(V('Day'),5))),[
    SET('Outcome',2),SET('Screen',6),BCAST('refresh')]),
 IF(AND(EQ(V('Outcome'),0),AND(EQ(V('Mode'),2),GT(V('Day'),250))),[
    SET('Outcome',2),SET('Screen',6),BCAST('refresh')])
]
# Endangered country visibility and speed are handled by the same on-map controller.
tick=FOREVER([
    IF(AND(AND(EQ(V('Screen'),3),EQ(V('Started'),1)),AND(GT(V('Speed'),0),EQ(V('Outcome'),0))),daily),
    IF(GT(V('Speed'),0),[WAIT(D(.76,V('Speed')))], [WAIT(.1)])
])
c.script(FLAG(),[tick],x=40,y=40)
# Route icons glide from infected source to destination while the sim continues.
for kind in ['air','sea']:
    icon=new_sprite('Passenger flights' if kind=='air' else 'Cargo shipping')
    im=Image.new('RGBA',(46,46),(0,0,0,0))
    draw=ImageDraw.Draw(im)
    if kind=='air':
        draw.polygon([(23,2),(28,19),(44,25),(44,30),(26,28),(25,42),(19,42),(19,28),(2,30),(2,25),(18,19)],
                     fill=(255,215,185,255),outline=(123,25,25,255))
    else:
        draw.polygon([(5,22),(42,22),(36,35),(10,35)],fill=(249,195,125,255),outline=(130,35,35,255))
        draw.rectangle((18,13,34,22),fill=(228,108,73,255))
    install_costume(icon,kind,im)
    cc=Compiler(icon)
    for name in ['startRoute','endRoute']:cc.localvar(name,1)
    cc.script(FLAG(),[HIDE()],x=30,y=30)
    # Create a new sprite clone on arrival of a fresh transmission route.
    cc.script(WHEN('route_'+kind),[CREATE()],x=30,y=105)
    source= 'AirSource' if kind=='air' else 'ShipSource'
    dest= 'AirDestination' if kind=='air' else 'ShipDestination'
    cc.script(WHENCLONE(),[
       SET('startRoute',V(source)),SET('endRoute',V(dest)),
       IF(EQ(V('Screen'),3),[
          GO(ITEM('X',V('startRoute')),ITEM('Y',V('startRoute'))),SHOW(),
          GLIDE(.65,ITEM('X',V('endRoute')),ITEM('Y',V('endRoute')))]),
       DELETE()
    ],x=30,y=185)
# Informative news ticker: announcements reflect actual simulation state.
news=new_sprite('World News Alerts')
FONT_PATH='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
try:ft=ImageFont.truetype(FONT_PATH,19)
except:ft=ImageFont.load_default()
messages=[
 'WORLD WATCH  /  INFECTION TRACKER','TRANSMISSION DETECTED NEAR BORDER',
 'NEIGHBORING REGION EXPOSED','INFECTED PASSENGERS ARRIVE BY AIR',
 'CARGO SHIP CARRIES INFECTION','INTERNATIONAL RESEARCH BEGINS',
 'OUTBREAK REACHES NEW REGIONS','GLOBAL HEALTH EMERGENCY DECLARED',
 'PUBLIC HEALTH SYSTEMS UNDER PRESSURE','MILLIONS AT RISK OF INFECTION',
 'GENOME MUTATION DETECTED','BIO-WEAPON LETHALITY IS RISING',
 'FUNGAL SPORES CROSS NEW BORDERS']
for i,s in enumerate(messages):
    im=Image.new('RGBA',(890,36),(16,4,6,228))
    d=ImageDraw.Draw(im)
    d.rounded_rectangle((1,1,889,35),radius=11,fill=(31,8,9,238),outline=(173,53,47,255),width=2)
    d.text((13,8),s,font=ft,fill=(255,220,203,255))
    install_costume(news,str(i),im)
nc=Compiler(news)
nc.script(FLAG(),[GO(0,-106),FOREVER([
   IF(EQ(V('Screen'),3),[
      STMT('looks_switchcostumeto',{'COSTUME':A(V('NewsCode'),1)}),SHOW()
   ],[HIDE()]),WAIT(.22)
])],x=35,y=35)
# Redraw existing backdrops using genuine Unity textures as modular assets.
accent=(231,83,67);light=(255,226,207);muted=(201,159,151)
def fnt(sz,bold=False):
 try:return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans'+('-Bold' if bold else '')+'.ttf',sz)
 except:return ImageFont.load_default()
def text(d,xy,s,size=18,fill=light,bold=False,anchor=None):
 d.text(xy,s,fill=fill,font=fnt(size,bold),anchor=anchor)
def rect(d,xy,fill,outline=None,radius=10,width=2):
 d.rounded_rectangle(xy,radius=radius,fill=fill,outline=outline,width=width)
def plaguetexture(size):
    if '0410' in orig_assets:
        bg=orig_assets['0410'].convert('RGB').resize(size,Image.Resampling.BILINEAR)
    else:bg=Image.new('RGB',size,(50,8,12))
    # keep Unity artwork visible without swallowing live game text
    veil=Image.new('RGBA',size,(6,5,8,153))
    return Image.alpha_composite(bg.convert('RGBA'),veil)
def basepanel(title,subtitle='',darkness=130):
    im=plaguetexture((960,720))
    d=ImageDraw.Draw(im,'RGBA')
    rect(d,(12,11,947,92),(10,5,8,227),(135,35,34,255),17,3)
    text(d,(32,19),title,size=37,bold=True)
    if subtitle:text(d,(926,56),subtitle,size=15,fill=muted,anchor='ra')
    return im
def form_button(d,box,label,active=False,red=False,size=21):
    rect(d,box,(73,12,19,240) if active else (24,12,17,228),
         (230,96,71,255) if active or red else (115,55,52,220),13,3)
    text(d,((box[0]+box[2])/2,(box[1]+box[3])/2),label,size=size,anchor='mm',bold=True)
def merge_world_image():
    im=plaguetexture((960,720))
    d=ImageDraw.Draw(im,'RGBA')
    rect(d,(10,8,950,96),(18,9,13,244),(177,56,48,255),15,3)
    rect(d,(10,106,950,601),(16,8,13,198),(135,39,37,210),14,3)
    # Original Unity map outline supplied in the game files.
    if '0536' in orig_assets:
        outline=orig_assets['0536'].convert('RGBA').resize((918,452),Image.Resampling.LANCZOS)
        alpha=outline.getchannel('R').point(lambda p:min(200,int(max(0,p-40)*1.25)))
        outline.putalpha(alpha)
        im.alpha_composite(outline,(20,134))
    rect(d,(11,603,948,668),(16,7,10,233),(145,46,45,240),12,2)
    for x in (260,500,740):d.line((x,609,x,662),fill=(108,58,52,255),width=2)
    text(d,(31,118),'GLOBAL OUTBREAK MAP    /    COLOR = INFECTION STAGE',17,(241,202,195),True)
    for box,label in [((16,677,152,716),'MENU'),((163,677,308,716),'PAUSE'),((318,677,471,716),'SPEED'),
                      ((480,677,673,716),'COUNTRIES'),((680,676,945,717),'EVOLVE')]:
        form_button(d,box,label,label=='EVOLVE',size=18)
    return im
def merge_menu_image():
    im=plaguetexture((960,720))
    d=ImageDraw.Draw(im,'RGBA')
    rect(d,(19,16,940,697),(10,5,10,188),(125,40,46,240),19,3)
    # Actual original splash/logo composition, cropped to preserve button labels
    if '0949' in orig_assets:
      original=orig_assets['0949'].copy()
      # Preserve original emblem and title in upper third.
      top=original.crop((0,0,1024,380)).resize((895,332),Image.Resampling.BILINEAR)
      top.putalpha(top.getchannel('A').point(lambda x:int(x*.68)))
      im.alpha_composite(top,(36,14))
    d=ImageDraw.Draw(im,'RGBA')
    rect(d,(43,320,916,397),(5,5,7,220),(172,60,48,225),12,2)
    text(d,(59,341),'EVOLVE. SPREAD. SURVIVE THE CURE.',24,light,True)
    for box,t,sm in [((104,408,500,487),'NEW OUTBREAK','7 disease types'),
                     ((522,408,898,487),'SCENARIOS','12 world conditions'),
                     ((104,502,500,583),'GENETIC CODE','25 genetic modifiers'),
                     ((522,502,898,583),'CHEAT LAB','8 simulation modifiers')]:
        form_button(d,box,t,t=='NEW OUTBREAK',size=23)
        text(d,(box[0]+22,box[3]-20),sm,14,muted)
    text(d,(107,652),'HOW TO PLAY  /  OPEN GUIDE',18,light,True)
    text(d,(888,652),'SCRATCH 3.0',17,muted,True,anchor='ra')
    return im
def tone_other_backdrop(name,old):
    # Dark red palette across interactive screens, original background graphics retained subtly.
    im=old.convert('RGBA')
    overlay=plaguetexture(im.size)
    if name.startswith('evo'):
        # Keep data cards and readable costs while using the original game's darker red palette.
        tint=Image.new('RGBA',im.size,(78,12,15,80))
        im=Image.alpha_composite(im,tint)
    else:
        tint=Image.new('RGBA',im.size,(60,10,12,58))
        im=Image.alpha_composite(im,tint)
    return im
for j,desc in enumerate(stage['costumes']):
    name=desc['name']
    if name=='world':now=merge_world_image()
    elif name=='menu':now=merge_menu_image()
    else:
        old=Image.open(io.BytesIO(files[desc['md5ext']])).convert('RGBA')
        now=tone_other_backdrop(name,old)
    install_costume(stage,name,now,index=j)
# Make DNA collectible icon from actual Unity extracted texture, not a drawn plus sign.
for spr in targets:
    if spr['name']=='Collectible DNA' and '0968' in orig_assets:
        dna=orig_assets['0968']
        shadow=Image.new('RGBA',(144,144),(0,0,0,0))
        resized=dna.resize((112,112),Image.Resampling.LANCZOS)
        shadow.alpha_composite(resized,(16,16))
        install_costume(spr,'UNITY DNA',shadow,index=0)
    if spr['name']=='Start Prompt':
        im=Image.new('RGBA',(1020,116),(0,0,0,0));d=ImageDraw.Draw(im)
        rect(d,(7,5,1013,112),(20,6,8,245),(220,82,65,255),18,4)
        text(d,(510,60),'SELECT A STARTING COUNTRY',36,light,True,anchor='mm')
        install_costume(spr,'Select country',im,index=0)
    if spr['name']=='Country Nodes':
        for i,col in enumerate([(117,178,185),(249,204,80),(255,122,42),(228,39,44),(99,94,98)]):
            im=Image.new('RGBA',(60,60),(0,0,0,0))
            d=ImageDraw.Draw(im,'RGBA')
            d.ellipse((2,2,58,58),fill=(50,6,11,60),outline=(*col,110),width=3)
            d.ellipse((11,11,49,49),fill=(*col,238),outline=(252,224,210,255),width=2)
            # Retain a larger transparent hit area for country clicks.
            install_costume(spr,str(i),im,index=i)
# Remove hidden giant texture gallery, which made editing awkward and doubled file weight.
targets[:]=[t for t in targets if t['name']!='Unity Texture Library']
for i,spr in enumerate(targets):spr['layerOrder']=i

# The network is really locked: show missing prerequisite icons on evolution cards.
locks=new_sprite('Evolution Tree Locks')
lv=Image.new('RGBA',(54,38),(0,0,0,0))
ld=ImageDraw.Draw(lv)
ld.rounded_rectangle((6,13,48,36),radius=7,fill=(35,11,14,238),outline=(219,98,70,255),width=3)
ld.arc((16,2,38,29),180,360,fill=(235,199,175,255),width=5)
ld.ellipse((26,20,31,25),fill=(242,207,173,255))
install_costume(locks,'Locked evolution',lv)
lockc=Compiler(locks)
lockc.localvar('slot',1)
lockc.script(FLAG(),[HIDE(),SET('slot',1),REPEAT(12,[CREATE(),CHG('slot',1)])],x=30,y=35)
lockc.script(WHENCLONE(),[
 FOREVER([
  IF(EQ(V('Screen'),4),[
   IF(AND(GT(ITEM('Requires',A(M(S(V('EvoTab'),1),12),V('slot'))),0),
     EQ(ITEM('Upgrades',ITEM('Requires',A(M(S(V('EvoTab'),1),12),V('slot')))),0)),[
      GO(A(-96,M(MOD(S(V('slot'),1),3),156)), S(58,M(Expr('operator_mathop',{'NUM':D(S(V('slot'),1),3)},{'OPERATOR':['floor',None]}),48))),SHOW()
   ],[HIDE()])
  ],[HIDE()]),WAIT(.30)
 ])
],x=35,y=130)

# Report and check structural integrity before writing.
proj['meta']['agent']='Scratch Geo-Epidemic Remaster — native Scratch SIR / Unity asset integration'
proj['meta']['version']='2.0'
refs=set()
for t in targets:
    for v in t['costumes']+t['sounds']:refs.add(v['md5ext'])
missing=refs-set(files)
if missing:raise ValueError(f'Missing assets: {sorted(missing)}')
OUT.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(OUT,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    z.writestr('project.json',json.dumps(proj,separators=(',',':'),ensure_ascii=False))
    for fn in sorted(refs):z.writestr(fn,files[fn])
print('Saved:',OUT,OUT.stat().st_size,'bytes',
      'sprites',len(targets)-1,'blocks',sum(len(t['blocks']) for t in targets),
      'country regions',N,'textures',len(orig_assets))