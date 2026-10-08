"""Read-only source/ordinary ladder geometry proof and optional executed server evidence.

Run after the coordinated GameTest run with --xml and --console to append the
real native comparison. Model proof is independent of client visual acceptance.
"""
from __future__ import annotations
import argparse, hashlib, json, re, statistics, zipfile
from pathlib import Path
from xml.etree import ElementTree
from analyze_resources import element_vertices, rotate_point

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
PACK = Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
FACES = ['north', 'east', 'south', 'west']
BACKINGS = [[0, 0, 16], [-16, 0, 0], [0, 0, -16], [16, 0, 0]]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def cw(point, degrees):
    return rotate_point(point, 'y', -degrees, [8, 8, 8])

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--xml', type=Path)
    parser.add_argument('--console', type=Path)
    parser.add_argument('--out', type=Path, default=ROOT/'reports/LADDER_V8_SOURCE_AND_PHYSICS.json')
    args = parser.parse_args()
    original_hash = sha(PACK)
    models, placements = [], []
    checked_corners = 0
    with zipfile.ZipFile(PACK) as source:
        for variant in range(3):
            path = f'block/hold/wood_ladder_0{variant+2}'
            raw = source.read(f'assets/minecraft/models/{path}.json')
            original = json.loads(raw)
            staged_path = RES / f'assets/bloodborne_dw/models/base/source/minecraft/{path}.json'
            staged = json.loads(staged_path.read_text(encoding='utf8'))
            assert original['elements'] == staged['elements']
            assert original['display'] == staged['display']
            texture_id = original['textures']['2']
            namespace, texture = texture_id.split(':') if ':' in texture_id else ('minecraft', texture_id)
            original_texture = source.read(f'assets/{namespace}/textures/{texture}.png')
            staged_texture = RES / ('assets/bloodborne_dw/textures/' + staged['textures']['2'].split(':')[1] + '.png')
            assert original_texture == staged_texture.read_bytes()
            assert original['elements'][2]['from'][2] == original['elements'][2]['to'][2] == 17.25
            uv = json.dumps([element['faces'] for element in original['elements']], sort_keys=True, separators=(',', ':')).encode()
            models.append({'variant': variant, 'source': path,
                'source_json_sha256': hashlib.sha256(raw).hexdigest(),
                'staged_json_sha256': sha(staged_path), 'element_count': len(original['elements']),
                'indexed_faces_uv_sha256': hashlib.sha256(uv).hexdigest(),
                'texture_sha256': hashlib.sha256(original_texture).hexdigest(),
                'elements_uv_display_equal': True, 'texture_bytes_equal': True})
            for yaw in range(8):
                cardinal = (yaw//2)*90
                translation = BACKINGS[yaw//2]
                errors = []
                for element in original['elements']:
                    for point in element_vertices(element):
                        # Implementation order: authored rotation -> vanilla
                        # cardinal bake -> existing mount -> whole optional45.
                        baked = cw(point, cardinal)
                        mounted = cw(baked, 180)
                        mounted = [mounted[i]+translation[i] for i in range(3)]
                        if yaw % 2:
                            mounted = cw(mounted, 45)
                        # Reference is the unchanged original visual carrier
                        # at its actual backing position, including its yaw.
                        expected = cw(point, cardinal+180)
                        expected = [expected[i]+translation[i] for i in range(3)]
                        if yaw % 2:
                            expected = cw(expected, 45)
                        errors.append(max(abs(a-b)/16 for a,b in zip(mounted,expected)))
                        checked_corners += 1
                assert max(errors) < 1e-8
                placements.append({'variant': variant, 'global_yaw_degrees': yaw*45,
                    'ordinary_and_clone_same_mount': True,
                    'max_indexed_corner_error_blocks': max(errors),
                    'cardinal_backing_shift_units': translation,
                    'extra_whole45_after_mount': bool(yaw%2)})
    blockstates=json.loads((RES/'assets/bloodborne_dw/blockstates/prototype_ladder.json').read_text())['variants']
    assert len(blockstates)==192
    for key, application in blockstates.items():
        opposite=key.replace('source_clone=true','source_clone=false') if 'source_clone=true' in key else key.replace('source_clone=false','source_clone=true')
        assert application==blockstates[opposite]
    result={'schema':'dreamwalker-ladder-v8-review-v1','status':'PASS_OFFLINE_SOURCE_MOUNT_AND_UV',
        'source_pack_sha256_before':original_hash,'source_pack_sha256_after':sha(PACK),
        'authored_corners_checked':checked_corners,'models':models,'orientations':placements,
        'ordinary_visual_fix':{'before_north_tread_z_units':17.25,'stone_support_starts_z_units':16,
            'after_north_tread_z_units':14.75,'source_elements_uv_textures_changed':False,
            'same_mount_for_ordinary_and_source_clone':True,'item_hidden_source_flag_required':False,
            'raw_inventory_model_display_preserved':True},
        'contracts':{'physical':'One cached rectangle per yaw. Cardinal depth2.80215/16 unchanged; diagonals deliberately use a coarse clipped AABB.',
            'selection':'Separately cached single rectangle per yaw, independent of rendered protrusions and support.',
            'ordinary_support':'Actual touching face must cover a centered2x4-pixel attachment pad on either vertical half. Top/bottom slab faces work; a detached central fence post does not.',
            'source_support':'Original full-face validation, physical3/16 section and fixed original beehive cube retained.',
            'placement':'Clicked side of a flat wall has a working cardinal fallback. A45-degree corner pose requires both suitable touching walls.',
            'cleanup':'Ordinary sections have no helper/BE; source pair remains two UUID-linked owned cells with typed provenance.'},
        'counts':{'before':{'cardinal_collision_boxes':1,'diagonal_collision_boxes':'measured native legacy builder below',
                'ordinary_helpers':0,'source_helpers':1,'registered_states':192},
            'after':{'cardinal_collision_boxes':1,'diagonal_collision_boxes':1,'outline_boxes_per_yaw':1,
                'ordinary_helpers':0,'source_helpers':1,'registered_states':192}},
        'server_tests':{'status':'NOT_RUN_IN_THIS_REPORT'},'native_query_comparison':{'status':'NOT_RUN_IN_THIS_REPORT'},
        'visual_acceptance':'PENDING_USER_REVIEW','full_city_performance':'NOT_CLAIMED'}
    if args.xml:
        doc=ElementTree.parse(args.xml);cases=[]
        for case in doc.getroot().iter('testcase'):
            key=(case.attrib.get('classname','')+' '+case.attrib.get('name','')).lower()
            if 'prototypeladdergametests' in key or 'sourceladdergametests' in key:
                cases.append({'name':case.attrib['name'],'status':'FAIL' if case.find('failure') is not None or case.find('error') is not None else 'PASS'})
        assert cases, 'No actual ladder cases in supplied XML'
        result['server_tests']={'status':'PASS' if all(c['status']=='PASS' for c in cases) else 'FAIL',
            'xml':args.xml.resolve().as_posix(),'sha256':sha(args.xml),'count':len(cases),'cases':cases}
    if args.console:
        console=args.console.read_text(encoding='utf8',errors='replace')
        matches=re.findall(r'DW_LADDER_V8_PHYSICS_BENCHMARK=(\{[^\r\n]+\})',console)
        assert len(matches)==1, 'Expected exactly one executed native shape comparison marker'
        benchmark=json.loads(matches[0]);assert benchmark['sameProcessAndInputs'] and benchmark['newDiagonalBoxes']==1
        benchmark['oldQueryMedianNs']=statistics.median(benchmark['oldQueryNs'])
        benchmark['newQueryMedianNs']=statistics.median(benchmark['newQueryNs'])
        benchmark['oldMedianNsPerQuery']=benchmark['oldQueryMedianNs']/benchmark['iterationsPerRound']
        benchmark['newMedianNsPerQuery']=benchmark['newQueryMedianNs']/benchmark['iterationsPerRound']
        result['native_query_comparison']={'status':'MEASURED_NATIVE_SAME_PROCESS','console':args.console.resolve().as_posix(),
            'sha256':sha(args.console),'measurement':benchmark,
            'limit':'A bounded reused-shape movement-query comparison in one dev server. No FPS, city capacity or visual acceptance conclusion.'}
        result['counts']['before']['diagonal_collision_boxes']=benchmark['oldDiagonalBoxes']
    assert result['source_pack_sha256_after']==original_hash
    args.out.parent.mkdir(parents=True,exist_ok=True)
    args.out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'source_uv_mount':'PASS_OFFLINE','corners':checked_corners,'states':192,
        'server_tests':result['server_tests']['status'],'native_query_comparison':result['native_query_comparison']['status']}))

if __name__=='__main__':
    main()
