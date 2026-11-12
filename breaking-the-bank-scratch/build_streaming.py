"""Build a lightweight TurboWarp SB3 with streamed video, original audio and Scratch logic.
The project itself contains no Flash frame sheets. Requires bankstream.js and public MP4.
"""
import hashlib
import json
import os
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / 'BreakingTheBank_Lite.sb3'
SCENES = [
    ('Intro', 0, 153),
    ('Tunnel', 161, 757),
    ('Explosion', 763, 1250),
    ('LazerDrill', 1251, 1640),
    ('WreckingBall', 1649, 2357),
    ('Teleporter', 2358, 2734),
    ('Disguise', 2735, 5394)
]
VARIABLES = ['Playing', 'Scene end', 'Choice']
VAR_IDS = {name: 'var' + str(i) for i, name in enumerate(VARIABLES)}
BCAST = {name: 'bc' + str(i) for i, (name, _, _) in enumerate(SCENES)}

def expr(name, *args):
    return (name, *args)

class Blocks:
    def __init__(self):
        self.items = {}
        self.count = 0

    def make(self, opcode, parent=None, fields=None, top=False, shadow=False, x=0, y=0):
        self.count += 1
        key = 'block' + str(self.count)
        b = {
            'opcode': opcode, 'next': None, 'parent': parent,
            'inputs': {}, 'fields': fields or {},
            'shadow': shadow, 'topLevel': top
        }
        if top:
            b['x'] = x
            b['y'] = y
        self.items[key] = b
        return key

    def input(self, value, parent, boolean=False):
        if isinstance(value, tuple):
            id_ = self.expression(value, parent)
            return [2, id_] if boolean else [3, id_, [4, '0']]
        if isinstance(value, (int, float)):
            return [1, [4, str(value)]]
        return [1, [10, str(value)]]

    def expression(self, val, parent):
        op, *data = val
        if op == 'var':
            name = data[0]
            return self.make('data_variable', parent, {'VARIABLE': [name, VAR_IDS[name]]})
        if op in ('mx', 'my'):
            return self.make('sensing_mousex' if op == 'mx' else 'sensing_mousey', parent)
        if op == 'finished':
            return self.make('bankstream_finishedPlaying', parent)
        if op == 'abs':
            id_ = self.make('operator_mathop', parent, {'OPERATOR': ['abs', None]})
            self.items[id_]['inputs']['NUM'] = self.input(data[0], id_)
            return id_
        operators = {
            'add': ('operator_add', 'NUM1', 'NUM2'),
            'sub': ('operator_subtract', 'NUM1', 'NUM2'),
            'eq': ('operator_equals', 'OPERAND1', 'OPERAND2'),
            'lt': ('operator_lt', 'OPERAND1', 'OPERAND2'),
            'gt': ('operator_gt', 'OPERAND1', 'OPERAND2'),
            'and': ('operator_and', 'OPERAND1', 'OPERAND2'),
        }
        if op in operators:
            opcode, first, second = operators[op]
            id_ = self.make(opcode, parent)
            self.items[id_]['inputs'][first] = self.input(data[0], id_, op == 'and')
            self.items[id_]['inputs'][second] = self.input(data[1], id_, op == 'and')
            return id_
        raise ValueError(val)

    def sequence(self, statements, parent):
        first = None
        prev = None
        for item in statements:
            id_ = self.statement(item, parent if prev is None else prev)
            if first is None:
                first = id_
            if prev is not None:
                self.items[prev]['next'] = id_
            prev = id_
        return first

    def statement(self, action, parent):
        op, *args = action
        if op in ('set', 'change'):
            name, val = args
            id_ = self.make('data_setvariableto' if op == 'set' else 'data_changevariableby',
                            parent, {'VARIABLE': [name, VAR_IDS[name]]})
            self.items[id_]['inputs']['VALUE'] = self.input(val, id_)
            return id_
        if op == 'stop-other':
            return self.make('control_stop', parent, {'STOP_OPTION': ['other scripts in sprite', None]})
        if op == 'stop-sounds':
            return self.make('sound_stopallsounds', parent)
        if op == 'video':
            id_ = self.make('bankstream_playSegment', parent)
            self.items[id_]['inputs']['START'] = self.input(args[0], id_)
            self.items[id_]['inputs']['END'] = self.input(args[1], id_)
            return id_
        if op == 'sound':
            id_ = self.make('sound_play', parent)
            shadow = self.make('sound_sounds_menu', id_,
                               {'SOUND_MENU': [args[0], None]}, shadow=True)
            self.items[id_]['inputs']['SOUND_MENU'] = [1, shadow]
            return id_
        if op == 'wait-until':
            id_ = self.make('control_wait_until', parent)
            self.items[id_]['inputs']['CONDITION'] = self.input(args[0], id_, True)
            return id_
        if op == 'broadcast':
            name = args[0]
            id_ = self.make('event_broadcast', parent)
            shadow = self.make('event_broadcast_menu', id_,
                               {'BROADCAST_OPTION': [name, BCAST[name]]}, shadow=True)
            self.items[id_]['inputs']['BROADCAST_INPUT'] = [1, shadow]
            return id_
        if op in ('if', 'ifelse'):
            id_ = self.make('control_if' if op == 'if' else 'control_if_else', parent)
            self.items[id_]['inputs']['CONDITION'] = self.input(args[0], id_, True)
            first = self.sequence(args[1], id_)
            if first:
                self.items[id_]['inputs']['SUBSTACK'] = [2, first]
            if op == 'ifelse':
                second = self.sequence(args[2], id_)
                if second:
                    self.items[id_]['inputs']['SUBSTACK2'] = [2, second]
            return id_
        raise ValueError(action)

    def script(self, hat, statements, fields=None, x=0, y=0):
        root = self.make(hat, None, fields, top=True, x=x, y=y)
        first = self.sequence(statements, root)
        if first:
            self.items[root]['next'] = first

