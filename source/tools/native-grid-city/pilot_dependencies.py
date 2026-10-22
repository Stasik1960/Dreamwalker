"""One explicit finite dependency repair of the same two cropped pilot maps.

Audit/staging never changes worlds or persistent SQLite. Only root executes the
reviewed file plan. Original427 remains the migration selection domain.
"""
import argparse,collections,copy,hashlib,json,os,shutil,time,traceback
from pathlib import Path
import migrate as m
import context_stage as c

PLAN=m.ROOT/'research/native-grid-plan'
REPAIR=m.ROOT/'research/pilot/dependency-repair'
INVENTORY=PLAN/'context-extension/boundary-dependency-inventory-v3.json'
INVENTORY_SHA='9f0e46154d4cd1be4d90d0d4e99cc9f9e376c077ee792686e52fccdf36031ebe'
GLOBAL='data/bloodborne_dw_composite_owners.dat'

def typed_sha(tag):return m.digest(c._typed(m,tag))
def chunk_decode(compression,payload):return m.decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression])
def positions(tags):return {tuple(m.compound(t)[k].value for k in ('x','y','z')):t for t in tags}
def be_tags(root):return root.get('block_entities',m.Tag(9,[],10)).value
def comparison(state):return c._comparison_state(m,state)
def exact_payload_equivalence(db,variant,cx,cz,compression,payload,cached):
    if compression!=cached[2] or payload!=cached[3]:return False
    if variant=='baseline':return True
    # This archived source_chunks union includes roots, visual placements,
    # old stored contributions and old member cleanup footprints. Absence is
    # deliberately stronger than merely having no destination placements.
    return (not db.execute("SELECT 1 FROM stage1_source_chunks WHERE dim='.' AND cx=? AND cz=?",(cx,cz)).fetchone()
        and not db.execute("SELECT 1 FROM stage1_placements WHERE dim='.' AND cx=? AND cz=?",(cx,cz)).fetchone())
def outer(tag):
    fields=dict(m.compound(tag));fields.pop('owners',None);fields.pop('OwnerDataV1',None);fields.pop('keepPacked',None)
    return fields
def has_placement(db,at):
    return any(db.execute('SELECT 1 FROM '+table+' WHERE dim=\'.\' AND x=? AND y=? AND z=? LIMIT 1',at).fetchone() for table in ('placements','stage1_placements'))

def prove_loss(db,expected,actual,expected_state,actual_state,allowed_owners):
    e=m.compound(expected);at=tuple(e[k].value for k in ('x','y','z'))
    m.require(not has_placement(db,at),'A native/helper loss touches a current or committed placement')
    m.require(e.get('id',m.Tag(8,'')).value=='bloodborne_dw:composite_cell','Unexpected foreign/game BE changed')
    before=m.be_owners(e);after=m.be_owners(m.compound(actual)) if actual else []
    before_counts=collections.Counter(m.canonical(c._typed(m,t)) for t in before);after_counts=collections.Counter(m.canonical(c._typed(m,t)) for t in after)
    m.require(not(after_counts-before_counts),'Native helper changed or introduced an owner payload')
    lost=[];remaining=after_counts.copy()
    for tag in before:
        key=m.canonical(c._typed(m,tag))
        if remaining[key]:remaining[key]-=1;continue
        owner=m.compound(m.compound(tag)['owner']);uid=m.owner_uuid(m.compound(tag)['owner']);proof=allowed_owners.get(uid)
        m.require(proof is not None and tuple(owner['root'].value)==tuple(proof['root']) and owner['id'].value==proof['registryId'],'Lost owner is not an exact authorized outside-root dependency')
        m.require(not db.execute('SELECT 1 FROM selected_objects WHERE dim=\'.\' AND uuid=?',(uid,)).fetchone() and not db.execute('SELECT 1 FROM stage1_selected_objects WHERE dim=\'.\' AND uuid=?',(uid,)).fetchone(),'A selected owner was lost')
        lost.append(uid)
    m.require(lost,'BE changed without a proven removed outside-root owner')
    if actual is None:
        fields=outer(expected)
        m.require(set(fields)<= {'id','x','y','z','payload'} and ('payload' not in fields or fields['payload'].type==10 and not fields['payload'].value),'Disappeared helper had independent resident/payload/base/unknown data')
        m.require(expected_state=={'registryId':'bloodborne_dw:composite_cell','properties':{}} and actual_state==m.AIR,'Disappeared helper is not the exact CompositeCell to AIR transition')
    else:
        stripped=m.Tag(10,dict(outer(expected),owners=m.Tag(9,after,10)))
        m.require(c._be_value(m,stripped)==c._be_value(m,actual),'Native prune changed another typed BE field or payload')
        m.require(comparison(expected_state)==comparison(actual_state),'Partially pruned helper changed its carrier state')
    return {'position':list(at),'lostOwners':sorted(lost),'expectedState':expected_state,'actualState':actual_state,'expectedBlockEntity':c._typed(m,expected),'expectedBlockEntitySha256':typed_sha(expected),'actualBlockEntitySha256':typed_sha(actual) if actual else None,'noCurrentOrStage1Placement':True,'unknownOtherFieldsChanged':False}

