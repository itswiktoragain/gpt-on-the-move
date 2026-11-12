#!/usr/bin/env python3
"""Convert already-rendered Breaking the Bank Flash frames into a portable Scratch 3 project.

No extension, live network access, oversized sprite sheets, embedded JavaScript, or custom runtime.
Reuses identical sampled frames and packs per-scene lookup lists to save image decode memory.
"""
import argparse
import functools
import hashlib
import io
import json
import math
import os
import sys
import zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent
SCENES = [
    ('Intro', 0, 153), ('Tunnel', 161, 757), ('Explosion', 763, 1250),
    ('LazerDrill', 1251, 1640), ('WreckingBall', 1649, 2357),
    ('Teleporter', 2358, 2734), ('Disguise', 2735, 5394),
]
SOUND_PATH = {name: ROOT / f'{name}.mp3' for name, _, _ in SCENES}
BROADCASTS = {name: f'bscene_{i}' for i, (name, _, _) in enumerate(SCENES)}
BROADCASTS['Menu'] = 'breturn_menu'
SCENE_ID = 'g_scene'
DURATION_ID = 'g_duration'


def digest(data, ext):
    sha = hashlib.md5(data).hexdigest()
    return sha, sha + '.' + ext


class AssetBag:
    def __init__(self):
        self.files = {}

    def add(self, data, ext):
        aid, name = digest(data, ext)
        self.files.setdefault(name, data)
        return aid, name


