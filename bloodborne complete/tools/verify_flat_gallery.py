"""Read-only independent saved-NBT proof for the newly authored flat gallery.

Checks every exhibit, actual terrain cells, typed owner identity/payload/cuboids,
foreign-carrier ledger contributions, entity exhibits, floor cells and grid gaps.
An optional earlier snapshot establishes unchanged object data after a genuine
production-only reopen. No visual or physical-input acceptance is inferred.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import hashlib
import json
import os
from pathlib import Path
import sys
import uuid

from world_io import RegionFile, Tag, block_state_key, compound, read_nbt, section_blocks
from verify_remaining_saved_world import checked_path, typed_hash, owner, contribution

ROOT = Path(__file__).resolve().parents[1]

def require(value, message):
    if not value:
        raise ValueError(message)

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def plain(tag):
    if tag.type == 10:
        return {key: plain(value) for key, value in tag.value.items()}
    if tag.type == 9:
        return [plain(value) for value in tag.value]
    return list(tag.value) if isinstance(tag.value, bytes) else tag.value

def entity_uuid(tag):
    values = compound(tag).get('UUID')
    if values is None:
        return None
    require(values.type == 11 and len(values.value) == 4, 'Entity UUID must be NBT int-array[4]')
    bits = int.from_bytes(b''.join((part & 0xffffffff).to_bytes(4, 'big') for part in values.value), 'big')
    return str(uuid.UUID(int=bits))

def saved_files(world):
    for directory, folders, filenames in os.walk(world, followlinks=False):
        for name in folders:
            checked_path(Path(directory) / name)
        for name in filenames:
            if name != 'session.lock':
                yield checked_path(Path(directory) / name, file=True)

def platform(row):
    values = row['platform']
    require(len(values) in (4, 5), 'Platform needs [x,z,w,d] or [x,y,z,w,d]')
    return values if len(values) == 4 else [values[0], values[2], values[3], values[4]]

def grid(rows):
    slots = defaultdict(dict)
    rectangles = []
    for row in rows:
        x, z, width, depth = platform(row)
        require(all(isinstance(v, int) for v in (x, z, width, depth)) and width > 0 and depth > 0, 'Invalid platform: ' + row['key'])
        key = (row['row'], row['column'])
        require(key not in slots[row['section']], 'Repeated row/column slot: ' + row['key'])
        slots[row['section']][key] = row
        rectangles.append((x, z, x + width, z + depth, row['key']))
        if row.get('bounds') and row.get('kind') != 'diagnostic' and not row['representation'].startswith('diagnostic_placeholder'):
            bounds = row['bounds']
            require(len(bounds) == 6 and bounds[0] >= x and bounds[2] >= z and bounds[3] <= x + width and bounds[5] <= z + depth,
                    'Object escapes platform: ' + row['key'])
    horizontal = vertical = 0
    for section, section_slots in slots.items():
        for (r, c), row in section_slots.items():
            x, z, w, d = platform(row)
            right = section_slots.get((r, c + 1))
            if right:
                rx, rz, rw, rd = platform(right)
                require(rx - (x + w) == 2 and rz == z, 'Horizontal edge gap/alignment differs: ' + row['key'])
                horizontal += 1
            below = section_slots.get((r + 1, c))
            if below:
                bx, bz, bw, bd = platform(below)
                require(bz - (z + d) == 2 and bx == x, 'Vertical edge gap/alignment differs: ' + row['key'])
                vertical += 1
    # Every pair, including different section grids, must have two cells of space.
    rectangles.sort()
    for i, a in enumerate(rectangles):
        for b in rectangles[i + 1:]:
            if b[0] >= a[2] + 2:
                break
            require(a[2] + 2 <= b[0] or b[2] + 2 <= a[0] or a[3] + 2 <= b[1] or b[3] + 2 <= a[1], 'Platforms overlap or gap <2: ' + a[4] + ' / ' + b[4])
    return {'status': 'PASS', 'horizontalNeighbors': horizontal, 'verticalNeighbors': vertical, 'sectionSlots': {k: len(v) for k, v in slots.items()}}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--compare', type=Path)
    args = parser.parse_args()
    world = checked_path(args.world)
    require(world.is_relative_to((ROOT / 'build').resolve()) and (world / 'level.dat').is_file(), 'Existing new project-build gallery required')
    require('reference-inputs' not in world.as_posix().lower(), 'Original input world is outside scope')
    manifest = read(args.manifest)
    digest = sha(args.production_jar)
    require(manifest['productionJarSha256'] == digest, 'Manifest and exact production JAR differ')
    rows = manifest['exhibits']
    require(rows and len(set(row['key'] for row in rows)) == len(rows), 'Exhibit keys must be unique')
    uuids = [row['uuid'] for row in rows if row.get('uuid')]
    require(len(set(uuids)) == len(uuids), 'Exhibit identities must be unique')
    grid_result = grid(rows)
    level = compound(compound(read_nbt(world / 'level.dat').root)['Data'])
    require(level['DataVersion'].value == 3465, 'Minecraft saved world must be 1.20.1/DataVersion3465')
    dimensions = compound(compound(level['WorldGenSettings'])['dimensions'])
    generator = compound(compound(dimensions['minecraft:overworld'])['generator'])
    require(generator['type'].value == 'minecraft:flat', 'Overworld is not flat')
    require(level['GameType'].value == 1 and level['allowCommands'].value == 1, 'Creative commands must be enabled')
    files = list(saved_files(world))
    before = {str(path.relative_to(world)): sha(path) for path in files}
    samples = {}
    roots = {}
    floor_y = manifest.get('floorY', 63)
    for row in rows:
        x, z, w, d = platform(row)
        if len(row['platform']) == 5:
            require(row['platform'][1] == floor_y, 'Platform floor Y differs from flat plan')
        require(row.get('floor'), 'Manifest must name actual floor block: ' + row['key'])
        for px in range(x, x + w):
            for pz in range(z, z + d):
                samples[(px, floor_y, pz)] = row['floor']
        if row.get('root'):
            roots[tuple(row['root'])] = row
    by_chunk = defaultdict(set)
    for pos in set(samples) | set(roots):
        by_chunk[(pos[0] >> 4, pos[2] >> 4)].add(pos)
    states = {}
    entities = {}
    residents = {}
    signs = {}
    owner_cells = defaultdict(list)
    scanned_chunks = 0
    for path in sorted((world / 'region').glob('r.*.*.mca')):
        for chunk in RegionFile.open(path).chunks():
            nbt = chunk.nbt().root
            data = compound(nbt)
            scanned_chunks += 1
            positions = by_chunk.get((data['xPos'].value, data['zPos'].value), set())
            for section in data.get('sections', Tag(9, [], 10)).value:
                sy = compound(section)['Y'].value
                wanted = [p for p in positions if p[1] >> 4 == sy]
                if not wanted:
                    continue
                decoded = section_blocks(section)
                if decoded is None:
                    continue
                palette, indices = decoded
                for pos in wanted:
                    value = palette[indices[(pos[0] & 15) + 16 * (pos[2] & 15) + 256 * (pos[1] & 15)]]
                    states[pos] = {'registry': compound(value)['Name'].value,
                                   'properties': plain(compound(value).get('Properties', Tag(10, {}))),
                                   'state': block_state_key(value), 'typedSha256': typed_hash(value)}
            for tag in data.get('block_entities', Tag(9, [], 10)).value:
                be = compound(tag)
                pos = tuple(be[key].value for key in ('x', 'y', 'z'))
                if be.get('id', Tag(8, '')).value.endswith(':sign'):
                    signs[pos] = plain(tag)
                if 'resident' in be:
                    own = owner(be['resident'])
                    require(own['uuid'] not in residents, 'Duplicate resident UUID: ' + own['uuid'])
                    residents[own['uuid']] = {'position': list(pos), 'owner': own, 'typedSha256': typed_hash(tag), 'nbt': plain(tag)}
                for entry in be.get('owners', Tag(9, [], 10)).value:
                    value = contribution(entry)
                    owner_cells[value['owner']['uuid']].append({'storage': 'block_entity', 'position': list(pos), 'value': value})
    for path in sorted((world / 'entities').glob('r.*.*.mca')):
        if not path.stat().st_size:
            continue
        for chunk in RegionFile.open(path).chunks():
            for entity in compound(chunk.nbt().root).get('Entities', Tag(9, [], 10)).value:
                key = entity_uuid(entity)
                require(key and key not in entities, 'Missing or duplicated persisted entity UUID')
                entities[key] = {'typedSha256': typed_hash(entity), 'nbt': plain(entity)}
    ledger_path = world / 'data/bloodborne_dw_composite_owners.dat'
    ledger_count = 0
    if ledger_path.is_file():
        ledger = compound(compound(read_nbt(ledger_path).root)['data'])
        for tag in ledger.get('cells', Tag(9, [], 10)).value:
            entry = compound(tag)
            pos = list(entry['pos'].value)
            require(len(pos) == 3, 'Invalid ledger position')
            for owner_tag in entry.get('owners', Tag(9, [], 10)).value:
                value = contribution(owner_tag)
                owner_cells[value['owner']['uuid']].append({'storage': 'foreign_carrier_ledger', 'position': pos, 'value': value})
                ledger_count += 1
    for pos, expected in samples.items():
        require(states.get(pos, {}).get('registry') == expected, f'Saved platform cell differs at {pos}: {states.get(pos)} / {expected}')
    stable = []
    diagnostics = []
    for row in rows:
        representation = row['representation']
        pos = tuple(row['root']) if row.get('root') else None
        px, pz, _, _ = platform(row)
        sign = signs.get((px, floor_y + 1, pz))
        require(sign and sign.get('is_waxed'), 'Saved immutable label missing: ' + row['key'])
        text = [json.loads(message).get('text', '') for message in sign['front_text']['messages']]
        require(text[0] == row['name'][:26], 'Saved Russian/source exhibit name differs: ' + row['key'])
        require(text[1] == ('ID ' + row['id'] if row['id'] else 'Без ID мода'), 'Saved ID label differs: ' + row['key'])
        if row.get('kind') == 'diagnostic' or representation.startswith('diagnostic_placeholder'):
            require(not row.get('uuid'), 'Damaged source placeholder cannot claim a real object UUID')
            require(states.get(pos, {}).get('registry') == 'minecraft:red_stained_glass', 'Damaged-source placeholder marker missing: ' + row['key'])
            diagnostics.append({'key': row['key'], 'status': 'PASS_LABELLED_PLACEHOLDER', 'root': row.get('root')})
            continue
        if row.get('kind') == 'architecture' or representation in ('ordinary_block_item', 'ordinary_native_block_item', 'native_composite_placement_transaction'):
            actual = states.get(pos)
            require(actual and actual['registry'] == row['registry'], 'Persisted root registry differs: ' + row['key'])
            if row.get('placedState'):
                require(actual['properties'] == row['placedState'], 'Saved full state differs from authored state: ' + row['key'])
            for key in ('variant', 'profile', 'open'):
                if key in actual['properties']:
                    expected = str(row[key]).lower()
                    require(actual['properties'][key] == expected, f'Persisted {key} differs: {row["key"]}')
            resident = residents.get(row['uuid'])
            require(resident and resident['position'] == row['root'] and resident['owner']['registryId'] == row['registry'] and resident['owner']['root'] == row['root'], 'Persisted typed resident differs: ' + row['key'])
            cells = sorted(owner_cells[row['uuid']], key=lambda v: (v['position'], v['storage']))
            for cell in cells:
                require(cell['value']['owner']['root'] == row['root'] and cell['value']['owner']['registryId'] == row['registry'], 'Contribution points to another object')
            stable.append({'key': row['key'], 'uuid': row['uuid'], 'root': row['root'], 'state': actual,
                           'residentTypedSha256': resident['typedSha256'], 'ownerCells': cells})
        else:
            actual = entities.get(row['uuid'])
            require(actual, 'Persisted entity exhibit missing: ' + row['key'])
            data = actual['nbt']
            if representation == 'item_frame':
                require(data['id'] == 'minecraft:item_frame' and data.get('Fixed') and data['Item']['id'] == row['item'], 'Framed item differs')
            else:
                require(data['id'] == row['registry'], 'Registered entity exhibit type differs')
            # Entity engine counters are volatile; preserve only authored identity/pose.
            values = {key: data[key] for key in ('id', 'UUID', 'Pos', 'Rotation', 'Item', 'Facing', 'Fixed', 'NoAI', 'PersistenceRequired', 'NoGravity', 'Scale', 'Open', 'Locked', 'DogsVisible', 'VerticalOffset', 'CustomName', 'CustomNameVisible', 'IsOpen', 'AssetId', 'ObjectScale') if key in data}
            stable.append({'key': row['key'], 'uuid': row['uuid'], 'entity': values})
    stable.sort(key=lambda row: row['key'])
    expected_arch = {row['uuid'] for row in rows if row.get('kind') == 'architecture' or row['representation'] in ('ordinary_block_item', 'ordinary_native_block_item', 'native_composite_placement_transaction')}
    require(set(residents) == expected_arch, 'Unplanned or missing architecture resident(s): ' + str(sorted(set(residents) ^ expected_arch)[:10]))
    if args.compare:
        previous = read(args.compare)
        require(previous['status'] == 'PASS' and previous['productionJarSha256'] == digest, 'Comparison snapshot is not valid')
        require(previous['stableExhibits'] == stable, 'Object identity/state/typed owner-cell geometry changed after reopen')
    require(all(path.is_file() and sha(path) == before[str(path.relative_to(world))] for path in files), 'Read-only verification modified an input file')
    result = {'schema': 'dreamwalker-flat-gallery-disk-verification-v1', 'status': 'PASS', 'productionJarSha256': digest,
              'manifestSha256': sha(args.manifest), 'world': str(world), 'flatOverworld': True, 'dataVersion': 3465,
              'verifiedExhibits': len(stable), 'verifiedDiagnostics': len(diagnostics), 'platformCells': len(samples),
              'verifiedRussianIdLabels': len(rows),
              'regionChunksRead': scanned_chunks, 'typedOwnerContributionCells': sum(len(v) for v in owner_cells.values()),
              'foreignCarrierLedgerContributions': ledger_count, 'grid': grid_result,
              'sectionCounts': dict(Counter(row['section'] for row in rows)), 'inputFilesUnchanged': True,
              'stableExhibits': stable, 'diagnostics': diagnostics,
              'productionReopenCompared': bool(args.compare), 'visualAcceptance': 'NOT_RUN', 'physicalMouseKeyboard': 'NOT_RUN'}
    write(args.report, result)

if __name__ == '__main__':
    try:
        main()
    except Exception as failure:
        if '--report' in sys.argv:
            write(Path(sys.argv[sys.argv.index('--report') + 1]), {'schema': 'dreamwalker-flat-gallery-disk-verification-v1', 'status': 'FAIL', 'failure': repr(failure)})
        raise
