"""Read-only art audit of screenshot5's flat composite lower seam and RP tree1."""
import hashlib
import json
from pathlib import Path
import zipfile
ROOT=Path(__file__).resolve().parents[1]
PACK=Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
SCREEN=Path('C:/Users/vakir/AppData/Local/Temp/codex-clipboard-96214bf8-c440-4627-b2ac-f23a37db7b27.png')
def digest(data):return hashlib.sha256(data).hexdigest()
def fingerprint(value):return digest(json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode('utf8'))
def main():
    descriptor_path=ROOT/'src/architecture/resources/bloodborne_dw/composite/prototype_tree.json'
    descriptor=json.loads(descriptor_path.read_text(encoding='utf8'))
    parts=descriptor['variants'][0]['poses']['closed']['parts']
    evidence=json.loads((ROOT/'reports/FIRST_SET_SOURCE_EVIDENCE.json').read_text(encoding='utf8'))
    assembly=evidence['confirmed_local_tree_assemblies'][0]
    source_parts=assembly['selected_original_coordinate_models'];origin=assembly['below_lowest_spine']['pos']
    rows=[];upper=[]
    with zipfile.ZipFile(PACK) as source:
        for part,original in zip(parts,source_parts):
            local=original['model'].split(':',1)[1]
            source_path='assets/minecraft/models/'+local+'.json'
            raw=source.read(source_path);art=json.loads(raw)
            actual_path=ROOT/'src/architecture/resources/assets/bloodborne_dw/models/tree_source'/(local+'.json')
            actual=json.loads(actual_path.read_text(encoding='utf8'))
            expected_offset=[16*(original['pos'][axis]-origin[axis])for axis in range(3)]
            assert part['offset']==expected_offset and part.get('yaw',0)==original.get('y',0) and part.get('pitch',0)==original.get('x',0)
            assert part.get('pivot')==[8,8,8] and actual['elements']==art['elements']
            assert actual.get('texture_size')==art.get('texture_size')
            textures=[]
            for key,value in art['textures'].items():
                if value.startswith('#'):assert actual['textures'][key]==value;continue
                local_texture=value.split(':')[-1]
                source_bytes=source.read('assets/minecraft/textures/'+local_texture+'.png')
                copy=ROOT/'src/architecture/resources/assets/bloodborne_dw/textures/tree_source'/(local_texture+'.png')
                assert source_bytes==copy.read_bytes()
                textures.append({'source_texture':local_texture,'source_and_imported_sha256':digest(source_bytes)})
            row={'source_model':original['model'],'source_model_sha256':digest(raw),'imported_model_sha256':digest(actual_path.read_bytes()),
                'source_pos':original['pos'],'offset_units':part['offset'],'yaw':part.get('yaw',0),'pitch':part.get('pitch',0),
                'elements_uv_equal':True,'author_geometry_uv_fingerprint':fingerprint(art['elements']),'textures':textures,
                'world_bounds_relative_to_floor_units':{'from':[min(e['from'][i]for e in art['elements'])+part['offset'][i]for i in range(3)],
                    'to':[max(e['to'][i]for e in art['elements'])+part['offset'][i]for i in range(3)]} if part.get('yaw',0)==0 else None,
                'bounds_note':'Bounds provided only for unrotated spine parts; transformed branch bounds are not needed for this lower-seam comparison.'}
            rows.append(row)
            if not original['model'].endswith('/melon'):upper.append({'part':part,'geometry_uv_fingerprint':row['author_geometry_uv_fingerprint'],'textures':textures})
        lower=json.loads(source.read('assets/minecraft/models/block/melon.json'))
        authored_base=json.loads(source.read('assets/minecraft/models/block/white_wool.json'))
        first_upper=json.loads(source.read('assets/minecraft/models/block/orange_wool.json'))
        assert all(a['from']==b['from']and a['to']==b['to']and a['rotation']==b['rotation']for a,b in zip(lower['elements'],authored_base['elements'][:2]))
        white={'model':'minecraft:block/white_wool','source_model_sha256':digest(source.read('assets/minecraft/models/block/white_wool.json')),
            'first_two_elements':authored_base['elements'][:2],'remaining_elements_not_selected':len(authored_base['elements'])-2,
            'authored_candidate_base_uv':[[7.9375,13,10.9375,16],[10.9375,13,7.9375,16]],
            'candidate_atlas_pixel_rect':[127,208,175,256],
            'source_membership':'NOT a member of the frozen18-cell local assembly; do not silently claim it was originally placed there.'}
    catalog=json.loads((ROOT/'src/rp/resources/assets/bloodborne_rp/catalog.json').read_text(encoding='utf8'))
    rp=catalog['tree1'];geo_path=ROOT/'src/rp/resources/assets/bloodborne_rp'/rp['model'];geo=json.loads(geo_path.read_text(encoding='utf8'))
    cubes=[cube for geometry in geo['minecraft:geometry']for bone in geometry['bones']for cube in bone.get('cubes',[])]
    volumetric=sum(all(value>0 for value in cube['size'])for cube in cubes)
    lower_rows=[row for row in rows if row['source_model'].endswith('/melon')]
    assert len(rows)==18 and len(lower_rows)==2 and len(upper)==16
    has_proposal=len(descriptor['variants'])>1 and descriptor['sourceEvidence'].get('lowerProposal',{}).get('status')=='PROPOSED_LOWER_UV_ONLY_NOT_ACCEPTED'
    report={'schema':'dreamwalker-flat-tree-lower-review-audit-v1','status':'PASS_SOURCE_PARITY_WITH_SOURCE_INHERITED_ART_SEAM',
        'scope':'Read-only original variant0 source diagnosis; optional variant1, if present, is a separately labelled local proposal.',
        'screenshot':str(SCREEN),'screenshot_sha256':digest(SCREEN.read_bytes()),'source_pack':str(PACK),'source_pack_sha256':digest(PACK.read_bytes()),
        'flat_type':{'registry':'bloodborne_dw:prototype_tree','temporary_type_id':'90005','art_variant':0,'source_members':18,
            'descriptor_sha256':digest(descriptor_path.read_bytes()),'art_parts_fingerprint':fingerprint(parts)},
        'lower_members':lower_rows,'all18_source_art_comparisons':rows,
        'lower_original_elements':lower['elements'],'lower_texture_pixel_rects':[[59,0,107,48],[60,0,108,48]],
        'first_accepted_upper_model':'minecraft:block/orange_wool','first_upper_uv_faces':first_upper['elements'],
        'first_upper_tree_atlas_pixel_rect':[127,160,175,208],
        'lower_transform_verdict':'Exact source poses, scale1, original zero intrinsic rotations, yaw0, byte-exact texture, unchanged face UV; first segment0..3blocks, repeated second3..6blocks, first upper6..9blocks. No import variant/offset/yaw/scale error found.',
        'diagnosis':'Both lower source members sample the atlas top-left small trunk sprite; upper orange/magenta/light_blue spine samples a separate main tree sprite. Original map already uses two melon members, so repeating that lower sprite and the seam are source-inherited. Geometry planes touch; mismatch is art/UV segment choice, not a physical gap.',
        'author_existing_base_candidate':white,
        'minimal_local_proposal':{'status':'GENERATED_OPTIONAL_VARIANT1_NOT_ACCEPTED'if has_proposal else'PROPOSED_NOT_APPLIED','scope':'Only the two lower melon-derived parts; keep all16 upper part transforms/models/UV/textures and all RPtree1 assets unchanged.',
            'preferred_review_example':'Use only white_wool first two authored stem quads/UV for a lower-specific wrapper, preserving the top junction at localY96. Because the existing lower span is6blocks but authored base tile spans3, any extension/stretch/repetition must be explicitly labelled a local proposal; source does not prove a ready six-block seamless base.',
            'source_migration_policy':'Keep original source placement/art as BASE provenance unless a separately accepted reviewed correction is explicitly selected; no silent source-anchor or upper-art changes.',
            'new_art_pixels_created':False,'production_resources_changed':has_proposal,
            'separate_generated_proposal_evidence':'reports/TREE_LOWER_PROPOSAL_INDEPENDENT.json'if has_proposal else None},
        'accepted_upper_art_fingerprint':fingerprint(upper),'accepted_upper_part_count':16,
        'accepted_volumetric_rp_tree':{'registry':'bloodborne_rp:tree1','model':rp['model'],'model_sha256':digest(geo_path.read_bytes()),
            'texture':rp['texture'],'texture_sha256':digest((ROOT/'src/rp/resources/assets/bloodborne_rp'/rp['texture']).read_bytes()),
            'cube_count':len(cubes),'nonzero_xyz_cube_count':volumetric,'scale':rp['scale'],'different_from_flat_composite':True,'changed':False},
        'art_modified_by_this_readonly_audit':False,'optional_proposed_variant_present':has_proposal,
        'world_modified':False,'manual_visual_acceptance':'PENDING_USER_REVIEW',
        'limits':'Screenshot is associated by the user with flat tree; it has no coordinate/type overlay. Composite source/scene and atlas analysis support attribution; no live screenshot UUID was recovered.'}
    target=ROOT/'reports/FLAT_TREE_LOWER_REVIEW_DIAGNOSIS.json';target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':report['status'],'lower_model':'minecraft:block/melon','upper_parts_preserved':16,'rp_volumetric_cubes':volumetric,'report':str(target)}))
if __name__=='__main__':main()
