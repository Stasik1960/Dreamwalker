"""Check non-JSON vanilla fluid/biome visuals against the immutable source pack."""
import json,zipfile,hashlib,io
from pathlib import Path
import numpy as np
from PIL import Image
from v125_map_index import ROOT,MAP,PACK,sha,token
from v127_apply_full_map_poses import write
def main():
    paths=['textures/block/water_still.png','textures/block/water_flow.png','textures/block/lava_still.png','textures/block/lava_flow.png','textures/block/water_overlay.png','textures/colormap/grass.png','textures/colormap/foliage.png'];rows=[]
    with zipfile.ZipFile(PACK) as source,zipfile.ZipFile(Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar') as vanilla:
        names=source.namelist()
        for path in paths:
            key='assets/minecraft/'+path;matches=[n for n in names if n.endswith(key)]
            if not matches:rows.append({'resource':key,'sourceOverrides':False,'decision':'UNCHANGED_VANILLA_SPECIAL_RENDER_SOURCE'});continue
            assert len(matches)==1;data=source.read(matches[0]);reference=vanilla.read(key);a=np.asarray(Image.open(io.BytesIO(data)).convert('RGBA'));b=np.asarray(Image.open(io.BytesIO(reference)).convert('RGBA'));equal=a.shape==b.shape and np.array_equal(a,b)
            row={'resource':key,'sourceOverrides':True,'sourcePixelDimensions':list(a.shape),'vanillaPixelDimensions':list(b.shape),'pixelsEqual':equal,'sourceSha256':hashlib.sha256(data).hexdigest(),'vanillaSha256':hashlib.sha256(reference).hexdigest(),'decision':'UNCHANGED_VANILLA_SPECIAL_RENDER_SOURCE' if equal else 'REPORT_SPECIAL_RENDER_AUTHOR_OVERRIDE'}
            if '/colormap/' in key:
                colours=np.unique(a.reshape(-1,4),axis=0);row['uniqueSourceRgbaColours']=len(colours);row['sourceRgbaExamples']=colours[:12].tolist();temperature=float(np.float32(.8));humidity=float(np.float32(.4))*temperature;x=int((1-temperature)*255);y=int((1-humidity)*255);row['plainsClimateIndex']=[x,y];row['sourcePlainsRgba']=a[y,x].tolist();row['vanillaPlainsRgba']=b[y,x].tolist()
            if key.endswith('/colormap/grass.png'):
                copied=ROOT/'src/architecture/resources/assets/bloodborne_dw/textures/remaining/colormap/grass.png';row['scopedImportedPaletteSha256']=sha(copied);assert row['scopedImportedPaletteSha256']==row['sourceSha256'];row['decision']='SCOPED_NEW_SOURCE_GRASS_PALETTE_PRESERVED';row['providerSource']='src/architecture/java/dev/dreamwalker/bloodbornedw/composite/RemainingSourceTint.java';row['runtimeProviderValidation']='Separate real client provider fixture; this audit proves immutable source/copy pixel identity only'
            rows.append(row)
    fixture=json.loads((ROOT/'src/gametest/resources/bloodborne_dw/v127-source-lighting-decisions.json').read_text(encoding='utf-8-sig'));fluid=[{'caseId':r['caseId'],'sourceState':r['sourceState'],'targetState':r['targetState'],'action':r['action'],'occurrences':r['occurrences']} for r in fixture['cases'] if r['sourceState'].startswith(('minecraft:water[','minecraft:lava['))]
    result={'status':'PASS_SPECIAL_RENDER_SOURCE_ASSETS_PRESERVED' if all(r['decision'] in {'UNCHANGED_VANILLA_SPECIAL_RENDER_SOURCE','SCOPED_NEW_SOURCE_GRASS_PALETTE_PRESERVED'} for r in rows) else 'REVIEW_KNOWN_SPECIAL_RENDER_OVERRIDE','sourceHashes':{'map':sha(MAP),'pack':sha(PACK)},'resources':rows,'actualFluidBlockSourceCases':fluid,'originalWorldWritten':False};write(ROOT/'docs/v12.7/conversion/special-render-source-audit.json',result);print(token(result))
if __name__=='__main__':main()
