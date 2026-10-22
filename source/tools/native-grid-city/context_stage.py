"""One bounded context extension of the existing, already written pilot.

Immutable authoring inputs remain the original read cache. Prior target cells
are admitted only by the small archived placement/bridge plan, never by an ID
wildcard. This module does not launch builds, games, or create another map.
"""
import collections,copy,hashlib,json,os,re,shutil,sqlite3,struct,zipfile
from pathlib import Path

HISTORY_TABLES=('selected_objects','placements','bridge_cells','whole_cells','compositions','cleanup_owners','regions','files','relight','source_chunks')
PILOT_CONTEXT_META=('contextExtension','contextRegionBinding','contextNativeFunctionGate','contextNativeFunctionDecisions','contextRequirePriorOwners','contextRuntimeEntryProof','contextLightAdoption','contextDependencyRepair','contextNativeLightRestart')

def dependency_repair(m,db):
    binding=m.meta(db,'contextDependencyRepair')
    if not binding:return None
    m.require(m.meta(db,'scope') and m.meta(db,'contextExtension') and m.sha(binding['path'])==binding['sha256'],'Pilot dependency repair binding changed')
    report=m.read(binding['path']);m.require(report['status']=='PASS' and report['kind']=='PRESERVING_FINITE_PILOT_DEPENDENCY_REPAIR' and report['sourceBinding']==m.meta(db,'binding') and report['originalSelectionDomainUnchanged']==427 and report['newOwnersRecursed'] is False,'Invalid finite pilot dependency repair')
    m.require(m.sha(report['inventory']['path'])==report['inventory']['sha256'] and m.sha(report['repairPlan'])==report['repairPlanSha256'],'Dependency repair provenance changed')
    return report

def native_light_restart(m,db):
    value=m.meta(db,'contextNativeLightRestart')
    if not value:return False
    repair=dependency_repair(m,db);m.require(repair and value['repair']==m.meta(db,'contextDependencyRepair') and value['oldSavedRowsInvalidated']==8 and repair['lightPolicy']['reuseSavedPrefix'] is False and repair['lightPolicy']['oldLightNowInvalid'] is True,'Fresh native light lacks its exact changed-dependency proof')
    return True

def leave_pilot_context(m,db):
    """Called only by the passed-pilot promotion; archives/tables remain."""
    for key in PILOT_CONTEXT_META:db.execute('DELETE FROM meta WHERE key=?',(key,))
    m.meta(db,'fullNativeFunctionGateRequired',True)

def import_full_functions(a,m):
    db=m.connect(a.plan)
    try:
        m.require(not m.meta(db,'scope') and m.meta(db,'successfulPilotGate') and m.meta(db,'fullNativeFunctionGateRequired') and not m.meta(db,'applyStarted'),'Import fresh full-city function evidence only after passed-pilot promotion')
        report=m.read(a.report);m.require(report.get('status')=='PASS' and report.get('scope')=='FULL_CITY' and report.get('mapWrites',0)==0 and report.get('sourceBinding')==m.meta(db,'binding'),'A fresh actual FULL_CITY source-bound function report is required; pilot evidence cannot substitute')
        evidence=report['inputEvidence'];m.require(m.sha(evidence['path'])==evidence['sha256'],'Full-city actual input evidence changed');actual=m.read(evidence['path'])
        candidates=db.execute("SELECT count(*) FROM objects WHERE kind IN ('static','whole')").fetchone()[0]
        m.require(actual.get('scope')=='FULL_CITY' and actual.get('sourceBinding')==m.meta(db,'binding') and actual.get('coverage',{}).get('candidateObjects')==candidates,'The function report does not cover the actual complete full-city candidate input set')
        decisions={}
        for row in report['states']:
            m.require(row['decision'] in ('static_base','bridge_host_only','retain_local'),'Unknown full native function decision');key=m.canonical(row['state']);m.require(key not in decisions,'Duplicate full native function state')
            decisions[key]={'decision':row['decision'],'reason':row.get('reason','actual_native_function_unproved')}
        m.require(decisions,'An empty full-city function map is not proof')
        m.require(isinstance(actual.get('nativeBaseCandidates'),list) and isinstance(actual.get('blockEntityHostCandidates'),list),'Actual full input evidence must include both finite base and BE-host groups')
        required={m.canonical(row['state']) for group in ('nativeBaseCandidates','blockEntityHostCandidates') for row in actual[group]};m.require(required<=set(decisions),'The full function report omitted actual native bases or existing BE hosts')
        m.require(all(decisions[m.canonical(row['state'])]['decision'] in ('bridge_host_only','retain_local') for row in actual['blockEntityHostCandidates']),'A real native BE host cannot be flattened as passive material')
        m.meta(db,'fullNativeFunctionGate',{'path':str(m.checked(a.report)),'sha256':m.sha(a.report),'states':len(decisions),'candidateObjects':candidates});m.meta(db,'fullNativeFunctionDecisions',decisions);db.commit()
    finally:db.close()

def require_full_functions(m,db):
    binding=m.meta(db,'fullNativeFunctionGate');m.require(m.meta(db,'fullNativeFunctionGateRequired') and binding and m.sha(binding['path'])==binding['sha256'],'Import a fresh actual FULL_CITY function gate before full composition/apply')
    report=m.read(binding['path']);m.require(report.get('scope')=='FULL_CITY' and report.get('status')=='PASS' and report.get('sourceBinding')==m.meta(db,'binding'),'A pilot function report cannot authorize full-city conversion')

def full_apply_gate(m,db,plan):
    require_full_functions(m,db);gate=_final_input_gate(m,db,plan,full=True);m.meta(db,'fullNativeInputCoverageGate',gate)

def attached_readonly(m,plan):
    db=sqlite3.connect('file:'+(m.checked(plan)/'plan.sqlite').as_posix()+'?mode=ro',uri=True)
    old=m.meta(db,'oldPlan');db.execute('ATTACH DATABASE ? AS old',('file:'+(Path(old)/'plan.sqlite').as_posix()+'?mode=ro',));return db

def allowed_copy(m,db):
    target=m.checked(m.meta(db,'target'));copy=m.read(target.parent/(target.name+'-copy.json'));original=m.meta(db,'binding')
    m.require(copy['status']=='PASS' and copy['sourceCopyManifestSha256']==original['copyManifestSha256'],'Context copy lost its immutable source binding')
    bounds=copy['bounds'];radius=copy['contextRadius'];core={(x,z) for x in range(bounds[0]>>4,(bounds[1]>>4)+1) for z in range(bounds[2]>>4,(bounds[3]>>4)+1)}
    main={(x+dx,z+dz) for x,z in core for dx in range(-radius,radius+1) for dz in range(-radius,radius+1)}
    dependencies=set(db.execute("SELECT cx,cz FROM source_chunks WHERE dim='.'"))
    extra=m.read(m.ROOT/'research/function-qa/satellites.json');dependencies.update((r['x'],r['z']) for r in extra['chunks'])
    allowed=main|{(x+dx,z+dz) for x,z in dependencies for dx in (-1,0,1) for dz in (-1,0,1)}
    m.require(m.digest(sorted(allowed))==copy['selectedChunkSetSha256'] and len(main)==400 and len(allowed)==427,'The fixed pilot copy chunk set changed')
    return target,copy,main,allowed

