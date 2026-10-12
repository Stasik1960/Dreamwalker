import json
from pathlib import Path
from v127_apply_full_map_poses import write
R=Path(__file__).resolve().parents[1];A=R/'src/architecture/resources';d=json.loads((A/'bloodborne_dw/imported-compact-v127.json').read_text(encoding='utf-8-sig'));f=json.loads((A/'bloodborne_dw/imported-stair-types.json').read_text(encoding='utf-8-sig'));functional={r['path'] for k in ['stairTypes','ladderTypes'] for r in f[k]};rows=[]
for row in d['types']:
    path=row['registryId'].split(':')[1];s=json.loads((A/'bloodborne_dw/composite'/f'{path}.json').read_text(encoding='utf-8-sig'))
    assert path not in functional and not s.get('openable') and not s.get('support',{}).get('required') and not s.get('importedClimbingBounds'),path
    for pose in row['poses']:
        assert not s.get('openable'),path
        for yaw in [0,2,4,6]:
            for kind in ['collision','aim']:
                for b in row['shapes'][str(pose)][yaw][kind]:assert all(-1e-7<=b['from'][i]<=b['to'][i]<=16+1e-7 for i in range(3)),(path,pose,kind)
    rows.append({'registryId':row['registryId'],'poseCount':len(row['poses']),'status':'PASS_NONFUNCTIONAL_STATIONARY_EXACT_SINGLE_CELL_PHYSICS'})
write(R/'docs/v12.7/conversion/compact-functional-exclusions.json',{'status':'PASS_EXACT_FUNCTIONAL_FACTORY_PRIORITY','types':len(rows),'poses':sum(r['poseCount'] for r in rows),'functionalPathsExcluded':sorted(functional),'rows':rows,'openableSupportClimbExcluded':True})
print(json.dumps({'status':'PASS','types':len(rows),'poses':sum(r['poseCount'] for r in rows)}))