def audit_chunk(db,variant,cx,cz,source,actual,outside,native_restores=()):
    if variant=='candidate':changes,tags=c._content(m,db,'.',cx,cz,source,'stage1_')
    else:changes={};tags=be_tags(source)
    expected_sections=c._sections(m,source);actual_sections=c._sections(m,actual)
    expected_states=c._states_digest(m,expected_sections,changes);actual_states=c._states_digest(m,actual_sections)
    expected_bes=c._bes_digest(m,tags);actual_bes=c._bes_digest(m,be_tags(actual));losses=[];state_repairs={};actual_tags=positions(be_tags(actual));expected_tags=positions(tags);repaired_tags=dict(actual_tags)
    if expected_bes!=actual_bes:
        for at in sorted(set(expected_tags)|set(actual_tags)):
            e=expected_tags.get(at);a=actual_tags.get(at)
            if e is not None and a is not None and c._be_value(m,e)==c._be_value(m,a):continue
            m.require(e is not None,'Actual target introduced a BE absent from exact source/stage1')
            expected_state=changes.get(at,m.source_state(expected_sections,at));actual_state=m.source_state(actual_sections,at)
            loss=prove_loss(db,e,a,expected_state,actual_state,outside);losses.append(loss);repaired_tags[at]=e;state_repairs[at]=expected_state
    for item in native_restores:
        at=tuple(item['position']);m.require((at[0]>>4,at[2]>>4)==(cx,cz),'Finite native restoration crosses its addressed chunk')
        expected_state=changes.get(at,m.source_state(expected_sections,at));actual_state=m.source_state(actual_sections,at)
        m.require(comparison(expected_state)==comparison(item['expected']) and comparison(actual_state)==comparison(item['actual']),'Exact finite native state pair changed')
        m.require(expected_state['registryId'] in c._comparison_ids(m) and at not in expected_tags and at not in actual_tags and not has_placement(db,at),'Finite native restoration touches a BE/placement/unknown native class')
        state_repairs[at]=expected_state;losses.append({'kind':'RESTORE_EXACT_OLD_STATIC_COMPONENT','position':list(at),'lostOwners':[],'expectedState':expected_state,'actualState':actual_state,'expectedBlockEntity':None,'expectedBlockEntitySha256':None,'actualBlockEntitySha256':None,'componentKeys':[row['componentKey'] for row in item['removedComponents']],'noCurrentOrStage1Placement':True})
    m.require(c._states_digest(m,actual_sections,state_repairs)==expected_states,'Other unexplained block-state changes exist in original427: '+str((variant,cx,cz)))
    m.require(c._bes_digest(m,list(repaired_tags.values()))==expected_bes,'Other unexplained BE changes exist in original427: '+str((variant,cx,cz)))
    return {'chunk':[cx,cz],'expectedStatesSha256':expected_states,'actualStatesSha256':actual_states,'expectedBlockEntitiesSha256':expected_bes,'actualBlockEntitiesSha256':actual_bes,'losses':losses}

class PriorCleanup:
    def __init__(self,db):self.db=db
    def execute(self,sql,args=()):return self.db.execute(sql.replace('FROM cleanup_owners','FROM stage1_cleanup_owners'),args)

def original_shards():
    bootstrap=m.read(m.ROOT/'research/pilot/bootstrap-repair/plan.json')
    return bootstrap['worlds']['candidate']['ledger']['shards']

def empty_shard(nbt,cx,cz):
    root=m.compound(nbt.root);m.require(set(root)<= {'data','DataVersion'} or 'data' not in root,'Generated shard has unknown wrapper fields');data=m.compound(root['data']) if 'data' in root else root
    m.require(set(data)<= {'Dictionary','Schema','ChunkX','ChunkZ','ownerTable','dataTable','shapeTable','cells','ownerMembership'} and data.get('Dictionary',m.Tag(3,0)).value==1,'Generated shard has unknown schema/fields')
    m.require(data.get('Schema',m.Tag(3,1)).value==1 and data.get('ChunkX',m.Tag(3,cx)).value==cx and data.get('ChunkZ',m.Tag(3,cz)).value==cz,'Generated shard coordinates/schema differ')
    m.require(all(not data.get(key,m.Tag(9,[],10)).value for key in ('ownerTable','dataTable','shapeTable','cells','ownerMembership')),'Generated shard contains existing owner/game data')
    return {'proof':'EXACT_EMPTY_SCHEMA1_GENERATED_SHARD','typedNbtSha256':typed_sha(nbt.root),'chunk':[cx,cz]}

def audit_ledgers(db,variant,target,binding,manifest):
    old_files={p:h for p,h in db.execute("SELECT path,after_hash FROM stage1_files WHERE phase='committed'")};source=Path(binding['source']);shards=original_shards();proof={};unexpected=[]
    for rel,source_hash in sorted(shards.items()):
        path=target/rel;actual_hash=m.sha(path);expected_hash=old_files.get(rel,source_hash) if variant=='candidate' else source_hash
        if actual_hash!=expected_hash:
            nbt=m.read_nbt(source/rel)
            if variant=='candidate':m.cleanup_dictionary(PriorCleanup(db),'.',nbt)
            actual=m.read_nbt(path)
            if typed_sha(actual.root)!=typed_sha(nbt.root):unexpected.append({'path':rel,'expectedHash':expected_hash,'actualHash':actual_hash,'expectedTypedSha256':typed_sha(nbt.root),'actualTypedSha256':typed_sha(actual.root)})
        proof[rel]={'sourceSha256':source_hash,'beforeSha256':actual_hash,'expectedCommittedSha256':expected_hash,'preserveBytes':True}
    global_nbt=m.read_nbt(source/GLOBAL);crop=m.crop_pilot_global_index(global_nbt,shards)
    if variant=='candidate':m.cleanup_global(PriorCleanup(db),'.',global_nbt)
    actual_global=m.read_nbt(target/GLOBAL);expected_fields=m.compound(global_nbt.root);actual_fields=m.compound(actual_global.root);expected_fields=m.compound(expected_fields['data']) if 'data'in expected_fields else expected_fields;actual_fields=m.compound(actual_fields['data']) if 'data'in actual_fields else actual_fields
    expected_index=set(expected_fields['OwnerShardChunks'].value);actual_index=actual_fields['OwnerShardChunks'].value;m.require(len(set(actual_index))==len(actual_index) and expected_index<=set(actual_index),'Global ledger lost/repeated original shard indexes')
    allowed_aux={(r['x'],r['z']) for r in m.read(INVENTORY)['chunks']};extras=[]
    for key in sorted(set(actual_index)-expected_index):
        cx=key&0xffffffff;cz=(key>>32)&0xffffffff;cx=cx if cx<1<<31 else cx-(1<<32);cz=cz if cz<1<<31 else cz-(1<<32)
        m.require((cx,cz) in allowed_aux,'Unexpected generated shard index outside approved dependency88');path=target/('data/bloodborne_dw_owner_shards/owners-%d.%d.nbt'%(cx,cz));extras.append({'path':str(path),'sha256':m.sha(path),**empty_shard(m.read_nbt(path),cx,cz)})
    saved=actual_fields['OwnerShardChunks'];actual_fields['OwnerShardChunks']=expected_fields['OwnerShardChunks'];m.require(typed_sha(actual_global.root)==typed_sha(global_nbt.root),'Global ledger changed a field other than known empty auxiliary indexes');actual_fields['OwnerShardChunks']=saved
    return {'shards':proof,'unexpectedShardChanges':unexpected,'global':{'path':GLOBAL,'beforeSha256':m.sha(target/GLOBAL),'typedSha256':typed_sha(actual_global.root),'sourceSha256':manifest['files'][GLOBAL]['sha256'],'originalIndex':crop,'extraGeneratedEmptyIndexes':extras,'allOtherFieldsMatchExactSourceCrop':True}}

