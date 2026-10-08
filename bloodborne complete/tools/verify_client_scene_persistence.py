"""Audit saved client fixture ownership/native data against its immutable copy source."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import uuid

from archive_first_set_scene import inventory
from verify_source_review_saved import typed_diff
from world_io import RegionFile, NbtFile, compound, encode_nbt, read_nbt

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def typed_sha(tag):
    return hashlib.sha256(encode_nbt(NbtFile('', tag))).hexdigest()

def require(condition, message):
    if not condition:
        raise ValueError(message)

def read(path):
    return json.loads(Path(path).read_text(encoding='utf8'))

def resolve(path):
    path = Path(path)
    return (path if path.is_absolute() else ROOT / path).resolve()

def identity(tag):
    return str(uuid.UUID(bytes=struct.pack('>4I', *(value & 0xffffffff for value in tag.value))))

def full_rp_entities(world):
    result = {}
    for path in sorted((world / 'entities').glob('*.mca')):
        if not path.stat().st_size:
            continue
        for chunk in RegionFile(path.read_bytes()).chunks():
            for entity in compound(chunk.nbt().root)['Entities'].value:
                fields = compound(entity)
                if not fields['id'].value.startswith('bloodborne_rp:'):
                    continue
                key = identity(fields['UUID'])
                require(key not in result, 'Duplicate RP UUID in saved entity regions')
                result[key] = entity
    return result

def phase(report_path, artifact_sha):
    report_path = resolve(report_path)
    wrapper = read(report_path)
    require(wrapper['artifact_sha256'] == artifact_sha and wrapper['exit_code'] == 0
            and wrapper['status'] == 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT'
            and wrapper.get('integrated_save_messages_present') is True and 'termination' not in wrapper,
            'Client wrapper is not a normal saved-world PASS for the exact production JAR')
    copy = wrapper['derived_world_copy']
    require(copy['source_unchanged_after_run'] and copy['copy_byte_verification'] == 'PASS', 'Launcher copy/source proof failed')
    source = resolve(copy['source'])
    output = wrapper['client_review_output']
    require(sha(output['path']) == output['sha256'], 'Actual client QA output bytes changed')
    actual = read(output['path'])
    require(actual == output['result'] and actual['normalStopRequested'], 'Client QA output differs from wrapper')
    target = resolve(actual['actualWorldDirectory'])
    for world in (source, target):
        world.relative_to((ROOT / 'build').resolve())
        require((world / 'level.dat').is_file(), 'Isolated world is missing level.dat')
    for row in copy['files']:
        path = source / row['path']
        require(path.stat().st_size == row['bytes'] and sha(path) == row['sha256'], 'Copy source has changed: ' + str(path))
    before_roots, before_bes, _ = inventory(source)
    after_roots, after_bes, _ = inventory(target)
    failures = []
    if len(before_roots) != 68 or after_roots != before_roots:
        failures.append('Exact68 prototype root states did not persist')
    old_helpers = {pos: be for pos, be in before_bes.items() if compound(be)['id'].value == 'bloodborne_dw:composite_cell'}
    new_helpers = {pos: be for pos, be in after_bes.items() if compound(be)['id'].value == 'bloodborne_dw:composite_cell'}
    if old_helpers != new_helpers:
        failures.append('Composite helper/root typed NBT or exact cell set differs')
    owners = []
    for pos, be in old_helpers.items():
        resident = compound(be).get('resident')
        if resident is None:
            continue
        owner = compound(resident)
        if 'uuid' not in owner:
            continue
        same = new_helpers.get(pos) == be
        owners.append({'root': list(pos), 'uuid': identity(owner['uuid']), 'typed_nbt_sha256': typed_sha(be),
                       'state': before_roots.get(pos), 'typed_nbt_equal': same})
    if len(owners) != 12 or not all(row['typed_nbt_equal'] for row in owners):
        failures.append('Expected12 root UUID/full typed NBT records did not persist')
    data_files = []
    relevant = sorted(path for path in (source / 'data').glob('*.dat')
                      if path.name.startswith(('bloodborne_dw', 'bloodborne_rp')))
    saved_data_names = {path.name for path in (target / 'data').glob('*.dat')
                        if path.name.startswith(('bloodborne_dw', 'bloodborne_rp'))}
    if saved_data_names != {path.name for path in relevant}:
        failures.append('Exact DW/RP saved-data file set changed')
    if not any(path.name == 'bloodborne_dw_composite_owners.dat' for path in relevant):
        failures.append('Source composite ledger is missing')
    for old in relevant:
        new = target / 'data' / old.name
        equal = new.is_file() and read_nbt(old) == read_nbt(new)
        if not equal:
            failures.append('DW/RP typed saved data changed: ' + old.name)
        data_files.append({'path': 'data/' + old.name, 'typed_nbt_equal': equal,
                           'before_sha256': sha(old), 'after_sha256': sha(new) if new.exists() else None,
                           'typed_nbt_sha256': typed_sha(read_nbt(old).root)})
    chest_pos = (25, 64, 32)
    chest = before_bes.get(chest_pos)
    same_chest = chest is not None and after_bes.get(chest_pos) == chest
    items = compound(chest)['Items'].value if chest else []
    diamonds = sum(compound(item)['Count'].value for item in items if compound(item)['id'].value == 'minecraft:diamond')
    if not same_chest or diamonds != 7:
        failures.append('Foreign chest full typed NBT/seven diamonds did not persist')
    before_rp, after_rp = full_rp_entities(source), full_rp_entities(target)
    if set(before_rp) != set(after_rp) or {compound(entity)['id'].value for entity in before_rp.values()} != {'bloodborne_rp:tree1', 'bloodborne_rp:hunterlamp'}:
        failures.append('RP tree/lamp exact entity set changed')
    entity_rows = []
    for key, old in sorted(before_rp.items()):
        new = after_rp.get(key)
        old_fields, new_fields = compound(old), compound(new) if new else {}
        identity_equal = all(old_fields[field] == new_fields.get(field) for field in ('id', 'UUID', 'Pos'))
        diffs = []
        typed_diff(old, new, 'entity/' + key, diffs)
        other_diffs = [row for row in diffs if row['path'].rsplit('/', 1)[-1] not in ('id', 'UUID', 'Pos')]
        if not identity_equal:
            failures.append('RP id/UUID/typed Pos changed: ' + key)
        entity_rows.append({'uuid': key, 'id': old_fields['id'].value,
                            'pos': [value.value for value in old_fields['Pos'].value],
                            'id_uuid_pos_typed_equal': identity_equal, 'other_typed_runtime_differences': other_diffs})
    changed_bes = []
    for pos in set(before_bes) | set(after_bes):
        if before_bes.get(pos) != after_bes.get(pos):
            diffs = []
            typed_diff(before_bes.get(pos), after_bes.get(pos), 'block_entity/' + str(pos), diffs)
            changed_bes.append({'pos': list(pos), 'typed_differences': diffs})
    return {'report': str(report_path), 'report_sha256': sha(report_path), 'profile': wrapper['profile'],
            'source': str(source), 'saved_client_world': str(target), 'source_byte_manifest_rechecked': True,
            'status': 'PASS_BOUNDED_CLIENT_SCENE_PERSISTENCE' if not failures else 'FAIL_BOUNDED_CLIENT_SCENE_PERSISTENCE',
            'root_states': len(after_roots), 'root_states_equal': before_roots == after_roots,
            'owners': owners, 'owner_count': len(owners), 'helper_and_root_cell_count': len(old_helpers),
            'all_helper_root_typed_nbt_equal': old_helpers == new_helpers, 'typed_block_entity_differences': changed_bes,
            'data_files': data_files, 'foreign_chest': {'pos': list(chest_pos), 'typed_nbt_equal': same_chest,
                'diamonds': diamonds, 'typed_nbt_sha256': typed_sha(chest) if chest else None},
            'rp_entities': entity_rows, 'failures': failures,
            'limits': 'Bounded architectural/root/helper/ledger/native-chest and RP identity/Pos proof; other RP runtime fields are listed separately. Whole-world bytes/DFU/timers/manual visual acceptance are not claimed equal.'}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--revision', default='V7')
    parser.add_argument('--jar', type=Path, default=ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.2.jar')
    parser.add_argument('--client-report', type=Path, action='append', default=[])
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    revision = args.revision.upper()
    require(re.fullmatch(r'V[1-9][0-9]*', revision), 'Invalid revision')
    reports = args.client_report or [ROOT / ('reports/CLIENT_FIRST_SET_' + name + '_' + revision + '.json')
                                     for name in ('MINIMAL', 'REOPEN', 'FULL')]
    artifact_sha = sha(args.jar)
    phases = [phase(path, artifact_sha) for path in reports]
    result = {'schema': 'dreamwalker-client-scene-persistence-v1',
              'status': 'PASS_BOUNDED_CLIENT_SCENE_PERSISTENCE' if all(row['status'].startswith('PASS') for row in phases) else 'FAIL_BOUNDED_CLIENT_SCENE_PERSISTENCE',
              'artifact_sha256': artifact_sha, 'phases': phases, 'user_review': 'PENDING_USER_REVIEW',
              'whole_world_bytes_equal_claim': False}
    output = resolve(args.report or ROOT / ('reports/CLIENT_SCENE_PERSISTENCE_' + revision + '.json'))
    output.relative_to((ROOT / 'reports').resolve())
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': result['status'], 'phases': [{'profile': row['profile'], 'status': row['status'],
        'roots': row['root_states'], 'owners': row['owner_count'], 'helpers': row['helper_and_root_cell_count'],
        'failures': row['failures']} for row in phases], 'report': str(output)}))
    if not result['status'].startswith('PASS'):
        raise SystemExit(1)

if __name__ == '__main__':
    main()
