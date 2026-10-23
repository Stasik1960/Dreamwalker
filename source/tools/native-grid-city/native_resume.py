"""Explicit finite certificates after a proved native-transfer failure.

This is not an automatic conflict policy. Each root-approved scope names exact
isolated candidates from a bound report. It never follows another owner. Native
and finalization commit boundaries resume without repeating selection/preflight.
"""
import hashlib
import json
import sqlite3
from pathlib import Path

FAILED_SOURCE = '56df9f37ce4218d7d6911336e16ddaa4e4a50939f0d0d432c23b1a2cb4a0d239'
DIAGNOSTIC_SHA = '0cb269677c97644aed5163fb02d302181563bda34a5c13d87ae006078fa12689'
ADMISSION_SHA = 'ec8d264af1fcb6f48abe853b81bfb1c3fa25579a0e841381d4eb7f2ad2556ce1'
PHYSICAL_SHA = '019b1e247a216a953a05817d86d5c792d22605bac7e32a60517369fe94654840'
PREFLIGHT_SHA = '853f06ebffef1dab19a8fa838ab28651fc9f2f02be478dbed981811c8d907586'
DECOR_TYPES_SHA = '160bc13e47a9eb442bea517120ff3a31849554971b53f13b8fb71aacbc1dc337'
SIX_DECOR_SHA = 'de8a995d38538156cf0350eedde0a77a09a539be2118d8001f55264a4cc453a4'
FOUR_DECOR_SHA = 'fe4983a629b909747d6e59ce4277485d70d93069aae859a12ba1ef9d8de56196'
C = '[-533749318,1735200735,-92998311,375839603]'
D = '[-574261653,1664766113,-2029337881,-275948963]'
C_ROOT = (-270,52,-191)
REASON = 'native_overhang_exact_D_local_retention'
META_KEYS = ('binding','classification','poseFragments','fullNativeFunctionGate',
             'fullNativeFunctionDecisions','functionalGuestPreflight',
             'preservedAssemblyOverrides','preservedAssemblyComponents',
             'preservedAssemblyGate','wholeTemplates','carrierMarkerRequest',
             'doorFootprintGuard','payloadDecisions')
COUNT_TABLES = ('selected_objects','fragments','substrate_fragments','whole_cells',
                'native_visual_roots','native_visual_fragments','activated_native',
                'cell_queue','bridge_cells')


def ref(m,path):
    path=m.checked(path)
    return {'path':str(path),'sha256':m.sha(path)}


def read_bound(m,binding):
    m.require(binding and m.sha(binding['path'])==binding['sha256'],'Native phase evidence changed: '+str(binding))
    return m.read(binding['path'])


def require_unfinalized(m,db,finalizing=False):
    m.require(not m.meta(db,'scope') and m.meta(db,'successfulPilotGate') and m.meta(db,'fullNativeFunctionGateRequired'),'Native phase recovery is full-city only after promotion')
    for key in ('applyStarted','offlineApplied','compositionGeneration'):
        m.require(not m.meta(db,key),'Native transfer cannot resume after current generation/apply: '+key)
    if not finalizing:m.require(not m.meta(db,'compositionsSha256'),'Native phase already has finalized output metadata')
    tables=('cleanup_owners','regions','relight') if finalizing else ('placements','compositions','cleanup_owners','regions','source_chunks','relight')
    for table in tables:
        m.require(not db.execute('SELECT 1 FROM '+table+' LIMIT 1').fetchone(),'Native transfer already has finalized data: '+table)


def evidence(m,db,plan,scope):
    directory=plan/'full'
    bindings={name:ref(m,directory/filename) for name,filename in (
        ('diagnostic','native-overhang-conflict.json'),
        ('admission','native-overhang-retention-admission.json'),
        ('physical','native-overhang-C-source-proof.json'))}
    for name,wanted in (('diagnostic',DIAGNOSTIC_SHA),('admission',ADMISSION_SHA),('physical',PHYSICAL_SHA)):
        m.require(bindings[name]['sha256']==wanted,'This transition admits only the approved first exact conflict: '+name)
    diagnostic,admission,physical=(read_bound(m,bindings[k]) for k in ('diagnostic','admission','physical'))
    m.require(diagnostic['sourceBinding']==physical['sourceBinding']==m.meta(db,'binding'),'Local retention belongs to another source snapshot')
    component=physical['nativeComponent']
    m.require(physical['status']=='PASS' and component['dimension']=='.' and m.canonical(component['uuid'])==C and tuple(component['root'])==C_ROOT,'Physical proof identifies another native component')
    m.require(physical['CmustRemainOriginalCells']==[list(C_ROOT)] and not physical.get('CphysicalHelperCellsOutsideRoot'),'Retain only the proved legacy member, not guessed visual neighbors')
    m.require(component['pose']==diagnostic['nativeObject']['pose'] and component['poseDataSha256']==diagnostic['nativePoseDataSha256'],'Exact native pose proof changed')
    m.require(admission['basis']==bindings['diagnostic'] and admission['all27QueuePending'] is True and admission['existingSubstrateOrNativeVisualReferences']==0 and admission['otherSelectedOwnersAt27Cells']==[],'The exact D isolation admission did not pass')
    m.require(len(admission['owners'])==1 and admission['owners'][0]['uuid']==D and len(admission['owners'][0]['cells'])==27,'Retention scope must remain one owner / 27 cells')
    failed=scope.get('failedRun') or ref(m,m.ROOT/'research/processes/20261022T222752-migrate.json')
    failure_path=m.checked(failed['path']);failure=read_bound(m,failed)
    arguments=failure.get('args',[])
    m.require(failure.get('status')=='FAIL' and failure.get('exitCode')==1 and len(arguments)==3 and arguments[0]=='--plan' and m.checked(arguments[1])==plan and arguments[-1] in ('compose','resume-native-compose'),'Actual same-plan failed native compose receipt required')
    source_rows=list(failure['inputSources'].values())
    archived=next((r for r in source_rows if Path(r['archive']).name=='migrate.py'),None)
    expected_source=FAILED_SOURCE if arguments[-1]=='compose' else m.sha(m.__file__)
    m.require(archived and archived['sha256']==expected_source and m.sha(archived['archive'])==expected_source,'Actual failed source archive is missing or altered')
    if arguments[-1]=='resume-native-compose':
        resume_archive=next((r for r in source_rows if Path(r['archive']).name=='native_resume.py'),None)
        m.require(resume_archive and m.sha(resume_archive['archive'])==resume_archive['sha256']==m.sha(__file__),'Failed resumed phase used another finite-admission implementation')
    for row in source_rows:m.require(m.sha(row['archive'])==row['sha256'],'Failed-run archived input changed')
    context=next(r for r in source_rows if Path(r['archive']).name=='context_stage.py')
    m.require(m.sha(Path(m.__file__).with_name('context_stage.py'))==context['sha256'],'Context/native-function semantics changed after the accepted preflight')
    log=m.checked(failure['log']);message=log.read_text(encoding='utf-8',errors='replace')
    m.require('Required native visual overhang meets a protected local object' in message and any(case['nativeUuid'] in message for case in scope['cases']),'Failed receipt is not one of the explicitly admitted native conflicts')
    gate=m.meta(db,'functionalGuestPreflight');preflight=read_bound(m,gate)
    m.require(gate['sha256']==PREFLIGHT_SHA and preflight.get('status')=='PASS','The already accepted full functional preflight must remain bound')
    m.context_stage.require_full_functions(m,db)
    return bindings|{'failedRun':ref(m,failure_path),'failedLog':ref(m,log),
                     'failedSource':archived,'functionalPreflight':gate},diagnostic,admission,physical


