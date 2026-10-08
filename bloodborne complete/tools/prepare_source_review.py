"""Prepare a bounded source derivative before Minecraft sees any Forge entities.

Never writes the original ZIP or the frozen subset. Architectural blocks remain
source blocks until the optional QA runtime transaction consumes explicit members.
All non-migration NBT fields keep their tag types. Original bytes are archived.
"""
from __future__ import annotations
import argparse, base64, copy, hashlib, json, uuid, zipfile
from collections import Counter
from pathlib import Path
from world_io import *
from rp_migration import registry_schema, migrate_entity, migrate_inventory_record, migrate_legacy_lamp_state
from audit_rp_migration import differences

ROOT=Path(__file__).resolve().parents[1]
SOURCE_LIGHT='bloodborne:hunter_lamp_light_source'
TARGET_LIGHT='bloodborne_dw:source_hunter_lamp_light'
LIGHT_CELLS=frozenset(((228,69,-932),(229,69,-931)))
def sha(data): return hashlib.sha256(data).hexdigest()
def dump(path,value): path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def typed_bytes(tag): return encode_nbt(NbtFile('',tag))
def state_json(key):
    name,_,tail=key.partition('[')
    return {'block':name,'properties':dict(pair.split('=',1) for pair in tail.rstrip(']').split(',')) if tail else {}}
def raw_nbt(tag): return None if tag is None else base64.b64encode(typed_bytes(tag)).decode('ascii')

