#!/usr/bin/env python3
"""Rebuild facade corrections from frozen v6 pixels, shared by every model path.

Only visual resources change. Shapes, source catalog recipes and world data stay
identical. Quantile transfer keeps masonry detail while matching a real existing
wall material rather than multiplying unrelated atlases by a guessed colour.
"""
from __future__ import annotations
import argparse, hashlib, io, json, pathlib, struct, zipfile
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[1]
RESOURCE = ROOT / 'src/main/resources'
PREFIX = 'assets/bloodborne_dw/textures/block/'
SOURCE = PREFIX + 'remaining/source/minecraft/block/'

def png(data):
    return Image.open(io.BytesIO(data)).convert('RGBA')

def lum(c):
    return (77*c[0] + 150*c[1] + 29*c[2] + 128) >> 8

def transfer(image, reference, rectangles=None):
    """Map luminance ranks to target RGB ranks; preserve every source alpha."""
    out = image.copy()
    target = sorted((p for p in reference.getdata() if p[3]), key=lum)
    points = [(x,y) for y in range(image.height) for x in range(image.width)
              if image.getpixel((x,y))[3] and (rectangles is None or any(a<=x<c and b<=y<d for a,b,c,d in rectangles))]
    values = sorted(lum(image.getpixel(p)) for p in points)
    import bisect
    for point in points:
        pixel = image.getpixel(point)
        value = lum(pixel)
        rank = (bisect.bisect_left(values,value)+bisect.bisect_right(values,value)-1)/2
        index = round(rank*max(0,len(target)-1)/max(1,len(values)-1))
        out.putpixel(point,(*target[index][:3],pixel[3]))
    return out