def row_dict(db,sql,args):
    cursor=db.execute(sql,args)
    return [dict(zip([c[0] for c in cursor.description],row)) for row in cursor]


def local_snapshot(m,db,diagnostic,admission):
    expected=diagnostic['selectedTouchingObjects']
    m.require(len(expected)==1 and expected[0]['uuid']==D,'Only the approved D owner may be removed from selection')
    obj=expected[0]['object']
    objects=row_dict(db,'SELECT o.*,p.data AS poseData FROM objects o JOIN poses p ON p.key=o.pose WHERE o.dim=? AND o.uuid=?',('.',D))
    m.require(objects==[obj],'Classified object/pose/source status changed; never rewrite census inputs')
    selected=row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',('.',D))
    m.require(selected==[{k:obj[k] for k in ('dim','uuid','pose','x','y','z','kind')}],'D is no longer the exact selected static object')
    fragments=row_dict(db,'SELECT x,y,z,pose,dx,dy,dz FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z',('.',D))
    m.require(fragments==sorted(expected[0]['finiteFragments'],key=lambda r:(r['x'],r['y'],r['z'])),'The exact 27 D fragment rows changed')
    cells=[]
    for saved in admission['owners'][0]['cells']:
        at=tuple(saved['position']);args=('.',*at)
        current={'position':list(at)}
        for name,sql,params in (
            ('queue','SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args),
            ('otherFragments','SELECT uuid,pose FROM fragments WHERE dim=? AND x=? AND y=? AND z=? AND uuid!=?',(*args,D)),
            ('substrate','SELECT uuid,pose FROM substrate_fragments WHERE dim=? AND x=? AND y=? AND z=?',args),
            ('nativeVisual','SELECT rx,ry,rz,state FROM native_visual_fragments WHERE dim=? AND x=? AND y=? AND z=?',args),
            ('whole','SELECT uuid,template FROM whole_cells WHERE dim=? AND x=? AND y=? AND z=?',args),
            ('bridge','SELECT host_state,retained_resident FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args)):
            current[name]=[list(r) for r in db.execute(sql,params)]
        m.require(current==saved,'One of the exact D27 pending/foreign demands changed: '+str(at))
        m.require(current['queue']==[[0]] and not any(current[k] for k in ('otherFragments','substrate','nativeVisual','whole')),'Retention cannot remove processed or independently demanded cells')
        m.require(not db.execute('SELECT 1 FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),'A D cell is itself an activated native visual root')
        cells.append(current)
    cobj=row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',('.',C))
    m.require(cobj==[diagnostic['nativeObject']],'Retained C original object changed')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(diagnostic['nativeObject']['pose'],)).fetchone()
    m.require(pose and hashlib.sha256(pose[0].encode()).hexdigest()==diagnostic['nativePoseDataSha256'],'Exact retained C pose changed')
    m.require(not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',('.',C)).fetchone(),'C was already activated; this local pending-only transition cannot undo it')
    for dx,dy,dz in diagnostic['nativePoseCells']:
        m.require(not db.execute('SELECT 1 FROM substrate_fragments WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',('.',C_ROOT[0]+dx,C_ROOT[1]+dy,C_ROOT[2]+dz,C)).fetchone(),'C already has transferred visual fragments')
    return {'object':objects,'selected':selected,'fragments':fragments,'cells':cells,'nativeObject':cobj}


def snapshot_bindings(m,db):
    return {key:m.meta(db,key) for key in META_KEYS}


def counts(db):
    return {table:db.execute('SELECT count(*) FROM '+table).fetchone()[0] for table in COUNT_TABLES}


def load_scope(m,db,path,expected_sha):
    m.require(expected_sha and m.sha(path)==expected_sha,'Explicit scope SHA is required before reading any candidate list')
    scope=m.read(path)
    m.require(scope.get('schemaVersion')==1 and scope.get('status') in ('ROOT_APPROVED_FINITE_ISOLATED_SCOPE','ROOT_APPROVED_FINITE_NATIVE_RETENTION_SCOPE'),'An explicit root-reviewed finite scope is required; discovery is never mutation authority')
    m.require(set(scope)<=set(('schemaVersion','status','candidateReport','physicalPattern','typeGeometryProof','physicalDecorProof','fourDecorProof','cases','failedRun','authorization','createdUtc')),'Unknown finite scope field')
    report=read_bound(m,scope['candidateReport']);physical=read_bound(m,scope['physicalPattern'])
    m.require(scope['physicalPattern']['sha256']==PHYSICAL_SHA and physical['status']=='PASS','Only the approved exact physical pattern may be reused')
    m.require(report.get('status')=='READ_ONLY_EXACT_POSE_INSTANCE_REPORT_NOT_APPLY_GATE' and report.get('sourceBinding')==m.meta(db,'binding') and report['physicalAndLifecycleProof']==scope['physicalPattern'],'Candidate certificate belongs to another source/physical authority')
    m.require(report['exactPose']==physical['nativeComponent']['pose'] and report['poseDataSha256']==physical['nativeComponent']['poseDataSha256'] and report['requiredActualRootState']==physical['nativeComponent']['actualRootState'] and report['requiredLegacyRecipe']==1161,'Candidate report does not prove the exact shared legacy recipe')
    type_binding=scope.get('typeGeometryProof')
    m.require(type_binding and type_binding['sha256']==DECOR_TYPES_SHA,'Retained D actual member geometry must be explicitly bound')
    types=read_bound(m,type_binding)
    m.require(types['sourceBinding']==report['sourceBinding'] and types['inputInstanceReport']==scope['candidateReport'],'D type geometry belongs to another source/report')
    type_rows={row['pose']:row for row in types['types']}
    six_binding=scope.get('physicalDecorProof');six=None
    if six_binding:
        m.require(six_binding['sha256']==SIX_DECOR_SHA,'Only the exact six approved visual-only overlap cases may use this route')
        six=read_bound(m,six_binding)
        m.require(six['status']=='PASS' and six['sourceBinding']==report['sourceBinding'] and six['inputInstanceReport']==scope['candidateReport'] and six['inputSavedFacts']==type_binding,'Visual-only overlap proof binding changed')
    four_binding=scope.get('fourDecorProof');four=None
    if four_binding:
        m.require(four_binding['sha256']==FOUR_DECOR_SHA,'Only the four exact approved pairs may retain the additional E owner')
        four=read_bound(m,four_binding)
        m.require(four['sourceBinding']==report['sourceBinding'] and four['inputSavedFacts']==type_binding and four['ownerGraphTraversal'] is False,'Four-pair source/member proof binding changed')
    requested=scope.get('cases',[])
    m.require(0<len(requested)<=64 and all({'nativeUuid','selectedOwnerUuid'}<=set(row)<=set(('nativeUuid','selectedOwnerUuid','additionalSelectedOwnerUuid','policy')) for row in requested),'Scope must name at most 64 exact native/owner pairs')
    candidates={row['nativeObject']['uuid']:row for row in report['candidates']}
    m.require(len(candidates)==len(report['candidates']),'Ambiguous native candidate identity')
    seen_native=set();seen_owner=set();selected=[]
    for row in requested:
        uid,owner=row['nativeUuid'],row['selectedOwnerUuid'];candidate=candidates.get(uid)
        m.require(uid not in seen_native and owner not in seen_owner,'A finite scope cannot repeat native or selected-owner identities')
        seen_native.add(uid);seen_owner.add(owner)
        m.require(candidate and len(candidate['directSelectedOwners'])==1 and candidate['directSelectedOwners'][0]['uuid']==owner,'Scope owner is not the one exact direct owner')
        policy=row.get('policy','isolated_pending');decor=None;pair=None
        if policy=='isolated_pending':
            m.require(candidate['classification']=='ISOLATED_PENDING_FINITE_CANDIDATE_FOR_ROOT_DECISION' and candidate['declinedReasons']==[] and candidate['directSelectedOwners'][0]['isolatedAllPendingDirectStaticOwner'] is True,'Declined/non-isolated candidate cannot be admitted: '+str(uid))
        elif policy=='visual_only_keep_members':
            m.require(policy=='visual_only_keep_members' and six is not None,'A visual-only route requires its exact six-case source proof')
            decor=next((case for case in six['cases'] if case['nativeC']['uuid']==uid and case['D']['uuid']==owner),None)
            m.require(decor and decor['classification']=='PASS_EXACT_THREE_D_MEMBERS_E_OVERLAPS_VISUAL_ONLY' and decor['failures']==[] and decor['EintersectsActualDMemberCells']==[],'Declined physical-member conflict cannot use a visual-only retention certificate')
        else:
            m.require(policy=='isolated_pair_keep_members' and four is not None,'Additional E owner requires an exact four-pair certificate')
            euid=row.get('additionalSelectedOwnerUuid')
            pair=next((case for case in four['cases'] if case['nativeC']['uuid']==uid and case['D']['uuid']==owner and case['E']['uuid']==euid),None)
            m.require(pair and euid not in seen_owner and pair['finiteERetentionWholeClippedFootprintIsolatedAndPending'] is True and pair['independentSelectedOwnerUuidsNotTraversed']==[],'Unknown or non-isolated additional E owner; no traversal is permitted')
            seen_owner.add(euid)
            m.require(pair['EcountsAfterExcludingOnlyExactDandE']['pendingCells']==18 and pair['EcountsAfterExcludingOnlyExactDandE']['independentSelectedFragmentCells']==0,'Finite E queue/demand proof did not pass')
        m.require(policy=='isolated_pair_keep_members' or 'additionalSelectedOwnerUuid' not in row,'An additional owner is forbidden outside the exact pair route')
        m.require(candidate['poseDataSha256']==report['poseDataSha256'] and candidate['syntheticLegacyUidMatches'] is True and candidate['sourcePaletteState']==report['requiredActualRootState'] and candidate['legacyRecipeIndex']==1161,'Candidate does not have the exact approved native pose/state identity')
        root=[candidate['nativeObject'][k] for k in ('x','y','z')]
        m.require(candidate['CmustRemainOriginalCells']==[root] and not candidate['nativeActivation'],'A candidate has different physical coverage or prior activation')
        direct=candidate['directSelectedOwners'][0];obj=direct['object'][0];geometry=type_rows.get(obj['pose'])
        m.require(geometry and geometry['actualClass'].endswith('ImportedRootBlock') and geometry['rootOnlyHypothesis'] is False,'Retained D must use its exact runtime member geometry, never root-only guesswork')
        members=[[obj['x']+cell['cell'][0],obj['y']+cell['cell'][1],obj['z']+cell['cell'][2]] for cell in geometry['runtimeCells']]
        fragment_points={(f['x'],f['y'],f['z']) for f in direct['fragments']}
        m.require({tuple(at) for at in members}<=fragment_points,'D has runtime members outside the proved visual/queue footprint')
        if decor:m.require(sorted(members)==sorted(decor['DmustRemainOriginalCells']),'Per-instance three-member proof disagrees with exact D type geometry')
        if pair:
            m.require({tuple(at) for at in members}=={tuple(cell['position']) for cell in pair['DexactRuntimeMembers']},'Pair D physical footprint differs from exact type geometry')
            m.require({tuple(cell['position']) for cell in pair['EruntimeMemberCells']}<={(f['x'],f['y'],f['z']) for f in pair['Efragments']},'E runtime member lies outside its bounded original visual/queue footprint')
        selected.append({**candidate,'_retentionPolicy':policy,'_runtimeMemberCells':members,'_physicalDecorProof':six_binding if decor else four_binding if pair else None,
                         '_decorDposeDataSha256':decor['DposeDataSha256'] if decor else pair['DposeDataSha256'] if pair else None,'_pair':pair})
    return scope,selected


def case_snapshot(m,db,candidate):
    native=candidate['nativeObject'];dim=native['dim'];uid=native['uuid']
    owner=candidate['directSelectedOwners'][0];duid=owner['uuid']
    native_saved={k:v for k,v in native.items() if k!='rootQueueProcessed'}
    m.require(row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,uid))==[native_saved],'Native object changed after the finite certificate')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(native['pose'],)).fetchone()
    m.require(pose and hashlib.sha256(pose[0].encode()).hexdigest()==candidate['poseDataSha256'],'Native pose bytes changed')
    m.require(row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,duid))==owner['object'],'Direct owner classification/root changed')
    dpose=db.execute('SELECT data FROM poses WHERE key=?',(owner['object'][0]['pose'],)).fetchone()
    m.require(dpose and ('sp_'+hashlib.sha256(dpose[0].encode()).hexdigest()[:24])==owner['object'][0]['pose'],'Direct owner exact pose bytes changed')
    if candidate['_decorDposeDataSha256']:m.require(hashlib.sha256(dpose[0].encode()).hexdigest()==candidate['_decorDposeDataSha256'],'Three-member per-instance D pose proof changed')
    m.require(row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',(dim,duid))==owner['selectedObject'],'Direct owner selection changed')
    m.require(row_dict(db,'SELECT x,y,z,pose,dx,dy,dz FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z',(dim,duid))==owner['fragments'],'Direct owner finite fragment rows changed')
    m.require(not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',(dim,uid)).fetchone(),'Native component already activated')
    cells=[]
    for saved in owner['finiteCellDemands']:
        args=(dim,*saved['position']);actual={'position':saved['position']}
        actual['queue']=row_dict(db,'SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args)
        actual['otherSelectedFragments']=row_dict(db,'SELECT uuid,pose FROM fragments WHERE dim=? AND x=? AND y=? AND z=? AND uuid!=?',(*args,duid))
        for table in ('substrate_fragments','native_visual_fragments','whole_cells','bridge_cells','protected_cells','native_visual_roots','placements'):
            actual[table]=row_dict(db,'SELECT * FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',args)
        m.require(actual==saved,'Finite owner pending/demand point changed: '+str(args))
        m.require(actual['queue']==[{'processed':0}] and all(not actual[t] for t in ('substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','placements')),'Owner has non-pending or newly independent native/whole demand')
        member=saved['position'] in candidate['_runtimeMemberCells']
        if candidate['_retentionPolicy'] in ('isolated_pending','isolated_pair_keep_members') or member:
            extra=candidate['_pair']['E']['uuid'] if candidate['_pair'] else None
            m.require(all(row['uuid']==extra for row in actual['otherSelectedFragments']),'Independent selected work intersects a retained actual D member')
        cells.append(actual)
    root=(native['x'],native['y'],native['z']);args=(dim,*root)
    m.require(db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone()==(0,),'Native physical member is no longer pending')
    for table,expected in candidate['rootOtherDemands'].items():
        m.require(row_dict(db,'SELECT * FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',args)==expected==[],'Independent physical-member demand changed')
    m.require(row_dict(db,'SELECT uuid,pose,dx,dy,dz FROM fragments WHERE dim=? AND x=? AND y=? AND z=?',args)==candidate['rootSelectedFragments'],'Physical root fragment identity changed')
    m.require(row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND x=? AND y=? AND z=?',args)==candidate['selectedRootsAtPhysicalMember'],'Physical root selected demand changed')
    for conflict in candidate['protectedNoBridgeVisualConflicts']:
        args=(dim,*conflict['position'])
        protected=row_dict(db,"SELECT uuid,reason FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND reason!='openable_both_states_local_helpers'",args)
        m.require(protected==conflict['protected'] and not db.execute('SELECT 1 FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),'The protected/no-bridge conflict identity changed')
    retained_owners=[{'dimension':dim,'uuid':duid,'object':owner['object'][0],'selected':owner['selectedObject'][0],
                      'fragments':owner['fragments'],'runtimeMemberCells':candidate['_runtimeMemberCells']}]
    if candidate['_pair']:retained_owners.append(extra_owner_snapshot(m,db,candidate['_pair'],duid))
    return {'selectedOwnerRetained':duid,'policy':candidate['_retentionPolicy'],'retainedOwners':retained_owners,
            'runtimeMemberCells':candidate['_runtimeMemberCells'],'physicalDecorProof':candidate['_physicalDecorProof'],
            'beforeLocal':{'object':owner['object'],'selected':owner['selectedObject'],'fragments':owner['fragments'],'cells':cells},
            'retainedNative':{'dimension':dim,'uuid':uid,'pose':native['pose'],'root':list(root),'mustRemainOriginalCells':[list(root)]},
            'fragmentRowsRemoved':len(owner['fragments']),
            'queueRowsPruned':sum(not cell['otherSelectedFragments'] for cell in cells),
            'bridgeRowsPruned':sum(bool(cell['bridge_cells']) and not cell['otherSelectedFragments'] for cell in cells)}


def extra_owner_snapshot(m,db,pair,duid):
    obj=pair['E'];dim=obj['dim'];uid=obj['uuid']
    m.require(row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,uid))==[obj],'Exact E classified object changed')
    m.require(row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid))==[pair['selectedE']],'Exact E selected object changed')
    m.require(row_dict(db,'SELECT x,y,z,pose,dx,dy,dz FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z',(dim,uid))==pair['Efragments'],'Exact E finite fragment footprint changed')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(obj['pose'],)).fetchone()
    m.require(pose and hashlib.sha256(pose[0].encode()).hexdigest()==pair['EposeDataSha256'],'Exact E pose bytes changed')
    members=[cell['position'] for cell in pair['EruntimeMemberCells']]
    for saved in pair['EfiniteDirectDemands']:
        args=(dim,*saved['position'])
        current={'position':saved['position'],'runtimeMember':saved['position'] in members,
                 'queue':row_dict(db,'SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args),
                 'independentSelectedFragmentsExcludingOnlyExactDandE':row_dict(db,'SELECT uuid,pose FROM fragments WHERE dim=? AND x=? AND y=? AND z=? AND uuid NOT IN (?,?)',(*args,duid,uid))}
        for table in ('substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','bridge_cells','placements','protected_cells'):
            current[table]=row_dict(db,'SELECT * FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',args)
        m.require(current==saved and current['queue']==[{'processed':0}] and current['independentSelectedFragmentsExcludingOnlyExactDandE']==[],'Exact E union point acquired an independent owner/demand')
        m.require(all(not current[table] for table in ('substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','placements')),'E union acquired native/whole/finalized demand')
    return {'dimension':dim,'uuid':uid,'object':obj,'selected':pair['selectedE'],
            'fragments':pair['Efragments'],'runtimeMemberCells':members}


def point_plan(m,db,cases):
    owners=[owner for case in cases for owner in case['retainedOwners']]
    identities={(owner['dimension'],owner['uuid']) for owner in owners}
    m.require(len(identities)==len(owners),'A scope cannot remove the same original owner twice')
    member_points={(owner['dimension'],*at) for owner in owners for at in owner['runtimeMemberCells']}
    native_points={(case['retainedNative']['dimension'],*at) for case in cases for at in case['retainedNative']['mustRemainOriginalCells']}
    points=sorted({(owner['dimension'],f['x'],f['y'],f['z']) for owner in owners for f in owner['fragments']})
    m.require(member_points|native_points<=set(points),'A retained actual member is outside the checked finite union')
    result=[]
    for args in points:
        other=[row for row in row_dict(db,'SELECT uuid,pose FROM fragments WHERE dim=? AND x=? AND y=? AND z=?',args) if (args[0],row['uuid']) not in identities]
        demands={'fragments':other}
        for table in ('substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots'):
            demands[table]=row_dict(db,'SELECT * FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',args)
        demanded=any(demands.values())
        m.require(not (args in member_points|native_points and demanded),'An independent owner/demand touches a retained actual member; scope expansion is forbidden')
        queue=row_dict(db,'SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args)
        m.require(queue==[{'processed':0}],'A scoped union point is no longer pending')
        result.append({'dimension':args[0],'position':list(args[1:]),'queue':queue,'remainingDemands':demands,
                       'bridge':row_dict(db,'SELECT * FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args),
                       'pruneQueueAndBridge':not demanded})
    return result


def plan_preview(m,db,plan,scope_path,scope_sha):
    require_unfinalized(m,db)
    scope,candidates=load_scope(m,db,scope_path,scope_sha)
    bindings,_,_,_=evidence(m,db,plan,scope)
    previous=m.meta(db,'nativeLocalRetention')
    if previous:validate_resume(m,db,plan)
    snapshots=[case_snapshot(m,db,row) for row in candidates]
    points=point_plan(m,db,snapshots)
    existing=m.meta(db,'nativeRetainedComponents') or []
    m.require(not {row['uuid'] for row in existing}&{row['retainedNative']['uuid'] for row in snapshots},'Already admitted component cannot reenter another scope')
    return {'schemaVersion':1,'status':'EXACT_LOCAL_RETENTION_PREVIEW','kind':'EXPLICIT_FINITE_NATIVE_RETENTION',
            'plan':str(plan),'evidence':bindings,'sourceBinding':m.meta(db,'binding'),
            'scope':ref(m,scope_path),'previousRetention':previous,'priorRetainedComponents':existing,
            'semanticBindings':snapshot_bindings(m,db),'beforeCounts':counts(db),'cases':snapshots,'pointPlan':points,
            'selectedOwnersRemoved':sum(len(case['retainedOwners']) for case in snapshots),
            'fragmentRowsRemoved':sum(len(owner['fragments']) for case in snapshots for owner in case['retainedOwners']),
            'queueRowsPruned':sum(point['pruneQueueAndBridge'] for point in points),
            'bridgeRowsPruned':sum(bool(point['bridge']) and point['pruneQueueAndBridge'] for point in points),
            'policy':'Keep exact legacy root/member and D original owners; no graph closure',
            'sourceFiles':{'migrate':m.sha(m.__file__),'nativeResume':m.sha(__file__)},
            'databaseWrites':0,'worldReads':0,'mapWrites':0}


def apply_local_delta(m,db,preview):
    """Called inside the single caller-owned transaction; no world operation."""
    cells=preview['beforeLocal']['cells']
    native=preview['retainedNative'];dim=native.get('dimension','.');duid=preview.get('selectedOwnerRetained',D);cuid=native.get('uuid',C)
    members=preview.get('runtimeMemberCells',[cell['position'] for cell in cells])
    db.execute('DELETE FROM fragments WHERE dim=? AND uuid=?',(dim,duid))
    db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',(dim,duid))
    for cell in cells:
        at=cell['position'];args=(dim,*at)
        # Retaining D's whole original model requires removing all D fragments,
        # but E's independent visual-only shared work remains in the queue.
        demands=[table for table in ('fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots') if db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=? LIMIT 1',args).fetchone()]
        m.require(not (at in members and demands),'A retained actual D member still has independent demand: '+str(demands))
        m.require(db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone()==(0,),'Only isolated pending rows may be pruned')
        if not demands:
            db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args)
            db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args)
        if at in members:db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(*args,duid,REASON))
    for at in preview['retainedNative']['mustRemainOriginalCells']:
        args=(dim,*at)
        m.require(not db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),'Retained legacy member still has replacement work')
        db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(*args,cuid,'native_legacy_member_retained_exact_proof'))
    root=preview['beforeLocal']['object'][0]
    m.issue(db,'native_local_retention',dim,(root['x'],root['y'],root['z']),
            {'uuid':duid,'nativeUuid':cuid,'reason':'Native visual overhang conflicts with a protected openable; preserve this one original static owner'})


