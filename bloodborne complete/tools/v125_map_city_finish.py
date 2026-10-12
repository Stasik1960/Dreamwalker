"""Attach exact whole-pose and invisible-service evidence to the source clip.

No new production types, models or original blocks are written. Unresolved
visible multipart / originalV12 poses remain concrete handoff rows.
"""
from __future__ import annotations
import collections,gzip,json
from v125_map_index import ROOT,MAP,PACK,dump,sha,token
from v125_map_resolve import Resolver,rotation,source_uuid
DEST=ROOT/'docs/v12.5-city-review/citycandidate'

def main():
    file=DEST/'city-source-data.json.gz';data=json.loads(gzip.decompress(file.read_bytes()));resolver=Resolver();origin=data['sourceOrigin']
    rows={tuple(r['sourcePosition']):r for r in data['sourceApplications']};unresolved={tuple(r['sourcePosition']):r for r in data['unresolvedSourceCells']};consumed=set();wholes=[]
    for pos,row in sorted(unresolved.items()):
        if pos in consumed:continue
        for c in resolver.whole:
            if not c['applications'] or c['open']:continue
            first=[a for a in c['applications'] if not any(a.get('offset',[0,0,0]))]
            yaw=resolver.match(first,row['sourceApplications'])
            if yaw is None:continue
            expected=collections.defaultdict(list)
            for a in c['applications']:
                off=rotation([v/16 for v in a.get('offset',[0,0,0])],yaw)
                if any(abs(v-round(v))>1e-8 for v in off):break
                worldpos=tuple(pos[i]+round(off[i]) for i in range(3));app={k:v for k,v in a.items() if k!='offset'};app['y']=(app.get('y',0)+yaw*45)%360;expected[worldpos].append(app)
            else:
                if all(p in unresolved and p not in consumed and resolver.match(expected[p],rows[p]['sourceApplications'])==0 for p in expected):
                    target=resolver.target(c,yaw);owner={**row,**target,'instanceUuid':source_uuid('minecraft:overworld',pos,'whole'),'sourceComponentPositions':[list(p) for p in expected],'sourceComponentApplications':[rows[p] for p in expected],'resolution':'EXACT_MULTI_CELL_SOURCE_WHOLE_THEN_V124_ALIASES'};wholes.append(owner);consumed.update(expected);break
    helper=json.loads((ROOT/'docs/remaining-transfer/transparent-helper-validation.json').read_text());assert helper['status']=='PASS' and helper['packSha256']==sha(PACK)
    transparent={r['model'] for r in helper['models'] if r['status']=='ALL_VISIBLE_FACE_PIXELS_ALPHA_ZERO'}
    inventory=json.loads((ROOT/'docs/remaining-transfer/source-inventory.json').read_text(encoding='utf-8-sig'));empty={r['model'] for r in inventory['models'] if not any(e.get('faces') for e in r['resolved'].get('elements',[]))}
    service=[];still=[]
    for pos,row in unresolved.items():
        if pos in consumed:continue
        names={a['model'] for a in row['sourceApplications']}
        if names and names<=transparent|empty:
            service.append({**row,'resolution':'SOURCE_ALL_ALPHA_ZERO_INVISIBLE_SERVICE' if names&transparent else 'SOURCE_EMPTY_GEOMETRY_SERVICE','visibleReplacementPolicy':'Do not substitute standard visible vanilla glass/pane/ladder. Source carrier is retained as raw evidence, not offered finished artwork. Native test conversion needs an explicit invisible technical representation or declared omitted service; no density skip.','sourceTransparencyProof':'docs/remaining-transfer/transparent-helper-validation.json','sourceTransparencyProofSha256':sha(ROOT/'docs/remaining-transfer/transparent-helper-validation.json')})
        else:still.append(row)
    data['owners'].extend(wholes);data['wholeSourceOwners']=wholes;data['sourceInvisibleServices']=service;data['unresolvedSourceCells']=still;data['status']='READONLY_SOURCE_EXPORT_WITH_EXPLICIT_VISIBLE_UNRESOLVED_AND_INVISIBLE_SERVICES'
    with file.open('wb') as f:
        with gzip.GzipFile(fileobj=f,mode='wb',mtime=0) as out:out.write(json.dumps(data,ensure_ascii=False,separators=(',',':')).encode())
    classes=collections.Counter(r['sourceCarrier'] for r in still)
    dump(DEST/'city-source-data-summary.json',{'status':data['status'],'candidateId':data['candidateId'],'sourceBoundsInclusive':data['rawBlocks']['boundsInclusive'],'owners':len(data['owners']),'exactWholeOwners':len(wholes),'wholeConsumedSourceCells':len(consumed),'invisibleServiceCells':len(service),'unresolvedVisibleSourceCells':len(still),'unresolvedCarriers':dict(classes),'passThroughBlocks':len(data['passThroughBlocks']),'rawPaletteStates':len(data['rawBlocks']['palette']),'rawDenseCells':len(data['rawBlocks']['denseIndices']),'dataFile':str(file),'dataSha256':sha(file),'dataBytes':file.stat().st_size,'mapSha256After':sha(MAP),'packSha256After':sha(PACK)})
    dump(DEST/'visible-unresolved-evidence.json',{'status':'EXPLICIT_SOURCE_POSES_REQUIRE_CONVERSION_SUPPORT','sourceBoundsInclusive':data['rawBlocks']['boundsInclusive'],'rows':still,'invisibleServiceCount':len(service),'wholeOwners':wholes,'policy':'Every occupied raw source cell retained. This scanner does not register a new fallback block or claim unrepresented poses were natively rendered.'})

if __name__=='__main__':main()
