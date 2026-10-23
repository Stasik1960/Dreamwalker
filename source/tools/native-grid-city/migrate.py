"""Native-grid disk planner and guarded, resumable offline writer.

The existing blocks.2 discovery database is attached read-only. This tool never
imports the ordinary.partial map or runs Minecraft. Coordinate/owner data stays
in this disk plan; the production catalogue contains only shared cell types.
"""
from __future__ import annotations
import argparse, collections, gzip, hashlib, json, math, os, re, shutil, sqlite3
import struct, subprocess, sys, time, traceback, uuid, zipfile, zlib
from functools import lru_cache
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0,str(Path(__file__).resolve().parent))
import context_stage
import native_resume

def module_api():
    return sys.modules.get(__name__) or SimpleNamespace(**globals())

PROJECT = Path(__file__).resolve().parents[2]
ROOT = PROJECT.parent
sys.path.insert(0, str(PROJECT / 'tools'))
from world_io import (Tag, NbtFile, NbtError, compound, decode_nbt, encode_nbt,
                      read_nbt, write_nbt, section_blocks, block_state_key,
                      pack_palette_indices, invalidate_chunk_lighting)

DEFAULT_OLD = Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-ordinary-city-pilot-20261020\research\city-pilot\full-city-copy.ordinary-migration')
DEFAULT_PLAN = ROOT / 'research' / 'native-grid-plan'
CATALOG_REL = 'assets/bloodborne_dw/native_grid/catalog.json'
AIR = {'registryId': 'minecraft:air', 'properties': {}}
SCHEMA = '''
PRAGMA journal_mode=WAL;
PRAGMA synchronous=FULL;
PRAGMA temp_store=FILE;
PRAGMA cache_size=-32768;
CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT);
CREATE TABLE IF NOT EXISTS poses(key TEXT PRIMARY KEY,data TEXT NOT NULL,objects INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS objects(dim TEXT,uuid TEXT,pose TEXT,x INTEGER,y INTEGER,z INTEGER,
 kind TEXT DEFAULT 'unclassified',reason TEXT,source_status TEXT,PRIMARY KEY(dim,uuid));
CREATE INDEX IF NOT EXISTS object_pose ON objects(pose);
CREATE INDEX IF NOT EXISTS object_pose_zone ON objects(pose,dim,x,z,y);
CREATE INDEX IF NOT EXISTS object_root ON objects(dim,x,z);
CREATE TABLE IF NOT EXISTS pose_cells(pose TEXT,dx INTEGER,dy INTEGER,dz INTEGER,PRIMARY KEY(pose,dx,dy,dz));
CREATE TABLE IF NOT EXISTS protected_cells(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,reason TEXT,PRIMARY KEY(dim,x,y,z,uuid));
CREATE INDEX IF NOT EXISTS protected_pos ON protected_cells(dim,x,y,z);
CREATE TABLE IF NOT EXISTS source_visual_domains(dim TEXT,uuid TEXT,data TEXT NOT NULL,digest TEXT NOT NULL,PRIMARY KEY(dim,uuid));
CREATE TABLE IF NOT EXISTS source_visual_contacts(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,incoming TEXT,data TEXT NOT NULL,digest TEXT NOT NULL,PRIMARY KEY(dim,x,y,z,uuid,incoming));
CREATE TABLE IF NOT EXISTS source_visual_owner_members(dim TEXT,uuid TEXT,sourceTable TEXT,x INTEGER,y INTEGER,z INTEGER,PRIMARY KEY(dim,uuid,sourceTable,x,y,z)) WITHOUT ROWID;
CREATE TABLE IF NOT EXISTS source_local_retentions(sequence INTEGER PRIMARY KEY,data TEXT NOT NULL,digest TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS selected_objects(dim TEXT,uuid TEXT,pose TEXT,x INTEGER,y INTEGER,z INTEGER,kind TEXT,PRIMARY KEY(dim,uuid));
CREATE INDEX IF NOT EXISTS selected_root ON selected_objects(dim,x,y,z);
CREATE TABLE IF NOT EXISTS whole_cells(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,template TEXT,dx INTEGER,dy INTEGER,dz INTEGER,PRIMARY KEY(dim,x,y,z));
CREATE TABLE IF NOT EXISTS fragments(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,pose TEXT,dx INTEGER,dy INTEGER,dz INTEGER,PRIMARY KEY(dim,x,y,z,uuid));
CREATE INDEX IF NOT EXISTS fragment_chunk ON fragments(dim,x,z);
CREATE INDEX IF NOT EXISTS fragment_owner ON fragments(dim,uuid);
CREATE INDEX IF NOT EXISTS fragment_native_chunk ON fragments(dim,(x>>4),(z>>4),x,y,z);
CREATE TABLE IF NOT EXISTS substrate_fragments(dim TEXT,x INTEGER,y INTEGER,z INTEGER,uuid TEXT,pose TEXT,dx INTEGER,dy INTEGER,dz INTEGER,PRIMARY KEY(dim,x,y,z,uuid));
CREATE INDEX IF NOT EXISTS substrate_owner ON substrate_fragments(dim,uuid);
CREATE TABLE IF NOT EXISTS cell_queue(dim TEXT,x INTEGER,y INTEGER,z INTEGER,processed INTEGER DEFAULT 0,PRIMARY KEY(dim,x,y,z));
CREATE INDEX IF NOT EXISTS queue_work ON cell_queue(processed,dim,(x>>4),(z>>4));
CREATE TABLE IF NOT EXISTS activated_native(dim TEXT,uuid TEXT,PRIMARY KEY(dim,uuid));
CREATE TABLE IF NOT EXISTS native_visual_roots(dim TEXT,x INTEGER,y INTEGER,z INTEGER,state TEXT,PRIMARY KEY(dim,x,y,z));
CREATE TABLE IF NOT EXISTS native_visual_fragments(dim TEXT,x INTEGER,y INTEGER,z INTEGER,rx INTEGER,ry INTEGER,rz INTEGER,state TEXT,dx INTEGER,dy INTEGER,dz INTEGER,PRIMARY KEY(dim,x,y,z,rx,ry,rz));
CREATE INDEX IF NOT EXISTS native_visual_owner ON native_visual_fragments(dim,rx,ry,rz);
CREATE TABLE IF NOT EXISTS bridge_cells(dim TEXT,x INTEGER,y INTEGER,z INTEGER,host_state TEXT,retained_resident INTEGER,type_index INTEGER,PRIMARY KEY(dim,x,y,z));
CREATE INDEX IF NOT EXISTS bridge_chunk ON bridge_cells(dim,(x>>4),(z>>4));
CREATE TABLE IF NOT EXISTS compositions(key TEXT PRIMARY KEY,data TEXT NOT NULL,type_index INTEGER);
CREATE TABLE IF NOT EXISTS placements(dim TEXT,x INTEGER,y INTEGER,z INTEGER,cx INTEGER,cz INTEGER,
 composition TEXT,type_index INTEGER,source_state TEXT NOT NULL,optics_equal INTEGER DEFAULT 0,
 PRIMARY KEY(dim,x,y,z));
CREATE INDEX IF NOT EXISTS placement_chunk ON placements(dim,cx,cz);
CREATE TABLE IF NOT EXISTS source_chunks(dim TEXT,cx INTEGER,cz INTEGER,region TEXT,hash TEXT,PRIMARY KEY(dim,cx,cz));
CREATE TABLE IF NOT EXISTS regions(path TEXT PRIMARY KEY,before_hash TEXT,after_hash TEXT,phase TEXT DEFAULT 'planned',stats TEXT);
CREATE TABLE IF NOT EXISTS files(path TEXT PRIMARY KEY,before_hash TEXT,after_hash TEXT,phase TEXT,kind TEXT,stats TEXT);
CREATE TABLE IF NOT EXISTS cleanup_owners(dim TEXT,uuid TEXT,kind TEXT,PRIMARY KEY(dim,uuid));
CREATE TABLE IF NOT EXISTS relight(dim TEXT,cx INTEGER,cz INTEGER,reason TEXT,status TEXT DEFAULT 'pending',receipt TEXT,PRIMARY KEY(dim,cx,cz));
CREATE TABLE IF NOT EXISTS issues(kind TEXT,dim TEXT,x INTEGER,y INTEGER,z INTEGER,detail TEXT,PRIMARY KEY(kind,dim,x,y,z,detail));
'''

def require(condition, message):
    if not condition: raise RuntimeError(message)

def read(path): return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def canonical(value): return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))
def digest(value): return hashlib.sha256(canonical(value).encode()).hexdigest()
def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): h.update(block)
    return h.hexdigest()

def checked(path):
    path = Path(os.path.abspath(path))
    require(path.drive.upper() == 'C:', 'Only the paused-migration LocalC binding is allowed')
    for part in reversed([path, *path.parents]):
        if part.exists():
            require(not part.is_symlink() and not (getattr(part.lstat(), 'st_file_attributes', 0) & 1024), 'Reparse point refused: ' + str(part))
    return path

def files(path):
    for item in sorted(checked(path).iterdir()):
        checked(item)
        if item.is_dir(): yield from files(item)
        elif item.is_file(): yield item

def atomic_json(path, value):
    path = checked(path); path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.writing')
    with temporary.open('w', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2); stream.write('\n'); stream.flush(); os.fsync(stream.fileno())
    os.replace(temporary, path)

def control_check():
    control = Path(r'C:\Users\vakir\CodexControl')
    state = read(control / 'storage-mode.json')
    require(state['Mode'] == 'LocalC' and state['ExternalMigrationPaused'], 'Expected LocalC with external migration paused')
    (control / 'C-return-result.txt').read_text(encoding='utf-8-sig')
    result = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(control / 'Guard.ps1'), '-Mode', 'Check'], capture_output=True, text=True)
    require(result.returncode == 0, 'Guard Check failed: ' + result.stdout + result.stderr)

def connect(plan, old=None):
    plan = checked(plan); plan.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(plan / 'plan.sqlite',uri=True); db.executescript(SCHEMA)
    old = old or meta(db, 'oldPlan')
    if old:
        uri = 'file:' + checked(Path(old) / 'plan.sqlite').as_posix() + '?mode=ro'
        db.execute('ATTACH DATABASE ? AS old', (uri,))
    return db

def meta(db, key, value=None):
    if value is None:
        row = db.execute('SELECT value FROM meta WHERE key=?', (key,)).fetchone()
        return json.loads(row[0]) if row else None
    db.execute('INSERT OR REPLACE INTO meta VALUES(?,?)', (key, canonical(value)))

def progress(plan, db, phase, **extra):
    counts = {name: db.execute('SELECT count(*) FROM ' + name).fetchone()[0] for name in ('poses','objects','selected_objects','placements','cleanup_owners','relight')}
    atomic_json(plan / 'progress.json', {'schemaVersion':1, 'phase':phase, 'updatedUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()), 'counts':counts, **extra})

def state_json(tag):
    data = compound(tag)
    return {'registryId':data['Name'].value, 'properties':{k:v.value for k,v in data.get('Properties',Tag(10,{})).value.items()}}

def state_tag(state):
    out = {'Name':Tag(8,state.get('registryId',state.get('id')))}
    if state.get('properties'): out['Properties'] = Tag(10,{k:Tag(8,str(v)) for k,v in state['properties'].items()})
    return Tag(10,out)

def owner_uuid(owner): return canonical(compound(owner)['uuid'].value)
def be_owners(data):
    if 'OwnerDataV1' in data: return compound(decode_nbt(bytes(data['OwnerDataV1'].value)).root).get('owners',Tag(9,[],10)).value
    return data.get('owners',Tag(9,[],10)).value

def issue(db, kind, dim, pos, detail):
    db.execute('INSERT OR IGNORE INTO issues VALUES(?,?,?,?,?,?)',(kind,dim,*pos,canonical(detail)))

def source_binding(old, verify=False):
    old = checked(old); manifest = read(old / 'copy.json'); source = checked(manifest['source'])
    require(source.name == 'bloodborne-blocks-fixed-2', 'Only the immutable blocks.2 source is supported')
    require((old / 'read-only-plan.json').is_file(), 'Original discovery must retain its read-only marker')
    require(not (old / 'plan-in-progress.json').exists(), 'Original discovery remains incomplete')
    if verify:
        for rel, row in manifest['files'].items():
            path = checked(source / rel)
            require(path.is_file() and path.stat().st_size == row['bytes'] and sha(path) == row['sha256'], 'Source binding mismatch: ' + rel)
    database = old / 'plan.sqlite'
    return {'schemaVersion':1,'source':str(source),'oldPlan':str(old),'copyManifest':str(old/'copy.json'),
            'copyManifestSha256':sha(old/'copy.json'),'oldDatabaseBytes':database.stat().st_size,
            'oldDatabaseMtimeNs':database.stat().st_mtime_ns,'sourceFileCount':len(manifest['files']),
            'sourceBytes':sum(r['bytes'] for r in manifest['files'].values()),'sourceHashesVerified':verify,
            'plannerBindingSha256':sha(old/'planner-binding.json'),'originalPlanReadOnly':True}

def prepare(a):
    plan = checked(a.plan); db = connect(plan,a.source_plan)
    binding = source_binding(a.source_plan,a.verify_source)
    previous = meta(db,'binding')
    require(previous is None or all(previous.get(k)==binding.get(k) for k in ('source','copyManifestSha256','oldDatabaseBytes','oldDatabaseMtimeNs')), 'Source checkpoint changed since preparation')
    if previous and previous.get('sourceHashesVerified'): binding['sourceHashesVerified']=True
    meta(db,'oldPlan',str(checked(a.source_plan))); meta(db,'binding',binding); db.commit()
    atomic_json(plan/'source-binding.json',binding)
    cursor = meta(db,'importCursor') or 0
    cache = {}; pending = []; imported = 0
    for rowid,dim,uid,rid,encoded,x,y,z,status in db.execute('SELECT rowid,dim,uuid,registry_id,pose_data,x,y,z,status FROM old.candidates WHERE rowid>? ORDER BY rowid',(cursor,)):
        key = cache.get(encoded)
        if key is None:
            value = json.loads(encoded); key='sp_'+digest(value)[:24]; cache[encoded]=key
            db.execute('INSERT OR IGNORE INTO poses(key,data) VALUES(?,?)',(key,canonical(value)))
        pending.append((dim,uid,key,x,y,z,'unclassified',None,status)); imported+=1; cursor=rowid
        if len(pending)>=10000:
            db.executemany('INSERT OR IGNORE INTO objects VALUES(?,?,?,?,?,?,?,?,?)',pending); pending.clear()
            meta(db,'importCursor',cursor); db.commit(); progress(plan,db,'IMPORTING',lastSourceRowid=cursor)
    if pending: db.executemany('INSERT OR IGNORE INTO objects VALUES(?,?,?,?,?,?,?,?,?)',pending)
    meta(db,'importCursor',cursor)
    db.execute('UPDATE poses SET objects=(SELECT count(*) FROM objects WHERE objects.pose=poses.key)'); db.commit()
    poses=[]
    for key,data,count in db.execute('SELECT key,data,objects FROM poses ORDER BY key'):
        poses.append({'key':key,**json.loads(data),'objectCount':count})
    atomic_json(ROOT/'research'/'source-poses.json',{'schemaVersion':1,'sourceBinding':binding,'poses':poses,'nativeStates':native_requests()})
    progress(plan,db,'SOURCE_IMPORTED',lastSourceRowid=cursor,sourcePoseRequest=str(ROOT/'research'/'source-poses.json'))

def export_poses(a):
    """Publish the small unique-pose request before the coordinate import."""
    plan=checked(a.plan);db=connect(plan,a.source_plan);binding=source_binding(a.source_plan,a.verify_source)
    previous=meta(db,'binding')
    require(previous is None or all(previous.get(k)==binding.get(k) for k in ('source','copyManifestSha256','oldDatabaseBytes','oldDatabaseMtimeNs')),'Source checkpoint changed')
    if previous and previous.get('sourceHashesVerified'):binding['sourceHashesVerified']=True
    meta(db,'oldPlan',str(checked(a.source_plan)));meta(db,'binding',binding);db.commit();atomic_json(plan/'source-binding.json',binding)
    poses=[]
    for encoded,count in db.execute('SELECT pose_data,count(*) FROM old.candidates GROUP BY pose_data'):
        value=json.loads(encoded);key='sp_'+digest(value)[:24]
        db.execute('INSERT OR REPLACE INTO poses VALUES(?,?,?)',(key,canonical(value),count));poses.append({'key':key,**value,'objectCount':count})
    db.commit();poses.sort(key=lambda row:row['key'])
    atomic_json(ROOT/'research'/'source-poses.json',{'schemaVersion':1,'sourceBinding':binding,'poses':poses,'nativeStates':native_requests()})
    progress(plan,db,'POSE_REQUEST_READY',sourcePoseRequest=str(ROOT/'research'/'source-poses.json'))

def native_requests():
    rows=read(DEFAULT_OLD.parents[2]/'tools'/'ordinary-stage1'/'native-supports-canonical.json')['states']
    states={canonical({'registryId':r['registryId'],'properties':r.get('properties',{})}):{'registryId':r['registryId'],'properties':r.get('properties',{})} for r in rows}
    return [states[key] for key in sorted(states)]

def classify(a):
    db=connect(a.plan); doc=read(a.classification)
    require(not meta(db,'applyStarted'), 'Cannot reclassify an applied plan')
    poses=doc.get('poses',{}); registry=doc.get('registryIds',{})
    if isinstance(poses,list): poses={r['key']:r for r in poses}
    if isinstance(registry,list): registry={r['registryId']:r for r in registry}
    for key,encoded in db.execute('SELECT key,data FROM poses'):
        pose=json.loads(encoded); row=poses.get(key,registry.get(pose['registryId'],{}))
        if isinstance(row,str):row={'kind':row}
        kind=row.get('kind',row.get('disposition',row.get('route','preserve')))
        require(kind in ('static','whole','preserve'),'Invalid classification kind: '+str(kind))
        reason=row.get('reason','unclassified_type' if not row else kind)
        db.execute('UPDATE objects SET kind=?,reason=? WHERE pose=?',(kind,reason,key))
        if not row.get('payloadProvenGeometric',False):
            db.execute("UPDATE objects SET kind='preserve',reason='unclassified_private_payload' WHERE pose=? AND source_status='private_payload' AND kind IN ('static','whole')",(key,))
    # The old discovery expands existing native/static states into synthetic
    # source-pose units. They are substrate, not individual decorative BE
    # owners. Leave isolated compact/native cells unchanged; include the actual
    # installed BlockState as baseState only when a real migrated owner meets it.
    db.execute("UPDATE objects SET kind='substrate',reason='existing_native_static_substrate' WHERE kind='static' AND NOT EXISTS(SELECT 1 FROM old.stored_owners s WHERE s.dim=objects.dim AND s.uuid=objects.uuid) AND NOT EXISTS(SELECT 1 FROM old.be_owners b WHERE b.dim=objects.dim AND b.uuid=objects.uuid)")
    meta(db,'classification',{'path':str(checked(a.classification)),'sha256':sha(a.classification)})
    meta(db,'wholeTemplates',{key:row.get('template','wo_'+key) for key,row in poses.items() if isinstance(row,dict) and row.get('kind',row.get('route'))=='whole'})
    db.execute('DELETE FROM protected_cells')
    db.execute("INSERT OR IGNORE INTO protected_cells SELECT o.dim,o.x,o.y,o.z,o.uuid,o.reason FROM objects o WHERE o.kind='preserve'")
    db.execute("INSERT OR IGNORE INTO protected_cells SELECT c.dim,c.x,c.y,c.z,c.uuid,o.reason FROM old.stored_contributions c CROSS JOIN objects o ON o.dim=c.dim AND o.uuid=c.uuid WHERE o.kind='preserve'")
    db.execute("INSERT OR IGNORE INTO protected_cells SELECT c.dim,c.x,c.y,c.z,c.uuid,o.reason FROM old.members c CROSS JOIN objects o ON o.dim=c.dim AND o.uuid=c.uuid WHERE o.kind='preserve'")
    db.commit(); progress(checked(a.plan),db,'CLASSIFIED')

def import_fragments(a):
    db=connect(a.plan); require(not meta(db,'applyStarted'),'Applied plan is frozen')
    count=0
    if checked(a.fragments).suffix=='.sqlite':
        source=sqlite3.connect('file:'+checked(a.fragments).as_posix()+'?mode=ro',uri=True)
        db.executemany('INSERT OR IGNORE INTO pose_cells VALUES(?,?,?,?)',source.execute('SELECT pose,x,y,z FROM fragments'))
        count=db.execute('SELECT count(*) FROM pose_cells').fetchone()[0];source.close()
        meta(db,'poseFragments',{'path':str(checked(a.fragments)),'sha256':sha(a.fragments)})
        db.commit();progress(checked(a.plan),db,'POSE_FOOTPRINTS_IMPORTED',fragments=count);return
    with checked(a.fragments).open(encoding='utf-8-sig') as stream:
        for line in stream:
            row=json.loads(line); key=row.get('key',row.get('pose'))
            cells=row.get('cells',row.get('fragments',[]))
            for item in cells:
                cell=item.get('cell',item.get('offset')) if isinstance(item,dict) else item
                require(isinstance(cell,list) and len(cell)==3 and all(type(v)is int for v in cell),'Expected integer relative source-root cell offset')
                db.execute('INSERT OR IGNORE INTO pose_cells VALUES(?,?,?,?)',(key,*cell));count+=1
    meta(db,'poseFragments',{'path':str(checked(a.fragments)),'sha256':sha(a.fragments)})
    db.commit(); progress(checked(a.plan),db,'POSE_FOOTPRINTS_IMPORTED',fragments=count)

def get_source_chunk(db,dim,cx,cz):
    binding=meta(db,'binding'); source=Path(binding['source']); manifest=read(binding['copyManifest'])
    row=db.execute('SELECT region_path,region_hash,compression,payload FROM old.chunk_cache WHERE dim=? AND x=? AND z=?',(dim,cx,cz)).fetchone()
    if row:
        rel,before,compression,payload=row
        require(manifest['files'][rel]['sha256']==before,'Cached source chunk belongs to another region snapshot')
        require(compression in (1,2,3),'External/unknown source chunk compression')
        return decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]),rel,before
    rel=(Path(dim)/'region'/('r.%d.%d.mca'%(cx>>5,cz>>5))).as_posix()
    if rel.startswith('./'):rel=rel[2:]
    path=checked(source/rel); require(rel in manifest['files'],'Destination chunk is absent from original source: '+rel)
    require(sha(path)==manifest['files'][rel]['sha256'],'Addressed source region changed: '+rel)
    with path.open('rb') as stream:
        stream.seek(((cx&31)+((cz&31)*32))*4); loc=int.from_bytes(stream.read(4),'big'); require(loc>>8,'Destination chunk is absent')
        stream.seek((loc>>8)*4096); length=int.from_bytes(stream.read(4),'big'); compression=stream.read(1)[0];payload=stream.read(length-1)
    require(compression in (1,2,3),'External chunk unsupported')
    return decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]),rel,manifest['files'][rel]['sha256']

