"""Record only actually observed supplemental native frames and owner checks."""
import hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
INDEX=ROOT/'build/v126-native-pixel-index-2/index.json'
CAPTURE=ROOT/'build/runtime-client-v126-flatgallery-native-supplement-2/client-capture-output.json'
MANIFEST=ROOT/'build/runtime-server-v126-flatgallery-author-2/catalogue-gallery-output.json'
NOTES={
1:'Назначенная открытая90031 находится дальше закрытого соседа слева: видны свободный арочный проём и обе повёрнутые деревянные створки с белыми металлическими деталями. Каменная арка не повернулась.',
2:'Правый бок90031: проём открыт, створки расположены поперёк прежней дверной плоскости; в паре с предыдущим видом видны обе створки, без подмены закрытым соседом.',
3:'93160OPEN показана узким ребром слева; широкая панель правее относится к отдельному соседу. Широкая поверхность назначенной открытой93160 уже наблюдалась в основном кадре37.',
4:'Назначенные большие ворота90266 стоят дальше закрытого соседа слева: две центральные кованые створки раскрыты, наружные панели и верхние орнаменты остались на месте.',
5:'Правый бок90266: видны обе открытые кованые створки с решёткой и свободный проход между неподвижными наружными секциями.',
6:'Назначенная93152 дальше закрытой копии слева: обе нижние деревянные створки раскрыты, верхняя фрамуга и каменное оформление сохраняются.',
7:'Правый бок93152: поверхности двух нижних раскрытых створок видны по обе стороны прохода; неподвижное верхнее оформление не стало створкой.',
8:'Назначенная90458 с обратной стороны почти не видна, кроме тонкого вертикального контура; соседняя закрытая копия также не показывает фронтальный каменный рисунок с этой стороны. Этот ракурс не подтверждает всю широкую поверхность открытого полотна.',
9:'Назначенная93153 дальше закрытого соседа слева: обе нижние деревянные створки открыты, верхняя декоративная панель и боковые стойки сохраняются.',
10:'Правый бок93153: обе раскрытые створки видны под прямым углом к статическому деревянному обрамлению; закрытая соседняя копия находится отдельно дальше.',
11:'Назначенная90923pose1 с обратной стороны почти не видна, кроме тонкого контура; фронтальная поверхность открытого полотна этим кадром не подтверждается. Основной47 показывает открытую нижнюю арку и сохранённую верхнюю.',
12:'Назначенная90923pose2 с обратной стороны почти не видна, кроме тонкого контура; полное UV-лицо этим кадром не подтверждается. Основной49 показывает свободную нижнюю арку и верхнее завершение в монтаже в глубину.'}
LIMITS={3:'Ребро панели; широкое лицо проверено основным37.',8:'Backside/тонкий контур: полная широкая поверхность и фронтальная рамка в этом ракурсе не видны.',
11:'Backside/тонкий контур: полное UV-лицо створки в этом ракурсе не видно.',12:'Backside/тонкий контур: полное UV-лицо створки в этом ракурсе не видно.'}
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    index,capture,manifest=map(read,[INDEX,CAPTURE,MANIFEST]);assert index['captureSha256']==sha(CAPTURE)
    assert index['productionJarSha256']==capture['productionJarSha256']==manifest['productionJarSha256']
    actual_by_name={r['fileName']:r for r in capture['frames']}
    components={r['key']:r['components'][0] for r in manifest['reviewPlatforms']}
    records=[]
    for entry in index['frames']:
        n=entry['index']
        if n not in NOTES:continue
        assert sha(Path(entry['path']))==entry['sha256']
        actual=actual_by_name[entry['fileName']]
        assert actual['status']=='PASS' and actual['sha256']==entry['sha256'] and actual['renderedCameraVerified']
        key=entry['key'].removeprefix('SUPPLEMENT-RIGHT-SIDE-').removeprefix('SUPPLEMENT-')
        expected=components[key];checks=actual['actualRootChecks'];assert len(checks)==1
        check=checks[0];assert check['status']=='PASS' and check['root']==expected['root']
        assert check['actualUuid']==expected['uuid'] and check['actualRegistry']==expected['registry']
        assert check['actualProperties']['open']=='true' and check['actualProperties']==expected['placedState']
        record={**entry,'status':'PASS_OBSERVED_PIXELS','observation':NOTES[n],
                'targetOpenOwner':{'root':check['root'],'uuid':check['actualUuid'],'registryId':check['actualRegistry'],'actualState':check['actualProperties'],'rootCheckStatus':'PASS'},
                'sourcePngActuallyObserved':True,'neighborClosedCopiesNotUsedAsTarget':True,
                'confirmedModelDefect':False,'missingTextureFallbackObserved':False}
        if n in LIMITS:record['visibilityLimitations']=LIMITS[n]
        records.append(record)
    assert len(records)==12 and {r['index'] for r in records}==set(range(1,13))
    first=read(ROOT/'build/v126-native-pixel-index-1/index.json');main16=next(r for r in first['frames'] if r['index']==16)
    assert main16['key']=='bloodborne_dw:source_addon_gate2_1' and sha(Path(main16['path']))==main16['sha256']
    out={'schema':'dreamwalker-v126-native-supplement-pixel-review','status':'PASS_ASSIGNED_PIXELS',
         'assignedIndices':[1,12],'observedFrames':12,'productionJarSha256':index['productionJarSha256'],
         'captureSha256':index['captureSha256'],'pixelIndexSha256':sha(INDEX),
         'reviewScope':'Actual supplemental PNGs plus exact live OPEN owner UUID/root/state; visible double leaves confirmed across paired viewpoints. Limited backside views are not full-face evidence.',
         'confirmedModelDefects':[],'doubleLeafVisibilityAcrossPairedViews':{'90031':[1,2],'90266':[4,5],'93152':[6,7],'93153':[9,10]},
         'fullOpenLeafFaceVisibilityStillNotClaimedFor':['90458','90923pose1','90923pose2'],
         'static90143EarlierEdgeViewResolvedByActuallyObservedMainFrame':{**main16,'status':'PASS_OBSERVED_PIXELS',
            'captureSha256':first['captureSha256'],'observation':'В основнойnativePNG16 видна полная кованая декоративная панель90143: вертикальная решётка, нижняя рамка и наклонный орнамент сверху; это source_addon_gate2_1, не соседнее окно93066.'},
         'frames':records,'nativeInteractionEvidence':'Separate native GameTests; pixel review itself issues no world operations',
         'worldOrProductionOrImagesModified':False}
    target=ROOT/'docs/v12.6/native/review-supplement-001-012.json';target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps({'status':out['status'],'observedFrames':12,'confirmedModelDefects':0,'fullOpenLeafFaceVisibilityStillNotClaimedFor':out['fullOpenLeafFaceVisibilityStillNotClaimedFor']}))
if __name__=='__main__':main()
