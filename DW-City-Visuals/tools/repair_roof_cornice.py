"""Prepare exact same-UV ALT roof-fin pixels from its connected stone corner.

User correction9: prototype_roof is a cornice fin. Match the connected stone
corner rather than the blue roof tiles. This preparation is idempotent; normal
builds consume its committed immutable input and family bake rule.
"""
from pathlib import Path
import hashlib,io,json,zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
TARGET='bloodborne_dw:base/source/minecraft/block/addon/spirelamp_0026'
REFERENCE='bloodborne_dw:block/remaining/source/minecraft/block/addon/spirelamp_0026'
def sprite_path(sprite):return 'assets/'+sprite.replace(':','/textures/')+'.png'
def prepare():
    contract_path=ROOT/'tools/prop-repair-contract.json';family_path=ROOT/'tools/family-baked-palettes.json'
    inputs_path=ROOT/'tools/v12-palette-inputs.zip'
    contract=json.loads(contract_path.read_text());family=json.loads(family_path.read_text())
    boxes=contract['roof']['uvRegions'];target=sprite_path(TARGET);reference=sprite_path(REFERENCE)
    reference_path=ROOT/'src/main/resources/dw_city_visuals/alt'/reference
    reference_bytes=reference_path.read_bytes();ref=Image.open(io.BytesIO(reference_bytes)).convert('RGBA')
    with zipfile.ZipFile(ROOT/'baseline/mod.jar') as base:
        original_bytes=base.read(target)
        assert original_bytes==base.read(reference),'The source and connected-corner atlas mapping has changed'
        original=Image.open(io.BytesIO(original_bytes)).convert('RGBA')
    assert original.size==ref.size==(128,128)
    assert original.getchannel('A').tobytes()==ref.getchannel('A').tobytes(),'Connected-corner reference changed alpha'
    hybrid=original.copy()
    for box in boxes:hybrid.paste(ref.crop(box),box)
    output=io.BytesIO();hybrid.save(output,format='PNG')
    with zipfile.ZipFile(inputs_path) as archive:inputs={name:archive.read(name) for name in archive.namelist()}
    inputs['alt/'+target]=output.getvalue()
    packed=io.BytesIO()
    with zipfile.ZipFile(packed,'w',compression=zipfile.ZIP_DEFLATED) as archive:
        for name,data in sorted(inputs.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,data)
    inputs_path.write_bytes(packed.getvalue())
    identity={'luminance':False,'scale':[1,1,1],'offset':[0,0,0]}
    rule=next(rule for rule in family['sprites'] if rule['sprite']==TARGET)
    rule.clear();rule.update(sprite=TARGET,sourceSprite=TARGET,regions=[{'pixels':box,'alt':identity} for box in boxes])
    roof=contract['roof'];roof.pop('altTone',None)
    roof.update(altAppearance='EXACT_CONNECTED_STONE_CORNICE_SAME_UV',
                referenceSprite=REFERENCE,
                referenceModel='bloodborne_dw:remaining/source/minecraft/block/stairs/jungle_stairs_roof_corner',
                referenceSource='bloodborne_dw:source_stairs_jungle_stairs_inner_left_top',
                referenceVariant=2,referenceRoot=[296,64,-983],referenceRotation=6,
                referenceAltSha256=hashlib.sha256(reference_bytes).hexdigest(),
                altOutsideMask='Exact original baseline pixels',
                userCorrection='Match the connected upper stone corner cornice; blue slate roof palette is explicitly superseded.',
                original='unchanged')
    for path,data in [(contract_path,contract),(family_path,family)]:path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
    print('Roof-fin ALT uses exact connected stone-corner pixels inside authored UV union; Original and outside pixels preserved.')
if __name__=='__main__':prepare()
