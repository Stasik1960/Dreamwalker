"""Finite preserved-assembly overrides; preview is read-only, apply is root-only.

No world discovery or world-file writes. The main database may be changed only
after passed-pilot promotion; the current applied pilot is always refused.
"""
import argparse,collections,hashlib,json,sqlite3,traceback
from pathlib import Path
import migrate as m

POLICIES={'compiled_whole_or_local_original','local_original_functional','local_original_v12','local_original_due_uncertainty'}
DYNAMIC_PROTECTION={'functional_guest','whole_local_overlap','missing_exported_footprint'}

def data_sha(encoded):return hashlib.sha256(encoded.encode('utf-8')).hexdigest()

def open_plan(plan,writable=False):
    path=m.checked(plan)/'plan.sqlite';m.require(path.is_file(),'Use the one existing global plan')
    db=sqlite3.connect('file:'+path.as_posix()+('?mode=rw' if writable else '?mode=ro'),uri=True)
    db.execute('PRAGMA cache_size=-4096');db.execute('PRAGMA temp_store=FILE')
    old=m.checked(Path(m.meta(db,'oldPlan'))/'plan.sqlite');m.require(old.is_file(),'Saved source discovery is missing')
    db.execute('ATTACH DATABASE ? AS old',('file:'+old.as_posix()+'?mode=ro',));db.execute('PRAGMA old.cache_size=-4096')
    return db

def load_manifest(db,path,evidence_bindings=None):
    document=m.read(path);m.require(document.get('schemaVersion')==1 and document.get('status')=='CONFIRMED_FINITE_ASSEMBLY_MANIFEST' and document.get('sourceBinding')==m.meta(db,'binding'),'A final confirmed assembly manifest for this exact immutable source is required')
    redirected={}
    if evidence_bindings:
        index=m.read(evidence_bindings);m.require(index['schemaVersion']==1,'Unknown local assembly evidence binding schema')
        redirected={row['originalPath']:row for row in index['records']};m.require(len(redirected)==len(index['records']),'Ambiguous original-to-local evidence bindings')
    verified=set()
    def verify(proof):
        origin=Path(proof['path']);expected=proof['sha256'];path=origin
        if not origin.is_relative_to(m.ROOT):
            mapped=redirected.get(str(origin));m.require(mapped and mapped['sha256']==expected,'External assembly decision needs its exact staged local artifact: '+str(origin));path=Path(mapped['path']);m.require(path.is_relative_to(m.ROOT),'Historical evidence must resolve inside this isolated project')
        key=(str(path),expected)
        if key not in verified:m.require(m.sha(path)==expected,'Assembly decision source changed: '+str(path));verified.add(key)
    entries={}
    for entry in document['entries']:
        key=entry['key'];m.require(key not in entries and entry.get('matchScope')=='exact_pose','Only unique finite exact-pose assembly entries are accepted')
        preservation=entry['preservation'];m.require(preservation['route']=='whole' and preservation['runtimePolicy'] in POLICIES and type(preservation['requireOriginalRegistryId'])is bool,'Unrecognized whole assembly policy')
        m.require(preservation['requireOriginalRegistryId']==(preservation['runtimePolicy']=='local_original_v12'),'Exact old registry identity is required only for the protected v12 route')
        m.require(entry.get('currentMainItem') and entry.get('decisionSources'),'Assembly needs its primary item and existing decision evidence')
        m.require(entry.get('recipe',{}).get('components') or preservation['runtimePolicy']=='local_original_due_uncertainty','A compiled assembly needs its exact component recipe')
        for proof in entry['decisionSources']:verify(proof)
        entries[key]=entry
    m.require(entries,'An empty preserved assembly registry is not proof')
    matches={}
    for match in document['poseMatches']:
        pose=match['pose'];key=match['assemblyKey'];m.require(key in entries and match.get('matchEvidence'),'Each pose match needs an explicit entry and exact match evidence')
        row=db.execute('SELECT data FROM poses WHERE key=?',(pose,)).fetchone();m.require(row and data_sha(row[0])==match['poseDataSha256'],'Saved exact assembly SourcePose changed: '+pose)
        if pose not in matches:matches[pose]={'poseDataSha256':match['poseDataSha256'],'entries':[]}
        m.require(key not in matches[pose]['entries'],'Duplicate assembly-to-pose binding');matches[pose]['entries'].append(key)
    for row in matches.values():row['entries'].sort()
    return document,entries,matches