def audit_original():
    REPAIR.mkdir(parents=True,exist_ok=True);output=REPAIR/'audit.json';m.require(not output.exists(),'The one original427 audit already exists; preserve it')
    shutil.copyfile(__file__,REPAIR/'audit-source.py');db=c.attached_readonly(m,PLAN);started=time.monotonic();worlds={};errors=[];checkpoint=REPAIR/'audit-chunks.jsonl';prior={}
    if checkpoint.exists():
        for line in checkpoint.read_text(encoding='utf-8').splitlines():
            row=json.loads(line);prior[(row['variant'],*row['proof']['chunk'])]=row
    def record(variant,rel,before,proof):
        with checkpoint.open('a',encoding='utf-8',newline='\n') as stream:stream.write(m.canonical({'variant':variant,'region':rel,'regionSha256':before,'inventorySha256':INVENTORY_SHA,'proof':proof})+'\n');stream.flush();os.fsync(stream.fileno())
    def progress(variant,verified):m.atomic_json(REPAIR/'audit-progress.json',{'status':'RUNNING','variant':variant,'verifiedChunks':verified,'seconds':time.monotonic()-started,'mapWrites':0,'databaseWrites':0})
    try:
        m.require(m.sha(INVENTORY)==INVENTORY_SHA,'Reviewed finite dependency inventory changed');inventory=m.read(INVENTORY);binding=m.meta(db,'binding');m.require(inventory['sourceBinding']==binding,'Finite dependency input source changed')
        outside={r['uuid']:r for r in inventory['owners'] if r['rootOutsideCopy']};m.require(len(outside)==960,'Finite outside-root owner set changed');allowed=set(db.execute('SELECT cx,cz FROM context_allowed_chunks'));m.require(len(allowed)==427,'Original427 domain changed');groups=collections.defaultdict(set)
        for cx,cz in allowed:groups['region/r.%d.%d.mca'%(cx>>5,cz>>5)].add((cx,cz))
        manifest=m.read(binding['copyManifest'])
        for variant in ('baseline','candidate'):
            target=m.ROOT/'research/pilot'/variant;regions={};chunks=[]
            for rel,wanted in sorted(groups.items()):
                path=target/rel;before=m.sha(path);regions[rel]=before
                for cx,cz,compression,payload in c._region_chunks(m,path,wanted):
                    old=prior.get((variant,cx,cz))
                    if old:
                        m.require(old['region']==rel and old['regionSha256']==before and old['inventorySha256']==INVENTORY_SHA,'Durable audit checkpoint input changed');chunks.append(old['proof']);continue
                    cached=db.execute('SELECT region_path,region_hash,compression,payload FROM old.chunk_cache WHERE dim=\'.\' AND x=? AND z=?',(cx,cz)).fetchone()
                    if compression is None:
                        if cached:errors.append({'variant':variant,'chunk':[cx,cz],'error':'Original cached chunk disappeared'})
                        continue
                    if not cached:errors.append({'variant':variant,'chunk':[cx,cz],'error':'Generated chunk occupies originally absent source slot'});continue
                    try:
                        m.require(manifest['files'][cached[0]]['sha256']==cached[1],'Cached payload source snapshot changed')
                        if exact_payload_equivalence(db,variant,cx,cz,compression,payload,cached):
                            proof={'chunk':[cx,cz],'proof':'EXACT_COMPRESSION_AND_COMPRESSED_PAYLOAD_BYTES','sourceRegion':cached[0],'sourceRegionSha256':cached[1],'compressedPayloadSha256':hashlib.sha256(payload).hexdigest(),'noStage1SourceCleanupFootprint':variant=='candidate','losses':[]};chunks.append(proof);record(variant,rel,before,proof);progress(variant,len(chunks));continue
                        source=m.compound(chunk_decode(cached[2],cached[3]).root);actual=m.compound(chunk_decode(compression,payload).root)
                        m.require((actual['xPos'].value,actual['zPos'].value)==(cx,cz),'Target chunk coordinates differ')
                        proof=audit_chunk(db,variant,cx,cz,source,actual,outside);chunks.append(proof);record(variant,rel,before,proof)
                    except Exception as error:errors.append({'variant':variant,'chunk':[cx,cz],'error':str(error)})
                    progress(variant,len(chunks))
                m.require(m.sha(path)==before,'Target region changed during offline audit')
            ledgers=audit_ledgers(db,variant,target,binding,manifest);errors.extend({'variant':variant,'error':'Unexpected existing shard bytes/typed fields','detail':row} for row in ledgers['unexpectedShardChanges'])
            worlds[variant]={'target':str(target),'regions':regions,'chunks':chunks,'losses':[dict(row,chunk=item['chunk']) for item in chunks for row in item['losses']],'ledgers':ledgers}
        report={'schemaVersion':1,'status':'PASS' if not errors else 'BLOCKED_UNEXPECTED_INPUTS','kind':'ORIGINAL427_FINITE_OUTSIDE_ROOT_NATIVE_PRUNE_AUDIT','sourceBinding':binding,'inventory':{'path':str(INVENTORY),'sha256':INVENTORY_SHA},'auditSource':{'path':str(REPAIR/'audit-source.py'),'sha256':m.sha(REPAIR/'audit-source.py')},'worlds':worlds,'errors':errors,'outsideRootOwners':960,'originalDomain':427,'mapWrites':0,'databaseWrites':0,'seconds':time.monotonic()-started}
        m.atomic_json(output,report);print(m.canonical({'status':report['status'],'losses':{v:len(w['losses']) for v,w in worlds.items()},'errors':errors[:12],'seconds':report['seconds']}))
    finally:db.close()