def saved_light(m,db,plan):
    queue=(plan/'relight-queue.tsv').read_bytes();cursor=(plan/'relight-engine.json.done.tsv').read_bytes();lines=cursor.splitlines(keepends=True)
    runtime=m.meta(db,'runtime');qh=hashlib.sha256(queue).hexdigest();m.require(cursor.endswith(b'\n') and lines[0].rstrip()==('# '+qh+' '+runtime['sha256']).encode(),'The durable native light cursor/header changed')
    saved=[v.decode().rstrip('\r\n') for v in lines[1:]];queued=[v for v in queue.decode().splitlines() if v and not v.startswith('#')]
    m.require(saved==queued[:len(saved)] and len(saved)==8,'Only the eight actually saved satellite chunks are authorized for continuation')
    transition=m.meta(db,'runtimeStorageTransition');m.require(transition and m.sha(transition['path'])==transition['sha256'],'Archived successful native-save adoption proof is missing')
    evidence=m.read(transition['path']);proof=evidence['adoptedNativeSavedCursor']
    m.require(evidence['newRuntime']==runtime and proof['processed']==len(saved) and proof['rowsSha256']==hashlib.sha256(b''.join(lines[1:])).hexdigest(),'The eight rows differ from their original saveFlushed=true proof')
    return {'queueSha256':qh,'cursorSha256':hashlib.sha256(cursor).hexdigest(),'rowsSha256':proof['rowsSha256'],'savedRows':saved,'runtime':runtime,'nativeSavedEvidence':transition,'dependencyRadius':11}

def inventory(a,m):
    plan=m.checked(a.plan);db=attached_readonly(m,plan)
    try:
        m.require(m.meta(db,'offlineApplied') and not m.meta(db,'relightComplete') and not m.meta(db,'contextExtension'),'Only one incomplete applied pilot may extend its existing context')
        target,copy,main,allowed=allowed_copy(m,db)
        db.execute('CREATE TEMP TABLE allowed(cx INTEGER,cz INTEGER,main INTEGER,PRIMARY KEY(cx,cz))');db.executemany('INSERT INTO allowed VALUES(?,?,?)',[(x,z,int((x,z) in main)) for x,z in sorted(allowed)])
        db.execute('CREATE TEMP TABLE candidates(dim TEXT,uuid TEXT,pose TEXT,x INTEGER,y INTEGER,z INTEGER,kind TEXT,PRIMARY KEY(dim,uuid))')
        db.execute("INSERT INTO candidates SELECT o.dim,o.uuid,o.pose,o.x,o.y,o.z,o.kind FROM objects o JOIN allowed a ON a.cx=o.x>>4 AND a.cz=o.z>>4 WHERE o.dim='.' AND a.main=1 AND o.kind IN ('static','whole')")
        db.execute('INSERT OR IGNORE INTO candidates SELECT * FROM selected_objects')
        db.execute('CREATE TEMP TABLE invalid(dim TEXT,uuid TEXT,reason TEXT,PRIMARY KEY(dim,uuid,reason))')
        db.execute("INSERT OR IGNORE INTO invalid SELECT c.dim,c.uuid,'missing_exported_footprint' FROM candidates c WHERE NOT EXISTS(SELECT 1 FROM pose_cells p WHERE p.pose=c.pose)")
        db.execute("INSERT OR IGNORE INTO invalid SELECT c.dim,c.uuid,'pose_footprint_outside_copied_pilot' FROM candidates c JOIN pose_cells p ON p.pose=c.pose LEFT JOIN allowed a ON a.cx=(c.x+p.dx)>>4 AND a.cz=(c.z+p.dz)>>4 WHERE a.cx IS NULL")
        for table in ('stored_contributions','members'):
            db.execute("INSERT OR IGNORE INTO invalid SELECT t.dim,t.uuid,'saved_membership_outside_copied_pilot' FROM old."+table+" t CROSS JOIN candidates c ON c.dim=t.dim AND c.uuid=t.uuid LEFT JOIN allowed a ON a.cx=t.x>>4 AND a.cz=t.z>>4 WHERE t.dim='.' AND a.cx IS NULL")
        m.require(not db.execute('SELECT 1 FROM selected_objects s JOIN invalid i ON i.dim=s.dim AND i.uuid=s.uuid LIMIT 1').fetchone(),'A prior committed source owner would be lost at the context boundary')
        directory=plan/'context-extension';directory.mkdir(exist_ok=True);eligible=directory/'eligible-owners.jsonl';temporary=eligible.with_suffix('.writing');count=0;routes=collections.Counter()
        with temporary.open('w',encoding='utf-8',newline='\n') as stream:
            for dim,uid,pose,x,y,z,kind in db.execute('SELECT c.* FROM candidates c WHERE NOT EXISTS(SELECT 1 FROM invalid i WHERE i.dim=c.dim AND i.uuid=c.uuid) ORDER BY c.dim,c.uuid'):
                stream.write(m.canonical({'dimension':dim,'uuid':uid,'pose':pose,'root':[x,y,z],'kind':kind})+'\n');count+=1;routes[kind]+=1
            stream.flush();os.fsync(stream.fileno())
        os.replace(temporary,eligible)
        boundary=[{'dimension':d,'uuid':u,'reason':r} for d,u,r in db.execute('SELECT dim,uuid,reason FROM invalid ORDER BY dim,uuid,reason')];m.atomic_json(directory/'boundary-retentions.json',{'objects':len({(r['dimension'],r['uuid']) for r in boundary}),'reasons':dict(collections.Counter(r['reason'] for r in boundary)),'entries':boundary})
        old_catalog=m.meta(db,'catalog');m.require(m.sha(old_catalog['path'])==old_catalog['sha256'] and m.sha(Path(old_catalog['path']).with_name('meshes.bin'))==old_catalog['meshesSha256'],'The compiled pilot prefix changed')
        report={'schemaVersion':1,'status':'DRY_RUN','kind':'SAME_PILOT_CONTEXT_EXTENSION','target':str(target),'sourceCopyManifestSha256':copy['sourceCopyManifestSha256'],'originalCopyReceipt':str(target.parent/(target.name+'-copy.json')),'originalCopyReceiptSha256':m.sha(target.parent/(target.name+'-copy.json')),
                'allowedChunkSetSha256':copy['selectedChunkSetSha256'],'allowedChunks':[list(p) for p in sorted(allowed)],'mainChunks':[list(p) for p in sorted(main)],
                'rootBounds':[min(x for x,z in main)*16,max(x for x,z in main)*16+15,min(z for x,z in main)*16,max(z for x,z in main)*16+15],
                'eligibleOwners':{'path':str(eligible),'sha256':m.sha(eligible),'objects':count,'routes':dict(routes)},'specificBoundaryRetentions':{'path':str(directory/'boundary-retentions.json'),'sha256':m.sha(directory/'boundary-retentions.json'),'objects':len({(r['dimension'],r['uuid']) for r in boundary})},
                'parentPlanFingerprint':m.storage_plan_fingerprint(db),'parentCatalog':old_catalog,'parentRuntime':m.meta(db,'runtime'),'nativeSavedLight':saved_light(m,db,plan),'oneGlobalDatabase':True,'newMapCopies':0,'mapReads':0,'mapWrites':0}
        m.atomic_json(directory/'inventory.json',report);print(m.canonical({'status':'DRY_RUN','inventory':str(directory/'inventory.json'),'eligible':count,'boundaryRetained':report['specificBoundaryRetentions']['objects']}))
    finally:db.close()

