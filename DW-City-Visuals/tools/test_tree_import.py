#!/usr/bin/env python3
"""Verify the actual resource paths, user payload and protected original trees.

Optionally inspects the combined production JAR, including the packaged classes
and its active ALT resource paths. Does not claim an in-game visual pass.
"""
from __future__ import annotations
import argparse
import base64
from io import BytesIO
import json
import math
from pathlib import Path
import zipfile
from PIL import Image

WORK = Path(__file__).resolve().parents[1]
ASSETS = WORK / 'src/main/resources'


def rotated_vertices(geometry):
    """Mirror X and apply cube/ancestor pivots, following the RP geometry loader."""
    bones = geometry['minecraft:geometry'][0]['bones']
    lookup = {b['name']:b for b in bones}
    def mirror(vector):
        return [-vector[0],vector[1],vector[2]]
    def around(point, pivot, degrees):
        x,y,z = [point[i]-pivot[i] for i in range(3)]
        a=math.radians(-degrees[0]);c,s=math.cos(a),math.sin(a)
        y,z=y*c-z*s,y*s+z*c
        a=math.radians(-degrees[1]);c,s=math.cos(a),math.sin(a)
        x,z=x*c+z*s,-x*s+z*c
        a=math.radians(degrees[2]);c,s=math.cos(a),math.sin(a)
        x,y=x*c-y*s,x*s+y*c
        return [x+pivot[0],y+pivot[1],z+pivot[2]]
    for bone in bones:
        for cube in bone.get('cubes',[]):
            for corner in range(8):
                point=mirror([cube['origin'][i]+(cube['size'][i] if corner&(1<<i) else 0) for i in range(3)])
                point=around(point,mirror(cube.get('pivot',[0,0,0])),cube.get('rotation',[0,0,0]))
                parent=bone
                while parent:
                    point=around(point,mirror(parent.get('pivot',[0,0,0])),parent.get('rotation',[0,0,0]))
                    parent=lookup.get(parent.get('parent'))
                yield [v/16 for v in point]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--report', type=Path, default=WORK / 'validation/tree-contract-v7.json')
    args = parser.parse_args()
    checks = []
    def check(name, condition, detail):
        checks.append({'name': name, 'status': 'PASS' if condition else 'FAIL', 'detail': detail})
        if not condition:
            raise AssertionError(name + ': ' + detail)
    project_raw = (WORK / 'inputs/trees/DreamWalker-trees.dwtree.json').read_bytes()
    project = json.loads(project_raw.decode('utf-8-sig'))
    jar = zipfile.ZipFile(args.jar) if args.jar else None
    def resource(path):
        return jar.read(path) if jar else (ASSETS / path).read_bytes()
    with zipfile.ZipFile(WORK / 'baseline/mod.jar') as base:
        for model in project['models']:
            path = model['assetPath']
            alt = resource('dw_city_visuals/alt/' + path)
            original = resource('dw_city_visuals/original/' + path)
            check(model['id'] + '-exact-editor-geometry', json.loads(alt) == model['geometry'], 'All bones/cubes/UV/transform values equal the uploaded editor project')
            check(model['id'] + '-original-preserved', original == base.read(path), 'Original profile remains byte-identical to the pinned original geometry')
            vertices=list(rotated_vertices(model['geometry']))
            minimum=[min(v[i] for v in vertices) for i in range(3)]
            maximum=[max(v[i] for v in vertices) for i in range(3)]
            check(model['id'] + '-finite-render-envelope', all(-16 <= v[0] <= 16 and -3 <= v[1] <= 27 and -16 <= v[2] <= 16 for v in vertices), f'All rotated cube corners fit the declared ALT render envelope: min={minimum}, max={maximum}')
        for texture in project['textures'].values():
            path = texture['assetPath']
            check('exact-editor-atlas', resource('dw_city_visuals/alt/' + path) == base64.b64decode(texture['dataURL'].split(',', 1)[1]), 'Uploaded embedded PNG bytes are used directly')
            check('original-atlas-preserved', resource('dw_city_visuals/original/' + path) == base.read(path), 'Original 3D atlas is unchanged')
        descriptor=b'(Ldev/dreamwalker/bloodbornerp/object/RpObjectEntity;Lnet/minecraft/class_4604;DDD)Z'
        check('renderer-injection-target-exists', descriptor in base.read('dev/dreamwalker/bloodbornerp/client/CatalogObjectRenderer.class'), 'Typed production shouldRender overload exists with intermediary Frustum descriptor')
    sprite_path = 'assets/bloodborne_dw/textures/block/remaining/source/minecraft/block/addon/'
    palette_rules=json.loads((WORK/'tools/v12-baked-palettes.json').read_text(encoding='utf-8'))
    tree_rule=next(r for r in palette_rules['sprites'] if r['sprite'].endswith('/addon/tree_1'))
    stone_mask={(x,y) for row in tree_rule['regions'] for x0,y0,x1,y1 in [row['pixels']] for y in range(y0,y1) for x in range(x0,x1)}
    with zipfile.ZipFile(WORK / 'tools/tree-sprite-inputs.zip') as frozen:
        for name in ('tree_1.png', 'tree_2.png'):
            before = Image.open(BytesIO(frozen.read(name))).convert('RGBA')
            after = Image.open(BytesIO(resource('dw_city_visuals/alt/' + sprite_path + name))).convert('RGBA')
            check(name + '-alpha-and-size', before.size == after.size and before.getchannel('A').tobytes() == after.getchannel('A').tobytes(), 'Geometry silhouette and transparency remain exact')
            leaves = []
            protected_unchanged=True
            for y in range(before.height):
                for x in range(before.width):
                    r,g,b,a = before.getpixel((x,y))
                    leaf=a and r > 1.15*g and g > 1.35*b and ((name == 'tree_1.png' and x >= 52 and y >= 60) or (name == 'tree_2.png' and x < 80 and y < 50))
                    if leaf:
                        leaves.append((sum((r,g,b)), sum(after.getpixel((x,y))[:3])))
                    allowed=leaf or (name == 'tree_1.png' and (x,y) in stone_mask) or (name == 'tree_2.png' and (x < 145 and y >= 63 or x < 140 and y < 50))
                    if not allowed and before.getpixel((x,y)) != after.getpixel((x,y)):
                        protected_unchanged=False
            check(name + '-foliage-lighter', bool(leaves) and all(new > old for old,new in leaves), f'{len(leaves)} authored ochre leaf texels increase luminance')
            check(name + '-unrelated-islands-protected', protected_unchanged, 'Outside the requested leaf/bark areas and pre-existing stone mask, all atlas pixels are unchanged')
            if name == 'tree_2.png':
                # Resolve the actual screenshot target's UV instead of checking
                # only the larger tree artwork elsewhere in this shared atlas.
                with zipfile.ZipFile(WORK/'baseline/mod.jar') as base:
                    spec=json.loads(base.read('bloodborne_dw/composite/source_original_tree.json'))
                    twig=spec['variants'][5]['poses']['closed']['parts'][0]['model']
                    twig_model=json.loads(base.read('assets/'+twig.replace(':','/models/')+'.json'))
                    check('target-twig-uses-corrected-small-island',twig.endswith('/birch_log_1') and twig_model['textures']['1'].endswith('/tree_2') and all(set(face['uv'])<={0,3} for e in twig_model['elements'] for face in e['faces'].values()),'Actual source_original_tree variant5 uses the top-left48x48 island')
                residuals=[]
                for y in range(48):
                    for x in range(48):
                        px=after.getpixel((x,y));l=(77*px[0]+150*px[1]+29*px[2]+128)>>8
                        if px[3] and l<80 and max(px[:3])-min(px[:3])<48:residuals.append((x,y))
                check('small-twig-no-black-neutral-contour',not residuals,'No neutral twig contour remains darker than the main bark minimum; saturated ochre leaf detail is preserved')
                unchanged = changed = 0
                for y in range(63, 256):
                    for x in range(145):
                        old = before.getpixel((x,y))
                        new = after.getpixel((x,y))
                        if old[3] and old[:3] == (57,54,51):
                            changed += 1
                            check_value = new[:3] == (89,79,69)
                            if not check_value:
                                raise AssertionError('Dark outline bark does not match warm primary tree')
                check('second-tree-outline-matches-primary', changed == 1908, 'All 1908 darkest bark texels now match the main tree, preserving the original shape')
    mixins = json.loads(resource('dw-alt-tree-bounds.mixins.json'))
    check('tree-render-bounds-registered', 'AltTreeRenderBoundsMixin' in mixins['client'], 'Separate client-only culling adjustment')
    code = (WORK / 'src/client/java/dev/dreamwalker/bloodbornedw/visual/mixin/AltTreeRenderBoundsMixin.java').read_text(encoding='utf-8')
    check('bounds-mode-and-id-gated', '!CityVisualClient.isAlt()' in code and all(f'id.equals("tree{i}")' in code for i in (1,2,3)) and 'setBoundingBox' not in code, 'Only ALT tree rendering changes, without physical bounds mutation')
    if jar:
        check('production-tree-mixin-class', 'dev/dreamwalker/bloodbornedw/visual/mixin/AltTreeRenderBoundsMixin.class' in jar.namelist(), 'Compiled class present')
        fabric = json.loads(jar.read('fabric.mod.json'))
        check('production-tree-mixin-config', any(m == 'dw-alt-tree-bounds.mixins.json' or isinstance(m,dict) and m.get('config') == 'dw-alt-tree-bounds.mixins.json' for m in fabric['mixins']), 'Production Fabric metadata registers the config')
    result = {'status':'PASS', 'jar':str(args.jar) if args.jar else None, 'checks':checks}
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(result,indent=2) + '\n',encoding='utf-8')
    print(json.dumps({'status':'PASS','checks':len(checks),'report':str(args.report)}))


if __name__ == '__main__':
    main()
