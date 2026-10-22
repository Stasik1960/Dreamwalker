"""Prepare finite real-city function QA inputs from the existing read-only plan.

Run under Guard.ps1 -Mode Operation. This utility never edits a Minecraft map,
starts Minecraft, or modifies either coordinate database. Source witnesses use
actual stored owner UUIDs; generated native/synthetic owners are not candidates.
"""
from __future__ import annotations
import argparse, copy, hashlib, heapq, json, math, sqlite3, struct, uuid, zipfile
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
ROOT = PROJECT.parent
RESEARCH = ROOT / 'research'
TOOLS = PROJECT / 'tools' / 'native-grid'
PLAN = RESEARCH / 'native-grid-plan'
OUT = RESEARCH / 'function-qa'
FOCUS = (912, 1007, 64, 159)
STAND = {'min': [1007, 224, 95], 'max': [1040, 256, 128], 'root': [1024, 225, 111]}
BASE_JAR = Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018\deliverables\block-fixes-2-20261020\game\dreamwalker-bb-materials.1-streaming.7-blocks.2.jar')
BASE_SHA256 = '40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
OFFLINE_REFERENCE = 'OFFLINE_ASSET_REFERENCE_NOT_GAMEPLAY'
WITNESSES = {'door': 'aa93496c-eb3d-5b07-8458-fe191c8db407',
             'tree': '1f3bd07f-6943-5ac8-afb6-516534346617',
             'statue': '552eab76-9727-5d2d-882a-43626ff07a55',
             'protected': 'd430135b-ad31-5dcc-9f53-fe6aa08d000d',
             'ladder': '1990aa22-19fe-50b5-b17f-3b2cb0d465d7'}

def read(path): return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def write(path, data):
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.writing')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)
def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b''): value.update(chunk)
    return value.hexdigest()
def require(condition, message):
    if not condition: raise RuntimeError(message)
def readonly(path):
    db = sqlite3.connect('file:' + Path(path).absolute().as_posix() + '?mode=ro', uri=True)
    db.row_factory = sqlite3.Row
    return db
def context(plan=PLAN):
    db = readonly(Path(plan) / 'plan.sqlite')
    meta = {key: json.loads(value) for key, value in db.execute('SELECT key,value FROM meta')}
    old = Path(meta['binding']['oldPlan'])
    db.execute('ATTACH DATABASE ? AS old', ('file:' + (old / 'plan.sqlite').as_posix() + '?mode=ro',))
    return db, meta
def actual_uuid(encoded):
    values = json.loads(encoded)
    require(len(values) == 4, 'Stored owner UUID is not an int[4]')
    return str(uuid.UUID(bytes=struct.pack('>4i', *values)))
def distance(x, z):
    return max(FOCUS[0] - x, 0, x - FOCUS[1]) ** 2 + max(FOCUS[2] - z, 0, z - FOCUS[3]) ** 2
def row_object(row):
    value = dict(row)
    value['ownerUuid'] = actual_uuid(value['uuid'])
    value['source'] = [value['x'], value['y'], value['z']]
    value['distanceSquaredToPilot'] = distance(value['x'], value['z'])
    if value.get('poseData'): value['poseData'] = json.loads(value['poseData'])
    return value

def closest(db, ids, limit=8):
    if not ids: return []
    marks = ','.join('?' for _ in ids)
    # Actual stored UUID join is essential: objects also contains synthetic
    # plain native cells, which must not become functional object witnesses.
    rows = db.execute(f'''SELECT s.dim,s.uuid,s.registry_id,s.x,s.y,s.z,
       o.pose,o.kind,o.reason,p.data AS poseData
       FROM old.stored_owners s LEFT JOIN objects o ON o.dim=s.dim AND o.uuid=s.uuid
       LEFT JOIN poses p ON p.key=o.pose
       WHERE s.dim='.' AND s.registry_id IN ({marks})''', sorted(ids))
    return heapq.nsmallest(limit, (row_object(row) for row in rows), key=lambda r: (r['distanceSquaredToPilot'], r['source'][1], r['registry_id']))