def stage(a,m):
    plan=m.checked(a.plan);inv=m.read(a.inventory);db=m.connect(plan)
    try:
        old=m.meta(db,'contextExtension')
        if old:
            m.require(m.sha(old['path'])==old['sha256'],'Committed stage receipt changed');receipt=m.read(old['path']);m.require(receipt['inventorySha256']==m.sha(a.inventory),'Another context extension already owns this pilot')
            m.atomic_json(plan/'context-extension'/'stage-intent.json',{'schemaVersion':1,'phase':'PREPARED','inventorySha256':m.sha(a.inventory),'receipt':old['path']});return
        m.require(inv['status']=='DRY_RUN' and inv['kind']=='SAME_PILOT_CONTEXT_EXTENSION' and inv['target']==m.meta(db,'target'),'Context extension inventory belongs to another pilot')
        m.require(m.meta(db,'offlineApplied') and not m.meta(db,'relightComplete') and m.storage_plan_fingerprint(db)==inv['parentPlanFingerprint'],'The applied pilot changed after extension inventory')
        target,copy,main,allowed=allowed_copy(m,db)
        m.require(inv['target']==str(target) and inv['allowedChunks']==[list(p) for p in sorted(allowed)] and inv['mainChunks']==[list(p) for p in sorted(main)] and inv['allowedChunkSetSha256']==copy['selectedChunkSetSha256'],'Inventory would change the existing copied pilot scope')
        m.require(m.sha(inv['originalCopyReceipt'])==inv['originalCopyReceiptSha256'],'The original independent copy receipt changed')
        for key in ('eligibleOwners','specificBoundaryRetentions'):
            m.require(m.sha(inv[key]['path'])==inv[key]['sha256'],'Context inventory decisions changed')
        m.require(m.meta(db,'catalog')==inv['parentCatalog'] and m.meta(db,'runtime')==inv['parentRuntime'],'Pilot runtime/catalogue changed after inventory')
        m.require(saved_light(m,db,plan)==inv['nativeSavedLight'],'Native saved light cursor changed after inventory')
        history=plan/'stage-history'/'stage-001';history.mkdir(parents=True,exist_ok=True)
        source=m.checked(plan/'journals');destination=m.checked(history/'journals');intent_path=plan/'context-extension'/'stage-intent.json'
        def journal_manifest(directory):return {p.relative_to(directory).as_posix():m.sha(p) for p in m.files(directory)}
        if intent_path.exists():
            intent=m.read(intent_path);m.require(intent['phase']=='PREPARING' and intent['inventorySha256']==m.sha(a.inventory) and intent['parentPlanFingerprint']==inv['parentPlanFingerprint'],'Interrupted stage belongs to another inventory/parent plan')
            m.require(source.exists()!=destination.exists(),'Interrupted stage must have exactly one original journal directory')
            m.require(journal_manifest(source if source.exists() else destination)==intent['journalManifest'],'Original atomic journal bytes changed during stage preparation')
        else:
            m.require(source.exists() and not destination.exists(),'Only an intact original journal directory may start a context stage')
            intent={'schemaVersion':1,'phase':'PREPARING','inventorySha256':m.sha(a.inventory),'parentPlanFingerprint':inv['parentPlanFingerprint'],'journalManifest':journal_manifest(source)};m.atomic_json(intent_path,intent)
        saved_meta={k:json.loads(v) for k,v in db.execute('SELECT key,value FROM meta')}
        if (history/'meta.json').exists():m.require(m.read(history/'meta.json')==saved_meta,'Immutable prior-stage metadata archive changed')
        else:m.atomic_json(history/'meta.json',saved_meta)
        for name in ('frozen-inputs.json','relight-binding.json','relight-queue.tsv','relight-engine.json','relight-engine.json.done.tsv','relight-launch-binding.json','relight-launch-receipt.json','relight-server-console.log','applied-journal-report.json'):
            src=plan/name;dest=history/name
            if src.exists():
                if dest.exists():m.require(m.sha(dest)==m.sha(src),'Prior stage evidence archive changed: '+name)
                else:
                    with src.open('rb') as inp,dest.open('wb') as out:shutil.copyfileobj(inp,out);out.flush();os.fsync(out.fileno())
        for src,dest in ((Path(inv['parentCatalog']['path']),history/'catalog.json'),(Path(inv['parentCatalog']['path']).with_name('meshes.bin'),history/'meshes.bin'),(Path(inv['parentRuntime']['path']),history/'production.jar')):
            if dest.exists():m.require(m.sha(dest)==m.sha(src),'Prior compiled stage archive changed')
            else:
                with src.open('rb') as inp,dest.open('wb') as out:shutil.copyfileobj(inp,out);out.flush();os.fsync(out.fileno())
        m.require(m.sha(history/'production.jar')==inv['parentRuntime']['sha256'],'Actual applied production JAR was not preserved')
        db.execute('BEGIN IMMEDIATE')
        for table in HISTORY_TABLES:db.execute('CREATE TABLE stage1_'+table+' AS SELECT * FROM '+table)
        for table in ('placements','bridge_cells','whole_cells'):db.execute('CREATE UNIQUE INDEX stage1_'+table+'_pos ON stage1_'+table+'(dim,x,y,z)')
        db.execute('CREATE UNIQUE INDEX stage1_cleanup_uid ON stage1_cleanup_owners(dim,uuid)')
        for statement in ('CREATE TABLE context_allowed_chunks(cx INTEGER,cz INTEGER,PRIMARY KEY(cx,cz))','CREATE TABLE context_allowed_owners(dim TEXT,uuid TEXT,PRIMARY KEY(dim,uuid))','CREATE TABLE context_changed_chunks(dim TEXT,cx INTEGER,cz INTEGER,PRIMARY KEY(dim,cx,cz))'):db.execute(statement)
        db.executemany('INSERT INTO context_allowed_chunks VALUES(?,?)',inv['allowedChunks'])
        with Path(inv['eligibleOwners']['path']).open(encoding='utf-8') as stream:
            db.executemany('INSERT INTO context_allowed_owners VALUES(?,?)',((r['dimension'],r['uuid']) for r in map(json.loads,stream)))
        if source.exists():m.require(not destination.exists(),'Both previous-stage journal directories exist');os.replace(source,destination)
        m.require(destination.exists() and journal_manifest(destination)==intent['journalManifest'],'Previous atomic map journals are missing or changed')
        receipt={'schemaVersion':1,'status':'CONTEXT_STAGE_PREPARED','kind':inv['kind'],'inventory':str(m.checked(a.inventory)),'inventorySha256':m.sha(a.inventory),'target':inv['target'],'allowedChunkSetSha256':inv['allowedChunkSetSha256'],'allowedChunks':inv['allowedChunks'],'rootBounds':inv['rootBounds'],'history':str(history),
                 'parentTables':{t:'stage1_'+t for t in HISTORY_TABLES},'parentCatalog':{'path':str(history/'catalog.json'),'sha256':m.sha(history/'catalog.json'),'meshesSha256':m.sha(history/'meshes.bin')},'parentRuntime':{'path':str(history/'production.jar'),'sha256':m.sha(history/'production.jar')},'nativeSavedLight':inv['nativeSavedLight'],'mapWrites':0}
        receipt['parentPlanFingerprint']=inv['parentPlanFingerprint'];receipt['journalManifestSha256']=m.digest(intent['journalManifest'])
        if (history/'context-extension-receipt.json').exists():m.require(m.read(history/'context-extension-receipt.json')==receipt,'Immutable existing stage receipt changed before SQLite commit')
        else:m.atomic_json(history/'context-extension-receipt.json',receipt)
        m.meta(db,'contextExtension',{'path':str(history/'context-extension-receipt.json'),'sha256':m.sha(history/'context-extension-receipt.json')})
        for table in ('selected_objects','whole_cells','fragments','substrate_fragments','native_visual_roots','native_visual_fragments','bridge_cells','cell_queue','activated_native','placements','compositions','cleanup_owners','regions','source_chunks','relight','files','issues'):db.execute('DELETE FROM '+table)
        for key in ('applyStarted','offlineApplied','regionPhaseComplete','relightComplete','runtime','catalog','compositionsSha256','frozenInputs','bridgeCarrierGate','bridgeHostStates','cleanupBeAudit','runtimeStorageTransition','pilotRegionBinding'):db.execute('DELETE FROM meta WHERE key=?',(key,))
        scope=dict(saved_meta['scope']);scope['rootBounds']=inv['rootBounds'];m.meta(db,'scope',scope);m.meta(db,'contextRequirePriorOwners',True);db.commit()
        m.atomic_json(plan/'context-extension'/'stage-intent.json',{'schemaVersion':1,'phase':'PREPARED','inventorySha256':m.sha(a.inventory),'receipt':str(history/'context-extension-receipt.json')});m.progress(plan,db,'CONTEXT_STAGE_PREPARED')
    finally:db.close()

