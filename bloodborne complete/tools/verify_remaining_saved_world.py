"""Read-only typed-NBT verification of an isolated remaining-transfer test world.

Run only after the test server stops.  No input-event or visual acceptance is
inferred from saved files.  The source map/gallery cannot be an input: --world
must resolve inside this project's build/, and the plan must assert a fresh
test world.  Region/ledger files are read, hashed, and checked again at the end.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import re
import math
import os
from pathlib import Path
import stat
import uuid

from world_io import (RegionFile, Tag, NbtFile, NbtError, compound, decode_nbt,
                      encode_nbt, section_blocks, TAG_BYTE_ARRAY, TAG_COMPOUND,
                      TAG_DOUBLE, TAG_INT, TAG_INT_ARRAY, TAG_LIST, TAG_STRING)

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build'
PREFIX = 'bloodborne_dw:source_'
CELL = 'bloodborne_dw:composite_cell'


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(value):
    return hashlib.sha256(value).hexdigest()


def checked_path(path, *, file=False):
    path = path.absolute()
    for node in (path, *path.parents):
        if not node.exists():
            continue
        info = node.lstat()
        require(not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 0x400,
                'Reparse/symlink input unsupported: ' + str(node))
    if file:
        require(path.is_file(), 'Regular input file required: ' + str(path))
    return path.resolve()


def world_path(path):
    path = checked_path(path)
    require(path.is_relative_to(BUILD.resolve()) and path.is_dir(), 'World must be an existing isolated project build/ directory')
    require(not any(word in path.as_posix().lower() for word in ('gallery', 'reference-inputs', 'source-reference')),
            'Source map, gallery or reference world is forbidden')
    return path


def field(tag, name, kind):
    value = compound(tag).get(name)
    require(value is not None and value.type == kind, f'{name}: expected NBT tag {kind}')
    return value


def typed(tag):
    if tag.type == TAG_COMPOUND:
        value = {key: typed(value) for key, value in sorted(tag.value.items())}
    elif tag.type == TAG_LIST:
        require(all(row.type == tag.list_type for row in tag.value), 'NBT list contains mixed tag types')
        return {'type': tag.type, 'listType': tag.list_type, 'value': [typed(row) for row in tag.value]}
    elif tag.type == TAG_BYTE_ARRAY:
        value = tag.value.hex()
    else:
        value = tag.value
    return {'type': tag.type, 'value': value}


def typed_hash(tag):
    return sha(json.dumps(typed(tag), sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8'))


def owner(tag):
    identifier = field(tag, 'id', TAG_STRING).value
    root = field(tag, 'root', TAG_INT_ARRAY).value
    identifier_parts = field(tag, 'uuid', TAG_INT_ARRAY).value
    require(len(root) == 3 and len(identifier_parts) == 4, 'Invalid typed owner root/UUID')
    bits = int.from_bytes(b''.join((v & 0xffffffff).to_bytes(4, 'big') for v in identifier_parts), 'big')
    return {'registryId': identifier, 'root': list(root), 'uuid': str(uuid.UUID(int=bits)), 'typedSha256': typed_hash(tag)}


def boxes(tag):
    require(tag.type == TAG_LIST and (not tag.value or tag.list_type == TAG_LIST), 'Cuboids must be a typed list of lists')
    for row in tag.value:
        require(row.type == TAG_LIST and row.list_type == TAG_DOUBLE and len(row.value) == 6, 'Cuboid must have six NBT DOUBLE values')
        values = [v.value for v in row.value]
        require(all(v.type == TAG_DOUBLE and math.isfinite(v.value) for v in row.value), 'Invalid nonfinite/type cuboid')
        require(all(values[i] < values[i + 3] for i in range(3)), 'Degenerate saved cuboid')
    return {'boxes': len(tag.value), 'typedSha256': typed_hash(tag)}


def contribution(tag):
    own = owner(field(tag, 'owner', TAG_COMPOUND))
    root_data = field(tag, 'rootData', TAG_COMPOUND)
    identifier = field(root_data, 'id', TAG_STRING).value
    properties = field(root_data, 'properties', TAG_COMPOUND)
    require(all(v.type == TAG_STRING for v in properties.value.values()), 'Root properties must be NBT STRING values')
    payload = field(root_data, 'payload', TAG_BYTE_ARRAY)
    decoded_payload = decode_nbt(payload.value).root
    require(decoded_payload.type == TAG_COMPOUND, 'Root payload must decode to NBT COMPOUND')
    require(identifier == own['registryId'], 'Owner and rootData registry IDs differ')
    return {'owner': own, 'rootData': {'registryId': identifier,
            'properties': {k: v.value for k, v in sorted(properties.value.items())},
            'payloadBytes': len(payload.value), 'typedPayloadSha256': typed_hash(decoded_payload),
            'typedSha256': typed_hash(root_data)},
            'collision': boxes(field(tag, 'collision', TAG_LIST)),
            'selection': boxes(field(tag, 'selection', TAG_LIST)), 'typedSha256': typed_hash(tag)}


def source_contributions(tag):
    require(tag.type == TAG_LIST and (not tag.value or tag.list_type == TAG_COMPOUND), 'Owners must be NBT compound list')
    result = []
    for row in tag.value:
        if field(field(row, 'owner', TAG_COMPOUND), 'id', TAG_STRING).value.startswith(PREFIX):
            result.append(contribution(row))
    return sorted(result, key=lambda row: (row['owner']['registryId'], row['owner']['root'], row['owner']['uuid']))


def state(tag):
    name = field(tag, 'Name', TAG_STRING).value
    props = tag.value.get('Properties', Tag(TAG_COMPOUND, {}))
    require(props.type == TAG_COMPOUND and all(v.type == TAG_STRING for v in props.value.values()), 'Invalid typed blockstate')
    return {'registryId': name, 'properties': {k: v.value for k, v in sorted(props.value.items())}, 'typedSha256': typed_hash(tag)}


def key(dimension, pos):
    return dimension + ':' + ','.join(map(str, pos))


def dimension(relative):
    parts = relative.parts
    if parts[0] == 'DIM-1':
        return 'minecraft:the_nether'
    if parts[0] == 'DIM1':
        return 'minecraft:the_end'
    if parts[0] == 'dimensions':
        return parts[1] + ':' + '/'.join(parts[2:-2])
    return 'minecraft:overworld'


def walk_saved(world):
    for directory, folders, files in os.walk(world, followlinks=False):
        for folder in folders:
            checked_path(Path(directory) / folder)
        for filename in files:
            path = Path(directory) / filename
            if path.parent.name == 'region' and filename.startswith('r.') and filename.endswith('.mca'):
                yield path, 'region'
            elif path.parent.name == 'data' and filename == 'bloodborne_dw_composite_owners.dat':
                yield path, 'ledger'


def world_files(world):
    """Bind every input copied by run_v11_server.py, excluding session locks."""
    for directory, folders, files in os.walk(world, followlinks=False):
        for folder in folders:
            checked_path(Path(directory) / folder)
        for filename in files:
            if filename != 'session.lock':
                yield checked_path(Path(directory) / filename, file=True)


def verify_offline_qualification(report):
    """Independently bind a narrowly qualified localhost report to its preserved FAIL."""
    binding = report['originalStrictReport']
    original_path = checked_path(Path(binding['path']), file=True)
    require(original_path.is_relative_to(BUILD.resolve()), 'Original strict report must remain under build/')
    original_raw = original_path.read_bytes()
    require(sha(original_raw) == binding['sha256'] and binding['status'] == 'FAIL', 'Original strict FAIL binding changed')
    original = json.loads(original_raw.decode('utf-8-sig'))
    changes = {'status', 'errors', 'verificationDomain', 'originalStrictReport', 'externalAuthentication', 'actualClientRejoin'}
    require({key: value for key, value in report.items() if key not in changes} ==
            {key: value for key, value in original.items() if key not in changes}, 'Qualification changed original runtime/copy fields')
    require(set(report) == set(original) | {'verificationDomain', 'originalStrictReport', 'externalAuthentication', 'actualClientRejoin'},
            'Unexpected offline qualification fields')
    require(original['schema'] == 'dreamwalker-v11-server-v1' and original['status'] == 'FAIL' and original['exit_code'] == 0,
            'Qualification requires the original normally exited strict FAIL')
    require(original['world_startup'] == 'PASS' and not original.get('termination') and not original.get('failure'),
            'Server startup/termination is not valid for offline qualification')
    patterns = [r'^\[[^\]]+\] \[Yggdrasil Key Fetcher/ERROR\]: Failed to request yggdrasil public key$',
                r'^com\.mojang\.authlib\.exceptions\.AuthenticationUnavailableException: Cannot contact authentication server$',
                r'^Caused by: java\.net\.SocketTimeoutException: Connect timed out$']
    errors = original['errors']
    require(len(errors) == 3 and all(re.fullmatch(pattern, line) for pattern, line in zip(patterns, errors)),
            'Only the exact external public-key connection timeout may qualify')
    require(report['errors'] == [] and report['verificationDomain'] ==
            'Offline localhost mod/world runtime only; external authentication availability is separately FAIL', 'Qualified domain differs')
    require(report['externalAuthentication']['status'] == 'FAIL' and report['externalAuthentication']['errors'] == errors,
            'External authentication FAIL must remain explicit')
    run = checked_path(Path(original['run_directory']))
    require(run.is_relative_to(BUILD.resolve()), 'Qualified server run must stay under build/')
    log_path = checked_path(Path(original['evidence']), file=True)
    require(log_path == run / 'launch-console.log' and sha(log_path.read_bytes()) == original['console_sha256'],
            'Qualified server console path/hash differs')
    log = log_path.read_text(encoding='utf-8')
    optional_reach = re.compile(r'^\[[^\]]+\] \[main/WARN\]: Error loading class: com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes \(java\.lang\.ClassNotFoundException: com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes\)$')
    full_errors = [line for line in log.splitlines() if ('/ERROR]' in line or 'Exception' in line) and not optional_reach.fullmatch(line)]
    require(full_errors == errors and 'Saving worlds' in log and 'All dimensions are saved' in log,
            'Full console has a game failure or lacks normal saving')
    properties_path = checked_path(run / 'server.properties', file=True)
    properties = dict(line.split('=', 1) for line in properties_path.read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith(('#', '!')))
    for values in (properties, original['server_properties']):
        require(values.get('online-mode') == 'false' and values.get('server-ip') == '127.0.0.1', 'Qualification is limited to localhost offline mode')
    rejoin_binding = report['actualClientRejoin']
    rejoin_path = checked_path(Path(rejoin_binding['path']), file=True)
    require(rejoin_path.is_relative_to(BUILD.resolve()) and sha(rejoin_path.read_bytes()) == rejoin_binding['sha256'], 'Actual rejoin binding changed')
    rejoin = json.loads(rejoin_path.read_text(encoding='utf-8-sig'))
    require(rejoin_binding['status'] == 'PASS' and rejoin_binding['cases'] == 15 and rejoin['mode'] == 'VERIFY_REJOIN' and rejoin['status'] == 'PASS',
            'Qualified server requires actual fifteen-case read-only client rejoin')
    require(rejoin['productionJarSha256'] == original['artifact_sha256'], 'Rejoined client uses a different production artifact')
    require(len(rejoin['cases']) == 15 and all(case['status'] == 'PASS' and case['expectedOwnerUuid'] == case['actualOwnerUuid'] and
            case['expectedRotation'] == case['actualRotation'] and case['expectedOpen'] == case['actualOpen'] for case in rejoin['cases']),
            'Actual rejoined owner identity/pose failed')
    artifact = checked_path(Path(original['artifact']), file=True)
    require(sha(artifact.read_bytes()) == original['artifact_sha256'], 'Qualified production artifact changed')
    return {'status': 'PASS_OFFLINE_LOCAL_RUNTIME', 'originalStrictStatus': 'FAIL', 'externalAuthenticationStatus': 'FAIL',
            'originalReport': str(original_path), 'originalReportSha256': binding['sha256'], 'actualRejoinReport': str(rejoin_path),
            'actualRejoinReportSha256': rejoin_binding['sha256'], 'actualRejoinCases': 15}


def verify_copy_report(prior, current, world, report_path):
    report_path = checked_path(report_path, file=True)
    report_raw = report_path.read_bytes()
    report = json.loads(report_raw.decode('utf-8-sig'))
    qualification = verify_offline_qualification(report) if 'originalStrictReport' in report else None
    require(report.get('schema') == 'dreamwalker-v11-server-v1' and report.get('status') == 'PASS' and report.get('exit_code') == 0,
            'Derived copy requires a successful stopped server report with expected schema')
    copied = report.get('derived_world_copy')
    require(isinstance(copied, dict) and copied.get('copy_byte_verification') == 'PASS' and copied.get('source_unchanged_after_run') is True,
            'Derived world copy lacks exact verified bytes or source preservation')
    source = world_path(Path(copied['source']))
    require(str(source) == prior.get('world'), 'Copy report source differs from the prior snapshot world')
    run = checked_path(Path(report['run_directory']))
    require(run.is_relative_to(BUILD.resolve()) and world == run / 'isolated-smoke-world' and world != source,
            'Current world is not the fresh isolated server copy in this report')
    require(report.get('artifact_sha256') == prior['artifact']['sha256'] == current['artifact']['sha256'],
            'Source snapshot, reopened server and current snapshot require the same artifact SHA')
    previous = {row['path']: row for row in prior['savedFiles']}
    require(len(previous) == len(prior['savedFiles']), 'Prior snapshot repeats saved file paths')
    inputs = copied.get('files')
    require(isinstance(inputs, list) and inputs, 'Copy report has no verified input files')
    seen = set()
    for row in inputs:
        relative = Path(row['path'])
        require(not relative.is_absolute() and '..' not in relative.parts and relative.name != 'session.lock', 'Unsafe copied input path')
        name = relative.as_posix()
        require(name not in seen and name in previous, 'Copied input is duplicate or absent from prior full saved-file snapshot: ' + name)
        seen.add(name)
        require(row['sha256'] == previous[name]['sha256'] and row['bytes'] == previous[name]['bytes'],
                'Copied input SHA/size differs from prior snapshot: ' + name)
        original = checked_path(source / relative, file=True)
        require(original.is_relative_to(source) and sha(original.read_bytes()) == row['sha256'], 'Original saved input changed after its snapshot: ' + name)
    require(seen == set(previous), 'Copy omitted saved inputs beyond the explicit session.lock exclusion')
    require({path.relative_to(source).as_posix() for path in world_files(source)} == seen,
            'Original source input file set changed after its snapshot')
    return {'status': 'PASS', 'report': str(report_path), 'reportSha256': sha(report_raw),
            'offlineRuntimeQualification': qualification,
            'sourceWorld': str(source), 'reopenedWorld': str(world), 'verifiedInputFiles': len(seen),
            'sameArtifactSha256': current['artifact']['sha256'], 'scope': 'Fresh own test-world byte-identical copy only; all original inputs match the stopped source snapshot.'}


def planned_cases(plan, roots):
    cases = []
    for row in plan['cases']:
        for slot in ('root', 'copyRoot'):
            if slot not in row:
                continue
            own = roots.get(key(row.get('dimension', 'minecraft:overworld'), row[slot]))
            result = {'case': row['case'], 'slot': slot, 'position': row[slot], 'expectedRegistryId': row['registryId'],
                      'expectedVariant': row.get('variant', 0), 'savedStateStatus': 'NOT_INSTALLED',
                      'nativeInputStatus': 'NOT_INFERRED_FROM_SAVED_FILES'}
            if own is not None:
                result.update(savedStateStatus='PASS' if own['state']['registryId'] == row['registryId'] and
                    own['state']['properties'].get('variant') == str(row.get('variant', 0)) else 'FAIL',
                    actualRegistryId=own['state']['registryId'], actualProperties=own['state']['properties'],
                    ownerUuid=own['resident']['uuid'])
            cases.append(result)
    return cases


def capture(world, plan):
    sources, roots, carriers, ledger, files, saved_states = [], {}, {}, {}, {}, {}
    chunks = 0

    def read(path):
        checked_path(path, file=True)
        raw = path.read_bytes()
        files[path] = sha(raw)
        sources.append({'path': path.relative_to(world).as_posix(), 'bytes': len(raw), 'sha256': files[path]})
        return raw

    # Read ledger first so foreign vanilla carrier states can be captured as
    # well as our source roots/helpers when scanning chunk palettes.
    for path, kind in sorted(walk_saved(world), key=lambda row: (row[1] != 'ledger', row[0])):
        dim = dimension(path.relative_to(world))
        raw = read(path)
        if kind == 'ledger':
            document = decode_nbt(raw, compressed='gzip').root
            data = document.value.get('data', document)
            for row in field(data, 'cells', TAG_LIST).value:
                pos = field(row, 'pos', TAG_INT_ARRAY).value
                require(len(pos) == 3, 'Invalid saved ledger cell')
                entries = source_contributions(field(row, 'owners', TAG_LIST))
                if entries:
                    ledger[key(dim, pos)] = {'dimension': dim, 'position': pos, 'owners': entries}
            continue
        region = RegionFile(raw)
        rx, rz = [int(v) * 32 for v in path.stem.split('.')[1:]]
        for chunk in region.chunks():
            chunks += 1
            document = chunk.nbt().root
            root = document.value.get('Level', document)
            cx, cz = root.value.get('xPos', Tag(TAG_INT, rx + chunk.x)).value, root.value.get('zPos', Tag(TAG_INT, rz + chunk.z)).value
            states = {}
            foreign_targets = {tuple(cell['position']) for cell in ledger.values()
                               if cell['dimension'] == dim and cell['position'][0] // 16 == cx and cell['position'][2] // 16 == cz}
            for section in root.value.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                contents = section_blocks(section)
                if contents is None:
                    continue
                palette, indices = contents
                interesting = {i: state(value) for i, value in enumerate(palette)
                               if field(value, 'Name', TAG_STRING).value.startswith(PREFIX) or field(value, 'Name', TAG_STRING).value == CELL}
                if not interesting and not foreign_targets:
                    continue
                sy = section.value['Y'].value
                for i, entry in enumerate(indices):
                    pos = (cx * 16 + (i & 15), sy * 16 + (i >> 8), cz * 16 + ((i >> 4) & 15))
                    if entry in interesting or pos in foreign_targets:
                        states[pos] = interesting.get(entry) or state(palette[entry])
                        saved_states[key(dim, pos)] = states[pos]
            bes = {}
            for be in root.value.get('block_entities', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                pos = tuple(field(be, coord, TAG_INT).value for coord in ('x', 'y', 'z'))
                bes[pos] = be
                entries = source_contributions(be.value['owners']) if 'owners' in be.value else []
                if entries or pos in states:
                    resident = owner(be.value['resident']) if 'resident' in be.value else None
                    carrier = {'dimension': dim, 'position': list(pos), 'blockEntityId': field(be, 'id', TAG_STRING).value,
                               'state': states.get(pos), 'resident': resident, 'owners': entries,
                               'typedPayloadSha256': typed_hash(be.value.get('payload', Tag(TAG_COMPOUND, {})))}
                    carriers[key(dim, pos)] = carrier
                    if pos in states and states[pos]['registryId'].startswith(PREFIX):
                        require(resident is not None and resident['root'] == list(pos) and resident['registryId'] == states[pos]['registryId'],
                                'Invalid/missing source root resident at ' + str(pos))
                        roots[key(dim, pos)] = carrier
            require(all(pos in bes for pos, value in states.items() if value['registryId'].startswith(PREFIX)),
                    'Source blockstate lacks saved block entity in ' + str(path))
            for pos, value in states.items():
                if value['registryId'] == CELL:
                    require(pos in bes and bes[pos].value.get('owners') is not None and bes[pos].value['owners'].value,
                            'Orphan/empty saved helper at ' + str(pos))
    # The reopen helper copies all world files, including level/player/entity
    # metadata.  Hash them too so a later derived copy is fully bound to this
    # snapshot, while semantic comparisons still exclude changing world time.
    for path in sorted(world_files(world)):
        if path not in files:
            read(path)
    for path, expected in files.items():
        require(sha(path.read_bytes()) == expected, 'Saved file changed during capture; stop the test server first: ' + str(path))
    errors = []
    for location, carrier in carriers.items():
        if carrier['owners'] and (location not in ledger or carrier['owners'] != ledger[location]['owners']):
            errors.append('Carrier and persistent ledger differ at ' + location)
    for location, cell in ledger.items():
        if location not in saved_states:
            errors.append('Saved source ledger carrier state is missing at ' + location)
        else:
            cell['carrierState'] = saved_states[location]
        for entry in cell['owners']:
            own = entry['owner']
            source_root = roots.get(key(cell['dimension'], own['root']))
            if source_root is None or source_root['resident']['uuid'] != own['uuid'] or source_root['state']['registryId'] != own['registryId']:
                errors.append('Orphan source owner in ledger at ' + location)
            elif entry['rootData']['properties'] != source_root['state']['properties']:
                errors.append('Saved owner/root properties differ at ' + location)
    for location, own in roots.items():
        resident_entries = [entry for entry in own['owners'] if entry['owner']['uuid'] == own['resident']['uuid']]
        if not resident_entries:
            errors.append('Root lacks its resident contribution at ' + location)
        elif any(entry['rootData']['typedPayloadSha256'] != own['typedPayloadSha256'] for entry in resident_entries):
            errors.append('Root payload differs from resident owner payload at ' + location)
    cases = planned_cases(plan, roots)
    errors += ['Planned slot has wrong saved type/variant: ' + row['case'] + '/' + row['slot'] for row in cases if row['savedStateStatus'] == 'FAIL']
    semantic = {'roots': dict(sorted(roots.items())), 'carriers': dict(sorted(carriers.items())), 'ledger': dict(sorted(ledger.items()))}
    return {'semanticState': semantic, 'semanticSha256': sha(json.dumps(semantic, sort_keys=True, separators=(',', ':')).encode()),
            'plannedCases': cases, 'errors': errors, 'savedFiles': sources,
            'counts': {'chunksRead': chunks, 'sourceRoots': len(roots), 'sourceCarrierCells': len(carriers),
                       'sourceLedgerCells': len(ledger), 'installedPlannedSlots': sum(row['savedStateStatus'] == 'PASS' for row in cases),
                       'notInstalledPlannedSlots': sum(row['savedStateStatus'] == 'NOT_INSTALLED' for row in cases)}}


def self_test():
    def make_own(words):
        return Tag(TAG_COMPOUND, {'id': Tag(TAG_STRING, PREFIX + 'test'), 'root': Tag(TAG_INT_ARRAY, [1, 128, -1]), 'uuid': Tag(TAG_INT_ARRAY, words)})
    assert owner(make_own([0, 0, 0, 1]))['uuid'] == '00000000-0000-0000-0000-000000000001'
    assert typed_hash(make_own([0, 0, 0, 1])) != typed_hash(make_own([0, 0, 0, 2]))
    row = Tag(TAG_LIST, [Tag(TAG_DOUBLE, v) for v in [0, 0, 0, 1, 1, 1]], TAG_DOUBLE)
    assert boxes(Tag(TAG_LIST, [row], TAG_LIST))['boxes'] == 1
    rejected = 0
    for bad in (Tag(TAG_LIST, [Tag(TAG_LIST, [Tag(TAG_INT, 1)] * 6, TAG_INT)], TAG_LIST),
                Tag(TAG_LIST, [Tag(TAG_LIST, [Tag(TAG_DOUBLE, v) for v in [0, 0, 0, 0, 1, 1]], TAG_DOUBLE)], TAG_LIST)):
        try:
            boxes(bad)
        except ValueError:
            rejected += 1
    assert rejected == 2
    original = Tag(TAG_COMPOUND, {'b': Tag(TAG_INT, 2), 'a': Tag(TAG_STRING, 'v')})
    reordered = Tag(TAG_COMPOUND, dict(reversed(list(original.value.items()))))
    assert typed_hash(original) == typed_hash(reordered)
    assert typed_hash(decode_nbt(encode_nbt(NbtFile('', original))).root) == typed_hash(original)
    root_data = Tag(TAG_COMPOUND, {'id': Tag(TAG_STRING, PREFIX + 'test'),
        'properties': Tag(TAG_COMPOUND, {'variant': Tag(TAG_STRING, '0')}),
        'payload': Tag(TAG_BYTE_ARRAY, encode_nbt(NbtFile('', Tag(TAG_COMPOUND, {}))))})
    saved = Tag(TAG_COMPOUND, {'owner': make_own([0, 0, 0, 1]), 'rootData': root_data,
        'collision': Tag(TAG_LIST, [row], TAG_LIST), 'selection': Tag(TAG_LIST, [row], TAG_LIST)})
    summary = contribution(decode_nbt(encode_nbt(NbtFile('', saved))).root)
    assert summary['rootData']['payloadBytes'] == 4 and summary['selection']['boxes'] == 1
    report = {'status': 'PASS', 'checks': 8, 'scope': 'In-memory typed UUID, exact types, degenerate cuboid rejection, canonical compound order and complete typed owner-contribution NBT roundtrip; no world/native input.'}
    (ROOT / 'docs/remaining-transfer/saved-world-verifier-selftest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', type=Path)
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--compare', type=Path)
    parser.add_argument('--verified-copy-report', type=Path,
                        help='Successful run_v11_server.py report proving a fresh byte-identical reopen of the prior saved test world')
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if not args.world or not args.plan or not args.output:
        parser.error('--world, --plan and --output are required')
    if args.verified_copy_report and not args.compare:
        parser.error('--verified-copy-report requires --compare')
    world = world_path(args.world)
    output = checked_path(args.output)
    require(output.is_relative_to(BUILD.resolve()) and not output.is_relative_to(world), 'Snapshot output must be under build/ and outside the read-only world')
    plan_path = checked_path(args.plan, file=True)
    plan_raw = plan_path.read_bytes()
    plan = json.loads(plan_raw.decode('utf-8-sig'))
    require(plan.get('guard') == 'FRESH_FLAT_TEST_WORLD_ONLY' and plan.get('sourceMapOrGalleryUsed') is False,
            'Plan must authorize only a fresh test world; source map/gallery input forbidden')
    artifact = checked_path(Path(plan['productionJar']), file=True)
    artifact_sha = sha(artifact.read_bytes())
    try:
        result = capture(world, plan)
    except (ValueError, OSError, NbtError) as failure:
        result = {'semanticState': {}, 'plannedCases': [], 'savedFiles': [],
                  'errors': [type(failure).__name__ + ': ' + str(failure)],
                  'counts': {'installedPlannedSlots': 0}, 'captureComplete': False}
    if artifact_sha != plan['productionJarSha256']:
        result['errors'].append('Current production artifact SHA differs from the native plan')
    result.update(schema='dreamwalker-remaining-transfer-saved-world-v1',
                  capturedUtc=datetime.now(timezone.utc).isoformat(), world=str(world),
                  plan={'path': str(plan_path), 'sha256': sha(plan_raw)},
                  artifact={'path': str(artifact), 'sha256': artifact_sha, 'planSha256': plan['productionJarSha256']},
                  nativeInputStatus='NOT_INFERRED_FROM_SAVED_FILES')
    if args.compare:
        prior_path = checked_path(args.compare, file=True)
        prior = json.loads(prior_path.read_text(encoding='utf-8-sig'))
        require(prior.get('schema') == result['schema'], 'Prior snapshot schema differs')
        require(prior['artifact']['sha256'] == result['artifact']['sha256'], 'Snapshot comparison requires the same artifact SHA')
        require(prior['plan']['sha256'] == result['plan']['sha256'], 'Snapshot comparison requires the same native plan SHA')
        if prior.get('world') != str(world):
            require(args.verified_copy_report is not None, 'Comparison of different world paths requires an explicit verified copy report')
            result['verifiedCopy'] = verify_copy_report(prior, result, world, args.verified_copy_report)
        elif args.verified_copy_report:
            raise ValueError('A verified derived copy report is only valid for distinct fresh reopen paths')
        same = prior['semanticState'] == result['semanticState']
        result['comparison'] = {'snapshot': str(prior_path), 'snapshotSha256': sha(prior_path.read_bytes()),
                                'typedSavedStateEqual': same, 'status': 'PASS' if same else 'FAIL',
                                'scope': 'All actual source roots, states, resident UUIDs, typed owner contributions and helper/foreign-ledger cells; excludes file timestamps and world time.'}
        if not same:
            result['errors'].append('Typed saved source state changed after real server restart')
    result['status'] = 'FAIL' if result['errors'] else 'PASS' if result['counts']['installedPlannedSlots'] else 'NOT_RUN'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': result['status'], 'counts': result['counts'], 'errors': result['errors']}))
    if result['status'] == 'FAIL':
        raise SystemExit(2)


if __name__ == '__main__':
    main()