def generate():
    receipt=[]
    with zipfile.ZipFile(ROOT/'tools/facade-inputs.zip') as frozen, zipfile.ZipFile(ROOT/'baseline/mod.jar') as base:
        def read(mode,path):
            try:return frozen.read(mode+'/'+path)
            except KeyError:return base.read(path)
        def save(mode,path,image,reason):
            dest=RESOURCE/'dw_city_visuals'/mode/path
            dest.parent.mkdir(parents=True,exist_ok=True)
            buffer=io.BytesIO();image.save(buffer,format='PNG');data=buffer.getvalue();dest.write_bytes(data)
            receipt.append({'profile':mode,'path':path,'reason':reason,'sha256':hashlib.sha256(data).hexdigest()})
        def save_exact(mode,path,data,reason):
            dest=RESOURCE/'dw_city_visuals'/mode/path
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(data)
            receipt.append({'profile':mode,'path':path,'reason':reason,'sha256':hashlib.sha256(data).hexdigest()})
        chestnut=png(read('alt',SOURCE+'polished_deepslate_wall_1.png'))
        beige=png(read('alt',SOURCE+'bricks.png'))
        stone=png(read('alt',SOURCE+'stone_bricks.png'))
        for suffix in ('bricks','bricks_0','bricks_1','bricks_2'):
            previous=read('alt',SOURCE+suffix+'.png')
            old=png(previous)
            save('alt',PREFIX+'autumn_c3/accent/base_'+suffix+'.png',old,'preserve sage/sand regional base')
            save_exact('alt',SOURCE+suffix+'.png',previous,'restore global v6 masonry byte-for-byte, including streets and unrelated buildings')
            save('alt',PREFIX+'autumn_c3/accent/red_'+suffix+'.png',transfer(old,chestnut),'separate red masonry restricted to the explicitly bounded screenshot6 house walls')
        roof=png(read('original',SOURCE+'addon/roof_1.png'))
        brick_rect=[(0,0,128,64)]
        rust_roof=transfer(roof,chestnut,brick_rect)
        stone_roof=transfer(roof,stone,brick_rect)
        pale_roof=transfer(roof,beige,brick_rect)
        rose=png(read('alt',PREFIX+'autumn_c3/accent/bricks.png'))
        rose_roof=transfer(roof,rose,brick_rect)
        for path in (SOURCE+'addon/roof_1.png',PREFIX+'autumn_c3/window_surround.png'):
            save('alt',path,stone_roof,'all closed/open/source/saved stone shutter wall representations')
        save('alt',PREFIX+'autumn_c3/accent/brick_window_surround.png',rust_roof,'contextual chestnut brick shutter substrate')
        save('alt',PREFIX+'autumn_c3/accent/window_surround.png',rose_roof,'rose shutter wall matches rose masonry')
        save('alt',PREFIX+'autumn_c3/accent/base_window_surround.png',pale_roof,'preserve sage/sand shutter wall base')
        band_path=SOURCE+'addon/spirelamp_0010.png'
        band=png(read('alt',band_path))
        save('alt',PREFIX+'autumn_c3/accent/brick_wall_0010.png',transfer(band,chestnut,[(51,48,71,64)]),'embedded masonry follows neighbouring red brick walls; all stone trims preserved')
        # The corner model uses these two masonry patches of the shared atlas.
        # Editing their pixels also covers UV-locked models and native meshes.
        for mode in ('original','alt'):
            texture=png(read(mode,SOURCE+'addon/spirelamp_0018.png'))
            wall=png(read(mode,SOURCE+'bricks.png')) if mode=='original' else beige
            fixed=transfer(texture,wall,[(96,64,126,96)])
            # This authored corner keeps shade:false and receives substantially
            # brighter source lighting than the recessed adjoining brick wall.
            # Equal raw PNG means therefore stayed conspicuously pale in-game.
            # Compensate its isolated atlas island using the measured v7 frame.
            for y in range(64,96):
                for x in range(96,126):
                    pixel=fixed.getpixel((x,y))
                    if pixel[3]:fixed.putpixel((x,y),(*(round(c*.65) for c in pixel[:3]),pixel[3]))
            save(mode,SOURCE+'addon/spirelamp_0018.png',fixed,'corner masonry palette compensates unshaded source brightness')
        # User correction: preserve the original HALF-arch artwork and UVs.
        # Only remove its unwanted upper and non-arched side stone render faces.
        # Do not replace the artwork with a rounded full arch or another atlas.
        modelpath='assets/bloodborne_dw/models/remaining/source/minecraft/block/addon/gray_glazed_terracotta_0.json'
        model=json.loads(base.read(modelpath))
        shell=model['elements'][0]
        assert shell['from']==[-10,0,-16] and shell['to']==[26,25,21]
        for face in ('north','south','up'):
            assert shell['faces'][face]['texture']=='#4'
            del shell['faces'][face]
        for mode in ('original','alt'):
            dest=RESOURCE/'dw_city_visuals'/mode/modelpath
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_text(json.dumps(model,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
            receipt.append({'profile':mode,'path':modelpath,'reason':'original half-arch UVs restored; only top/north/south stone render faces suppressed'})
        # Discover every source family carrying the shared shutter backing or
        # embedded 0010 masonry. This routes all clones, not a single house box.
        models={}
        for name in base.namelist():
            if name.endswith('.json') and (name.startswith('assets/bloodborne_dw/models/remaining/') or name.startswith('assets/bloodborne_dw/models/interactions/')):
                key='bloodborne_dw:'+name.removeprefix('assets/bloodborne_dw/models/').removesuffix('.json')
                models[key]=json.loads(base.read(name))
        contextual=set()
        for key,model in models.items():
            if any(isinstance(v,str) and (v.endswith('/roof_1') or v.endswith('/spirelamp_0010')) for v in model.get('textures',{}).values()):contextual.add(key)
        while True:
            added={key for key,model in models.items() if model.get('parent') in contextual}-contextual
            if not added:break
            contextual.update(added)
        sources=set()
        for name in base.namelist():
            if not(name.startswith('bloodborne_dw/composite/') and name.endswith('.json')):continue
            spec=json.loads(base.read(name))
            for variant in spec.get('variants',[]):
                for pose in variant.get('poses',{}).values():
                    if any(part.get('model') in contextual for part in pose.get('parts',[])):sources.add(spec['id'])
        context={'models':sorted(contextual),'sources':sorted(sources)}
        path='assets/bloodborne_dw/visuals/contextual-masonry.json'
        data=(json.dumps(context,separators=(',',':'))+'\n').encode()
        (RESOURCE/path).write_bytes(data)
        receipt.append({'profile':'shared','path':path,'reason':'all source and static families with contextual shutter/0010 wall masonry','sha256':hashlib.sha256(data).hexdigest(),'modelCount':len(contextual),'sourceCount':len(sources)})
    output=ROOT/'validation/facade-v7-assets.json'
    output.write_text(json.dumps({'schema':1,'resources':receipt},indent=2)+'\n',encoding='utf-8')
    return receipt

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.parse_args()
    print(json.dumps({'generated':len(generate())}))