class B:
    def __init__(self):
        self.blocks = {}
        self.counter = 0
        self.list_ids = {}
        self.var_ids = {'Scene': SCENE_ID, 'Duration': DURATION_ID}

    def set_var(self, name, identifier):
        self.var_ids[name] = identifier

    def set_list(self, name, identifier):
        self.list_ids[name] = identifier

    def create(self, opcode, parent=None, *, fields=None, shadow=False, top=False, x=0, y=0):
        self.counter += 1
        key = 'b' + str(self.counter)
        obj = dict(opcode=opcode, next=None, parent=parent, inputs={}, fields=fields or {}, shadow=shadow, topLevel=top)
        if top:
            obj.update(x=x, y=y)
        self.blocks[key] = obj
        return key

    def expr(self, value, parent):
        op, *args = value
        if op == 'var':
            name = args[0]
            return self.create('data_variable', parent, fields={'VARIABLE': [name, self.var_ids[name]]})
        if op == 'item':
            name, index = args
            result = self.create('data_itemoflist', parent, fields={'LIST': [name, self.list_ids[name]]})
            self.blocks[result]['inputs']['INDEX'] = self.input(index, result)
            return result
        if op in ('timer', 'mx', 'my'):
            return self.create({'timer': 'sensing_timer', 'mx': 'sensing_mousex', 'my': 'sensing_mousey'}[op], parent)
        table = {
            'add': ('operator_add','NUM1','NUM2'),
            'sub': ('operator_subtract','NUM1','NUM2'),
            'mul': ('operator_multiply','NUM1','NUM2'),
            'div': ('operator_divide','NUM1','NUM2'),
            'eq': ('operator_equals','OPERAND1','OPERAND2'),
            'gt': ('operator_gt','OPERAND1','OPERAND2'),
            'lt': ('operator_lt','OPERAND1','OPERAND2'),
            'and': ('operator_and','OPERAND1','OPERAND2'),
            'or': ('operator_or','OPERAND1','OPERAND2'),
        }
        if op in table:
            name, a, b = table[op]
            oid = self.create(name, parent)
            flag = op in ('and', 'or')
            self.blocks[oid]['inputs'][a] = self.input(args[0], oid, flag)
            self.blocks[oid]['inputs'][b] = self.input(args[1], oid, flag)
            return oid
        if op == 'floor':
            oid = self.create('operator_mathop', parent, fields={'OPERATOR': ['floor', None]})
            self.blocks[oid]['inputs']['NUM'] = self.input(args[0], oid)
            return oid
        if op == 'not':
            oid = self.create('operator_not', parent)
            self.blocks[oid]['inputs']['OPERAND'] = self.input(args[0], oid, True)
            return oid
        raise RuntimeError(f'Unsupported expression: {value}')

    def input(self, value, parent, boolean=False):
        if isinstance(value, tuple):
            key = self.expr(value, parent)
            return [2, key] if boolean else [3, key, [4,'0']]
        if isinstance(value, (int, float)):
            return [1, [4, str(value)]]
        return [1, [10, str(value)]]

    def emit(self, stmt, parent):
        op, *args = stmt
        if op in ('set', 'change'):
            name, value = args
            opcode = 'data_setvariableto' if op == 'set' else 'data_changevariableby'
            bid = self.create(opcode, parent, fields={'VARIABLE': [name, self.var_ids[name]]})
            self.blocks[bid]['inputs']['VALUE'] = self.input(value, bid)
            return bid
        if op in ('if', 'ifelse'):
            bid = self.create('control_if' if op == 'if' else 'control_if_else', parent)
            self.blocks[bid]['inputs']['CONDITION'] = self.input(args[0], bid, True)
            for field, child in zip(('SUBSTACK','SUBSTACK2'),args[1:]):
                sub = self.sequence(child, bid)
                if sub:
                    self.blocks[bid]['inputs'][field] = [2, sub]
            return bid
        if op == 'forever':
            bid = self.create('control_forever',parent)
            child = self.sequence(args[0],bid)
            if child:
                self.blocks[bid]['inputs']['SUBSTACK'] = [2,child]
            return bid
        if op == 'wait':
            bid = self.create('control_wait', parent)
            self.blocks[bid]['inputs']['DURATION'] = self.input(args[0], bid)
            return bid
        if op in ('hide','show','resettimer','stopall'):
            return self.create({'hide':'looks_hide','show':'looks_show','resettimer':'sensing_resettimer',
                                'stopall':'sound_stopallsounds'}[op],parent)
        if op == 'broadcast':
            name = args[0]
            bid = self.create('event_broadcast',parent)
            shadow = self.create('event_broadcast_menu',bid,fields={'BROADCAST_OPTION':[name,BROADCASTS[name]]},shadow=True)
            self.blocks[bid]['inputs']['BROADCAST_INPUT']=[1,shadow]
            return bid
        if op == 'switch':
            bid = self.create('looks_switchcostumeto',parent)
            shadow = self.create('looks_costume',bid,fields={'COSTUME':['Frame 1',None]},shadow=True)
            self.blocks[bid]['inputs']['COSTUME'] = [3,self.expr(args[0],bid),shadow]
            return bid
        if op == 'backdrop':
            bid = self.create('looks_switchbackdropto',parent)
            shadow = self.create('looks_backdrops',bid,fields={'BACKDROP':[args[0],None]},shadow=True)
            self.blocks[bid]['inputs']['BACKDROP'] = [1,shadow]
            return bid
        if op == 'sound':
            bid = self.create('sound_play',parent)
            shadow = self.create('sound_sounds_menu',bid,fields={'SOUND_MENU':[args[0],None]},shadow=True)
            self.blocks[bid]['inputs']['SOUND_MENU'] = [1,shadow]
            return bid
        raise RuntimeError(f'Unsupported statement: {stmt}')

    def sequence(self, statements, parent):
        head = None
        last = None
        for stmt in statements:
            key = self.emit(stmt, last if last else parent)
            if head is None:
                head = key
            if last is not None:
                self.blocks[last]['next'] = key
            last = key
        return head

    def script(self, hat, steps, *, fields=None,x=0,y=0):
        key = self.create(hat,None,fields=fields,top=True,x=x,y=y)
        self.blocks[key]['next'] = self.sequence(steps,key)
        return key


def V(n): return ('var',n)
def T(): return ('timer',)
def MX(): return ('mx',)
def MY(): return ('my',)
def E(op,*args): return (op,*args)


@functools.lru_cache(maxsize=3)
def sheet(index):
    with Image.open(ROOT / 'assets' / f'sheet_{index:04}.png') as im:
        return im.convert('RGB')


