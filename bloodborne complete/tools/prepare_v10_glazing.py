"""Bounded V10 glazing correction; legacy source models/descriptors stay readable."""
import copy
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'

def sha(data): return hashlib.sha256(data).hexdigest()
def load(path): return json.loads(path.read_text(encoding='utf8'))
def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')

def main():
    table_path = RES / 'bloodborne_dw/debug_catalogue.json'
    table = load(table_path)
    used = {row['temporaryId']: row['registryId'] for row in table['entries']}
    new_id = 'bloodborne_dw:prototype_glass_window_03'
    assert used.get('90020', new_id) == new_id, '90020 is already reserved'
    original = load(RES / 'bloodborne_dw/composite/prototype_thin_window.json')
    rows = []
    for number in (2, 3):
        source = RES / f'assets/bloodborne_dw/models/base/source/minecraft/block/hold/window_{number:02}.json'
        source_bytes = source.read_bytes()
        model = load(source)
        before = copy.deepcopy(model)
        element = model['elements'][0]
        assert set(element['faces']) == {'north', 'south'}
        assert element['faces']['north']['uv'] == [0.125, 10.25, 5.625, 15.75]
        # Opposite-face winding reverses world U: reverse U only, keeping the
        # exact window02/03 front rectangle of the shared128px source atlas.
        element['faces']['south']['uv'] = [5.625, 10.25, 0.125, 15.75]
        if number == 3:
            assert element['rotation']['angle'] == -45
            element['rotation']['angle'] = 0
        name = f'window_{number:02}'
        target = RES / f'assets/bloodborne_dw/models/base/v10_windows/{name}.json'
        dump(target, model)
        alt_path = f'alt/prototype_windows/thin/{name}' if number == 2 else f'alt/v10_windows/{name}'
        dump(RES / f'assets/bloodborne_dw/models/{alt_path}.json', {'parent': f'bloodborne_dw:base/v10_windows/{name}'})
        descriptor = copy.deepcopy(original)
        descriptor['id'] = f'bloodborne_dw:prototype_glass_window_{number:02}'
        descriptor['displayName'] = f'Тонкое остекление · window{number:02}'
        variant = copy.deepcopy(original['variants'][number - 1])
        low, high = element['from'], element['to']
        bounds = {'from': [16 - high[0], low[1], 16 - high[2]],
                  'to': [16 - low[0], high[1], 16 - low[2]]}
        part = variant['poses']['closed']['parts'][0]
        part['model'] = f'bloodborne_dw:base/v10_windows/{name}'
        part['altModel'] = f'bloodborne_dw:{alt_path}'
        variant['poses']['closed']['collision'] = [copy.deepcopy(bounds)]
        variant['poses']['closed']['selection'] = [copy.deepcopy(bounds)]
        variant['visibleBounds'] = copy.deepcopy(bounds)
        variant['mountedPlaneBounds'] = copy.deepcopy(bounds)
        variant['mountAlignmentYaw'] = 0
        variant['sourceIntrinsicYaw'] = 0
        descriptor['variants'] = [variant]
        descriptor['globalOrientations'] = 4
        descriptor['orientationStepDegrees'] = 90
        descriptor['source'] = {'model': f'minecraft:block/hold/{name}',
            'originalIntrinsicYaw': before['elements'][0]['rotation']['angle'],
            'sourcePngBytesUnchanged': True,
            'authorizedChanges': ['south UV uses front atlas rectangle with reversed U'] +
                (['ordinary intrinsic yaw -45 normalized to0; installed legacy poses retained'] if number == 3 else [])}
        descriptor['variantMeaning'] = 'One independently offered type; cardinal global yaw and three mounts; BASE/ALT are states.'
        descriptor['mountingPolicy'] = 'Initial actual clicked-face seating; persists freely after support removal; separate user vertical offset.'
        descriptor['compatibility'] = 'Legacy90004 installed variant1/2 keeps its saved geometry/UUID/root; pick/drop maps to the corresponding independent cardinal type.'
        descriptor['proposal'] = {'status': 'V10_EXPLICIT_USER_REQUEST_IMPLEMENTED_AWAITING_RUNTIME', 'motion': 'FIXED_GLAZING'}
        dump(RES / f"bloodborne_dw/composite/prototype_glass_window_{number:02}.json", descriptor)
        dump(RES / f'assets/bloodborne_dw/blockstates/prototype_glass_window_{number:02}.json', {'variants': {'': {'model': 'bloodborne_dw:block/composite_empty'}}})
        dump(RES / f'assets/bloodborne_dw/models/item/prototype_glass_window_{number:02}.json', {'parent': 'builtin/entity'})
        assert source.read_bytes() == source_bytes, 'Legacy source model was rewritten'
        rows.append({'temporaryId': '90010' if number == 2 else '90020', 'registryId': descriptor['id'],
            'sourceModelSha256': sha(source_bytes), 'currentModelSha256': sha(target.read_bytes()),
            'frontUv': element['faces']['north']['uv'], 'backUv': element['faces']['south']['uv'],
            'faceCount': 2, 'geometryFrom': low, 'geometryTo': high,
            'sourceIntrinsicYaw': before['elements'][0]['rotation']['angle'], 'ordinaryIntrinsicYaw': element['rotation']['angle'],
            'physicalPrisms': 1, 'selectionPrisms': 1, 'ordinaryYawDegrees': [0, 90, 180, 270]})
    if '90020' not in used:
        table['entries'].append({'temporaryId': '90020', 'finalId': None, 'kind': 'architecture',
            'name': 'Тонкое остекление · window03', 'registryId': new_id, 'aliases': [new_id]})
    for row in table['stateAliases']:
        if row['registryId'] == 'bloodborne_dw:prototype_thin_window' and row['property'] == 'variant' and row['value'] == '2':
            row['canonicalRegistryId'] = new_id
    for row in table['entries']:
        if row['temporaryId'] in ('90008', '90009'): row['name'] = 'Строительный инструмент'
    dump(table_path, table)
    dump(ROOT / 'reports/V10_GLAZING_TYPE_ASSETS.json', {'schema': 'dreamwalker-v10-glazing-assets-v1',
        'status': 'STATIC_SOURCE_AND_UV_ASSERTIONS_PASS_RUNTIME_PENDING', 'baselineReservedEntries': len(used),
        'appendOnlyNewTemporaryId': '90020', 'rows': rows, 'legacyDescriptorUnchanged': True,
        'old90004Variant2PickDrop': new_id, 'legacyInstalledPoseMigration': 'NOT_APPLIED',
        'manualVisualAcceptance': 'PENDING', 'finalNumericIds': 'UNASSIGNED'})
    print('PASS bounded V10 glazing: exact source front rectangle on both faces; independent90020; legacy resources unchanged.')

if __name__ == '__main__': main()
