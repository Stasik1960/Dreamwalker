"""Prove only the four addressed saved component tuples, without world reads."""
import hashlib,json,sys,zipfile
from pathlib import Path
from export_preserved_assemblies import P,R,BASE,BASE_SHA,ro,meta,read,sha,bind,write,rotate,effective,yaw,canonical,require
sys.path.insert(0,str(P/'tools'))
from world_io import Tag,compound,decode_nbt,section_blocks
from offline_asset_reference import typed,nbt64

MANIFEST_SHA='affb003bfdb9a417e76707af9b25e74164fc257c06d5d11375bc78953f229229'
GROUPS_SHA='b5166a112e40dac692f6772a090f4a73403fae3a8fc2f5c4e883a99d4ca10cd4'

def hashed(value):return hashlib.sha256(canonical(value).encode()).hexdigest()
def plain(tag):
    if tag.type==10:return {k:plain(v) for k,v in tag.value.items()}
    if tag.type==9:return [plain(v) for v in tag.value]
    if tag.type==7:return list(tag.value)
    return tag.value
def state(tag):
    value=compound(tag)
    return {'registryId':value['Name'].value,'properties':{k:v.value for k,v in compound(value.get('Properties',Tag(10,{}))).items()}}
def owner(tag):
    value=compound(tag)
    return {'uuid':canonical(value['uuid'].value),'registryId':value['id'].value,'root':list(value['root'].value)}
def aliases(jar):
    base=json.loads(jar.read('bloodborne_dw/imported-type-aliases.json'));follow=json.loads(jar.read('bloodborne_dw/imported-type-aliases-v127.json'))
    primary={m:g['primaryRegistryId'] for g in base['groups'] for m in g['members']}
    mapping={(m['oldRegistryId'],m['oldVariant']):m for m in base['mappings']}
    additions={(m['oldRegistryId'],m['oldVariant']):m for m in follow['mappings']}
    for group in follow['groups']:
        for member in group['members']:primary[member]=group['primaryRegistryId']
    for key,value in list(mapping.items()):
        next_row=additions.get((value['newRegistryId'],value['newVariant']))
        if next_row:mapping[key]={**value,'newRegistryId':next_row['newRegistryId'],'newVariant':next_row['newVariant']}
    mapping.update(additions)
    for rid in list(primary):
        next_id=primary[rid];seen=set()
        while next_id!=primary.get(next_id,next_id):
            require(next_id not in seen,'Cyclic primary alias');seen.add(next_id);next_id=primary[next_id]
        primary[rid]=next_id
    return primary,mapping
def needs_migration(actual,payload,primary,mapping):
    rid=actual['registryId'];props=actual['properties']
    selected=None if primary.get(rid,rid)==rid and int(props.get('art_bank',0))>0 else mapping.get((rid,int(props.get('variant',0))))
    return bool(selected and (selected['oldRegistryId']!=selected['newRegistryId'] or payload.get('ImportedTypeVersion',0)<1))