B = Blocks()
V = lambda x: expr('var', x)
EQ = lambda a, b: expr('eq', a, b)
LT = lambda a, b: expr('lt', a, b)
GT = lambda a, b: expr('gt', a, b)
AND = lambda a, b: expr('and', a, b)
ABS = lambda a: expr('abs', a)

B.script('event_whenflagclicked', [
    ('broadcast', 'Intro')
], x=40, y=40)
for i, (name, start, end) in enumerate(SCENES):
    B.script('event_whenbroadcastreceived', [
        ('stop-other',),
        ('set', 'Playing', 1),
        ('set', 'Scene end', end),
        ('stop-sounds',),
        ('video', start, end),
        ('sound', name),
        ('wait-until', expr('finished')),
        ('set', 'Playing', 0)
    ], fields={'BROADCAST_OPTION': [name, BCAST[name]]},
       x=40 + 260 * (i % 4), y=250 + 220 * (i // 4))

# Click any part of the original menu to select one of its six buttons.
# After a fail/win movie, any click starts the menu again.
click_logic = [
    ('if', EQ(V('Playing'), 0), [
        ('ifelse', EQ(V('Scene end'), 153), [
            ('if', AND(LT(ABS(expr('mx')), 225),
                       LT(ABS(expr('my')), 175)), [
                ('set', 'Choice', 1),
                ('if', GT(expr('mx'), -75), [('set', 'Choice', 2)]),
                ('if', GT(expr('mx'), 75), [('set', 'Choice', 3)]),
                ('if', LT(expr('my'), 0), [('change', 'Choice', 3)]),
                *[
                    ('if', EQ(V('Choice'), i), [('broadcast', name)])
                    for i, (name, _, _) in enumerate(SCENES[1:], 1)
                ]
            ])
        ], [('broadcast', 'Intro')])
    ])
]
B.script('event_whenthisspriteclicked', click_logic, x=40, y=760)
for i, (name, _, _) in enumerate(SCENES[1:], 1):
    B.script('event_whenkeypressed', [('broadcast', name)],
             fields={'KEY_OPTION': [str(i), None]},
             x=340 + 180 * ((i - 1) // 3), y=760 + ((i - 1) % 3) * 100)
B.script('event_whenkeypressed', [('broadcast', 'Intro')],
         fields={'KEY_OPTION': ['r', None]}, x=800, y=760)

def make_asset(b, extension, output):
    digest = hashlib.md5(b).hexdigest()
    fname = digest + '.' + extension
    output[fname] = b
    return digest, fname

white_svg = (b'<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360" '
             b'viewBox="0 0 480 360"><rect width="480" height="360" fill="#fff"/></svg>')
assets = {}
digest, fname = make_asset(white_svg, 'svg', assets)
costume = {
    'assetId': digest, 'name': 'Click surface',
    'md5ext': fname, 'dataFormat': 'svg',
    'rotationCenterX': 240, 'rotationCenterY': 180
}
sound_assets = []
for name, start, end in SCENES:
    data = (HERE / (name + '.mp3')).read_bytes()
    md5, file = make_asset(data, 'mp3', assets)
    sound_assets.append({
        'assetId': md5, 'name': name, 'dataFormat': 'mp3', 'format': '',
        'rate': 44100, 'sampleCount': round((end - start + 1) * 44100 / 30),
        'md5ext': file
    })
stage = {
    'isStage': True, 'name': 'Stage', 'variables': {}, 'lists': {},
    'broadcasts': {v: k for k, v in BCAST.items()},
    'blocks': {}, 'comments': {}, 'currentCostume': 0,
    'costumes': [dict(costume, name='White background')], 'sounds': [],
    'volume': 100, 'layerOrder': 0, 'tempo': 60, 'videoTransparency': 50,
    'videoState': 'off', 'textToSpeechLanguage': None
}
sprite = {
    'isStage': False, 'name': 'Bank - click menu and run scenes',
    'variables': {VAR_IDS[n]: [n, 0] for n in VARIABLES},
    'lists': {}, 'broadcasts': {}, 'blocks': B.items, 'comments': {},
    'currentCostume': 0, 'costumes': [costume], 'sounds': sound_assets,
    'volume': 100, 'layerOrder': 1, 'visible': True,
    'x': 0, 'y': 0, 'size': 100, 'direction': 90,
    'draggable': False, 'rotationStyle': "don't rotate"
}
extension_url = (
    'https://raw.githubusercontent.com/itswiktoragain/gpt-on-the-move/'
    'main/breaking-the-bank-scratch/bankstream.js'
)
project = {
    'targets': [stage, sprite], 'monitors': [],
    'extensions': ['bankstream'],
    'extensionURLs': {'bankstream': extension_url},
    'meta': {'semver': '3.0.0', 'vm': '11.3.0',
             'agent': 'Bank Stream - original assets with low-memory TurboWarp playback'}
}
with zipfile.ZipFile(OUT, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=5) as z:
    z.writestr('project.json', json.dumps(project, separators=(',', ':')))
    for name, data in assets.items():
        z.writestr(name, data)
with zipfile.ZipFile(OUT) as z:
    assert z.testzip() is None
    p = json.loads(z.read('project.json'))
    assert p['extensions'] == ['bankstream']
    assert len(p['targets'][1]['costumes']) == 1
    assert len(p['targets'][1]['sounds']) == 7
    assert len(p['targets'][1]['blocks']) > 80
    assert len(p['targets'][1]['costumes']) + len(p['targets'][0]['costumes']) == 2
    assert all(c['md5ext'] in z.namelist() for t in p['targets'] for c in t['costumes'])
    assert all(s['md5ext'] in z.namelist() for s in p['targets'][1]['sounds'])
    for block_id, block in B.items.items():
        for key in ('next', 'parent'):
            link = block[key]
            assert link is None or link in B.items, (block_id, key, link)
        for inp in block['inputs'].values():
            for dep in inp[1:]:
                if isinstance(dep, str):
                    assert dep in B.items, (block_id, dep)
print('Built and validated', OUT, 'bytes', OUT.stat().st_size,
      'Scratch blocks', len(B.items), 'assets', len(assets))