def read_slot(path,cx,cz):
    if not path.is_file() or path.stat().st_size==0:return None
    with path.open('rb') as stream:
        header=stream.read(8192);m.require(len(header)==8192,'Short nonempty region header: '+str(path));index=(cx&31)+32*(cz&31)
        value=int.from_bytes(header[index*4:index*4+4],'big');sector,count=value>>8,value&255
        if not sector:return None
        m.require(sector>=2 and count>0,'Invalid allocated auxiliary slot');stream.seek(sector*4096);size=int.from_bytes(stream.read(4),'big');compression=stream.read(1)[0]
        m.require(1<=size<=count*4096-4 and compression in (1,2,3),'Invalid auxiliary inline record');payload=stream.read(size-1);m.require(len(payload)==size-1,'Truncated auxiliary record')
        return compression,payload,int.from_bytes(header[4096+index*4:4100+index*4],'big')

def flat_before(record):
    if record is None:return {'proof':'ABSENT_SLOT'}
    nbt=chunk_decode(record[0],record[1]);root=m.compound(nbt.root)
    allowed={'DataVersion','xPos','yPos','zPos','Status','LastUpdate','InhabitedTime','sections','block_entities','block_ticks','fluid_ticks','Heightmaps','isLightOn','structures','PostProcessing','CarvingMasks','Lights','blending_data','below_zero_retrogen','UpgradeData','entities','Entities'}
    m.require(set(root)<=allowed,'Auxiliary terrain contains unknown independent fields')
    for key in ('block_entities','block_ticks','fluid_ticks','entities','Entities'):
        m.require(key not in root or not root[key].value,'Auxiliary terrain contains BE/entity/tick game data')
    structures=m.compound(root.get('structures',m.Tag(10,{})))
    m.require(all(not value.value for value in structures.values()),'Auxiliary terrain has structures/references requiring explicit review')
    for key in ('blending_data','below_zero_retrogen','UpgradeData'):
        m.require(key not in root or not root[key].value,'Auxiliary terrain contains nonempty generation upgrade data')
    if 'CarvingMasks' in root:
        masks=m.compound(root['CarvingMasks']);m.require(set(masks)<= {'AIR','LIQUID'} and all(tag.type==12 and len(tag.value)==0 for tag in masks.values()),'Auxiliary carving mask has nonempty/unknown/wrongly typed data')
    sections=c._sections(m,root);full=root.get('Status',m.Tag(8,'')).value in ('minecraft:full','full');all_air=True
    for sy,(palette,values) in sections.items():
        names=[m.state_json(t) for t in palette]
        for i,p in enumerate(values):
            value=names[p];y=sy*16+(i>>8)
            expected={'registryId':'minecraft:bedrock' if y==-64 else 'minecraft:dirt' if y in (-63,-62) else 'minecraft:grass_block' if y==-61 else 'minecraft:air','properties':{'snowy':'false'} if y==-61 else {}}
            if value!=m.AIR:all_air=False
            m.require(value==expected or not full and value==m.AIR,'Auxiliary terrain is not exact unmodified flat generation: '+m.canonical(value))
    m.require(not full or -4 in sections,'Full auxiliary chunk is missing its flat ground section')
    return {'proof':'EXACT_VANILLA_FLAT_GENERATION_NO_GAME_DATA','typedNbtSha256':typed_sha(nbt.root),'status':root.get('Status',m.Tag(8,'')).value,'allAirProto':all_air and not full,'blockEntities':0,'scheduledTicks':0,'structures':0}

def empty_layer_before(record,layer):
    if record is None:return {'proof':'ABSENT_SLOT'}
    root=m.compound(chunk_decode(record[0],record[1]).root)
    if layer=='entities':
        m.require(set(root)<= {'DataVersion','Entities','Position'} and not root.get('Entities',m.Tag(9,[],10)).value,'Auxiliary entity slot has independent data')
    else:
        m.require(set(root)<= {'DataVersion','Sections'},'Auxiliary POI slot has unknown data')
        for section in m.compound(root.get('Sections',m.Tag(10,{}))).values():
            fields=m.compound(section);m.require(set(fields)<= {'Valid','Records'} and not fields.get('Records',m.Tag(9,[],10)).value,'Auxiliary POI slot has records/unknown data')
    return {'proof':'EMPTY_NATIVE_'+layer.upper(),'typedNbtSha256':typed_sha(m.Tag(10,root))}