def inspect(args):
    db, meta = context(args.plan)
    classification = read(TOOLS / 'classification.json')
    doors = set(classification['openableRegistryIds'])
    trees = set(classification['spriteTreeRegistryIds'])
    statues = {row['registryId'] for row in read(TOOLS / 'statue-ids.json')['entries'] if row['classification'] == 'whole_statue'}
    protected = set(classification['protectedRegistryIds'])
    protected = {row if isinstance(row, str) else row['registryId'] for row in protected} if not all(isinstance(r, str) for r in protected) else protected
    ladders = {row['registryId'] for row in classification['poses'] if 'ladder' in row['registryId']}
    doc = {'schemaVersion': 1, 'focus': FOCUS, 'source': meta['binding']['source'],
           'tables': {r[0]: [column[1] for column in db.execute('PRAGMA table_info(' + r[0] + ')')] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")},
           'oldTables': {r[0]: [column[1] for column in db.execute('PRAGMA old.table_info(' + r[0] + ')')] for r in db.execute("SELECT name FROM old.sqlite_master WHERE type='table'")},
           'candidates': {name: closest(db, ids) for name, ids in [('door', doors), ('tree', trees), ('statue', statues), ('protected', protected), ('ladder', ladders)]}}
    fragments = readonly(RESEARCH / 'fragments.sqlite')
    doc['fragmentTables'] = {r[0]: [c[1] for c in fragments.execute('PRAGMA table_info(' + r[0] + ')')] for r in fragments.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    write(args.output / 'selection-discovery.json', doc)
    print(json.dumps({'status': 'WITNESSES_LISTED_NOT_READY', 'output': str(args.output / 'selection-discovery.json'),
                      'candidates': {name: [{k: row.get(k) for k in ('registry_id', 'source', 'ownerUuid', 'pose', 'kind', 'reason', 'distanceSquaredToPilot')} for row in rows[:4]] for name, rows in doc['candidates'].items()}}, ensure_ascii=False))

def encoded_uuid(text): return json.dumps(struct.unpack('>4i', uuid.UUID(text).bytes), separators=(',', ':'))
def shifted(root, offset): return tuple(a + b for a, b in zip(root, offset))
def chunk(point): return point[0] // 16, point[2] // 16
def bound_chunks(points): return sorted({chunk(point) for point in points})
def cube(center): return sorted((center[0]+x,center[1]+y,center[2]+z) for x in range(-1,2) for y in range(-1,2) for z in range(-1,2))

def select(args):
    if args.statue_selection:
        chosen=read(args.statue_selection)
        require(chosen.get('status')=='PASS','Statue selection must have an actual finite footprint proof')
        WITNESSES['statue']=str(uuid.UUID(bytes=struct.pack('>4i',*json.loads(chosen['winner']['uuid']))))
    db, meta = context(args.plan)
    selected = {}
    for kind, owner in WITNESSES.items():
        row = db.execute('''SELECT s.dim,s.uuid,s.registry_id,s.x,s.y,s.z,o.pose,o.kind,o.reason,p.data AS poseData
            FROM old.stored_owners s JOIN objects o ON o.dim=s.dim AND o.uuid=s.uuid JOIN poses p ON p.key=o.pose
            WHERE s.dim='.' AND s.uuid=?''', (encoded_uuid(owner),)).fetchone()
        require(row is not None, 'Bound real stored owner missing: ' + kind)
        selected[kind] = row_object(row)
        selected[kind]['footprint'] = {tuple(selected[kind]['source'])}
        for dx,dy,dz in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?', (row['pose'],)):
            selected[kind]['footprint'].add(shifted(selected[kind]['source'],(dx,dy,dz)))
    fragment_db = readonly(RESEARCH/'fragments.sqlite')
    ladder = selected['ladder']; climbing = []
    for row in fragment_db.execute('SELECT x,y,z,data FROM fragments WHERE pose=?',(ladder['pose'],)):
        for box in json.loads(row['data']).get('climbing',[]):
            climbing.append(shifted(ladder['source'],(row['x']+(box[0]+box[3])/2,row['y']+box[1]+0.02,row['z']+(box[2]+box[5])/2)))
    require(climbing,'Bound ladder has no exported climb witness')
    ladder['player'] = climbing[0]
    # Exported source geometry is only 3102 finite poses, not world cells.
    # Prefer maximum luminance to avoid ambiguity from a brighter colocated lamp.
    light_poses = {}
    with (RESEARCH/'geometry-final'/'source-geometry.jsonl').open(encoding='utf-8') as stream:
        for line in stream:
            pose = json.loads(line)
            values = [(cell['optics'].get('luminance',0),cell['cell']) for cell in pose['cells'] if cell['optics'].get('luminance',0)>0]
            if values: light_poses[pose['key']] = max(values)
    require(light_poses,'No actual light pose exported')
    light_rows = []
    for pose,(level,offset) in light_poses.items():
        rows = db.execute('''SELECT s.dim,s.uuid,s.registry_id,s.x,s.y,s.z,o.pose,o.kind,o.reason,p.data AS poseData
             FROM objects o JOIN old.stored_owners s ON s.dim=o.dim AND s.uuid=o.uuid JOIN poses p ON p.key=o.pose
             WHERE o.dim='.' AND o.pose=?''',(pose,))
        for raw in rows:
            row=row_object(raw); row['pos']=shifted(row['source'],offset);row['expected']=level
            light_rows.append(row)
    require(light_rows,'No real stored lamp owner exists')
    light=min(light_rows,key=lambda r:(r['distanceSquaredToPilot'],-r['expected'],r['source']))
    selected['light']=light
    light['footprint']={tuple(light['source'])}
    for dx,dy,dz in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?',(light['pose'],)):
        light['footprint'].add(shifted(light['source'],(dx,dy,dz)))
    # These historical tables have no UUID index: one finite batched scan each,
    # rather than a repeated full scan per selected owner.
    by_uuid = {row['uuid']: row for row in selected.values()}
    marks = ','.join('?' for _ in by_uuid)
    for table in ('stored_contributions','members'):
        for row in db.execute(f'SELECT uuid,x,y,z FROM old.{table} WHERE dim=\'.\' AND uuid IN ({marks})', tuple(by_uuid)):
            by_uuid[row['uuid']]['footprint'].add((row['x'],row['y'],row['z']))
    for row in selected.values():
        row['footprint'] = sorted(row['footprint'])
        row['chunks'] = bound_chunks(row['footprint'])
    heavy=db.execute('''SELECT x,y,z,count(*) AS n FROM fragments WHERE dim='.' AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?
       GROUP BY x,y,z HAVING n>1 ORDER BY n DESC,x,y,z LIMIT 1''',FOCUS).fetchone()
    border=db.execute('''SELECT x,y,z,count(*) AS n FROM fragments WHERE dim='.' AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?
       AND (x%16=0 OR x%16=15 OR z%16=0 OR z%16=15) GROUP BY x,y,z ORDER BY n DESC,x,y,z LIMIT 1''',FOCUS).fetchone()
    require(heavy and border,'Need actual heavy-intersection and chunk-boundary windows')
    windows=[{'key':name,'origin':[row['x'],row['y'],row['z']],'positions':cube((row['x'],row['y'],row['z'])),'sourceFragmentCount':row['n']}
             for name,row in [('heavy_intersection',heavy),('chunk_boundary',border)]]
    points=set()
    for row in selected.values(): points.update(tuple(p) for p in row.get('footprint',[row.get('pos',row['source'])]))
    points.add(tuple(light['pos'])); points.add(tuple(int(v//1) for v in ladder['player']))
    for window in windows: points.update(window['positions'])
    # Stand chunks include every guarded cell, plus the optional support y223.
    for x in range(STAND['min'][0]//16,STAND['max'][0]//16+1):
        for z in range(STAND['min'][2]//16,STAND['max'][2]//16+1): points.add((x*16,223,z*16))
    chunks=bound_chunks(points); require(len(chunks)<=64,'Finite helper sourceChunks cap exceeded')
    known={(row['x'],row['z']) for row in db.execute("SELECT x,z FROM old.checked_chunks WHERE dim='.'")}
    require(set(chunks)<=known,'Witness references chunks not proved in original scan: '+str(set(chunks)-known))
    satellites={'schemaVersion':1,'status':'INPUT_BOUND_NOT_QA_READY','owners':[], 'chunks':[], 'witnesses':{}}
    for kind in ('tree','statue','light'):
        row=selected[kind]
        satellites['witnesses'][kind]=row
        satellites['chunks'].extend({'dimension':'.','x':x,'z':z} for x,z in row['chunks'])
        if kind=='statue':
            satellites['owners'].append({'dimension':'.','uuid':row['uuid'],'ownerUuid':row['ownerUuid'],'root':row['source'],'registryId':row['registry_id'],'pose':row['pose'],'kind':'whole','authorization':'single_additional_function_witness'})
    satellites['chunks']=sorted({(c['dimension'],c['x'],c['z']) for c in satellites['chunks']})
    satellites['chunks']=[{'dimension':d,'x':x,'z':z} for d,x,z in satellites['chunks']]
    write(args.output/'satellites.json',satellites)
    write(args.output/'witness-selection.json',{'schemaVersion':1,'status':'INPUT_BOUND_NOT_QA_READY','sourceBinding':meta['binding'],'selected':selected,'windows':windows,'sourceChunks':chunks,'stand':STAND})
    baseline={'schemaVersion':1,'mode':'BOUND_NATIVE_GRID_FUNCTION_QA','variant':'baseline','targetWorldPath':str(args.world.absolute()),
        'productionJar':str(args.jar.absolute()),'productionSha256':sha(args.jar),'output':str((args.output/'baseline').absolute()),
        'sourceChunks':chunks,'stand':STAND,'windows':windows,
        'protected':[{'pos':p,'protectedOwnerUuid':selected['protected']['ownerUuid']} for p in selected['protected']['footprint']],
        'luminance':[{'pos':light['pos'],'expected':light['expected']}], 'ladder':{'player':ladder['player'],'expected':True},
        'wholeObjects':[{'kind':kind,'source':selected[kind]['source'],'ownerUuid':selected[kind]['ownerUuid'],**({'requireCrossChunk':True} if kind=='statue' else {})} for kind in ('door','tree','statue')]}
    write(args.output/'baseline-plan.json',baseline)
    write(args.output/'selection-receipt.json',{'status':'INPUTS_PREPARED_NOT_QA_READY','baselinePlan':str(args.output/'baseline-plan.json'),
       'chunks':len(chunks),'witnesses':{k:{'ownerUuid':v['ownerUuid'],'source':v['source'],'cells':len(v.get('footprint',[])),'chunks':v.get('chunks')} for k,v in selected.items()},'windows':windows})
    print('INPUTS_PREPARED_NOT_QA_READY: '+str(args.output))

def offline_reference(args, baseline, db, meta):
    result=read(args.offline_reference)
    require(result.get('schemaVersion')==1 and result.get('status')==OFFLINE_REFERENCE and result.get('mode')==OFFLINE_REFERENCE and result.get('variant')=='baseline','Explicit offline asset reference required; this input cannot assert gameplay PASS')
    require(result.get('baselineGameplayStatus')=='NOT_ACQUIRED_OOM' and result.get('baselineFullTargetStatus')=='FAIL_OOM','Offline source data cannot upgrade the failed/unacquired baseline gameplay result')
    require(result.get('baselinePlanSha256')==sha(args.baseline_plan),'Offline reference does not bind the exact baseline input plan')
    require(result.get('sourceJarSha256')==BASE_SHA256 and baseline.get('productionSha256')==BASE_SHA256 and sha(baseline['productionJar'])==BASE_SHA256,'Offline reference must bind the immutable blocks.2 source JAR')
    binding=meta['binding']
    require(result.get('sourceBinding')==binding and binding.get('sourceHashesVerified') is True and binding.get('originalPlanReadOnly') is True,'Offline source binding differs from the read-only coordinate proof')
    require(sha(binding['copyManifest'])==binding['copyManifestSha256'],'Immutable source copy manifest changed')
    manifest=read(binding['copyManifest'])['files']
    require(result.get('unknownOrMissingPhysicsRoutes')==[],'Offline reference has unknown or missing physics routes')
    proof=result.get('sourceFilesRead',{});shards=proof.get('addressedOwnerShards',[]);cached=result.get('cachedChunksUsed',[])
    require(proof.get('cachedChunkDataOnly') is True and 1<=len(shards)<=64 and 1<=len(cached)<=64,'Offline reference lacks finite cached-chunk and original-ledger proof')
    required_chunks={(p[0]>>4,p[2]>>4) for window in baseline['windows'] for p in window['positions']}
    required_chunks.update((row['pos'][0]>>4,row['pos'][2]>>4) for row in baseline['protected'])
    require(len(cached)==len(shards)==len(required_chunks) and {tuple(row['chunk']) for row in cached}==required_chunks and {tuple(row['chunk']) for row in shards}==required_chunks,'Offline chunk/ledger proof does not cover the exact reference addresses')
    for row in shards:
        recorded=manifest.get(row['relativePath']);path=Path(row['readPath'])
        require(row['manifestSha256']==binding['copyManifestSha256'] and recorded is not None and recorded['sha256']==row['sha256'] and recorded['bytes']==row['bytes'],'Addressed source owner shard differs from the immutable source manifest')
        require(path.is_file() and path.stat().st_size==row['bytes'] and sha(path)==row['sha256'],'Addressed unchanged owner-shard copy changed after reference acquisition')
    for row in cached:
        original=db.execute("SELECT region_path,region_hash,compression,payload FROM old.chunk_cache WHERE dim='.' AND x=? AND z=?",row['chunk']).fetchone()
        require(original is not None and original['region_path']==row['region'] and original['region_hash']==row['boundRegionSha256'] and original['compression']==row['compression'] and hashlib.sha256(original['payload']).hexdigest()==row['cachedCompressedPayloadSha256'],'Addressed cached source chunk differs from offline reference provenance')
    return result

def bind_migrated_ladder(db, plan, catalog, catalog_sha, matrix, matrix_sha):
    ladder=plan['ladder'];owner=ladder.get('ownerUuid',WITNESSES['ladder']);encoded=encoded_uuid(owner)
    original=db.execute('''SELECT s.uuid,s.registry_id,s.x,s.y,s.z,o.pose,o.kind
        FROM old.stored_owners s JOIN objects o ON o.dim=s.dim AND o.uuid=s.uuid
        WHERE s.dim='.' AND s.uuid=?''',(encoded,)).fetchone()
    require(original is not None and original['kind']=='static','Ladder witness must be one real stored owner classified for static migration')
    selected=db.execute("SELECT pose,x,y,z FROM selected_objects WHERE dim='.' AND uuid=?",(encoded,)).fetchone()
    require(selected is not None and all(selected[k]==original[k] for k in ('pose','x','y','z')),'The bound ladder owner is not selected in the final composed plan')
    source=[original[k] for k in ('x','y','z')];point=[math.floor(v) for v in ladder['player']]
    offset=[a-b for a,b in zip(point,source)]
    require(db.execute('SELECT 1 FROM pose_cells WHERE pose=? AND dx=? AND dy=? AND dz=?',(original['pose'],*offset)).fetchone() is not None,'Ladder actor cell lies outside its actual source footprint')
    fragments=readonly(RESEARCH/'fragments.sqlite')
    try:fragment=fragments.execute('SELECT data FROM fragments WHERE pose=? AND x=? AND y=? AND z=?',(original['pose'],*offset)).fetchone()
    finally:fragments.close()
    source_climbing=json.loads(fragment['data']).get('climbing',[]) if fragment else []
    require(source_climbing,'Ladder actor cell has no exported source climb shape')
    placement=db.execute("SELECT type_index,composition FROM placements WHERE dim='.' AND x=? AND y=? AND z=?",point).fetchone()
    require(placement is not None and isinstance(placement['type_index'],int),'Ladder actor cell has no final compiled placement')
    index=placement['type_index'];require(0<=index<len(catalog['cells']),'Ladder placement catalog index is invalid')
    cell=catalog['cells'][index];climbing=catalog['shapes'][cell['climbing']]
    require(climbing,'Migrated ladder lost its shared prepared climbing shape')
    player=ladder['player'];body=[player[0]-.3,player[1],player[2]-.3,player[0]+.3,player[1]+1.8,player[2]+.3]
    def intersects(box):return all(box[a]+point[a]<body[a+3] and box[a+3]+point[a]>body[a] for a in range(3))
    require(any(intersects(box) for box in source_climbing) and any(intersects(box) for box in climbing),'Bound source and compiled climbing shapes must both intersect the declared actor body')
    binding={'mode':'plain','pos':point,'cell':index,'cellKey':cell['key'],'climbing':climbing,
        'catalogSha256':catalog_sha,'composition':placement['composition'],
        'originalOwnerUuid':actual_uuid(original['uuid']),'source':source,'pose':original['pose'],
        'registryId':original['registry_id'],'sourceClimbing':source_climbing,'sourceOwnerSelectedForRemoval':True}
    bridge=db.execute("SELECT host_state,retained_resident,type_index FROM bridge_cells WHERE dim='.' AND x=? AND y=? AND z=?",point).fetchone()
    if bridge is not None:
        state=json.loads(bridge['host_state'])
        require(bridge['type_index']==index and any(pair['state']==state and pair['cell']==index for pair in matrix['pairs']),'Ladder bridge must bind the exact final audited host/base pair')
        # This finite membership list constrains the runtime assertion; it is
        # not substituted for the writer's authoritative cached-BE validation.
        retained=db.execute('''SELECT DISTINCT candidates.uuid FROM (
            SELECT uuid FROM old.stored_contributions WHERE dim='.' AND x=? AND y=? AND z=?
            UNION SELECT uuid FROM old.stored_owners WHERE dim='.' AND x=? AND y=? AND z=?
            ) candidates WHERE NOT EXISTS(SELECT 1 FROM selected_objects s WHERE s.dim='.' AND s.uuid=candidates.uuid)''',(*point,*point)).fetchall()
        retained_uuids=sorted(actual_uuid(row['uuid']) for row in retained)
        require(retained_uuids and owner not in retained_uuids,'Ladder bridge lacks known original retained guests')
        binding.update(mode='bridge',hostState=state,retainedResident=bool(bridge['retained_resident']),
            retainedOwnerUuids=retained_uuids,matrixSha256=matrix_sha,
            retainedMembershipOrigin='original stored contributions/resident minus final selected owners; runtime exact-set assertion')
    ladder['migratedCell']=binding

def candidate_target_profile(plan):
    require(plan.get('variant')=='candidate','Target-profile correction is candidate-only')
    plan['authoringReferenceOnly']=False
    plan.pop('referenceScope',None)

def candidate(args):
    baseline=read(args.baseline_plan); db,meta=context(args.plan)
    reference_path=args.offline_reference or args.baseline_result
    if args.offline_reference:
        result=offline_reference(args,baseline,db,meta);reference_origin=OFFLINE_REFERENCE
    else:
        result=read(args.baseline_result)
        require(result.get('status')=='PASS' and result.get('variant')=='baseline','Candidate requires an actual PASS baseline report or explicit --offline-reference')
        require(result.get('planSha256')==sha(args.baseline_plan),'Baseline report does not bind the exact input plan')
        reference_origin='BASELINE_GAMEPLAY_PASS'
    plan=copy.deepcopy(baseline);plan['variant']='candidate';plan['targetWorldPath']=str(args.world.absolute());plan['productionJar']=str(args.jar.absolute());plan['productionSha256']=sha(args.jar);plan['output']=str((args.output/'candidate').absolute())
    candidate_target_profile(plan)
    plan['referenceOrigin']=reference_origin
    plan['referenceProvenance']={'path':str(reference_path.absolute()),'sha256':sha(reference_path),'baselinePlanSha256':sha(args.baseline_plan),'sourceJarSha256':baseline['productionSha256']}
    if args.offline_reference:
        plan['referenceProvenance']['baselineGameplayStatus']=result['baselineGameplayStatus'];plan['referenceProvenance']['baselineFullTargetStatus']=result['baselineFullTargetStatus']
    windows={row['key']:row for row in result['windows']}
    require(len(windows)==len(result['windows'])==len(plan['windows']) and set(windows)=={row['key'] for row in plan['windows']},'Reference exact windows are missing, duplicated or different')
    for row in plan['windows']:
        reference=windows[row['key']];require(reference['positions']==row['positions'] and reference['origin']==row['origin'],'Baseline shape addresses differ')
        row['referenceCollision']=reference['collision'];row['referenceOutline']=reference['outline']
    originals={tuple(row['pos']):row['reference'] for row in result['originalWitnesses']}
    require(len(originals)==len(result['originalWitnesses']),'Reference original addresses are duplicated')
    if args.offline_reference:require(set(originals)=={tuple(row['pos']) for row in plan['protected']},'Offline original snapshots must cover precisely the declared protected witnesses')
    for row in plan['protected']:
        row['reference']=originals[tuple(row['pos'])]
        for kind in ('state','fluid'):
            state=row['reference'][kind]
            require(isinstance(state,dict) and set(state)=={'registryId','properties'} and isinstance(state['registryId'],str) and isinstance(state['properties'],dict) and all(isinstance(k,str) and isinstance(v,str) for k,v in state['properties'].items()),'Protected reference needs the canonical registry/properties cell snapshot schema')
    with zipfile.ZipFile(args.jar) as production:
        require(hashlib.sha256(production.read('assets/bloodborne_dw/native_grid/catalog.json')).hexdigest()==sha(args.catalog),'Catalog is not the exact resource in the bound candidate JAR')
    catalog=read(args.catalog); cells=catalog['cells'];shapes=catalog['shapes'];whole={member['cell'] for whole in catalog['wholeObjects'] for member in whole['cells']}
    locations=[]
    for window in plan['windows']:
        for point in window['positions']:
            row=db.execute("SELECT p.type_index FROM placements p WHERE p.dim='.' AND p.x=? AND p.y=? AND p.z=? AND NOT EXISTS(SELECT 1 FROM bridge_cells b WHERE b.dim=p.dim AND b.x=p.x AND b.y=p.y AND b.z=p.z)",point).fetchone()
            if row is None:continue
            index=row[0]
            require(isinstance(index,int) and 0<=index<len(cells),'Placement types have not been bound to the compiled catalog')
            cell=cells[index]
            if index not in whole and not cell.get('fluid') and shapes[cell['outline']]:locations.append(point)
    require(locations,'No dry selectable independent compiled cell in the baseline windows')
    plan['staticCell']={'source':locations[0]}
    matrix_path=args.bridge_matrix or args.plan/'bridge-host-states.json'
    matrix=read(matrix_path)
    require(matrix.get('schemaVersion')==1 and matrix.get('catalogueSha256')==sha(args.catalog),'Bridge fixture matrix/catalog binding differs')
    bind_migrated_ladder(db,plan,catalog,sha(args.catalog),matrix,sha(matrix_path))
    door=next(row for row in plan['wholeObjects'] if row['kind']=='door')
    helper_offset=[0,-1,1]
    source_background=[coordinate+offset for coordinate,offset in zip(door['source'],helper_offset)]
    actual=db.execute("SELECT type_index,host_state FROM bridge_cells WHERE dim='.' AND x=? AND y=? AND z=?",source_background).fetchone()
    require(actual is not None and isinstance(actual[0],int),'Actual bound door floor contact lacks its compiled NativeGridBase type')
    actual_state=json.loads(actual[1])
    require(actual_state.get('registryId')=='bloodborne_dw:composite_cell','Actual door background must be a nonresident composite_cell helper')
    def volume(boxes):return sum(max(0,b[3]-b[0])*max(0,b[4]-b[1])*max(0,b[5]-b[2]) for b in boxes)
    admissible=[]
    for pair in matrix.get('pairs',[]):
        if pair['state']!=actual_state or pair['cell']!=actual[0]:continue
        index=pair['cell'];require(isinstance(index,int) and 0<=index<len(cells),'Invalid fixture background index')
        cell=cells[index];optics=catalog['optics'][cell['optics']]
        if index in whole or cell.get('fluid') or optics['luminance']!=0:continue
        require(optics['sidedTransparency'] is False,'Actual CELL fixture background still has the rejected pre-audit sided-transparency predicate; refresh catalog and bridge matrix')
        collision=shapes[cell['collision']];outline=shapes[cell['outline']]
        admissible.append(((volume(collision),volume(outline),len(collision),catalog['meshes'][cell['mesh']]['quadCount'],index),pair))
    require(admissible,'Need the exact actual door floor background in the refreshed admissible bridge matrix')
    score,pair=min(admissible,key=lambda item:item[0])
    source_proof_path=args.bridge_source_reference or RESEARCH/'bridge-door-background-source-proof.json'
    source_proof=read(source_proof_path);background=cells[pair['cell']]
    require(source_proof.get('status')=='PASS' and source_proof.get('position')==source_background and source_proof.get('typeIndex')==pair['cell'] and source_proof.get('catalogSha256')==sha(args.catalog) and source_proof.get('preservedDoorExcludedFromBackground') is True,'Actual floor-background source physics proof differs from the bound fixture')
    for kind in ('collision','outline'):
        require(source_proof['compiledShapeRefs'][kind]==background[kind] and source_proof['compiledShapes'][kind]==shapes[background[kind]] and source_proof['expectedShapeUnions'][kind]==shapes[background[kind]],'Fixture background shape differs from its exact source physics proof')
    door['bridgeFixture']={'cell':pair['cell'],'catalogSha256':sha(args.catalog),'matrixSha256':sha(matrix_path),
        'admissibleCarrier':pair['state'],'collisionVolume':score[0],'helperOffset':helper_offset,'sourceBackground':source_background,
        'sourceShapeProof':{'path':str(source_proof_path.absolute()),'sha256':sha(source_proof_path)},
        'independentBreakMode':'server interaction API; ray not applicable: exact original outline EMPTY' if not shapes[background['outline']] else 'native selection ray + server interaction API',
        'policy':'normal AIR open/close; then actual persistent floor-helper background open/close with exact unions -> whole break preserves ordinary background -> normal independent break'}
    write(args.output/'candidate-plan.json',plan)
    binding={'status':'INPUT_BOUND_NOT_QA_READY','referenceOrigin':reference_origin,'referenceProvenance':plan['referenceProvenance'],'catalogSha256':sha(args.catalog),'staticCell':plan['staticCell'],'bridgeFixture':door['bridgeFixture'],'migratedLadder':plan['ladder']['migratedCell'],'candidatePlanSha256':sha(args.output/'candidate-plan.json')}
    binding['offlineReferenceSha256' if args.offline_reference else 'baselineReportSha256']=sha(reference_path)
    write(args.output/'candidate-binding.json',binding)
    print('CANDIDATE_INPUT_BOUND_NOT_QA_READY: '+str(args.output/'candidate-plan.json'))

def parser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--plan', type=Path, default=PLAN)
    p.add_argument('--output', type=Path, default=OUT)
    commands = p.add_subparsers(dest='command', required=True)
    commands.add_parser('inspect').set_defaults(run=inspect)
    selection=commands.add_parser('select');selection.add_argument('--world',type=Path,default=RESEARCH/'pilot'/'baseline');selection.add_argument('--jar',type=Path,default=BASE_JAR);selection.add_argument('--statue-selection',type=Path);selection.set_defaults(run=select)
    c=commands.add_parser('candidate');c.add_argument('--baseline-plan',type=Path,default=OUT/'baseline-plan.json');c.add_argument('--baseline-result',type=Path,default=OUT/'baseline'/'functions.json');c.add_argument('--offline-reference',type=Path);c.add_argument('--catalog',type=Path,default=PROJECT/'src'/'architecture'/'resources'/'assets'/'bloodborne_dw'/'native_grid'/'catalog.json');c.add_argument('--world',type=Path,default=RESEARCH/'pilot'/'candidate');c.add_argument('--jar',type=Path,required=True);c.add_argument('--bridge-matrix',type=Path);c.add_argument('--bridge-source-reference',type=Path);c.set_defaults(run=candidate)
    return p

if __name__ == '__main__':
    args = parser().parse_args()
    args.run(args)
