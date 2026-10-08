"""Import only the evidenced eighteen-cell local tree; preserve source quads and UV.

This is a new-build prototype, not a city converter. Physical stem is a separate
reviewable proposal pending native1.18 reference measurements; foliage is passable.
"""
import copy
import hashlib
import itertools
import json
import math
from pathlib import Path
import zipfile
from prepare_tree_selection import tree_selection

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
PACK = Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def rotate(point, origin, degrees, axis='y'):
    result = list(point)
    a, b = {'y': (0, 2), 'x': (1, 2), 'z': (0, 1)}[axis]
    x, z = point[a] - origin[a], point[b] - origin[b]
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    # Source element Y is mathematical positive; blockstate clockwise is opposite.
    if axis == 'y':
        result[a], result[b] = origin[a] + c*x + s*z, origin[b] - s*x + c*z
    else:
        result[a], result[b] = origin[a] + c*x - s*z, origin[b] + s*x + c*z
    return result


def main():
    evidence = json.loads((ROOT/'reports/FIRST_SET_SOURCE_EVIDENCE.json').read_text(encoding='utf8'))
    source = evidence['confirmed_local_tree_assemblies'][0]
    origin = source['below_lowest_spine']['pos']
    records = {record['model']: record for record in evidence['tree_atlas_model_records']}
    selected = source['selected_original_coordinate_models']
    assert len(selected) == 18
    assert all(record.get('x', 0) == 0 and not record.get('uvlock', False) for record in selected)
    parts, selection, files, comparisons = [], [], [], []
    with zipfile.ZipFile(PACK) as archive:
        for item in selected:
            name = item['model'].split(':')[1]
            record = records[item['model']]
            raw = archive.read('assets/minecraft/models/' + name + '.json')
            assert hashlib.sha256(raw).hexdigest() == record['source_sha256']
            original = json.loads(raw)
            imported = copy.deepcopy(original)
            assert 'parent' not in imported, 'Explicit parent closure required for new source'
            for key, value in imported.get('textures', {}).items():
                if value.startswith('#'):
                    continue
                texture = value.split(':')[-1]
                target = 'bloodborne_dw:tree_source/' + texture
                imported['textures'][key] = target
                image = archive.read('assets/minecraft/textures/' + texture + '.png')
                output = RES/'assets/bloodborne_dw/textures/tree_source'/ (texture+'.png')
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(image)
                files.append({'path': str(output.relative_to(ROOT)).replace('\\','/'), 'sha256': hashlib.sha256(image).hexdigest(), 'source_bytes_equal': True})
            assert imported['elements'] == original['elements']
            model = 'bloodborne_dw:tree_source/' + name
            alt = 'bloodborne_dw:tree_alt/' + name
            write(RES/'assets/bloodborne_dw/models/tree_source'/(name+'.json'), imported)
            write(RES/'assets/bloodborne_dw/models/tree_alt'/(name+'.json'), {'parent': model})
            offset = [16*(item['pos'][i]-origin[i]) for i in range(3)]
            parts.append({'model':model, 'altModel':alt, 'offset':offset, 'yaw':item.get('y',0), 'pitch':item.get('x',0), 'pivot':[8,8,8]})
            for element in original['elements']:
                assert all(-16 <= coordinate <= 32 for coordinate in element['from']+element['to'])
                points = list(itertools.product(*zip(element['from'],element['to'])))
                rotation = element.get('rotation', {})
                if rotation.get('rescale'):
                    raise ValueError('Explicit source rescale proof required')
                points = [rotate(point,rotation.get('origin',[8,8,8]),rotation.get('angle',0),rotation.get('axis','y')) for point in points]
                transformed = [[rotate(point,[8,8,8],-item.get('y',0))[i]+offset[i] for i in range(3)] for point in points]
                lower, upper = [min(p[i] for p in transformed) for i in range(3)], [max(p[i] for p in transformed) for i in range(3)]
                # Pick only the actual panels; a zero-depth visual quad needs a small ray thickness.
                for i in range(3):
                    if upper[i]-lower[i] < .001:
                        lower[i] -= .125
                        upper[i] += .125
                selection.append({'from':lower,'to':upper})
                comparisons.append({'source_model':item['model'], 'source_pos':item['pos'], 'source_yaw':item.get('y',0), 'offset_units':offset, 'elements_and_faces_uv_equal':True, 'source_coordinates_rebased_only':True})
    physical = [{'from':[6,0,6], 'to':[10,96,10]}]
    descriptor = {'schemaVersion':1,'units':16,'id':'bloodborne_dw:prototype_tree','displayName':'Дерево Bloodborne · цельный прототип','layer':'cutout','openable':False,
        'support':{'offset':[0,-1,0],'required':True},'essentialMask':[[0,0,0]],
        'variants':[{'poses':{'closed':{'parts':parts,'collision':physical,'selection':tree_selection()}}}],
        'sourceEvidence':{'source_canonical_floor_pivot':origin,'source_owned_anchor':[origin[0],origin[1]+1,origin[2]],'source_owner_compensation':[0,-1,0],'members':18,'second_evidenced_tree':evidence['confirmed_local_tree_assemblies'][1]['center_xz'],
            'physical_policy':'Proposed narrow lower stem only (0.25x0.25x6 blocks); branches/crown passable. Actual1.18 measured original melon/wool fullcube stops1.2m and west foliagegap passes3m. Stem continuity/narrowing and passable central crown require user acceptance.','profile_policy':'ALT retained per object; source-model parent fallback until authored profile exists.'}}
    write(RES/'bloodborne_dw/composite/prototype_tree.json',descriptor)
    empty={'elements':[],'textures':{'particle':'bloodborne_dw:tree_source/block/addon/tree_1'}}
    write(RES/'assets/bloodborne_dw/models/block/composite_empty.json',empty)
    for key in ['composite_cell','prototype_tree','prototype_double_door','prototype_wood_window','prototype_thin_window']:
        write(RES/'assets/bloodborne_dw/blockstates'/(key+'.json'), {'variants':{'':{'model':'bloodborne_dw:block/composite_empty'}}})
        if key != 'composite_cell':
            write(RES/'assets/bloodborne_dw/models/item'/(key+'.json'), {'parent':'builtin/entity'})
    report={'schema':'dreamwalker-tree-prototype-v1','status':'PASS_OFFLINE_RESOURCE_TRANSFORMS','source_resource_sha256':hashlib.sha256(PACK.read_bytes()).hexdigest(),
        'source_world_mutated':False,'member_count':18,'source_root':origin,'source_yaw_values':sorted(set(p['yaw'] for p in parts)),'source_pitch_values':[0],
        'source_uvlock':False,'source_model_quads_uv_preserved':all(c['elements_and_faces_uv_equal'] for c in comparisons),
        'native_physics_reference':'reports/SOURCE_REFERENCE_CLIENT.json + reports/SOURCE_PHYSICS_CLIENT_ACTUAL.json','native_reference_scope':'Original Forge40.2.0 + Bloodborne6.0 + Gecko3.0.57 + QA probe; integrated server actual shapes/moves, not all historical modpack interactions.',
        'client_visual_acceptance':'NOT_RUN','source_assembly_migration':'limited fixture owned by root; see source anchor tests; no city conversion',
        'proposed_physical_changes_acceptance':'PENDING_USER_REVIEW','source_owned_anchor':descriptor['sourceEvidence']['source_owned_anchor'],'source_owner_compensation':[0,-1,0],
        'collision_policy':descriptor['sourceEvidence']['physical_policy'],'texture_files':list({f['path']:f for f in files}.values()),'part_comparisons':comparisons}
    write(ROOT/'reports/TREE_PROTOTYPE_RESOURCES.json',report)
    print('PASS: exact18 selected source parts; unchanged elements/UV; byte-exact atlas; root-only reservation; passable crown.')


if __name__ == '__main__':
    main()