def apply_scope_delta(m,db,preview):
    """Remove only the explicitly certified owners before pruning their union."""
    owners=[owner for case in preview['cases'] for owner in case['retainedOwners']]
    for owner in owners:
        args=(owner['dimension'],owner['uuid'])
        db.execute('DELETE FROM fragments WHERE dim=? AND uuid=?',args)
        db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',args)
    for point in preview['pointPlan']:
        args=(point['dimension'],*point['position'])
        current={'fragments':row_dict(db,'SELECT uuid,pose FROM fragments WHERE dim=? AND x=? AND y=? AND z=?',args)}
        for table in ('substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots'):
            current[table]=row_dict(db,'SELECT * FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',args)
        m.require(current==point['remainingDemands'],'A finite union acquired independent work during retention')
        expected_queue=(point['queue'][0]['processed'],) if point['queue'] else None
        m.require(db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone()==expected_queue,'Finite queue processed bits changed')
        m.require(expected_queue!=(1,) or any(current.values()),'A processed cell without remaining work cannot be pruned/reset')
        m.require(point['pruneQueueAndBridge']==(not any(current.values()) and bool(point['queue'])),'Finite queue pruning proof disagrees with actual remaining demand')
        if point['pruneQueueAndBridge']:
            db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args)
            db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args)
        if point.get('bridgeWrite'):
            bridge=point['bridgeWrite']
            db.execute('INSERT OR REPLACE INTO bridge_cells VALUES(?,?,?,?,?,?,NULL)',(*args,bridge['host_state'],bridge['retained_resident']))
    for case in preview['cases']:
        native=case['retainedNative']
        for owner in case['retainedOwners']:
            policies={tuple(p['position']):p['policy'] for p in owner.get('memberPolicies',[])}
            for at in owner['runtimeMemberCells']:
                args=(owner['dimension'],*at)
                if policies.get(tuple(at),'original')!='original':continue
                m.require(not db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),'A retained original D/E member still has replacement work')
                db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(*args,owner['uuid'],REASON))
            obj=owner['object']
            m.issue(db,'native_local_retention',owner['dimension'],(obj['x'],obj['y'],obj['z']),
                    {'uuid':owner['uuid'],'nativeUuid':native['uuid'],'policy':case['policy'],
                     'reason':'Keep this exact original whole source model and runtime members; omit all its compiled fragments'})
        for at in native['mustRemainOriginalCells']:
            args=(native['dimension'],*at)
            m.require(not db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),'Retained legacy native member still has replacement work')
            db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(*args,native['uuid'],'native_legacy_member_retained_exact_proof'))