class Source:
    def __init__(self, files):
        self.files=files;self.chunks={};self.entities={};self.block_entities={}
        for path,data in files.items():
            if not path.startswith('region/') or not path.endswith('.mca'):continue
            for stored in RegionFile(data).chunks():
                root=compound(stored.nbt().root);cx,cz=root['xPos'].value,root['zPos'].value
                self.chunks[cx,cz]=root
                for value in root.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                    f=compound(value);self.block_entities[tuple(f[k].value for k in ('x','y','z'))]=value
    def cell(self,pos):
        x,y,z=pos;chunk=self.chunks.get((x//16,z//16))
        if chunk is None: raise ValueError('Explicit coordinate outside frozen source subset: '+str(pos))
        state='minecraft:air'
        for section in chunk.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            if compound(section)['Y'].value==y//16:
                values=section_blocks(section)
                if values:
                    palette,indices=values;state=block_state_key(palette[indices[(y%16)*256+(z%16)*16+x%16]])
                break
        tag=self.block_entities.get(tuple(pos))
        return {'pos':list(pos),'state':state,**state_json(state),'sourceNbt':raw_nbt(tag),'sourceNbtSha256':sha(typed_bytes(tag)) if tag else None}

def transform(files):
    """No architecture palette rewrites, no unknown entity exclusions."""
    result=dict(files);schema=registry_schema();records=[];issues=[];entities=[];counts=Counter();bes=Counter();contexts=Counter();block_counts=Counter();custom_blocks=[];mapped_lights=set();palette_ids=set()
    def checked(tag,migration,path,kind):
        contexts[kind]+=1
        actual=set(differences(tag,migration.tag,path));expected={x['path'] for x in migration.changes}
        if actual!=expected:raise AssertionError('Unlogged typed changes: '+str(actual^expected))
        second=migrate_entity(migration.tag,path=path,schema=schema) if kind=='entity' else migrate_inventory_record(migration.tag,kind=kind,path=path,schema=schema)
        assert second.tag==migration.tag and not second.changed,'Not idempotent: '+path
        issues.extend(migration.issues)
        if migration.changed:records.append({'path':path,'kind':kind,'changes':migration.changes,'beforeSha256':sha(typed_bytes(tag)),
            'afterSha256':sha(typed_bytes(migration.tag)),'preservation':'PASS_ALL_UNLOGGED_TYPED_FIELDS_EQUAL','secondPass':'PASS'})
        return migration.tag
    for path,data in sorted(files.items()):
        if path.endswith('.mca') and path.split('/')[0] in ('region','entities'):
            region=RegionFile(data)
            for stored in list(region.chunks()):
                nbt=stored.nbt();before=copy.deepcopy(nbt.root);f=compound(nbt.root)
                if path.startswith('region/'):
                    cx,cz=f['xPos'].value,f['zPos'].value
                    for section in f.get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
                        values=section_blocks(section)
                        if not values:continue
                        palette,indices=values;fields=compound(section);sy=fields['Y'].value;original_palette=copy.deepcopy(palette)
                        for old in original_palette:
                            identifier=compound(old)['Name'].value;palette_ids.add(identifier)
                            if not identifier.startswith('minecraft:') and identifier!=SOURCE_LIGHT:issues.append({'path':f'{path}/chunk/{stored.x},{stored.z}/section/{sy}/palette','code':'UNKNOWN_PALETTE_PROVIDER_PRELOAD_REJECTED','value':identifier})
                        for index,pi in enumerate(indices):
                            old=original_palette[pi];identifier=compound(old)['Name'].value;block_counts[identifier]+=1
                            if identifier.startswith('minecraft:'):continue
                            pos=(cx*16+index%16,sy*16+index//256,cz*16+(index//16)%16);context=f'{path}/chunk/{stored.x},{stored.z}/block/{pos[0]},{pos[1]},{pos[2]}'
                            raw=raw_nbt(old);custom_blocks.append({'pos':list(pos),'state':block_state_key(old),'typedState':raw})
                            if identifier!=SOURCE_LIGHT or pos not in LIGHT_CELLS or compound(old).get('Properties',Tag(TAG_COMPOUND,{})).value:
                                issues.append({'path':context,'code':'UNKNOWN_OCCUPIED_BLOCK_PROVIDER_PRELOAD_REJECTED','value':identifier});continue
                            target=Tag(TAG_COMPOUND,{'Name':Tag(TAG_STRING,TARGET_LIGHT)})
                            # Replace this proven one-entry registry value in-place; keep all palette indices/long-array bytes.
                            palette[pi]=target;mapped_lights.add(pos)
                            records.append({'path':context,'kind':'technical_light_block','pos':list(pos),'beforeState':block_state_key(old),'afterState':TARGET_LIGHT,
                                            'originalTypedState':raw,'targetTypedState':raw_nbt(target),'rule':'EXACT_TWO_SOURCE_AIRBLOCK_LIGHT9_CELLS',
                                            'proof':'reports/SOURCE_LEGACY_LAMP_BLOCK_BYTECODE.txt','luminance':9,'collision':'EMPTY','selection':'EMPTY','isAir':False,'replaceable':True})
                for listkey in ('Entities','entities'):
                    for i,tag in enumerate(f.get(listkey,Tag(TAG_LIST,[],TAG_COMPOUND)).value):
                        fields=compound(tag);identifier=fields.get('id',Tag(TAG_STRING,'<missing>')).value;counts[identifier]+=1
                        context=f'{path}/chunk/{stored.x},{stored.z}/{listkey}/{i}'
                        if not identifier.startswith('minecraft:') and identifier not in schema['entity_map']:
                            issues.append({'path':context,'code':'UNKNOWN_ENTITY_PROVIDER_PRELOAD_REJECTED','value':identifier})
                        converted=checked(tag,migrate_entity(tag,path=context,schema=schema),context,'entity')
                        f[listkey].value[i]=converted;entities.append(('minecraft:overworld',converted))
                for listkey in ('block_entities','TileEntities'):
                    for i,tag in enumerate(f.get(listkey,Tag(TAG_LIST,[],TAG_COMPOUND)).value):
                        identifier=compound(tag).get('id',Tag(TAG_STRING,'<missing>')).value;bes[identifier]+=1
                        context=f'{path}/chunk/{stored.x},{stored.z}/{listkey}/{i}'
                        if not identifier.startswith('minecraft:'):issues.append({'path':context,'code':'UNKNOWN_BLOCK_ENTITY_PROVIDER_PRELOAD_REJECTED','value':identifier})
                        f[listkey].value[i]=checked(tag,migrate_inventory_record(tag,kind='block_entity',path=context,schema=schema),context,'block_entity')
                if nbt.root!=before:region.set_chunk(stored.x,stored.z,nbt,compression=stored.compression,timestamp=stored.timestamp)
            result[path]=region.to_bytes()
        elif path=='level.dat':
            nbt=decode_nbt(data,compressed='gzip');f=compound(compound(nbt.root)['Data'])
            if 'Player' in f:
                tag=f['Player'];context='level.dat/Data/Player'
                f['Player']=checked(tag,migrate_inventory_record(tag,kind='player',path=context,schema=schema),context,'player')
            # Only the isolated review fixture name is changed; gameplay/player fields are preserved.
            old=f['LevelName'].value;f['LevelName']=Tag(TAG_STRING,'isolated-smoke-world')
            records.append({'path':'level.dat/Data/LevelName','kind':'fixture_configuration','before':old,'after':'isolated-smoke-world'})
            result[path]=encode_nbt(nbt,compressed='gzip')
    lamp_report={'status':'NO_SOURCE_LAMP_MANAGER'}
    if 'data/hunter_lamp_manager.dat' in files:
        nbt=decode_nbt(files['data/hunter_lamp_manager.dat'],compressed='gzip');migration=migrate_legacy_lamp_state(nbt.root,entities=entities)
        lamp_report={'sourceBytesPreserved':True,'changes':migration.changes,'issues':migration.issues,
                     'status':'PASS_SEPARATE_RP_LAMP_STATE' if migration.changed else 'UNRESOLVED_SOURCE_LAMP_STATE_PRESERVED'}
        if migration.changed:
            result['data/bloodborne_rp_lamps.dat']=encode_nbt(NbtFile(nbt.name,migration.tag),compressed='gzip')
        else:issues.extend(migration.issues)
    if mapped_lights!=LIGHT_CELLS:issues.append({'path':'technical_light_scope','code':'SOURCE_TECHNICAL_LIGHT_CELL_SET_MISMATCH','value':[list(x) for x in sorted(mapped_lights)]})
    return result,{'entityCountsBefore':dict(counts),'blockEntityCountsBefore':dict(bes),'contextsAudited':dict(contexts),
        'allOccupiedBlockCountsBefore':dict(block_counts),'allCustomOccupiedBlocksBefore':custom_blocks,'technicalLightTarget':TARGET_LIGHT,
        'allPaletteRegistryIdsBefore':sorted(palette_ids),'customPaletteRegistryIdsBefore':sorted(i for i in palette_ids if not i.startswith('minecraft:')),
        'entityContexts':len(entities),'records':records,'issues':issues,'lampManager':lamp_report,
        'unknownEntityExclusions':[],'unknownBlockEntityExclusions':[],'originalDataFilesBytesRetained':True}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--name',default='source-review-prepared-v1')
    parser.add_argument('--physics-reference',type=Path,default=ROOT/'reports/SOURCE_PHYSICS_CLIENT_SWEPT_ACTUAL.json')
    parser.add_argument('--plan',type=Path,default=ROOT/'reports/FIRST_SET_MIGRATION_PLAN.json')
    parser.add_argument('--report',type=Path,default=ROOT/'reports/SOURCE_REVIEW_PREPARATION.json');args=parser.parse_args()
    if not args.name.replace('-','').replace('_','').isalnum():raise ValueError('Unsafe output name')
    planpath=args.plan.resolve();plan=json.loads(planpath.read_text(encoding='utf8'))
    reportpath=args.report.resolve()
    if reportpath.parent!=(ROOT/'reports').resolve():raise ValueError('Preparation report must be directly under project reports')
    if plan.get('review_revision','V7')!='V7' and (reportpath==ROOT/'reports/SOURCE_REVIEW_PREPARATION.json' or reportpath.exists()):
        raise ValueError('New review preparation needs a fresh distinct report; historical preparation is preserved')
    if plan.get('review_revision','V7')!='V7':
        if plan.get('review_revision')=='V9':
            from prepare_source_review_v9_plan import frozen_descriptor_contract
            artifact=plan.get('production_descriptor_artifact')
            if artifact is None or sha(Path(artifact['path']).read_bytes())!=artifact['sha256']:
                raise ValueError('V9 preparation requires the exact frozen production descriptor artifact')
            if frozen_descriptor_contract(Path(artifact['path']))!=plan['descriptor_sha256']:
                raise ValueError('V9 plan omits a registered composite descriptor or changes frozen bytes')
        for kind,expected in plan['descriptor_sha256'].items():
            descriptor=ROOT/'src/architecture/resources/bloodborne_dw/composite'/(kind+'.json')
            if sha(descriptor.read_bytes())!=expected:raise ValueError('Reviewed descriptor changed since source plan; create a new versioned plan: '+kind)
    fixture=Path(plan['source_fixture']['path']);fixture_before=sha(fixture.read_bytes());assert fixture_before==plan['source_fixture']['sha256']
    destination=ROOT/'build'/args.name
    if destination.exists():raise ValueError('Never overwrite an existing source review derivative: '+str(destination))
    with zipfile.ZipFile(fixture) as archive:files={n:archive.read(n) for n in archive.namelist() if not n.endswith('/')}
    source=Source(files);objects=[];excluded=[];members=set();contexts={}
    def context_walk(value):
        if isinstance(value,dict):
            if isinstance(value.get('pos'),list) and isinstance(value.get('state'),str) and value.get('included_in_frozen_source_fixture',False):
                pos=tuple(value['pos']);actual=source.cell(pos);assert actual['state']==value['state'],('Source plan precondition mismatch',pos,value['state'],actual['state']);contexts[pos]=actual
            for k,v in value.items():
                if k not in ('measured_original_1_18_2_physics','source_block_entity_nbt'):context_walk(v)
        elif isinstance(value,list):
            for child in value:context_walk(child)
    for entry in plan['coordinate_instances']:
        if not all(m.get('included_in_frozen_source_fixture') for m in entry['source_members']):
            excluded.append({'key':entry['key'],'reason':'SOURCE_MEMBER_CHUNKS_ABSENT_FROM_FROZEN_SUBSET','sourceMembers':entry['source_members']});continue
        context_walk(entry)
        root=entry['owned_source_root_candidate']['root']['pos'] if entry['key'].startswith('architecture_tree') else entry['target_root_context']['root']['pos']
        snapshots=[source.cell(m['pos']) for m in entry['source_members']]
        for m in snapshots:
            assert tuple(m['pos']) not in members,'Overlapping source membership';members.add(tuple(m['pos']))
        shift=entry.get('required_per_instance_source_alignment',{}).get('offset_blocks',[0,0,0])
        if entry['key']=='roof':shift=entry['normalization']['compensating_source_shift_blocks'] if 'compensating_source_shift_blocks' in entry['normalization'] else [0,-3/16,5/16]
        row={'key':entry['key'],'kind':entry['target_kind'].split(':',1)[1],'root':root,'expectedRoot':source.cell(root),'members':snapshots,
             'rotation':entry.get('target_rotation',0),'variant':entry.get('target_art_variant',entry.get('thin_variant') or 0),'profile':'base','open':False,'sourceShift':shift}
        if 'target_mount' in entry:row['mount']=entry['target_mount']
        if entry['key']=='wall':row['art']=entry['stable_source_manual_form']
        objects.append(row)
    for i,entry in enumerate(plan['ladder_pairs']):
        assert all(m.get('included_in_frozen_source_fixture') for m in entry['source_members']);context_walk(entry)
        snapshots=[source.cell(m['pos']) for m in entry['source_members']]
        for m in snapshots:
            assert tuple(m['pos']) not in members;members.add(tuple(m['pos']))
        objects.append({'key':f'source_ladder_{i:02d}','kind':'source_ladder_pair','root':entry['target_root_candidate'],
            'visual':entry['source_visual_member']['pos'],'physical':entry['source_invisible_climbing_member']['pos'],
            'members':snapshots,'variant':entry['target_art_variant'],'profile':'base','sourceVisualModel':entry['source_visual_model']})
    context_walk(plan['unconsumed_ladder_caps'])
    assert len(objects)==41 and len(members)==110
    transformed,audit=transform(files)
    # Reject preload if any context would lose an unknown registered object/item.
    fatal=[x for x in audit['issues'] if x['code']!='SOURCE_DUPLICATE_LAMP_REGISTRATION']
    if fatal:dump(ROOT/'reports/SOURCE_REVIEW_PRELOAD_REJECTED.json',audit);raise ValueError('Source subset has unresolved typed migration contexts; see preload report')
    destination.mkdir();world=destination/'world';provenance=destination/'source-provenance';world.mkdir();provenance.mkdir()
    (provenance/'Source-coordinate-fixture-original.zip').write_bytes(fixture.read_bytes())
    dump(provenance/'rp-preload-migration.json',audit)
    for path,data in transformed.items():target=world/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    marker={'schema':'dreamwalker-source-copy-preload-v1','sourceFixtureSha256':fixture_before,'sourceArchiveSha256':plan['source_sha256_before'],
        'preloadAuditStatus':'PASS_ALL_ENTITY_AND_INVENTORY_CONTEXTS_RESOLVED','originalSnapshot':str(provenance/'Source-coordinate-fixture-original.zip'),
        'occupiedBlockAuditStatus':'PASS_ALL_OCCUPIED_BLOCK_PROVIDERS_RESOLVED','requiredTechnicalBlockIds':[TARGET_LIGHT],
        'worldFiles':[{'path':p,'sha256':sha(data)} for p,data in sorted(transformed.items())]}
    dump(world/'dreamwalker-source-copy.json',marker)
    for row in objects:
        row['ownerUuid']=str(uuid.uuid5(uuid.NAMESPACE_URL,fixture_before+'/'+row['key']))
        row['migrationSignature']=sha(json.dumps(row,sort_keys=True,separators=(',',':')).encode())
    physics_path=args.physics_reference.resolve();physics=json.loads(physics_path.read_text(encoding='utf8'))
    if physics.get('status')!='MEASURED_ACTUAL_SOURCE_SERVER' or not all(m.get('allSweptMovementChunksPreloaded') for m in physics['actual_fake_player_movements']):
        raise ValueError('Complete original swept-chunk physics reference is required')
    document={'schemaVersion':1,'authoringGuard':'SOURCE_COPY_EXPLICIT_MEMBERS_ONLY','allowedLevelName':'isolated-smoke-world',
        'sourceFixtureSha256':fixture_before,'sourceArchiveSha256':plan['source_sha256_before'],'objects':objects,
        'technicalLightCells':[{k:r[k] for k in ('pos','beforeState','afterState','originalTypedState','targetTypedState','luminance','collision','selection','isAir','replaceable')} for r in audit['records'] if r['kind']=='technical_light_block'],
        'nonmemberPreconditions':[row for pos,row in sorted(contexts.items()) if pos not in members and pos not in {tuple(o['root']) for o in objects}],
        'excludedInstances':excluded,'sourceMemberCount':len(members),'expectedObjectCount':len(objects),
        'sourcePhysicsReference':str(physics_path),'sourcePhysicsReferenceSha256':sha(physics_path.read_bytes()),
        'sourcePhysicsMovements':physics['actual_fake_player_movements'],
        'commands':['gamerule spawnRadius 0','gamerule doTileDrops true','defaultgamemode creative','setworldspawn -93 58 -211','time set noon','weather clear']}
    if plan.get('review_revision','V7')!='V7':
        document['reviewRevision']=plan['review_revision'];document['sourcePlan']=str(planpath);document['sourcePlanSha256']=sha(planpath.read_bytes())
        document['descriptorSha256']=plan['descriptor_sha256'];document['temporaryTypeIds']=plan['temporary_type_ids']
    inputpath=destination/'source-review-input.json';dump(inputpath,document)
    assert sha(fixture.read_bytes())==fixture_before
    report={'schema':'dreamwalker-source-review-preparation-v1','status':'PASS_PRELOAD_TYPED_MIGRATION_RUNTIME_NOT_RUN','world':str(world),'input':str(inputpath),
        'inputSha256':sha(inputpath.read_bytes()),'sourceFixture':str(fixture),'sourceFixtureSha256':fixture_before,
        'sourceFixtureBytesUnchanged':True,'originalArchiveWritten':False,'sourceMemberCount':len(members),'objectCount':len(objects),
        'architectureCount':7,'ladderPairCount':34,'nonmemberPreconditions':len(document['nonmemberPreconditions']),'excludedInstances':excluded,
        'preloadAudit':audit,'worldFiles':marker['worldFiles'],'provenanceArchive':str(provenance/'Source-coordinate-fixture-original.zip'),
        'runtimeMigration':'NOT_RUN','gameSavePreservation':'NOT_RUN','reviewAcceptance':'PENDING_USER_REVIEW',
        'limits':['Selected source areas only; no full-city conversion','Full Forge typed NBT is archived; live RP SourceLegacyPayload retention requires an independent game-save check','Minecraft DFU changes and runtime migration changes must be audited separately']}
    report['reviewRevision']=plan.get('review_revision','V7');report['sourcePlan']=str(planpath);report['sourcePlanSha256']=sha(planpath.read_bytes())
    dump(reportpath,report)
    print(json.dumps({k:report[k] for k in ('status','world','input','objectCount','sourceMemberCount')}))
if __name__=='__main__':main()
