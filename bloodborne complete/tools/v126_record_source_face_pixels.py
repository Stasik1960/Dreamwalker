"""Observed native source-face PNGs close the earlier one-sided view limits."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
INDEX=ROOT/'build/v126-native-pixel-index-3/index.json'
CAPTURE=ROOT/'build/runtime-client-v126-flatgallery-native-source-face-3/client-capture-output.json'
MANIFEST=ROOT/'build/runtime-server-v126-flatgallery-author-2/catalogue-gallery-output.json'
NOTES={
0:'93160CLOSED: широкая орнаментированная деревянная поверхность видна целиком, верхняя вытянутая панель и нижняя короткая секция имеют текстуры. Ранее закрытая модель была показана ребром; этот исходный лицевой ракурс подтверждает её широкую поверхность.',
1:'90458OPEN: широкое деревянное лицо повёрнутого полотна видно целиком от платформы до арочной верхушки, с вертикальным рисунком древесины. Каменное обрамление с этого восточного ракурса показано ребром; его фронтальная форма была видна в основных42/43.',
2:'90923pose1OPEN: широкое деревянное лицо нижнего полотна и его арочная верхушка видны целиком. Верхнее каменное завершение присутствует в прямом монтаже; из этого ракурса его часть видна узким боком, фронтальная форма подтверждена основными46/47.',
3:'90923pose2OPEN: широкое деревянное лицо нижнего полотна с арочной верхушкой видно целиком. Верхнее каменное завершение находится в монтаже в глубину; неподвижная рама показана преимущественно ребром, её фронтальная форма подтверждена основными48/49.'}
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    index,capture,manifest=map(read,[INDEX,CAPTURE,MANIFEST])
    assert index['captureSha256']==sha(CAPTURE)
    assert index['productionJarSha256']==capture['productionJarSha256']==manifest['productionJarSha256']
    reviews={r['key']:r['components'][0] for r in manifest['reviewPlatforms']}
    byname={r['fileName']:r for r in capture['frames']};records=[]
    for entry in index['frames']:
        n=entry['index'];assert n in NOTES and sha(Path(entry['path']))==entry['sha256']
        raw=byname[entry['fileName']];assert raw['status']=='PASS' and raw['renderedCameraVerified'] and raw['sha256']==entry['sha256']
        expected=reviews[entry['key'].removeprefix('SOURCE-FACE-')]
        assert len(raw['actualRootChecks'])==1;actual=raw['actualRootChecks'][0]
        assert actual['status']=='PASS' and actual['root']==expected['root']
        assert actual['actualUuid']==expected['uuid'] and actual['actualRegistry']==expected['registry']
        assert actual['actualProperties']==expected['placedState']
        assert actual['actualProperties']['open']==('false' if n==0 else 'true')
        records.append({**entry,'status':'PASS_OBSERVED_PIXELS','observation':NOTES[n],
            'fullResolutionSourcePngActuallyObserved':True,'wideLeafUvFaceActuallyVisible':True,
            'actualTypedTarget':{'root':actual['root'],'uuid':actual['actualUuid'],'registryId':actual['actualRegistry'],'properties':actual['actualProperties'],'rootCheckStatus':'PASS'},
            'missingTextureFallbackObserved':False,'confirmedVisualGeometryDefect':False,
            'visibilityScope':'Wide wooden leaf face; edge-on static frame is assessed in earlier front-facing captures.'})
    assert len(records)==4 and {r['index'] for r in records}==set(range(4))
    out={'schema':'dreamwalker-v126-native-source-face-pixel-review','status':'PASS_ASSIGNED_PIXELS',
         'assignedIndices':[0,3],'observedFrames':4,'productionJarSha256':index['productionJarSha256'],
         'captureSha256':index['captureSha256'],'pixelIndexSha256':sha(INDEX),
         'confirmedVisualGeometryDefects':[],'wideLeafFaceVisibilityResolvedFor':['93160CLOSED','90458OPEN','90923pose1OPEN','90923pose2OPEN'],
         'previousBacksideLimitationsResolvedByAppropriateSourceFrontCamera':True,
         'sourceSidednessPolicy':'The earlier backside invisibility is preserved; no invented back-face artwork or changed production resource is used for these captures.',
         'frames':records,'worldOrJavaOrProductionOrImagesModified':False}
    target=ROOT/'docs/v12.6/native/review-source-face-000-003.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps({'status':out['status'],'observedFrames':4,'confirmedVisualGeometryDefects':0,'wideLeafFaceVisibilityResolvedFor':out['wideLeafFaceVisibilityResolvedFor']}))
if __name__=='__main__':main()