def retain(a,m):
    plan=m.checked(a.plan)
    if not a.execute:
        db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+'?mode=ro',uri=True)
        try:
            db.execute('PRAGMA query_only=ON');db.execute('PRAGMA cache_size=-2048');db.execute('BEGIN')
            preview=plan_preview(m,db,plan,a.scope,a.scope_sha256)
            m.atomic_json(a.preview,preview)
        finally:db.close()
        return
    m.require(a.expected_preview_sha256 and m.sha(a.preview)==a.expected_preview_sha256,'Execute requires the exact reviewed preview SHA')
    wanted=m.read(a.preview);db=m.connect(plan)
    try:
        db.execute('BEGIN IMMEDIATE')
        receipt_path=plan/'full/native-retention-receipts'/(a.expected_preview_sha256+'.json')
        previous=m.meta(db,'nativeLocalRetention')
        if previous and read_bound(m,previous)['preview']['sha256']==a.expected_preview_sha256:
            receipt=read_bound(m,previous)
            m.require(receipt['preview']['sha256']==a.expected_preview_sha256 and receipt['preview']['path']==str(m.checked(a.preview)),'Another retention owns this phase')
            validate_resume(m,db,plan)
            db.rollback();return
        current=plan_preview(m,db,plan,a.scope,a.scope_sha256)
        m.require(current==wanted,'Reviewed finite snapshot/counts/bindings changed before execute')
        apply_scope_delta(m,db,current)
        after=counts(db);before=current['beforeCounts']
        expected={**before,'selected_objects':before['selected_objects']-current['selectedOwnersRemoved'],'fragments':before['fragments']-current['fragmentRowsRemoved'],
                  'cell_queue':before['cell_queue']-current['queueRowsPruned'],'bridge_cells':before['bridge_cells']-current['bridgeRowsPruned']}
        m.require(after==expected,'Local delta changed unrelated committed native work')
        m.require(snapshot_bindings(m,db)==current['semanticBindings'],'Selection-only retention changed classification/function/discovery evidence')
        retained=[case['retainedNative'] for case in current['cases']]
        receipt={'schemaVersion':1,'status':'PASS','kind':'EXPLICIT_FINITE_NATIVE_RETENTION','preview':ref(m,a.preview),
                 'sourceBinding':current['sourceBinding'],'semanticBindings':current['semanticBindings'],
                 'evidence':current['evidence'],'beforeCounts':before,'afterCounts':after,
                 'sourceFiles':current['sourceFiles'],
                 'scope':current['scope'],'previousRetention':current['previousRetention'],
                 'selectedOwnersRetained':[owner['uuid'] for case in current['cases'] for owner in case['retainedOwners']],
                 'retainedNative':retained,
                 'objectsClassificationUnchanged':True,'processedQueueRowsReset':0,'mapWrites':0,'worldReads':0}
        # Deterministic receipt before commit admits both rollback and after-commit
        # retry. It is not authoritative until the matching SQLite meta commits.
        if receipt_path.exists():m.require(m.read(receipt_path)==receipt,'Uncommitted retention receipt differs')
        else:m.atomic_json(receipt_path,receipt)
        binding=ref(m,receipt_path);m.meta(db,'nativeLocalRetention',binding)
        m.meta(db,'nativeRetainedComponents',current['priorRetainedComponents']+retained)
        cursor=m.meta(db,'nativeComposeCursor') or {'schemaVersion':1,'phase':'NATIVE_TRANSFER',
                                                   'batchesCommitted':0,'rowsProcessedSinceAdmission':0}
        m.require(cursor['phase']=='NATIVE_TRANSFER','Cannot append pending retention after native finalization')
        m.meta(db,'nativeComposeCursor',{**cursor,'retention':binding})
        db.commit()
        m.progress(plan,db,'NATIVE_TRANSFER_READY_TO_RESUME',nativeLocalRetention=binding)
    finally:db.close()