def source_state(sections,pos):
    sy=pos[1]>>4; section=sections.get(sy)
    if section is None:return AIR
    palette,values=section if isinstance(section,tuple) else section_blocks(section);at=(pos[0]&15)+((pos[2]&15)*16)+((pos[1]&15)*256)
    return state_json(palette[values[at]])

@lru_cache(maxsize=1)
def legacy_native_catalog():
    doc=read(PROJECT/'src'/'architecture'/'resources'/'bloodborne_dw'/'static-architecture-v1274.json')
    overlays={r['id']:r['overlayRecipe'] for r in read(PROJECT/'src'/'architecture'/'resources'/'bloodborne_dw'/'restored-material-slabs.json')['materials'] if 'overlayRecipe' in r}
    return doc,overlays

def synthetic_uuid(dim,root,pose):
    raw=hashlib.sha256((dim+'/'+str(tuple(root))+'/'+pose).encode()).digest()[:16]
    return canonical(list(struct.unpack('>4i',raw)))

def native_roots_at(db,dim,pos,actual,native=None):
    """A consumed helper activates its exact root via saved recipe origins."""
    # A complete actual native model already supplies its own visual. Legacy
    # SourcePose expansion is not an alternate model for a compact/native state.
    if native and native.get('nativeVisualComplete',True):return []
    doc,overlays=legacy_native_catalog();rid=actual['registryId'];recipe=None
    if rid.startswith('bloodborne_dw:static_architecture_'):
        recipe=int(rid.rsplit('_',1)[1])*128+int(actual.get('properties',{}).get('part',0))
    elif rid in overlays:recipe=overlays[rid]
    if recipe is None:
        return db.execute("SELECT uuid,pose,x,y,z FROM objects WHERE dim=? AND x=? AND y=? AND z=? AND kind='substrate'",(dim,*pos)).fetchall()
    result=[]
    for fragment in doc['recipes'][recipe]['fragments']:
        origin=fragment['origin'];root=tuple(pos[i]+origin[i] for i in range(3));uid=synthetic_uuid(dim,root,fragment['pose'])
        row=db.execute('SELECT uuid,pose,x,y,z,kind,reason FROM objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone()
        require(row is not None,'Saved native recipe references an unrecorded synthetic root: '+str((dim,root,fragment['pose'])))
        if row[5]=='whole' and db.execute("SELECT 1 FROM selected_objects WHERE dim=? AND uuid=? AND kind='whole'",(dim,uid)).fetchone():
            # The whole template already owns its complete role footprint.
            # Do not route that same sculpture through architectural fragments.
            continue
        require(row[5]=='substrate','A consumed native helper belongs to a retained/protected local object: '+str((dim,uid,row[5],row[6])))
        result.append(row[:5])
    return result

def functional_be_conflict(db,dim,be):
    """Before compilation, a state without BE must never consume real data."""
    rid=be.get('id',Tag(8,'')).value;owners=be_owners(be)
    if not owners:return {'id':rid,'reason':'foreign_block_entity_or_unclassified_resident'}
    # These are the complete fields written by CompositeBlockEntity. Unknown
    # outer fields are not silently treated as geometric owner bookkeeping.
    allowed={'id','x','y','z','keepPacked','payload','resident','owners','OwnerDataV1'}
    if rid=='bloodborne_dw:source_ladder_owner':allowed.update({'Owner','Root','FixedBacking','Original'})
    extra=set(be)-allowed
    if extra:return {'id':rid,'reason':'private_outer_game_data','keys':sorted(extra)}
    retained=[]
    for item in owners:
        uid=owner_uuid(compound(item)['owner'])
        if not db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone():retained.append(uid)
    if 'resident' in be:
        uid=owner_uuid(be['resident'])
        if not db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone():retained.append(uid)
    if retained:return {'id':rid,'reason':'retained_local_owner','owners':sorted(set(retained))}
    return None

def bridge_candidate(db,dim,be,detail):
    if not detail or detail.get('reason')!='retained_local_owner':return False
    if be.get('id',Tag(8,'')).value!='bloodborne_dw:composite_cell':return False
    # A genuine foreign container or unknown owner payload never becomes a
    # wrapper. Only the installed known CompositeBE owner format is supported.
    return all(db.execute('SELECT 1 FROM objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone() for uid in detail['owners'])

def record_bridge(db,dim,be,state):
    at=tuple(be[k].value for k in ('x','y','z'));resident='resident' in be and not db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',(dim,owner_uuid(be['resident']))).fetchone()
    # A removed decorative resident no longer supplies its original source
    # block predicates. The existing generic helper keeps retained guest data.
    host=state if resident else {'registryId':'bloodborne_dw:composite_cell','properties':{}}
    db.execute('INSERT OR REPLACE INTO bridge_cells VALUES(?,?,?,?,?,?,NULL)',(dim,*at,canonical(host),int(bool(resident))))

def preflight_functional_guests(db,plan):
    """Address saved chunks only, retain directly colliding finite objects."""
    conflicts=[];blocked=set();bridged=0
    chunks=db.execute('SELECT DISTINCT dim,x>>4,z>>4 FROM fragments ORDER BY dim,x>>4,z>>4').fetchall()
    for dim,cx,cz in chunks:
        nbt,_,_=get_source_chunk(db,dim,cx,cz);root=compound(nbt.root)
        sections={compound(s)['Y'].value:section_blocks(s) for s in root.get('sections',Tag(9,[],10)).value}
        for tag in root.get('block_entities',Tag(9,[],10)).value:
            be=compound(tag);at=tuple(be[k].value for k in ('x','y','z'))
            touching=db.execute('SELECT DISTINCT uuid FROM fragments WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchall()
            if not touching:continue
            detail=functional_be_conflict(db,dim,be)
            if detail is None:continue
            if bridge_candidate(db,dim,be,detail):
                record_bridge(db,dim,be,source_state(sections,at));bridged+=1;continue
            detail={**detail,'cell':list(at),'dimension':dim,'directlyConflictingOwners':[row[0] for row in touching]}
            conflicts.append(detail);blocked.update((dim,row[0]) for row in touching)
            issue(db,'functional_guest',dim,at,detail)
        if meta(db,'contextExtension') or meta(db,'fullNativeFunctionGateRequired'):
            for at,detail,uids in context_stage.native_function_conflicts(module_api(),db,dim,cx,cz,sections,root):
                detail={**detail,'cell':list(at),'dimension':dim,'directlyConflictingOwners':uids};conflicts.append(detail);blocked.update((dim,uid) for uid in uids);issue(db,'functional_guest',dim,at,detail)
    for dim,uid in sorted(blocked):
        # Protect this particular finite source object. Do not walk the old
        # owner membership graph or expand a district around a container.
        db.execute("INSERT OR IGNORE INTO protected_cells SELECT dim,x,y,z,uuid,'functional_guest' FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=?",(dim,uid))
        db.execute('DELETE FROM fragments WHERE dim=? AND uuid=?',(dim,uid));db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid))
    report={'schemaVersion':1,'status':'PASS','worldReads':0,'addressedSavedChunks':len(chunks),'functionalGuestCells':len(conflicts),'existingCompositeBridges':bridged,'directlyRetainedObjects':len(blocked),'conflicts':conflicts,'graphClosure':False}
    path=plan/'functional-guest-preflight.json';atomic_json(path,report);meta(db,'functionalGuestPreflight',{'path':str(path),'sha256':sha(path)})
    return report

def verify_planned_be_safety(db,plan):
    conflicts=[]
    for dim,cx,cz in db.execute('SELECT DISTINCT dim,cx,cz FROM placements ORDER BY dim,cx,cz').fetchall():
        nbt,_,_=get_source_chunk(db,dim,cx,cz);root=compound(nbt.root)
        sections={compound(s)['Y'].value:section_blocks(s) for s in root.get('sections',Tag(9,[],10)).value}
        for tag in root.get('block_entities',Tag(9,[],10)).value:
            be=compound(tag);at=tuple(be[k].value for k in ('x','y','z'))
            if not db.execute('SELECT 1 FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone():continue
            detail=functional_be_conflict(db,dim,be)
            if bridge_candidate(db,dim,be,detail):
                record_bridge(db,dim,be,source_state(sections,at));continue
            if detail:
                conflicts.append({'dimension':dim,'cell':list(at),**detail});issue(db,'unresolved_functional_guest',dim,at,detail)
    report={'schemaVersion':1,'status':'PASS' if not conflicts else 'FAIL','conflicts':conflicts,'worldReads':0}
    path=plan/'planned-be-safety.json';atomic_json(path,report);meta(db,'plannedBeSafety',{'path':str(path),'sha256':sha(path),'status':report['status']})
    db.commit();require(not conflicts,'Functional guest remains in a planned replacement; final compilation is blocked: '+str(path))

def compose(a):
    db=connect(a.plan); plan=checked(a.plan)
    require(meta(db,'classification') and meta(db,'poseFragments'),'Classification and exported pose footprints required')
    require(not meta(db,'applyStarted'),'Applied plan is frozen')
    require(not meta(db,'nativeLocalRetention'),'A committed native transfer must use resume-native-compose; initial selection cannot be reset')
    scope={'dimension':a.dimension,'rootBounds':a.bounds} if a.bounds else None
    if not scope and meta(db,'fullNativeFunctionGateRequired'):
        context_stage.require_full_functions(module_api(),db)
        import assembly_overrides
        assembly_overrides.require_bound(db)
    extra=[]
    if a.additional_owners:
        extra_doc=read(a.additional_owners);extra=extra_doc.get('owners',extra_doc.get('additionalOwners',[]))
        if scope:scope['additionalOwners']=extra
    prior=meta(db,'scope');require(prior is None or prior==scope or (prior and scope and prior['dimension']==scope['dimension'] and prior['rootBounds']==scope['rootBounds']),'Root district is immutable before promotion; additive exact witnesses may be refined before apply')
    meta(db,'scope',scope)
    for key in ('plannedBeSafety','functionalGuestPreflight','compositionsSha256','catalog','compositionGeneration','bridgeHostStates','bridgeCarrierGate','cleanupBeAudit'):
        db.execute('DELETE FROM meta WHERE key=?',(key,))
    for table in ('selected_objects','whole_cells','fragments','substrate_fragments','native_visual_roots','native_visual_fragments','bridge_cells','cell_queue','activated_native','placements','compositions','cleanup_owners','regions','source_chunks','relight'):
        db.execute('DELETE FROM '+table)
    db.execute("DELETE FROM protected_cells WHERE reason IN ('missing_exported_footprint','whole_local_overlap','functional_guest')")
    db.execute("DELETE FROM issues WHERE kind IN ('protected_local_overlap','missing_exported_footprint','whole_local_overlap','functional_guest','unresolved_functional_guest')")
    where="kind IN ('static','whole')";params=[]
    if scope:
        where+=' AND dim=? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?';params=[a.dimension,*a.bounds]
    db.execute('INSERT INTO selected_objects SELECT dim,uuid,pose,x,y,z,kind FROM objects WHERE '+where,params)
    for row in extra:
        dim=row.get('dimension',row.get('dim','.'));uid=row['uuid']
        if isinstance(uid,list):uid=canonical(uid)
        elif not uid.startswith('['):uid=canonical(list(struct.unpack('>4i',uuid.UUID(uid).bytes)))
        saved=db.execute('SELECT dim,uuid,pose,x,y,z,kind FROM objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone()
        require(saved is not None and saved[6]=='whole','Additional owner must be one exact classified whole-object witness')
        if row.get('root'):require(list(saved[3:6])==row['root'],'Additional whole owner root differs from witness receipt')
        db.execute('INSERT OR IGNORE INTO selected_objects VALUES(?,?,?,?,?,?,?)',saved)
    context_stage.filter_selection(module_api(),db)
    db.execute('INSERT OR IGNORE INTO fragments SELECT o.dim,o.x+p.dx,o.y+p.dy,o.z+p.dz,o.uuid,o.pose,p.dx,p.dy,p.dz FROM selected_objects o JOIN pose_cells p ON p.pose=o.pose')
    db.execute("INSERT OR IGNORE INTO issues SELECT 'missing_exported_footprint',o.dim,o.x,o.y,o.z,o.pose FROM selected_objects o WHERE NOT EXISTS(SELECT 1 FROM pose_cells p WHERE p.pose=o.pose)")
    # Fail closed for unknown geometry. An empty exported footprint can never
    # authorize deletion of an old object's BE, helpers or shard records.
    missing=db.execute('SELECT dim,uuid,x,y,z FROM selected_objects o WHERE NOT EXISTS(SELECT 1 FROM pose_cells p WHERE p.pose=o.pose)').fetchall()
    for dim,uid,x,y,z in missing:
        db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(dim,x,y,z,uid,'missing_exported_footprint'))
        db.execute("INSERT OR IGNORE INTO protected_cells SELECT c.dim,c.x,c.y,c.z,c.uuid,'missing_exported_footprint' FROM old.stored_contributions c WHERE c.dim=? AND c.uuid=?",(dim,uid))
        db.execute("INSERT OR IGNORE INTO protected_cells SELECT c.dim,c.x,c.y,c.z,c.uuid,'missing_exported_footprint' FROM old.members c WHERE c.dim=? AND c.uuid=?",(dim,uid))
        db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid))
    preflight_functional_guests(db,plan)
    # A protected local object blocks only its own cells. Do not compute an
    # owner graph closure. Exact conflicting objects are reported individually.
    conflicts=db.execute("SELECT DISTINCT f.dim,f.uuid FROM fragments f JOIN protected_cells p ON p.dim=f.dim AND p.x=f.x AND p.y=f.y AND p.z=f.z WHERE p.reason!='openable_both_states_local_helpers' AND NOT EXISTS(SELECT 1 FROM bridge_cells b WHERE b.dim=f.dim AND b.x=f.x AND b.y=f.y AND b.z=f.z)").fetchall()
    for dim,uid in conflicts:
        row=db.execute('SELECT x,y,z FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone()
        issue(db,'protected_local_overlap',dim,row,{'uuid':uid,'policy':'Own footprint only; no graph closure'})
        db.execute('DELETE FROM fragments WHERE dim=? AND uuid=?',(dim,uid));db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid))
    # A statue gets dedicated finite-role cells. An actual shared cell with
    # another independent object is a local migration limitation, never grounds
    # to preserve the surrounding connected owner graph.
    whole_conflicts=db.execute("SELECT DISTINCT f.dim,f.uuid FROM fragments f JOIN selected_objects o ON o.dim=f.dim AND o.uuid=f.uuid WHERE o.kind='whole' AND (EXISTS(SELECT 1 FROM fragments g WHERE g.dim=f.dim AND g.x=f.x AND g.y=f.y AND g.z=f.z AND g.uuid!=f.uuid) OR EXISTS(SELECT 1 FROM bridge_cells b WHERE b.dim=f.dim AND b.x=f.x AND b.y=f.y AND b.z=f.z))").fetchall()
    for dim,uid in whole_conflicts:
        row=db.execute('SELECT x,y,z FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone()
        issue(db,'whole_local_overlap',dim,row,{'uuid':uid,'reason':'Statue shares a source cell with an independent object; dedicated whole-template cell cannot contain that object'})
        db.execute("INSERT OR IGNORE INTO protected_cells SELECT dim,x,y,z,uuid,'whole_local_overlap' FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=?",(dim,uid))
        db.execute('DELETE FROM fragments WHERE dim=? AND uuid=?',(dim,uid));db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid))
    # A locally retained statue must be protected before static compositions
    # are formed. Conflicting architecture is retained locally, once, without
    # extending this exclusion to the previous owner's connected district.
    statue_overlap=db.execute("SELECT DISTINCT f.dim,f.uuid FROM fragments f JOIN protected_cells p ON p.dim=f.dim AND p.x=f.x AND p.y=f.y AND p.z=f.z WHERE p.reason='whole_local_overlap' AND NOT EXISTS(SELECT 1 FROM bridge_cells b WHERE b.dim=f.dim AND b.x=f.x AND b.y=f.y AND b.z=f.z)").fetchall()
    for dim,uid in statue_overlap:
        row=db.execute('SELECT x,y,z FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid)).fetchone()
        issue(db,'protected_local_overlap',dim,row,{'uuid':uid,'reason':'Retained local statue footprint'})
        db.execute('DELETE FROM fragments WHERE dim=? AND uuid=?',(dim,uid));db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid))
    templates=meta(db,'wholeTemplates') or {}
    for dim,uid,pose,x,y,z in db.execute("SELECT dim,uuid,pose,x,y,z FROM selected_objects WHERE kind='whole'"):
        template=templates.get(pose);require(template,'Missing confirmed whole-object template key: '+pose)
        db.execute('INSERT INTO whole_cells SELECT dim,x,y,z,uuid,?,dx,dy,dz FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=?',(template,dim,uid))
    db.execute("DELETE FROM fragments WHERE EXISTS(SELECT 1 FROM selected_objects o WHERE o.dim=fragments.dim AND o.uuid=fragments.uuid AND o.kind='whole')")
    db.commit()
    # Newly retained independent sculptures and rare foreign conflicts can
    # leave known old owners in other cells. A finite bridge handles those
    # cells directly; do not preserve their connected owner graph.
    for dim,cx,cz in db.execute('SELECT DISTINCT dim,x>>4,z>>4 FROM fragments').fetchall():
        nbt,_,_=get_source_chunk(db,dim,cx,cz);root=compound(nbt.root)
        sections={compound(s)['Y'].value:section_blocks(s) for s in root.get('sections',Tag(9,[],10)).value}
        for tag in root.get('block_entities',Tag(9,[],10)).value:
            be=compound(tag);at=tuple(be[k].value for k in ('x','y','z'))
            if not db.execute('SELECT 1 FROM fragments WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone():continue
            detail=functional_be_conflict(db,dim,be)
            if bridge_candidate(db,dim,be,detail):record_bridge(db,dim,be,source_state(sections,at))
    db.commit()
    # Old native static roots draw whole models without cell clipping. Replacing
    # such a root transfers ALL of its visual cells. Only actual native roots
    # encountered by this finite work queue are activated; isolated native
    # states remain byte-for-byte unchanged. Source fragment physics is ignored
    # in favor of exact installed native-state physics in every composed cell.
    __import__('native_certificates').prepare_source_visual_member_index(module_api(),db)
    db.execute('INSERT OR IGNORE INTO cell_queue(dim,x,y,z) SELECT dim,x,y,z FROM fragments')
    db.execute('INSERT OR IGNORE INTO cell_queue(dim,x,y,z) SELECT dim,x,y,z FROM whole_cells')
    compose_native_tail(db,plan,scope,len(conflicts))


def compose_native_tail(db,plan,scope,local_overlap_objects):
    """The original native-transfer/finalization tail, shared with resume."""
    source_cache=collections.OrderedDict()
    exports=sqlite3.connect('file:'+Path(meta(db,'poseFragments')['path']).as_posix()+'?mode=ro',uri=True)
    visual_reservations=__import__('native_certificates').SourceVisualReservations(module_api(),db,exports)
    @lru_cache(maxsize=512)
    def native_export(state):
        saved=exports.execute('SELECT data FROM native WHERE state=?',(state,)).fetchone()
        return json.loads(saved[0]) if saved else None
    def cached_actual(dim,x,y,z):
        token=(dim,x>>4,z>>4)
        if token not in source_cache:
            nbt,_,_=get_source_chunk(db,*token);root=compound(nbt.root)
            source_cache[token]=({compound(s)['Y'].value:section_blocks(s) for s in root.get('sections',Tag(9,[],10)).value},root,
                                 {tuple(compound(t)[k].value for k in ('x','y','z')):compound(t) for t in root.get('block_entities',Tag(9,[],10)).value})
            if len(source_cache)>4:source_cache.popitem(last=False)
        source_cache.move_to_end(token)
        return source_state(source_cache[token][0],(x,y,z))
    def actual_and_bridge(dim,pos):
        actual=cached_actual(dim,*pos);be=source_cache[(dim,pos[0]>>4,pos[2]>>4)][2].get(pos)
        if be:
            detail=functional_be_conflict(db,dim,be)
            if bridge_candidate(db,dim,be,detail):record_bridge(db,dim,be,actual)
        return actual
    rounds=0
    while True:
        pending=db.execute('SELECT dim,x,y,z FROM cell_queue WHERE processed=0 ORDER BY dim,(x>>4),(z>>4),y,x,z LIMIT 1024').fetchall()
        if not pending:break
        try:
            for dim,x,y,z in pending:
                actual=actual_and_bridge(dim,(x,y,z));state_key=canonical(actual);native=native_export(state_key)
                if (native and native.get('nativeVisualComplete',True)) or visual_reservations.local_native_domains() or visual_reservations.local_native_cells() or visual_reservations.original_zones():
                    __import__('native_certificates').check_retained_native_member(visual_reservations,dim,(x,y,z),actual)
                if native and native.get('nativeVisualComplete',True) and any(v[axis]<-1e-6 or v[axis]>1.000001 for q in native.get('quads',[]) for v in q['vertices'] for axis in range(3)):
                    if not db.execute('SELECT 1 FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)).fetchone():
                        cells=exports.execute('SELECT x,y,z FROM native_fragments WHERE state=?',(state_key,)).fetchall()
                        require(cells,'A wide native model has no proven clipped native visual fragments: '+state_key)
                        db.execute('INSERT INTO native_visual_roots VALUES(?,?,?,?,?)',(dim,x,y,z,state_key))
                        for dx,dy,dz in cells:
                            at=(x+dx,y+dy,z+dz)
                            actual_and_bridge(dim,at)
                            visual_reservations.clearance(dim,at,{'kind':'wide_native','root':[x,y,z],'state':actual})
                            __import__('native_certificates').check_whole_native_contact(visual_reservations,dim,at,{'kind':'wide_native','root':[x,y,z],'state':actual})
                            db.execute('INSERT INTO native_visual_fragments VALUES(?,?,?,?,?,?,?,?,?,?,?)',(dim,*at,x,y,z,state_key,dx,dy,dz))
                            db.execute('INSERT OR IGNORE INTO cell_queue(dim,x,y,z) VALUES(?,?,?,?)',(dim,*at))
                for uid,pose,rx,ry,rz in __import__('native_certificates').pending_native_roots(visual_reservations,dim,(x,y,z),actual,native):
                    if (dim,uid) in visual_reservations.local_native_domains():continue
                    native_resume.check_native_activation(module_api(),db,dim,uid)
                    if db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',(dim,uid)).fetchone():continue
                    db.execute('INSERT INTO activated_native VALUES(?,?)',(dim,uid))
                    # Even a visually empty root slot must cease drawing the old
                    # whole model after any of its helpers becomes a breakable cell.
                    db.execute('INSERT OR IGNORE INTO cell_queue(dim,x,y,z) VALUES(?,?,?,?)',(dim,rx,ry,rz))
                    cells=db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?',(pose,)).fetchall()
                    if not cells:
                        issue(db,'missing_substrate_visual',dim,(x,y,z),{'pose':pose,'uuid':uid});db.commit()
                        raise RuntimeError('Native root visual was not exported; no native carrier may be replaced: '+str((dim,uid,pose)))
                    for dx,dy,dz in cells:
                        at=(rx+dx,ry+dy,rz+dz)
                        actual_and_bridge(dim,at)
                        visual_reservations.clearance(dim,at,{'kind':'legacy_native','uuid':uid,'pose':pose,'root':[rx,ry,rz]})
                        __import__('native_certificates').check_whole_native_contact(visual_reservations,dim,at,{'kind':'legacy_native','uuid':uid,'pose':pose,'root':[rx,ry,rz]})
                        db.execute('INSERT OR IGNORE INTO substrate_fragments VALUES(?,?,?,?,?,?,?,?,?)',(dim,*at,uid,pose,dx,dy,dz))
                        db.execute('INSERT OR IGNORE INTO cell_queue(dim,x,y,z) VALUES(?,?,?,?)',(dim,*at))
                db.execute('UPDATE cell_queue SET processed=1 WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z))
            rounds+=1;native_resume.checkpoint(module_api(),db,'NATIVE_TRANSFER',len(pending));db.commit()
        except RuntimeError as error:
            db.rollback()
            visual_reservations=__import__('native_certificates').SourceVisualReservations(module_api(),db,exports)
            if __import__('native_certificates').automatic_source_zone_retention(module_api(),db,visual_reservations,dim,(x,y,z),actual,error):
                visual_reservations=__import__('native_certificates').SourceVisualReservations(module_api(),db,exports)
                continue
            raise
        if rounds%8==0:progress(plan,db,'TRANSFERRING_NATIVE_VISUALS',nativeRootsActivated=db.execute('SELECT count(*) FROM activated_native').fetchone()[0],replacementCells=db.execute('SELECT count(*) FROM cell_queue').fetchone()[0])
    if meta(db,'nativeLocalRetention') and meta(db,'nativeComposeCursor')['phase']=='NATIVE_TRANSFER':
        db.execute('BEGIN IMMEDIATE')
        __import__('native_certificates').stage_reserved_source_domain_queues(module_api(),db,visual_reservations)
    native_resume.checkpoint(module_api(),db,'FINALIZING');db.commit()
    last_chunk=None;sections={};root=None;rel=None;before=None
    needed_native={};protected_ids=set(read(PROJECT/'tools'/'native-grid'/'classification.json').get('protectedRegistryIds',[]))
    for dim,x,y,z in db.execute('SELECT dim,x,y,z FROM cell_queue q WHERE NOT EXISTS(SELECT 1 FROM whole_cells w WHERE w.dim=q.dim AND w.x=q.x AND w.y=q.y AND w.z=q.z) ORDER BY dim,(x>>4),(z>>4),y,x,z'):
        chunk=(dim,x>>4,z>>4)
        if chunk!=last_chunk:
            nbt,rel,before=get_source_chunk(db,*chunk);root=compound(nbt.root)
            sections={compound(s)['Y'].value:section_blocks(s) for s in root.get('sections',Tag(9,[],10)).value}
            db.execute('INSERT OR REPLACE INTO source_chunks VALUES(?,?,?,?,?)',(*chunk,rel,before));last_chunk=chunk
        original=source_state(sections,(x,y,z))
        bridge=db.execute('SELECT 1 FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)).fetchone()
        require(bridge or original['registryId'] not in protected_ids,'A protected original block cannot become a static base: '+str((dim,x,y,z,original)))
        fragments=[{'pose':p,'cell':[dx,dy,dz]} for p,dx,dy,dz in db.execute('SELECT pose,dx,dy,dz FROM fragments WHERE dim=? AND x=? AND y=? AND z=? ORDER BY pose,dx,dy,dz',(dim,x,y,z))]
        substrate=[{'pose':p,'cell':[dx,dy,dz]} for p,dx,dy,dz in db.execute('SELECT pose,dx,dy,dz FROM substrate_fragments WHERE dim=? AND x=? AND y=? AND z=? ORDER BY pose,dx,dy,dz',(dim,x,y,z))]
        substrate_native=[{'state':json.loads(s),'cell':[dx,dy,dz]} for s,dx,dy,dz in db.execute('SELECT state,dx,dy,dz FROM native_visual_fragments WHERE dim=? AND x=? AND y=? AND z=? ORDER BY state,dx,dy,dz',(dim,x,y,z))]
        # Remove a source native carrier only with exact local membership/root
        # evidence. Any genuine substrate is kept as a baked baseState.
        owner_root=db.execute('SELECT p.data FROM selected_objects o JOIN poses p ON p.key=o.pose WHERE o.dim=? AND o.x=? AND o.y=? AND o.z=?',(dim,x,y,z)).fetchall()
        owned_ids={json.loads(r[0])['registryId'] for r in owner_root}
        exact_member=db.execute('SELECT 1 FROM old.members m JOIN selected_objects o ON o.dim=m.dim AND o.uuid=m.uuid WHERE m.dim=? AND m.x=? AND m.y=? AND m.z=? LIMIT 1',(dim,x,y,z)).fetchone()
        exact_saved=db.execute('SELECT 1 FROM old.stored_contributions m JOIN selected_objects o ON o.dim=m.dim AND o.uuid=m.uuid WHERE m.dim=? AND m.x=? AND m.y=? AND m.z=? LIMIT 1',(dim,x,y,z)).fetchone()
        generic=original['registryId'].startswith('bloodborne_dw:static_architecture_') or original['registryId'] in ('bloodborne_dw:composite_cell','bloodborne_dw:composite_fragment','bloodborne_dw:source_door_fragment','bloodborne_dw:source_compatibility_fragment')
        # A native/static source state remains a substrate unless a selected
        # synthetic unit explicitly describes that carrier. A real BE merely
        # sharing its cell is insufficient evidence to erase the native base.
        synthetic_native=db.execute("SELECT 1 FROM objects WHERE dim=? AND x=? AND y=? AND z=? AND kind='substrate' LIMIT 1",(dim,x,y,z)).fetchone()
        carrier=(original['registryId'] in owned_ids or (generic and (exact_member or exact_saved))) and not synthetic_native
        base=AIR if bridge else None if carrier or original['registryId'] in ('minecraft:air','minecraft:cave_air','minecraft:void_air') else original
        comp={'fragments':fragments,'baseState':base,'optics':None,'localFunction':'none'}
        if bridge:
            comp['wrapper']='NativeGridBase'
            predicates=context_stage.carrier_predicates(module_api(),db,dim,(x,y,z))
            if predicates is not None:comp['carrierPredicates']=predicates
        if base:
            needed_native[canonical(base)]={**base,'physicsOnly':base['registryId'].startswith('bloodborne_dw:')}
            native=native_export(canonical(base))
            source_transfer=bool(db.execute('SELECT 1 FROM activated_native a JOIN objects o ON o.dim=a.dim AND o.uuid=a.uuid WHERE o.dim=? AND o.x=? AND o.y=? AND o.z=? LIMIT 1',(dim,x,y,z)).fetchone())
            wide_transfer=bool(db.execute('SELECT 1 FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)).fetchone())
            if source_transfer or wide_transfer or substrate or substrate_native:
                # Complete contained native bases remain native, even when a
                # different root's visual overhang also visits this cell.
                if native and native.get('nativeVisualComplete',True) and not wide_transfer:
                    cells=exports.execute('SELECT x,y,z FROM native_fragments WHERE state=?',(canonical(base),)).fetchall()
                    if not cells:
                        # The compiler can combine contained native geometry
                        # with foreign substrate refs without erasing its base.
                        comp['baseVisualMode']='nativePlusSubstrateFragments'
                    else:substrate_native.append({'state':base,'cell':[0,0,0]});comp['baseVisualMode']='substrateFragments'
                else:comp['baseVisualMode']='substrateFragments'
                comp['substrateFragments']=substrate
                if substrate_native:comp['substrateNativeFragments']=substrate_native
        elif substrate or substrate_native:
            # A native visual overhang may inhabit a genuinely empty cell or a
            # carrier whose selected contributions are all removed. Its exact
            # remaining substrate physics is empty; geometry alone is moved.
            comp['baseState']=AIR;comp['baseVisualMode']='substrateFragments';comp['substrateFragments']=substrate
            if substrate_native:comp['substrateNativeFragments']=substrate_native
        key='cell_'+digest(comp);data=canonical(comp)
        db.execute('INSERT OR IGNORE INTO compositions VALUES(?,?,NULL)',(key,data))
        db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,NULL,?,0)',(dim,x,y,z,x>>4,z>>4,key,canonical(original)))
    for dim,x,y,z,uid,template,dx,dy,dz in db.execute('SELECT * FROM whole_cells ORDER BY dim,(x>>4),(z>>4),y,x,z'):
        chunk=(dim,x>>4,z>>4)
        if chunk!=last_chunk:
            nbt,rel,before=get_source_chunk(db,*chunk);root=compound(nbt.root);sections={compound(s)['Y'].value:section_blocks(s) for s in root.get('sections',Tag(9,[],10)).value}
            db.execute('INSERT OR REPLACE INTO source_chunks VALUES(?,?,?,?,?)',(*chunk,rel,before));last_chunk=chunk
        original=source_state(sections,(x,y,z));key='whole:'+template+':'+canonical([dx,dy,dz])
        db.execute('INSERT INTO placements VALUES(?,?,?,?,?,?,?,NULL,?,0)',(dim,x,y,z,x>>4,z>>4,key,canonical(original)))
    db.commit()
    verify_planned_be_safety(db,plan)
    context_stage.check_complete(module_api(),db)
    if not scope and meta(db,'fullNativeFunctionGateRequired'):
        import assembly_components
        assembly_components.require_bound(db)
    db.execute('DELETE FROM bridge_cells WHERE NOT EXISTS(SELECT 1 FROM placements p WHERE p.dim=bridge_cells.dim AND p.x=bridge_cells.x AND p.y=bridge_cells.y AND p.z=bridge_cells.z)')
    bridge_ids=sorted({json.loads(r[0])['registryId'] for r in db.execute('SELECT DISTINCT host_state FROM bridge_cells')})
    bridge_doc={'schemaVersion':1,'nbtKey':'NativeGridBase','registryIds':bridge_ids}
    atomic_json(plan/'bridge-carriers.json',bridge_doc)
    atomic_json(PROJECT/'src'/'architecture'/'resources'/'assets'/'bloodborne_dw'/'native_grid'/'bridge_carriers.json',bridge_doc)
    output=plan/'compositions.jsonl';temp=output.with_suffix('.writing')
    with temp.open('w',encoding='utf-8',newline='\n') as stream:
        for key,data in db.execute('SELECT key,data FROM compositions ORDER BY key'):stream.write(canonical({'key':key,**json.loads(data)})+'\n')
        stream.flush();os.fsync(stream.fileno())
    os.replace(temp,output);meta(db,'compositionsSha256',sha(output));db.commit()
    atomic_json(plan/'native-states-needed.json',{'schemaVersion':1,'nativeStates':[needed_native[k] for k in sorted(needed_native)]})
    meta(db,'compositionGeneration',{'compositionsSha256':sha(output),'doorFootprintGuard':meta(db,'doorFootprintGuard'),
                                    'functionalGuestPreflight':meta(db,'functionalGuestPreflight'),'plannedBeSafety':meta(db,'plannedBeSafety'),
                                    'sourceFragments':meta(db,'poseFragments'),'plannerSha256':sha(__file__),
                                    **({'nativeLocalRetention':meta(db,'nativeLocalRetention')} if meta(db,'nativeLocalRetention') else {})})
    db.commit()
    progress(plan,db,'COMPOSITIONS_READY',scope=scope,compositionFile=str(output),localOverlapObjects=local_overlap_objects)

def import_types(a):
    db=connect(a.plan);require(not meta(db,'applyStarted'),'Applied plan is frozen')
    generation=meta(db,'compositionGeneration');require(generation and generation['compositionsSha256']==sha(checked(a.plan)/'compositions.jsonl'),'Final composition generation is absent or changed')
    safety=meta(db,'plannedBeSafety');require(safety and safety.get('status')=='PASS' and sha(safety['path'])==safety['sha256'],'Successful pre-compiler functional guest gate required')
    doors=meta(db,'doorFootprintGuard');require(doors and sha(doors['path'])==doors['sha256'],'Both open/closed native descriptor door footprints must be protected before compilation import')
    with checked(a.mapping).open(encoding='utf-8-sig') as stream:
        for line in stream:
            row=json.loads(line);index=row.get('typeIndex',row.get('cell',row.get('index')))
            require(type(index)is int and index>=0,'Nonnegative compiled typeIndex required')
            db.execute('UPDATE compositions SET type_index=? WHERE key=?',(index,row['key']))
    db.execute("UPDATE placements SET type_index=(SELECT type_index FROM compositions WHERE compositions.key=placements.composition) WHERE composition NOT LIKE 'whole:%'")
    context_stage.catalog_prefix(module_api(),db,a.catalog)
    catalog=read(a.catalog);require(catalog['schemaVersion']==1 and catalog.get('bankSize',128)==128,'Catalogue contract mismatch')
    whole={r['key']:r for r in catalog.get('wholeObjects',[])}
    for key in (r[0] for r in db.execute("SELECT DISTINCT composition FROM placements WHERE composition LIKE 'whole:%'")):
        _,template,offset=key.split(':',2);obj=whole.get(template);require(obj,'Whole-object template absent from compiled catalogue: '+template)
        at=json.loads(offset);cell=next((r['cell'] for r in obj['cells'] if r['offset']==at),None);require(cell is not None,'Whole-template role absent: '+key)
        db.execute('UPDATE placements SET type_index=? WHERE composition=?',(cell,key))
    require(not db.execute('SELECT 1 FROM placements WHERE type_index IS NULL LIMIT 1').fetchone(),'Missing compiled composition index')
    max_index=db.execute('SELECT max(type_index) FROM placements').fetchone()[0]
    require(max_index is None or max_index<len(catalog['cells']),'Mapped type index outside catalogue')
    db.execute('UPDATE bridge_cells SET type_index=(SELECT p.type_index FROM placements p WHERE p.dim=bridge_cells.dim AND p.x=bridge_cells.x AND p.y=bridge_cells.y AND p.z=bridge_cells.z)')
    unsafe=[];pairs=[]
    for encoded,index in db.execute('SELECT DISTINCT host_state,type_index FROM bridge_cells ORDER BY host_state,type_index'):
        cell=catalog['cells'][index];optics=catalog['optics'][cell['optics']]
        if cell.get('fluid') or optics.get('luminance',0)!=0:unsafe.append({'state':json.loads(encoded),'cell':index,'reason':'bridge_requires_dry_nonemitting_base'})
        pairs.append({'state':json.loads(encoded),'cell':index})
    atomic_json(checked(a.plan)/'unsafe-bridge-cells.json',{'status':'FAIL' if unsafe else 'PASS','pairs':unsafe})
    require(not unsafe,'Wet/emitting source owner must remain local before any bridge apply: '+str(checked(a.plan)/'unsafe-bridge-cells.json'))
    host_path=checked(a.plan)/'bridge-host-states.json';atomic_json(host_path,{'schemaVersion':1,'catalogueSha256':sha(a.catalog),'pairs':pairs,'nbtKey':'NativeGridBase'})
    meta(db,'bridgeHostStates',{'path':str(host_path),'sha256':sha(host_path),'pairs':len(pairs)})
    meta(db,'catalog',{'path':str(checked(a.catalog)),'sha256':sha(a.catalog),'meshesSha256':sha(Path(a.catalog).with_name('meshes.bin'))})
    db.execute('INSERT OR REPLACE INTO regions(path,before_hash) SELECT DISTINCT region,hash FROM source_chunks')
    manifest=read(meta(db,'binding')['copyManifest'])
    # Source bookkeeping can extend beyond the visible fragment footprint.
    # Include exact selected roots/member references for BE cleanup, once.
    affected=db.execute('SELECT DISTINCT dim,x>>9,z>>9 FROM selected_objects UNION SELECT DISTINCT c.dim,c.x>>9,c.z>>9 FROM old.stored_contributions c CROSS JOIN selected_objects o ON o.dim=c.dim AND o.uuid=c.uuid UNION SELECT DISTINCT c.dim,c.x>>9,c.z>>9 FROM old.members c CROSS JOIN selected_objects o ON o.dim=c.dim AND o.uuid=c.uuid')
    for dim,rx,rz in affected:
        rel=(Path(dim)/'region'/('r.%d.%d.mca'%(rx,rz))).as_posix()
        if rel.startswith('./'):rel=rel[2:]
        if rel in manifest['files']:db.execute('INSERT OR IGNORE INTO regions(path,before_hash) VALUES(?,?)',(rel,manifest['files'][rel]['sha256']))
    db.execute('DELETE FROM cleanup_owners')
    # Every selected object must have at least its complete exported footprint
    # represented in placements before any old reference becomes removable.
    incomplete=db.execute('SELECT o.dim,o.uuid FROM selected_objects o JOIN pose_cells p ON p.pose=o.pose LEFT JOIN placements w ON w.dim=o.dim AND w.x=o.x+p.dx AND w.y=o.y+p.dy AND w.z=o.z+p.dz WHERE w.type_index IS NULL LIMIT 1').fetchone()
    require(not incomplete,'Selected object footprint lacks a compiled placement: '+str(incomplete))
    require(not db.execute('SELECT 1 FROM selected_objects o WHERE NOT EXISTS(SELECT 1 FROM pose_cells p WHERE p.pose=o.pose) LIMIT 1').fetchone(),'Selected object has no exported footprint')
    db.execute('INSERT INTO cleanup_owners SELECT dim,uuid,kind FROM selected_objects')
    db.commit();progress(checked(a.plan),db,'PLACEMENTS_READY')

def rewrite_states(root,changes):
    sections={compound(s)['Y'].value:s for s in root.get('sections',Tag(9,[],10)).value}
    by_section=collections.defaultdict(dict)
    for pos,state in changes.items():by_section[pos[1]>>4][pos]=state
    for sy,rows in by_section.items():
        sec=sections.get(sy)
        if sec is None:sec=Tag(10,{'Y':Tag(1,sy)});sections[sy]=sec;palette=[state_tag(AIR)];values=[0]*4096
        else:palette,values=section_blocks(sec)
        keys={block_state_key(t):i for i,t in enumerate(palette)}
        for pos,state in rows.items():
            tag=state_tag(state);key=block_state_key(tag);at=keys.get(key)
            if at is None:at=len(palette);keys[key]=at;palette.append(tag)
            values[(pos[0]&15)+((pos[2]&15)*16)+((pos[1]&15)*256)]=at
        used=sorted(set(values));remap={old:i for i,old in enumerate(used)};new=[palette[i] for i in used]
        container={'palette':Tag(9,new,10)}
        if len(new)>1:container['data']=Tag(12,pack_palette_indices([remap[i] for i in values],len(new)))
        compound(sec)['block_states']=Tag(10,container)
    root['sections']=Tag(9,[sections[k] for k in sorted(sections)],10)

def stream_region(source,destination,transform,keep_chunk=None):
    """At most one compressed record and one decoded chunk are resident.

    Repack untouched compressed sectors without decoding/recompressing them.
    No whole-region bytearray, source map overwrite, or in-place region edits.
    """
    stats=collections.Counter()
    if checked(source).stat().st_size==0:
        # Vanilla saves can leave an actual empty entities/POI region file.
        # Zero bytes mean no allocated chunks; a short nonempty header remains
        # corruption and must never be silently converted into an empty file.
        with checked(destination).open('wb') as out:out.flush();os.fsync(out.fileno())
        return {'emptyRegion':1,'copiedChunks':0}
    with checked(source).open('rb') as inp,checked(destination).open('w+b') as out:
        header=inp.read(8192);require(len(header)==8192,'Short region header')
        size=os.fstat(inp.fileno()).st_size;alloc=[]
        for i in range(1024):
            loc=int.from_bytes(header[i*4:i*4+4],'big');sector,count=loc>>8,loc&255
            require(bool(sector)==bool(count),'Unpaired region offset/count')
            if sector:require(sector>=2 and sector*4096+count*4096<=math.ceil(size/4096)*4096,'Invalid region allocation');alloc.append((sector,sector+count))
        alloc.sort();require(all(a[1]<=b[0] for a,b in zip(alloc,alloc[1:])),'Overlapping source region allocations')
        locations=bytearray(4096);out.write(bytes(8192))
        for i in range(1024):
            loc=int.from_bytes(header[i*4:i*4+4],'big');sector,count=loc>>8,loc&255
            if not sector:continue
            if keep_chunk is not None and not keep_chunk(i&31,i>>5):continue
            inp.seek(sector*4096);raw=inp.read(count*4096)
            require(len(raw)>=5,'Short chunk record');length=int.from_bytes(raw[:4],'big')
            require(1<=length and length+4<=len(raw),'Invalid/truncated source chunk')
            compression=raw[4];require(compression in (1,2,3),'External source chunks are not supported')
            replaced=transform(i&31,i>>5,compression,raw[5:4+length])
            if replaced is not None:
                payload=zlib.compress(encode_nbt(replaced));raw=(len(payload)+1).to_bytes(4,'big')+b'\x02'+payload;stats['changedChunks']+=1
            needed=math.ceil(len(raw)/4096);require(needed<=255,'Inline chunk sector limit exceeded')
            new_sector=out.tell()//4096;require(new_sector<(1<<24),'Region offset overflow')
            locations[i*4:i*4+4]=((new_sector<<8)|needed).to_bytes(4,'big')
            out.write(raw);out.write(bytes(needed*4096-len(raw)));stats['copiedChunks']+=1
        out.seek(0);out.write(locations);out.write(header[4096:8192]);out.flush();os.fsync(out.fileno())
    return dict(stats)

def file_commit(db,plan,target,rel,kind,producer,namespace=''):
    key=namespace+rel
    path=checked(target/rel);row=db.execute('SELECT before_hash,after_hash,phase FROM files WHERE path=?',(key,)).fetchone()
    current=sha(path);receipt=plan/'journals'/(hashlib.sha256(key.encode()).hexdigest()+'.json')
    if row and row[2]=='committed':require(current==row[1],'Committed file changed: '+rel);return
    if receipt.exists():
        saved=read(receipt)
        if current==saved['afterSha256']:
            db.execute('INSERT OR REPLACE INTO files VALUES(?,?,?,?,?,?)',(key,saved['beforeSha256'],saved['afterSha256'],'committed',kind,canonical(saved['stats'])));db.commit();return
        require(current==saved['beforeSha256'],'Interrupted file has neither before nor after bytes: '+rel)
    expected=row[0] if row else current
    require(current==expected,'Uncommitted target file changed: '+rel)
    temporary=path.with_name(path.name+'.native-grid-writing')
    stats=producer(path,temporary);after=sha(temporary)
    # Light debt is durable BEFORE the file swap. Recovery after a crash between
    # os.replace and SQLite's file-row commit must not lose that debt.
    db.commit()
    atomic_json(receipt,{'schemaVersion':1,'path':rel,'operationKey':key,'kind':kind,'beforeSha256':current,'afterSha256':after,'stats':stats})
    os.replace(temporary,path)
    db.execute('INSERT OR REPLACE INTO files VALUES(?,?,?,?,?,?)',(key,current,after,'committed',kind,canonical(stats)));db.commit()

def transformed_chunk(db,dim,cx,cz,compression,payload):
    if meta(db,'contextExtension'):return context_stage.transformed_chunk(module_api(),db,dim,cx,cz,compression,payload)
    writes=db.execute('SELECT x,y,z,type_index,source_state,optics_equal FROM placements WHERE dim=? AND cx=? AND cz=?',(dim,cx,cz)).fetchall()
    # Cleanup may affect a chunk without a new cell (old root/helper cells).
    nbt=decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]);root=compound(nbt.root)
    require((root['xPos'].value,root['zPos'].value)==(cx,cz),'Source chunk NBT coordinate differs from its region slot')
    changes={};dirty=False;kept=[];stats=collections.Counter()
    bridges={(x,y,z):(json.loads(host),index) for x,y,z,host,index in db.execute('SELECT x,y,z,host_state,type_index FROM bridge_cells WHERE dim=? AND x>>4=? AND z>>4=?',(dim,cx,cz))}
    for x,y,z,index,source,equal in writes:
        require(index is not None,'Uncompiled placement')
        changes[(x,y,z)]={'registryId':'bloodborne_dw:native_grid_'+str(index//128),'properties':{'part':str(index%128)}}
    for tag in root.get('block_entities',Tag(9,[],10)).value:
        be=compound(tag);pos=tuple(be[k].value for k in ('x','y','z'));old=be_owners(be);new=[];resident_removed=False
        for item in old:
            uid=owner_uuid(compound(item)['owner'])
            if db.execute('SELECT 1 FROM cleanup_owners WHERE dim=? AND uuid=?',(dim,uid)).fetchone():
                dirty=True;stats['removedContributions']+=1
                if 'resident' in be and owner_uuid(be['resident'])==uid:resident_removed=True
            else:new.append(item)
        if resident_removed:be.pop('resident',None);be['payload']=Tag(10,{})
        if pos in bridges:
            host,index=bridges[pos];require(pos in changes and type(index)is int,'Bridge has no compiled static background')
            require(be.get('id',Tag(8,'')).value=='bloodborne_dw:composite_cell' and (new or 'resident' in be),'NativeGridBase requires an existing retained CompositeBE')
            be['NativeGridBase']=Tag(3,index);changes[pos]=host
            if len(old)!=len(new):
                be.pop('owners',None);be['OwnerDataV1']=Tag(7,encode_nbt(NbtFile('',Tag(10,{'owners':Tag(9,new,10)}))))
            kept.append(tag);dirty=True;stats['existingNativeGridBridges']+=1;continue
        if len(old)==len(new):
            require(pos not in changes,'Placement would overwrite a retained BlockEntity at '+str(pos));kept.append(tag);continue
        if new or 'resident' in be:
            require(pos not in changes,'Placement would erase a retained local owner at '+str(pos))
            be.pop('owners',None);be['OwnerDataV1']=Tag(7,encode_nbt(NbtFile('',Tag(10,{'owners':Tag(9,new,10)}))));kept.append(tag)
        else:
            stats['removedBlockEntities']+=1
            if pos not in changes:changes[pos]=AIR
    if not writes and not dirty:return None
    rewrite_states(root,changes);root['block_entities']=Tag(9,kept,10)
    # Equality is an explicit proof, never inferred from NBT shape. Metadata is
    # retained in proved chunks; every unknown changed cell incurs native debt.
    equal=bool(writes) and all(row[5] for row in writes) and not dirty
    if not equal:
        invalidate_chunk_lighting(root)
        # Keep heightmaps: the engine runner recomputes them from target states
        # before saving. Deleting them here would add hidden first-login work.
        for dx in (-1,0,1):
            for dz in (-1,0,1):db.execute('INSERT OR IGNORE INTO relight(dim,cx,cz,reason) VALUES(?,?,?,?)',(dim,cx+dx,cz+dz,'changed_or_unproved_optics'))
    return nbt

def cleanup_dictionary(db,dim,nbt):
    root=compound(nbt.root);root=compound(root['data']) if 'data' in root else root
    require(root.get('Dictionary',Tag(3,0)).value,'Unknown owner shard representation')
    owners=root.get('ownerTable',Tag(9,[],10)).value
    gone={i for i,t in enumerate(owners) if db.execute('SELECT 1 FROM cleanup_owners WHERE dim=? AND uuid=?',(dim,owner_uuid(t))).fetchone()}
    if not gone:return {'removedOwners':0}
    cells=[];members=[]
    for tag in root.get('cells',Tag(9,[],10)).value:
        row=compound(tag);refs=[r for r in row['refs'].value if r.value[0] not in gone]
        if refs:row['refs']=Tag(9,refs,11);cells.append(tag)
    for tag in root.get('ownerMembership',Tag(9,[],10)).value:
        if compound(tag)['ownerRef'].value not in gone:members.append(tag)
    used_o=sorted({r.value[0] for c in cells for r in compound(c)['refs'].value}|{compound(m)['ownerRef'].value for m in members})
    used_d=sorted({r.value[1] for c in cells for r in compound(c)['refs'].value})
    used_s=sorted({r.value[2] for c in cells for r in compound(c)['refs'].value}|{compound(t)['shapeRef'].value for m in members for t in compound(m)['members'].value})
    om={v:i for i,v in enumerate(used_o)};dm={v:i for i,v in enumerate(used_d)};sm={v:i for i,v in enumerate(used_s)}
    for cell in cells:
        for ref in compound(cell)['refs'].value:ref.value=[om[ref.value[0]],dm[ref.value[1]],sm[ref.value[2]]]
    for member in members:
        row=compound(member);row['ownerRef']=Tag(3,om[row['ownerRef'].value])
        for item in row['members'].value:compound(item)['shapeRef']=Tag(3,sm[compound(item)['shapeRef'].value])
    root['cells']=Tag(9,cells,10);root['ownerMembership']=Tag(9,members,10)
    root['ownerTable']=Tag(9,[owners[i] for i in used_o],10)
    root['dataTable']=Tag(9,[root['dataTable'].value[i] for i in used_d],10)
    root['shapeTable']=Tag(9,[root['shapeTable'].value[i] for i in used_s],10)
    return {'removedOwners':len(gone),'retainedOwners':len(used_o)}

def cleanup_global(db,dim,nbt):
    root=compound(nbt.root);root=compound(root['data']) if 'data' in root else root
    cells=[];removed=0
    for tag in root.get('cells',Tag(9,[],10)).value:
        row=compound(tag);new=[]
        for own in row.get('owners',Tag(9,[],10)).value:
            uid=owner_uuid(compound(own)['owner'])
            if db.execute('SELECT 1 FROM cleanup_owners WHERE dim=? AND uuid=?',(dim,uid)).fetchone():removed+=1
            else:new.append(own)
        if new:row['owners']=Tag(9,new,10);cells.append(tag)
    if removed:root['cells']=Tag(9,cells,10)
    return {'removedContributions':removed}

def bind_runtime(db,path):
    path=checked(path);catalog=meta(db,'catalog');require(catalog,'Compiled catalogue not bound')
    with zipfile.ZipFile(path) as archive:
        require(hashlib.sha256(archive.read(CATALOG_REL)).hexdigest()==catalog['sha256'],'JAR and migration catalogue differ')
        require(hashlib.sha256(archive.read('assets/bloodborne_dw/native_grid/meshes.bin')).hexdigest()==catalog['meshesSha256'],'JAR and migration meshes differ')
    value={'path':str(path),'sha256':sha(path)};prior=meta(db,'runtime')
    require(prior is None or prior['sha256']==value['sha256'],'Runtime JAR changed during resume');meta(db,'runtime',value)

def crop_pilot_global_index(nbt,shards):
    """Change only the index of an already cropped independent pilot copy."""
    root=compound(nbt.root);data=compound(root['data']) if 'data' in root else root
    require(data.get('OwnerShardSchema',Tag(3,0)).value==1 and not data.get('cells',Tag(9,[],10)).value,'Only an empty global schema-1 owner index can be cropped')
    index=data.get('OwnerShardChunks');require(index is not None and index.type==12,'Expected native long-array shard index')
    original=list(index.value);require(len(set(original))==len(original),'Repeated source owner shard index')
    keys=set()
    for rel in shards:
        match=re.fullmatch(r'data/bloodborne_dw_owner_shards/owners-(-?\d+)\.(-?\d+)\.nbt',rel);require(match,'Only exact overworld copied shard paths are allowed')
        x,z=map(int,match.groups());value=(x&0xffffffff)|((z&0xffffffff)<<32);keys.add(value if value<1<<63 else value-(1<<64))
    require(keys<=set(original),'A copied owner shard was absent from the immutable source index')
    def other_hash():
        copy=decode_nbt(encode_nbt(nbt));fields=compound(copy.root);fields=compound(fields['data']) if 'data' in fields else fields
        fields.pop('OwnerShardChunks');return hashlib.sha256(encode_nbt(copy)).hexdigest()
    original_other_hash=other_hash()
    # Assignment preserves every source compound field's insertion position.
    kept=[key for key in original if key in keys];data['OwnerShardChunks']=Tag(12,kept)
    require(other_hash()==original_other_hash,'Index crop changed another global NBT field')
    return {'indexBefore':len(original),'indexAfter':len(kept),'keptKeysSha256':digest(sorted(kept)),'otherFieldsNbtSha256':original_other_hash}

def bind_pilot_harness(db,target,path):
    """Prove the one reviewed global-index transform without editing manifest."""
    require(meta(db,'scope') and not meta(db,'applyStarted'),'Harness metadata binding is bounded-pilot-only and pre-apply')
    path=checked(path);receipt=read(path);binding=meta(db,'binding');scope=meta(db,'scope')
    require(receipt.get('status')=='PASS' and receipt.get('kind')=='BOUNDED_PILOT_BOOTSTRAP_REPAIR' and receipt.get('terrainRegionWrites')==0,'Expected exact bootstrap repair receipt')
    require(receipt.get('source')==binding['source'] and receipt.get('sourceCopyManifestSha256')==binding['copyManifestSha256'],'Harness receipt belongs to another immutable source')
    require(sha(receipt['repairPlan'])==receipt['repairPlanSha256'],'Reviewed bounded repair plan changed')
    world=receipt.get('worlds',{}).get(target.name);require(world and world.get('target')==str(target) and target.parent==ROOT/'research'/'pilot','Only the reviewed independent pilot target may use a metadata override')
    copy=read(world['copyReceipt']);require(sha(world['copyReceipt'])==world['copyReceiptSha256'] and copy['bounds']==scope['rootBounds'] and copy['selectedChunkSetSha256']==world['selectedChunkSetSha256'],'Harness copy/scope binding changed')
    ledger=world['ledger'];rel='data/bloodborne_dw_composite_owners.dat';manifest=read(binding['copyManifest'])
    require(ledger['path']==rel and ledger['sourceSha256']==manifest['files'][rel]['sha256'],'Only the reviewed global ledger index may override an immutable before hash')
    source=checked(Path(binding['source'])/rel);require(sha(source)==ledger['sourceSha256'],'Immutable addressed global index changed')
    checkpoint=read(target.parent/(target.name+'-copy-progress.json'));shards={p:h for p,h in checkpoint['files'].items() if Path(p).parent.name=='bloodborne_dw_owner_shards'}
    require(shards==ledger['shards'] and len(shards)==407,'Harness must preserve exactly all 407 committed copied shards')
    require(all((target/p).is_file() and sha(target/p)==wanted for p,wanted in shards.items()),'A copied owner shard changed during metadata correction')
    nbt=read_nbt(source);proof=crop_pilot_global_index(nbt,shards)
    require(proof['indexBefore']==3694 and proof['indexAfter']==407 and all(ledger.get(k)==v for k,v in proof.items()),'Harness index filtering proof differs from exact immutable NBT transform')
    expected=hashlib.sha256(encode_nbt(nbt,compressed='gzip')).hexdigest()
    require(ledger['afterSha256']==expected==sha(target/rel),'Harness global index bytes differ from the proved transform')
    meta(db,'pilotMetadataBinding',{'path':str(path),'sha256':sha(path),'target':str(target),'copyReceiptSha256':world['copyReceiptSha256'],
                                 'globalLedger':{'path':rel,'sourceSha256':ledger['sourceSha256'],'beforeCleanupSha256':expected}})

def pilot_ledger_before_hash(db,target,rel,immutable_hash):
    if meta(db,'contextExtension'):return context_stage.ledger_before(module_api(),db,target,rel,immutable_hash)
    metadata=meta(db,'pilotMetadataBinding')
    if not metadata or rel!=metadata['globalLedger']['path']:return immutable_hash
    require(meta(db,'scope') and metadata['target']==str(target),'Pilot metadata override cannot be used for full-city apply or another target')
    require(sha(metadata['path'])==metadata['sha256'],'Bounded harness receipt changed after binding')
    ledger=metadata['globalLedger'];require(ledger['sourceSha256']==immutable_hash,'Pilot global before override lost its immutable source binding')
    return ledger['beforeCleanupSha256']

def freeze_inputs(db,plan):
    classification=meta(db,'classification');catalog=meta(db,'catalog')
    require(classification and sha(classification['path'])==classification['sha256'],'Classification changed after coordinate planning')
    require(sha(catalog['path'])==catalog['sha256'] and sha(Path(catalog['path']).with_name('meshes.bin'))==catalog['meshesSha256'],'Compiled catalogue changed after mapping')
    gate_names=['payloadDecisions','carrierMarkerRequest','doorFootprintGuard','functionalGuestPreflight','plannedBeSafety','bridgeHostStates','cleanupBeAudit']
    if db.execute('SELECT 1 FROM bridge_cells LIMIT 1').fetchone():gate_names.append('bridgeCarrierGate')
    if meta(db,'scope') and meta(db,'pilotMetadataBinding'):gate_names.append('pilotMetadataBinding')
    if meta(db,'runtimeStorageTransition'):gate_names.append('runtimeStorageTransition')
    if meta(db,'contextExtension'):gate_names.extend(('contextExtension','contextRegionBinding','contextNativeFunctionGate'))
    if meta(db,'fullNativeFunctionGateRequired'):gate_names.extend(('fullNativeFunctionGate','fullNativeInputCoverageGate','preservedAssemblyGate'))
    if meta(db,'nativeLocalRetention'):gate_names.append('nativeLocalRetention')
    if meta(db,'nativeResumeSourceTransition'):gate_names.append('nativeResumeSourceTransition')
    if meta(db,'nativeVisualProtectionCorrection'):
        __import__('native_certificates').validate_visual_protection_corrections(module_api(),db,finalized=True)
        gate_names.append('nativeVisualProtectionCorrection')
    if meta(db,'nativeWholeTemplateFallback'):
        __import__('native_certificates').validate_whole_template_fallbacks(module_api(),db,finalized=True)
        gate_names.append('nativeWholeTemplateFallback')
    if meta(db,'nativeRetainedFunctionalVisualCorrection'):
        __import__('native_certificates').validate_retained_functional_visual_corrections(module_api(),db,finalized=True)
        gate_names.append('nativeRetainedFunctionalVisualCorrection')
    if meta(db,'sourceVisualReservationJournal'):
        __import__('native_certificates').validate_source_visual_reservations(module_api(),db,finalized=True)
        gates_journal=meta(db,'sourceVisualReservationJournal')
    if meta(db,'sourceLocalRetentionJournal'):
        __import__('native_certificates').validate_source_local_retentions(module_api(),db,finalized=True)
    gates={key:meta(db,key) for key in gate_names}
    for name,gate in gates.items():require(gate and sha(gate['path'])==gate['sha256'],'Missing or changed pre-apply evidence: '+name)
    require(gates['plannedBeSafety'].get('status')=='PASS','Functional BlockEntity planning gate did not pass')
    require(gates['cleanupBeAudit'].get('status')=='PASS','Actual cleanup-only BlockEntity payload audit did not pass')
    current={'schemaVersion':1,'plannerSha256':sha(__file__),'worldIoSha256':sha(PROJECT/'tools'/'world_io.py'),
             'classification':classification,'catalog':catalog,'sourceBinding':meta(db,'binding'),
             'pilotRegionBinding':meta(db,'pilotRegionBinding') if meta(db,'scope') else None,
             'pilotMetadataBinding':meta(db,'pilotMetadataBinding') if meta(db,'scope') else None,
             'semanticGates':gates,
             'counts':{t:db.execute('SELECT count(*) FROM '+t).fetchone()[0] for t in ('selected_objects','placements','cleanup_owners','regions')}}
    previous=meta(db,'frozenInputs')
    if meta(db,'nativeResumeSourceBinding'):
        certificates=__import__('native_certificates');certificates.active_binding(module_api(),db)
        current['nativeConsumerSourceFiles']=certificates.source_files(module_api())
    if meta(db,'sourceVisualReservationJournal'):current['sourceVisualReservationJournal']=gates_journal
    if meta(db,'sourceLocalRetentionJournal'):current['sourceLocalRetentionJournal']=meta(db,'sourceLocalRetentionJournal')
    if meta(db,'contextExtension'):current['contextStageSha256']=sha(Path(__file__).with_name('context_stage.py'))
    if meta(db,'fullNativeFunctionGateRequired'):
        current['assemblyOverridesSha256']=sha(Path(__file__).with_name('assembly_overrides.py'))
        current['assemblyComponentsSha256']=sha(Path(__file__).with_name('assembly_components.py'))
    require(previous is None or previous==current,'Planner/classification/catalogue/counts changed after first apply; preserve the existing cursor')
    if previous is None:meta(db,'frozenInputs',current);atomic_json(plan/'frozen-inputs.json',current)

def apply(a):
    db=connect(a.plan);plan=checked(a.plan);target=checked(a.target);binding=meta(db,'binding')
    require(target!=Path(binding['source']) and target not in Path(binding['source']).parents and Path(binding['source']) not in target.parents,'Immutable source cannot be an apply target')
    require(meta(db,'catalog'),'Placements not compiled')
    if meta(db,'scope'):
        if meta(db,'contextExtension'):context_stage.apply_guard(module_api(),db,target,a.runtime_jar)
        else:
            copy_binding=meta(db,'pilotRegionBinding');require(copy_binding and copy_binding.get('target')==str(target),'Bind the committed cropped pilot source copy after import-types before apply')
    if not meta(db,'scope'):
        import assembly_overrides,assembly_components
        assembly_overrides.require_bound(db);assembly_components.require_bound(db,compiled=True)
        context_stage.full_apply_gate(module_api(),db,plan)
        gate_path=a.pilot_gate or meta(db,'successfulPilotGate')
        gate=read(gate_path) if gate_path else {}
        require(gate.get('status')=='PASS' and gate.get('denseBrickPilot') is True,'Full apply requires root successful dense brick pilot gate')
        if gate.get('catalogSha256')!=meta(db,'catalog')['sha256']:
            prior=read(plan/'pilot-catalog.json');current=read(meta(db,'catalog')['path'])
            require(sha(plan/'pilot-catalog.json')==gate.get('catalogSha256'),'Pilot catalogue snapshot hash differs from gate')
            require(all(current[k][:len(prior[k])]==prior[k] for k in ('materials','shapes','optics','meshes','cells','wholeObjects')),'Full catalogue changed a frozen pilot index')
    require(not db.execute('SELECT 1 FROM placements WHERE type_index IS NULL LIMIT 1').fetchone(),'Uncompiled placements')
    prior=meta(db,'target');require(prior is None or prior==str(target),'Resume target differs');meta(db,'target',str(target));bind_runtime(db,a.runtime_jar)
    if db.execute('SELECT 1 FROM bridge_cells LIMIT 1').fetchone():
        bridge_gate=meta(db,'bridgeCarrierGate');require(bridge_gate and read(bridge_gate['path']).get('runtimeSha256')==meta(db,'runtime')['sha256'],'Native actual-carrier bridge query receipt must bind this exact runtime before map writes')
    freeze_inputs(db,plan)
    require(meta(db,'binding')['sourceHashesVerified'],'Source hashes were not verified during prepare')
    if meta(db,'offlineApplied'):
        require(not meta(db,'relightComplete'),'Native light already accepted; do not rerun apply')
        export_relight(db,plan);progress(plan,db,'OFFLINE_APPLIED_RELIGHT_PENDING');return
    meta(db,'applyStarted',True);db.commit()
    atomic_json(target/'NATIVE-GRID-INCOMPLETE.json',{'status':'DO_NOT_OPEN','plan':str(plan),'reason':'Region/shard cleanup and relight are not complete'})
    manifest=read(binding['copyManifest'])
    region_rows=[] if meta(db,'regionPhaseComplete') else db.execute('SELECT path,before_hash,phase FROM regions ORDER BY path').fetchall()
    for rel,before,phase in region_rows:
        path=target/rel;require(path.is_file(),'Target region missing: '+rel)
        dim=Path(rel).parent.parent.as_posix();rx,rz=map(int,re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca',path.name).groups())
        file_row=db.execute('SELECT phase FROM files WHERE path=?',(rel,)).fetchone()
        if not file_row:require(sha(path)==before,'Target region differs from immutable source: '+rel);db.execute('INSERT INTO files VALUES(?,?,NULL,?,?,NULL)',(rel,before,'planned','region'));db.commit()
        def produce(src,tmp):
            return stream_region(src,tmp,lambda lx,lz,c,p:transformed_chunk(db,dim,rx*32+lx,rz*32+lz,c,p))
        file_commit(db,plan,target,rel,'region',produce)
        row=db.execute('SELECT after_hash,stats FROM files WHERE path=?',(rel,)).fetchone()
        db.execute("UPDATE regions SET phase='committed',after_hash=?,stats=? WHERE path=?",(*row,rel));db.commit();progress(plan,db,'WRITING_REGIONS',lastRegion=rel)
    require(not db.execute("SELECT 1 FROM regions WHERE phase!='committed' LIMIT 1").fetchone(),'All regions must be committed before ledger cleanup')
    meta(db,'regionPhaseComplete',True);db.commit()
    for rel,row in manifest['files'].items():
        path=target/rel
        if not path.is_file():continue
        dictionary=path.parent.name=='bloodborne_dw_owner_shards' and path.suffix=='.nbt'
        global_ledger=path.name=='bloodborne_dw_composite_owners.dat' and path.parent.name=='data'
        if not dictionary and not global_ledger:continue
        dim=path.parent.parent.parent.relative_to(target).as_posix() if dictionary else path.parent.parent.relative_to(target).as_posix()
        def produce(src,tmp):
            nbt=read_nbt(src);stats=cleanup_dictionary(db,dim,nbt) if dictionary else cleanup_global(db,dim,nbt)
            if not stats.get('removedOwners',stats.get('removedContributions',0)):shutil.copyfile(src,tmp)
            else:write_nbt(tmp,nbt)
            with tmp.open('r+b') as stream:os.fsync(stream.fileno())
            return stats
        if not db.execute('SELECT 1 FROM files WHERE path=?',(rel,)).fetchone():
            require(sha(path)==pilot_ledger_before_hash(db,target,rel,row['sha256']),'Target ledger differs from source/proved pilot index before cleanup: '+rel)
        file_commit(db,plan,target,rel,'owner_shard' if dictionary else 'global_ledger',produce)
    # Invalidate the complete durable debt set, including existing neighbors,
    # while the world is still offline. The native loader then recomputes from
    # uncached data; no stale neighbor nibble arrays can seed false light.
    if not meta(db,'scope') and meta(db,'fullNativeFunctionGateRequired'):
        import assembly_components
        assembly_components.prepare_full_light(db,plan)
    invalidate_debt(db,plan,target)
    meta(db,'offlineApplied',True);db.commit();export_relight(db,plan)
    progress(plan,db,'OFFLINE_APPLIED_RELIGHT_PENDING')

def export_relight(db,plan):
    output=plan/'relight-queue.tsv';temp=output.with_suffix('.writing')
    with temp.open('w',encoding='utf-8',newline='\n') as stream:
        stream.write('# native-grid-relight-v1\tdimension\tcx\tcz\n')
        for dim,cx,cz in db.execute("SELECT dim,cx,cz FROM relight WHERE status!='done' ORDER BY dim,cx,cz"):
            dimension={'.':'minecraft:overworld','DIM-1':'minecraft:the_nether','DIM1':'minecraft:the_end'}.get(dim,dim.removeprefix('dimensions/').replace('/',':',1))
            stream.write('%s\t%d\t%d\n'%(dimension,cx,cz))
        stream.flush();os.fsync(stream.fileno())
    os.replace(temp,output);light_binding={'schemaVersion':1,'queueSha256':sha(output),'queued':db.execute("SELECT count(*) FROM relight WHERE status!='done'").fetchone()[0],'target':meta(db,'target'),'catalog':meta(db,'catalog'),'runtime':meta(db,'runtime')}
    if context_stage.native_light_restart(module_api(),db):
        restart=meta(db,'contextNativeLightRestart');light_binding.update(engineReceipt=restart['freshReceipt'],engineDoneCursor=restart['freshDoneCursor'],savedPrefixReused=False,dependencyRepair=restart['repair'])
    elif not meta(db,'scope') and meta(db,'fullNativeFunctionGateRequired'):
        fresh=plan/'full-city-relight-engine.json';light_binding.update(engineReceipt=str(fresh),engineDoneCursor=str(fresh.with_name(fresh.name+'.done.tsv')),savedPrefixReused=False,fullCityFreshLight=True)
        request=meta(db,'fullAssemblyLightRequest');require(request and sha(request['path'])==request['sha256'],'Full native light requires its finite four-assembly request');light_binding.update(assemblyRequest=request,assemblyPhaseReceipt=str(fresh.with_name(fresh.name+'.assemblies.json')))
    atomic_json(plan/'relight-binding.json',light_binding)
    if meta(db,'contextExtension'):context_stage.adopt_light(module_api(),db,plan)

def invalidate_debt(db,plan,target):
    grouped=collections.defaultdict(set)
    for dim,cx,cz in db.execute('SELECT dim,cx,cz FROM relight'):
        if meta(db,'contextExtension') and not context_stage.light_to_invalidate(module_api(),db,dim,cx,cz):continue
        rel=(Path(dim)/'region'/('r.%d.%d.mca'%(cx>>5,cz>>5))).as_posix()
        if rel.startswith('./'):rel=rel[2:]
        path=checked(target/rel);exists=False
        if path.is_file():
            with path.open('rb') as stream:
                stream.seek(((cx&31)+(cz&31)*32)*4);exists=bool(int.from_bytes(stream.read(4),'big')>>8)
        if exists:grouped[(dim,rel)].add((cx&31,cz&31))
        else:db.execute('DELETE FROM relight WHERE dim=? AND cx=? AND cz=?',(dim,cx,cz))
    db.commit()
    for (dim,rel),local in sorted(grouped.items()):
        def transform(lx,lz,compression,payload):
            if (lx,lz) not in local:return None
            nbt=decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]);root=compound(nbt.root)
            return nbt if invalidate_chunk_lighting(root) else None
        file_commit(db,plan,target,rel,'relight_cache',lambda src,tmp:stream_region(src,tmp,transform),namespace='relight-cache/')
        progress(plan,db,'PREPARING_NATIVE_LIGHT',lastLightRegion=rel)

def accept_relight(a):
    db=connect(a.plan);plan=checked(a.plan);binding=read(plan/'relight-binding.json');receipt=read(a.receipt)
    require(receipt.get('status')=='PASS' and receipt.get('nativeEngine') is True,'Successful native engine relight receipt required')
    require(receipt.get('queueSha256')==binding['queueSha256'],'Relight queue binding differs')
    require(receipt.get('world')==binding['target'],'Relight receipt belongs to another world')
    require(receipt.get('runtimeSha256')==binding['runtime']['sha256'] and receipt.get('catalogSha256')==binding['catalog']['sha256'],'Native relight runtime/catalogue differ')
    require(receipt.get('queued')==receipt.get('processed')==binding['queued'],'Native engine receipt does not cover the exact queued chunk count')
    require(receipt.get('pending',-1)==0 and receipt.get('saveFlushed') is True,'Relight save/debt not complete')
    if not meta(db,'scope') and meta(db,'fullNativeFunctionGateRequired'):
        import assembly_components
        assembly_components.accept_full_light(db,binding,receipt)
    db.execute("UPDATE relight SET status='done',receipt=?",(sha(a.receipt),));meta(db,'relightComplete',True);db.commit()
    target=checked(meta(db,'target'));marker=target/'NATIVE-GRID-INCOMPLETE.json'
    if marker.exists():marker.unlink()
    atomic_json(target/'NATIVE-GRID-MIGRATION.json',{'status':'READY_FOR_GAMEPLAY_QA','catalog':meta(db,'catalog'),'runtime':meta(db,'runtime'),'plan':str(plan),'lightReceipt':str(checked(a.receipt)),'lightReceiptSha256':sha(a.receipt)})
    progress(plan,db,'RELIGHT_COMPLETE')

def clone(a):
    db=connect(a.plan) if a.bounds else None
    try:return _clone_source(a,db)
    finally:
        if db:db.close()

def _clone_source(a,db=None):
    """Root-only explicit source copy; never invoked by prepare/apply."""
    old=checked(a.source_plan);manifest=read(old/'copy.json');source=checked(manifest['source']);target=checked(a.target)
    require(target!=source and source not in target.parents and target not in source.parents,'Independent target required')
    bounds=a.bounds;selected=[];allowed=None
    if bounds:
        require(meta(db,'scope') and meta(db,'scope')['dimension']=='.' and meta(db,'scope')['rootBounds']==bounds,'Pilot copy bounds must equal the prepared coordinate plan')
        core={(cx,cz) for cx in range(bounds[0]>>4,(bounds[1]>>4)+1) for cz in range(bounds[2]>>4,(bounds[3]>>4)+1)}
        radius=a.context_radius
        require(1<=radius<=16,'Pilot context radius must stay in 1..16 chunks')
        allowed={(cx+dx,cz+dz) for cx,cz in core for dx in range(-radius,radius+1) for dz in range(-radius,radius+1)}
        dependencies=set(db.execute("SELECT cx,cz FROM source_chunks WHERE dim='.'"))
        dependencies.update(db.execute("SELECT x>>4,z>>4 FROM selected_objects WHERE dim='.'"))
        dependencies.update(db.execute("SELECT DISTINCT c.x>>4,c.z>>4 FROM old.stored_contributions c CROSS JOIN selected_objects o ON o.dim=c.dim AND o.uuid=c.uuid WHERE c.dim='.'"))
        dependencies.update(db.execute("SELECT DISTINCT c.x>>4,c.z>>4 FROM old.members c CROSS JOIN selected_objects o ON o.dim=c.dim AND o.uuid=c.uuid WHERE c.dim='.'"))
        if a.extra_chunks:
            extra_doc=read(a.extra_chunks)
            for row in extra_doc.get('chunks',[]):
                require(row.get('dimension',row.get('dim','.'))=='.','Current pilot satellite copy supports overworld only')
                dependencies.add((row.get('x',row.get('cx')),row.get('z',row.get('cz'))))
        # A bounded source-context border must cover the fixed client distance.
        # Mutation remains limited to declared selected roots/needed visuals.
        # Satellite copies are the exact approved local witnesses plus their
        # native light neighborhood, not additional seven-chunk districts.
        allowed.update((cx+dx,cz+dz) for cx,cz in dependencies for dx in (-1,0,1) for dz in (-1,0,1))
    for rel,row in manifest['files'].items():
        path=Path(rel);include=True
        if bounds and path.suffix=='.mca' and path.parent.name in ('region','entities','poi'):
            m=re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca',path.name)
            if m:
                rx,rz=map(int,m.groups());include=any((cx>>5,cz>>5)==(rx,rz) for cx,cz in allowed)
        if bounds and path.parent.name=='bloodborne_dw_owner_shards':
            match=re.fullmatch(r'owners-(-?\d+)\.(-?\d+)\.nbt',path.name)
            if match:include=tuple(map(int,match.groups())) in allowed
        if include:selected.append((rel,row))
    size=sum(r['bytes'] for _,r in selected);require(shutil.disk_usage(target.anchor).free>=size+6*1024**3,'Copy would breach 6 GiB working reserve')
    progress_path=target.parent/(target.name+'-copy-progress.json');receipt_path=target.parent/(target.name+'-copy.json')
    copy_binding={'source':str(source),'target':str(target),'bounds':bounds,'contextRadius':a.context_radius if bounds else None,
                  'sourceCopyManifestSha256':sha(old/'copy.json'),'selectedChunkSetSha256':digest(sorted(allowed)) if allowed is not None else None}
    had_checkpoint=progress_path.exists()
    checkpoint=read(progress_path) if had_checkpoint else {'schemaVersion':1,'binding':copy_binding,'files':{}}
    require(checkpoint.get('binding')==copy_binding,'Partial source-copy checkpoint belongs to another source/scope')
    chosen={rel for rel,row in selected}
    if target.exists():
        require(not receipt_path.exists(),'Completed source copy must not be overwritten or adopted as a partial copy')
        require(had_checkpoint or target.parent==ROOT/'research'/'pilot','An unjournaled partial clone may only be adopted in this task research/pilot directory')
        # An old pre-journal failed clone can be adopted only if every existing
        # path belongs to this exact independent source copy. Content is proved
        # against the source (or its deterministic cropped sectors) below.
        for existing in files(target):
            rel=existing.relative_to(target).as_posix()
            if had_checkpoint and rel.endswith('.native-grid-copy-writing') and rel.removesuffix('.native-grid-copy-writing') in chosen:continue
            require(rel in chosen,'Partial copy contains an unrelated file: '+rel)
    else:target.mkdir(parents=True)
    atomic_json(progress_path,checkpoint)
    for rel,row in selected:
        src=checked(source/rel);dest=target/rel;dest.parent.mkdir(parents=True,exist_ok=True)
        require(sha(src)==row['sha256'],'Immutable copy source mismatch: '+rel)
        if rel in checkpoint['files']:
            require(dest.is_file() and sha(dest)==checkpoint['files'][rel],'Committed source-copy file changed: '+rel);continue
        temporary=checked(dest.with_name(dest.name+'.native-grid-copy-writing'))
        if bounds and src.suffix=='.mca' and src.parent.name in ('region','entities','poi'):
            rx,rz=map(int,re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca',src.name).groups())
            stream_region(src,temporary,lambda *_:None,keep_chunk=lambda lx,lz:(rx*32+lx,rz*32+lz) in allowed)
        else:shutil.copy2(src,temporary);require(sha(temporary)==row['sha256'],'Copy hash mismatch: '+rel)
        expected=sha(temporary)
        if dest.exists():
            require(dest.is_file(),'Partial copy path is not a regular file: '+rel)
            actual=sha(dest)
            if actual!=expected:
                # The only incomplete direct-stream form produced by the old
                # failed clone has a zero region header followed by an exact
                # prefix of the deterministic copied compressed records.
                partial=False
                if dest.suffix=='.mca' and dest.stat().st_size<=temporary.stat().st_size:
                    with dest.open('rb') as saved,temporary.open('rb') as wanted:
                        header=saved.read(8192);wanted.read(8192)
                        if header and not any(header):
                            partial=True
                            while True:
                                block=saved.read(1024*1024)
                                if not block:break
                                if wanted.read(len(block))!=block:partial=False;break
                require(partial,'Partial copy file differs from the exact original/cropped source: '+rel)
            if actual==expected:temporary.unlink()
            else:os.replace(temporary,dest)
        else:os.replace(temporary,dest)
        checkpoint['files'][rel]=expected;atomic_json(progress_path,checkpoint)
    # Pilot cropping is an explicit clone transform: install per-run before
    # hashes without altering the immutable full source/discovery binding.
    if bounds:
        for rel,_ in selected:
            if (target/rel).parent.name=='region' and (target/rel).suffix=='.mca':
                db.execute('UPDATE regions SET before_hash=? WHERE path=?',(sha(target/rel),rel))
        meta(db,'pilotCopyChunks',len(allowed));db.commit()
    atomic_json(receipt_path,{'status':'PASS',**copy_binding,'localChunkBudget':len(allowed) if allowed is not None else None,'files':len(selected),'bytesBeforeCropping':size,
                              'regionSha256':{rel:checkpoint['files'][rel] for rel,row in selected if Path(rel).parent.name=='region' and Path(rel).suffix=='.mca'}})

def bind_pilot_copy(a):
    """Bind deterministic cropped source hashes after catalogue region import."""
    db=connect(a.plan);target=checked(a.target);scope=meta(db,'scope');require(scope and not meta(db,'applyStarted'),'Only an unapplied pilot can bind a cropped copy')
    path=target.parent/(target.name+'-copy.json');receipt=read(path);binding=meta(db,'binding')
    require(receipt.get('status')=='PASS' and receipt.get('source')==binding['source'] and receipt.get('target')==str(target),'Pilot copy receipt belongs to another source/target')
    require(receipt.get('bounds')==scope['rootBounds'] and receipt.get('sourceCopyManifestSha256')==binding['copyManifestSha256'],'Pilot copy source/scope binding differs')
    hashes=receipt.get('regionSha256');require(isinstance(hashes,dict),'A resumable clone receipt with exact cropped region hashes is required')
    for rel, in db.execute('SELECT path FROM regions').fetchall():
        require(rel in hashes and (target/rel).is_file() and sha(target/rel)==hashes[rel],'Cropped pilot region differs from its committed source copy: '+rel)
        db.execute('UPDATE regions SET before_hash=? WHERE path=?',(hashes[rel],rel))
    for dim,cx,cz in db.execute('SELECT dim,cx,cz FROM source_chunks'):
        rel=(Path(dim)/'region'/('r.%d.%d.mca'%(cx>>5,cz>>5))).as_posix().removeprefix('./')
        with checked(target/rel).open('rb') as stream:
            stream.seek(((cx&31)+(cz&31)*32)*4);require(bool(int.from_bytes(stream.read(4),'big')>>8),'A planned source chunk is absent from cropped copy: '+str((dim,cx,cz)))
    meta(db,'pilotRegionBinding',{'target':str(target),'copyReceipt':str(path),'copyReceiptSha256':sha(path),'regions':db.execute('SELECT count(*) FROM regions').fetchone()[0]})
    if getattr(a,'harness_receipt',None):bind_pilot_harness(db,target,a.harness_receipt)
    db.commit();progress(checked(a.plan),db,'CROPPED_PILOT_SOURCE_BOUND')

def promote_city(a):
    """Reuse one global disk plan after a passed tiny pilot, keeping its cursor."""
    db=connect(a.plan);plan=checked(a.plan);gate=read(a.pilot_gate)
    require(meta(db,'scope') and meta(db,'offlineApplied') and meta(db,'relightComplete'),'A completed, lit pilot is required before promoting the one disk plan')
    require(gate.get('status')=='PASS' and gate.get('denseBrickPilot') is True and gate.get('catalogSha256')==meta(db,'catalog')['sha256'],'Successful root dense-pilot gate must bind this exact catalogue')
    require(not meta(db,'successfulPilotGate'),'The shared plan was already promoted')
    for table in ('placements','selected_objects','whole_cells','regions','files','relight','cleanup_owners'):
        db.execute('CREATE TABLE IF NOT EXISTS pilot_'+table+' AS SELECT * FROM '+table)
    shutil.copyfile(meta(db,'catalog')['path'],plan/'pilot-catalog.json')
    atomic_json(plan/'pilot-state.json',{k:json.loads(v) for k,v in db.execute('SELECT key,value FROM meta')})
    db.commit()
    journal=checked(plan/'journals');archived=checked(plan/'pilot-journals')
    require(journal.parent==plan and archived.parent==plan,'Pilot journal archive target invalid')
    if journal.exists():
        require(not archived.exists(),'Both pilot journal locations exist; preserve them for review')
        os.replace(journal,archived)
    for table in ('selected_objects','whole_cells','fragments','substrate_fragments','native_visual_roots','native_visual_fragments','bridge_cells','cell_queue','activated_native','placements','compositions','cleanup_owners','regions','source_chunks','relight','files','issues'):
        db.execute('DELETE FROM '+table)
    for key in ('scope','target','runtime','applyStarted','offlineApplied','regionPhaseComplete','relightComplete','catalog','compositionsSha256','frozenInputs','pilotRegionBinding','pilotMetadataBinding'):
        db.execute('DELETE FROM meta WHERE key=?',(key,))
    context_stage.leave_pilot_context(module_api(),db)
    meta(db,'successfulPilotGate',str(checked(a.pilot_gate)));db.commit();progress(plan,db,'CITY_PREPARATION_FROM_PASSED_PILOT')

def status(a):
    db=connect(a.plan);progress(checked(a.plan),db,'STATUS',meta={k:json.loads(v) for k,v in db.execute('SELECT key,value FROM meta')},issues=[{'kind':k,'count':n} for k,n in db.execute('SELECT kind,count(*) FROM issues GROUP BY kind')],routes=[{'kind':k,'reason':r,'objects':n} for k,r,n in db.execute('SELECT kind,reason,count(*) FROM objects GROUP BY kind,reason')])

def audit_units(a):
    db=connect(a.plan)
    result={'sourceCandidateUnits':db.execute('SELECT count(*) FROM old.candidates').fetchone()[0],
            'realStoredOwnerUUIDs':db.execute('SELECT count(*) FROM old.stored_owners').fetchone()[0],
            'realBEOwnerUUIDs':db.execute('SELECT count(*) FROM old.be_owners').fetchone()[0],
            'sourceStatusCounts':dict(db.execute('SELECT status,count(*) FROM old.candidates GROUP BY status')),
            'topRegistryUnits':[{'registryId':rid,'units':n,'storedOwners':stored} for rid,n,stored in db.execute('SELECT c.registry_id,count(*),sum(EXISTS(SELECT 1 FROM old.stored_owners s WHERE s.dim=c.dim AND s.uuid=c.uuid)) FROM old.candidates c GROUP BY c.registry_id ORDER BY count(*) DESC LIMIT 40')],
            'compactPoseUnits':db.execute("SELECT count(*) FROM old.candidates WHERE json_extract(pose_data,'$.properties.compact')='true'").fetchone()[0],
            'policy':'Candidates include synthetic source-state expansion units; they are not all old owner UUIDs. No world scan.'}
    atomic_json(checked(a.plan)/'source-units.json',result)

def pilot_native_requests(a):
    # Exactly the saved pilot payloads; never a fresh world scan.
    old=sqlite3.connect('file:'+str(DEFAULT_OLD/'plan.sqlite').replace('\\','/')+'?mode=ro',uri=True)
    capabilities={r['registryId']:r for r in read(DEFAULT_OLD.parents[2]/'tools'/'ordinary-stage1'/'source-capabilities.json')['states']}
    protected={r['registryId'] for r in read(PROJECT/'tools'/'native-grid'/'protected-v12-ids.json')['entries']}
    states={};chunks=0
    for compression,payload in old.execute("SELECT compression,payload FROM chunk_cache WHERE dim='.' AND x BETWEEN 57 AND 62 AND z BETWEEN 4 AND 9"):
        chunks+=1;root=compound(decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]).root)
        for section in root.get('sections',Tag(9,[],10)).value:
            container=compound(section).get('block_states')
            if container is None:continue
            for tag in compound(container).get('palette',Tag(9,[],10)).value:
                value=state_json(tag);rid=value['registryId'];cap=capabilities.get(rid,{})
                compact=value['properties'].get('compact')=='true' and int(value['properties'].get('rotation','0'))%2==0
                if rid in protected or ((cap.get('anyStateHasBlockEntity') or cap.get('compositeRoot')) and not compact):continue
                value['physicsOnly']=rid.startswith('bloodborne_dw:')
                states[canonical({k:v for k,v in value.items() if k!='physicsOnly'})]=value
    atomic_json(ROOT/'research'/'pilot-native-states.json',{'schemaVersion':1,'bounds':[912,1007,64,159],'cachedChunks':chunks,'nativeStates':[states[k] for k in sorted(states)]})

def normalize_substrates(a):
    db=connect(a.plan);require(not meta(db,'applyStarted'),'Applied plan is frozen')
    db.execute("UPDATE objects SET kind='substrate',reason='existing_native_static_substrate' WHERE kind='static' AND NOT EXISTS(SELECT 1 FROM old.stored_owners s WHERE s.dim=objects.dim AND s.uuid=objects.uuid) AND NOT EXISTS(SELECT 1 FROM old.be_owners b WHERE b.dim=objects.dim AND b.uuid=objects.uuid)")
    db.commit();progress(checked(a.plan),db,'SUBSTRATE_CLASSIFICATION_READY',routes=[{'kind':k,'objects':n} for k,n in db.execute('SELECT kind,count(*) FROM objects GROUP BY kind')])

def plain_tag(tag):
    if tag.type==10:return {k:plain_tag(v) for k,v in tag.value.items()}
    if tag.type==9:return [plain_tag(v) for v in tag.value]
    if tag.type==7:return list(tag.value)
    return tag.value

def payload_audit(a):
    db=connect(a.plan);groups=[]
    rows=db.execute("SELECT detail,count(*),min(dim),x,y,z FROM old.issues WHERE kind='private_payload' GROUP BY detail").fetchall()
    for encoded,count,dim,x,y,z in rows:
        detail=json.loads(encoded);samples=[]
        nbt,rel,before=get_source_chunk(db,dim,x>>4,z>>4)
        for tag in compound(nbt.root).get('block_entities',Tag(9,[],10)).value:
            be=compound(tag)
            for item in be_owners(be):
                own=compound(item);owner=compound(own['owner'])
                if owner.get('root',Tag(11,[])).value!=[x,y,z]:continue
                data=compound(own['rootData']);rid=data['id'].value
                if rid!=detail.get('id'):continue
                payload=data.get('payload',Tag(7,b''));payload=compound(decode_nbt(bytes(payload.value)).root) if payload.type==7 and payload.value else compound(payload) if payload.type==10 else {}
                samples.append({'registryId':rid,'uuid':owner_uuid(own['owner']),'root':[x,y,z],
                                'payloadTypes':{k:v.type for k,v in payload.items()},'payload':{k:plain_tag(v) for k,v in payload.items()},
                                'sourceRegion':rel,'sourceRegionSha256':before,'fromSavedChunkCache':True})
        groups.append({'detail':detail,'issueOccurrences':count,'representativeRoot':[dim,x,y,z],'samples':samples[:2]})
    atomic_json(checked(a.plan)/'private-payload-audit.json',{'schemaVersion':1,'worldReads':0,'savedChunkGroups':len(groups),'groups':groups,'policy':'Unique registry/key-set groups and representative saved rootData only; no per-instance city/world scan'})

def resolve_payloads(a):
    db=connect(a.plan);require(not meta(db,'applyStarted'),'Applied plan is frozen')
    classification=read(meta(db,'classification')['path']);routes={r['key']:r for r in classification['poses']}
    marker_aliases=meta(db,'carrierMarkerAliases') or {};marker_poses={}
    audit=read(checked(a.plan)/'private-payload-audit.json');decisions=[]
    assembly_keys={'ImportedAssemblyV126','ImportedAssemblyV126Parts','ImportedAssemblyV126References'}
    assembly_ids={g['detail']['id'] for g in audit['groups'] if set(g['detail']['keys'])==assembly_keys}
    originals={g['detail']['id'] for g in audit['groups'] if g['detail']['keys']==['OriginalSourceBlockEntities']}
    cache=collections.OrderedDict()
    def root_payload(dim,uid,x,y,z):
        token=(dim,x>>4,z>>4)
        if token not in cache:
            nbt,_,_=get_source_chunk(db,*token);found={}
            for tag in compound(nbt.root).get('block_entities',Tag(9,[],10)).value:
                for item in be_owners(compound(tag)):
                    own=compound(item);data=compound(own['rootData']);raw=data.get('payload',Tag(7,b''))
                    payload=compound(decode_nbt(bytes(raw.value)).root) if raw.type==7 and raw.value else compound(raw) if raw.type==10 else {}
                    found[owner_uuid(own['owner'])]={k:plain_tag(v) for k,v in payload.items()}
            cache[token]=found
            if len(cache)>4:cache.popitem(last=False)
        cache.move_to_end(token);return cache[token].get(uid)
    rows=db.execute("SELECT o.dim,o.uuid,o.pose,o.x,o.y,o.z,p.data FROM objects o JOIN poses p ON p.key=o.pose WHERE o.source_status='private_payload'").fetchall()
    for dim,uid,pose,x,y,z,encoded in rows:
        source=json.loads(encoded);rid=source['registryId'];route=routes[marker_aliases.get(pose,pose)];kind=route['route'];reason=route['reason'];payload=None
        if kind=='preserve':
            # Priority exceptions retain their exact original reason/data.
            pass
        elif rid in assembly_ids:
            kind=route['route'];reason='baked_installed_assembly_pose_historical_provenance_only'
        elif rid in originals:
            payload=root_payload(dim,uid,x,y,z)
            if payload is None:kind='preserve';reason='source_be_provenance_not_found_in_addressed_cache'
            else:
                records=payload.get('OriginalSourceBlockEntities',[]);content=[]
                for entity in records:
                    significant={k:v for k,v in entity.items() if k not in ('id','x','y','z','keepPacked') and v not in ([],{},'')}
                    if significant:content.append({'id':entity.get('id'),'content':significant})
                if content:kind='preserve';reason='real_source_be_gameplay_payload'
                else:kind=route['route'];reason='baked_pose_empty_historical_source_be_provenance'
        elif rid in ('bloodborne_dw:source_large_grille_gate','bloodborne_dw:source_original_tree'):
            payload=root_payload(dim,uid,x,y,z)
            if payload is not None and set(payload)-{'ImportedTypeVersion','SourceShift','MountY','LegacyStairArt'}=={'SourceCarrierContextVersion'} and payload['SourceCarrierContextVersion']==1:
                source['sourceCarrierContextVersion']=1;new_pose='sp_'+digest(source)[:24]
                db.execute('INSERT OR IGNORE INTO poses(key,data) VALUES(?,?)',(new_pose,canonical(source)))
                db.execute('UPDATE objects SET pose=? WHERE dim=? AND uuid=?',(new_pose,dim,uid))
                marker_aliases[new_pose]=marker_aliases.get(pose,pose);marker_poses[new_pose]={'key':new_pose,**source}
                kind=route['route'];reason='baked_exact_source_carrier_context_v1'
            else:kind='preserve';reason='specific_unclassified_private_payload'
        else:kind='preserve';reason='specific_unclassified_private_payload'
        db.execute('UPDATE objects SET kind=?,reason=? WHERE dim=? AND uuid=?',(kind,reason,dim,uid))
        decisions.append({'dim':dim,'uuid':uid,'registryId':rid,'root':[x,y,z],'kind':kind,'reason':reason,
                          'sourceBE':payload.get('OriginalSourceBlockEntities') if payload else None})
    meta(db,'carrierMarkerAliases',marker_aliases)
    for key in marker_aliases:
        encoded=db.execute('SELECT data FROM poses WHERE key=?',(key,)).fetchone()
        if encoded:marker_poses[key]={'key':key,**json.loads(encoded[0])}
    for key in set(marker_aliases)|set(marker_aliases.values()):db.execute('UPDATE poses SET objects=(SELECT count(*) FROM objects WHERE pose=?) WHERE key=?',(key,key))
    marker_output=ROOT/'research'/'carrier-marker-poses.json'
    atomic_json(marker_output,{'schemaVersion':1,'poses':[marker_poses[key] for key in sorted(marker_poses)],'nativeStates':[],
                               'sourceBinding':meta(db,'binding'),'proof':'SourceCarrierContextVersion=1 only; all actual game payloads remain locally preserved'})
    meta(db,'carrierMarkerRequest',{'path':str(marker_output),'sha256':sha(marker_output)})
    # Protection is derived from current local disposition, never a stale gate.
    db.execute("DELETE FROM protected_cells WHERE NOT EXISTS(SELECT 1 FROM objects o WHERE o.dim=protected_cells.dim AND o.uuid=protected_cells.uuid AND o.kind='preserve')")
    db.commit();report={'schemaVersion':1,'status':'PASS','worldReads':0,'decisions':decisions,
                       'byReason':dict(collections.Counter(d['reason'] for d in decisions)),
                       'proofs':{'assembly':'ImportedAssemblyV126 stores historical parts; current installed variant/ArtBank is the compiled complete pose. No renderer reads PARTS_KEY.',
                                 'sourceBEs':'All addressed OriginalSourceBlockEntities payloads were grouped by actual content. Nonempty gameplay content is retained.',
                                 'priority':'Historic v12/openable/sprite routes override payload simplification.'}}
    output=checked(a.plan)/'payload-decisions.json';atomic_json(output,report);meta(db,'payloadDecisions',{'path':str(output),'sha256':sha(output)});db.commit();progress(checked(a.plan),db,'PAYLOAD_CLASSIFICATION_READY',payloadReasons=report['byReason'])

def native_visual_needs(a):
    db=connect(a.plan);path=Path(meta(db,'poseFragments')['path']);native=sqlite3.connect('file:'+path.as_posix()+'?mode=ro',uri=True)
    rows=read(checked(a.plan)/'native-states-needed.json')['nativeStates'];wanted=[];missing=[]
    for row in rows:
        state={k:v for k,v in row.items() if k!='physicsOnly'};saved=native.execute('SELECT data FROM native WHERE state=?',(canonical(state),)).fetchone()
        if not saved:missing.append(state);continue
        value=json.loads(saved[0])
        if state.get('properties',{}).get('compact')=='true' and not value.get('nativeVisualComplete',True):wanted.append({**state,'physicsOnly':False})
    atomic_json(ROOT/'research'/'native-visual-request.json',{'schemaVersion':1,'poses':[],'nativeStates':wanted,'missingNativePhysics':missing,'source':'Exact actual pilot composed states; no speculative registry combinations'})

def door_request(a):
    db=connect(a.plan);ids={r['registryId'] for r in read(ROOT/'research'/'openable-sprite-classification.json')['types'] if r.get('openable')}
    poses=[]
    for key,encoded in db.execute('SELECT key,data FROM poses'):
        row=json.loads(encoded)
        if row['registryId'] not in ids:continue
        if row['registryId']=='bloodborne_dw:source_large_grille_gate':row['sourceCarrierContextVersion']=1
        poses.append({'key':key,**row})
    output=ROOT/'research'/'door-footprint-request.json';atomic_json(output,{'schemaVersion':1,'poses':poses,'sourceBinding':meta(db,'binding')});meta(db,'doorFootprintRequest',{'path':str(output),'sha256':sha(output)});db.commit()

def import_door_footprints(a):
    db=connect(a.plan);request=meta(db,'doorFootprintRequest');report=read(a.footprints)
    require(request and report.get('status')=='PASS' and report.get('nativeDescriptor') is True,'A successful native descriptor footprint export is required')
    require(report.get('sourceRequestSha256')==request['sha256'],'Door footprint export request differs')
    require(report.get('baselineModJarSha256')=='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51','Door descriptor export used another baseline JAR')
    for row in report['poses']:
        for dx,dy,dz in row['cells']:
            db.execute("INSERT OR IGNORE INTO protected_cells SELECT dim,x+?,y+?,z+?,uuid,'openable_both_states_local_helpers' FROM objects WHERE pose=? AND kind='preserve'",(dx,dy,dz,row['key']))
    meta(db,'doorFootprintGuard',{'path':str(checked(a.footprints)),'sha256':sha(a.footprints),'poses':len(report['poses'])});db.commit();progress(checked(a.plan),db,'OPENABLE_BOTH_STATES_PROTECTED')

def accept_bridge_carriers(a):
    db=connect(a.plan);expected=meta(db,'bridgeHostStates');receipt=read(a.receipt)
    require(expected and receipt.get('status')=='PASS' and receipt.get('nativeRegistryQueries') is True,'Native actual-carrier bridge query gate required')
    require(receipt.get('bridgePlanSha256')==expected['sha256'] and receipt.get('catalogSha256')==meta(db,'catalog')['sha256'],'Native carrier query plan/catalogue binding differs')
    require(receipt.get('pairsChecked')==expected['pairs'] and receipt.get('runtimeSha256'),'Native query receipt must cover every exact carrier/base pair and actual runtime')
    meta(db,'bridgeCarrierGate',{'path':str(checked(a.receipt)),'sha256':sha(a.receipt)});db.commit();progress(checked(a.plan),db,'BRIDGE_NATIVE_PREDICATES_PROVEN')

def correct_bridge_optics(a):
    """Use exact observed carrier predicates; the runtime guard stays strict."""
    db=connect(a.plan);plan=checked(a.plan);require(not meta(db,'applyStarted'),'Applied catalogue/cursor is frozen')
    audit=read(a.receipt);expected=meta(db,'bridgeHostStates');binding=meta(db,'catalog');catalog=read(binding['path'])
    require(audit.get('nativeRegistryQueries') is True and audit.get('catalogSha256')==binding['sha256'] and audit.get('bridgePlanSha256')==expected['sha256'],'Actual carrier correction evidence belongs to another plan/catalogue')
    observed={}
    for row in audit['results']:
        require(type(row.get('opaque'))is bool and type(row.get('sidedTransparency'))is bool and row.get('fluidEmpty') is True and row.get('luminance')==0,'Only proved dry nonemitting actual flags may correct shared bridge optics')
        require(row.get('status')=='PASS' or row.get('error')=='java.lang.IllegalArgumentException: Native bridge worldless optical predicates differ','The native audit has another failure; do not override it')
        item=row['input'];observed[(canonical(item['state']),item['cell'])]=row
    wanted=read(expected['path'])['pairs'];require(len(observed)==audit.get('pairsChecked')==expected['pairs'] and set(observed)=={(canonical(r['state']),r['cell']) for r in wanted},'Correction must cover the exact finite host/base matrix')
    changed=set()
    for dim,x,y,z,host,index in db.execute('SELECT dim,x,y,z,host_state,type_index FROM bridge_cells').fetchall():
        actual=observed[(host,index)];old_key=db.execute('SELECT composition FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)).fetchone()[0]
        comp=json.loads(db.execute('SELECT data FROM compositions WHERE key=?',(old_key,)).fetchone()[0]);optics=dict(catalog['optics'][catalog['cells'][index]['optics']])
        optics.update(opaque=actual['opaque'],sidedTransparency=actual['sidedTransparency']);comp['optics']=optics
        key='cell_'+digest(comp);db.execute('INSERT OR IGNORE INTO compositions VALUES(?,?,NULL)',(key,canonical(comp)))
        db.execute('UPDATE placements SET composition=?,type_index=NULL WHERE dim=? AND x=? AND y=? AND z=?',(key,dim,x,y,z));changed.add((old_key,key))
    db.execute('UPDATE bridge_cells SET type_index=NULL')
    db.execute('DELETE FROM compositions WHERE NOT EXISTS(SELECT 1 FROM placements p WHERE p.composition=compositions.key)')
    output=plan/'compositions.jsonl';temporary=output.with_suffix('.writing')
    with temporary.open('w',encoding='utf-8',newline='\n') as stream:
        for key,data in db.execute('SELECT key,data FROM compositions ORDER BY key'):stream.write(canonical({'key':key,**json.loads(data)})+'\n')
        stream.flush();os.fsync(stream.fileno())
    os.replace(temporary,output);generation=meta(db,'compositionGeneration');generation.update(compositionsSha256=sha(output),plannerSha256=sha(__file__))
    meta(db,'compositionGeneration',generation);meta(db,'compositionsSha256',sha(output))
    correction={'schemaVersion':1,'status':'PASS','actualCarrierAudit':str(checked(a.receipt)),'actualCarrierAuditSha256':sha(a.receipt),'oldCatalogSha256':binding['sha256'],
                'changedCompositions':len(changed),'unchangedScope':meta(db,'scope'),'changes':'Only observed opaque/sidedTransparency flags; geometry, physics, fluid, emission, opacity and ambient light remain the compiled exact values.'}
    atomic_json(plan/'bridge-optics-correction.json',correction)
    for name in ('catalog','bridgeHostStates','bridgeCarrierGate','cleanupBeAudit','runtime','frozenInputs'):db.execute('DELETE FROM meta WHERE key=?',(name,))
    db.commit();progress(plan,db,'COMPOSITIONS_READY',compositionFile=str(output),bridgeOpticsCorrection=correction)

def audit_cleanup_bes(a):
    """Prove actual cleanup-only BEs and resident payloads, not only new cells."""
    db=connect(a.plan);plan=checked(a.plan);require(meta(db,'catalog') and not meta(db,'applyStarted'),'Audit the final unapplied cleanup selection')
    chunks=db.execute('SELECT DISTINCT dim,x>>4,z>>4 FROM selected_objects UNION SELECT DISTINCT c.dim,c.x>>4,c.z>>4 FROM old.stored_contributions c CROSS JOIN selected_objects o ON o.dim=c.dim AND o.uuid=c.uuid UNION SELECT DISTINCT c.dim,c.x>>4,c.z>>4 FROM old.members c CROSS JOIN selected_objects o ON o.dim=c.dim AND o.uuid=c.uuid').fetchall()
    decisions=read(meta(db,'payloadDecisions')['path'])['decisions'];known={ (r['dim'],r['uuid']):r['reason'] for r in decisions }
    geometric={'ImportedTypeVersion','ImportedStairLegacyArt','SourceShift','MountY','VerticalOffset'}
    assembly={'ImportedAssemblyV126','ImportedAssemblyV126Parts','ImportedAssemblyV126References'}
    failures=[];examined=0;resident_count=0
    def selected(dim,uid):return db.execute('SELECT p.data FROM selected_objects s JOIN poses p ON p.key=s.pose WHERE s.dim=? AND s.uuid=?',(dim,uid)).fetchone()
    for number,(dim,cx,cz) in enumerate(chunks):
        nbt,_,_=get_source_chunk(db,dim,cx,cz)
        for tag in compound(nbt.root).get('block_entities',Tag(9,[],10)).value:
            be=compound(tag);at=tuple(be[k].value for k in ('x','y','z'));old=be_owners(be)
            gone=[owner_uuid(compound(item)['owner']) for item in old if selected(dim,owner_uuid(compound(item)['owner']))]
            resident=owner_uuid(be['resident']) if 'resident' in be else None;pose_row=selected(dim,resident) if resident else None
            if not gone and not pose_row:continue
            examined+=1;retained=len(old)>len(gone) or (resident and not pose_row)
            allowed={'id','x','y','z','keepPacked','payload','resident','owners','OwnerDataV1'}
            if be.get('id',Tag(8,'')).value=='bloodborne_dw:source_ladder_owner':allowed.update({'Owner','Root','FixedBacking','Original'})
            if not retained and set(be)-allowed:failures.append({'dimension':dim,'cell':at,'reason':'unknown_outer_payload_on_deleted_be','keys':sorted(set(be)-allowed),'owners':gone})
            if pose_row:
                resident_count+=1;payload=plain_tag(be.get('payload',Tag(10,{})));pose=json.loads(pose_row[0]);private=set(payload)-geometric;reason=known.get((dim,resident),'')
                if reason=='baked_installed_assembly_pose_historical_provenance_only':private-=assembly
                if reason=='baked_pose_empty_historical_source_be_provenance':
                    private.discard('OriginalSourceBlockEntities')
                    for entity in payload.get('OriginalSourceBlockEntities',[]):
                        if any(k not in ('id','x','y','z','keepPacked') and v not in ([],{},'') for k,v in entity.items()):
                            failures.append({'dimension':dim,'cell':at,'reason':'actual_resident_has_source_be_game_data','owner':resident});break
                if pose.get('sourceCarrierContextVersion')==1 and payload.get('SourceCarrierContextVersion')==1:private.discard('SourceCarrierContextVersion')
                if private:failures.append({'dimension':dim,'cell':at,'reason':'actual_resident_private_payload_not_classified','keys':sorted(private),'owner':resident})
                actual_shift=payload.get('SourceShift',[0,0,0]);actual_mount=payload.get('MountY',0)+payload.get('VerticalOffset',0);actual_art=payload.get('ImportedStairLegacyArt','')
                if actual_shift!=pose.get('shift',[0,0,0]) or abs(actual_mount-pose.get('mountY',0))>1e-10 or actual_art!=pose.get('legacyStairArt',''):
                    failures.append({'dimension':dim,'cell':at,'reason':'actual_resident_geometric_pose_differs_from_saved_source','owner':resident})
        if number%64==63:atomic_json(plan/'cleanup-be-audit-progress.json',{'checkedChunks':number+1,'totalChunks':len(chunks),'examinedBlockEntities':examined})
    report={'schemaVersion':1,'status':'PASS' if not failures else 'FAIL','worldReads':0,'savedChunks':len(chunks),'examinedBlockEntities':examined,'selectedResidentPayloads':resident_count,'failures':failures,
            'selection':{'objects':db.execute('SELECT count(*) FROM cleanup_owners').fetchone()[0],'placements':db.execute('SELECT count(*) FROM placements').fetchone()[0]},'catalogSha256':meta(db,'catalog')['sha256']}
    output=plan/'cleanup-be-audit.json';atomic_json(output,report);meta(db,'cleanupBeAudit',{'path':str(output),'sha256':sha(output),'status':report['status']});db.commit()
    require(not failures,'Actual cleanup/resident data require exact local handling before apply: '+str(output));progress(plan,db,'ALL_CLEANUP_BLOCK_ENTITIES_PROVEN')

STORAGE_CLASS_PREFIX='dev/dreamwalker/bloodbornedw/composite/CompactStateTransitions'
STORAGE_MIXIN_CLASS='dev/dreamwalker/bloodbornedw/composite/mixin/NewStateStorageMixin.class'
STORAGE_SOURCE_PATHS={
    'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompactStateTransitions.java',
    'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin/NewStateStorageMixin.java'}

def storage_jar_proof(old_path,new_path):
    """No resources, registration, light, rendering or other runtime may change."""
    def entries(path):
        with zipfile.ZipFile(checked(path)) as archive:
            names=[i.filename for i in archive.infolist() if not i.is_dir()]
            require(len(names)==len(set(names)),'Duplicate JAR entry is not a safe runtime binding')
            require(all(not n.startswith('/') and '..' not in Path(n).parts for n in names),'Unsafe JAR entry path')
            return {name:hashlib.sha256(archive.read(name)).hexdigest() for name in names}
    old,new=entries(old_path),entries(new_path)
    def allowed(name):return name==STORAGE_MIXIN_CLASS or name==STORAGE_CLASS_PREFIX+'.class' or name.startswith(STORAGE_CLASS_PREFIX+'$') and name.endswith('.class')
    changed=[name for name in sorted(old.keys()|new.keys()) if old.get(name)!=new.get(name)]
    require(changed and all(allowed(name) for name in changed),'Runtime differs outside the two authorized state-storage class families: '+str([n for n in changed if not allowed(n)]))
    require(STORAGE_CLASS_PREFIX+'.class' in old and STORAGE_CLASS_PREFIX+'.class' in new and STORAGE_MIXIN_CLASS in old and STORAGE_MIXIN_CLASS in new,'Both state-storage entrypoint classes must exist in both JARs')
    return {'schemaVersion':1,'status':'PASS','oldRuntimeSha256':sha(old_path),'newRuntimeSha256':sha(new_path),'oldEntries':len(old),'newEntries':len(new),
            'unchangedEntries':sum(old.get(n)==new.get(n) for n in old.keys()&new.keys()),'unchangedEntrySetSha256':digest({n:old[n] for n in sorted(old.keys()&new.keys()) if old[n]==new[n]}),
            'changedEntries':[{'path':n,'beforeSha256':old.get(n),'afterSha256':new.get(n)} for n in changed],
            'catalogSha256':new.get(CATALOG_REL),'meshesSha256':new.get('assets/bloodborne_dw/native_grid/meshes.bin'),'allOtherEntriesByteIdentical':True}

def storage_source_proof(path):
    report=read(path);require(report.get('schemaVersion')==1 and checked(report['sourceRoot'])==PROJECT,'Storage source receipt belongs to another source tree')
    rows=report.get('files',[]);require(len(rows)==2 and {r['path'] for r in rows}==STORAGE_SOURCE_PATHS,'Storage patch source scope must be exactly the two authorized files')
    require(report.get('sourceEditsLimitedToListedFiles') is True,'Source receipt must identify the actual bounded patch scope')
    for row in rows:
        require(sha(checked(PROJECT/row['path']))==row['afterSha256'],'Storage source differs from the reviewed after bytes')
        require(row.get('beforePath') and sha(checked(row['beforePath']))==row['beforeSha256'],'The original storage source bytes were not preserved')
    return {'path':str(checked(path)),'sha256':sha(path),'files':rows,'scope':'Only two edited source files are evidenced; all other compiled runtime entries are independently byte-compared.'}

def adopted_light_cursor(queue_bytes,cursor_bytes,old_runtime,new_runtime,engine):
    require(cursor_bytes.endswith(b'\n'),'A torn light journal tail must not be treated as completed progress')
    queue_hash=hashlib.sha256(queue_bytes).hexdigest();lines=cursor_bytes.splitlines(keepends=True)
    require(lines and lines[0].rstrip(b'\r\n')==('# '+queue_hash+' '+old_runtime).encode(),'Saved light cursor belongs to another queue/runtime')
    queued=[line for line in queue_bytes.decode('utf-8').splitlines() if line and not line.startswith('#')]
    saved=[line.decode('utf-8').rstrip('\r\n') for line in lines[1:]]
    require(len(set(queued))==len(queued) and len(set(saved))==len(saved),'Duplicate light rows are not verified progress')
    require(saved==queued[:len(saved)],'Saved light rows are not an exact completed prefix of this queue')
    require(engine.get('nativeEngine') is True and engine.get('saveFlushed') is True and engine.get('queueSha256')==queue_hash and engine.get('runtimeSha256')==old_runtime,'Native saved progress receipt is not bound to the failed runtime/queue')
    require(engine.get('queued')==len(queued) and engine.get('processed')==len(saved) and engine.get('pending')==len(queued)-len(saved),'Native saved receipt does not prove every adopted cursor row')
    updated=('# '+queue_hash+' '+new_runtime+'\n').encode()+b''.join(lines[1:])
    return updated,{'queued':len(queued),'processed':len(saved),'pending':len(queued)-len(saved),'rowsSha256':hashlib.sha256(b''.join(lines[1:])).hexdigest(),'oldCursorSha256':hashlib.sha256(cursor_bytes).hexdigest(),'newCursorSha256':hashlib.sha256(updated).hexdigest(),'change':'Runtime hash in header only; all saved rows retain their exact bytes.'}

def storage_plan_fingerprint(db):
    report={}
    for table in ('placements','selected_objects','cleanup_owners','regions','files','relight'):
        h=hashlib.sha256();count=0
        for row in db.execute('SELECT * FROM '+table+' ORDER BY rowid'):
            h.update(canonical(row).encode());h.update(b'\n');count+=1
        report[table]={'rows':count,'sha256':h.hexdigest()}
    return report

def _rebind_replace(path,payload,before_hash):
    path=checked(path);after_hash=hashlib.sha256(payload).hexdigest();actual=sha(path)
    require(actual in (before_hash,after_hash),'Interrupted metadata transition has unrelated bytes: '+str(path))
    if actual==after_hash:return
    temporary=path.with_name(path.name+'.storage-rebind-writing')
    with temporary.open('wb') as stream:stream.write(payload);stream.flush();os.fsync(stream.fileno())
    os.replace(temporary,path)

def rebind_storage_runtime(a):
    """Explicit failed-light recovery, preserving every map/coordinate journal."""
    plan=checked(a.plan);db=connect(plan)
    try:
        require(meta(db,'offlineApplied') is True and not meta(db,'relightComplete') and meta(db,'applyStarted') is True,'Storage runtime rebind requires an offline-applied, incomplete migration')
        require(not db.execute("SELECT 1 FROM files WHERE phase!='committed' LIMIT 1").fetchone(),'Region/shard writes must all be committed before changing a failed-light runtime')
        target=checked(meta(db,'target'));require((target/'NATIVE-GRID-INCOMPLETE.json').is_file(),'A completed/playable map cannot rebind its storage runtime')
        old_path,new_path=checked(a.old_runtime_jar),checked(a.runtime_jar);old_hash,new_hash=sha(old_path),sha(new_path)
        require(old_hash!=new_hash,'A runtime recovery must identify an actual bounded storage fix')
        directory=plan/'runtime-transitions'/('storage-'+old_hash[:16]+'-'+new_hash[:16]);intent_path=directory/'intent.json';completion=directory/'completion.json'
        if completion.exists():
            saved=read(completion);require(saved.get('status')=='PASS' and meta(db,'runtime')['sha256']==new_hash and meta(db,'runtimeStorageTransition')==saved['transition'],'Committed storage transition differs from current runtime');return
        directory.mkdir(parents=True,exist_ok=True);before=directory/'before';before.mkdir(exist_ok=True)
        if intent_path.exists():
            intent=read(intent_path);require(intent['oldRuntimeSha256']==old_hash and intent['newRuntimeSha256']==new_hash,'Another runtime transition owns this recovery directory')
            old_meta=read(before/'meta.json');require(storage_plan_fingerprint(db)==intent['planFingerprint'],'Coordinate/light/file journals changed during runtime rebind')
        else:
            current=meta(db,'runtime');require(current and current['sha256']==old_hash,'The archived old JAR does not match the applied migration runtime')
            old_meta={key:meta(db,key) for key in ('runtime','frozenInputs','bridgeCarrierGate','runtimeStorageTransition')}
            require(old_meta['frozenInputs'] and read(plan/'frozen-inputs.json')==old_meta['frozenInputs'],'Original frozen inputs are missing or changed')
            archive_names=('relight-binding.json','frozen-inputs.json','relight-engine.json','relight-launch-binding.json','relight-launch-receipt.json','relight-server-console.log','relight-engine.json.done.tsv')
            archived={}
            for name in archive_names:
                src=checked(plan/name);dest=before/name;require(src.is_file(),'Missing failed-light evidence: '+name);wanted=sha(src)
                if dest.exists():require(sha(dest)==wanted,'Pre-transition evidence archive changed: '+name)
                else:
                    with src.open('rb') as inp,dest.open('wb') as out:shutil.copyfileobj(inp,out);out.flush();os.fsync(out.fileno())
                archived[name]=wanted
            if old_meta['bridgeCarrierGate']:
                gate=old_meta['bridgeCarrierGate'];require(sha(gate['path'])==gate['sha256'],'Applied native carrier gate changed')
                with checked(gate['path']).open('rb') as inp,(before/'bridge-carrier-gate.json').open('wb') as out:shutil.copyfileobj(inp,out);out.flush();os.fsync(out.fileno())
            atomic_json(before/'meta.json',old_meta)
            intent={'schemaVersion':1,'phase':'PREPARED','oldRuntimeSha256':old_hash,'newRuntimeSha256':new_hash,'target':str(target),'archivedEvidence':archived,'planFingerprint':storage_plan_fingerprint(db)}
            atomic_json(intent_path,intent)
        require(meta(db,'runtime')['sha256'] in (old_hash,new_hash),'Another runtime was installed during recovery')
        for name,wanted in intent['archivedEvidence'].items():require(sha(before/name)==wanted,'Archived failed-light evidence changed: '+name)
        old_binding=read(before/'relight-binding.json');engine=read(before/'relight-engine.json');failed=read(before/'relight-launch-receipt.json');launch=read(before/'relight-launch-binding.json')
        require(failed.get('status')=='FAIL' and failed.get('runtimeSha256')==old_hash and 'OutOfMemoryError' in (before/'relight-server-console.log').read_text(encoding='utf-8',errors='replace'),'The storage exception requires the archived actual failed-runtime OOM evidence')
        catalog=meta(db,'catalog');require(old_binding['runtime']['sha256']==old_hash and old_binding['target']==str(target) and engine.get('world')==str(target) and old_binding['catalog']==catalog and engine.get('catalogSha256')==catalog['sha256'],'Failed engine/binding belongs to another map/catalogue/runtime')
        proof=storage_jar_proof(old_path,new_path);require(proof['catalogSha256']==catalog['sha256'] and proof['meshesSha256']==catalog['meshesSha256'],'State-storage recovery cannot change catalogue or meshes')
        source_proof=storage_source_proof(a.source_receipt)
        audit=read(a.receipt);matrix=meta(db,'bridgeHostStates')
        require(audit.get('status')=='PASS' and audit.get('nativeRegistryQueries') is True and audit.get('runtimeSha256')==new_hash,'Fresh actual native carrier audit for the new runtime is required')
        require(audit.get('catalogSha256')==catalog['sha256'] and audit.get('bridgePlanSha256')==matrix['sha256'] and audit.get('pairsChecked')==matrix['pairs'],'New runtime audit does not cover the exact applied carrier matrix')
        helper=checked(a.helper_jar) if a.helper_jar else None
        if helper is None:
            choices=[p for p in (PROJECT/'build'/'libs').glob('dreamwalker-native-grid-relight-*.jar') if not p.name.endswith('-dev.jar')];require(len(choices)==1,'Select the actual unchanged light helper with --helper-jar');helper=checked(choices[0])
        helper_hash=sha(helper);require(launch.get('helperSha256')==helper_hash and launch.get('runtimeSha256')==old_hash and launch.get('queueSha256')==old_binding['queueSha256'],'The failed light helper/queue differs; saved cursor cannot be adopted')
        queue_bytes=(plan/'relight-queue.tsv').read_bytes();require(hashlib.sha256(queue_bytes).hexdigest()==old_binding['queueSha256'],'Relight queue changed after failed native engine save')
        cursor_bytes,cursor_proof=adopted_light_cursor(queue_bytes,(before/'relight-engine.json.done.tsv').read_bytes(),old_hash,new_hash,engine)
        require(old_binding['queued']==cursor_proof['queued']==db.execute('SELECT count(*) FROM relight').fetchone()[0],'The same complete light queue must remain pending in the migration plan')
        fresh_gate={'path':str(checked(a.receipt)),'sha256':sha(a.receipt)};new_runtime={'path':str(new_path),'sha256':new_hash}
        receipt={'schemaVersion':1,'status':'PASS','kind':'STORAGE_STATE_TRANSITION_ONLY','oldRuntime':{'path':str(old_path),'sha256':old_hash},'newRuntime':new_runtime,'target':str(target),
                 'catalog':catalog,'jarEntryProof':proof,'sourcePatchProof':source_proof,'newActualCarrierGate':fresh_gate,'unchangedLightHelper':{'path':str(helper),'sha256':helper_hash},
                 'queueSha256':old_binding['queueSha256'],'adoptedNativeSavedCursor':cursor_proof,'archivedFailedEvidence':intent['archivedEvidence'],'oldFrozenInputsSha256':sha(before/'frozen-inputs.json'),
                 'unchangedPlanFingerprint':intent['planFingerprint'],'mapWrites':0,'plannerSha256':sha(__file__)}
        receipt_path=directory/'transition-receipt.json'
        if receipt_path.exists():require(read(receipt_path)==receipt,'Resumed runtime transition proof changed')
        else:atomic_json(receipt_path,receipt)
        transition={'path':str(receipt_path),'sha256':sha(receipt_path)}
        new_frozen=json.loads(canonical(old_meta['frozenInputs']));new_frozen['plannerSha256']=sha(__file__);new_frozen['semanticGates']['bridgeCarrierGate']=fresh_gate;new_frozen['semanticGates']['runtimeStorageTransition']=transition
        new_binding={**old_binding,'runtime':new_runtime,'runtimeStorageTransition':transition}
        payload=lambda value:(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
        # Every original is archived above. The intent makes all three swaps
        # recoverable before the one atomic SQLite metadata transaction.
        _rebind_replace(plan/'relight-binding.json',payload(new_binding),intent['archivedEvidence']['relight-binding.json'])
        _rebind_replace(plan/'relight-engine.json.done.tsv',cursor_bytes,intent['archivedEvidence']['relight-engine.json.done.tsv'])
        _rebind_replace(plan/'frozen-inputs.json',payload(new_frozen),intent['archivedEvidence']['frozen-inputs.json'])
        require(storage_plan_fingerprint(db)==intent['planFingerprint'],'Runtime transition changed the coordinate/map/light journals')
        meta(db,'runtime',new_runtime);meta(db,'bridgeCarrierGate',fresh_gate);meta(db,'runtimeStorageTransition',transition);meta(db,'frozenInputs',new_frozen);db.commit()
        atomic_json(completion,{'schemaVersion':1,'status':'PASS','transition':transition,'runtime':new_runtime,'preservedPlanFingerprint':intent['planFingerprint'],'adoptedSavedChunks':cursor_proof['processed'],'pendingChunks':cursor_proof['pending'],'mapWrites':0})
        progress(plan,db,'OFFLINE_APPLIED_RELIGHT_PENDING',runtimeStorageTransition=transition,adoptedSavedLightChunks=cursor_proof['processed'])
    finally:db.close()

def parser():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--plan',type=Path,default=DEFAULT_PLAN)
    sub=ap.add_subparsers(dest='command',required=True)
    p=sub.add_parser('prepare');p.add_argument('--source-plan',type=Path,default=DEFAULT_OLD);p.add_argument('--verify-source',action='store_true');p.set_defaults(run=prepare)
    p=sub.add_parser('export-poses');p.add_argument('--source-plan',type=Path,default=DEFAULT_OLD);p.add_argument('--verify-source',action='store_true');p.set_defaults(run=export_poses)
    p=sub.add_parser('classify');p.add_argument('--classification',type=Path,required=True);p.set_defaults(run=classify)
    p=sub.add_parser('import-fragments');p.add_argument('--fragments',type=Path,required=True);p.set_defaults(run=import_fragments)
    p=sub.add_parser('compose');p.add_argument('--dimension',default='.');p.add_argument('--bounds',type=int,nargs=4,metavar=('MIN_X','MAX_X','MIN_Z','MAX_Z'));p.add_argument('--additional-owners',type=Path);p.set_defaults(run=compose)
    p=sub.add_parser('retain-native-local');p.add_argument('--scope',type=Path,required=True);p.add_argument('--scope-sha256',required=True);p.add_argument('--preview',type=Path,required=True);p.add_argument('--execute',action='store_true');p.add_argument('--expected-preview-sha256');p.set_defaults(run=lambda a:native_resume.retain(a,module_api()))
    p=sub.add_parser('resume-native-compose');p.set_defaults(run=lambda a:native_resume.resume(a,module_api()))
    p=sub.add_parser('upgrade-native-resume-binding');p.add_argument('--failed-run',type=Path,required=True);p.add_argument('--source-proof',type=Path,required=True);p.add_argument('--source-proof-sha256',required=True);p.add_argument('--preview',type=Path,required=True);p.add_argument('--execute',action='store_true');p.add_argument('--expected-preview-sha256');p.set_defaults(run=lambda a:__import__('native_certificates').upgrade(a,module_api()))
    p=sub.add_parser('retain-native-certificate');p.add_argument('--certificate',type=Path,required=True);p.add_argument('--certificate-sha256',required=True);p.add_argument('--preview',type=Path,required=True);p.add_argument('--execute',action='store_true');p.add_argument('--expected-preview-sha256');p.set_defaults(run=lambda a:__import__('native_certificates').retain(a,module_api()))
    p=sub.add_parser('retain-native-retraction-certificate');p.add_argument('--certificate',type=Path,required=True);p.add_argument('--certificate-sha256',required=True);p.add_argument('--preview',type=Path,required=True);p.add_argument('--execute',action='store_true');p.add_argument('--expected-preview-sha256');p.set_defaults(run=lambda a:__import__('native_certificates').retract(a,module_api()))
    p=sub.add_parser('retain-preserved-native-whole-certificate');p.add_argument('--certificate',type=Path,required=True);p.add_argument('--certificate-sha256',required=True);p.add_argument('--preview',type=Path,required=True);p.add_argument('--execute',action='store_true');p.add_argument('--expected-preview-sha256');p.set_defaults(run=lambda a:__import__('native_certificates').retain_preserved_whole(a,module_api()))
    p=sub.add_parser('correct-native-visual-protection');p.add_argument('--certificate',type=Path,required=True);p.add_argument('--certificate-sha256',required=True);p.add_argument('--preview',type=Path,required=True);p.add_argument('--execute',action='store_true');p.add_argument('--expected-preview-sha256');p.set_defaults(run=lambda a:__import__('native_certificates').correct_visual_protection(a,module_api()))
    p=sub.add_parser('fallback-native-whole-template');p.add_argument('--certificate',type=Path,required=True);p.add_argument('--certificate-sha256',required=True);p.add_argument('--preview',type=Path,required=True);p.add_argument('--execute',action='store_true');p.add_argument('--expected-preview-sha256');p.set_defaults(run=lambda a:__import__('native_certificates').fallback_whole_template(a,module_api()))
    p=sub.add_parser('correct-retained-functional-visual-protection');p.add_argument('--certificate',type=Path,required=True);p.add_argument('--certificate-sha256',required=True);p.add_argument('--preview',type=Path,required=True);p.add_argument('--execute',action='store_true');p.add_argument('--expected-preview-sha256');p.set_defaults(run=lambda a:__import__('native_certificates').correct_retained_functional_visual(a,module_api()))
    p=sub.add_parser('import-types');p.add_argument('--mapping',type=Path,required=True);p.add_argument('--catalog',type=Path,required=True);p.set_defaults(run=import_types)
    p=sub.add_parser('apply');p.add_argument('--target',type=Path,required=True);p.add_argument('--runtime-jar',type=Path,required=True);p.add_argument('--pilot-gate',type=Path);p.set_defaults(run=apply)
    p=sub.add_parser('accept-relight');p.add_argument('--receipt',type=Path,required=True);p.set_defaults(run=accept_relight)
    p=sub.add_parser('clone');p.add_argument('--source-plan',type=Path,default=DEFAULT_OLD);p.add_argument('--target',type=Path,required=True);p.add_argument('--bounds',type=int,nargs=4);p.add_argument('--context-radius',type=int,default=7);p.add_argument('--extra-chunks',type=Path);p.set_defaults(run=clone)
    p=sub.add_parser('bind-pilot-copy');p.add_argument('--target',type=Path,required=True);p.add_argument('--harness-receipt',type=Path);p.set_defaults(run=bind_pilot_copy)
    p=sub.add_parser('status');p.set_defaults(run=status)
    p=sub.add_parser('audit-units');p.set_defaults(run=audit_units)
    p=sub.add_parser('promote-city');p.add_argument('--pilot-gate',type=Path,required=True);p.set_defaults(run=promote_city)
    p=sub.add_parser('pilot-native-requests');p.set_defaults(run=pilot_native_requests)
    p=sub.add_parser('normalize-substrates');p.set_defaults(run=normalize_substrates)
    p=sub.add_parser('payload-audit');p.set_defaults(run=payload_audit)
    p=sub.add_parser('resolve-payloads');p.set_defaults(run=resolve_payloads)
    p=sub.add_parser('native-visual-needs');p.set_defaults(run=native_visual_needs)
    p=sub.add_parser('door-request');p.set_defaults(run=door_request)
    p=sub.add_parser('import-door-footprints');p.add_argument('--footprints',type=Path,required=True);p.set_defaults(run=import_door_footprints)
    p=sub.add_parser('accept-bridge-carriers');p.add_argument('--receipt',type=Path,required=True);p.set_defaults(run=accept_bridge_carriers)
    p=sub.add_parser('correct-bridge-optics');p.add_argument('--receipt',type=Path,required=True);p.set_defaults(run=correct_bridge_optics)
    p=sub.add_parser('audit-cleanup-bes');p.set_defaults(run=audit_cleanup_bes)
    p=sub.add_parser('rebind-storage-runtime');p.add_argument('--old-runtime-jar',type=Path,required=True);p.add_argument('--runtime-jar',type=Path,required=True);p.add_argument('--receipt',type=Path,required=True);p.add_argument('--source-receipt',type=Path,default=ROOT/'research'/'pending-lithium-storage'/'source-receipt.json');p.add_argument('--helper-jar',type=Path);p.set_defaults(run=rebind_storage_runtime)
    p=sub.add_parser('context-extension-inventory');p.set_defaults(run=lambda a:context_stage.inventory(a,module_api()))
    p=sub.add_parser('stage-context-extension');p.add_argument('--inventory',type=Path,required=True);p.set_defaults(run=lambda a:context_stage.stage(a,module_api()))
    p=sub.add_parser('import-context-native-functions');p.add_argument('--report',type=Path,required=True);p.set_defaults(run=lambda a:context_stage.import_functions(a,module_api()))
    p=sub.add_parser('import-full-native-functions');p.add_argument('--report',type=Path,required=True);p.set_defaults(run=lambda a:context_stage.import_full_functions(a,module_api()))
    p=sub.add_parser('bind-context-copy');p.add_argument('--target',type=Path,required=True);p.set_defaults(run=lambda a:context_stage.bind_copy(a,module_api()))
    return ap

if __name__=='__main__':
    args=parser().parse_args();start=time.monotonic()
    try:
        control_check();args.run(args);atomic_json(checked(args.plan)/'last-invocation.json',{'status':'PASS','command':args.command,'seconds':time.monotonic()-start})
    except BaseException:
        atomic_json(checked(args.plan)/'last-invocation.json',{'status':'FAIL','command':args.command,'seconds':time.monotonic()-start,'error':traceback.format_exc()});raise
