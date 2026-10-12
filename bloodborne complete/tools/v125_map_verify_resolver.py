"""Readonly actual-source versus canonical placed vertex/anchor verification.

Geometry proof only, not native rendering/gameplay. Bind every source owner to
the real source apps at XYZ and check source normalization/dedup yaw/shift.
"""
from __future__ import annotations
import collections,gzip,json
import numpy as np
from pathlib import Path
from v124_visual_audit import Visuals,rot
from v125_map_door_projections import SourceVisuals
from v125_map_index import ROOT,MAP,PACK,BASELINE,dump,sha,token
from v125_map_resolve import rotation
DEST=ROOT/'docs/v12.5-city-review/citycandidate'

def signature(quads):
    return collections.Counter(tuple(sorted(tuple(round(float(x),6) for x in p) for p in q[0])) for q in quads)
def main():
    data=json.loads(gzip.decompress((DEST/'city-source-data.json.gz').read_bytes()));source=SourceVisuals();native=Visuals(vanilla_jar=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    legacy={r['key']:r for r in json.loads((ROOT/'src/architecture/resources/bloodborne_dw/imported-stair-legacy-art.json').read_text())['states']};specs={};checks=[];cache={}
    for row in data['owners']:
        key=token({k:row[k] for k in ['registryId','properties','payload','sourceApplications']})
        if row.get('sourceComponentApplications'):key+=token(row['sourceComponentApplications'])
        if key in cache:result=cache[key]
        else:
            srcquads=[]
            sources=row.get('sourceComponentApplications',[row])
            for component in sources:
                delta=[(component['sourcePosition'][i]-row['sourcePosition'][i])*16 for i in range(3)]
                for a in component['sourceApplications']:
                    part={'model':a['model'],'pitch':a.get('x',0),'yaw':a.get('y',0),'offset':delta}
                    srcquads.extend(source.quads(part))
            reg=row['registryId'];path=reg.split(':',1)[1]
            if reg not in specs:specs[reg]=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/composite'/f'{path}.json').read_text())
            spec=specs[reg];props=row['properties'];vi=int(props['variant'])+int(props.get('art_bank','0'))*16+(10 if props.get('trim')=='true' else 0);parts=spec['variants'][vi]['poses']['open' if props.get('open')=='true' and 'open' in spec['variants'][vi]['poses'] else 'closed']['parts']
            selector=row['payload'].get('ImportedStairLegacyArt')
            if selector:
                art=legacy[selector];assert art['registryId']==reg and art['variant']==int(props['variant']);parts=art['parts']
            dst=[];angle=-int(props['rotation'])*45;shift=rotation(row['sourceShift'],int(props['rotation']))
            for p in parts:
                if p.get('emissive'):continue
                for q in native.quads(p):
                    dst.append((rot(q[0],1,angle,[8,8,8])+np.array(shift)*16,*q[1:]))
            a,b=signature(srcquads),signature(dst)
            result={'status':'PASS_EXACT_VERTEX_GEOMETRY' if a==b else 'FAIL_VERTEX_GEOMETRY','sourceQuads':sum(a.values()),'canonicalQuads':sum(b.values()),'missingSourceQuads':sum((a-b).values()),'extraCanonicalQuads':sum((b-a).values()),'sourceBounds':[float(v) for v in np.concatenate((np.asarray([q[0] for q in srcquads]).reshape(-1,3).min(0),np.asarray([q[0] for q in srcquads]).reshape(-1,3).max(0)))/16],'canonicalBounds':[float(v) for v in np.concatenate((np.asarray([q[0] for q in dst]).reshape(-1,3).min(0),np.asarray([q[0] for q in dst]).reshape(-1,3).max(0)))/16]};cache[key]=result
        checks.append({'sourcePosition':row['sourcePosition'],'registryId':row['registryId'],'mainId':row['mainId'],'properties':row['properties'],'payload':row['payload'],'sourceModels':[a['model'] for a in row['sourceApplications']],**result})
    failures=[r for r in checks if r['status'].startswith('FAIL')]
    dump(DEST/'source-canonical-anchor-proof.json',{'schemaVersion':1,'status':'PASS_GEOMETRY_ONLY' if not failures else 'FAIL_GEOMETRY_ONLY','baselineCommit':BASELINE,'mapSha256':sha(MAP),'packSha256':sha(PACK),'cityDataSha256':sha(DEST/'city-source-data.json.gz'),'ownersChecked':len(checks),'distinctTypedSourcePosesChecked':len(cache),'exactGeometryOwners':len(checks)-len(failures),'failures':failures,'checks':checks,'testScope':'Source original FaceBakery-style transformed quad vertices versus canonical native-model vertices including legacy selector/yaw/SourceShift. Not texture, visibility, physical behavior, native client or unresolved source cells.'})

if __name__=='__main__':
    try:main()
    except BaseException:
        import traceback
        (DEST/'source-anchor-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
