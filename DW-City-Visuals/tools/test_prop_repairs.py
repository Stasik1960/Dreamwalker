"""Verify the four reported props against the actual combined production archive."""
from pathlib import Path
import argparse, json, zipfile, hashlib, math, io
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
def png_path(sprite):
    return 'assets/'+sprite.replace(':','/textures/')+'.png'

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--jar',type=Path,required=True)
    ap.add_argument('--report',type=Path,required=True)
    args=ap.parse_args()
    contract=json.loads((ROOT/'tools/prop-repair-contract.json').read_text())
    rules=json.loads((ROOT/'tools/family-baked-palettes.json').read_text())['sprites']
    checks=[]
    def check(name,ok,detail=''):
        checks.append(dict(name=name,status='PASS' if ok else 'FAIL',detail=detail))
        if not ok: raise AssertionError(name+': '+str(detail))
    with zipfile.ZipFile(ROOT/'baseline/mod.jar') as base, zipfile.ZipFile(args.jar) as jar, \
         zipfile.ZipFile(ROOT/'tools/v12-palette-inputs.zip') as inputs:
        for key,spec in contract.items():
            sprite=spec['sprite'];path=png_path(sprite)
            rule=next(r for r in rules if r['sprite']==sprite)
            original_base=Image.open(io.BytesIO(base.read(path))).convert('RGBA')
            for mode in ('original','alt'):
                canonical=mode+'/'+path
                raw_source=inputs.read(canonical) if canonical in inputs.namelist() else base.read(path)
                source=Image.open(io.BytesIO(raw_source)).convert('RGBA')
                active=rule.get(mode) is not None or any(r.get(mode) is not None for r in rule.get('regions',[]))
                target='dw_city_visuals/'+mode+'/'+path
                if not active:
                    # The profile resource index may mirror an unchanged atlas into
                    # both packs. Verify the effective resource rather than absence.
                    effective=jar.read(target) if target in jar.namelist() else jar.read(path)
                    check(key+'/'+mode+' protected',effective==base.read(path) and jar.read(path)==base.read(path),
                          dict(profileOverridePresent=target in jar.namelist(),effectiveSha256=hashlib.sha256(effective).hexdigest()))
                    continue
                out=Image.open(io.BytesIO(jar.read(target))).convert('RGBA')
                check(key+'/'+mode+' dimensions',out.size==source.size)
                changed=protected=alpha=0
                for y in range(out.height):
                    for x in range(out.width):
                        before=source.getpixel((x,y));after=out.getpixel((x,y));tone=rule.get(mode)
                        for r in rule.get('regions',[]):
                            b=r['pixels']
                            if b[0]<=x<b[2] and b[1]<=y<b[3]: tone=r.get(mode);break
                        expected=before
                        if tone is not None and before[3]:
                            values=before[:3]
                            if tone.get('luminance'):
                                l=(77*values[0]+150*values[1]+29*values[2]+128)>>8;values=(l,l,l)
                            expected=tuple(max(0,min(255,math.floor(values[c]*tone['scale'][c]+tone['offset'][c]+.5))) for c in range(3))+(before[3],)
                        if after!=expected: raise AssertionError(f'{key}/{mode} pixel {x},{y}: expected{expected} got{after}')
                        changed+=after!=original_base.getpixel((x,y));alpha+=after[3]!=before[3];protected+=tone is None and after!=before
                check(key+'/'+mode+' changed',changed>0,dict(changedPixels=changed))
                check(key+'/'+mode+' alpha and protected pixels',alpha==0 and protected==0)
                if key=='roof' and mode=='alt':
                    reference_path='dw_city_visuals/alt/'+png_path(spec['referenceSprite'])
                    reference_bytes=jar.read(reference_path)
                    reference=Image.open(io.BytesIO(reference_bytes)).convert('RGBA')
                    check('roof/connected cornice reference bytes preserved',hashlib.sha256(reference_bytes).hexdigest()==spec['referenceAltSha256'])
                    mismatches=outside=0
                    for y in range(out.height):
                        for x in range(out.width):
                            inside=any(b[0]<=x<b[2] and b[1]<=y<b[3] for b in spec['uvRegions'])
                            if inside:mismatches+=out.getpixel((x,y))!=reference.getpixel((x,y))
                            else:outside+=out.getpixel((x,y))!=original_base.getpixel((x,y))
                    check('roof/exact connected stone-cornice pixels',mismatches==0,dict(mismatchedTexels=mismatches))
                    check('roof/unrelated atlas pixels unchanged',outside==0,dict(outsideMismatches=outside))
                    reference_model=json.loads(jar.read('assets/'+spec['referenceModel'].replace(':','/models/')+'.json'))
                    reference_composite=json.loads(jar.read('bloodborne_dw/composite/'+spec['referenceSource'].split(':')[1]+'.json'))
                    check('roof/actual connected corner model binding',any(p['model']==spec['referenceModel'] for p in reference_composite['variants'][spec['referenceVariant']]['poses']['closed']['parts']))
                    check('roof/actual connected corner atlas binding',spec['referenceSprite'] in reference_model['textures'].values())
                if key=='friezeCorner':
                    before=Image.open(io.BytesIO(inputs.read(path) if mode=='alt' else base.read(path))).convert('RGBA')
                    errors=0
                    for y in range(out.height):
                        for x in range(out.width):
                            if any(b[0]<=x<b[2] and b[1]<=y<b[3] for b in spec['uvRegions']):continue
                            expected=before.getpixel((x,y))
                            if mode=='alt' and expected[3]:
                                expected=tuple(max(0,min(255,math.floor(expected[c]*[.96,1,1.02][c]+[1,1,0][c]+.5))) for c in range(3))+(expected[3],)
                            errors+=out.getpixel((x,y))!=expected
                    check(key+'/'+mode+' prior outside-mask pixels exact',errors==0,dict(mismatches=errors))
        # Geometry/gameplay must not silently change while fixing appearance.
        for path in ['assets/bloodborne_rp/geo/door_1.geo.json',
                     'assets/bloodborne_rp/animations/door.animation.json',
                     'bloodborne_dw/composite/source_addon_coral_dead_horn_coral_wall_fan.json',
                     'bloodborne_dw/composite/prototype_roof.json',
                     'assets/bloodborne_dw/native_grid/catalog.json',
                     'assets/bloodborne_dw/native_grid/meshes.bin']:
            check('preserved/'+path,jar.read(path)==base.read(path))
        # Bind each reported block to its actual source model and sampled atlas
        # footprint; a new texture sitting unused in the JAR must not pass.
        for key,model_path,composite_path,variant in [
            ('shrine','remaining/source/minecraft/block/addon/coral/dead_horn_coral_wall_fan',
             'source_addon_coral_dead_horn_coral_wall_fan',0),
            ('frieze','remaining/source/minecraft/block/quartz_stairs_top','source_quartz_stairs_top',1),
            ('friezeCorner','remaining/source/minecraft/block/quartz_stairs_outer_1','source_quartz_stairs_outer_1',0),
            ('roof','base/source/minecraft/block/stairs/jungle_stairs_roof','prototype_roof',0)]:
            model=json.loads(jar.read('assets/bloodborne_dw/models/'+model_path+'.json'))
            composite=json.loads(jar.read('bloodborne_dw/composite/'+composite_path+'.json'))
            parts=composite['variants'][variant]['poses']['closed']['parts']
            check(key+'/source model bound',any(p['model']=='bloodborne_dw:'+model_path for p in parts))
            check(key+'/atlas bound',contract[key]['sprite'] in model.get('textures',{}).values())
            boxes=contract[key]['uvRegions'];sampled=set()
            for element in model['elements']:
                for face in element.get('faces',{}).values():
                    a,b,c,d=face['uv']
                    sampled.update((x,y) for x in range(math.floor(min(a,c)*8),math.ceil(max(a,c)*8))
                                   for y in range(math.floor(min(b,d)*8),math.ceil(max(b,d)*8)))
            covered={(x,y) for box in boxes for x in range(box[0],box[2]) for y in range(box[1],box[3])}
            check(key+'/exact sampled UV coverage',sampled==covered,dict(sampledTexels=len(sampled),maskTexels=len(covered)))
        cat=json.loads(jar.read('assets/bloodborne_rp/catalog.json'))
        check('door uses identified atlas',cat['door_1']['texture']=='textures/entities/decorations/door_1.png')
        door=Image.open(io.BytesIO(jar.read('dw_city_visuals/alt/assets/bloodborne_rp/textures/entities/decorations/door_1.png'))).convert('RGBA')
        similar=Image.open(io.BytesIO(jar.read('dw_city_visuals/alt/assets/bloodborne_rp/textures/entities/decorations/door.png'))).convert('RGBA')
        check('door1 matches reviewed similar door exactly',door.tobytes()==similar.tobytes())
    report=dict(status='PASS',checks=checks,identities=contract,
                limitation='F3 air at the shrine is explained by the imported displaced resident/selection. Map and gameplay were preserved; the overlay does not claim to change debug targeting.')
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    print(f'PASS: {len(checks)} prop/atlas/gameplay-preservation checks')

if __name__=='__main__': main()
