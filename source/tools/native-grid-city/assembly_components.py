"""Finite UUID-bound original assembly preservation, separate from pose overrides.

Preview reads eight exact objects from the saved disk plan. Only the root's
post-pilot apply may change those kinds and their own protected coordinates.
An original pair is NOT a completed whole assembly until the normal runtime
matcher has actually merged it; the required anchors are exported separately.
"""
import argparse,collections,hashlib,json,zipfile
from pathlib import Path
import assembly_overrides as a
m=a.m
CHECKS={'residentUuid','residentRegistry','residentRoot','exactSavedStateAndGeometryPose','recipeRegistry','recipeEffectiveVariant','noPendingTypeMigration','integerRecipeRoot','recipeRoot','recipeYaw','sameProfile','sameOpen','recipeWorldShiftAndMount'}
REASON='preserved_assembly_exact_component_pending_runtime_merge'

def ref(path):return {'path':str(m.checked(path)),'sha256':m.sha(path)}
def bound(document):
    m.require(set(document)>={'path','sha256'} and m.sha(document['path'])==document['sha256'],'Finite assembly evidence changed: '+str(document))
    return m.read(document['path'])
def points(values):
    m.require(all(isinstance(v,list) and len(v)==3 and all(type(n)is int for n in v) for v in values),'An assembly footprint must contain exact integer cells')
    result={tuple(v) for v in values};m.require(len(result)==len(values),'Duplicate finite assembly cells');return result

