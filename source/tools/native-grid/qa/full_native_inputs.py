"""Read-only FULL_CITY native-function input census from existing saved data.

Run through Guard Operation only after the pilot gate. This is an input proof,
not a planner, migration hook, function decision, or permission to apply. Only
SQLite TEMP tables and the explicitly named output are written. No world files,
discovery, Minecraft, builds, or persistent coordinate changes are used.
"""
from __future__ import annotations
import argparse, collections, gzip, hashlib, json, os, sqlite3, sys, time, zlib
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(TOOLS))
from world_io import _Reader, _read_payload, compound, section_blocks


def require(ok, message):
    if not ok: raise RuntimeError(message)


def canonical(value): return json.dumps(value, sort_keys=True, separators=(',', ':'))
def read(path): return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b''): digest.update(chunk)
    return digest.hexdigest()


def readonly(path):
    require(Path(path).is_file(), 'Missing existing database: ' + str(path))
    db = sqlite3.connect('file:' + Path(path).resolve().as_posix() + '?mode=ro', uri=True)
    db.row_factory = sqlite3.Row
    return db


def advance(reader, count):
    require(count >= 0 and reader.pos + count <= len(reader.data), 'Invalid saved NBT length')
    reader.pos += count


def skip(reader, kind, depth=0):
    """Skip BE/other trees without materializing their owner arrays."""
    require(depth < 128, 'Saved NBT depth exceeds the inspection bound')
    fixed = {1:1, 2:2, 3:4, 4:8, 5:4, 6:8}
    if kind in fixed: advance(reader, fixed[kind])
    elif kind == 8: advance(reader, reader.unpack('>H'))
    elif kind in (7, 11, 12): advance(reader, reader.unpack('>i') * {7:1, 11:4, 12:8}[kind])
    elif kind == 9:
        child = reader.unpack('>B'); count = reader.unpack('>i')
        require(count >= 0, 'Negative saved list length')
        if child in fixed: advance(reader, count * fixed[child])
        else:
            for _ in range(count): skip(reader, child, depth + 1)
    elif kind == 10:
        while True:
            child = reader.unpack('>B')
            if child == 0: break
            advance(reader, reader.unpack('>H')); skip(reader, child, depth + 1)
    else: raise RuntimeError('Unsupported saved NBT tag ' + str(kind))


def saved_sections(payload, compression):
    require(compression in (1, 2, 3), 'Unsupported cached chunk compression')
    raw = {1:gzip.decompress, 2:zlib.decompress, 3:lambda value:value}[compression](payload)
    reader = _Reader(raw)
    require(reader.unpack('>B') == 10, 'Expected a saved chunk compound'); reader.string()
    while True:
        kind = reader.unpack('>B')
        if kind == 0: return []
        name = reader.string()
        if name == 'sections':
            require(kind == 9, 'Saved sections must be an NBT list')
            return _read_payload(reader, kind).value
        skip(reader, kind)


def state(tag):
    fields = compound(tag); properties = fields.get('Properties')
    return {'registryId':fields['Name'].value,
            'properties':{key:value.value for key,value in sorted((properties.value if properties else {}).items())}}


def add(groups, value, dimension, point, capability, role, limit):
    key = canonical(value)
    row = groups.setdefault(key, {'state':value, 'cells':0, 'witnesses':[],
        'className':capability.get('className'), 'sourceCapabilities':capability or None,
        'role':role})
    row['cells'] += 1
    if len(row['witnesses']) < limit:
        row['witnesses'].append({'dimension':dimension, 'position':list(point)})