def receipt(m,db):
    binding=m.meta(db,'contextExtension')
    if not binding:return None
    m.require(m.sha(binding['path'])==binding['sha256'],'Context extension receipt changed');return m.read(binding['path'])

def filter_selection(m,db):
    if not m.meta(db,'contextExtension'):return
    db.execute('DELETE FROM selected_objects WHERE NOT EXISTS(SELECT 1 FROM context_allowed_owners a WHERE a.dim=selected_objects.dim AND a.uuid=selected_objects.uuid)')

def check_complete(m,db):
    if not m.meta(db,'contextExtension'):return
    m.require(not db.execute('SELECT 1 FROM stage1_selected_objects old LEFT JOIN selected_objects n ON n.dim=old.dim AND n.uuid=old.uuid WHERE n.uuid IS NULL LIMIT 1').fetchone(),'Expanded composition lost a previously converted complete source object')
    m.require(not db.execute('SELECT 1 FROM stage1_placements old LEFT JOIN placements n ON n.dim=old.dim AND n.x=old.x AND n.y=old.y AND n.z=old.z WHERE n.x IS NULL LIMIT 1').fetchone(),'Expanded composition lost a previously compiled cell')
    m.require(not db.execute('SELECT 1 FROM placements p LEFT JOIN context_allowed_chunks a ON a.cx=p.cx AND a.cz=p.cz WHERE a.cx IS NULL LIMIT 1').fetchone(),'A composition exceeds the same authorized copied pilot chunk set')

def catalog_prefix(m,db,catalog_path):
    old=receipt(m,db)
    if not old:return
    m.require(m.sha(old['parentCatalog']['path'])==old['parentCatalog']['sha256'] and m.sha(Path(old['parentCatalog']['path']).with_name('meshes.bin'))==old['parentCatalog']['meshesSha256'],'Archived immutable catalogue/mesh prefix changed')
    previous=m.read(old['parentCatalog']['path']);current=m.read(catalog_path)
    for key in ('materials','shapes','optics','meshes','cells','wholeObjects'):m.require(current[key][:len(previous[key])]==previous[key],'Context catalogue changed a prior '+key+' index')
    before=Path(old['parentCatalog']['path']).with_name('meshes.bin').read_bytes();after=Path(catalog_path).with_name('meshes.bin').read_bytes()
    m.require(before[:4]==after[:4]==b'NGQ1' and struct.unpack('>i',after[4:8])[0]>=struct.unpack('>i',before[4:8])[0] and after[8:len(before)]==before[8:],'Context compiler changed prior binary mesh payload bytes')

_CARRIER_CACHE={}
def carrier_predicates(m,db,dim,pos):
    extension=receipt(m,db)
    if not extension:return None
    key=extension['history']
    if key not in _CARRIER_CACHE:
        previous=m.read(Path(key)/'meta.json');binding=previous['bridgeCarrierGate'];m.require(m.sha(binding['path'])==binding['sha256'],'Actual prior carrier evidence changed')
        audit=m.read(binding['path']);m.require(audit['status']=='PASS' and audit['nativeRegistryQueries'] is True,'Prior actual carrier query proof missing')
        states={}
        for row in audit['results']:
            state=m.canonical(row['input']['state']);flags={k:row[k] for k in ('opaque','sidedTransparency')};m.require(all(type(v)is bool for v in flags.values()),'Actual carrier worldless predicates are not boolean')
            m.require(state not in states or states[state]==flags,'Inconsistent actual carrier predicates');states[state]=flags
        _CARRIER_CACHE[key]=states
    host=db.execute('SELECT host_state FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',(dim,*pos)).fetchone()
    return _CARRIER_CACHE[key].get(host[0]) if host else None

def import_functions(a,m):
    db=m.connect(a.plan)
    try:
        m.require(m.meta(db,'contextExtension') and not m.meta(db,'applyStarted'),'Finite functional input is pre-compose/pre-apply context evidence')
        report=m.read(a.report);m.require(report.get('status')=='PASS' and report.get('mapWrites',0)==0,'Finite native functional report must be complete')
        rows=report.get('states',report.get('decisions',[]));m.require(isinstance(rows,list),'Finite actual state decisions required')
        decisions={}
        for row in rows:
            state=row.get('state',{'registryId':row.get('registryId'),'properties':row.get('properties',{})});decision=row.get('decision')
            m.require(state.get('registryId') and decision in ('static_base','bridge_host_only','retain_local'),'Unknown native functional decision contract')
            decisions[m.canonical(state)]={'decision':decision,'reason':row.get('reason','exact_native_function_unproved')}
        m.meta(db,'contextNativeFunctionGate',{'path':str(m.checked(a.report)),'sha256':m.sha(a.report),'states':len(decisions)})
        m.meta(db,'contextNativeFunctionDecisions',decisions);db.commit()
    finally:db.close()

