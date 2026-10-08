"""Prove source visual/climbing anchor transforms at every actual ladder pair.

No models, world cells or numeric IDs are changed.  Tests every authored element
corner after its intrinsic rotation/rescale; the exact same texture/UV remains
bound to the same indexed source element and face.
"""
from __future__ import annotations
import json,math,zipfile
from pathlib import Path
from analyze_resources import element_vertices,rotate_point
from positional_rng import weighted_index
from record_inputs import sha256
ROOT=Path(__file__).resolve().parents[1]
PACK=Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
ANGLES={'north':0,'east':90,'south':180,'west':270}
def cw(point,angle):return rotate_point(point,'y',-angle,[8,8,8])
def main():
    pairs=json.loads((ROOT/'reports/RESOURCE_AUDIT_NEIGHBORHOODS.json').read_text())['ladder_pairs'];records=[];corners=0
    before=sha256(PACK)
    with zipfile.ZipFile(PACK) as pack:
        states=json.loads(pack.read('assets/minecraft/blockstates/beehive.json'))['variants']
        for pair in pairs:
            if pair['honey_level']!='1':continue
            physical=pair['proposed_owner_cell'];source=pair['pos'];visual_face=pair['facing'];physical_face=pair['actual_adjacent_state'].split('facing=')[1].split(',')[0]
            variant=weighted_index([1,1,1],source);selected=states[f'facing={visual_face},honey_level=1'][variant]
            model=json.loads(pack.read('assets/minecraft/models/'+selected['model'].split(':')[-1]+'.json'))
            imported=json.loads((ROOT/'src/architecture/resources/assets/bloodborne_dw/models/base/source/minecraft'/ (selected['model'].split(':')[-1]+'.json')).read_text())
            assert imported['elements']==model['elements'] and imported['display']==model['display']
            source_yaw=selected.get('y',0);physical_yaw=ANGLES[physical_face];shift=[-16*value for value in pair['expected_climbing_offset']]
            max_wrong=max_correct=0;first_wrong=None;count=0
            for index,element in enumerate(model['elements']):
                for corner,point in enumerate(element_vertices(element)):
                    old_local=cw(point,source_yaw);wrong_local=cw(point,physical_yaw)
                    corrected=cw(wrong_local,180);corrected=[corrected[i]+shift[i] for i in range(3)]
                    old_world=[source[i]+old_local[i]/16 for i in range(3)]
                    wrong_world=[physical[i]+wrong_local[i]/16 for i in range(3)]
                    new_world=[physical[i]+corrected[i]/16 for i in range(3)]
                    wrong=max(abs(wrong_world[i]-old_world[i]) for i in range(3));correct=max(abs(new_world[i]-old_world[i]) for i in range(3))
                    max_wrong=max(max_wrong,wrong);max_correct=max(max_correct,correct);count+=1
                    if wrong>1e-8 and first_wrong is None:first_wrong={'element_index':index,'corner_index':corner,'source_world':old_world,'ordinary_wrong_world':wrong_world,'corrected_world':new_world}
            assert max_wrong>1e-8 and max_correct<1e-8
            corners+=count
            records.append({'source_visual_pos':source,'source_physical_pos':physical,'source_visual_facing':visual_face,'source_blockstate_yaw':source_yaw,
                'physical_facing':physical_face,'ordinary_new_blockstate_yaw':physical_yaw,'frozen_art_variant':variant,'source_model':selected['model'],
                'authored_corners_checked':count,'ordinary_moved_root_world_vertex_max_error_blocks':round(max_wrong,10),
                'corrected_world_vertex_max_error_blocks':round(max_correct,10),'first_wrong_correspondence':first_wrong,
                'source_clone_transform':{'after_ordinary_cardinal_bake_art_yaw_offset_clockwise':180,'then_world_local_translation_model_units':shift,
                    'translation_canonical_for_physical_north':[0,0,16],'rotate_translation_with_entire_object_on_builder_turn':True}})
    result={'schema':'dreamwalker-source-ladder-anchor-contract-v1','status':'ALL_SOURCE_WORLD_VERTEX_TRANSFORMS_PROVEN_OFFLINE_CLIENT_RENDER_SEPARATE',
        'source_pack_sha256_before':before,'source_pack_sha256_after':sha256(PACK),'actual_pair_count':len(records),'authored_corners_checked':corners,'pairs':records,
        'checks':{'all34_source_pairs':'PASS','all_intrinsic_rotations_preserved':'PASS','indexed_source_vertices_world_equal_after_proposed_transform':'PASS',
            'original_elements_faces_uv_display_unchanged':'PASS','ordinary_moved_root_art_without_extra_transform':'FAIL_PROVEN','minecraft_render_and_gameplay':'NOT_RUN'},
        'stable_proposal':{'ordinary_placement':'Unchanged normal art/physics contract, source-clone flag false by default.',
            'source_clone':'Same object/item identity; a controlled source-assembly art transform decouples presentation from physical facing. This may be a boolean source-mount property or equivalent per-instance payload, not a new catalog object.',
            'order':'Existing authored intrinsic rotations -> ordinary cardinal bake -> source ArtYawOffset180 -> canonical VisualSourceShift [0,0,16] rotated by physical cardinal facing -> optional whole-assembly45 rotation.',
            'collider':'Physical root stays at original invisible vanilla ladder cell with its actual physical facing. ArtYawOffset is never applied to collision or climbing orientation.',
            'source_members':'Exact visual beehive cell + exact invisible ladder cell. Typed source beehive NBT is preserved. Honey0 caps are independent and never auto-consumed.',
            'support_role':'The original visual cell supplies a native full-face physical backing. Replacing its visible carrier requires an explicitly owned hidden support role preserving original source physics; do not delete it or leave ordinary vanilla beehive art.',
            'whole_object_lifecycle':'Support/helper is part of the same logical owner transaction, with one pick/drop and no stray helper after removal, undo or failure.',
            'diagonal_turn':'Rotate complete model/translation once around the physical root. Implemented source backing stays at its fixed original installation cell; no new cube follows the rotation. The new physical yaw requires all suitable existing backing faces. Cardinal offline art proof does not certify client rendering or diagonal gameplay.',
            'runtime_status':'SourceLadderRuntime is implemented; actual executed server lifecycle/support evidence and client rendering are separately recorded.'},
        'numeric_ids_frozen':False,'source_world_modified':False}
    owned=ROOT/'reports/OWNED_FIRST_SET_SERVER_CHECKS.json'
    if owned.exists():
        execution=json.loads(owned.read_text(encoding='utf-8'));scope=execution['scopes']['source_ladder']
        result['separate_server_runtime_evidence']={'report':str(owned.relative_to(ROOT)),'sha256':sha256(owned),'xml':execution['xml'],'xml_sha256':execution['xml_sha256'],'scope_status':scope['status'],'cases':scope['cases'],'limit':'Executed server cases only; does not imply an actual native client vertex or visual acceptance result.'}
    assert len(records)==34 and result['source_pack_sha256_before']==result['source_pack_sha256_after']
    (ROOT/'reports/LADDER_SOURCE_ANCHOR_CONTRACT.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'source_ladder_anchor_transform':'PASS_OFFLINE','pairs':len(records),'authored_corners':corners,'wrong_ordinary_transform':'FAIL_PROVEN','world_written':False}))
if __name__=='__main__':main()