def load(db,path):
    doc=m.read(path);m.require(doc.get('schemaVersion')==1 and doc.get('status')=='PASS' and doc.get('applyAuthorization')is False and doc['sourceBinding']==m.meta(db,'binding'),'A source-bound, non-mutating finite component proof is required')
    manifest=bound(doc['manifest']);coverage=bound(doc['coverage']);raw=bound(doc['rawTupleProof']);m.require(manifest['sourceBinding']==doc['sourceBinding'] and coverage['sourceBinding']==doc['sourceBinding'] and raw['sourceBinding']==doc['sourceBinding'],'Assembly evidence sources disagree')
    m.require(coverage['manifest']==doc['manifest'] and raw['manifest']==doc['manifest'] and raw['status']=='PASS' and not raw['failures'],'Finite raw tuple evidence has unresolved matches')
    algorithm=doc['matcherProof'];m.require(algorithm['status']=='PASS' and algorithm['algorithm']==raw['algorithm'] and algorithm['rawTupleProof']==doc['rawTupleProof'],'One exact runtime matcher proof must cover every finite group')
    proof=algorithm['algorithm'];m.require(m.sha(proof['source']['path'])==proof['source']['sha256'] and m.sha(proof['originalJar']['path'])==proof['originalJar']['sha256'],'The existing whole-assembly mechanism proof changed')
    with zipfile.ZipFile(proof['originalJar']['path']) as archive:
        m.require(hashlib.sha256(archive.read(proof['classResource'])).hexdigest()==proof['originalClassSha256'] and hashlib.sha256(archive.read(proof['recipeResource'])).hexdigest()==proof['recipeSha256'],'Original runtime class/finite recipe bytes changed')
    groups_path=coverage['componentGroups'];m.require(m.sha(groups_path['path'])==groups_path['sha256'],'Saved finite component match stream changed')
    with Path(groups_path['path']).open(encoding='utf-8') as stream:groups={row['groupKey']:row for row in map(json.loads,stream) if row}
    raw_groups={row['groupKey']:row for row in raw['groups']};m.require(len(raw_groups)==len(raw['groups']) and set(raw_groups)==set(groups)=={g['groupKey'] for g in doc['groups']} and len(doc['groups'])==len(groups),'All independently enumerated finite tuple groups must be covered exactly once')
    components={};anchors=[];door_cache={}
    for group in doc['groups']:
        key=group['groupKey'];original=groups[key];actual=raw_groups[key];m.require(group['decision']=='local_original_existing_assembly' and group['runtimePolicy']=='local_original_functional' and actual['checksPassed']is True and actual['dimension']==group['dimension']==original['dimension'] and actual['assemblyKey']==group['assemblyKey']==original['assemblyKey'],'Only the exact original functional assembly route is authorized')
        actual_members={v['uuid']:v for v in actual['members']};expected_members={v['uuid']:v for v in original['components']};m.require(len(actual_members)==len(actual['members']) and len(expected_members)==len(original['components']) and set(actual_members)==set(expected_members)=={v['uuid'] for v in group['components']},'Finite group resident identities disagree')
        for component in group['components']:
            uid=component['uuid'];identity=(group['dimension'],uid);m.require(identity not in components,'An owner cannot belong to multiple finite assembly overrides');member=actual_members[uid];saved=expected_members[uid]
            for field in ('uuid','pose','root'):
                m.require(component[field]==member[field]==saved[field],'Component identity/pose/root changed: '+field)
            m.require(component['registryId']==member['registryId']==saved['component']['registryId'] and component['poseDataSha256']==member['poseDataSha256'] and component['rawResidentSha256']==member['rawResidentSha256'],'Component registry or exact resident proof changed')
            m.require(set(member['checks'])>=CHECKS and all(member['checks'][v]is True for v in CHECKS),'Every exact resident/matcher precondition must pass')
            pose=db.execute('SELECT data FROM poses WHERE key=?',(component['pose'],)).fetchone();m.require(pose and a.data_sha(pose[0])==component['poseDataSha256'] and json.loads(pose[0])['registryId']==component['registryId'],'A component SourcePose changed')
            obj=db.execute('SELECT dim,uuid,pose,x,y,z,kind,reason,source_status FROM objects WHERE dim=? AND uuid=?',identity).fetchone();m.require(obj and obj[2]==component['pose'] and list(obj[3:6])==component['root'] and obj[6] in ('static','preserve'),'Only an exact existing original static/preserved component may be retained')
            real=db.execute('SELECT registry_id,x,y,z FROM old.stored_owners WHERE dim=? AND uuid=?',identity).fetchone();m.require(real and real[0]==component['registryId'] and list(real[1:])==component['root'],'A synthetic native unit is not an independent original assembly owner')
            footprint=component['ownFootprint'];m.require(footprint==member['ownFootprint'] and footprint['onlyOwnIdentity']is True and footprint['noForeignOwnerClosure']is True,'Only the exact resident\'s own footprint is authorized')
            own=points(footprint['savedOwnMembershipCells']);protected=points(footprint['protectedOwnCells']);m.require(tuple(component['root'])in own,'Original resident root is missing from its own membership')
            both=footprint.get('functionalBothStates')
            if both:
                descriptor=both['artifact'];token=(descriptor['path'],descriptor['sha256'])
                if token not in door_cache:door_cache[token]=bound(descriptor)
                candidates=[v for v in door_cache[token]['poses'] if v['key']==component['pose']];m.require(len(candidates)==1 and candidates[0]==both['record'] and m.digest(both['record'])==both['recordSha256'] and both['pose']==component['pose'],'Both-state exact native descriptor proof changed')
                record=both['record'];m.require({v['open'] for v in record['states']}=={False,True} and all(type(v['open'])is bool for v in record['states']),'Both exact open and closed states are required')
                offsets=set().union(*(points(v['cells']) for v in record['states']));m.require(offsets==points(record['cells']),'Door descriptor union must include both complete states')
                root=component['root'];own|={(root[0]+x,root[1]+y,root[2]+z) for x,y,z in offsets}
            m.require(own==protected,'The protected footprint must equal saved own cells plus the two finite functional states')
            components[identity]={'groupKey':key,'component':component,'row':obj,'cells':protected}
        anchor=group['components'][0];m.require(anchor['registryId']==group['currentMainItem']['registryId'] and anchor['root']==original['anchor'] and type(group['currentMainItem']['installationPose'])is int,'The runtime anchor must remain the proved primary item')
        anchors.append({'groupKey':key,'assemblyKey':group['assemblyKey'],'dimension':group['dimension'],'root':anchor['root'],'primaryOwnerUuid':anchor['uuid'],'expectedRegistryId':group['currentMainItem']['registryId'],'expectedInstallationPose':group['currentMainItem']['installationPose'],'retiredOwnerUuids':[v['uuid'] for v in group['components'][1:]],'hiddenPartRedirects':group['hiddenPartRedirects'],'requiredResult':'ONE_PRIMARY_RESIDENT_WHOLE_ASSEMBLY_OLD_UPPER_UUID_RETIRED','initialComponentsAlreadyMerged':False})
    m.require(len(components)==raw['actualOwnerCount'] and len(anchors)==raw['actualGroupCount'],'Finite raw tuple coverage counts differ')
    return doc,components,anchors

