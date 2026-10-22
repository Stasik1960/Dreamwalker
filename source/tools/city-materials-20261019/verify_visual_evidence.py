"""Check exact visible geometry/material identity and only the proved light delta."""
import json
from pathlib import Path
from PIL import Image

OUT=Path(__file__).absolute().parents[3]/'research/city-materials-20261019'


def read(case):return json.loads((OUT/case/'client-capture-output.json').read_text())
def visible(probe):return next(h for h in probe['visibleTriangleProbe']['nearestIntersections'] if not h.get('transparentAtBaseLevel'))
def identity_value(hit,key):
    value=hit.get(key)
    # The separate enhanced probe adds carrier diagnostics to source rows.
    # Compare the same source identity fields emitted by the frozen base probe.
    if key=='staticSources' and value is not None:
        return [{k:row[k] for k in ['pose','sourceState','models']} for row in value]
    return value


def main():
    cases={case:read(case) for case in ['compare-base','compare-final2','reopen-final2']}
    frames={case:next(f for f in cap['frames'] if f['key']=='facade') for case,cap in cases.items()}
    base=frames['compare-base'];candidate=frames['compare-final2'];reopen=frames['reopen-final2']
    for frame in [candidate,reopen]:
        for key in ['actualCamera','dayTime','ambientOcclusion','gamma','rainGradient','thunderGradient']:
            assert frame[key]==base[key],key
    images={case:Image.open(OUT/case/'screenshots'/frame['fileName']).convert('RGB') for case,frame in frames.items()}
    results=[]
    for i,old_probe in enumerate(base['pixelRayProbes']):
        old=visible(old_probe);new=visible(candidate['pixelRayProbes'][i]);again=visible(reopen['pixelRayProbes'][i])
        for other in [new,again]:
            for key in ['root','state','models','staticSources','sprite','spriteLocalUv','nearestTextureTexels','geometricNormal']:
                assert identity_value(old,key)==identity_value(other,key),(i,key)
            for key in ['face','shade','tintIndex','hasTint','brightness','rendererColor','provenOpaqueUsedUvAndMips']:
                assert old['quad'][key]==other['quad'][key],(i,key)
            assert len(old['actualRenderedVertices'])==len(other['actualRenderedVertices'])
            for a,b in zip(old['actualRenderedVertices'],other['actualRenderedVertices']):
                assert all(x==y for n,(x,y) in enumerate(zip(a,b)) if n!=9),'Geometry/UV/colour/normal changed'
        expected=15<<20 if i<2 else 13<<20
        assert new['quad']['packedLights']==again['quad']['packedLights']==[expected]*4
        assert old['quad']['packedLights']==([0]*4 if i<2 else [expected]*4)
        pixel=tuple(old_probe['pixel'])
        colors={case:list(image.getpixel(pixel)) for case,image in images.items()}
        if i<2:assert sum(colors['compare-final2'])>sum(colors['compare-base'])*2
        else:assert colors['compare-final2']==colors['compare-base'],'Control surface colour changed'
        assert colors['reopen-final2']==colors['compare-final2'],'Reopened corrected surface differs'
        results.append({'root':old['root'],'state':old['state'],'sprite':old['sprite'],
            'pixel':list(pixel),'nativeScreenRGB':colors,'rgbaAndUvIdentical':True,
            'geometryTintShadeAndOpacityProofIdentical':True,
            'packedLightsBefore':old['quad']['packedLights'],'packedLightsAfterAndReopen':new['quad']['packedLights'],
            'provenOpaqueUsedUvAndMips':new['quad']['provenOpaqueUsedUvAndMips']})
    result={'status':'PASS_TWO_LIGHT_CORRECTIONS_AND_UNCHANGED_DARK_CUTOUT_CONTROL_AFTER_REOPEN',
        'method':'Actual shipped-renderer visible triangles and native screenshots; all vertex fields except light identical.',
        'cases':results,'mapRelightRequired':False,'textureOrModelEdits':False}
    (OUT/'visual-verification.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))


if __name__=='__main__':main()
