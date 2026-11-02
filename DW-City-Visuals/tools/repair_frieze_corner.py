"""Prepare the outer frieze's immutable masked source and bake rule.

The old ALT source has a brighter frieze than the original atlas. Restore only
the sampled corner-frieze source texels before applying the same tone as the
straight frieze. Outside this UV union retain the old frozen source and palette.
This preparation is idempotent; normal builds consume its committed ZIP/rules.
"""
from pathlib import Path
import zipfile,json,math,io
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
SPRITE='bloodborne_dw:block/remaining/source/minecraft/block/addon/spirelamp_0015'
PATH='assets/'+SPRITE.replace(':','/textures/')+'.png'
MODEL='remaining/source/minecraft/block/quartz_stairs_outer_1'
def prepare():
    family_path=ROOT/'tools/family-baked-palettes.json'
    v12_path=ROOT/'tools/v12-baked-palettes.json'
    inputs_path=ROOT/'tools/v12-palette-inputs.zip'
    contract_path=ROOT/'tools/prop-repair-contract.json'
    family=json.loads(family_path.read_text());v12=json.loads(v12_path.read_text())
    with zipfile.ZipFile(ROOT/'baseline/mod.jar') as base:
        model=json.loads(base.read('assets/bloodborne_dw/models/'+MODEL+'.json'))
        source=Image.open(io.BytesIO(base.read(PATH))).convert('RGBA')
    boxes=[]
    for element in model['elements']:
        for face in element.get('faces',{}).values():
            a,b,c,d=face['uv'];box=[math.floor(min(a,c)*8),math.floor(min(b,d)*8),math.ceil(max(a,c)*8),math.ceil(max(b,d)*8)]
            if box[0]<box[2] and box[1]<box[3] and box not in boxes:boxes.append(box)
    with zipfile.ZipFile(inputs_path) as archive:
        inputs={n:archive.read(n) for n in archive.namelist()}
    canonical=Image.open(io.BytesIO(inputs[PATH])).convert('RGBA')
    assert canonical.size==source.size
    for box in boxes:canonical.paste(source.crop(box),box)
    buf=io.BytesIO();canonical.save(buf,format='PNG');inputs['alt/'+PATH]=buf.getvalue()
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED) as archive:
        for name,data in sorted(inputs.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,data)
    inputs_path.write_bytes(output.getvalue())
    old=next((r for r in v12['sprites'] if r['sprite']==SPRITE),None)
    grey=v12['palettes']['grey_stone']
    if old is not None:assert old=={'sprite':SPRITE,'palette':'grey_stone'}
    v12['sprites']=[r for r in v12['sprites'] if r['sprite']!=SPRITE]
    straight=next(r for r in family['sprites'] if r['sprite'].endswith('/spirelamp_0012'))
    tones={mode:straight['regions'][0][mode] for mode in ['original','alt']}
    rule={'sprite':SPRITE,'sourceSprite':SPRITE,'alt':grey,
          'regions':[{'pixels':b,**tones} for b in sorted(boxes)]}
    family['sprites']=[r for r in family['sprites'] if r['sprite']!=SPRITE]+[rule]
    contract=json.loads(contract_path.read_text())
    contract['friezeCorner']={'source':'bloodborne_dw:source_quartz_stairs_outer_1',
                            'staticCarrier':'bloodborne_dw:static_architecture_1[part=63]',
                            'position':[282,46,-849],'sprite':SPRITE,'uvRegions':sorted(boxes),
                            'originalTone':tones['original'],'altTone':tones['alt'],
                            'altOutsideMask':'Exact prior grey_stone transform of unprefixed canonical input',
                            'originalOutsideMask':'Exact original baseline pixels'}
    for path,data in [(family_path,family),(v12_path,v12),(contract_path,contract)]:
        path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
    print('Outer frieze prepared; unrelated atlas texels retain their previous profile palette.')
if __name__=='__main__':prepare()
