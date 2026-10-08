"""Verify saved review roots, UUID/payload/masks, native fixtures and static RP controls.

Counts come from the frozen input, never an assumed68-root historical scene.
Read-only: this tool opens neither Minecraft nor a world for writing.
"""
from __future__ import annotations
import argparse, hashlib, json, struct, uuid
from collections import Counter, defaultdict
from pathlib import Path
from archive_first_set_scene import inventory
from verify_client_scene_persistence import full_rp_entities
from world_io import RegionFile, NbtFile, compound, block_state_key, section_blocks, read_nbt, decode_nbt, encode_nbt

ROOT=Path(__file__).resolve().parents[1]
COMPOSITE_KINDS={'prototype_double_door','prototype_wood_window','prototype_thin_window','prototype_roof','prototype_tree'}

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def typed_sha(tag):return hashlib.sha256(encode_nbt(NbtFile('',tag))).hexdigest()
def require(condition,message):
    if not condition:raise ValueError(message)
def read(path):return json.loads(Path(path).read_text(encoding='utf8'))
def resolve(path):
    path=Path(path);return (path if path.is_absolute() else ROOT/path).resolve()
def identity(tag):return str(uuid.UUID(bytes=struct.pack('>4I',*(value&0xffffffff for value in tag.value))))
def owner(tag):
    fields=compound(tag);return identity(fields['uuid']),fields['id'].value,tuple(fields['root'].value)
def state_parts(key):
    name,separator,properties=key.partition('[')
    return name,dict(pair.split('=',1) for pair in properties.rstrip(']').split(',')) if separator else {}

def states_at(world,positions):
    """Decode only actual fixture chunks, retaining arbitrary native state properties."""
    groups=defaultdict(list);regions={};result={}
    for pos in positions:groups[(pos[0]>>4,pos[2]>>4)].append(pos)
    for (cx,cz),samples in groups.items():
        region=(cx>>5,cz>>5)
        if region not in regions:regions[region]=RegionFile((world/'region'/f'r.{region[0]}.{region[1]}.mca').read_bytes())
        chunk=regions[region].get_chunk(cx&31,cz&31);require(chunk is not None,'Missing fixture terrain chunk')
        sections={compound(section)['Y'].value:section_blocks(section) for section in compound(chunk.nbt().root)['sections'].value}
        for pos in samples:
            decoded=sections.get(pos[1]>>4)
            if not decoded:result[pos]='minecraft:air';continue
            palette,indices=decoded;index=(pos[0]&15)+16*(pos[2]&15)+256*(pos[1]&15)
            result[pos]=block_state_key(palette[indices[index]])
    return result

