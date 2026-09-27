"""Independent proof of exact retirement and preservation; never writes a world."""
from __future__ import annotations

import argparse
from collections import OrderedDict
import copy
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import stat
import zipfile

from world_io import (RegionFile, Tag, NbtFile, TAG_COMPOUND, TAG_LIST, TAG_LONG,
                      TAG_STRING, block_state_key, compound, encode_nbt, section_blocks)

ROOT = Path(__file__).resolve().parents[1]
APPROVED = ROOT / 'docs/city-compat/missing-model-positions.json'
APPROVED_SHA = '18fba494229775f0d240b1afc6f61762328274e4e311bbf6ee51de9409edf0a0'
PART = 'bloodborne_blocks:architecture_part'
SOURCE_DIGEST = 'c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9'
FROZEN_DIGEST = 'd0bc2543551af6bf6d65b0ecef33eba2fa51ae28b1bcf0485d2c482f5475527e'
HISTORICAL_JAR = ROOT.parent / 'releases/Bloodborne-Blocks/bloodborne-blocks-2.0.1-mc1.20.1.jar'
HISTORICAL_JAR_DIGEST = 'e400442c1711b013dd73d2a7763fc7c34e778af05876d90b69c2bedced3c1212'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def reject_links(path):
    for p in (Path(path).absolute(), *Path(path).absolute().parents):
        if p.is_symlink() or (p.exists() and getattr(p.lstat(), 'st_file_attributes', 0) & 0x400):
            raise ValueError('Linked world path')


def file_manifest(root):
    reject_links(root)
    result = {}
    for p in sorted(Path(root).rglob('*')):
        if p.is_symlink() or getattr(p.lstat(), 'st_file_attributes', 0) & 0x400:
            raise ValueError('Linked world entry')
        if p.is_file():
            result[p.relative_to(root).as_posix()] = file_sha(p)
    return result


def approved():
    data = json.loads(APPROVED.read_bytes())
    if sha(json.dumps(data, sort_keys=True, separators=(',', ':')).encode()) != APPROVED_SHA:
        raise ValueError('Authorization list changed')
    rows = [{'dimension': c['dimension'], 'position': c['pos'], 'before': c['state'],
             'after': 'minecraft:air', 'reason': 'USER_APPROVED_RETIREMENT'}
            for cells in data.values() for c in cells]
    if len(data) != 23 or len(rows) != 33:
        raise ValueError('Authorization count mismatch')
    return sorted(rows, key=lambda r: (r['dimension'], r['position']))


def zip_members(z):
    names, roots, seen = [], [], set()
    for info in z.infolist():
        p = PurePosixPath(info.filename)
        if (not p.parts or p.is_absolute() or '\\' in info.filename or ':' in info.filename or
                any(q in ('.', '..') or q.rstrip('. ') != q for q in p.parts) or
                stat.S_ISLNK(info.external_attr >> 16)):
            raise ValueError('Unsafe source archive entry')
        canonical = p.as_posix().casefold()
        if canonical in seen:
            raise ValueError('Duplicate source archive entry')
        seen.add(canonical)
        if not info.is_dir():
            names.append((p, info))
            if p.name == 'level.dat':
                roots.append(p.parent)
    if len(roots) != 1:
        raise ValueError('Ambiguous world root')
    try:
        files = {p.relative_to(roots[0]).as_posix(): info for p, info in names}
    except ValueError as error:
        raise ValueError('File outside source world root') from error
    for name in files:
        if any(p.as_posix() in files for p in PurePosixPath(name).parents):
            raise ValueError('Source archive file/directory collision')
    return files


def region_location(name):
    p = PurePosixPath(name)
    m = re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca', p.name)
    if not m or p.parent.name != 'region':
        return None
    base = p.parent.parent.as_posix()
    dimension = {'.': 'minecraft:overworld', 'DIM-1': 'minecraft:the_nether',
                 'DIM1': 'minecraft:the_end'}.get(base)
    if dimension is None:
        parts = PurePosixPath(base).parts
        if len(parts) >= 3 and parts[0] == 'dimensions':
            dimension = parts[1] + ':' + '/'.join(parts[2:])
        else:
            raise ValueError('Unknown terrain dimension path')
    return dimension, int(m[1]), int(m[2])