def main():
    out=R/'preserved-assembly-raw-tuple-proof.json';bindings=R/'preserved-assembly-local-component-bindings.json'
    require(not out.exists() and not bindings.exists(),'Preserve prior bound tuple proof')
    manifest_path=R/'preserved-assemblies.json';groups_path=R/'preserved-assembly-component-matches.jsonl'
    require(sha(manifest_path)==MANIFEST_SHA and sha(groups_path)==GROUPS_SHA,'Exact addressed tuple input changed')
    manifest=read(manifest_path);groups=[json.loads(line) for line in groups_path.read_text(encoding='utf-8').splitlines()]
    require(len(groups)==4 and sum(len(g['components']) for g in groups)==8,'Only four already-found two-part tuples are authorized')
    db=ro(R/'native-grid-plan/plan.sqlite');binding=meta(db,'binding');require(binding==manifest['sourceBinding'],'Manifest source binding changed')
    copy=read(binding['copyManifest']);require(sha(binding['copyManifest'])==binding['copyManifestSha256'],'Immutable source manifest changed')
    oldpath=Path(binding['oldPlan'])/'plan.sqlite';require(oldpath.stat().st_size==binding['oldDatabaseBytes'] and oldpath.stat().st_mtime_ns==binding['oldDatabaseMtimeNs'],'Saved discovery cache binding changed')
    old=ro(oldpath);failures=[];rows=[];chunks=[];local_groups=[]
    doorpath=R/'geometry-supplement/door-footprints.json';doors=read(doorpath)
    require(doors['status']=='PASS' and doors['baselineModJarSha256']==BASE_SHA and not doors['worldOpened'],'Functional own-footprint export source binding failed')
    doorrows={v['key']:v for v in doors['poses']}
    require(sha(BASE)==BASE_SHA,'Original source JAR changed')
    with zipfile.ZipFile(BASE) as jar:
        assembly=json.loads(jar.read('bloodborne_dw/imported-assembly-v126.json'));primary,mapping=aliases(jar)
        source=P/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/ImportedAssemblyV126.java'
        algorithm={'source':bind(source),'originalJar':bind(BASE),'classResource':'dev/dreamwalker/bloodbornedw/composite/ImportedAssemblyV126.class','originalClassSha256':hashlib.sha256(jar.read('dev/dreamwalker/bloodbornedw/composite/ImportedAssemblyV126.class')).hexdigest(),'constraintMethod':'ImportedAssemblyV126.match + migrateLoadedRoot preconditions; ImportedTypeAliases.needsMigration','recipeResource':'bloodborne_dw/imported-assembly-v126.json','recipeSha256':hashlib.sha256(jar.read('bloodborne_dw/imported-assembly-v126.json')).hexdigest()}
        for group in groups:
            require(group['assemblyKey'].startswith('v126:'),'Only the found explicit V12.6 recipes are authorized')
            recipe=next(v for v in assembly['recipes'] if 'v126:'+v['key']==group['assemblyKey'])
            members=[];anchor=None;checks=[]
            for position,member in enumerate(group['components']):
                dim=group['dimension'];x,y,z=member['root'];cx,cz=x>>4,z>>4
                cached=old.execute('SELECT region_path,region_hash,compression,payload FROM chunk_cache WHERE dim=? AND x=? AND z=?',(dim,cx,cz)).fetchone()
                require(cached is not None,'Addressed resident root is absent from saved chunk cache: '+str((dim,cx,cz)))
                rel,region_sha,compression,payload_bytes=cached
                require(copy['files'][rel]['sha256']==region_sha,'Saved chunk region provenance mismatch')
                require(compression in (1,2,3),'Unknown saved chunk compression')
                nbt=decode_nbt(payload_bytes,compressed={1:'gzip',2:'zlib',3:None}[compression]);fields=compound(nbt.root)
                entities=[compound(v) for v in fields.get('block_entities',Tag(9,[],10)).value if tuple(compound(v)[k].value for k in ('x','y','z'))==(x,y,z)]
                require(len(entities)==1,'Exactly one addressed root BE required: '+str(member['root']))
                be=entities[0];sections={compound(v)['Y'].value:v for v in fields.get('sections',Tag(9,[],10)).value}
                require(y>>4 in sections,'Addressed root section absent');palette,indices=section_blocks(sections[y>>4]);actual=state(palette[indices[(x&15)+16*(z&15)+256*(y&15)]])
                require('resident' in be,'Addressed assembly root has no saved resident')
                resident=owner(be['resident']);raw_payload=be.get('payload',Tag(10,{}));actual_payload=plain(raw_payload)
                encoded=db.execute('SELECT data FROM poses WHERE key=?',(member['pose'],)).fetchone();require(encoded is not None,'Addressed saved pose disappeared');pose=json.loads(encoded[0])
                actual_pose={'registryId':actual['registryId'],'properties':actual['properties'],'shift':actual_payload.get('SourceShift',[0,0,0]),'mountY':actual_payload.get('MountY',0)+actual_payload.get('VerticalOffset',0),'legacyStairArt':actual_payload.get('ImportedStairLegacyArt','')}
                matched_pose={'registryId':pose['registryId'],'properties':pose.get('properties',{}),'shift':pose.get('shift',[0,0,0]),'mountY':pose.get('mountY',0),'legacyStairArt':pose.get('legacyStairArt','')}
                component=recipe['components'][position];member_checks={'residentUuid':resident['uuid']==member['uuid'],'residentRegistry':resident['registryId']==component['registryId'],'residentRoot':resident['root']==member['root'],'exactSavedStateAndGeometryPose':actual_pose==matched_pose,'recipeRegistry':actual['registryId']==component['registryId'],'recipeEffectiveVariant':effective(actual)==component['pose'],'noPendingTypeMigration':not needs_migration(actual,actual_payload,primary,mapping)}
                worldshift=rotate(actual_pose['shift'],yaw(actual));vertical=actual_pose['mountY']
                if anchor is None:anchor=(actual,worldshift,vertical,member['root'])
                anchor_state,anchor_shift,anchor_vertical,anchor_root=anchor;anchor_yaw=yaw(anchor_state)
                relative=rotate(component['relativePosition'],anchor_yaw)
                member_checks['integerRecipeRoot']=all(abs(v-round(v))<=1e-7 for v in relative)
                member_checks['recipeRoot']=all(abs(member['root'][i]-anchor_root[i]-relative[i])<=1e-7 for i in range(3))
                member_checks['recipeYaw']=yaw(actual)==(anchor_yaw+component.get('componentYawDelta',0))&7
                member_checks['sameProfile']=actual['properties'].get('profile','base')==anchor_state['properties'].get('profile','base')
                member_checks['sameOpen']=actual['properties'].get('open','false')==anchor_state['properties'].get('open','false')
                part_shift=rotate(component['sourceShift'],component.get('componentYawDelta',0));first_shift=recipe['components'][0]['sourceShift'];expected=rotate([part_shift[i]-first_shift[i] for i in range(3)],anchor_yaw)
                member_checks['recipeWorldShiftAndMount']=all(abs(worldshift[i]-anchor_shift[i]-expected[i]+(vertical-anchor_vertical if i==1 else 0))<=1e-6 for i in range(3))
                chunkproof={'dimension':dim,'chunk':[cx,cz],'region':rel,'boundRegionSha256':region_sha,'compression':compression,'cachedCompressedPayloadSha256':hashlib.sha256(payload_bytes).hexdigest()}
                if chunkproof not in chunks:chunks.append(chunkproof)
                raw=Tag(10,{'resident':be['resident'],'payload':raw_payload});raw_hash=hashed(typed(raw));resident_hash=hashed(typed(be['resident']))
                saved_cells=sorted({tuple(v) for v in old.execute('SELECT x,y,z FROM members WHERE dim=? AND uuid=? UNION SELECT x,y,z FROM stored_contributions WHERE dim=? AND uuid=?',(dim,member['uuid'],dim,member['uuid']))})
                require(saved_cells,'Exact addressed owner has no saved own footprint')
                spec=json.loads(jar.read('bloodborne_dw/composite/'+actual['registryId'].split(':')[1]+'.json'));functional_member=spec.get('openable') is True
                door=doorrows.get(member['pose'])
                require(not functional_member or door is not None,'Functional exact pose lacks already-exported both-state footprint: '+member['pose'])
                descriptor_cells=sorted({tuple(member['root'][i]+v[i] for i in range(3)) for v in door['cells']}) if functional_member else []
                protection=sorted(set(saved_cells)|set(descriptor_cells)|{tuple(member['root'])})
                footprint={'savedOwnMembershipCells':[list(v) for v in saved_cells],'functionalBothStates':{'artifact':bind(doorpath),'pose':member['pose'],'recordSha256':hashed(door),'record':door} if functional_member else None,'protectedOwnCells':[list(v) for v in protection],'onlyOwnIdentity':True,'noForeignOwnerClosure':True}
                item={'uuid':member['uuid'],'registryId':resident['registryId'],'pose':member['pose'],'poseDataSha256':hashlib.sha256(encoded[0].encode()).hexdigest(),'root':member['root'],'rootBlockEntityId':be.get('id',Tag(8,'')).value,'state':actual,'resident':resident,'rawResidentSha256':resident_hash,'residentAndPayloadTypedSha256':raw_hash,'residentAndPayloadNbtBase64':nbt64(raw),'payload':typed(raw_payload),'geometricPose':actual_pose,'recipeComponent':component,'checks':member_checks,'cache':chunkproof,'ownFootprint':footprint}
                members.append(item);checks.extend(member_checks.values())
                for name,passed in member_checks.items():
                    if not passed:failures.append({'groupKey':group['groupKey'],'uuid':member['uuid'],'check':name})
                del nbt,fields,entities,be,sections,palette,indices,payload_bytes
            unique=len({v['uuid'] for v in members})==len(members)
            if not unique:failures.append({'groupKey':group['groupKey'],'check':'uniqueResidentUuids'})
            functional=json.loads(jar.read('bloodborne_dw/composite/'+recipe['primaryRegistryId'].split(':')[1]+'.json')).get('openable') is True
            require(functional,'Local-original retention must have the concrete functional limitation')
            rows.append({'groupKey':group['groupKey'],'assemblyKey':group['assemblyKey'],'dimension':group['dimension'],'recipeKey':recipe['key'],'checksPassed':all(checks) and unique,'members':members,'uniqueResidentUuids':unique,'concreteRetentionLimitation':'Approved main descriptor is openable; preserve its existing functional whole assembly and both actual component owners locally rather than flatten its opening behavior.'})
            authority=next(v for v in manifest['entries'] if v['key']==group['assemblyKey'])
            local_groups.append({'groupKey':group['groupKey'],'assemblyKey':group['assemblyKey'],'dimension':group['dimension'],'decision':'local_original_existing_assembly','runtimePolicy':'local_original_functional','currentMainItem':authority['currentMainItem'],'hiddenPartRedirects':{v['registryId']:manifest['hiddenPartRedirects'][v['registryId']] for v in members if v['registryId'] in manifest['hiddenPartRedirects']},'components':[{k:v[k] for k in ('uuid','registryId','pose','poseDataSha256','root','rawResidentSha256','ownFootprint')} for v in members],'scope':'only these exact real owners and their own existing finite footprints; no pose/family/owner-graph expansion','requiresProofPass':True})
    db.close();old.close();require(sha(manifest_path)==MANIFEST_SHA,'Manifest changed during tuple proof')
    proof={'schemaVersion':1,'status':'PASS' if not failures else 'FAIL','manifest':bind(manifest_path),'componentMatches':bind(groups_path),'sourceBinding':binding,'algorithm':algorithm,'savedChunkProvenance':chunks,'groups':rows,'failures':failures,'actualOwnerCount':8,'actualGroupCount':4,'residentRawNbtRead':True,'worldFilesRead':0,'databaseWrites':0,'mapWrites':0,'gameProcessesStarted':0,'limits':['Saved immutable resident/blockstate proof only; no Minecraft gameplay pass or map-write authorization.','All loaded-root conditions are proved as saved data existence; this does not assert a live loaded-chunk schedule.']}
    write(out,proof)
    write(bindings,{'schemaVersion':1,'status':proof['status'],'manifest':bind(manifest_path),'coverage':bind(R/'preserved-assemblies-coverage.json'),'rawTupleProof':bind(out),'matcherProof':{'status':proof['status'],'algorithm':algorithm,'rawTupleProof':bind(out)},'sourceBinding':binding,'groups':local_groups,'applyAuthorization':False,'databaseWrites':0,'mapWrites':0})
    print(json.dumps({'status':proof['status'],'proof':bind(out),'finiteBindings':bind(bindings),'groups':4,'owners':8,'failures':failures}))
    require(not failures,'Exact raw saved tuple constraints failed; no local override may consume a failed binding')

if __name__=='__main__':main()