def run(args):
    author_path,reopen_path,input_path=map(resolve,[args.author_report,args.server_report,args.scene_input])
    author,reopen,requested=map(read,[author_path,reopen_path,input_path]);expected={tuple(row['root']):row for row in requested['objects']}
    require(len(expected)==len(requested['objects']),'Input repeats an object root')
    for label,report in [('author',author),('reopen',reopen)]:
        require(report['status']=='PASS' and report['exit_code']==0 and report.get('original_world_loaded') is False,label+' is not a normal isolated-server PASS')
        require(report['artifact_sha256']==args.artifact_sha,label+' uses a different production artifact')
        require(not report.get('termination'),label+' was forcibly terminated')
        run_dir=resolve(report['run_directory']);require(run_dir.is_relative_to((ROOT/'build').resolve()),'Server path escapes isolated build area')
        jars=[path for path in (run_dir/'mods').glob('*.jar') if sha(path)==args.artifact_sha]
        require(len(jars)==1,label+' runtime production JAR is absent/ambiguous')
    require(not any(row.get('extra_mod') for row in reopen['modset']),'Production reopen still includes authoring QA')
    raw=author['review_scene_output'];require(sha(raw['path'])==raw['sha256'] and read(raw['path'])==raw['result'],'Raw scene author output changed')
    require(raw['result']['status']=='PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN','Author did not finish scene placement')
    require(raw['result']['sceneId']==requested['sceneId'],'Frozen scene ID differs from author output')
    input_proof=author['review_scene_input'];require(input_proof['sha256']==sha(input_path),'Scene input bytes differ from author input hash')
    author_world=resolve(author['run_directory'])/'isolated-smoke-world';world=resolve(reopen['run_directory'])/'isolated-smoke-world'
    copy=reopen['derived_world_copy'];require(resolve(copy['source'])==author_world and copy['source_unchanged_after_run'],'Reopen source is not the unchanged actual author world')
    require(copy['copy_byte_verification']=='PASS','Reopen world copy was not byte verified')
    for row in copy['files']:
        path=author_world/row['path'];require(path.stat().st_size==row['bytes'] and sha(path)==row['sha256'],'Author source file changed after reopen: '+row['path'])
    before_roots,before_bes,_=inventory(author_world);roots,bes,entities=inventory(world)
    require(set(roots)==set(expected),'Saved root set differs from exact declared input count='+str(len(expected)))
    require(roots==before_roots,'Root state changed on production-only reopen/save')
    authored={tuple(row['root']):row for row in raw['result']['objects']};require(set(authored)==set(expected),'Raw author root set differs from input')
    composite_owners={};owner_rows=[];native_rows=[]
    for pos,request in expected.items():
        name,props=state_parts(roots[pos]);require(name=='bloodborne_dw:'+request['kind'],'Wrong independent family at '+str(pos))
        require(props.get('profile')==request['profile'],'Profile not frozen at '+str(pos))
        if request['kind'] in COMPOSITE_KINDS:
            require(props.get('rotation')==str(request['yaw']) and props.get('variant')==str(request['variant']),'Composite pose/art differs from request')
            require(props.get('open')==str(request.get('open',False)).lower(),'Open state differs from request')
            if request.get('mount'):require(props.get('mount')==request['mount'],'Thin mounted plane differs from request')
            require(pos in bes and before_bes[pos]==bes[pos],'Root full typed BE changed at '+str(pos))
            fields=compound(bes[pos]);actual=owner(fields['resident']);require(actual[1]==name and actual[2]==pos,'Root owner identity binds a different ID/position')
            require(actual[0]==authored[pos]['ownerUuid'] and actual[0] not in composite_owners,'Root UUID changed/duplicated')
            composite_owners[actual[0]]=(actual,pos,fields['payload'])
            payload=compound(fields['payload'])
            if request.get('glazingMounted'):
                require(payload.get('GlazingMounted') is not None and payload['GlazingMounted'].value==1,'New-build glass lost explicit mounted payload')
                require(payload.get('MountFace') is not None and payload['MountFace'].value==request['mountFace'],'Mounted face differs from request')
                require('MountY' in payload,'Actual transformed surface seating was not persisted')
            owner_rows.append({'root':list(pos),'registryId':name,'uuid':actual[0],'state':roots[pos],
                'full_typed_nbt_sha256':typed_sha(bes[pos]),'payload_sha256':typed_sha(fields['payload']),
                'payload_fields':sorted(payload),'mountY':payload.get('MountY').value if 'MountY' in payload else None,
                'full_typed_nbt_unchanged':True,'author_counts':{key:authored[pos][key] for key in ['helperCount','ownedCells','foreignOverlayCells','visualPartCount','physicalBoxCount','selectionBoxCount'] if key in authored[pos]}})
        else:
            require(pos not in bes,'Ordinary native architecture unexpectedly has a BE at '+str(pos))
            if request['kind']=='prototype_ladder':
                yaw=request['yaw'];require(props.get('facing')==['north','east','south','west'][yaw//2] and props.get('diagonal')==str(bool(yaw%2)).lower(),'Ordinary ladder yaw differs from actual item request')
                require(props.get('variant')==str(request['variant']) and props.get('source_clone')=='false','Ordinary ladder carries wrong art/hidden installation role')
            if request['kind']=='prototype_wall':
                art=request.get('art',{});require(props.get('material')==art.get('material','0'),'Wall art differs from request')
                if requested['sceneId']=='first-set-review-v8':
                    require(props.get('connections')=='auto' and all(props.get(side) in {'none','low','tall'} for side in ['north','east','south','west']),'V8 scene wall is not ordinary native AUTO topology')
            require(authored[pos]['placementPath']=='ordinary Item.useOnBlock','Native architecture bypassed actual item authoring')
            native_rows.append({'root':list(pos),'state':roots[pos],'be_or_helper':False,'itemAction':authored[pos].get('itemAction'),'item_origin':request.get('itemOrigin','ordinary item')})
    saved_cells={pos:be for pos,be in bes.items() if compound(be)['id'].value=='bloodborne_dw:composite_cell'}
    old_cells={pos:be for pos,be in before_bes.items() if compound(be)['id'].value=='bloodborne_dw:composite_cell'}
    require(saved_cells==old_cells,'Root/helper full typed NBT or exact carrier cell set changed')
    ledger_path=world/'data/bloodborne_dw_composite_owners.dat';old_ledger_path=author_world/'data'/ledger_path.name
    require(read_nbt(ledger_path)==read_nbt(old_ledger_path),'Persisted exact ledger changed on reopen')
    ledger=compound(read_nbt(ledger_path).root)['data'];bindings={};counts=defaultdict(Counter)
    for cell in compound(ledger)['cells'].value:
        fields=compound(cell);pos=tuple(fields['pos'].value);require(pos not in bindings and fields['owners'].value,'Duplicate or empty ledger cell')
        bindings[pos]=fields['owners'];seen=set()
        for entry in fields['owners'].value:
            e=compound(entry);o=owner(e['owner']);key=o[0];require(key in composite_owners and o==composite_owners[key][0] and key not in seen,'Stale/foreign/duplicate owner in cell '+str(pos));seen.add(key)
            root=composite_owners[key][1];data=compound(e['rootData']);root_id,root_props=state_parts(roots[root])
            require(data['id'].value==root_id and {k:v.value for k,v in compound(data['properties']).items()}==root_props,'Cached root state differs from actual owner')
            require(decode_nbt(data['payload'].value).root==composite_owners[key][2],'Cached typed payload differs from actual owner')
            counts[key]['ownedCells']+=1;counts[key]['collisionCuboids']+=len(e['collision'].value);counts[key]['selectionCuboids']+=len(e['selection'].value)
            counts[key]['helperCells']+=int(pos in saved_cells and 'resident' not in compound(saved_cells[pos]))
            counts[key]['foreignOverlayCells']+=int(pos not in saved_cells)
        if pos in saved_cells:require(compound(saved_cells[pos])['owners']==fields['owners'],'Carrier contributions differ from ledger cell')
    for pos,be in saved_cells.items():require(pos in bindings and compound(be)['owners']==bindings[pos],'Saved root/helper missing exact ledger bindings')
    for row in owner_rows:
        uuid_key=row['uuid'];root=tuple(row['root']);require(root in bindings and any(owner(compound(e)['owner'])[0]==uuid_key for e in bindings[root].value),'Actual root has no own footprint binding')
        require(counts[uuid_key]['ownedCells']==authored[root]['ownedCells'],'Sparse own cell mask count differs from successful author transaction')
        row['saved_sparse_counts']=dict(counts[uuid_key])
    fixture_positions=[tuple(row['pos']) for row in requested['nativeBlocks']];old_states=states_at(author_world,fixture_positions);new_states=states_at(world,fixture_positions);require(old_states==new_states,'Native fixture state changed on reopen')
    fixtures=[]
    for row in requested['nativeBlocks']:
        pos=tuple(row['pos']);name,props=state_parts(new_states[pos]);require(name==row['block'] and all(props.get(k)==v for k,v in row.get('properties',{}).items()),'Native fixture differs from request '+str(pos))
        require(bes.get(pos)==before_bes.get(pos),'Native fixture full typed BE differs '+str(pos))
        if row.get('diamondCount'):
            require(pos in bes,'Foreign chest is missing');items=compound(bes[pos])['Items'].value;diamonds=sum(compound(item)['Count'].value for item in items if compound(item)['id'].value=='minecraft:diamond')
            require(diamonds==row['diamondCount'],'Foreign chest diamond inventory changed')
        fixtures.append({'pos':list(pos),'state':new_states[pos],'full_native_be_equal':True,'be_sha256':typed_sha(bes[pos]) if pos in bes else None})
    rp_before,rp_after=full_rp_entities(author_world),full_rp_entities(world);require(rp_before==rp_after,'Static accepted RP control entity ID/UUID/full typed NBT changed on reopen')
    require({compound(e)['id'].value for e in rp_after.values()}=={'bloodborne_rp:tree1','bloodborne_rp:hunterlamp'},'Accepted RP control types differ')
    entity_rows=[{'uuid':key,'registryId':compound(entity)['id'].value,'pos':[value.value for value in compound(entity)['Pos'].value],'full_typed_nbt_sha256':typed_sha(entity),'full_typed_nbt_unchanged':True} for key,entity in sorted(rp_after.items())]
    return {'schema':'dreamwalker-review-v8-scene-independent-v1','status':'PASS_SAVED_OWNERS_NATIVE_FIXTURES_AND_PRODUCTION_REOPEN',
        'sceneId':requested['sceneId'],'production_jar_sha256':args.artifact_sha,'scene_input':str(input_path),'scene_input_sha256':sha(input_path),
        'author_report':str(author_path),'author_report_sha256':sha(author_path),'production_reopen_report':str(reopen_path),'production_reopen_report_sha256':sha(reopen_path),
        'source_world':str(author_world),'world':str(world),'expected_root_count':len(expected),'actual_root_count':len(roots),
        'root_counts_by_kind':dict(Counter(row['kind'] for row in requested['objects'])),'composite_owners':owner_rows,
        'native_ladder_and_wall_roots':native_rows,'cached_root_and_helper_bes':len(saved_cells),'ledger_cell_count':len(bindings),
        'ledger_typed_sha256':typed_sha(ledger),'all_cached_uuid_state_payload_and_cell_bindings':'PASS',
        'native_fixtures':fixtures,'rp_controls':entity_rows,'author_source_copy_unchanged':'PASS_BYTE_HASHES',
        'exact_sparse_counts_recorded':True,'visual_acceptance':'PENDING_USER_REVIEW','creative_ui_acceptance':'PENDING_USER_REVIEW',
        'whole_source_city_converted':False,'whole_world_metadata_unchanged_claim':False}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--server-report',type=Path,required=True);parser.add_argument('--author-report',type=Path,required=True)
    parser.add_argument('--scene-input',type=Path,default=ROOT/'tools/first_set_scene_input.json');parser.add_argument('--artifact-sha',required=True)
    parser.add_argument('--report',type=Path,default=ROOT/'reports/REVIEW_V8_SCENE_INDEPENDENT.json');args=parser.parse_args()
    try:result=run(args)
    except Exception as failure:
        result={'schema':'dreamwalker-review-v8-scene-independent-v1','status':'FAIL','production_jar_sha256':args.artifact_sha,'error':str(failure)}
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':result['status'],'roots':result.get('actual_root_count'),'owners':len(result.get('composite_owners',[])),'report':str(args.report)}))
    if result['status']=='FAIL':raise SystemExit(1)

if __name__=='__main__':main()