def native_function_conflicts(m,db,dim,cx,cz,sections,root):
    prefix='context' if m.meta(db,'contextExtension') else 'full'
    binding=m.meta(db,prefix+'NativeFunctionGate');m.require(binding and m.sha(binding['path'])==binding['sha256'],'Import the finite actual native-function gate before composition')
    decisions=m.meta(db,prefix+'NativeFunctionDecisions');entities={tuple(m.compound(tag)[k].value for k in ('x','y','z')):m.compound(tag) for tag in root.get('block_entities',m.Tag(9,[],10)).value}
    for x,y,z in db.execute('SELECT DISTINCT x,y,z FROM fragments WHERE dim=? AND x>>4=? AND z>>4=?',(dim,cx,cz)):
        at=(x,y,z);state=m.source_state(sections,at);rid=state['registryId']
        if rid in ('minecraft:air','minecraft:cave_air','minecraft:void_air'):continue
        entity=entities.get(at)
        if entity and 'resident' in entity and db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',(dim,m.owner_uuid(entity['resident']))).fetchone():continue
        if logical_selected_carrier(m,db,dim,at,state):continue
        # A selected native synthetic SourcePose is still a substrate. It must
        # pass this exact-state gate; a decorative SourcePose name is not proof
        # that an independent native block has no gameplay functionality.
        decision=decisions.get(m.canonical(state))
        if decision and decision['decision']=='static_base':continue
        if decision and decision['decision']=='bridge_host_only' and db.execute('SELECT 1 FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone():continue
        uids=[r[0] for r in db.execute('SELECT uuid FROM fragments WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at))]
        yield at,{'reason':'native_functional_base_retained','registryId':rid,'properties':state.get('properties',{}),'specificReason':decision['reason'] if decision else 'actual_native_state_missing_function_proof'},uids

def logical_selected_carrier(m,db,dim,at,state):
    """Match compose's logical-AIR gate; a registry name alone is no proof."""
    rid=state['registryId']
    generic=rid.startswith('bloodborne_dw:static_architecture_') or rid in ('bloodborne_dw:composite_cell','bloodborne_dw:composite_fragment','bloodborne_dw:source_door_fragment','bloodborne_dw:source_compatibility_fragment')
    if not generic:return False
    if db.execute("SELECT 1 FROM objects WHERE dim=? AND x=? AND y=? AND z=? AND kind='substrate' LIMIT 1",(dim,*at)).fetchone():return False
    for table in ('members','stored_contributions'):
        if db.execute('SELECT 1 FROM old.'+table+' c JOIN selected_objects s ON s.dim=c.dim AND s.uuid=c.uuid WHERE c.dim=? AND c.x=? AND c.y=? AND c.z=? LIMIT 1',(dim,*at)).fetchone():return True
    return False

def grid_state(index):return {'registryId':'bloodborne_dw:native_grid_'+str(index//128),'properties':{'part':str(index%128)}}

def _sections(m,root):
    sections={}
    for tag in root.get('sections',m.Tag(9,[],10)).value:
        blocks=m.section_blocks(tag)
        if blocks is not None:sections[m.compound(tag)['Y'].value]=blocks
    return sections

_CLEANUP_CACHE={}
def _cleanup_ids(db,prefix):
    key=(db,prefix);count=db.execute('SELECT count(*) FROM '+prefix+'cleanup_owners').fetchone()[0]
    if key not in _CLEANUP_CACHE or _CLEANUP_CACHE[key][0]!=count:_CLEANUP_CACHE[key]=(count,set(db.execute('SELECT dim,uuid FROM '+prefix+'cleanup_owners')))
    return _CLEANUP_CACHE[key][1]

def _content(m,db,dim,cx,cz,source_root,prefix):
    """Reconstruct content only from immutable inputs and an exact stage table.

    Never mutates cached source NBT, and never copies light/heightmap metadata.
    No registry wildcard can admit a target NativeGrid state or background.
    """
    changes={};kept=[];gone=_cleanup_ids(db,prefix)
    writes=db.execute('SELECT x,y,z,type_index FROM '+prefix+'placements WHERE dim=? AND cx=? AND cz=?',(dim,cx,cz)).fetchall()
    bridges={(x,y,z):(json.loads(host),index) for x,y,z,host,index in db.execute('SELECT x,y,z,host_state,type_index FROM '+prefix+'bridge_cells WHERE dim=? AND x>>4=? AND z>>4=?',(dim,cx,cz))}
    for x,y,z,index in writes:
        m.require(type(index)is int and index>=0,'Uncompiled stage placement');changes[(x,y,z)]=grid_state(index)
    for original in source_root.get('block_entities',m.Tag(9,[],10)).value:
        tag=copy.deepcopy(original);be=m.compound(tag);at=tuple(be[k].value for k in ('x','y','z'));owners=m.be_owners(be)
        retained=[v for v in owners if (dim,m.owner_uuid(m.compound(v)['owner'])) not in gone]
        resident_removed='resident' in be and (dim,m.owner_uuid(be['resident'])) in gone
        if resident_removed:be.pop('resident');be['payload']=m.Tag(10,{})
        if len(retained)!=len(owners):
            be.pop('owners',None);be['OwnerDataV1']=m.Tag(7,m.encode_nbt(m.NbtFile('',m.Tag(10,{'owners':m.Tag(9,retained,10)}))))
        if at in bridges:
            host,index=bridges[at]
            m.require(at in changes and type(index)is int,'Bridge placement has no exact index')
            m.require(be.get('id',m.Tag(8,'')).value=='bloodborne_dw:composite_cell' and (retained or 'resident' in be),'NativeGridBase must have an existing retained CompositeBE')
            be['NativeGridBase']=m.Tag(3,index);changes[at]=host;kept.append(tag)
        elif len(retained)==len(owners) and not resident_removed:
            m.require(at not in changes,'A stage placement would overwrite a retained BlockEntity: '+str((dim,at)));kept.append(tag)
        elif retained or 'resident' in be:
            m.require(at not in changes,'A stage placement would erase a retained owner: '+str((dim,at)));kept.append(tag)
        else:
            if at not in changes:changes[at]=m.AIR
    return changes,kept

def _typed(m,tag):
    if tag.type==10:return [10,[[k,_typed(m,v)] for k,v in sorted(tag.value.items())]]
    if tag.type==9:return [9,tag.list_type,[_typed(m,v) for v in tag.value]]
    if tag.type==7:return [7,bytes(tag.value).hex()]
    return [tag.type,tag.value]

def _be_value(m,tag):
    fields=dict(m.compound(tag));fields.pop('keepPacked',None)
    # Only these two wire representations of the SAME typed owner list are
    # normalized. All unknown game fields and original payload stay in proof.
    if 'OwnerDataV1' in fields or 'owners' in fields:
        values=m.be_owners(fields);fields.pop('OwnerDataV1',None);fields.pop('owners',None)
        fields['owners']=m.Tag(9,sorted(values,key=lambda v:m.canonical(_typed(m,v))),10)
        if fields.get('id',m.Tag(8,'')).value=='bloodborne_dw:composite_cell' and 'payload' not in fields:fields['payload']=m.Tag(10,{})
    return _typed(m,m.Tag(10,fields))

def _bes_digest(m,tags):
    return m.digest(sorted((_be_value(m,t) for t in tags),key=m.canonical))

_COMPARISON_IDS=None
_COMPARISON_CAPABILITIES_SHA256='4631f03fe354733a230dab9d5bbffc339a3cbf3fefb9362544199569685dc461'

def _comparison_ids(m):
    global _COMPARISON_IDS
    if _COMPARISON_IDS is None:
        path=m.DEFAULT_OLD.parents[2]/'tools/ordinary-stage1/source-capabilities.json'
        m.require(m.sha(path)==_COMPARISON_CAPABILITIES_SHA256,'The exact native-registry default-state capability proof changed')
        _COMPARISON_IDS=frozenset(row['registryId'] for row in m.read(path)['states'] if row.get('registryPresent') is True and row.get('className')=='dev.dreamwalker.bloodbornedw.composite.StaticArchitectureBlock' and row.get('defaultProperties')=={'part':'0','waterlogged':'false'} and row.get('stateCount')==256 and row.get('anyStateHasBlockEntity') is False)
        m.require(len(_COMPARISON_IDS)==256,'The finite registered StaticArchitecture default-state proof changed')
    return _COMPARISON_IDS

def _comparison_state(m,state):
    # StaticArchitectureBlock's constructor sets WATERLOGGED=false. The native
    # loader writes that default when old NBT omitted it. Normalize only this
    # finite registered class for comparison; never alter palettes or states.
    rid=state['registryId'];properties=state.get('properties',{})
    if rid in _comparison_ids(m) and set(properties)=={'part'} and re.fullmatch(r'0|[1-9][0-9]*',properties['part']) and int(properties['part'])<128:
        return {'registryId':rid,'properties':dict(properties,waterlogged='false')}
    return state

def _states_digest(m,sections,changes=None):
    changes=changes or {};sections=dict(sections);by_section=collections.defaultdict(dict)
    for at,state in changes.items():by_section[at[1]>>4][(at[0]&15)+16*(at[2]&15)+256*(at[1]&15)]=m.canonical(_comparison_state(m,state))
    result=hashlib.sha256();air=m.canonical(m.AIR)
    for sy in sorted(set(sections)|set(by_section)):
        blocks=sections.get(sy);palette,values=blocks if blocks else ([],[0]*4096)
        names=[m.canonical(_comparison_state(m,m.state_json(t))) for t in palette] if palette else [air]
        override=by_section.get(sy,{})
        runs=[];last=None;count=0
        for i,index in enumerate(values):
            current=override.get(i,names[index])
            if current!=last:
                if count:runs.append([last,count])
                last=current;count=1
            else:count+=1
        if count:runs.append([last,count])
        if runs==[[air,4096]]:continue
        result.update(m.canonical([sy,runs]).encode());result.update(b'\n')
    return result.hexdigest()

def _verify_prior_chunk(m,db,dim,cx,cz,actual_root,source_root):
    m.require((actual_root['xPos'].value,actual_root['zPos'].value)==(cx,cz),'Actual target chunk coordinates changed')
    changes,entities=_content(m,db,dim,cx,cz,source_root,'stage1_')
    expected_states=_states_digest(m,_sections(m,source_root),changes);actual_states=_states_digest(m,_sections(m,actual_root))
    m.require(actual_states==expected_states,'Actual target block states differ from the exact archived stage placements: '+str((dim,cx,cz)))
    expected_bes=_bes_digest(m,entities);actual_bes=_bes_digest(m,actual_root.get('block_entities',m.Tag(9,[],10)).value)
    m.require(actual_bes==expected_bes,'Actual target BE payload/owners/NativeGridBase differs from the exact archived stage cleanup: '+str((dim,cx,cz)))
    return {'statesSha256':actual_states,'blockEntitiesSha256':actual_bes}

def _original_chunk(m,db,dim,cx,cz):
    # Missing chunks must not trigger a new world discovery/address fallback.
    m.require(db.execute('SELECT 1 FROM old.chunk_cache WHERE dim=? AND x=? AND z=?',(dim,cx,cz)).fetchone(),'Context source chunk is absent from the saved discovery cache: '+str((dim,cx,cz)))
    source,_,_=m.get_source_chunk(db,dim,cx,cz);return m.compound(source.root)

def _region_chunks(m,path,wanted):
    """Read one addressed compressed chunk, never materialize a whole region."""
    with m.checked(path).open('rb') as stream:
        header=stream.read(8192);m.require(len(header)==8192,'Short actual target region header')
        size=os.fstat(stream.fileno()).st_size
        for cx,cz in sorted(wanted):
            loc=int.from_bytes(header[((cx&31)+32*(cz&31))*4:((cx&31)+32*(cz&31))*4+4],'big');sector,count=loc>>8,loc&255
            if not sector:yield cx,cz,None,None;continue
            m.require(sector>=2 and count>0 and sector*4096+5<=size,'Invalid actual region allocation')
            stream.seek(sector*4096);length=int.from_bytes(stream.read(4),'big');compression=stream.read(1)[0]
            m.require(1<=length<=count*4096-4 and compression in (1,2,3),'Invalid actual inline chunk record')
            payload=stream.read(length-1);m.require(len(payload)==length-1,'Short actual chunk record');yield cx,cz,compression,payload

def transformed_chunk(m,db,dim,cx,cz,compression,payload):
    if dim!='.' or not db.execute('SELECT 1 FROM context_allowed_chunks WHERE cx=? AND cz=?',(cx,cz)).fetchone():return None
    actual=m.decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]);root=m.compound(actual.root);source=_original_chunk(m,db,dim,cx,cz)
    # This same proof is repeated immediately before the atomic region writer.
    # Re-entry after a committed swap is handled by file_commit's exact hash.
    _verify_prior_chunk(m,db,dim,cx,cz,root,source)
    desired,entities=_content(m,db,dim,cx,cz,source,'');sections=_sections(m,root)
    changes={at:state for at,state in desired.items() if m.source_state(sections,at)!=state}
    old_entities=root.get('block_entities',m.Tag(9,[],10)).value;be_changed=_bes_digest(m,old_entities)!=_bes_digest(m,entities)
    if not changes and not be_changed:return None
    if changes:m.rewrite_states(root,changes)
    if be_changed:root['block_entities']=m.Tag(9,entities,10)
    m.invalidate_chunk_lighting(root)
    db.execute('INSERT OR IGNORE INTO context_changed_chunks VALUES(?,?,?)',(dim,cx,cz))
    for dx in (-1,0,1):
        for dz in (-1,0,1):db.execute('INSERT OR IGNORE INTO relight(dim,cx,cz,reason) VALUES(?,?,?,?)',(dim,cx+dx,cz+dz,'context_extension_changed_or_unproved_optics'))
    return actual

def _binding(m,db,target):
    value=m.meta(db,'contextRegionBinding');m.require(value and value['target']==str(target) and m.sha(value['path'])==value['sha256'],'Bind exact in-place context content before any apply')
    report=m.read(value['path']);extension=receipt(m,db)
    m.require(report['status']=='PASS' and report['contextExtensionSha256']==m.meta(db,'contextExtension')['sha256'] and report['allowedChunkSetSha256']==extension['allowedChunkSetSha256'],'In-place context binding belongs to a different stage/scope')
    final=report['finalNativeInputGate'];m.require(m.sha(final['path'])==final['sha256'],'The accepted final actual-native input receipt changed after binding')
    m.require(report.get('dependencyRepair')==m.meta(db,'contextDependencyRepair'),'Dependency repair binding changed after in-place binding')
    return report

def _final_input_gate(m,db,plan,expected=None,full=False):
    path=plan/'full-native-input-coverage.json' if full else plan/'context-extension'/'final-state-coverage.json';report=m.read(path);binding={'path':str(m.checked(path)),'sha256':m.sha(path)}
    m.require(expected is None or expected==binding,'The accepted final actual-native input receipt changed after binding')
    m.require(report.get('status')=='PASS' and report.get('kind')=='ACTUAL_POST_ACTIVATION_NATIVE_INPUT_GATE','Final actual native-function/physical input coverage must pass before binding/apply')
    if full:m.require(report.get('scope')=='FULL_CITY','The full conversion requires its own fresh final native input gate')
    m.require(report['sourceBinding']==m.meta(db,'binding') and report['compositionGeneration']==m.meta(db,'compositionGeneration'),'Final actual native proof belongs to another source/composition generation')
    m.require(report['compositionsSha256']==m.sha(plan/'compositions.jsonl') and report['nativeStatesNeededSha256']==m.sha(plan/'native-states-needed.json'),'Final actual native proof lost its exact raw/state-needed binding')
    for name,key in (('nativeFunctionGate','fullNativeFunctionGate' if full else 'contextNativeFunctionGate'),('fragmentBinding','poseFragments')):
        current=m.meta(db,key);m.require(report[name]==current and m.sha(current['path'])==current['sha256'],'Final actual native proof reference changed: '+name)
    for name in ('missingFunctionDecisions','nonPassiveBaseStates','missingNativeExports','invalidPhysicalNativeRows','missingActivatedSourcePoseFootprints'):m.require(report.get(name)==[],'Final actual native proof has unresolved '+name)
    lineage=report.get('fragmentAppendLineage')
    if report['compositionGeneration']['sourceFragments']['sha256']!=report['fragmentBinding']['sha256']:
        m.require(lineage and m.sha(lineage['path'])==lineage['sha256'],'Historical/current fragment snapshots require their explicit immutable append proof')
        append=m.read(lineage['path']);m.require(append.get('status')=='PASS' and append.get('beforeFragmentsSha256')==report['compositionGeneration']['sourceFragments']['sha256'] and append.get('afterFragmentsSha256')==report['fragmentBinding']['sha256'],'Final native append proof belongs to different fragment snapshots')
        m.require(all(append.get(k) is True for k in ('posesUnchanged','fragmentsUnchanged','existingNativeRowsUnchanged')),'The final native append proof changed prior geometry/rows')
    return binding

def _content_fingerprint(m,db):
    result={}
    for table in ('placements','selected_objects','bridge_cells','cleanup_owners'):
        h=hashlib.sha256();count=0
        for row in db.execute('SELECT * FROM '+table+' ORDER BY rowid'):h.update(m.canonical(row).encode());h.update(b'\n');count+=1
        result[table]={'rows':count,'sha256':h.hexdigest()}
    return result

def _protect_saved_dependencies(m,extension,changed):
    for line in extension['nativeSavedLight']['savedRows']:
        dimension,x,z=line.split('\t');m.require(dimension=='minecraft:overworld','Unexpected saved light dimension');radius=extension['nativeSavedLight']['dependencyRadius']
        m.require(not any(dim=='.' and abs(cx-int(x))<=radius and abs(cz-int(z))<=radius for dim,cx,cz in changed),'A new content change invalidates a previously saved light dependency')

def apply_guard(m,db,target,runtime_jar):
    m.require(m.meta(db,'scope') and m.meta(db,'target')==str(target),'Context extension is restricted to the same pilot target')
    report=_binding(m,db,target);catalog=m.meta(db,'catalog')
    _final_input_gate(m,db,Path(report['plan']),report['finalNativeInputGate'])
    m.require(report['catalog']==catalog and report['compositionGeneration']==m.meta(db,'compositionGeneration'),'Compiled context generation changed after content binding')
    m.require(report['plannedContentFingerprint']==_content_fingerprint(m,db),'Planned context placements/cleanup/bridges changed after binding')
    if not native_light_restart(m,db):_protect_saved_dependencies(m,receipt(m,db),[tuple(v) for v in report['predictedChangedChunks']])
    m.require(m.read(Path(report['plan'])/'context-extension'/'stage-intent.json')['phase']=='PREPARED','Context stage is not durably prepared')
    catalog_prefix(m,db,catalog['path']);check_complete(m,db)
    extension=receipt(m,db);m.require(m.sha(extension['parentRuntime']['path'])==extension['parentRuntime']['sha256'],'Archived production runtime changed')
    runtime_proof=_light_code_proof(m,extension['parentRuntime']['path'],runtime_jar)
    m.bind_runtime(db,runtime_jar)
    proof_path=Path(report['plan'])/'context-extension'/'runtime-entry-proof.json';m.atomic_json(proof_path,{'schemaVersion':1,'status':'PASS',**runtime_proof,'catalogSha256':catalog['sha256'],'mapWrites':0});m.meta(db,'contextRuntimeEntryProof',{'path':str(proof_path),'sha256':m.sha(proof_path)})
    # Keep ALL prior debt, including the nine satellite cells whose geometry
    # is skipped because it is identical. A new queue must retain its old
    # eight actually saved rows as the exact sorted prefix.
    db.execute("INSERT OR IGNORE INTO relight(dim,cx,cz,reason,status,receipt) SELECT dim,cx,cz,reason,'pending',NULL FROM stage1_relight WHERE status!='done'")
    for line in (() if native_light_restart(m,db) else receipt(m,db)['nativeSavedLight']['savedRows']):
        dimension,x,z=line.split('\t');m.require(dimension=='minecraft:overworld','Unexpected saved light dimension')
        m.require(not db.execute('SELECT 1 FROM context_changed_chunks WHERE dim=? AND abs(cx-?)<=11 AND abs(cz-?)<=11 LIMIT 1',('.',int(x),int(z))).fetchone(),'A new edit invalidates a previously saved light dependency')

def bind_copy(a,m):
    plan=m.checked(a.plan);target=m.checked(a.target);db=m.connect(plan)
    try:
        extension=receipt(m,db);m.require(extension and not m.meta(db,'applyStarted') and m.meta(db,'target')==str(target),'Bind only the same prepared unapplied context pilot')
        m.require(m.meta(db,'catalog') and not db.execute('SELECT 1 FROM placements WHERE type_index IS NULL LIMIT 1').fetchone(),'Compile every context placement before binding')
        final_input_gate=_final_input_gate(m,db,plan)
        catalog_prefix(m,db,m.meta(db,'catalog')['path']);check_complete(m,db)
        history=Path(extension['history']);old_meta=m.read(history/'meta.json');manifest=m.read(m.meta(db,'binding')['copyManifest']);copy_receipt=m.read(target.parent/(target.name+'-copy.json'))
        m.require(copy_receipt['selectedChunkSetSha256']==extension['allowedChunkSetSha256'],'Context pilot copy chunk set changed')
        allowed=set(db.execute('SELECT cx,cz FROM context_allowed_chunks'));groups=collections.defaultdict(set)
        for cx,cz in allowed:groups['region/r.%d.%d.mca'%(cx>>5,cz>>5)].add((cx,cz))
        regions={};chunks={};missing=[];predicted=[]
        for rel,wanted in sorted(groups.items()):
            path=m.checked(target/rel);m.require(path.is_file(),'Copied context region missing: '+rel);before=m.sha(path)
            for cx,cz,compression,payload in _region_chunks(m,path,wanted):
                cached=db.execute("SELECT 1 FROM old.chunk_cache WHERE dim='.' AND x=? AND z=?",(cx,cz)).fetchone()
                if compression is None:
                    m.require(not cached,'An original copied context chunk disappeared: '+str((cx,cz)));missing.append([cx,cz]);continue
                m.require(cached,'A generated chunk occupies an originally absent authorized source slot: '+str((cx,cz)))
                actual=m.compound(m.decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]).root);source=_original_chunk(m,db,'.',cx,cz);proof=_verify_prior_chunk(m,db,'.',cx,cz,actual,source);chunks[str(cx)+','+str(cz)]=proof
                desired,entities=_content(m,db,'.',cx,cz,source,'')
                if proof['statesSha256']!=_states_digest(m,_sections(m,source),desired) or proof['blockEntitiesSha256']!=_bes_digest(m,entities):predicted.append(['.',cx,cz])
            m.require(m.sha(path)==before,'Target region changed during offline binding');regions[rel]=before
            m.progress(plan,db,'BINDING_CONTEXT_CONTENT',verifiedChunks=len(chunks),lastRegion=rel)
        # The exact previous after hash (or unchanged immutable/cropped hash)
        # admits a ledger; arbitrary post-game dictionaries are never trusted.
        repair=dependency_repair(m,db);repair_world=repair['worlds']['candidate'] if repair else None;repair_global=next((v['afterSha256'] for v in repair['operations'] if v['variant']=='candidate' and v['path']=='data/bloodborne_dw_composite_owners.dat'),None) if repair else None
        ledgers={};old_files={path:after for path,after,phase in db.execute('SELECT path,after_hash,phase FROM stage1_files') if phase=='committed' and not path.startswith('relight-cache/')}
        for rel,row in manifest['files'].items():
            path=target/rel;dictionary=path.parent.name=='bloodborne_dw_owner_shards' and path.suffix=='.nbt';global_ledger=path.name=='bloodborne_dw_composite_owners.dat' and path.parent.name=='data'
            if not path.is_file() or not(dictionary or global_ledger):continue
            expected=old_files.get(rel,row['sha256'])
            pilot=old_meta.get('pilotMetadataBinding')
            if rel not in old_files and pilot and rel==pilot['globalLedger']['path']:expected=pilot['globalLedger']['beforeCleanupSha256']
            if repair_world:
                if rel in repair_world['existingShards']:expected=repair_world['existingShards'][rel].get('repairedAfterSha256',repair_world['existingShards'][rel]['beforeSha256'])
                elif rel=='data/bloodborne_dw_composite_owners.dat':expected=repair_global
            actual=m.sha(path);m.require(actual==expected,'Prior-stage ledger differs from its exact committed after bytes: '+rel)
            ledgers[rel]={'sha256':actual,'sourceSha256':row['sha256'],'proof':'stage1_committed_after' if rel in old_files else 'proved_cropped_index' if actual!=row['sha256'] else 'unchanged_immutable_copy'}
        m.require(len([p for p in ledgers if Path(p).parent.name=='bloodborne_dw_owner_shards'])==(repair['requiredSourceShardCount'] if repair else 407),'The exact original/dependency source shard set changed')
        if not native_light_restart(m,db):_protect_saved_dependencies(m,extension,[tuple(v) for v in predicted])
        report={'schemaVersion':1,'status':'PASS','kind':'EXACT_PRIOR_STAGE_IN_PLACE_CONTEXT_BINDING','plan':str(plan),'target':str(target),'contextExtensionSha256':m.meta(db,'contextExtension')['sha256'],'allowedChunkSetSha256':extension['allowedChunkSetSha256'],'catalog':m.meta(db,'catalog'),'compositionGeneration':m.meta(db,'compositionGeneration'),'finalNativeInputGate':final_input_gate,'plannedContentFingerprint':_content_fingerprint(m,db),'predictedChangedChunks':predicted,'regions':regions,'chunks':chunks,'originalAbsentChunks':missing,'ledgers':ledgers,'dependencyRepair':m.meta(db,'contextDependencyRepair'),'archivedPlacementProof':True,'unknownBlockEntityFieldsPreserved':True,'mapWrites':0}
        output=plan/'context-extension'/'in-place-binding.json';m.atomic_json(output,report)
        for rel, in db.execute('SELECT path FROM regions').fetchall():m.require(rel in regions,'Planned region exceeds fixed copied context');db.execute('UPDATE regions SET before_hash=? WHERE path=?',(regions[rel],rel))
        m.meta(db,'contextRegionBinding',{'path':str(output),'sha256':m.sha(output),'target':str(target)});db.commit();m.progress(plan,db,'CONTEXT_IN_PLACE_CONTENT_BOUND',verifiedChunks=len(chunks),ledgerFiles=len(ledgers))
    finally:db.close()

def ledger_before(m,db,target,rel,immutable_hash):
    row=_binding(m,db,target)['ledgers'].get(rel);m.require(row and row['sourceSha256']==immutable_hash,'Context ledger override lacks its exact immutable source binding: '+rel)
    return row['sha256']

def light_to_invalidate(m,db,dim,cx,cz):
    """Only the proven eight saved rows are exempt from cache invalidation.

    Their SQLite debt rows stay pending and remain in the new queue. Native
    engine's durable done prefix supplies completion; no synthetic PASS rows.
    """
    if native_light_restart(m,db):return True
    extension=receipt(m,db)
    if not extension:return True
    saved={tuple(v.split('\t')) for v in extension['nativeSavedLight']['savedRows']}
    if dim!='.' or ('minecraft:overworld',str(cx),str(cz)) not in saved:return True
    report=_binding(m,db,m.checked(m.meta(db,'target')))
    _protect_saved_dependencies(m,extension,[tuple(v) for v in report['predictedChangedChunks']])
    radius=extension['nativeSavedLight']['dependencyRadius']
    m.require(not db.execute('SELECT 1 FROM context_changed_chunks WHERE dim=? AND abs(cx-?)<=? AND abs(cz-?)<=? LIMIT 1',(dim,cx,radius,cz,radius)).fetchone(),'A committed edit invalidates the saved-light exemption')
    return False

def _write_bytes(path,data):
    temporary=path.with_name(path.name+'.context-writing')
    with temporary.open('wb') as stream:stream.write(data);stream.flush();os.fsync(stream.fileno())
    os.replace(temporary,path)

def _light_code_proof(m,old_jar,new_jar):
    with zipfile.ZipFile(old_jar) as old,zipfile.ZipFile(new_jar) as new:
        old_entries=[v for v in old.infolist() if not v.is_dir()];new_entries=[v for v in new.infolist() if not v.is_dir()]
        m.require(len({v.filename for v in old_entries})==len(old_entries) and len({v.filename for v in new_entries})==len(new_entries),'Ambiguous duplicate JAR entries are forbidden')
        previous={v.filename:old.read(v) for v in old_entries};current={v.filename:new.read(v) for v in new_entries}
    # Phase two appends generated assets only. The exact production runtime
    # bytecode (including ALL lighting/storage classes and mixin config) stays.
    ignored=('assets/bloodborne_dw/native_grid/catalog.json','assets/bloodborne_dw/native_grid/meshes.bin','assets/bloodborne_dw/native_grid/bridge_carriers.json','assets/bloodborne_dw/native_grid/compiler-receipt.json')
    old_count=len(json.loads(previous[m.CATALOG_REL])['cells']);new_count=len(json.loads(current[m.CATALOG_REL])['cells']);new_banks=(new_count+127)//128
    changed=[]
    for name in sorted(set(previous)|set(current)):
        if previous.get(name)==current.get(name):continue
        allowed=name in ignored
        marker=re.fullmatch(r'assets/bloodborne_dw/models/block/native_grid_(\d+)_(\d+)\.json',name)
        bank_marker=re.fullmatch(r'assets/bloodborne_dw/(blockstates|models/item)/native_grid_(\d+)\.json',name)
        if marker:
            bank,part=map(int,marker.groups());allowed=(0<=part<128 and old_count<=bank*128+part<new_banks*128 and name in current)
            if allowed:
                data=json.loads(current[name]);allowed=set(data)=={'textures'} and set(data['textures'])=={'particle'} and isinstance(data['textures']['particle'],str)
        if bank_marker:
            folder,bank=bank_marker.group(1),int(bank_marker.group(2));allowed=bank<new_banks and name not in previous and name in current
            if allowed:
                expected={'parent':'bloodborne_dw:block/native_grid_'+str(bank)+'_0'} if folder=='models/item' else {'variants':{'part='+str(part):{'model':'bloodborne_dw:block/native_grid_'+str(bank)+'_'+str(part)} for part in range(128)}}
                allowed=json.loads(current[name])==expected
        m.require(allowed,'Context light continuation changed a production entry: '+name);changed.append(name)
    return {'allProductionCodeAndOtherResourcesByteIdentical':True,'allowedGeneratedAssetChanges':changed,'oldRuntimeSha256':m.sha(old_jar),'newRuntimeSha256':m.sha(new_jar)}

def adopt_light(m,db,plan):
    if native_light_restart(m,db):return
    extension=receipt(m,db);saved=extension['nativeSavedLight'];history=Path(extension['history']);runtime=m.meta(db,'runtime');catalog_prefix(m,db,m.meta(db,'catalog')['path'])
    m.require(m.sha(extension['parentRuntime']['path'])==extension['parentRuntime']['sha256'] and m.sha(runtime['path'])==runtime['sha256'],'Runtime bytes changed before native-light continuation')
    proof=_light_code_proof(m,extension['parentRuntime']['path'],runtime['path']);queue=(plan/'relight-queue.tsv').read_bytes();qh=hashlib.sha256(queue).hexdigest();rows=[v for v in queue.decode().splitlines() if v and not v.startswith('#')]
    m.require(rows[:len(saved['savedRows'])]==saved['savedRows'],'Expanded light queue lost the exact eight genuinely saved prefix rows')
    old_cursor=(history/'relight-engine.json.done.tsv').read_bytes();m.require(hashlib.sha256(old_cursor).hexdigest()==saved['cursorSha256'],'Archived native saved cursor changed')
    for line in saved['savedRows']:
        dimension,x,z=line.split('\t');m.require(dimension=='minecraft:overworld','Unknown saved dimension')
        m.require(not db.execute('SELECT 1 FROM context_changed_chunks WHERE dim=? AND abs(cx-?)<=? AND abs(cz-?)<=? LIMIT 1',('.',int(x),saved['dependencyRadius'],int(z),saved['dependencyRadius'])).fetchone(),'A context edit invalidates a saved light dependency')
    output=plan/'relight-engine.json.done.tsv';new_header=('# '+qh+' '+runtime['sha256']+'\n').encode();body=old_cursor.split(b'\n',1)[1];adopted=new_header+body
    if output.exists():
        actual=output.read_bytes()
        if actual.startswith(new_header):
            m.require(actual.endswith(b'\n'),'Torn resumed native cursor');done=[v.decode().rstrip('\r\n') for v in actual.splitlines(keepends=True)[1:]]
            m.require(done==rows[:len(done)] and done[:8]==saved['savedRows'],'Resumed native cursor is not an exact extended queue prefix')
        else:
            m.require(actual==old_cursor,'Existing native cursor is neither the archived original nor this exact new continuation');_write_bytes(output,adopted)
    else:_write_bytes(output,adopted)
    report={'schemaVersion':1,'status':'PASS','kind':'UNCHANGED_NATIVE_SAVED_LIGHT_PREFIX','savedRows':saved['savedRows'],'adoptedSavedChunks':len(saved['savedRows']),'queueSha256':qh,'runtimeSha256':runtime['sha256'],'catalogSha256':m.meta(db,'catalog')['sha256'],'nativeSavedEvidence':saved['nativeSavedEvidence'],'priorCursorSha256':saved['cursorSha256'],'rowsSha256':hashlib.sha256(body).hexdigest(),'dependencyRadius':saved['dependencyRadius'],'noNewChangedChunkWithinDependencyRadius':True,'productionEntryProof':proof,'mapWrites':0,'newLightWorkStillPending':len(rows)-len(saved['savedRows'])}
    path=plan/'context-extension'/'native-saved-light-adoption.json';m.atomic_json(path,report);m.meta(db,'contextLightAdoption',{'path':str(path),'sha256':m.sha(path)});db.commit()