def source_frame(index):
    img = sheet(index // 6)
    cell = index % 6
    col = cell % 3
    row = cell // 3
    return img.crop((col*480,row*360,(col+1)*480,(row+1)*360))


def encode_png(img):
    buf = io.BytesIO()
    img.quantize(colors=256, method=Image.Quantize.MEDIANCUT).save(buf,format='PNG',optimize=True)
    return buf.getvalue()


def render_scene(bag, name, start, end, fps, width, height):
    # The Flash project is 30 FPS. Sample every 30/fps seconds using time instead of frame count.
    length = (end-start+1)/30
    nframes = math.ceil(length*fps-1e-8)
    lookups = []
    costumes = []
    identities = {}
    for n in range(nframes):
        src = min(end, start + int(round(n * 30 / fps)))
        image = source_frame(src).resize((width,height),Image.Resampling.LANCZOS)
        identity = hashlib.sha256(image.tobytes()).digest()
        if identity not in identities:
            raw = encode_png(image)
            asset_id, file_name = bag.add(raw,'png')
            identities[identity] = len(costumes)+1
            costumes.append(dict(assetId=asset_id,name=f'Frame {len(costumes)+1}',
                                 bitmapResolution=1,dataFormat='png',md5ext=file_name,
                                 rotationCenterX=width/2,rotationCenterY=height/2))
        lookups.append(identities[identity])
    return costumes,lookups,length


def build(fps, size, output):
    width,height = map(int,size.lower().split('x'))
    assert width * 3 == height * 4, 'Sprite frames must have a 4:3 aspect ratio'
    assert fps in (10,12,15,20)
    bag = AssetBag()
    menu = encode_png(source_frame(150))
    menu_id,menu_file=bag.add(menu,'png')
    stage_sounds=[]
    for name, start, end in SCENES:
        sound_bytes=SOUND_PATH[name].read_bytes()
        asset_id,file_name=bag.add(sound_bytes,'mp3')
        stage_sounds.append(dict(assetId=asset_id,name=name,dataFormat='mp3',format='',
                                 rate=44100,sampleCount=int(round((end-start+1)/30*44100)),md5ext=file_name))
    stage_b = B()
    stage_b.script('event_whenflagclicked',[
        ('set','Scene',-1),('backdrop','Menu'),('stopall',),('broadcast','Intro'),
    ],x=40,y=35)
    stage_b.script('event_whenbroadcastreceived',[
        ('set','Scene',-1),('stopall',),('backdrop','Menu'),
    ],fields={'BROADCAST_OPTION':['Menu',BROADCASTS['Menu']]},x=340,y=35)
    # Only the Stage is visible while choices can be clicked.
    stage_b.script('event_whenstageclicked',[
        ('if',E('eq',V('Scene'),-1),[
            ('ifelse',E('gt',MY(),0),[
                ('ifelse',E('lt',MX(),-80),[('broadcast','Tunnel')],[
                    ('ifelse',E('lt',MX(),80),[('broadcast','Explosion')],[('broadcast','LazerDrill')]),
                ]),
            ],[
                ('ifelse',E('lt',MX(),-80),[('broadcast','WreckingBall')],[
                    ('ifelse',E('lt',MX(),80),[('broadcast','Teleporter')],[('broadcast','Disguise')]),
                ]),
            ]),
        ]),
    ],x=40,y=280)
    for i,(name,start,end) in enumerate(SCENES[1:],1):
        stage_b.script('event_whenkeypressed',[
            ('if',E('eq',V('Scene'),-1),[('broadcast',name)]),
        ],fields={'KEY_OPTION':[str(i),None]},x=40+235*((i-1)//3),y=640+100*((i-1)%3))
    stage_b.script('event_whenkeypressed',[('broadcast','Menu')],fields={'KEY_OPTION':['r',None]},x=540,y=640)
    for i,(name,start,end) in enumerate(SCENES):
        stage_b.script('event_whenbroadcastreceived',[
            ('set','Scene',-1),('stopall',),('resettimer',),
            ('set','Duration',(end-start+1)/30),('set','Scene',i),('sound',name),
        ],fields={'BROADCAST_OPTION':[name,BROADCASTS[name]]},x=40+290*(i%4),y=1050+220*(i//4))
    stage_b.script('event_whenflagclicked',[
        ('forever',[
            ('if',E('and',E('gt',V('Scene'),-1),E('gt',T(),V('Duration'))),[
                ('broadcast','Menu'),
            ]),
            ('wait',0.03),
        ]),
    ],x=40,y=1610)

    stage=dict(isStage=True,name='Stage',variables={
        SCENE_ID:['Scene',-1],DURATION_ID:['Duration',0]}, lists={},
        broadcasts={bid:name for name,bid in BROADCASTS.items()},blocks=stage_b.blocks,comments={},
        currentCostume=0,costumes=[dict(assetId=menu_id,name='Menu',bitmapResolution=1,
                                       md5ext=menu_file,dataFormat='png',rotationCenterX=240,rotationCenterY=180)],
        sounds=stage_sounds,volume=100,layerOrder=0,tempo=60,videoTransparency=50,
        videoState='off',textToSpeechLanguage=None)
    targets=[stage]
    for i,(name,start,end) in enumerate(SCENES):
        costumes,frame_map,duration=render_scene(bag,name,start,end,fps,width,height)
        assert all(0 < x <= len(costumes) for x in frame_map)
        frm_id=f'frame_{i}'
        last_id=f'last_{i}'
        lut_id=f'lut_{i}'
        b=B()
        b.set_var('Current Frame',frm_id)
        b.set_var('Last Frame',last_id)
        b.set_list('FrameLookup',lut_id)
        b.script('event_whenflagclicked',[
            ('hide',),('set','Last Frame',0),
            ('forever',[
                ('ifelse',E('eq',V('Scene'),i),[
                    ('set','Current Frame',E('add',('floor',E('mul',T(),fps)),1)),
                    ('if',E('gt',V('Current Frame'),len(frame_map)),[('set','Current Frame',len(frame_map))]),
                    ('if',E('not',E('eq',V('Current Frame'),V('Last Frame'))),[
                        ('switch',('item','FrameLookup',V('Current Frame'))),
                        ('set','Last Frame',V('Current Frame')),
                        ('show',),
                    ]),
                ],[
                    ('if',E('not',E('eq',V('Last Frame'),0)),[
                        ('hide',),('set','Last Frame',0),
                    ]),
                ]),
                ('wait',0.02),
            ]),
        ],x=40,y=30)
        targets.append(dict(isStage=False,name=f'{name} animation',
                            variables={frm_id:['Current Frame',0],last_id:['Last Frame',0]},
                            lists={lut_id:['FrameLookup',frame_map]},broadcasts={},blocks=b.blocks,
                            comments={},currentCostume=0,costumes=costumes,sounds=[],volume=100,
                            layerOrder=i+1,visible=False,x=0,y=0,size=480/width*100,
                            direction=90,draggable=False,rotationStyle='don\'t rotate'))
        print(f'{name}: {len(frame_map)} time samples, {len(costumes)} unique costumes; duration {duration:.2f}s',flush=True)

    project={'targets':targets,'monitors':[], 'extensions':[],
             'meta':{'semver':'3.0.0','vm':'11.3.0','agent':'Native Scratch Flash conversion (no extensions)'}}
    out=Path(output)
    out.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_STORED,allowZip64=True) as z:
        z.writestr('project.json',json.dumps(project,ensure_ascii=False,separators=(',',':')))
        for name,data in bag.files.items():
            z.writestr(name,data)

    with zipfile.ZipFile(out) as z:
        assert z.testzip() is None
        data=json.loads(z.read('project.json'))
        assert len(data['targets']) == len(SCENES)+1
        assert data['extensions'] == [] and 'extensionURLs' not in data
        for target in data['targets']:
            for costume in target['costumes']:
                assert costume['md5ext'] in z.namelist()
            for sound in target['sounds']:
                assert sound['md5ext'] in z.namelist()
            blocks=target['blocks']
            for bid,block in blocks.items():
                for ref in (block.get('next'),block.get('parent')):
                    assert ref is None or ref in blocks,(bid,ref)
                for inp in block.get('inputs',{}).values():
                    if isinstance(inp,list):
                        for ref in inp[1:]:
                            if isinstance(ref,str):
                                assert ref in blocks,(bid,ref)
            for name, vals in target['lists'].values():
                assert all(isinstance(v,int) for v in vals)
            assert not any(block['opcode'].startswith('bankstream_') for block in blocks.values())
    totalcostumes=sum(len(t['costumes']) for t in targets)
    totalframes=sum(len(t['lists'][f'lut_{i}'][1]) for i,t in enumerate(targets[1:]))
    print('OK',out,'MiB',round(out.stat().st_size/2**20,2),'unique assets',len(bag.files),
          'costumes',totalcostumes,'sampled frames',totalframes,
          'approx raw RGBA MiB',round((totalcostumes-1)*width*height*4/2**20,2),flush=True)

if __name__ == '__main__':
    ap=argparse.ArgumentParser()
    ap.add_argument('--fps',type=int,default=12)
    ap.add_argument('--size',default='192x144')
    ap.add_argument('--output',default=str(ROOT/'BreakingTheBank_Vanilla.sb3'))
    args=ap.parse_args()
    build(args.fps,args.size,args.output)
