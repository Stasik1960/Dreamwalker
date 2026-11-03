"""Build immutable native mesh eligibility from the pinned mesh dictionary."""
from pathlib import Path
from zipfile import ZipFile
import json, struct, sys, math
from PIL import Image
from functools import lru_cache
root=Path(__file__).resolve().parents[2]
inputs=root/'DW-City-Visuals/tools/editor2-palette-inputs'
policy=json.loads((inputs/'classification.json').read_text(encoding='utf8'))['sprites']
maskimages={sid:Image.open(inputs/r['mask']).convert('L') for sid,r in policy.items() if r.get('classification')=='mixed' and r.get('mask')}
facades=json.loads((root/'DW-City-Visuals/src/main/resources/assets/bloodborne_dw/visuals/facades.json').read_text(encoding='utf8'))
bricks=set(facades['brickSprites']); rose=set(facades['roseSprites']); red=set(facades['brickWindowSprites'])
with ZipFile(Path(sys.argv[1])) as z:
    catalog=json.loads(z.read('assets/bloodborne_dw/native_grid/catalog.json'))
    raw=z.read('assets/bloodborne_dw/native_grid/meshes.bin')
materials=catalog['materials']; counts=catalog['meshes']; magic,count=struct.unpack_from('>II',raw,0)
if magic!=1313296689 or count!=len(counts): raise ValueError('Native mesh header mismatch')
@lru_cache(maxsize=32768)
def mask_hit(sprite,uv):
    image=maskimages.get(sprite)
    if image is None:return False
    us=uv[0::2];vs=uv[1::2]
    if not all(math.isfinite(v) and 0<=v<=16 for v in uv):return False
    x0=max(0,int(math.floor(min(us)*image.width/16)));x1=min(image.width,int(math.ceil(max(us)*image.width/16)))
    y0=max(0,int(math.floor(min(vs)*image.height/16)));y1=min(image.height,int(math.ceil(max(vs)*image.height/16)))
    if x0>=x1:x1=min(image.width,x0+1)
    if y0>=y1:y1=min(image.height,y0+1)
    return x0<x1 and y0<y1 and image.crop((x0,y0,x1,y1)).getbbox() is not None
flags=bytearray(count); offset=8
for i,entry in enumerate(counts):
    n,=struct.unpack_from('>I',raw,offset);offset+=4
    if n!=entry['quadCount']:raise ValueError(f'Quad count mismatch {i}')
    for q in range(n):
        material,=struct.unpack_from('>i',raw,offset+q*139)
        if material<0 or material>=len(materials):raise ValueError(f'Invalid material {material}')
        sprite=materials[material]['sprite']; rule=policy.get(sprite,{}); kind=rule.get('classification','unknown')
        if kind=='stone':flags[i]|=1
        elif kind=='mixed' and not flags[i]&1:
            uv=tuple(v for vertex in range(4) for v in struct.unpack_from('>2f',raw,offset+q*139+23+vertex*32))
            if mask_hit(sprite,uv):flags[i]|=1
        if sprite in bricks or sprite in rose:flags[i]|=2
        if sprite in bricks:flags[i]|=4|8
        if sprite in red:flags[i]|=16
    offset+=n*139
if offset!=len(raw):raise ValueError('Trailing native mesh data')
out=root/'DW-City-Visuals/src/main/resources/bloodborne_dw/editor2-native-flags.bin'
out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(flags)
print(f'{count} meshes, {sum(bool(f&1) for f in flags)} paintable, {len(flags)} bytes')
