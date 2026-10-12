"""Read-only native/source water sprite, animation and tint override audit."""
import hashlib,io,json,zipfile
import numpy as np
from PIL import Image
from v127_visual_export import ROOT,VANILLA,write_json
from v125_map_index import PACK,PACK_SHA
def sha(b):return hashlib.sha256(b).hexdigest()
def main():
    with zipfile.ZipFile(PACK) as source,zipfile.ZipFile(VANILLA) as native:
        prefix=next(p[:-len('pack.mcmeta')] for p in source.namelist() if p.endswith('pack.mcmeta'));names=set(source.namelist());rows=[]
        for name in ('water_still','water_flow','water_overlay'):
            path='assets/minecraft/textures/block/'+name+'.png';vanilla=native.read(path);original=source.read(prefix+path) if prefix+path in names else vanilla
            a=np.asarray(Image.open(io.BytesIO(original)).convert('RGBA'));b=np.asarray(Image.open(io.BytesIO(vanilla)).convert('RGBA'));meta=path+'.mcmeta'
            vm=json.loads(native.read(meta)) if meta in native.namelist() else None;sm=json.loads(source.read(prefix+meta)) if prefix+meta in names else vm
            rows.append({'sprite':name,'sourceSpriteOverride':prefix+path in names,'sourceAnimationOverride':prefix+meta in names,'sourceRgbaSha256':sha(a.tobytes()),'nativeRgbaSha256':sha(b.tobytes()),'rgbaShape':list(a.shape),'rgbaEqual':np.array_equal(a,b),'animationEqual':sm==vm,'sourceAnimation':sm,'nativeAnimation':vm})
        properties=[p for p in names if p.lower().endswith('color.properties')];contents={p:source.read(p).decode('utf-8-sig') for p in properties};water_files=[p for p in names if 'water' in p.lower() and not p.endswith('/')]
        water_color_directives=[{'file':p,'line':line} for p,text in contents.items() for line in text.splitlines() if line.strip() and not line.strip().startswith('#') and any(k in line.lower() for k in ('water','palette.block','fog','sky'))]
        colormaps=[p for p in names if '/colormap/' in p.lower() and not p.endswith('/')]
    assert all(r['rgbaEqual'] and r['animationEqual'] for r in rows);assert not water_files and not water_color_directives
    result={'status':'PASS_SOURCE_WATER_SPRITES_ANIMATION_AND_TINT_USE_NATIVE_DEFAULTS','sourcePackSha256':PACK_SHA,'sprites':rows,'waterNamedFilesInSourcePack':water_files,'sourceColorProperties':contents,'waterOrPaletteColorDirectives':water_color_directives,'sourceColormaps':colormaps,
        'scope':'The actual source pack defines no water sprite/animation/OptiFine water tint override. Its sole color.properties directive is lilypad. Water in source and converted worlds therefore uses the same Minecraft1.20.1 native sprite pixels/animation and biome water color when original biomes remain unchanged. FluidState native parity is separately tested; this is not a shader or client-frame claim. Ordinary vanilla water handlers/textures remain unmodified.','worldOrPackModified':False}
    write_json(ROOT/'docs/v12.7/conversion/source-water-visual-proof.json',result);print(json.dumps(result,ensure_ascii=False))
if __name__=='__main__':main()