def validate_resume(m,db,plan,source_files_validator=None):
    cursor=m.meta(db,'nativeComposeCursor')
    m.require(cursor and cursor['phase'] in ('NATIVE_TRANSFER','FINALIZING'),'Native queue is not at an admitted resumable commit boundary')
    finalizing=cursor['phase']=='FINALIZING'
    require_unfinalized(m,db,finalizing=finalizing)
    binding=m.meta(db,'nativeLocalRetention');receipt=read_bound(m,binding)
    m.require(receipt['status']=='PASS' and receipt['kind']=='EXPLICIT_FINITE_NATIVE_RETENTION' and receipt['sourceBinding']==m.meta(db,'binding'),'Native resume lacks exact committed finite retention')
    m.require(snapshot_bindings(m,db)==receipt['semanticBindings'],'Native phase input bindings changed after admission')
    import native_certificates
    check_sources=source_files_validator or (lambda files:native_certificates.require_source_files(m,db,files))
    check_sources(receipt['sourceFiles'])
    archived=receipt['evidence']['failedSource']
    m.require(m.sha(archived['archive'])==archived['sha256'],'Failed native phase source archive changed')
    for bound in receipt['evidence'].values():
        if isinstance(bound,dict) and 'path' in bound and 'sha256' in bound:read_bound(m,bound) if Path(bound['path']).suffix=='.json' else m.require(m.sha(bound['path'])==bound['sha256'],'Native phase evidence changed')
    m.require(cursor['retention']==binding,'Native cursor and admitted receipt disagree')
    all_retained=[];current=receipt;seen=set()
    while current:
        m.require(current['status']=='PASS' and current['kind']=='EXPLICIT_FINITE_NATIVE_RETENTION' and current['sourceBinding']==receipt['sourceBinding'] and current['semanticBindings']==receipt['semanticBindings'],'Append-only retention receipt chain changed')
        check_sources(current['sourceFiles'])
        scope=read_bound(m,current['scope']);preview=read_bound(m,current['preview'])
        for name in ('candidateReport','physicalPattern','typeGeometryProof','physicalDecorProof','fourDecorProof','failedRun'):
            if scope.get(name):read_bound(m,scope[name])
        if scope.get('kind')=='FINITE_NATIVE_RETENTION_CERTIFICATE_V1':native_certificates.proof_files(m,scope)
        if scope.get('kind')=='FINITE_NATIVE_RETRACTION_CERTIFICATE_V1':native_certificates.retraction_proof_files(m,scope)
        if scope.get('kind')=='FINITE_PRESERVED_NATIVE_WHOLE_CERTIFICATE_V1':
            native_certificates.preserved_whole_proof_files(m,scope)
            native_certificates.validate_preserved_whole_state(m,db,scope,preview)
        m.require(preview['sourceBinding']==current['sourceBinding'],'Committed finite preview source changed')
        for case in preview['cases']:
            native=case['retainedNative'];dim=native['dimension'];uid=native['uuid']
            natives=[native]+case.get('retainedAdditionalNative',[])
            for member in natives:
                identity=(member['dimension'],member['uuid'])
                m.require(identity not in seen,'Duplicate native identity in retention chain');seen.add(identity);all_retained.append(member)
            for owner in case['retainedOwners']:
                owner_args=(owner['dimension'],owner['uuid'])
                m.require(not db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',owner_args).fetchone(),'Retained original owner reentered selection')
                m.require(not db.execute('SELECT 1 FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=?',owner_args).fetchone(),'Retained original owner has new fragment work')
                native_certificates.validate_owner_members(m,db,owner,source_files_validator=check_sources)
            for member in natives:
                dim=member['dimension'];uid=member['uuid']
                m.require(not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',(dim,uid)).fetchone(),'Retained legacy component was activated')
                for at in member['mustRemainOriginalCells']:
                    m.require(not db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone(),'Retained physical member has replacement work')
                    m.require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(dim,*at,uid)).fetchone(),'Retained physical member protection disappeared')
        previous=current.get('previousRetention');current=read_bound(m,previous) if previous else None
    m.require(sorted(all_retained,key=lambda r:(r['dimension'],r['uuid']))==sorted(m.meta(db,'nativeRetainedComponents') or [],key=lambda r:(r['dimension'],r['uuid'])),'Active retained identities differ from the committed certificate chain')
    native_certificates.validate_visual_protection_corrections(m,db,source_files_validator=check_sources)
    native_certificates.validate_whole_template_fallbacks(m,db,source_files_validator=check_sources)
    native_certificates.validate_retained_functional_visual_corrections(m,db,source_files_validator=check_sources)
    if not native_certificates.validate_source_visual_reservations(m,db,source_files_validator=check_sources):
        native_certificates.validate_source_local_retentions(m,db,source_files_validator=check_sources)
    if finalizing:m.require(not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone(),'Finalization cannot bypass pending native work')
    return receipt