def stage_region(path,output,replacements):
    """Bounded records; exact payload/timestamp pass-through for every other slot."""
    output.parent.mkdir(parents=True,exist_ok=True);header=bytearray(8192);untouched=[];changed=[]
    with output.open('wb') as target:
        target.write(header)
        for index in range(1024):
            cx=index&31;cz=index>>5;old=read_slot(path,cx,cz);new=replacements.get(index,old)
            if index in replacements:changed.append(index)
            elif old is not None:untouched.append([index,old[0],hashlib.sha256(old[1]).hexdigest(),old[2]])
            if new is None:continue
            compression,payload,timestamp=new;size=len(payload)+1;record=size.to_bytes(4,'big')+bytes([compression])+payload;sectors=(len(record)+4095)//4096;offset=target.tell()//4096
            m.require(sectors<256 and offset<1<<24,'Region record exceeds inline storage budget')
            header[index*4:index*4+4]=((offset<<8)|sectors).to_bytes(4,'big');header[4096+index*4:4100+index*4]=timestamp.to_bytes(4,'big');target.write(record);target.write(bytes(sectors*4096-len(record)))
        target.seek(0);target.write(header);target.flush();os.fsync(target.fileno())
    return {'changedSlots':changed,'untouchedCompressedPayloadAndTimestampSha256':m.digest(untouched),'untouchedSlots':len(untouched)}

def untyped(value):
    kind=value[0]
    if kind==10:return m.Tag(10,{key:untyped(tag) for key,tag in value[1]})
    if kind==9:return m.Tag(9,[untyped(v) for v in value[2]],value[1])
    if kind==7:return m.Tag(7,bytes.fromhex(value[1]))
    return m.Tag(kind,value[1])

def source_checked(binding,manifest,rel,verified):
    path=Path(binding['source'])/rel
    if rel not in verified:
        m.require(rel in manifest['files'] and m.sha(path)==manifest['files'][rel]['sha256'],'Addressed immutable source file changed: '+rel);verified[rel]=manifest['files'][rel]['sha256']
    return path

def restore_contributions(actual,expected,allowed):
    from probe_dependency_shards import semantic
    ec,em,eo,_=semantic(expected);ac,am,ao,_=semantic(actual)
    m.require(not(set(ac)-set(ec)) and am==em and ao==eo,'Shard has additions/changed membership/unknown fields')
    lost=[ec[key] for key in sorted(set(ec)-set(ac))];m.require(lost and all(row['uuid'] in allowed for row in lost),'Shard loss is not exact outside-root geometry')
    root=m.compound(actual.root);data=m.compound(root['data']) if 'data'in root else root
    def intern(field,typed):
        values=data[field].value;tag=untyped(typed)
        for index,existing in enumerate(values):
            if c._typed(m,existing)==typed:return index
        values.append(tag);return len(values)-1
    cells={m.canonical(c._typed(m,m.Tag(10,{k:v for k,v in m.compound(tag).items() if k!='refs'}))):tag for tag in data['cells'].value}
    for row in lost:
        position=m.canonical(row['position']);tag=cells.get(position)
        if tag is None:
            tag=untyped(row['position']);m.compound(tag)['refs']=m.Tag(9,[],11);data['cells'].value.append(tag);cells[position]=tag
        indices=[intern('ownerTable',row['owner']),intern('dataTable',row['rootData']),intern('shapeTable',row['shape'])];m.compound(tag)['refs'].value.append(m.Tag(11,indices))
    post_cells,post_members,post_other,_=semantic(actual);m.require(post_cells==ec and post_members==am and post_other==ao,'Finite contribution restoration changed other semantics')
    return {'restoredContributions':len(lost),'restoredOwners':sorted({r['uuid'] for r in lost}),'existingContributionsUnchanged':True,'allMembershipAndOtherFieldsUnchanged':True,'expectedSemanticSha256':m.digest([ec,em,eo]),'actualSemanticSha256':m.digest([post_cells,post_members,post_other])}

