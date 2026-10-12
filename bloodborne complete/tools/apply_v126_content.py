"""Apply proven append-only city poses and approved complete assembly poses."""
import copy, hashlib, json, shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources/bloodborne_dw'
DOC=ROOT/'docs/v12.6'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    cat=read(RES/'remaining-catalogue.json');objects={r['path']:r for r in cat['objects']}
    aliases=read(RES/'imported-type-aliases.json');groups={r['primaryRegistryId']:r for r in aliases['groups']}
    original_mappings=copy.deepcopy(aliases['mappings']);evidence=[]
    proposal=read(ROOT/'docs/v12.6-city-review/map/citycandidate/extra-canonical-city-poses-04.json')
    for group in proposal['groups']:
        registry=group['primaryRegistryId'];path=registry.split(':')[1];file=RES/'composite'/f'{path}.json'
        spec=read(file);before=copy.deepcopy(spec);oldcount=group['existingPoseCount']
        assert sha(file)==group['existingDescriptorSha256'] and len(spec['variants'])==oldcount
        assert spec['simplePhysicalCollision'] and path.startswith('source_')
        if registry not in groups and group['proposedTotalPoseCount']>16:
            added={'primaryRegistryId':registry,'primaryId':group['primaryId'],'members':[registry],
                   'kind':'artwork','oneItem':True,'poseCount':oldcount,'reason':'V12.6 append-only exact one-source-cell conditional art bank'}
            aliases['groups'].append(added);groups[registry]=added
        for pose in group['poses']:
            index=pose['proposedLogicalPoseIndex'];assert index==len(spec['variants'])
            parts=copy.deepcopy(pose['proposedParts']);bounds=pose['sourceRawVisibleBoundsUnits'];offset=pose['floorNormalizationOffsetUnits']
            visible={'from':[bounds[i]+offset[i] for i in range(3)],'to':[bounds[i+3]+offset[i] for i in range(3)]}
            physical=bool(before['variants'][0]['poses']['closed']['collision'])
            closed={'parts':parts,'collision':[copy.deepcopy(visible)] if physical else [],'selection':[copy.deepcopy(visible)]}
            spec['variants'].append({'poses':{'closed':closed,'open':copy.deepcopy(closed)},'visibleBounds':visible,
                'sourceVariant':f'Сочетание исходной ячейки {index+1}','sourcePlacementOffset':offset,'localOrigin':offset,
                'sourceClosedApplications':pose['canonicalSourceApplications'],'sourceOpenApplications':[],
                'sourceStateRefs':pose['sourcePropertyStates'],'v126SourceSingleCell':True})
            objects[path]['variants'].append({'variant':index,'name':objects[path]['displayName']+f' · сочетание {index+1}',
                'sourceModels':[a['model'] for a in pose['canonicalSourceApplications']]})
        assert spec['variants'][:oldcount]==before['variants']
        if registry in groups:groups[registry]['poseCount']=len(spec['variants'])
        write(file,spec);evidence.append({'registryId':registry,'kind':'source-single-cell','before':oldcount,'after':len(spec['variants']),'beforeSha':group['existingDescriptorSha256'],'afterSha':sha(file)})
    manifest=read(ROOT/'build/v126-assembly-plan/assembly-manifest.json')
    for group in manifest['assemblies']:
        path=group['primaryRegistryId'].split(':')[1];file=RES/'composite'/f'{path}.json'
        assert sha(file)==group['frozenPrimaryDescriptorSha256']
        staged=ROOT/group['stagedDescriptor'];assert sha(staged)==group['stagedDescriptorSha256']
        spec=read(staged);oldcount=group['installationPose'];assert spec['variants'][:oldcount]==read(file)['variants']
        objects[path]['displayName']=group['displayName']
        objects[path]['variants'].append({'variant':oldcount,'name':group['displayName'],'sourceModels':list(dict.fromkeys(p['model'] for p in spec['variants'][-1]['poses']['closed']['parts']))})
        shutil.copyfile(staged,file)
        if group['primaryRegistryId'] in groups:groups[group['primaryRegistryId']]['poseCount']=len(spec['variants'])
        evidence.append({'registryId':group['primaryRegistryId'],'kind':'approved-complete-assembly','before':oldcount,'after':len(spec['variants']),'beforeSha':group['frozenPrimaryDescriptorSha256'],'afterSha':sha(file)})
    assert aliases['mappings']==original_mappings
    write(RES/'remaining-catalogue.json',cat);write(RES/'imported-type-aliases.json',aliases)
    java=ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw'
    for relative in ['architecture/RemainingArchitecture.java','composite/CompositeSpec.java']:
        p=java/relative;s=p.read_text(encoding='utf8');s=s.replace('isBankedPath(path)?128:', 'isBankedPath(path)?dev.dreamwalker.bloodbornedw.composite.ImportedArtworkBlock.variantBudget(path):').replace('isBankedPath(id.getPath())?128:', 'isBankedPath(id.getPath())?ImportedArtworkBlock.variantBudget(id.getPath()):');p.write_text(s,encoding='utf8',newline='\n')
    p=java/'composite/ImportedArtworkStates.java';s=p.read_text(encoding='utf8').replace('changed.with(ImportedArtworkBlock.ART_BANK,next/16)','changed.with(ImportedArtworkBlock.bankProperty(changed),next/16)');p.write_text(s,encoding='utf8',newline='\n')
    p=ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/RemainingTransferGameTests.java';s=p.read_text(encoding='utf8').replace('state.with(ImportedArtworkBlock.ART_BANK,pose/16)','state.with(ImportedArtworkBlock.bankProperty(state),pose/16)');p.write_text(s,encoding='utf8',newline='\n')
    p=ROOT/'src/gametest/resources/fabric.mod.json';d=read(p);d['entrypoints']['fabric-gametest'].append('dev.dreamwalker.bloodbornedw.gametest.LadderV126GameTests');write(p,d)
    p=ROOT/'gradle.properties';s=p.read_text(encoding='utf8').replace('0.1.0-v12.5-city-review','0.1.0-v12.6-functional-assemblies');p.write_text(s,encoding='utf8',newline='\n')
    write(DOC/'applied-content.json',{'status':'PASS_APPEND_ONLY_NATIVE_NOT_RUN','baseline':'c52a06c9fa20007a1c06c8fc654b3c1b86f72791','groups':evidence,'oldMappingsUnchanged':True,'newRegistryIds':0,'originalV12Edits':0,'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED'})
    print(json.dumps({'status':'PASS','groups':len(evidence),'posesAdded':sum(r['after']-r['before'] for r in evidence)}))
if __name__=='__main__':main()