class Reader:
    def __init__(self, names, read):
        self.read = read
        self.regions = {region_location(n): n for n in names if region_location(n) is not None}
        if len(self.regions) != sum(region_location(n) is not None for n in names):
            raise ValueError('Duplicate dimension/region coordinates')
        self.cache = OrderedDict()
        self.chunk_cache = OrderedDict()

    def region(self, name):
        if name not in self.cache:
            self.cache[name] = RegionFile(self.read(name))
            if len(self.cache) > 3:
                self.cache.popitem(last=False)
        self.cache.move_to_end(name)
        return self.cache[name]

    def state(self, dimension, pos):
        x, y, z = pos
        name = self.regions.get((dimension, x//512, z//512))
        if name is None:
            return None
        key = (name, x//16, z//16)
        if key not in self.chunk_cache:
            c = self.region(name).get_chunk(x//16%32, z//16%32)
            self.chunk_cache[key] = compound(c.nbt().root) if c else None
            if len(self.chunk_cache) > 64:
                self.chunk_cache.popitem(last=False)
        self.chunk_cache.move_to_end(key)
        root = self.chunk_cache[key]
        if root is None:
            return None
        for section in root.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
            if compound(section)['Y'].value == y//16:
                values = section_blocks(section)
                return block_state_key(values[0][values[1][(y&15)*256+(z&15)*16+(x&15)]]) if values else 'minecraft:air'
        return 'minecraft:air'


def stripped_nbt(nbt):
    nbt = copy.deepcopy(nbt)
    for section in compound(nbt.root).get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
        fields = compound(section)
        if 'block_states' in fields:
            states = compound(fields['block_states'])
            states.pop('palette', None)
            states.pop('data', None)
    return encode_nbt(nbt)


def sections(root):
    result = {}
    for section in root.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
        y = compound(section)['Y'].value
        if y in result:
            raise ValueError('Duplicate section')
        result[y] = section
    return result


def knowledge():
    from source_mapping_archive import archive
    frozen_path = ROOT / 'docs/pre-production-source-mapping.json.gz'
    if file_sha(frozen_path) != FROZEN_DIGEST or file_sha(HISTORICAL_JAR) != HISTORICAL_JAR_DIGEST:
        raise ValueError('Frozen registry/geometry provenance mismatch')
    frozen = archive(ROOT)
    known = {PART}
    for name in ('definitions.json', 'v2\\definitions.json'):
        known.update('bloodborne_blocks:' + b['id'].split(':')[-1] for b in frozen[name]['blocks'])
    current = {PART}
    inputs = [{'path': str(frozen_path.relative_to(ROOT.parent)), 'sha256': FROZEN_DIGEST},
              {'path': str(HISTORICAL_JAR.relative_to(ROOT.parent)), 'sha256': HISTORICAL_JAR_DIGEST}]
    # Retirement leaves a legacy world. For IDs shared with the new registry,
    # its original geometry is authoritative until whole-owner conversion.
    schemas = []
    with zipfile.ZipFile(HISTORICAL_JAR) as jar:
        for category in ('', 'v2/'):
            defs = json.loads(jar.read('bloodborne_blocks/'+category+'definitions.json'))
            geo = json.loads(jar.read('bloodborne_blocks/'+category+'geometry.json'))
            schemas.append(({b['id']: b for b in defs['blocks']}, geo))
    for category in ('logical', 'city'):
        directory = ROOT / 'src/main/resources/bloodborne_blocks' / category
        doc = json.loads((directory / 'definitions.json').read_bytes())
        geo = json.loads((directory / 'geometry.json').read_bytes())
        schemas.append(({b['id']: b for b in doc['blocks']}, geo))
        inputs.extend({'path': str(p.relative_to(ROOT.parent)), 'sha256': file_sha(p)}
                      for p in (directory / 'definitions.json', directory / 'geometry.json'))
        current.update('bloodborne_blocks:' + b['id'].split(':')[-1] for b in doc['blocks'])
    return known | current, current, schemas, inputs


def owns_offset(schemas, saved_state, offset):
    name, _, suffix = saved_state.partition('[')
    ident = name.split(':', 1)[-1]
    props = dict(pair.split('=', 1) for pair in suffix.rstrip(']').split(',') if pair)
    for definitions, geometry in schemas:
        if ident not in definitions:
            continue
        definition = definitions[ident]
        actual = {**{k: str(v) for k, v in definition.get('default', {}).items()}, **props}
        key = ','.join(k+'='+v for k,v in sorted(actual.items()))
        value = geometry.get('blocks', {}).get(ident, {}).get('states', {}).get(key)
        if value is None:
            return False
        profile = geometry.get('profiles', {}).get(value.get('ref'), value)
        cells = {tuple(map(int, p.split(','))) for p in profile.get('cells', {})}
        reserved = definition.get('kind') == 'door' and actual.get('half') in ('lower', 'upper') and offset == (0, 1 if actual['half'] == 'lower' else -1, 0)
        return offset != (0,0,0) and offset in cells and not reserved
    return False


def unpack_position(value):
    value &= (1 << 64)-1
    x, y, z = value >> 38, value & 4095, (value >> 12) & 0x3ffffff
    return (x-(1<<26) if x >= 1<<25 else x,
            y-4096 if y >= 2048 else y,
            z-(1<<26) if z >= 1<<25 else z)


def verify(source, output, ledger, expected_source_sha256=None):
    source, output, ledger = map(Path, (source, output, ledger))
    if expected_source_sha256 != SOURCE_DIGEST or file_sha(source) != SOURCE_DIGEST:
        raise ValueError('Source ZIP SHA mismatch')
    if not output.is_dir():
        raise ValueError('Missing output world')
    rows = approved()
    target = {(r['dimension'], tuple(r['position'])): r for r in rows}
    proof = json.loads(ledger.read_bytes())
    if (proof.get('schemaVersion') != 1 or proof.get('kind') != 'missing-composite-retirement' or
            proof.get('sourceZipSha256') != expected_source_sha256 or proof.get('approvedListSha256') != APPROVED_SHA or
            proof.get('ledger') != rows or proof.get('worldName') != output.name or
            proof.get('retiredCells') != 33 or proof.get('helperRemovals') != 0):
        raise ValueError('Invalid retirement ledger')
    after_manifest = file_manifest(output)
    changed_cells, changed_chunks = set(), []
    helper_cells, helper_entities, all_names, errors = set(), {}, set(), []
    count_chunks, count_sections, nonterrain = 0, 0, 0
    with zipfile.ZipFile(source) as z:
        members = zip_members(z)
        before_manifest = {name: sha(z.read(info)) for name, info in sorted(members.items())}
        if set(before_manifest) != set(after_manifest):
            raise ValueError('World file membership changed')
        if proof.get('sourceManifest') != before_manifest or proof.get('outputManifest') != after_manifest:
            raise ValueError('File manifests disagree with actual source/output')
        before = Reader(members, lambda n: z.read(members[n]))
        after = Reader(after_manifest, lambda n: (output / n).read_bytes())
        for name in sorted(members):
            location = region_location(name)
            if location is None:
                if before_manifest[name] != after_manifest[name]:
                    raise ValueError('Non-terrain file changed: ' + name)
                nonterrain += 1
                continue
            dimension, rx, rz = location
            br, ar = before.region(name), after.region(name)
            old = {(c.x, c.z): c for c in br.chunks()}
            new = {(c.x, c.z): c for c in ar.chunks()}
            if set(old) != set(new):
                raise ValueError('Chunk membership changed')
            for local, a in old.items():
                b = new[local]
                cx, cz = rx*32+local[0], rz*32+local[1]
                count_chunks += 1
                if (a.timestamp, a.compression) != (b.timestamp, b.compression):
                    raise ValueError('Chunk timestamp/compression changed')
                bn = b.nbt()
                root = compound(bn.root)
                if root['xPos'].value != cx or root['zPos'].value != cz:
                    raise ValueError('Chunk coordinates disagree with region slot')
                bs = sections(root)
                if a.compressed_payload != b.compressed_payload:
                    an = a.nbt()
                    if stripped_nbt(an) != stripped_nbt(bn):
                        raise ValueError('Non-terrain typed chunk/section NBT changed')
                    old_sections = sections(compound(an.root))
                    if set(old_sections) != set(bs):
                        raise ValueError('Section membership changed')
                    chunk_delta = set()
                    for sy, s in old_sections.items():
                        ad, bd = section_blocks(s), section_blocks(bs[sy])
                        if ad is None or bd is None:
                            if s != bs[sy]:
                                raise ValueError('Empty section changed')
                            continue
                        av = [block_state_key(t) for t in ad[0]]
                        bv = [block_state_key(t) for t in bd[0]]
                        section_targets = {key: row for key, row in target.items()
                                           if key[0] == dimension and key[1][0]//16 == cx and key[1][1]//16 == sy and key[1][2]//16 == cz}
                        if not section_targets and s != bs[sy]:
                            raise ValueError('Non-target block-state container changed')
                        for i, (ai, bi) in enumerate(zip(ad[1], bd[1])):
                            p = (dimension, (cx*16+(i&15), sy*16+(i>>8), cz*16+((i>>4)&15)))
                            if av[ai] != bv[bi]:
                                if p not in target or av[ai] != target[p]['before'] or bv[bi] != 'minecraft:air':
                                    raise ValueError('Terrain change outside authorized retirement')
                                chunk_delta.add(p)
                        if section_targets:
                            removed_states = {r['before'] for r in section_targets.values()}
                            expected_palette = [t for t in ad[0] if block_state_key(t) not in removed_states]
                            if 'minecraft:air' not in av:
                                expected_palette.append(Tag(TAG_COMPOUND, {'Name': Tag(TAG_STRING, 'minecraft:air')}))
                            if bd[0] != expected_palette:
                                raise ValueError('Unauthorized palette metadata change')
                    if not chunk_delta:
                        raise ValueError('Unapproved recompression/empty chunk rewrite')
                    changed_cells.update(chunk_delta)
                    changed_chunks.append({'region': name, 'chunk': [cx, cz],
                                           'beforeSha256': sha(a.raw_nbt()), 'afterSha256': sha(b.raw_nbt())})
                for sy, section in bs.items():
                    count_sections += 1
                    state_container = compound(section).get('block_states')
                    if state_container is None:
                        continue
                    palette = compound(state_container).get('palette')
                    if palette is None or not palette.value:
                        raise ValueError('Invalid palette')
                    names = [compound(t)['Name'].value for t in palette.value]
                    all_names.update(names)
                    if PART in names:
                        indices = section_blocks(section)[1]
                        selected = {i for i, n in enumerate(names) if n == PART}
                        for i, ix in enumerate(indices):
                            if ix in selected:
                                helper_cells.add((dimension, (cx*16+(i&15), sy*16+(i>>8), cz*16+((i>>4)&15))))
                for entity in root.get('block_entities', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                    data = compound(entity)
                    if data.get('id', Tag(TAG_STRING, '')).value != PART:
                        continue
                    try:
                        pos = tuple(data[k].value for k in ('x', 'y', 'z'))
                    except KeyError:
                        errors.append({'reason': 'helper_missing_coordinates', 'chunk': [dimension, cx, cz]})
                        continue
                    key = (dimension, pos)
                    if key in helper_entities:
                        errors.append({'reason': 'duplicate_helper_entity', 'position': [dimension, *pos]})
                    helper_entities[key] = data
        if changed_cells != set(target):
            raise ValueError('Not exactly the 33 approved cells were retired')
        sort_chunk = lambda r: (r['region'], r['chunk'])
        if sorted(proof.get('changedChunks', []), key=sort_chunk) != sorted(changed_chunks, key=sort_chunk):
            raise ValueError('Changed-chunk ledger does not match independently read chunks')
        retired_names = {r['before'].split('[', 1)[0] for r in rows}
        remaining = sorted(all_names & retired_names)
        known, current, schemas, knowledge_inputs = knowledge()
        bloodborne = {n for n in all_names if n.startswith('bloodborne_blocks:')}
        unknown = sorted(bloodborne - known)
        for key in sorted(helper_cells | set(helper_entities)):
            entity = helper_entities.get(key)
            reason = None
            root_pos, owner = None, None
            if key not in helper_cells:
                reason = 'helper_entity_without_helper_cell'
            elif entity is None:
                reason = 'helper_cell_without_entity'
            elif entity.get('Owner', Tag(0, None)).type != TAG_STRING or entity.get('Root', Tag(0, None)).type != TAG_LONG:
                reason = 'malformed_helper_ownership'
            else:
                owner = entity['Owner'].value
                root_pos = unpack_position(entity['Root'].value)
                root_state = after.state(key[0], root_pos)
                if root_pos == key[1] or root_state is None or owner == PART or root_state.split('[', 1)[0] != owner:
                    reason = 'missing_or_mismatched_owner'
                elif not owns_offset(schemas, root_state, tuple(key[1][i]-root_pos[i] for i in range(3))):
                    reason = 'helper_outside_owner_state_footprint'
            if reason:
                errors.append({'reason': reason, 'dimension': key[0], 'position': list(key[1]),
                               'owner': owner, 'root': list(root_pos) if root_pos is not None else None,
                               'belongsToRetiredObject': bool(root_pos is not None and (key[0], root_pos) in target and
                                  owner == target[(key[0], root_pos)]['before'].split('[', 1)[0])})
        if file_manifest(output) != after_manifest or file_sha(source) != expected_source_sha256:
            raise ValueError('World changed during verification')
    return {'result': 'PASS' if not errors and not unknown and not remaining else 'FAIL',
            'sourceZipSha256': expected_source_sha256, 'approvedListSha256': APPROVED_SHA,
            'retiredIds': sorted(retired_names), 'retiredCells': len(changed_cells),
            'changedChunks': len(changed_chunks), 'checkedChunks': count_chunks,
            'checkedSections': count_sections, 'remainingRetiredIds': remaining,
            'unknownModPaletteIds': unknown, 'unknownModPaletteIdCount': len(unknown),
            'unknownToCurrentRuntimeIds': sorted(bloodborne-current),
            'unknownToCurrentRuntimeIdCount': len(bloodborne-current),
            'unknownScope': 'frozen legacy + frozen v2 + current registry; runtime separately reported',
            'helperSchema': 'pre-conversion legacy; current-only IDs use current geometry',
            'notRuntimeCompatibilityProof': True,
            'knowledgeInputs': knowledge_inputs,
            'helperCells': len(helper_cells), 'orphanHelpers': len(errors), 'orphans': errors,
            'additionalTerrainChanges': 0, 'additionalDeletions': 0, 'helperRemovals': 0,
            'nonTargetTypedNbtUnchanged': True, 'nonTerrainByteIdentical': True,
            'preservedNonTerrainFiles': nonterrain, 'ledgerSha256': file_sha(ledger)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--ledger', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--expected-source-sha256', required=True)
    args = parser.parse_args()
    # Diagnostic output must never overwrite a world, input or approved ledger.
    for p in (args.output,):
        if args.report.resolve().is_relative_to(p.resolve()):
            parser.error('Verifier report must be outside the world')
    if args.report.resolve() in {args.source.resolve(), args.ledger.resolve(), APPROVED.resolve()}:
        parser.error('Verifier report overlaps an input')
    try:
        result = verify(args.source, args.output, args.ledger, args.expected_source_sha256)
    except (ValueError, OSError, KeyError, TypeError, zipfile.BadZipFile) as error:
        result = {'result': 'FAIL', 'error': str(error)}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps({k: v for k, v in result.items() if not isinstance(v, list)}))
    raise SystemExit(0 if result['result'] == 'PASS' else 1)


if __name__ == '__main__':
    main()