def intended(db,pose,kind,entry_keys,entries):
    policies={entries[key]['preservation']['runtimePolicy'] for key in entry_keys}
    if kind=='preserve':return 'preserve','existing_preservation_priority'
    if kind=='substrate':return 'preserve','preserved_assembly_existing_native_do_not_merge'
    if 'local_original_v12' in policies:return 'preserve','preserved_assembly_original_v12'
    if 'local_original_functional' in policies:return 'preserve','preserved_assembly_original_function'
    if 'local_original_due_uncertainty' in policies:return 'preserve','preserved_assembly_unproved_component_recipe'
    if kind=='whole':return 'whole','existing_whole_object'
    m.require(kind=='static','An unclassified source pose cannot be approved as a whole object')
    bounds=db.execute('SELECT count(*),max(max(abs(dx),abs(dy),abs(dz))),sum(dx=0 AND dy=0 AND dz=0) FROM pose_cells WHERE pose=?',(pose,)).fetchone()
    if not bounds[0]:return 'preserve','preserved_assembly_missing_exact_footprint'
    if bounds[1]>127:return 'preserve','preserved_assembly_whole_role_exceeds_127'
    if not bounds[2]:return 'preserve','preserved_assembly_whole_origin_missing'
    return 'whole','confirmed_preserved_assembly_template'

def make_candidates(db,entries,matches):
    db.execute('CREATE TEMP TABLE asm_poses(pose TEXT PRIMARY KEY,entries TEXT,pose_sha TEXT)')
    db.executemany('INSERT INTO asm_poses VALUES(?,?,?)',[(pose,m.canonical(row['entries']),row['poseDataSha256']) for pose,row in sorted(matches.items())])
    db.execute('CREATE TEMP TABLE asm_objects(dim TEXT,uuid TEXT,pose TEXT,x INTEGER,y INTEGER,z INTEGER,kind TEXT,reason TEXT,source_status TEXT,new_kind TEXT,new_reason TEXT,PRIMARY KEY(dim,uuid))')
    counts=collections.Counter();fingerprint=hashlib.sha256()
    for pose,match in sorted(matches.items()):
        for row in db.execute('SELECT dim,uuid,pose,x,y,z,kind,reason,source_status FROM objects WHERE pose=? ORDER BY dim,uuid',(pose,)):
            dim,uid,_,x,y,z,kind,reason,status=row;new_kind,new_reason=intended(db,pose,kind,match['entries'],entries)
            if kind=='static' and new_kind=='whole':
                real=db.execute('SELECT 1 FROM old.stored_owners WHERE dim=? AND uuid=? UNION ALL SELECT 1 FROM old.be_owners WHERE dim=? AND uuid=? LIMIT 1',(dim,uid,dim,uid)).fetchone()
                m.require(real,'A synthetic/native unit cannot be independently converted as a real whole owner')
            db.execute('INSERT INTO asm_objects VALUES(?,?,?,?,?,?,?,?,?,?,?)',(*row,new_kind,new_reason));fingerprint.update((m.canonical(row)+'\n').encode());counts[(kind,new_kind,new_reason)]+=1
    return [{'before':a,'after':b,'reason':reason,'objects':n} for (a,b,reason),n in sorted(counts.items())],fingerprint.hexdigest()

