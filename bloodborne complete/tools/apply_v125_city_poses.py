"""Append exact source conditional unions; preserve every pre-existing pose."""
import copy, hashlib, json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources/bloodborne_dw'
DOC=ROOT/'docs/v12.5-city-review/citycandidate'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,d):p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    proposal=read(DOC/'extra-canonical-city-poses.json')
    assert proposal['newOneBlockPoseCount']==94 and not proposal['capExceededGroups'] and not proposal['missingResourcePaths']
    catalogue=read(RES/'remaining-catalogue.json');objects={r['path']:r for r in catalogue['objects']}
    aliases=read(RES/'imported-type-aliases.json');groups={r['primaryRegistryId']:r for r in aliases['groups']}
    evidence=[]
    for group in proposal['groups']:
        registry=group['primaryRegistryId'];path=registry.split(':')[1];file=RES/'composite'/f'{path}.json'
        spec=read(file);before=copy.deepcopy(spec);oldcount=group['existingPoseCount']
        assert sha(file)==group['existingDescriptorSha256'] and len(spec['variants'])==oldcount
        assert spec['simplePhysicalCollision']
        assert registry not in groups or groups[registry]['kind']=='artwork'
        if registry not in groups and group['proposedTotalPoseCount']>16:
            added={'primaryRegistryId':registry,'primaryId':group['primaryId'],'members':[registry],
                   'kind':'artwork','oneItem':True,'poseCount':oldcount,
                   'reason':'V12.5 append-only exact one-source-cell conditional art bank, no merged registrations'}
            aliases['groups'].append(added);groups[registry]=added
        for pose in group['poses']:
            index=pose['proposedLogicalPoseIndex'];assert index==len(spec['variants'])
            parts=copy.deepcopy(pose['proposedParts']);bounds=pose['sourceRawVisibleBoundsUnits'];offset=pose['floorNormalizationOffsetUnits']
            visible={'from':[bounds[i]+offset[i] for i in range(3)],'to':[bounds[i+3]+offset[i] for i in range(3)]}
            physical=bool(before['variants'][0]['poses']['closed']['collision'])
            closed={'parts':parts,'collision':[copy.deepcopy(visible)] if physical else [],'selection':[copy.deepcopy(visible)]}
            variant={'poses':{'closed':closed,'open':copy.deepcopy(closed)},'visibleBounds':visible,
                     'sourceVariant':f'Сочетание исходной ячейки {index+1}',
                     'sourcePlacementOffset':offset,'localOrigin':offset,
                     'sourceClosedApplications':pose['canonicalSourceApplications'],'sourceOpenApplications':[],
                     'sourceStateRefs':pose['sourcePropertyStates'],'v125SourceSingleCell':True}
            spec['variants'].append(variant)
            objects[path]['variants'].append({'variant':index,'name':objects[path]['displayName']+f' · сочетание {index+1}',
                                            'sourceModels':[a['model'] for a in pose['canonicalSourceApplications']]})
        assert spec['variants'][:oldcount]==before['variants']
        assert {k:v for k,v in spec.items() if k!='variants'}=={k:v for k,v in before.items() if k!='variants'}
        if registry in groups:groups[registry]['poseCount']=len(spec['variants'])
        write(file,spec)
        evidence.append({'registryId':registry,'primaryId':group['primaryId'],'beforePoses':oldcount,'afterPoses':len(spec['variants']),
                         'appendedPoses':group['newPoseCount'],'beforeDescriptorSha256':group['existingDescriptorSha256'],
                         'afterDescriptorSha256':sha(file),'oldPosesAndMetadataEqual':True,'existingModelDependenciesOnly':True})
    write(RES/'remaining-catalogue.json',catalogue);write(RES/'imported-type-aliases.json',aliases)
    write(DOC/'applied-city-poses.json',{'status':'PASS_APPEND_ONLY_REQUIRES_NATIVE_VALIDATION','groups':evidence,
          'appendedPoses':sum(r['appendedPoses'] for r in evidence),'newRegistryIds':0,'originalV12Edits':0,
          'noMultiBlockConstructionMerge':True,'productionAuthorCollisions':'NOT_SUPPLIED_NOT_APPLIED'})
    print(json.dumps({'status':'PASS','groups':len(evidence),'poses':94,'newRegistryIds':0}))

if __name__=='__main__':main()
