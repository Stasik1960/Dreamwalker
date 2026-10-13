"""Prepare native candidate comparisons, normalizing pose and preserving mapped target art."""
from pathlib import Path
import json,math,traceback
import numpy as np
from v124_visual_audit import Visuals, pose_data, rot
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/v12.4-dedup'
def load(p):return json.loads((OUT/p).read_text(encoding='utf-8'))
def rotate(v,angle):
    c,s=math.cos(math.radians(angle)),math.sin(math.radians(angle))
    return [c*v[0]+s*v[2],v[1],-s*v[0]+c*v[2]]
def main():
    manifest=load('type-state-mapping.json');mapping={(m['oldRegistryId'],m['oldVariant']):m for m in manifest['mappings']}
    candidates=load('retained-near-unique.json')
    near={(p['a']['registryId'],p['a']['variant'],p['b']['registryId'],p['b']['variant']):p for p in load('near-visual-candidates.json')['pairs']}
    frozen=load('baseline-content.json')['descriptors'];visuals=Visuals(vanilla_jar=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
    points={}
    def vertices(row):
        key=(row['registryId'],row['variant'])
        if key not in points:
            spec=frozen[row['registryId'].split(':')[1]]
            points[key]=np.asarray([q[0] for q in pose_data(visuals,spec,spec['variants'][row['variant']])]).reshape(-1,3)
        return points[key]
    stair=load('stair-review/functional-type-fragment.json')
    lookup={r:g['primaryId'] for g in stair['groups'] if g['kind']=='stairs' for r in g['members']}
    nearest={}
    for p in load('full-visual-coverage.json')['matches']:
        a,b=lookup.get(p['a']['registryId']),lookup.get(p['b']['registryId'])
        if not a or not b or a==b:continue
        key=tuple(sorted([a,b]))
        if key not in nearest or p['difference']<nearest[key]['difference']:nearest[key]=p
    candidates.extend(nearest.values())
    fixtures=[]
    for index,pair in enumerate(candidates):
        a,b=pair['a'],pair['b']
        source_pair=near.get((a['registryId'],a['variant'],b['registryId'],b['variant']))
        # Hash-minimizing canonical yaw is not a visual alignment for different
        # artwork. Use the actual best eight-view offset; otherwise a panel may
        # wrongly be compared face-on against its edge.
        if source_pair:delta=int(round(source_pair['yawOffset']/45))%8
        else:delta=int(round((a['canonicalYaw']-b['canonicalYaw']+pair.get('canonicalViewOffset',0))/45))%8
        # Target B is aligned to old A in source coordinates, then its approved
        # old-B→new-B state migration is composed with that alignment.
        low_a=vertices(a).min(axis=0)
        low_b=rot(vertices(b),1,-delta*45,[8,8,8]).min(axis=0)
        shiftworld=((low_a-low_b)/16).tolist()
        shiftlocal=rotate(shiftworld,delta*45)
        m=mapping.get((b['registryId'],b['variant']))
        if m:
            shifted=rotate(shiftlocal,m['rotationDelta']*45)
            shiftlocal=[v+w for v,w in zip(shifted,m['sourceShift'])]
            target=m['newRegistryId'];targetid=m['newId'];v=m['newVariant'];properties=m['properties'];delta=(delta+m['rotationDelta'])%8
        else:target=b['registryId'];targetid=b['id'];v=b['variant'];properties={}
        fixtures.append({'kind':'RETAINED_REVIEW','reviewIndex':index,'groupId':'review-'+a['id']+'-'+b['id'],
          'oldId':a['id'],'oldRegistryId':a['registryId'],'oldVariant':a['variant'],
          'mainId':targetid,'mainRegistryId':target,'variant':v,'yawDeltaSteps':delta,
          'sourceShiftDelta':[0. if abs(n)<1e-12 else n for n in shiftlocal],'properties':properties,
          'sourceTargetId':b['id'],'sourceTargetRegistryId':b['registryId'],'sourceTargetVariant':b['variant'],
          'sourceSoftwareDiagnostic':pair.get('diagnosticImage'),'sameNormalizedPlacement':True,
          'normalizationMethod':'Best actual eight-view relative yaw; align source geometry lower bounds; compose target state mapping'})
    result={'schemaVersion':1,'status':'PENDING_NATIVE_RETAINED_VISUAL_REVIEW','manifestShaPendingRebindOnBuild':True,
         'nonStairPairs':len(candidates)-len(nearest),'nativeStairMaterialPairs':len(nearest),'fixtures':fixtures}
    (OUT/'additional-native-review-fixtures.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except Exception:
        (OUT/'additional-native-review-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