def intersections(db,stream):
    counts=collections.Counter()
    queries=(
        ('selected_static',"SELECT o.dim,o.uuid,o.pose,o.x,o.y,o.z FROM asm_objects o JOIN selected_objects s ON s.dim=o.dim AND s.uuid=o.uuid WHERE s.kind!='whole' OR o.new_kind!='whole'"),
        ('architectural_fragment',"SELECT DISTINCT o.dim,o.uuid,o.pose,o.x,o.y,o.z FROM fragments f CROSS JOIN asm_objects o WHERE o.dim=f.dim AND o.uuid=f.uuid"),
        ('activated_native',"SELECT o.dim,o.uuid,o.pose,o.x,o.y,o.z FROM asm_objects o JOIN activated_native a ON a.dim=o.dim AND a.uuid=o.uuid"),
        ('root_placement',"SELECT o.dim,o.uuid,o.pose,o.x,o.y,o.z FROM asm_objects o JOIN placements p ON p.dim=o.dim AND p.x=o.x AND p.y=o.y AND p.z=o.z LEFT JOIN selected_objects s ON s.dim=o.dim AND s.uuid=o.uuid LEFT JOIN bridge_cells b ON b.dim=p.dim AND b.x=p.x AND b.y=p.y AND b.z=p.z WHERE (coalesce(s.kind,'')!='whole' OR o.new_kind!='whole') AND NOT(o.kind IN ('preserve','static','whole') AND s.dim IS NULL AND b.dim IS NOT NULL)"),
        ('substrate_visual',"SELECT DISTINCT s.dim,s.x,s.y,s.z,s.pose FROM substrate_fragments s CROSS JOIN asm_poses p WHERE p.pose=s.pose"),
    )
    for kind,sql in queries:
        for row in db.execute(sql):stream.write(m.canonical({'kind':kind,'row':list(row)})+'\n');counts[kind]+=1
    return dict(counts)

def preview(plan,manifest,out,evidence_bindings=None):
    db=open_plan(plan)
    try:
        db.execute('BEGIN');document,entries,matches=load_manifest(db,manifest,evidence_bindings);counts,fingerprint=make_candidates(db,entries,matches)
        out=m.checked(out);out.parent.mkdir(parents=True,exist_ok=True);details=out.with_suffix('.intersections.jsonl')
        with details.open('w',encoding='utf-8',newline='\n') as stream:contacts=intersections(db,stream)
        per_pose=[];templates=m.meta(db,'wholeTemplates') or {};requests=[]
        for pose,row in sorted(matches.items()):
            kinds=dict(db.execute('SELECT new_kind,count(*) FROM asm_objects WHERE pose=? GROUP BY new_kind',(pose,)));per_pose.append({'pose':pose,'poseDataSha256':row['poseDataSha256'],'assemblyKeys':row['entries'],'objectsByIntendedKind':kinds})
            if kinds.get('whole') and pose not in templates:
                first=entries[row['entries'][0]];requests.append({'key':'wo_'+pose,'pose':pose,'displayName':first.get('displayName') or first['key'],'sourceMainItem':first['currentMainItem']})
        request_path=out.with_suffix('.whole-objects.jsonl')
        with request_path.open('w',encoding='utf-8',newline='\n') as stream:
            for row in requests:stream.write(m.canonical(row)+'\n')
        result={'schemaVersion':1,'status':'NEEDS_BOUNDED_PILOT_CORRECTION' if contacts else 'FINITE_PREVIEW_PASS','scope':'EXACT_POSE_MATCHES_ONLY','separateFiniteComponentCoverageRequired':True,'kind':'PRESERVED_ASSEMBLIES_POSE_ONLY_OVERRIDE_PREVIEW','sourceBinding':m.meta(db,'binding'),'manifest':{'path':str(m.checked(manifest)),'sha256':m.sha(manifest)},'evidenceBindings':{'path':str(m.checked(evidence_bindings)),'sha256':m.sha(evidence_bindings)} if evidence_bindings else None,'classification':m.meta(db,'classification'),'entryCount':len(entries),'matchedPoseCount':len(matches),'matchedObjects':sum(row['objects'] for row in counts),'matchedObjectsBeforeSha256':fingerprint,'changes':counts,'poses':per_pose,'pilotIntersections':contacts,'pilotIntersectionDetails':{'path':str(details),'sha256':m.sha(details)},'wholeTemplateRequests':{'path':str(request_path),'sha256':m.sha(request_path),'count':len(requests)},'activePilotApplied':bool(m.meta(db,'applyStarted')),'worldFilesRead':0,'persistentDatabaseWrites':0,'mapWrites':0,'consumerSha256':m.sha(__file__)}
        m.atomic_json(out,result);return result
    finally:db.close()