def prepare():
    output=REPAIR/'plan.json';m.require(not output.exists(),'One repair plan already exists; execute/resume that exact plan')
    audit=m.read(REPAIR/'audit.json');m.require(audit['status']=='PASS' and not audit['errors'],'Original427 audit must pass with only proven outside-root losses');m.require(m.sha(INVENTORY)==INVENTORY_SHA,'Reviewed inventory changed')
    inventory=m.read(INVENTORY);db=c.attached_readonly(m,PLAN);binding=m.meta(db,'binding');manifest=m.read(binding['copyManifest']);verified={};operations=[];worlds={};aux_proofs=[];started=time.monotonic()
    try:
        m.require(audit['sourceBinding']==binding and inventory['sourceBinding']==binding and audit['inventory']=={'path':str(INVENTORY),'sha256':INVENTORY_SHA} and not m.meta(db,'applyStarted'),'Only the same unapplied context extension may repair dependencies')
        native_proof=audit['original427NativeDependencyCoverage'];m.require(m.sha(native_proof['path'])==native_proof['sha256'],'Final complete native dependency coverage changed');native=m.read(native_proof['path']);m.require(native['status']=='PASS' and native['depsComplete'] is True and native['inventory']==audit['inventory'] and not native['additionalChunksRequired'],'All original427 source native components need complete dependencies')
        selected={r[0] for r in db.execute('SELECT uuid FROM cleanup_owners UNION SELECT uuid FROM stage1_cleanup_owners')};oldshards=original_shards();allshards=set(oldshards)|{r['path'] for r in inventory['shards']};m.require(len(oldshards)==407 and len(allshards)==inventory['expectedFinalGlobalShardCount']==498,'Exact source shard union changed')
        source_records={}
        for row in inventory['chunks']:
            cx,cz=row['x'],row['z'];rel=row['sourceRegion'];path=source_checked(binding,manifest,rel,verified);record=read_slot(path,cx,cz);m.require(record is not None,'Source auxiliary terrain slot disappeared')
            cached=db.execute("SELECT compression,payload FROM old.chunk_cache WHERE dim='.' AND x=? AND z=?",(cx,cz)).fetchone();m.require(cached and tuple(record[:2])==cached,'Source auxiliary record differs from exact discovery cache')
            root=m.compound(chunk_decode(record[0],record[1]).root)
            for tag in be_tags(root):
                be=m.compound(tag);uids=[m.owner_uuid(m.compound(v)['owner']) for v in m.be_owners(be)]+([m.owner_uuid(be['resident'])] if 'resident' in be else [])
                m.require(not(set(uids)&selected),'Auxiliary immutable source would reintroduce a migrated selected owner')
            source_records[('region',cx,cz)]=record
            for layer in ('entities','poi'):
                layer_rel=rel.replace('region/',layer+'/',1)
                if layer_rel in manifest['files']:source_records[(layer,cx,cz)]=read_slot(source_checked(binding,manifest,layer_rel,verified),cx,cz)
                else:source_records[(layer,cx,cz)]=None
        source_global=m.read_nbt(source_checked(binding,manifest,GLOBAL,verified));global_proof=m.crop_pilot_global_index(source_global,allshards);source_data=m.compound(source_global.root);source_data=m.compound(source_data['data']) if 'data' in source_data else source_data;new_index=copy.deepcopy(source_data['OwnerShardChunks'])
        for variant,world_audit in audit['worlds'].items():
            target=Path(world_audit['target']);m.require(target==m.ROOT/'research/pilot'/variant,'Wrong pilot target');groups=collections.defaultdict(dict);before_files={};world_proofs=[]
            for rel,expected in world_audit['regions'].items():m.require(m.sha(target/rel)==expected,'Original region changed after audit: '+variant+'/'+rel)
            for row in inventory['chunks']:
                cx,cz=row['x'],row['z']
                for layer in ('region','entities','poi'):
                    rel=layer+'/r.%d.%d.mca'%(cx>>5,cz>>5);path=target/rel;old=read_slot(path,cx,cz);new=source_records[(layer,cx,cz)]
                    if old==new:proof={'proof':'EXACT_IMMUTABLE_SOURCE_RECORD'}
                    else:proof=flat_before(old) if layer=='region' else empty_layer_before(old,layer)
                    world_proofs.append({'layer':layer,'chunk':[cx,cz],'before':proof,'sourceCompressedPayloadSha256':hashlib.sha256(new[1]).hexdigest() if new else None})
                    if old!=new:groups[rel][(cx&31)+32*(cz&31)]=new
            grouped_losses=collections.defaultdict(list)
            for loss in world_audit['losses']:grouped_losses[tuple(loss['chunk'])].append(loss)
            for (cx,cz),losses in grouped_losses.items():
                rel='region/r.%d.%d.mca'%(cx>>5,cz>>5);old=read_slot(target/rel,cx,cz);m.require(old is not None,'Original helper chunk disappeared after audit');nbt=chunk_decode(old[0],old[1]);root=m.compound(nbt.root);tags=positions(be_tags(root));changes={}
                for loss in losses:
                    at=tuple(loss['position']);actual=tags.get(at);m.require((typed_sha(actual) if actual else None)==loss['actualBlockEntitySha256'],'Lost-helper input changed after audit')
                    if loss['expectedBlockEntity'] is not None:tags[at]=untyped(loss['expectedBlockEntity'])
                    changes[at]=loss['expectedState']
                m.rewrite_states(root,changes);root['block_entities']=m.Tag(9,[tags[p] for p in sorted(tags)],10);m.invalidate_chunk_lighting(root)
                groups[rel][(cx&31)+32*(cz&31)]=(2,m.zlib.compress(m.encode_nbt(nbt)),old[2])
            for rel,replacements in sorted(groups.items()):
                path=target/rel;before=m.sha(path) if path.is_file() else None;stage=REPAIR/'staged'/variant/rel;stats=stage_region(path,stage,replacements);operations.append({'variant':variant,'path':rel,'kind':'region','beforeSha256':before,'afterSha256':m.sha(stage),'staged':str(stage),'stats':stats});before_files[rel]=before
            for row in inventory['shards']:
                rel=row['path'];src=source_checked(binding,manifest,rel,verified);nbt=m.read_nbt(src);fields=m.compound(nbt.root);fields=m.compound(fields['data']) if 'data' in fields else fields
                m.require(not{m.owner_uuid(v) for v in fields.get('ownerTable',m.Tag(9,[],10)).value}&selected,'Auxiliary source shard reintroduces migrated selected UUID')
                path=target/rel;before=m.sha(path) if path.is_file() else None
                if before is not None:
                    match=m.re.fullmatch(r'data/bloodborne_dw_owner_shards/owners-(-?\d+)\.(-?\d+)\.nbt',rel);empty_shard(m.read_nbt(path),*map(int,match.groups()))
                stage=REPAIR/'staged'/variant/rel;stage.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,stage)
                operations.append({'variant':variant,'path':rel,'kind':'source_shard','beforeSha256':before,'afterSha256':m.sha(stage),'staged':str(stage),'sourceSha256':row['sha256']});before_files[rel]=before
            for rel,row in world_audit['ledgers']['shards'].items():
                if not row.get('restoreNativePrunedContributions'):continue
                src=source_checked(binding,manifest,rel,verified);expected=m.read_nbt(src)
                if variant=='candidate':m.cleanup_dictionary(PriorCleanup(db),'.',expected)
                path=target/rel;m.require(m.sha(path)==row['beforeSha256'],'Original pruned shard changed after exact audit');nbt=m.read_nbt(path);semantic=restore_contributions(nbt,expected,{r['uuid'] for r in inventory['owners'] if r['rootOutsideCopy']});stage=REPAIR/'staged'/variant/rel;stage.parent.mkdir(parents=True,exist_ok=True);m.write_nbt(stage,nbt)
                operations.append({'variant':variant,'path':rel,'kind':'restore_known_native_pruned_contributions','beforeSha256':row['beforeSha256'],'afterSha256':m.sha(stage),'staged':str(stage),'proof':semantic});before_files[rel]=row['beforeSha256'];row['repairedAfterSha256']=m.sha(stage)
            nbt=m.read_nbt(target/GLOBAL);before=m.sha(target/GLOBAL);m.require(before==world_audit['ledgers']['global']['beforeSha256'],'Global ledger changed after audit');fields=m.compound(nbt.root);fields=m.compound(fields['data']) if 'data' in fields else fields
            old_index=copy.deepcopy(fields['OwnerShardChunks']);fields.pop('OwnerShardChunks');other=typed_sha(nbt.root);fields['OwnerShardChunks']=new_index;fields.pop('OwnerShardChunks');m.require(typed_sha(nbt.root)==other,'Global non-index fields changed');fields['OwnerShardChunks']=new_index
            stage=REPAIR/'staged'/variant/GLOBAL;stage.parent.mkdir(parents=True,exist_ok=True);m.write_nbt(stage,nbt)
            operations.append({'variant':variant,'path':GLOBAL,'kind':'global_index_only','beforeSha256':before,'afterSha256':m.sha(stage),'staged':str(stage),'proof':{'indexBefore':len(old_index.value),'indexAfter':len(new_index.value),'nonIndexTypedNbtSha256':other,'sourceIndexFilter':global_proof}});before_files[GLOBAL]=before
            worlds[variant]={'target':str(target),'beforeFiles':before_files,'originalRegions':world_audit['regions'],'existingShards':world_audit['ledgers']['shards'],'lossesRestored':len(world_audit['losses']),'auxiliaryBeforeProofs':world_proofs,'originalSelectionDomainUnchanged':427}
        light_history={};history=REPAIR/'old-light-evidence';history.mkdir(parents=True,exist_ok=True)
        for name in ('relight-engine.json.done.tsv','relight-engine.json','relight-binding.json','relight-queue.tsv','relight-launch-binding.json','relight-launch-receipt.json','relight-server-console.log'):
            path=PLAN/name
            if path.is_file():
                dest=history/name;sync_copy(path,dest);light_history[name]={'path':str(dest),'sha256':m.sha(dest)}
        m.require('relight-engine.json.done.tsv' in light_history,'Original eight-row cursor evidence missing')
        fresh=PLAN/'context-extended-relight-engine.json';m.require(not fresh.exists() and not fresh.with_name(fresh.name+'.done.tsv').exists(),'Fresh context light namespace already occupied')
        report={'schemaVersion':1,'status':'DRY_RUN','kind':'PRESERVING_FINITE_PILOT_DEPENDENCY_REPAIR','sourceBinding':binding,'inventory':{'path':str(INVENTORY),'sha256':INVENTORY_SHA},'audit':{'path':str(REPAIR/'audit.json'),'sha256':m.sha(REPAIR/'audit.json')},'original427NativeDependencyCoverage':native_proof,'implementation':{'path':str(Path(__file__).resolve()),'sha256':m.sha(__file__)},'migrationSha256':m.sha(m.__file__),'contextSha256':m.sha(c.__file__),'semanticHelper':{'path':str(Path(__file__).with_name('probe_dependency_shards.py').resolve()),'sha256':m.sha(Path(__file__).with_name('probe_dependency_shards.py'))},'plannedContentFingerprint':c._content_fingerprint(m,db),'worlds':worlds,'operations':operations,'verifiedSourceFiles':verified,'auxiliaryChunks':inventory['chunks'],'oldLightEvidence':light_history,'lightPolicy':{'previousSavedRows':8,'oldLightNowInvalid':True,'reuseSavedPrefix':False,'allDebtPending':True,'freshReceipt':str(fresh),'freshDoneCursor':str(fresh.with_name(fresh.name+'.done.tsv'))},'newOwnersRecursed':False,'mapWrites':0,'databaseWrites':0,'seconds':time.monotonic()-started}
        report['requiredSourceShardCount']=len(allshards);m.atomic_json(output,report)
        file_plan={'schemaVersion':1,'status':'STAGED','kind':report['kind'],'semanticPlan':{'path':str(output),'sha256':m.sha(output)},'sourceBinding':binding,'inventory':report['inventory'],'audit':report['audit'],'worlds':{v:{'target':w['target']} for v,w in worlds.items()},'files':[{'target':str(Path(worlds[op['variant']]['target'])/op['path']),'staged':op['staged'],'beforeSha256':op['beforeSha256'],'afterSha256':op['afterSha256'],'kind':op['kind']} for op in operations]}
        m.atomic_json(REPAIR/'file-plan.json',file_plan);print(m.canonical({'status':'STAGED','operations':len(operations),'losses':{v:w['lossesRestored'] for v,w in worlds.items()},'path':str(REPAIR/'file-plan.json'),'sha256':m.sha(REPAIR/'file-plan.json')}))
    finally:db.close()

