"""Derive slab-model flags from frozen state-specific face sprite evidence."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[2];mod=root/'DW-City-Visuals'; inputs=mod/'tools/editor2-palette-inputs'
policy=json.loads((inputs/'classification.json').read_text(encoding='utf8'))['sprites']
fac=json.loads((mod/'src/main/resources/assets/bloodborne_dw/visuals/facades.json').read_text(encoding='utf8'));bricks=set(fac['brickSprites']);rose=set(fac['roseSprites']);red=set(fac['brickWindowSprites'])
def flags(sprites):
    result=0
    for sprite in sprites:
        rule=policy.get(sprite,{});kind=rule.get('classification','unknown')
        if kind=='stone' or (kind=='mixed' and rule.get('mask')):result|=1
        if sprite in bricks or sprite in rose:result|=2
        if sprite in bricks:result|=12
        if sprite in red:result|=16
    return result
slabs=json.loads((inputs/'slab-sprites.json').read_text(encoding='utf8'))['slabs']
result={name:[flags(row['bottom']),flags(row['top']),flags(row['double'])] for name,row in slabs.items()}
out=mod/'src/main/resources/bloodborne_dw/editor2-slab-flags.json';out.write_text(json.dumps({'schema':1,'slabs':result},separators=(',',':')),encoding='utf8')
print(f'{len(result)} exact material slab IDs')