def protect(db):
    # Only the matched object's own coordinates/known footprint; never another
    # owner's neighbors or transitive owner graph.
    selections=("SELECT a.dim,a.x,a.y,a.z,a.uuid,a.new_reason FROM asm_objects a WHERE a.new_kind='preserve'",
        "SELECT a.dim,a.x+p.dx,a.y+p.dy,a.z+p.dz,a.uuid,a.new_reason FROM asm_objects a JOIN pose_cells p ON p.pose=a.pose WHERE a.new_kind='preserve'",
        "SELECT a.dim,c.x,c.y,c.z,a.uuid,a.new_reason FROM old.members c CROSS JOIN asm_objects a WHERE a.dim=c.dim AND a.uuid=c.uuid AND a.new_kind='preserve'",
        "SELECT a.dim,c.x,c.y,c.z,a.uuid,a.new_reason FROM old.stored_contributions c CROSS JOIN asm_objects a WHERE a.dim=c.dim AND a.uuid=c.uuid AND a.new_kind='preserve'")
    for selection in selections:
        db.execute('INSERT INTO protected_cells '+selection+" ON CONFLICT(dim,x,y,z,uuid) DO UPDATE SET reason=excluded.reason WHERE protected_cells.reason IN ('functional_guest','whole_local_overlap','missing_exported_footprint')")

def require_bound(db):
    bound=m.meta(db,'preservedAssemblyOverrides');m.require(bound and m.sha(bound['manifest']['path'])==bound['manifest']['sha256'] and m.sha(bound['receipt']['path'])==bound['receipt']['sha256'],'Import the exact preserved-assembly pose overrides before full composition')
    report=m.read(bound['receipt']['path']);m.require(report['status']=='PASS' and report['sourceBinding']==m.meta(db,'binding'),'Whole assembly override receipt belongs to another source')
    accepted=report['acceptedPreview'];m.require(m.sha(accepted['path'])==accepted['sha256'],'The accepted pose-only assembly preview changed')
    preview=m.read(accepted['path']);m.require(preview['status']=='FINITE_PREVIEW_PASS' and preview['manifest']==bound['manifest'] and preview.get('separateFiniteComponentCoverageRequired')is True,'A pose-only preview cannot substitute for finite component coverage')
    current=hashlib.sha256()
    for pose in sorted(row['pose'] for row in report['matchedPoses']):
        for row in db.execute('SELECT dim,uuid,pose,x,y,z,kind,reason,source_status FROM objects WHERE pose=? ORDER BY dim,uuid',(pose,)):current.update((m.canonical(row)+'\n').encode())
    m.require(current.hexdigest()==report['matchedObjectsAfterSha256'],'An imported exact-pose assembly kind/source decision changed')
    import assembly_components
    component=assembly_components.require_bound(db)
    component_receipt=m.read(component['path']);m.require(component_receipt['acceptedPosePreview']==accepted and component_receipt['manifest']==bound['manifest'] and component_receipt['poseOverrides']==bound['receipt'],'Pose/component assembly receipts disagree')
    return bound

