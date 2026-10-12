"""Route full-map appended source unions to historical canonical types only."""
import collections,copy,json,math,subprocess
from pathlib import Path
from v125_map_index import ROOT,dump,sha
from v127_apply_full_map_poses import write

RES=ROOT/'src/architecture/resources';OUT=ROOT/'docs/v12.7/conversion';BASE='0c985dc28e4a38900cce3055ddb5658a71ed95e5'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def original(relative):return subprocess.check_output(['git','show',BASE+':bloodborne complete/'+relative],cwd=ROOT)
def main():
    evidence=read(OUT/'appended-poses-proof.json');aliasfile=RES/'bloodborne_dw/imported-type-aliases.json';raw=original('src/architecture/resources/bloodborne_dw/imported-type-aliases.json');aliases=json.loads(raw.decode('utf8'))
    canonical={member:g['primaryRegistryId'] for g in aliases['groups'] for member in g['members']}
    follow=read(RES/'bloodborne_dw/imported-type-aliases-v127.json');canonical.update({member:g['primaryRegistryId'] for g in follow['groups'] for member in g['members']})
    originalcounts={r['registryId']:r['beforePoses'] for r in evidence['groups']}
    specs={};sourceSuffixes={};routes=[]
    for g in evidence['groups']:
        reg=g['registryId'];file=RES/'bloodborne_dw/composite'/f'{reg.split(":")[1]}.json';spec=read(file);assert len(spec['variants'])==g['afterPoses'];specs[reg]=spec;sourceSuffixes[reg]=copy.deepcopy(spec['variants'][g['beforePoses']:])
    for old,suffix in sourceSuffixes.items():
        next=canonical.get(old,old)
        if next!=old:
            if next not in specs:specs[next]=read(RES/'bloodborne_dw/composite'/f'{next.split(":")[1]}.json')
            originalcounts.setdefault(next,len(specs[next]['variants']))
            specs[old]['variants']=specs[old]['variants'][:originalcounts[old]]
            start=len(specs[next]['variants']);specs[next]['variants'].extend(suffix)
            routes.append({'oldRegistryId':old,'oldAppendedStart':originalcounts[old],'oldAppendedEnd':originalcounts[old]+len(suffix)-1,'canonicalRegistryId':next,'newStart':start,'newEnd':start+len(suffix)-1,'sameSourceMeshUVAndShift':True})
    catalogfile=RES/'bloodborne_dw/remaining-catalogue.json';catalog=read(catalogfile);objects={r['path']:r for r in catalog['objects']}
    collisionRows=[];groups=[]
    for reg,spec in specs.items():
        count=originalcounts[reg];file=RES/'bloodborne_dw/composite'/f'{reg.split(":")[1]}.json'
        write(file,spec)
        row=objects[reg.split(':')[1]];row['variants']=row['variants'][:count]
        for index,variant in enumerate(spec['variants'][count:],count):
            row['variants'].append({'variant':index,'name':spec['displayName']+f' · соединение {index+1}','sourceModels':[a['model'] for a in variant['sourceClosedApplications']]})
            pose=variant['poses']['closed'];rotations=[]
            for yaw,boxes in enumerate(pose['simpleCollisionByRotation']):
                if boxes:aim=copy.deepcopy(boxes)
                else:
                    bound=variant['visibleBounds'];a=math.radians(yaw*45);c,s=math.cos(a),math.sin(a);points=[(8+c*(x-8)-s*(z-8),8+s*(x-8)+c*(z-8)) for x in [bound['from'][0],bound['to'][0]] for z in [bound['from'][2],bound['to'][2]]]
                    aim=[{'from':[min(p[0] for p in points),bound['from'][1],min(p[1] for p in points)],'to':[max(p[0] for p in points),bound['to'][1],max(p[1] for p in points)]}]
                assert len(boxes)<=1 and len(aim)<=1
                rotations.append({'collision':boxes,'aim':aim})
            collisionRows.append({'registryId':reg,'variant':index,'rotations':rotations,'sourceVariant':variant['sourceVariant']})
        if len(spec['variants'])>16:groups.append({'registryId':reg,'poses':len(spec['variants']),'bankMax':math.ceil(len(spec['variants'])/16)-1})
    aliasfile.write_bytes(raw);assert aliasfile.read_bytes()==raw
    write(catalogfile,catalog)
    write(RES/'bloodborne_dw/imported-full-map-v127.json',{'schemaVersion':1,'unitsPerBlock':16,'groups':groups,'objects':collisionRows,'policy':'Only exact appended NEW source poses. Sparse per-canonical-pose collision lookup; oldV125/V126resources andhistorical1281aliasesbyteexact. NoauthorCollisionStudioexportapplied.'})
    dump(OUT/'canonical-append-routing.json',{'status':'PASS_CANONICAL_ONLY_APPEND_NO_ALIAS_PROMOTION','historicalAliasesSha256':sha(aliasfile),'groups':groups,'routes':routes,'newPoseCount':len(collisionRows),'canonicalTypeCount':len({r['registryId'] for r in collisionRows}),'oldAliasSpecSuffixesRemoved':True,'sourceMapConverted':False})
    print(json.dumps({'status':'PASS','poses':len(collisionRows),'canonicalTypes':len({r['registryId'] for r in collisionRows}),'banks':groups}))
if __name__=='__main__':main()