def fingerprint(components):return m.digest([list(components[key]['row']) for key in sorted(components)])
def conflicts(db,components,compiled=False):
    result=[]
    for (dim,uid),item in sorted(components.items()):
        for table in ('selected_objects','activated_native','cleanup_owners'):
            if db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND uuid=? LIMIT 1',(dim,uid)).fetchone():result.append({'kind':table,'dimension':dim,'uuid':uid})
        for at in sorted(item['cells']):
            for table in ('fragments','substrate_fragments'):
                if db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(dim,*at,uid)).fetchone():result.append({'kind':table,'dimension':dim,'uuid':uid,'position':list(at)})
            placement=db.execute('SELECT type_index FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone()
            bridge=db.execute('SELECT type_index FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone()
            if placement and (not bridge or (compiled and placement[0]!=bridge[0])):result.append({'kind':'unbridged_own_footprint_placement','dimension':dim,'uuid':uid,'position':list(at)})
    return result

def preview(plan,bindings_path,pose_preview,out):
    db=a.open_plan(plan)
    try:
        doc,components,anchors=load(db,bindings_path);pose=m.read(pose_preview);m.require(pose['status']=='FINITE_PREVIEW_PASS' and pose['manifest']==doc['manifest'] and pose['sourceBinding']==doc['sourceBinding'],'The accepted exact-pose preview must precede the distinct finite component gate')
        contacts=conflicts(db,components);counts=collections.Counter((item['row'][6],'preserve') for item in components.values())
        summary=[{'registryId':v['registryId'],'exactVariant':v['exactVariant'],'installationPose':v['installationPose'],'reason':'preserved_assembly_unproved_component_recipe','objects':sum(p['objectsByIntendedKind'].get('preserve',0) for p in pose['poses'] if v['key']in p['assemblyKeys'])} for v in bound(doc['manifest'])['entries'] if v['preservation']['runtimePolicy']=='local_original_due_uncertainty']
        report={'schemaVersion':1,'status':'FINITE_COMPONENT_PREVIEW_PASS' if not contacts else 'NEEDS_BOUNDED_PILOT_CORRECTION','kind':'EXACT_UUID_PRESERVED_ASSEMBLY_COMPONENT_PREVIEW','sourceBinding':doc['sourceBinding'],'manifest':doc['manifest'],'coverage':doc['coverage'],'rawTupleProof':doc['rawTupleProof'],'componentBindings':ref(bindings_path),'acceptedPosePreview':ref(pose_preview),'matchedObjectsBeforeSha256':fingerprint(components),'componentGroups':len(anchors),'exactOwnerCount':len(components),'changes':[{'before':k[0],'after':k[1],'objects':v} for k,v in sorted(counts.items())],'pilotIntersections':contacts,'uncertainPoseRetentions':summary,'requiredActualRuntimeOutcomes':anchors,'runtimeAssemblyOutcomes':'PENDING_ACTUAL_FULL_LOAD','worldFilesRead':0,'persistentDatabaseWrites':0,'mapWrites':0,'consumerSha256':m.sha(__file__)}
        m.atomic_json(out,report);return report
    finally:db.close()

def apply(plan,bindings_path,preview_path,expected_sha,out):
    m.require(m.sha(preview_path)==expected_sha,'Accepted finite component preview changed');before=m.read(preview_path);m.require(before['status']=='FINITE_COMPONENT_PREVIEW_PASS' and not before['pilotIntersections'],'A fragmented pilot assembly requires bounded correction first')
    db=a.open_plan(plan,True)
    try:
        db.execute('BEGIN IMMEDIATE');m.require(not m.meta(db,'applyStarted') and not m.meta(db,'scope') and m.meta(db,'successfulPilotGate') and m.meta(db,'fullNativeFunctionGateRequired'),'Only root may apply finite components after successful-pilot promotion')
        pose=m.meta(db,'preservedAssemblyOverrides');m.require(pose and pose['manifest']==before['manifest'] and m.sha(pose['receipt']['path'])==pose['receipt']['sha256'],'The accepted pose overrides must be imported first')
        doc,components,anchors=load(db,bindings_path);m.require(before['componentBindings']==ref(bindings_path) and before['acceptedPosePreview']==m.read(pose['receipt']['path'])['acceptedPreview'],'Finite component or pose preview binding changed')
        prior=m.meta(db,'preservedAssemblyComponents')
        if prior:
            m.require(prior['path']==str(m.checked(out)) and m.sha(out)==prior['sha256'],'A different component override already exists');saved=m.read(out);m.require(saved['acceptedPreview']==ref(preview_path) and saved['matchedObjectsAfterSha256']==fingerprint(components),'Committed component preservation changed');return saved
        m.require(before['matchedObjectsBeforeSha256']==fingerprint(components),'A finite component kind/source decision changed since accepted preview')
        for (dim,uid),item in sorted(components.items()):
            if item['row'][6]!='preserve':db.execute("UPDATE objects SET kind='preserve',reason=? WHERE dim=? AND uuid=?",(REASON,dim,uid))
            db.executemany("INSERT INTO protected_cells VALUES(?,?,?,?,?,?) ON CONFLICT(dim,x,y,z,uuid) DO UPDATE SET reason=excluded.reason WHERE protected_cells.reason IN ('functional_guest','whole_local_overlap','missing_exported_footprint')",[(dim,*at,uid,REASON) for at in sorted(item['cells'])])
        doc,after,anchors=load(db,bindings_path);report={'schemaVersion':1,'status':'PASS','kind':'EXACT_UUID_PRESERVED_ASSEMBLY_COMPONENT_OVERRIDES','sourceBinding':doc['sourceBinding'],'manifest':doc['manifest'],'coverage':doc['coverage'],'rawTupleProof':doc['rawTupleProof'],'componentBindings':ref(bindings_path),'acceptedPosePreview':before['acceptedPosePreview'],'poseOverrides':pose['receipt'],'acceptedPreview':ref(preview_path),'matchedObjectsBeforeSha256':before['matchedObjectsBeforeSha256'],'matchedObjectsAfterSha256':fingerprint(after),'exactOwnerCount':len(after),'componentGroups':len(anchors),'changes':before['changes'],'requiredActualRuntimeOutcomes':anchors,'runtimeAssemblyOutcomes':'PENDING_ACTUAL_FULL_LOAD','noFamilyOverride':True,'noOwnerGraphClosure':True,'noNativeUnitConversion':True,'originalPrivatePayloadClassificationUnchanged':True,'worldFilesRead':0,'mapWrites':0,'consumerSha256':m.sha(__file__)}
        m.atomic_json(out,report);m.meta(db,'preservedAssemblyComponents',ref(out));m.meta(db,'preservedAssemblyGate',ref(out));db.commit();return report
    finally:db.close()

def require_bound(db,compiled=False):
    value=m.meta(db,'preservedAssemblyComponents');m.require(value,'Import the distinct finite component/raw-resident coverage gate before full composition');receipt=bound(value)
    m.require(receipt['status']=='PASS' and receipt['sourceBinding']==m.meta(db,'binding'),'Finite assembly override belongs to another source');doc,components,anchors=load(db,receipt['componentBindings']['path'])
    m.require(receipt['componentBindings']==ref(receipt['componentBindings']['path']) and receipt['manifest']==doc['manifest'] and receipt['coverage']==doc['coverage'] and receipt['rawTupleProof']==doc['rawTupleProof'],'Finite component evidence changed after import')
    for (dim,uid),item in components.items():
        m.require(item['row'][6]=='preserve','An original finite assembly component became independently migratable')
        for at in item['cells']:m.require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(dim,*at,uid)).fetchone(),'An assembly functional/owned cell lost its exact local protection')
    m.require(not conflicts(db,components,compiled),'A preserved finite assembly component entered independent native geometry/cleanup')
    return value