def check_native_activation(m,db,dim,uid):
    if m.meta(db,'sourceLocalRetentionJournal'):
        m.require(not db.execute("SELECT 1 FROM objects o JOIN protected_cells p ON p.dim=o.dim AND p.x=o.x AND p.y=o.y AND p.z=o.z AND p.uuid=o.uuid WHERE o.dim=? AND o.uuid=? AND o.kind='substrate' AND p.reason=?",(dim,uid,__import__('native_certificates').LOCAL_ORIGINAL_REASON)).fetchone(),'An automatically retained original native cannot be activated again')
    if m.meta(db,'nativeLocalRetention'):
        retained=m.meta(db,'nativeRetainedComponents')
        # The fallback covers the original first-case synthetic fixture only;
        # production admission always stores the complete explicit finite set.
        identities={(row['dimension'],row['uuid']) for row in retained} if retained is not None else {('.',C)}
        m.require((dim,uid) not in identities,'The exact retained legacy C root/member cannot be replaced or transferred')


def checkpoint(m,db,phase,processed=0):
    cursor=m.meta(db,'nativeComposeCursor')
    if cursor:
        m.require(cursor['phase']=='NATIVE_TRANSFER' or cursor['phase']==phase=='FINALIZING','Native cursor cannot rewind a finalized phase')
        cursor={**cursor,'phase':phase,'batchesCommitted':cursor['batchesCommitted']+(phase=='NATIVE_TRANSFER'),
                'rowsProcessedSinceAdmission':cursor['rowsProcessedSinceAdmission']+processed}
        m.meta(db,'nativeComposeCursor',cursor)
        if phase=='FINALIZING' and m.meta(db,'nativeLocalRetention'):
            validate_resume(m,db,Path(db.execute('PRAGMA database_list').fetchone()[2]).parent)