def apply(plan,manifest,preview_path,expected_sha,out,evidence_bindings=None):
    m.require(m.sha(preview_path)==expected_sha,'Explicit accepted assembly preview changed');before=m.read(preview_path);m.require(before['status']=='FINITE_PREVIEW_PASS' and not before['pilotIntersections'],'Fragmented pilot assemblies need their own bounded correction before override import')
    db=open_plan(plan,True)
    try:
        db.execute('BEGIN IMMEDIATE');m.require(not m.meta(db,'applyStarted') and not m.meta(db,'scope') and m.meta(db,'successfulPilotGate') and m.meta(db,'fullNativeFunctionGateRequired'),'Only root may import assembly overrides after successful-pilot promotion')
        m.require(before['manifest']=={'path':str(m.checked(manifest)),'sha256':m.sha(manifest)} and before['sourceBinding']==m.meta(db,'binding') and before['classification']==m.meta(db,'classification'),'Assembly preview source/classification/manifest changed')
        evidence={'path':str(m.checked(evidence_bindings)),'sha256':m.sha(evidence_bindings)} if evidence_bindings else None;m.require(evidence==before.get('evidenceBindings'),'Staged evidence bindings changed since preview')
        document,entries,matches=load_manifest(db,manifest,evidence_bindings)
        prior=m.meta(db,'preservedAssemblyOverrides')
        if prior:
            m.require(prior['manifest']==before['manifest'] and prior['receipt']['path']==str(m.checked(out)) and m.sha(out)==prior['receipt']['sha256'],'A different assembly override already exists');saved=m.read(out)
            h=hashlib.sha256()
            for pose in sorted(matches):
                for row in db.execute('SELECT dim,uuid,pose,x,y,z,kind,reason,source_status FROM objects WHERE pose=? ORDER BY dim,uuid',(pose,)):h.update((m.canonical(row)+'\n').encode())
            m.require(saved['acceptedPreview']=={'path':str(m.checked(preview_path)),'sha256':expected_sha} and h.hexdigest()==saved['matchedObjectsAfterSha256'],'Committed assembly override changed');return saved
        counts,fingerprint=make_candidates(db,entries,matches);m.require(fingerprint==before['matchedObjectsBeforeSha256'],'An exact matched source object kind/payload classification changed since preview')
        templates=m.meta(db,'wholeTemplates') or {}
        for pose in matches:
            if db.execute("SELECT 1 FROM asm_objects WHERE pose=? AND new_kind='whole' LIMIT 1",(pose,)).fetchone():templates.setdefault(pose,'wo_'+pose)
        db.execute("UPDATE objects SET kind=(SELECT a.new_kind FROM asm_objects a WHERE a.dim=objects.dim AND a.uuid=objects.uuid),reason=CASE WHEN kind IN ('preserve','whole') THEN reason ELSE (SELECT a.new_reason FROM asm_objects a WHERE a.dim=objects.dim AND a.uuid=objects.uuid) END WHERE EXISTS(SELECT 1 FROM asm_objects a WHERE a.dim=objects.dim AND a.uuid=objects.uuid)")
        protect(db);m.meta(db,'wholeTemplates',templates)
        h=hashlib.sha256()
        for pose in sorted(matches):
            for row in db.execute('SELECT dim,uuid,pose,x,y,z,kind,reason,source_status FROM objects WHERE pose=? ORDER BY dim,uuid',(pose,)):h.update((m.canonical(row)+'\n').encode())
        report={'schemaVersion':1,'status':'PASS','scope':'EXACT_POSE_MATCHES_ONLY','separateFiniteComponentCoverageRequired':True,'kind':'PRESERVED_ASSEMBLIES_POSE_ONLY_OVERRIDES','sourceBinding':m.meta(db,'binding'),'manifest':before['manifest'],'evidenceBindings':evidence,'acceptedPreview':{'path':str(m.checked(preview_path)),'sha256':expected_sha},'matchedObjectsBeforeSha256':fingerprint,'matchedObjectsAfterSha256':h.hexdigest(),'changes':counts,'matchedPoses':before['poses'],'wholeTemplateRequests':before['wholeTemplateRequests'],'originalClassificationUnchanged':True,'noFamilyOverride':True,'noOwnerGraphClosure':True,'worldFilesRead':0,'mapWrites':0,'consumerSha256':m.sha(__file__)}
        m.atomic_json(out,report);m.meta(db,'preservedAssemblyOverrides',{'manifest':before['manifest'],'receipt':{'path':str(m.checked(out)),'sha256':m.sha(out)}});db.commit();return report
    finally:db.close()

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--plan',type=Path,default=m.DEFAULT_PLAN);parser.add_argument('--manifest',type=Path,required=True);parser.add_argument('--evidence-bindings',type=Path);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--apply',action='store_true');parser.add_argument('--preview',type=Path);parser.add_argument('--expected-preview-sha256');args=parser.parse_args();m.control_check()
    try:
        if args.apply:m.require(args.preview and args.expected_preview_sha256,'Root apply requires its exact accepted preview');result=apply(args.plan,args.manifest,args.preview,args.expected_preview_sha256,args.out,args.evidence_bindings)
        else:result=preview(args.plan,args.manifest,args.out,args.evidence_bindings)
        print(m.canonical({'status':result['status'],'out':str(args.out),'sha256':m.sha(args.out)}))
    except BaseException:m.atomic_json(args.out.with_suffix('.error.json'),{'status':'FAIL','error':traceback.format_exc(),'mapWrites':0});raise
if __name__=='__main__':main()