def dimension_id(dim):return {'.':'minecraft:overworld','DIM-1':'minecraft:the_nether','DIM1':'minecraft:the_end'}.get(dim,dim.removeprefix('dimensions/').replace('/',':',1))
def light_debt(groups):
    return sorted({(g['dimension'],x+dx,z+dz) for g in groups for x,z in {tuple(v) for v in g['ownedChunks']} for dx in (-1,0,1) for dz in (-1,0,1)})

def present_debt(target,debt):
    present=[];absent=[];headers={}
    for dim,x,z in debt:
        path=m.checked(target/Path(dim)/'region'/('r.%d.%d.mca'%(x>>5,z>>5)))
        if path not in headers:
            header=b''
            if path.is_file():
                with path.open('rb') as stream:header=stream.read(8192)
            m.require(len(header)in (0,8192),'Short nonempty light-neighbor region header');headers[path]=header
        header=headers[path];index=((x&31)+32*(z&31))*4;location=int.from_bytes(header[index:index+4],'big') if header else 0
        if location>>8:present.append((dim,x,z))
        else:absent.append({'dimension':dimension_id(dim),'x':x,'z':z,'region':str(path),'location':0,'proof':'NO_ALLOCATED_NATIVE_MCA_SLOT_BEFORE_LIGHT_INVALIDATION'})
    return present,absent