def resume(a,m):
    plan=m.checked(a.plan);db=m.connect(plan)
    try:
        cursor=m.meta(db,'nativeComposeCursor');retention=m.meta(db,'nativeLocalRetention')
        boundary_only=bool(cursor and cursor['phase']=='NATIVE_TRANSFER' and retention and m.meta(db,'sourceVisualMemberIndex') and not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone())
        if boundary_only:
            require_unfinalized(m,db);__import__('native_certificates').active_binding(m,db);receipt=read_bound(m,retention)
            m.require(receipt['status']=='PASS' and receipt['kind']=='EXPLICIT_FINITE_NATIVE_RETENTION' and receipt['sourceBinding']==m.meta(db,'binding') and receipt['semanticBindings']==snapshot_bindings(m,db) and cursor['retention']==retention,'Completed native boundary inputs/retention changed')
        else:validate_resume(m,db,plan)
        if m.meta(db,'nativeComposeCursor')['phase']=='FINALIZING':
            restart_finalization(m,db)
        __import__('native_certificates').prepare_source_visual_member_index(m,db)
        policy='Own footprint only; no graph closure'
        local_overlap=sum(1 for row in db.execute("SELECT detail FROM issues WHERE kind='protected_local_overlap'") if json.loads(row[0]).get('policy')==policy)
        m.compose_native_tail(db,plan,None,local_overlap)
    finally:db.close()


def restart_finalization(m,db):
    """Discard only a bound interrupted derived tail, never native work."""
    m.require(not db.in_transaction,'Finalization restart requires its own atomic boundary')
    db.execute('BEGIN IMMEDIATE')
    try:
        require_unfinalized(m,db,finalizing=True)
        m.require(m.meta(db,'nativeComposeCursor')['phase']=='FINALIZING' and not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone(),'Only a completed native queue may rebuild interrupted derived tables')
        for table in ('placements','compositions','source_chunks'):db.execute('DELETE FROM '+table)
        for key in ('plannedBeSafety','compositionsSha256'):db.execute('DELETE FROM meta WHERE key=?',(key,))
        db.commit()
    except BaseException:db.rollback();raise
