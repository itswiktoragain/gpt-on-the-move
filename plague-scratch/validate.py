#!/usr/bin/env python3
"""Structural checks for an exported Scratch 3 (.sb3) ZIP project."""
import json
import sys
import zipfile
from pathlib import Path

f=Path(sys.argv[1])
with zipfile.ZipFile(f) as z:
    bad=z.testzip()
    if bad: raise RuntimeError(f"Corrupted ZIP entry: {bad}")
    project=json.loads(z.read('project.json'))
    targets=project['targets']
    assert targets and targets[0]['isStage'], "Scratch stage missing"
    assert len(targets)>=10, "Unexpectedly few Scratch sprites"
    assets=set(z.namelist())
    for target in targets:
        for item in target['costumes']+target['sounds']:
            assert item['md5ext'] in assets, (target['name'],item['md5ext'])
        blocks=target['blocks']
        for block_id, block in blocks.items():
            for link in ['next','parent']:
                if block.get(link) and block[link] not in blocks:
                    raise AssertionError((target['name'],block_id,link,block[link]))
    stage=targets[0]
    def lst(name):
        return next(contents[1] for contents in stage['lists'].values() if contents[0]==name)
    for key in ['Names','Population','Infected','Dead','Recovered','GovResponse','Adjacent1','Adjacent5']:
        assert len(lst(key))==72, f"{key} must cover all 72 regions"
    assert len(lst('Upgrades'))==36
    assert len(lst('Requires'))==36
    expected=['Epidemic Simulation','Passenger flights','Cargo shipping','World News Alerts','Evolution Tree Locks']
    actual=[t['name'] for t in targets]
    for name in expected:assert name in actual, f"Missing gameplay sprite: {name}"
    total=sum(len(t['blocks']) for t in targets)
    print(f"Validated {f.name}: {len(targets)} targets, {total} blocks, {len(assets)-1} assets")