def prepare_full_light(db,plan):
    """Called only by full apply, before offline native-light invalidation."""
    m.require(not m.meta(db,'scope') and m.meta(db,'fullNativeFunctionGateRequired'),'Assembly light preparation is full-city only')
    receipt_ref=require_bound(db,compiled=True);receipt=bound(receipt_ref);doc,components,anchors=load(db,receipt['componentBindings']['path']);raw=bound(doc['rawTupleProof']);raw_groups={g['groupKey']:g for g in raw['groups']}
    m.require(len(anchors)==4 and len(components)==8,'Only the four proved original assembly tuples are authorized for the full native-light phase')
    groups=[]
    for anchor in anchors:
        members=[item for item in components.values() if item['groupKey']==anchor['groupKey']];cells=set().union(*(v['cells'] for v in members));group_components=[{'ownerUuid':v['component']['uuid'],'ownerIntArray':json.loads(v['component']['uuid']),'registryId':v['component']['registryId'],'root':v['component']['root']} for v in members]
        m.require(group_components[0]['ownerUuid']==anchor['primaryOwnerUuid'] and len(group_components)==2,'The original matcher anchor/component order changed')
        groups.append({**anchor,'worldDimension':dimension_id(anchor['dimension']),'recipeKey':raw_groups[anchor['groupKey']]['recipeKey'],'components':group_components,'ownedCells':[list(v) for v in sorted(cells)],'ownedChunks':[list(v) for v in sorted({(x>>4,z>>4) for x,y,z in cells})]})
    debt=light_debt(groups);present,absent=present_debt(Path(m.meta(db,'target')),debt);m.require({(g['dimension'],x,z) for g in groups for x,z in g['ownedChunks']}<=set(present),'An original assembly owned chunk is absent from the full target')
    request={'schemaVersion':1,'kind':'EXACT_FOUR_ORIGINAL_ASSEMBLIES_BEFORE_FULL_RELIGHT','status':'PENDING_ACTUAL_NATIVE_ASSEMBLY_MERGE','sourceBinding':doc['sourceBinding'],'assemblyGate':receipt_ref,'manifest':doc['manifest'],'rawTupleProof':doc['rawTupleProof'],'matcherAlgorithm':doc['matcherProof']['algorithm'],'target':m.meta(db,'target'),'runtime':m.meta(db,'runtime'),'catalog':m.meta(db,'catalog'),'groups':groups,'requiredGroups':4,'requiredOriginalOwners':8,'requiredLightChunks':[{'dimension':dimension_id(dim),'x':x,'z':z} for dim,x,z in present],'absentLightNeighbors':absent,'noOwnerGraphClosure':True,'freshFullLightOnly':True}
    path=plan/'preserved-assembly-runtime-request.json'
    if path.exists():m.require(m.read(path)==request,'A different full assembly/light request already exists; preserve its cursor')
    else:m.atomic_json(path,request)
    for dim,x,z in debt:db.execute("INSERT OR IGNORE INTO relight(dim,cx,cz,reason) VALUES(?,?,?,'approved_assembly_runtime_merge')",(dim,x,z))
    m.meta(db,'fullAssemblyLightRequest',ref(path));db.commit();return ref(path)

