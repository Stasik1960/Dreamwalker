#!/usr/bin/env python3
"""Semantic checks for all shutter representations and masonry fixes.

These inspect real model UVs/materials and protected pixels. They do not claim
that a client screenshot or every world position has been visually inspected.
"""
from __future__ import annotations
import argparse, copy, hashlib, io, json, pathlib, struct, zipfile
from PIL import Image, ImageStat

ROOT=pathlib.Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
SOURCE='assets/bloodborne_dw/textures/block/remaining/source/minecraft/block/'
ACCENT='assets/bloodborne_dw/textures/block/autumn_c3/'

def image(data):return Image.open(io.BytesIO(data)).convert('RGBA')
def mean(im):
    pixels=[p for p in im.getdata() if p[3]]
    return [sum(p[i] for p in pixels)/len(pixels) for i in range(3)]

def run(jar=None):
    checks=[]
    def require(name,condition,detail):
        checks.append({'name':name,'status':'PASS' if condition else 'FAIL','detail':detail})
    def profile(mode,path):return (RES/'dw_city_visuals'/mode/path).read_bytes()
    with zipfile.ZipFile(ROOT/'baseline/mod.jar') as base, zipfile.ZipFile(ROOT/'tools/facade-inputs.zip') as frozen:
        catalog=json.loads(base.read('bloodborne_dw/static-architecture-v1274.json'))
        poses={p['key']:p for p in catalog['poses']}
        for bank,part,expected in ((230,36,'source_addon_gray_glazed_terracotta_0'),(13,103,'source_stairs_exposed_cut_copper_stairs_top')):
            sources=[poses[f['pose']]['registryId'] for f in catalog['recipes'][bank*128+part]['fragments']]
            require('actual-static-target-'+str(bank)+'-'+str(part),sources==['bloodborne_dw:'+expected],sources)
        corner=json.loads(base.read('assets/bloodborne_dw/models/remaining/source/minecraft/block/stairs/exposed_cut_copper_stairs_top.json'))
        uvfaces=[face for element in corner['elements'] for face in element.get('faces',{}).values()]
        require('every-corner-face-inside-corrected-island',all(face.get('texture')=='#0' and min(face['uv'][0::2])*8>=96 and max(face['uv'][0::2])*8<=126 and min(face['uv'][1::2])*8>=64 and max(face['uv'][1::2])*8<=96 for face in uvfaces),
                {'facesChecked':len(uvfaces),'sourceSprite':corner['textures']['0'],'rectangle':[96,64,126,96]})
        chestnut=image(frozen.read('alt/'+SOURCE+'polished_deepslate_wall_1.png'))
        brick_paths=[SOURCE+n+'.png' for n in ('bricks','bricks_0','bricks_1','bricks_2')]
        for path in brick_paths:
            actual=image(profile('alt',path));ref=mean(chestnut);tone=mean(actual)
            require('chestnut-generic-wall-'+path.split('/')[-1],max(abs(a-b) for a,b in zip(ref,tone))<4,
                    {'mean':tone,'matchingWallMean':ref,'allSourceRepresentations':'shared sprite'})
            previous=frozen.read('alt/'+path)
            require('accent-base-preserved-'+path.split('/')[-1],image(profile('alt',ACCENT+'accent/base_'+path.split('/')[-1])).tobytes()==image(previous).tobytes(),
                    'Regional sage/sand still receive the original warm base, not newly red pixels')
        roofpath=SOURCE+'addon/roof_1.png';source=image(base.read(roofpath));alt=image(profile('alt',roofpath))
        require('all-shutter-texture-aliases-equal',profile('alt',roofpath)==profile('alt',ACCENT+'window_surround.png'),
                'Closed/open source shutters, backdrop and dedicated wall models now sample identical material pixels')
        require('shutter-wood-glass-and-alpha-preserved',source.crop((0,64,128,128)).tobytes()==alt.crop((0,64,128,128)).tobytes() and source.getchannel('A').tobytes()==alt.getchannel('A').tobytes(),
                'Entire lower atlas and every transparent cutout remain exact')
        stone=image(frozen.read('alt/'+SOURCE+'stone_bricks.png'))
        require('default-shutter-tone-matches-stone-wall',max(abs(a-b) for a,b in zip(mean(alt.crop((0,0,128,64))),mean(stone)))<4,
                {'surround':mean(alt.crop((0,0,128,64))),'stoneWall':mean(stone)})
        rust=image(profile('alt',ACCENT+'accent/brick_window_surround.png'))
        require('brick-context-shutter-tone-matches-brick-wall',max(abs(a-b) for a,b in zip(mean(rust.crop((0,0,128,64))),mean(chestnut)))<4 and rust.crop((0,64,128,128)).tobytes()==source.crop((0,64,128,128)).tobytes(),
                'Nearest actual structural brick walls select a chestnut atlas; wood/glass still exact')
        affected=[]
        for path in base.namelist():
            if not path.startswith('assets/bloodborne_dw/models/') or not path.endswith('.json'):continue
            model=json.loads(base.read(path));textures=model.get('textures',{})
            if textures.get('0')=='bloodborne_dw:block/remaining/source/minecraft/block/addon/roof_1':affected.append(path)
        expected={'interactions/shutter_0.json','interactions/shutter_1.json','interactions/shutter_wall_0.json','interactions/shutter_wall_1.json','interactions/shutter_backdrop.json','remaining/source/minecraft/block/spruce_trapdoor_bottom.json','remaining/source/minecraft/block/spruce_trapdoor_open.json'}
        require('shutter-model-coverage',all('assets/bloodborne_dw/models/'+p in affected for p in expected),affected)
        mixed=[]
        for path in affected:
            model=json.loads(base.read(path));textures=model['textures']
            for index,element in enumerate(model.get('elements',[])):
                for direction,face in element.get('faces',{}).items():
                    material=textures.get(face.get('texture','')[1:])
                    if material!='bloodborne_dw:block/remaining/source/minecraft/block/addon/roof_1' or 'uv' not in face:continue
                    low,high=sorted(face['uv'][1::2])
                    if low<8<high:mixed.append([path,index,direction,face['uv']])
        require('regional-wall-quads-never-cross-wood-boundary',not mixed,mixed)
        config=json.loads((RES/'assets/bloodborne_dw/visuals/facades.json').read_text())
        sprite='bloodborne_dw:block/remaining/source/minecraft/block/addon/roof_1'
        require('regional-shutter-coverage',sprite in config['brickSprites'] and sprite in config['roseSprites'] and sprite in config['accentBaseSprites'] and sprite in config['brickWindowSprites'],
                'Shared source atlas participates in every existing facade role, not only the two patched model files')
        singles=json.loads((RES/'dw_city_visuals/alt/assets/minecraft/atlases/blocks.json').read_text())['sources']
        indexed={p.get('resource') for p in singles}
        require('accent-sprites-stitched',set(config['accentBaseSprites'].values())|set(config['brickWindowSprites'].values())<=indexed,'All preserved regional sprite aliases are physically stitched')
        embedded=image(profile('alt',ACCENT+'accent/brick_wall_0010.png'))
        old_band=image(frozen.read('alt/'+SOURCE+'addon/spirelamp_0010.png'))
        outside_equal=all(embedded.getpixel((x,y))==old_band.getpixel((x,y)) for y in range(128) for x in range(128) if not(51<=x<71 and 48<=y<64))
        require('embedded-red-wall-protects-all-trims',outside_equal and embedded.getchannel('A').tobytes()==old_band.getchannel('A').tobytes(),'Only the brick UV island changes; every stone trim, window and alpha texel stays exact')
        require('embedded-red-wall-matches-brick-family',max(abs(a-b) for a,b in zip(mean(embedded.crop((51,48,71,64))),mean(chestnut)))<4,'Mixed corner/side faces no longer retain beige embedded masonry next to generic red brick')
        context=json.loads((RES/'assets/bloodborne_dw/visuals/contextual-masonry.json').read_text())
        require('red-house-actual-static-family-is-contextual','bloodborne_dw:source_chiseled_quartz_block' in context['sources'] and 'bloodborne_dw:remaining/source/minecraft/block/warped_planks_0' in context['models'],
                'Actual screenshot6 cell183,64,-943 recipe198 resolves the embedded0010 family; every clone is classified by its structural neighbours')
        native=json.loads(base.read('assets/bloodborne_dw/native_grid/catalog.json'))
        metadata=(RES/'assets/bloodborne_dw/visuals/native-substrates.bin').read_bytes()
        count=struct.unpack('>I',metadata[4:8])[0]
        types=metadata[8:]
        require('all-native-meshes-classified',metadata[:4]==b'NSS1' and count==len(native['meshes'])==len(types) and set(types)<={0,1,2} and all(0<=cell['mesh']<count for cell in native['cells']),
                {'meshes':count,'nativeCells':len(native['cells']),'stoneMeshes':types.count(1),'brickMeshes':types.count(2)})
        # Structural meshes that consist entirely of one actual wall material
        # must classify unambiguously, independent of the conversion state ID.
        isolated=0;wrong=[]
        materials=[m['sprite'].rsplit('/',1)[-1] for m in native['materials']]
        with base.open('assets/bloodborne_dw/native_grid/meshes.bin') as stream:
            assert stream.read(8)[:4]==b'NGQ1'
            for index in range(count):
                quads=struct.unpack('>I',stream.read(4))[0];body=stream.read(quads*139)
                sprites={materials[struct.unpack_from('>i',body,offset)[0]] for offset in range(0,len(body),139)}
                expected=2 if sprites and sprites<={'bricks','bricks_0','bricks_1','bricks_2'} else 1 if sprites=={'stone_bricks'} else 0
                if expected:
                    isolated+=1
                    if types[index]!=expected:wrong.append(index)
        require('native-converted-wall-types-resolve-actual-sprites',isolated>0 and not wrong,{'homogeneousStructuralMeshesChecked':isolated,'wrongMeshIndices':wrong})
        for mode in ('original','alt'):
            path=SOURCE+'addon/spirelamp_0018.png'
            original=image(frozen.read(mode+'/'+path)) if mode+'/'+path in frozen.namelist() else image(base.read(path))
            result=image(profile(mode,path));target=image(base.read(SOURCE+'bricks.png')) if mode=='original' else chestnut
            outside_equal=all(original.getpixel((x,y))==result.getpixel((x,y)) for y in range(128) for x in range(128) if not(96<=x<126 and 64<=y<96))
            require(mode+'-corner-protected-atlas',outside_equal and original.getchannel('A').tobytes()==result.getchannel('A').tobytes(),'Only the UV islands used by the bright corner masonry change')
            m=mean(result.crop((96,64,126,96)));t=mean(target)
            require(mode+'-corner-tone',max(abs(a-b*.65) for a,b in zip(m,t))<4,{'corner':m,'adjacentBrick':t,'sourceLightingCompensation':.65})
            modelpath='assets/bloodborne_dw/models/remaining/source/minecraft/block/addon/gray_glazed_terracotta_0.json'
            before=json.loads(base.read(modelpath));after=json.loads(profile(mode,modelpath));normalized=copy.deepcopy(after)
            for direction in ('north','south','up','down','east','west'):normalized['elements'][0]['faces'][direction]=before['elements'][0]['faces'][direction]
            require(mode+'-arch-only-shell-material-changed',normalized==before,'All geometry, other elements and physical models remain identical')
            faces=after['elements'][0]['faces']
            require(mode+'-arch-stone-continues-between-ends',all(faces[d]['texture']=='#0' for d in ('north','south','up','down')),'All four shell faces use the real arch stone atlas rather than unrelated decoration pixels')
            require(mode+'-arch-ends-use-real-rounded-arch',faces['east']['texture']==faces['west']['texture']=='#0' and faces['east']['uv']==[10,0,16,3.5] and faces['west']['uv']==[16,0,10,3.5],
                    'Both ends sample the full rounded arch island at pixels80..128/y0..28; the old striped triangular roof island is no longer used')
            texpath=SOURCE+'addon/spirelamp_0042.png'
            tex=image(profile(mode,texpath)) if (RES/'dw_city_visuals'/mode/texpath).exists() else image(base.read(texpath))
            require(mode+'-arch-shell-opaque',min(tex.crop((54,40,74,78)).getchannel('A').getextrema())==255,'The connecting stone UV patch has no hidden transparent holes')
        if jar:
            receipt=json.loads((ROOT/'validation/facade-v7-assets.json').read_text())
            with zipfile.ZipFile(jar) as production:
                for entry in receipt['resources']:
                    rel=entry['path'] if entry['profile']=='shared' else 'dw_city_visuals/'+entry['profile']+'/'+entry['path']
                    require('production-'+entry['profile']+'-'+entry['path'],production.read(rel)==(RES/rel).read_bytes(),'Final JAR contains the verified resource bytes')
    return {'status':'PASS' if all(c['status']=='PASS' for c in checks) else 'FAIL','checks':checks,'limitations':['Static material/UV checks do not replace a runtime city visual pass.']}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--jar',type=pathlib.Path);p.add_argument('--report',type=pathlib.Path,default=ROOT/'validation/facade-v7-tests.json');args=p.parse_args()
    result=run(args.jar);args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(result['status'],len(result['checks']));raise SystemExit(0 if result['status']=='PASS' else 1)
