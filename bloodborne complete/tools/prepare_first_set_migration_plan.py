"""Bounded source recognition/alignment evidence; never writes a Minecraft save."""
from __future__ import annotations
import argparse,hashlib,json,math,re,zipfile
from pathlib import Path
import world_io as wi
from positional_rng import weighted_index
from record_inputs import sha256

ROOT=Path(__file__).resolve().parents[1]
REPORTS=ROOT/'reports'
SOURCE=Path('C:/Users/vakir/Downloads/bbmc_v16_map (1).zip')
AIR={'minecraft:air','minecraft:cave_air','minecraft:void_air'}
DELTAS=[[-1,0,0],[1,0,0],[0,-1,0],[0,1,0],[0,0,-1],[0,0,1]]
def load(name):return json.loads((REPORTS/name).read_text(encoding='utf-8'))
def typed(tag):
    if isinstance(tag,wi.Tag):return {'type':tag.type,**({'list_type':tag.list_type} if tag.list_type is not None else {}),'value':typed(tag.value)}
    if isinstance(tag,dict):return {key:typed(value) for key,value in tag.items()}
    if isinstance(tag,(list,tuple)):return [typed(value) for value in tag]
    if isinstance(tag,(bytes,bytearray)):return list(tag)
    return tag
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=REPORTS/'FIRST_SET_MIGRATION_PLAN_V8.json')
    parser.add_argument('--review-revision',default='V8')
    args=parser.parse_args();revision=args.review_revision.upper()
    if not re.fullmatch(r'V[1-9][0-9]*',revision):raise ValueError('Review revision must be V followed by a positive integer')
    output=args.output.resolve()
    if output.parent!=REPORTS.resolve():raise ValueError('Plan output must be directly under project reports')
    if revision!='V7' and output==REPORTS/'FIRST_SET_MIGRATION_PLAN.json':raise ValueError('New review plans require a distinct versioned output; historical plan is preserved')
    if revision!='V7' and output.exists():raise ValueError('Never overwrite an existing versioned source plan')
    before=sha256(SOURCE);trees=load('FIRST_SET_SOURCE_EVIDENCE.json')['confirmed_local_tree_assemblies']
    physics_name='SOURCE_PHYSICS_CLIENT_SWEPT_ACTUAL.json' if (REPORTS/'SOURCE_PHYSICS_CLIENT_SWEPT_ACTUAL.json').exists() else 'SOURCE_PHYSICS_CLIENT_ACTUAL.json'
    physics=load(physics_name);measured={tuple(row['pos']):row for row in physics['actual_shapes']}
    joined={row['state']:row for row in load('RESOURCE_AUDIT_WORLD_JOIN.json')['states']}
    fixture=load('FIRST_FIXTURE.json');fixture_terrain={tuple(row['chunk']) for record in fixture['region_chunks'] if record['path'].startswith('region/') for row in record['chunks']}
    regions={};chunks={};instances=[]
    with zipfile.ZipFile(SOURCE) as archive:
        names=set(archive.namelist())
        def chunk(cx,cz):
            if (cx,cz) not in chunks:
                path=f'region/r.{cx//32}.{cz//32}.mca'
                if path not in regions:regions[path]=wi.RegionFile(archive.read(path)) if path in names else None
                stored=regions[path].get_chunk(cx%32,cz%32) if regions[path] else None
                fields=wi.compound(stored.nbt().root) if stored else {}
                sections={wi.compound(s)['Y'].value:wi.section_blocks(s) for s in fields.get('sections',wi.Tag(9,[])).value}
                entities={tuple(wi.compound(e)[key].value for key in ['x','y','z']):e for e in fields.get('block_entities',wi.Tag(9,[])).value}
                chunks[cx,cz]=(sections,entities,stored)
            return chunks[cx,cz]
        def cell(pos):
            x,y,z=pos;sections,entities,stored=chunk(x//16,z//16);section=sections.get(y//16)
            state=wi.block_state_key(section[0][section[1][(y%16)*256+(z%16)*16+x%16]]) if section else 'minecraft:air'
            result={'pos':list(pos),'state':state,'terrain_chunk':[x//16,z//16],'source_chunk_exists':stored is not None,
                'included_in_frozen_source_fixture':(x//16,z//16) in fixture_terrain}
            if tuple(pos) in entities:
                entity=entities[tuple(pos)];raw=wi.encode_nbt(wi.NbtFile('',entity));result['source_block_entity_nbt']=typed(entity);result['source_block_entity_nbt_sha256']=hashlib.sha256(raw).hexdigest()
            if tuple(pos) in measured:result['measured_original_1_18_2_physics']=measured[tuple(pos)]
            return result
        def neighborhood(pos):return [{**cell([pos[i]+delta[i] for i in range(3)]),'offset':delta} for delta in DELTAS]
        def root_context(root,members,required=True):
            observed=cell(root);own={tuple(member['pos']) for member in members}
            classification='AIR' if observed['state'] in AIR else 'OWNED_SOURCE_MEMBER' if tuple(root) in own else 'FOREIGN_NONMEMBER'
            return {'root':observed,'ordinary_root_occupancy':classification,'required_support':required,'below':cell([root[0],root[1]-1,root[2]]),
                'neighbors':neighborhood(root),'ordinary_item_placement_on_original_root':'Requires explicit recognized-source replacement' if classification=='OWNED_SOURCE_MEMBER' else 'Occupied foreign nonmember: do not overwrite' if classification=='FOREIGN_NONMEMBER' else 'Root is free; runtime support/entity/permission checks still required',
                'source_runtime_support_acceptance':'NOT_RUN_ON_ACTUAL_BOUNDED_SOURCE_COORDINATE_FIXTURE'}
        for index,tree in enumerate(trees):
            members=[{**cell(member['pos']),'source_selection':member['source_selection']} for member in tree['source_cell_members']]
            x,z=tree['center_xz'];canonical=[x,118,z];owned_root=[x,119,z]
            signature=lambda value:sorted((p['pos'][0]-value['center_xz'][0],p['pos'][1],p['pos'][2]-value['center_xz'][1],p['model'],p['x'],p['y']) for p in value['selected_original_coordinate_models'])
            instances.append({'key':f'architecture_tree_{index+1}','target_kind':'bloodborne_dw:prototype_tree','source_members':members,'source_member_count':18,
                'source_selected_models':tree['selected_original_coordinate_models'],'descriptor_first_tree_signature_matches':signature(tree)==signature(trees[0]),
                'canonical_descriptor_anchor':root_context(canonical,members),'owned_source_root_candidate':root_context(owned_root,members),
                'required_per_instance_source_alignment':{'root_at':owned_root,'offset_blocks':[0,-1,0],'reason':'Canonical descriptor starts at source Y118 grass. Owned lowest melon is Y119. Offset must affect render/physics together while native grass remains untouched.'},
                'nonmember_overlap_context':tree['occupied_nonmember_cells_inside_visual_AABB'],'source_visual_bounds':tree['transformed_visible_element_bounds_world'],
                'physics_acceptance':'USER_PENDING_NEW_NARROW_STEM_DIFFERS_FROM_MEASURED_SOURCE_WOOL_AND_MELON',
                'migration_readiness':'SOURCE_SHIFT_RUNTIME_IMPLEMENTED_AND_SOURCE_ANCHOR_GAMETEST_PASS; EXACT_MEMBER_TRANSACTION_AND_BOUNDED_SOURCE_QA_PENDING'})
        for key,lower,upper,newroot,rotation in [('door_north',[-93,59,-212],[-93,61,-212],[-93,58,-211],0),
                ('door_east',[-58,76,-113],[-58,78,-113],[-59,75,-113],2)]:
            members=[cell(lower),cell(upper)]
            instances.append({'key':key,'target_kind':'bloodborne_dw:prototype_double_door','source_members':members,'source_member_count':2,
                'paired_model_contract':{'lower':'minecraft:block/aca_door_1','upper':'minecraft:block/aca_door_2','upper_relative':[0,2,0]},
                'target_root_context':root_context(newroot,members),'target_rotation':rotation,
                'world_anchor_shift_blocks':[newroot[i]-lower[i] for i in range(3)],'closed_art_alignment':'Exact source model + header position when descriptor is used at declared integer root shift',
                'physical_opening':'New fixed side panels/header and one central leaf; source carrier stairs did not author an opening state. Left hinge/swing remain an explicit functional proposal pending user review.',
                'migration_readiness':'SOURCE_ROOT_IS_AIR_AND_SOURCE_SUPPORT_RECORDED_RUNTIME_PLACEMENT_PENDING'})
        for key,target,pos,required in [('wood_window','prototype_wood_window',[-32,37,-1120],True),
                ('thin_window','prototype_thin_window',[-121,53,-241],False)]:
            members=[cell(pos)]
            instances.append({'key':key,'target_kind':f'bloodborne_dw:{target}','source_members':members,'source_member_count':1,'target_root_context':root_context(pos,members,required),
                'target_rotation':0,'world_anchor_shift_blocks':[0,0,0],'source_selected_rules':joined[members[0]['state']]['selected_rules'],
                'thin_variant':0 if key=='thin_window' else None,'closed_art_alignment':'Original source NORTH selector at same owned source root',
                'functional_proposal':'Only side panels move; opaque source center stays solid' if key=='wood_window' else 'Source two-sided thin frame stays fixed',
                'migration_readiness':'RECOGNIZED_SOURCE_MEMBER_REPLACEMENT_AND_LEDGER_TRANSITION_REQUIRED'})
        pos=[154,58,-976];members=[cell(pos)];props=joined[members[0]['state']]['properties']
        form={'connections':'manual','rotation':'0','profile':'base','material':'0','up':props['up'],'waterlogged':props['waterlogged'],
            **{key:props[key] for key in ['north','east','south','west']}}
        assert all(props[key] in ['none','low','tall'] for key in ['north','east','south','west'])
        instances.append({'key':'wall','target_kind':'bloodborne_dw:prototype_wall','source_members':members,'source_member_count':1,
            'target_root_context':root_context(pos,members),'stable_source_manual_form':form,'source_selected_rules':joined[members[0]['state']]['selected_rules'],
            'world_anchor_shift_blocks':[0,0,0],'source_below_is_other_wall_not_owned':True,'source_height_overlap_policy':'Keep below native wall. Its source carrier physics may extend above the shared cell boundary; never clear it to satisfy a visual bounding box.',
            'migration_readiness':'SINGLE_OWNED_SOURCE_CELL_REPLACEMENT_NEW_PHYSICS_REQUIRES_RUNTIME_VERIFICATION'})
        roof=load('ROOF_PROTOTYPE_ASSETS.json');pos=[-173,59,-239];members=[cell(pos)]
        rotations=[]
        for facing,source_yaw,rotation,offset in [('north',180,0,[0,-3/16,5/16]),('east',270,2,[-5/16,-3/16,0]),('south',0,4,[0,-3/16,-5/16]),('west',90,6,[5/16,-3/16,0])]:
            rotations.append({'source_facing':facing,'source_yaw':source_yaw,'target_rotation':rotation,'root_at_source_cell':True,'per_instance_world_render_and_physics_compensation_blocks':offset})
        instances.append({'key':'roof','target_kind':'bloodborne_dw:prototype_roof','source_members':members,'source_member_count':1,
            'target_root_context':root_context(pos,members),'target_rotation':0,'source_selected_rules':joined[members[0]['state']]['selected_rules'],
            'normalization':roof['normalization'],'per_source_orientation_alignment':rotations,
            'required_payload_contract':{'field':'SourceShift','nbt_type':'List of three DOUBLE tags','units':'blocks in canonical local axes','canonical_north_value':[0,-3/16,5/16],
                'rotation':'Global ROTATION rotates the XZ shift clockwise exactly once. Apply independently of computed ordinary MountY support seating.',
                'implementation':'CompositeSourceShift; runtime instance/restore/cleanup, distributed collision/selection and client BER use the same vector.',
                'new_build_rule':'Picked/dropped ordinary items strip SourceShift and MountY; they use their canonical new-build anchor.'},
            'migration_readiness':'SOURCE_SHIFT_RUNTIME_IMPLEMENTED_AND_ALL_EIGHT_SOURCE_ANCHOR_ROTATIONS_GAMETEST_PASS; EXACT_MEMBER_TRANSACTION_AND_BOUNDED_SOURCE_QA_PENDING'})
        ladder=[]
        for pair in load('RESOURCE_AUDIT_NEIGHBORHOODS.json')['ladder_pairs']:
            if pair['honey_level']!='1':continue
            visual=cell(pair['pos']);physical=cell(pair['proposed_owner_cell']);assert visual['state']==pair['state'] and physical['state']==pair['actual_adjacent_state'] and pair['pair_matches_expected']
            variant=weighted_index([1,1,1],pair['pos']);offset=pair['expected_climbing_offset'];facing=physical['state'].split('facing=')[1].split(',')[0].split(']')[0]
            support=[physical['pos'][i]-offset[i] for i in range(3)]
            ladder.append({'source_visual_member':visual,'source_invisible_climbing_member':physical,'source_pair_state_matches':True,
                'source_members':[visual,physical],'target_kind':'bloodborne_dw:prototype_ladder','target_root_candidate':physical['pos'],
                'target_physical_facing':facing,'target_art_variant':variant,'source_visual_model':f'minecraft:block/hold/wood_ladder_{variant+2:02}',
                'source_position_rng_frozen':True,'required_source_visual_model_translation_units':[-16*value for value in offset],
                'target_source_clone':True,'target_owned_fixed_backing':visual['pos'],'required_source_clone_art_yaw_offset_degrees':180,
                'canonical_source_clone_art_translation_blocks':[0,0,1],'source_clone_rotation_order':'Cardinal source bake, then art180 and facing-rotated canonical translation, then optional global45; physics receives physical yaw alone.',
                'source_clone_initial_physics':{'climbing_depth_blocks':3/16,'fixed_backing':'Original source beehive full cube at visualCell; no added full cube follows rotation.'},
                'source_visual_facing':pair['facing'],'target_physical_support':cell(support),'source_visual_neighbor_context':neighborhood(visual['pos']),
                'required_runtime_contract':'SourceLadderRuntime.install accepts a typed exact beehive honey1 + native ladder pair. Static UUID-linked backing retains the source beehive collision and typed provenance; the physical root uses opposite facing and an explicit compensated art transform. The support role remains fixed during supported builder rotations. Honey0 caps and other cells remain native.',
                'migration_readiness':'SOURCE_PAIR_RUNTIME_IMPLEMENTED; SEVEN_SOURCE_LADDER_GAMETESTS_PASS_ATTEMPT11_INCLUDING_DURABLE_RELOAD; ACTUAL_BOUNDED_SOURCE_QA_REPORTED_SEPARATELY'})
        assert len(ladder)==34
        cap_cells=[cell(pair['pos']) for pair in load('RESOURCE_AUDIT_NEIGHBORHOODS.json')['ladder_pairs'] if pair['honey_level']=='0']
        result={'schema':'dreamwalker-first-set-migration-plan-v1','status':'SOURCE_RECOGNITION_AND_ALIGNMENT_EVIDENCE_NOT_MIGRATED','source_archive':str(SOURCE),
            'source_sha256_before':before,'source_sha256_after':sha256(SOURCE),'source_fixture':load('FIRST_FIXTURE.json')['outputs']['source_fixture'],
            'coordinate_instances':instances,'ladder_pairs':ladder,'unconsumed_ladder_caps':cap_cells,
            'original_invisible_ladder_texture_proof':load('LADDER_SOURCE_CONTEXT.json')['source_vanilla_ladder_render'],
            'original_client_physics_reference':{'manifest':'reports/SOURCE_REFERENCE_CLIENT_SWEPT.json' if 'SWEPT' in physics_name else 'reports/SOURCE_REFERENCE_CLIENT.json','measured':'reports/'+physics_name,'runtime_mode':physics['runtime_mode'],
                'source_shapes':len(physics['actual_shapes']),'fake_player_movements':physics['actual_fake_player_movements'],'manual_keyboard_gameplay':physics['manual_gameplay'],
                'tree_comparison':'Foliage west gap already passes3m in source. Central wool at Y128 and melon Y119 stop source movement at about1.2m; proposed narrow lower stem does not preserve all source carrier collision. New physical policy and central passage change remain USER_PENDING.'},
            'global_rules':['Bounded members only; no global object frequencies inferred.','Never delete a visual AABB or assume palette states are occupied.','Preserve nonmember native blocks, block entities, source typed NBT and other owners.','Only exact recognized source members may be consumed by an explicit whole-object transaction.','Runtime support, source offsets, collision, permissions and idempotence must be checked on the copied subset before any migration is called PASS.'],
            'rp_tree_entities':'All59 source RP trees remain separate RP entities with original UUID/NBT; not members of architectural trees.',
            'numeric_ids_frozen':False,'source_world_written':False,'migration_gameplay':'NOT_RUN','production_gallery':'NOT_GENERATED'}
        result['review_revision']=revision
        result['runtime_contract']={'wall_state':'Native WallBlock sides none|low|tall and up; frozen source form remains explicit manual, no bool side/post/course keys.',
            'door':'One central leaf with two fixed side panels and fixed source header; closed source coordinates/UV retained; new opening proposal not accepted.',
            'tree':'Migrate exact original variant0; optional lower UV proposal variant1 must never silently replace source art.',
            'thin_window':'Explicit vertical mounting, original source offset retained; GlazingMounted absent so new-placement seating is bypassed.',
            'source_ladder':'Preserve separate34 source pairs/native backing provenance; ordinary new-build placement is a distinct contract.',
            'runtime_migration':'NOT_RUN_FOR_THIS_PLAN','user_acceptance':'PENDING'}
        if revision!='V7':
            frozen_catalogue=json.loads((ROOT/'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf8'))
            types={row['registryId']:row['temporaryId'] for row in frozen_catalogue['entries']}
            result['temporary_type_ids']={row['target_kind']:types[row['target_kind']] for row in instances}
            result['temporary_type_ids']['bloodborne_dw:prototype_ladder']=types['bloodborne_dw:prototype_ladder']
            result['temporary_type_ids_are_final']=False
            result['descriptor_sha256']={path.stem:sha256(path) for path in sorted((ROOT/'src/architecture/resources/bloodborne_dw/composite').glob('prototype_*.json'))}
            for entry in instances:
                if entry['key']=='thin_window':entry['target_mount']='vertical'
                if entry['key'].startswith('architecture_tree'):entry['target_art_variant']=0
        assert result['source_sha256_before']==result['source_sha256_after']
        output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps({'migration_plan':'SOURCE_EVIDENCE_ONLY','tree_members':[18,18],'other_coordinate_instances':len(instances)-2,'ladder_pairs':len(ladder),'unconsumed_caps':len(cap_cells),'source_chunks_read':len(chunks),'world_written':False}))
if __name__=='__main__':main()
