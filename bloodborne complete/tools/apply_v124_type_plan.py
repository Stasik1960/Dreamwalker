"""Apply reviewed NEW-only family/state unions; old registrations and numbers remain reserved."""
from pathlib import Path
import copy,json,os,time
ROOT=Path(__file__).resolve().parents[1];DOC=ROOT/'docs/v12.4-dedup';RES=ROOT/'src/architecture/resources/bloodborne_dw'
def write(p,data):
    content=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode('utf8')
    if p.exists() and p.read_bytes()==content:return
    temp=p.with_suffix('.v124.tmp');temp.write_bytes(content)
    for attempt in range(5):
        try:os.replace(temp,p);return
        except OSError:
            if attempt==4:raise
            time.sleep(.2)
def main():
    base=json.loads((DOC/'baseline-content.json').read_text(encoding='utf8'))
    plan=json.loads((DOC/'general-functional-groups.json').read_text(encoding='utf8'))
    remaining=copy.deepcopy(base['remainingCatalogue']);catalogue=copy.deepcopy(base['debugCatalogue'])
    bypath={r['path']:r for r in remaining['objects']};byid={r['registryId']:r for r in catalogue['entries']}
    groups=[];mappings=[];retired=set()
    previousfile=RES/'imported-type-aliases.json'
    currentprimaries={group['mainRegistryId'] for group in plan['groups']}
    if previousfile.exists():
        previous=json.loads(previousfile.read_text(encoding='utf8'))
        for group in previous.get('groups',[]):
            primary=group['primaryRegistryId']
            if group['kind']=='artwork' and primary not in currentprimaries:
                path=primary.split(':')[1]
                assert path.startswith('source_') and path in base['descriptors']
                write(RES/'composite'/f'{path}.json',base['descriptors'][path])
    for group in plan['groups']:
        primary=group['mainRegistryId'];path=primary.split(':')[1];assert path.startswith('source_')
        descriptor=copy.deepcopy(base['descriptors'][path]);descriptor['variants']=[];names=[]
        canonicalname=group.get('canonicalName')
        if group.get('renderLayerOverride'):descriptor['layer']=group['renderLayerOverride']
        if canonicalname:
            descriptor['displayName']=canonicalname;bypath[path]['displayName']=canonicalname;byid[primary]['name']=canonicalname
        for rep in group['representatives']:
            sourcepath=rep['registryId'].split(':')[1];old=rep['oldVariant'];assert sourcepath.startswith('source_')
            descriptor['variants'].append(copy.deepcopy(base['descriptors'][sourcepath]['variants'][old]))
            name=copy.deepcopy(bypath[sourcepath]['variants'][old]);name['variant']=len(names);names.append(name)
            if canonicalname:name['name']=canonicalname+(' · вид '+str(len(names)) if group['newVariantCount']>1 else '')
        assert len(descriptor['variants'])==group['newVariantCount']
        write(RES/'composite'/f'{path}.json',descriptor);bypath[path]['variants']=names
        metadata={'primaryId':group['mainId'],'primaryRegistryId':primary,'kind':'artwork','oneItem':True,'members':[r['registryId'] for r in group['members']],'poseCount':len(names)}
        if canonicalname:metadata['displayName']=canonicalname
        groups.append(metadata)
        for member in group['members']:
            if member['registryId']!=primary:
                retired.add(member['registryId']);byid[member['registryId']]['canonicalRegistryId']=primary
        for raw in group['mappings']:
            v=raw['variant'];props={'art_bank':str(v//16)} if len(names)>16 else {}
            mappings.append({'oldId':raw['oldId'],'oldRegistryId':raw['oldRegistryId'],'oldVariant':raw['oldVariant'],'newId':group['mainId'],'newRegistryId':primary,'newVariant':v%16,'rotationDelta':raw['yawDeltaSteps'],'sourceShift':raw['sourceShiftDelta'],'properties':props})
    stairfile=DOC/'stair-review/functional-type-fragment.json'
    if stairfile.exists():
        stair=json.loads(stairfile.read_text(encoding='utf8'))
        groups.extend(stair['groups']);mappings.extend(stair['mappings'])
        for group in stair['groups']:
            primary=group['primaryRegistryId']
            for member in group['members']:
                if member!=primary:retired.add(member);byid[member]['canonicalRegistryId']=primary
        for row in stair['catalogueObjects']:
            bypath[row['path']]['variants']=row['variants'];bypath[row['path']]['displayName']=row['displayName'];byid['bloodborne_dw:'+row['path']]['name']=row['displayName']
    assert len({m['oldRegistryId']+':'+str(m['oldVariant']) for m in mappings})==len(mappings)
    assert len({member for g in groups for member in g['members']})==sum(len(g['members']) for g in groups)
    write(RES/'remaining-catalogue.json',remaining);write(RES/'debug_catalogue.json',catalogue)
    manifest={'schemaVersion':1,'baselineCommit':base['baselineCommit'],'scope':'POST_ORIGINAL_V12_ONLY','groups':groups,'mappings':mappings}
    write(RES/'imported-type-aliases.json',manifest);write(DOC/'type-state-mapping.json',manifest)
    counts={'baselineCommit':base['baselineCommit'],'newRegisteredTypesBefore':1045,'newRegisteredAdaptersAfter':1045,'newWorkingTypesBefore':1045,'newWorkingTypesAfter':1045-len(retired),'newCreativeStacksBefore':1802,'newCreativeStacksAfter':1045-len(retired),'mergedGroups':len(groups),'retiredWorkingIds':len(retired),'reservedIdsBefore':len(catalogue['entries']),'reservedIdsAfter':len(catalogue['entries']),'originalCatalogueRowsUnchanged':all(r==byid[r['registryId']] for r in base['originalRows']),'stairFragmentApplied':stairfile.exists()}
    assert counts['originalCatalogueRowsUnchanged'];write(DOC/'type-counts.json',counts)
if __name__=='__main__':main()
