"""Apply the reviewed sprite policy to frozen finite pose/fallback evidence."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[2];mod=root/'DW-City-Visuals'; inputs=mod/'tools/editor2-palette-inputs';output=mod/'src/main/resources/bloodborne_dw'
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
pose=json.loads((inputs/'pose-sprites.json').read_text(encoding='utf8'))['poses']
result={name:[[flags(pair[0]),flags(pair[1])] for pair in rows] for name,rows in pose.items()}
(output/'editor2-pose-flags.json').write_text(json.dumps({'schema':1,'poses':result},separators=(',',':')),encoding='utf8')
for name,key in [('fallback','states'),('wall','wall')]:
    data=json.loads((inputs/(name+'-sprites.json')).read_text(encoding='utf8'))[key]
    (output/('editor2-'+name+'-flags.bin')).write_bytes(bytes(flags(row) for row in data))
print(f'{len(result)} pose registries, {len(data)} wall primitives')
