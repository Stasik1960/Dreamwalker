"""Prove the residual basalt case and preserve every material/geometry field."""
import json
from pathlib import Path
from PIL import Image

OUT=Path(__file__).absolute().parents[3]/'research/city-materials-20261019'
def read(case):return json.loads((OUT/case/'client-capture-output.json').read_text())
def visible(probe):return next(h for h in probe['visibleTriangleProbe']['nearestIntersections'] if not h.get('transparentAtBaseLevel'))

def main():
    old_case='diagnose-trim-flat2';new_case='reopen-final2'
    old_cap,new_cap=read(old_case),read(new_case)
    old_frame,new_frame=old_cap['frames'][0],new_cap['frames'][0]
    for key in ['actualCamera','dayTime','ambientOcclusion','gamma','rainGradient','thunderGradient']:
        assert old_frame[key]==new_frame[key],key
    images={case:Image.open(OUT/case/'screenshots'/frame['fileName']).convert('RGB') for case,frame in [(old_case,old_frame),(new_case,new_frame)]}
    rows=[]
    for index in [4,6,7]:
        old_probe,new_probe=old_frame['pixelRayProbes'][index],new_frame['pixelRayProbes'][index]
        assert old_probe['pixel']==new_probe['pixel']
        assert old_probe['visibleTriangleProbe']['rendererFailures']==new_probe['visibleTriangleProbe']['rendererFailures']==0
        old,new=visible(old_probe),visible(new_probe)
        for key in ['root','state','staticSources','sprite','spriteLocalUv','nearestTextureTexels','geometricNormal','actualRootOpacity','physicalOutwardCenterLight','physicalOutwardVertexLight']:
            assert old[key]==new[key],(index,key)
        for key in ['face','shade','tintIndex','hasTint','brightness','rendererColor','provenOpaqueUsedUvAndMips','sourceUnitOriginCell']:
            assert old['quad'][key]==new['quad'][key],(index,key)
        assert old['quad']['packedLights']==[0]*4
        assert new['quad']['packedLights']==[15<<20]*4
        assert old['actualRootOpacity']==15 and old['physicalOutwardCenterLight']['sky']==0
        assert all(p['sky']==15 and p['block']==0 for p in old['physicalOutwardVertexLight'])
        for a,b in zip(old['actualRenderedVertices'],new['actualRenderedVertices']):
            assert all(x==y for n,(x,y) in enumerate(zip(a,b)) if n!=9),'Only light may change'
        pixel=tuple(old_probe['pixel']);colors={case:list(image.getpixel(pixel)) for case,image in images.items()}
        assert sum(colors[new_case])>sum(colors[old_case])*2
        rows.append({'root':old['root'],'source':'source_basalt / basalt / spirelamp_0014',
            'pixel':list(pixel),'sourceRGBA':old['nearestTextureTexels'][0]['rgba'],
            'nativeScreenRGB':colors,'oldPackedLights':old['quad']['packedLights'],'newPackedLights':new['quad']['packedLights'],
            'centroidRemainsInsideOpacity15Root':True,'fourExteriorSamplesSky15':True,'onlyLightChanged':True})
    result={'status':'PASS_REVIEWED_BASALT_EXTERIOR_CORNER_LIGHT_AFTER_REOPEN','productionJarSha256':new_cap['productionJarSha256'],
        'cases':rows,'mapRelightRequired':False,'newBlockOrModelOrTexture':False}
    (OUT/'trim-verification.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))

if __name__=='__main__':main()