def run(args):
    started = time.monotonic(); plan = args.plan.resolve()
    database = plan if plan.is_file() else plan / 'plan.sqlite'
    output = args.out.resolve(); require(not output.exists(), 'Preserve existing input evidence; choose a fresh --out')
    db = readonly(database); old = None
    try:
        # TEMP may spill to SQLite's temporary storage; no persistent DB is writable.
        db.execute('PRAGMA temp_store=FILE'); db.execute('BEGIN')
        binding = json.loads(db.execute("SELECT value FROM meta WHERE key='binding'").fetchone()[0])
        require(binding.get('sourceHashesVerified') is True and binding.get('originalPlanReadOnly') is True,
                'The global discovery must retain its verified immutable source binding')
        manifest = Path(binding['copyManifest'])
        require(sha(manifest) == binding['copyManifestSha256'], 'Source copy manifest changed')
        old_plan = Path(binding['oldPlan']); old = readonly(old_plan / 'plan.sqlite'); old.execute('BEGIN')
        capabilities_path = args.capabilities or old_plan.parents[2] / 'tools/ordinary-stage1/source-capabilities.json'
        capabilities_document = read(capabilities_path)
        capabilities = {row['registryId']:row for row in capabilities_document['states']}
        require(len(capabilities) == len(capabilities_document['states']), 'Duplicate source capability registry IDs')
        db.execute('''CREATE TEMP TABLE candidates AS
            SELECT dim,uuid,pose,x,y,z FROM objects WHERE kind IN ('static','whole')''')
        db.execute('CREATE UNIQUE INDEX temp.candidate_id ON candidates(dim,uuid)')
        objects = db.execute('SELECT count(*) FROM candidates').fetchone()[0]
        require(objects > 0, 'No global static/whole objects')
        missing_poses = db.execute('''SELECT count(*) FROM candidates c LEFT JOIN poses p ON p.key=c.pose
            WHERE p.key IS NULL''').fetchone()[0]
        require(missing_poses == 0, 'Some global candidate owners lack their saved SourcePose')
        db.execute('CREATE TEMP TABLE refs(dim TEXT,x INTEGER,y INTEGER,z INTEGER,PRIMARY KEY(dim,x,y,z))')
        db.execute('INSERT OR IGNORE INTO refs SELECT dim,x,y,z FROM candidates')
        db.execute('''INSERT OR IGNORE INTO refs SELECT c.dim,c.x+p.dx,c.y+p.dy,c.z+p.dz
            FROM candidates c JOIN pose_cells p ON p.pose=c.pose''')
        db.execute('CREATE INDEX temp.ref_chunk ON refs(dim,(x>>4),(z>>4))')
        db.execute('''CREATE TEMP TABLE source_roots AS SELECT DISTINCT c.dim,c.x,c.y,c.z,
            json_extract(p.data,'$.registryId') AS registry_id
            FROM candidates c JOIN poses p ON p.key=c.pose''')
        db.execute('CREATE INDEX temp.source_root_pos ON source_roots(dim,x,y,z)')
        owner_digest = hashlib.sha256()
        for row in db.execute('SELECT * FROM candidates ORDER BY dim,uuid'):
            owner_digest.update((canonical(list(row)) + '\n').encode())
        chunks = db.execute('SELECT DISTINCT dim,x>>4 AS cx,z>>4 AS cz FROM refs ORDER BY dim,cx,cz').fetchall()
        bases = {}; hosts = {}; source_carriers = {}; cache_proofs = []; missing_chunks = []
        ref_digest = hashlib.sha256(); examined = 0
        for dimension, cx, cz in chunks:
            cached = old.execute('''SELECT region_path,region_hash,compression,payload FROM chunk_cache
                WHERE dim=? AND x=? AND z=?''', (dimension,cx,cz)).fetchone()
            if cached is None:
                missing_chunks.append({'dimension':dimension,'chunk':[cx,cz]}); continue
            sections = {}
            for section in saved_sections(cached['payload'], cached['compression']):
                blocks = section_blocks(section)
                if blocks is None: continue
                palette, values = blocks
                sections[compound(section)['Y'].value] = ([state(item) for item in palette], values)
            chunk_refs = 0
            rows = db.execute('''SELECT r.x,r.y,r.z,group_concat(DISTINCT s.registry_id) AS root_ids
                FROM refs r LEFT JOIN source_roots s ON s.dim=r.dim AND s.x=r.x AND s.y=r.y AND s.z=r.z
                WHERE r.dim=? AND r.x>>4=? AND r.z>>4=? GROUP BY r.x,r.y,r.z ORDER BY r.x,r.y,r.z''',
                (dimension,cx,cz))
            for x,y,z,root_ids in rows:
                point = (x,y,z); section = sections.get(y>>4)
                value = {'registryId':'minecraft:air','properties':{}} if section is None else section[0][section[1][(x&15)+16*(z&15)+256*(y&15)]]
                capability = capabilities.get(value['registryId'], {})
                # This is a SourcePose/class separation only, NOT an ownership,
                # cleanup, logical-AIR, or permission-to-replace proof.
                source_carrier = (value['registryId'] in (root_ids or '').split(',')
                    and capability.get('compositeRoot') is True and value['properties'].get('compact') != 'true')
                be_host = (not source_carrier and capability.get('anyStateHasBlockEntity') is True
                    and value['properties'].get('compact') != 'true')
                role = 'same_root_source_pose_carrier_inspection_only' if source_carrier else 'existing_be_host_requires_exact_proof' if be_host else 'native_base_requires_function_decision'
                target = source_carriers if source_carrier else hosts if be_host else bases
                # In particular, actual StaticArchitecture states are included;
                # no static_architecture registry-prefix exclusion is used.
                add(target,value,dimension,point,capability,role,args.witness_limit)
                ref_digest.update((canonical([dimension,x,y,z,value,role])+'\n').encode())
                chunk_refs += 1; examined += 1
            cache_proofs.append({'dimension':dimension,'chunk':[cx,cz], 'region':cached['region_path'],
                'boundRegionSha256':cached['region_hash'],'compression':cached['compression'],
                'cachedCompressedPayloadBytes':len(cached['payload']),
                'cachedCompressedPayloadSha256':hashlib.sha256(cached['payload']).hexdigest(),
                'candidateCells':chunk_refs})
        ref_count = db.execute('SELECT count(*) FROM refs').fetchone()[0]
        require(not missing_chunks, 'Saved discovery cache lacks candidate chunks; no world fallback allowed: '+canonical(missing_chunks[:32]))
        require(examined == ref_count, 'Full candidate-cell coverage is incomplete')
        rows = lambda group: sorted(group.values(),key=lambda row:canonical(row['state']))
        report = {'schemaVersion':1, 'status':'READ_ONLY_NATIVE_BASE_INPUT_EVIDENCE_NOT_APPLY_GATE',
            'scope':'FULL_CITY', 'sourceBinding':binding,
            'coverage':{'candidateObjects':objects, 'candidateObjectsSha256':owner_digest.hexdigest(),
                'candidateCells':ref_count,'candidateCellsInspected':examined,'candidateCellStatesSha256':ref_digest.hexdigest(),
                'cachedChunksRequired':len(chunks),'cachedChunksInspected':len(cache_proofs),'missingCachedChunks':[]},
            'nativeBaseCandidates':rows(bases), 'blockEntityHostCandidates':rows(hosts),
            'sameRootSourcePoseCarriers':rows(source_carriers),
            'sourceCapabilities':{'path':str(Path(capabilities_path).resolve()),'sha256':sha(capabilities_path),
                'runtimeVersion':capabilities_document.get('runtimeVersion'),'records':len(capabilities)},
            'cachedChunkProofs':cache_proofs,'inputGenerator':{'path':str(Path(__file__).resolve()),'sha256':sha(__file__),
                'worldIoSha256':sha(TOOLS/'world_io.py')},
            'worldFilesRead':0,'worldsOpened':0,'mapWrites':0,'persistentDatabaseWrites':0,'gameProcessesStarted':0,
            'limits':'Initial global static/whole root+pose_cells union only. SourcePose exclusions are inspection-only. BE hosts require exact existing typed BE/retained ownership proof. Additional native activation must receive another finite coverage gate; this report makes no function decisions or apply authorization.',
            'seconds':time.monotonic()-started}
        output.parent.mkdir(parents=True,exist_ok=True); temporary = output.with_name(output.name+'.writing')
        with temporary.open('w',encoding='utf-8') as stream:
            json.dump(report,stream,ensure_ascii=False,indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())
        os.replace(temporary,output)
        print(canonical({'status':report['status'],'scope':'FULL_CITY','candidateObjects':objects,
            'nativeStates':len(bases),'beHostStates':len(hosts),'out':str(output),'sha256':sha(output)}))
    finally:
        if old is not None: old.close()
        db.close()


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument('--plan',type=Path,required=True,help='Existing global plan directory or plan.sqlite')
    result.add_argument('--out',type=Path,required=True,help='Fresh machine-readable input evidence file')
    result.add_argument('--capabilities',type=Path,help='Optional exact saved source-capabilities.json path')
    result.add_argument('--witness-limit',type=int,default=12)
    return result


if __name__ == '__main__':
    arguments = parser().parse_args()
    require(1 <= arguments.witness_limit <= 32,'Witness limit must be in 1..32')
    run(arguments)