def sync_copy(source,target):
    target.parent.mkdir(parents=True,exist_ok=True)
    with source.open('rb') as src,target.open('wb') as dest:
        shutil.copyfileobj(src,dest);dest.flush();os.fsync(dest.fileno())

def admit(path):
    committed=m.read(path);m.require(committed.get('status')=='FILES_COMMITTED_NEEDS_ADMISSION','Root file commit must finish before metadata admission');file_binding=committed['filePlan'];file_path=m.checked(file_binding['path']);m.require(file_path==REPAIR/'file-plan.json' and m.sha(file_path)==file_binding['sha256'],'Committed file plan changed');file_plan=m.read(file_path);rich=file_plan['semanticPlan'];m.require(m.sha(rich['path'])==rich['sha256'],'Semantic repair plan changed');plan=m.read(rich['path'])
    m.require(plan['status']=='DRY_RUN' and plan['kind']=='PRESERVING_FINITE_PILOT_DEPENDENCY_REPAIR' and committed['filesCommitted']==len(file_plan['files']),'Wrong/incomplete pilot repair transaction')
    m.require(m.sha(__file__)==plan['implementation']['sha256'] and m.sha(m.__file__)==plan['migrationSha256'] and m.sha(c.__file__)==plan['contextSha256'],'Reviewed staging/admission implementation changed')
    m.require(m.sha(plan['semanticHelper']['path'])==plan['semanticHelper']['sha256'],'Reviewed finite contribution semantic helper changed')
    for evidence in (plan['inventory'],plan['audit'],plan['original427NativeDependencyCoverage']):m.require(m.sha(evidence['path'])==evidence['sha256'],'Frozen repair evidence changed')
    expected_files=[{'target':str(Path(plan['worlds'][op['variant']]['target'])/op['path']),'staged':op['staged'],'beforeSha256':op['beforeSha256'],'afterSha256':op['afterSha256'],'kind':op['kind']} for op in plan['operations']];m.require(file_plan['files']==expected_files,'Committed file plan omitted/changed a semantic repair operation')
    intent_binding=committed['intent'];m.require(m.sha(intent_binding['path'])==intent_binding['sha256'],'Root commit intent changed');intent=m.read(intent_binding['path']);m.require(intent['planSha256']==file_binding['sha256'] and intent['files']==[{k:row[k] for k in ('target','staged','beforeSha256','afterSha256')} for row in expected_files],'Root intent does not cover the exact semantic file operations')
    for record in file_plan['files']:m.require(m.sha(m.checked(record['target']))==record['afterSha256'] and m.sha(record['staged'])==record['afterSha256'],'Committed/staged repair content changed')
    for variant,world in plan['worlds'].items():
        target=Path(world['target']);m.require(target==m.ROOT/'research/pilot'/variant,'Pilot admission target escaped exact directory')
        operation_paths={op['path'] for op in plan['operations'] if op['variant']==variant}
        for rel,before in world['originalRegions'].items():
            if rel not in operation_paths:m.require(m.sha(target/rel)==before,'An untouched original terrain region changed')
        for rel,row in world['existingShards'].items():m.require(m.sha(target/rel)==row.get('repairedAfterSha256',row['beforeSha256']),'An original owner shard changed beyond its exact restoration plan')
    for evidence in plan['oldLightEvidence'].values():m.require(m.sha(evidence['path'])==evidence['sha256'],'Archived old native-light evidence changed')
    m.require(plan['lightPolicy']['oldLightNowInvalid'] is True and plan['lightPolicy']['reuseSavedPrefix'] is False,'Dependency repair cannot adopt the previous eight native saved rows')
    receipt={'schemaVersion':1,'status':'PASS','kind':plan['kind'],'repairPlan':rich['path'],'repairPlanSha256':rich['sha256'],'fileCommit':{'path':str(m.checked(path)),'sha256':m.sha(path)},'filePlan':file_binding,'sourceBinding':plan['sourceBinding'],'inventory':plan['inventory'],'audit':plan['audit'],'worlds':plan['worlds'],'operations':plan['operations'],'auxiliaryChunks':plan['auxiliaryChunks'],'oldLightEvidence':plan['oldLightEvidence'],'lightPolicy':plan['lightPolicy'],'originalSelectionDomainUnchanged':427,'newOwnersRecursed':False}
    receipt['requiredSourceShardCount']=plan['requiredSourceShardCount'];receipt_path=REPAIR/'receipt.json';m.atomic_json(receipt_path,receipt);db=m.connect(PLAN)
    try:
        m.require(m.meta(db,'binding')==plan['sourceBinding'] and m.meta(db,'contextExtension') and not m.meta(db,'applyStarted'),'Repair admission is only for the same unapplied context pilot')
        m.require(c._content_fingerprint(m,db)==plan['plannedContentFingerprint'],'Placements/selected cleanup changed after staging')
        binding={'path':str(receipt_path),'sha256':m.sha(receipt_path)};prior=m.meta(db,'contextDependencyRepair');m.require(prior is None or prior==binding,'A different dependency repair is already bound')
        m.meta(db,'contextDependencyRepair',binding);m.meta(db,'contextNativeLightRestart',{'repair':binding,'oldSavedRowsInvalidated':8,'freshReceipt':plan['lightPolicy']['freshReceipt'],'freshDoneCursor':plan['lightPolicy']['freshDoneCursor']})
        for row in plan['auxiliaryChunks']:
            for dx in (-1,0,1):
                for dz in (-1,0,1):db.execute("INSERT OR IGNORE INTO relight(dim,cx,cz,reason,status,receipt) VALUES('.',?,?,'new_auxiliary_source_dependency','pending',NULL)",(row['x']+dx,row['z']+dz))
        for world in plan['worlds'].values():
            for loss in m.read(plan['audit']['path'])['worlds'][Path(world['target']).name]['losses']:
                cx,cz=loss['chunk']
                for dx in (-1,0,1):
                    for dz in (-1,0,1):db.execute("INSERT OR IGNORE INTO relight(dim,cx,cz,reason,status,receipt) VALUES('.',?,?,'restored_original_helper_dependency','pending',NULL)",(cx+dx,cz+dz))
        db.execute("UPDATE relight SET status='pending',receipt=NULL");db.execute("DELETE FROM meta WHERE key IN ('contextRegionBinding','contextLightAdoption')");db.commit()
    finally:db.close()
    for world in plan['worlds'].values():
        marker=Path(world['target'])/'NATIVE-GRID-HARNESS-REPAIR-INCOMPLETE.json'
        if marker.exists():m.require(m.read(marker).get('filePlanSha256')==file_binding['sha256'],'A different repair owns the maintenance marker');marker.unlink()
    m.atomic_json(REPAIR/'admission-complete.json',{'status':'PASS','dependencyRepair':{'path':str(receipt_path),'sha256':m.sha(receipt_path)},'oldSavedRowsInvalidated':8,'freshNativeLightRequired':True,'metadataOnlyAdmission':True});print(str(receipt_path))

if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--audit',action='store_true');ap.add_argument('--prepare',action='store_true');ap.add_argument('--admit',type=Path);args=ap.parse_args();m.control_check()
    try:
        if args.audit:audit_original()
        elif args.admit:admit(args.admit)
        elif args.prepare:prepare()
        else:ap.error('Choose --audit, --prepare, or --admit <root-file-commit-receipt>')
    except BaseException:
        m.atomic_json(REPAIR/'last-error.json',{'status':'FAIL','error':traceback.format_exc(),'mode':'audit' if args.audit else 'admit' if args.admit else 'prepare'});raise