def accept_full_light(db,binding,engine):
    request_ref=m.meta(db,'fullAssemblyLightRequest');m.require(request_ref and binding.get('assemblyRequest')==request_ref,'Full native light must bind the finite assembly request');request=bound(request_ref)
    phase_ref=engine.get('assemblyPhase');m.require(phase_ref and phase_ref.get('path')==binding.get('assemblyPhaseReceipt'),'Full native light has no actual assembly save phase');phase=bound(phase_ref)
    m.require(phase.get('status')=='PASS' and phase.get('actualRuntimeQueries')is True and phase.get('saveFlushed')is True and phase.get('requestSha256')==request_ref['sha256'] and phase.get('world')==binding['target'] and phase.get('runtimeSha256')==binding['runtime']['sha256'] and phase.get('catalogSha256')==binding['catalog']['sha256'],'Actual four-assembly outcomes are not bound to this saved full runtime/world')
    expected={g['groupKey']:g for g in request['groups']};actual={g['groupKey']:g for g in phase.get('outcomes',[])};m.require(len(expected)==len(actual)==len(phase['outcomes'])==4 and set(actual)==set(expected),'Native assembly phase must cover all four exact anchors')
    for key,g in expected.items():
        value=actual[key];m.require(value.get('status')=='PASS' and value.get('root')==g['root'] and value.get('primaryOwnerUuid')==g['primaryOwnerUuid'] and value.get('registryId')==g['expectedRegistryId'] and value.get('installationPose')==g['expectedInstallationPose'] and value.get('recipeKey')==g['recipeKey'] and value.get('retiredOwnerUuids')==g['retiredOwnerUuids'] and value.get('oldUpperResidentAndContributionAbsent')is True and value.get('oldUpperSavedMembershipAbsent')is True and value.get('mergedFootprintWithinDeclaredCells')is True and value.get('primaryWholeMembershipComplete')is True,'An actual whole assembly outcome is incomplete: '+key)
    return phase_ref

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--plan',type=Path,default=m.DEFAULT_PLAN);parser.add_argument('--bindings',type=Path,required=True);parser.add_argument('--pose-preview',type=Path);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--apply',action='store_true');parser.add_argument('--preview',type=Path);parser.add_argument('--expected-preview-sha256');args=parser.parse_args();m.control_check()
    if args.apply:
        m.require(args.preview and args.expected_preview_sha256,'Root apply requires its accepted exact component preview');result=apply(args.plan,args.bindings,args.preview,args.expected_preview_sha256,args.out)
    else:
        m.require(args.pose_preview,'The already accepted exact-pose preview is required');result=preview(args.plan,args.bindings,args.pose_preview,args.out)
    print(m.canonical({'status':result['status'],'out':str(args.out),'sha256':m.sha(args.out)}))
if __name__=='__main__':main()
