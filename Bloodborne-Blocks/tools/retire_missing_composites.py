"""User-authorized, exact-cell retirement on a new copy of an immutable ZIP.

This tool does not reconstruct models, invoke city recovery, or remove helpers.
The ledger is outside the world. Existing outputs are read-only idempotency
checks, never an invitation to overwrite a world or resume a partial write.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import stat
import zipfile

from world_io import (RegionFile, Tag, TAG_COMPOUND, TAG_LIST, TAG_LONG_ARRAY,
                      TAG_STRING, block_state_key, compound, encode_nbt,
                      pack_palette_indices, section_blocks)

ROOT = Path(__file__).resolve().parents[1]
APPROVED_LIST = ROOT / 'docs/city-compat/missing-model-positions.json'
APPROVED_DIGEST = '18fba494229775f0d240b1afc6f61762328274e4e311bbf6ee51de9409edf0a0'
SOURCE_DIGEST = 'c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9'
REASON = 'USER_APPROVED_RETIREMENT'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_hash(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def approved_rows(path=APPROVED_LIST):
    data = json.loads(Path(path).read_bytes())
    canonical = json.dumps(data, sort_keys=True, separators=(',', ':')).encode()
    if digest(canonical) != APPROVED_DIGEST:
        raise ValueError('Approved 23-ID/33-cell list differs from user authorization')
    rows, seen = [], set()
    if not isinstance(data, dict) or len(data) != 23:
        raise ValueError('Expected exactly 23 authorized IDs')
    for ident, cells in data.items():
        if not re.fullmatch(r'm_[0-9a-f]{16}', ident):
            raise ValueError('Invalid authorized composite ID')
        for cell in cells:
            p, dim, before = cell['pos'], cell['dimension'], cell['state']
            if (dim != 'minecraft:overworld' or len(p) != 3 or
                    any(type(n) is not int for n in p) or
                    before.split('[', 1)[0] != 'bloodborne_blocks:' + ident):
                raise ValueError('Invalid namespace/dimension/position/state')
            key = (dim, *p)
            if key in seen:
                raise ValueError('Duplicate authorized position')
            seen.add(key)
            rows.append({'dimension': dim, 'position': p, 'before': before,
                         'after': 'minecraft:air', 'reason': REASON})
    if len(rows) != 33:
        raise ValueError('Expected exactly 33 authorized cells')
    return sorted(rows, key=lambda r: (r['dimension'], r['position']))


def archive_files(archive):
    """Validate every path before extraction, including Windows aliases."""
    infos = archive.infolist()
    seen = set()
    for info in infos:
        name = info.filename
        p = PurePosixPath(name)
        if (not p.parts or p.is_absolute() or '\\' in name or ':' in name or
                any(part in ('.', '..') or part.rstrip('. ') != part for part in p.parts) or
                stat.S_ISLNK(info.external_attr >> 16)):
            raise ValueError('Unsafe ZIP member')
        for part in p.parts:
            if part.split('.', 1)[0].upper() in {'CON', 'PRN', 'AUX', 'NUL', *('COM'+str(i) for i in range(1,10)), *('LPT'+str(i) for i in range(1,10))}:
                raise ValueError('Reserved ZIP member path')
        key = p.as_posix().casefold()
        if key in seen:
            raise ValueError('Duplicate/case-colliding ZIP member')
        seen.add(key)
    levels = [PurePosixPath(i.filename).parent for i in infos
              if not i.is_dir() and PurePosixPath(i.filename).name == 'level.dat']
    if len(levels) != 1:
        raise ValueError('ZIP must contain one world root')
    prefix = levels[0]
    files = {}
    for info in infos:
        if info.is_dir():
            continue
        try:
            rel = PurePosixPath(info.filename).relative_to(prefix).as_posix()
        except ValueError as error:
            raise ValueError('ZIP contains files outside the world root') from error
        files[rel] = info
    names = set(files)
    for name in names:
        if any(p.as_posix() in names for p in PurePosixPath(name).parents if p.as_posix() != '.'):
            raise ValueError('ZIP file/directory collision')
    return files


def plan_changes(archive, files, rows):
    """Validate all cells/NBT, then prepare replacements solely in memory."""
    by_region = defaultdict(lambda: defaultdict(list))
    for row in rows:
        x, _, z = row['position']
        by_region[f'region/r.{x//512}.{z//512}.mca'][(x//16, z//16)].append(row)
    changes, chunks = {}, []
    for name, groups in sorted(by_region.items()):
        if name not in files:
            raise ValueError('Missing target region: ' + name)
        region = RegionFile(archive.read(files[name]))
        for (cx, cz), expected in sorted(groups.items()):
            stored = region.get_chunk(cx % 32, cz % 32)
            if stored is None:
                raise ValueError('Missing target chunk')
            raw = stored.raw_nbt()
            nbt = stored.nbt()
            if encode_nbt(nbt) != raw:
                raise ValueError('Target NBT cannot be round-tripped without loss')
            root = compound(nbt.root)
            if root['xPos'].value != cx or root['zPos'].value != cz:
                raise ValueError('Chunk coordinates disagree with region slot')
            targets = {tuple(r['position']) for r in expected}
            for field in ('block_entities', 'block_ticks', 'fluid_ticks'):
                for tag in root.get(field, Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                    obj = compound(tag)
                    if all(k in obj for k in ('x', 'y', 'z')) and tuple(obj[k].value for k in ('x', 'y', 'z')) in targets:
                        raise ValueError('Target has block entity or scheduled tick; refusing to discard NBT')
            sections = {}
            for tag in root.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                y = compound(tag)['Y'].value
                if y in sections:
                    raise ValueError('Duplicate section Y')
                sections[y] = tag
            by_section = defaultdict(list)
            for row in expected:
                by_section[row['position'][1]//16].append(row)
            for sy, selected in by_section.items():
                section = sections.get(sy)
                data = section_blocks(section) if section else None
                if data is None:
                    raise ValueError('Missing target block-state section')
                palette, indices = data
                before_values = [block_state_key(palette[i]) for i in indices]
                retired_indices = set()
                for row in selected:
                    x, y, z = row['position']
                    i = (y & 15)*256 + (z & 15)*16 + (x & 15)
                    if block_state_key(palette[indices[i]]) != row['before']:
                        raise ValueError('Expected state mismatch at ' + str(row['position']))
                    retired_indices.add(indices[i])
                air = next((i for i, tag in enumerate(palette) if block_state_key(tag) == 'minecraft:air'), None)
                if air is None:
                    air = len(palette)
                    palette.append(Tag(TAG_COMPOUND, {'Name': Tag(TAG_STRING, 'minecraft:air')}))
                for row in selected:
                    x, y, z = row['position']
                    indices[(y & 15)*256 + (z & 15)*16 + (x & 15)] = air
                if retired_indices & set(indices):
                    raise ValueError('Retired palette state also occurs outside approved positions')
                # Remove only now-unused retired states; preserve other entries/order.
                retained = [i for i in range(len(palette)) if i not in retired_indices]
                mapping = {old: new for new, old in enumerate(retained)}
                states = compound(compound(section)['block_states'])
                states['palette'] = Tag(TAG_LIST, [palette[i] for i in retained], TAG_COMPOUND)
                packed = pack_palette_indices([mapping[i] for i in indices], len(retained))
                if packed:
                    states['data'] = Tag(TAG_LONG_ARRAY, packed)
                else:
                    states.pop('data', None)
                after_palette, after_indices = section_blocks(section)
                after_values = [block_state_key(after_palette[i]) for i in after_indices]
                expected_delta = {(r['position'][1]&15)*256+(r['position'][2]&15)*16+(r['position'][0]&15) for r in selected}
                if {i for i, (a, b) in enumerate(zip(before_values, after_values)) if a != b} != expected_delta:
                    raise ValueError('Semantic delta differs from the approved cell set')
            region.set_chunk(cx % 32, cz % 32, nbt, compression=stored.compression, timestamp=stored.timestamp)
            rewritten = region.get_chunk(cx % 32, cz % 32)
            chunks.append({'region': name, 'chunk': [cx, cz],
                           'beforeSha256': digest(raw), 'afterSha256': digest(rewritten.raw_nbt())})
        changes[name] = region.to_bytes()
    return changes, chunks


def tree_manifest(root):
    result = {}
    for path in sorted(root.rglob('*')):
        if path.is_symlink() or getattr(path.lstat(), 'st_file_attributes', 0) & 0x400:
            raise ValueError('Links are not allowed in an output world')
        if path.is_file():
            result[path.relative_to(root).as_posix()] = file_hash(path)
    return result


def reject_link_components(path):
    path = Path(path).absolute()
    for part in (path, *path.parents):
        if part.is_symlink() or (part.exists() and getattr(part.lstat(), 'st_file_attributes', 0) & 0x400):
            raise ValueError('Links/junctions are not allowed in source, output or ledger paths')


def retire(source, output, ledger_path, *, expected_source_sha256=SOURCE_DIGEST, list_path=APPROVED_LIST):
    for path in (source, output, ledger_path):
        reject_link_components(path)
    source, output, ledger_path = Path(source).resolve(), Path(output).resolve(), Path(ledger_path).resolve()
    if (not source.is_file() or source.suffix.lower() != '.zip' or
            source.is_relative_to(output) or ledger_path.is_relative_to(output) or output.is_relative_to(ledger_path) or
            output == ledger_path or ledger_path == source):
        raise ValueError('Source ZIP, new world directory and external ledger must be separate')
    if not re.fullmatch(r'[0-9a-f]{64}', expected_source_sha256):
        raise ValueError('An exact input ZIP SHA-256 is required')
    if expected_source_sha256 != SOURCE_DIGEST:
        raise ValueError('Input ZIP checksum differs from the authorized immutable source')
    source_sha = file_hash(source)
    if source_sha != expected_source_sha256:
        raise ValueError('Input ZIP checksum mismatch')
    rows = approved_rows(list_path)
    with zipfile.ZipFile(source) as archive:
        files = archive_files(archive)
        replacements, chunks = plan_changes(archive, files, rows)
        source_manifest = {name: digest(archive.read(info)) for name, info in sorted(files.items())}
        expected_manifest = dict(source_manifest)
        expected_manifest.update({name: digest(data) for name, data in replacements.items()})
        report = {'schemaVersion': 1, 'kind': 'missing-composite-retirement',
                  'sourceZipSha256': source_sha, 'approvedListSha256': APPROVED_DIGEST,
                  'worldName': output.name, 'ledger': rows, 'changedChunks': chunks,
                  'sourceManifest': source_manifest, 'outputManifest': expected_manifest,
                  'retiredIds': sorted({r['before'].split('[', 1)[0] for r in rows}),
                  'retiredCells': 33, 'helperRemovals': 0,
                  'policy': 'Intentional retirement to air; visual holes accepted; no recovery.'}
        if output.exists() or ledger_path.exists():
            if (not output.is_dir() or not ledger_path.is_file() or
                    json.loads(ledger_path.read_bytes()) != report or tree_manifest(output) != expected_manifest):
                raise ValueError('Existing output/ledger is incomplete, altered or not this retirement')
            return {**report, 'changedCellsThisRun': 0, 'idempotent': True}
        if file_hash(source) != source_sha:
            raise ValueError('Input ZIP changed during preflight')
        # All coordinates and the complete source have been validated. First
        # create a newly named world copy; only this directory is ever mutated.
        output.mkdir(parents=True, exist_ok=False)
        for name, info in sorted(files.items()):
            target = output / name
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('xb') as stream:
                stream.write(archive.read(info))
        for name, data in replacements.items():
            (output / name).write_bytes(data)
        if tree_manifest(output) != expected_manifest or file_hash(source) != source_sha:
            raise ValueError('Copy verification failed; incomplete output has no accepted ledger')
        ledger_path.parent.mkdir(parents=True, exist_ok=True)
        with ledger_path.open('x', encoding='utf-8', newline='\n') as stream:
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write('\n')
        return {**report, 'changedCellsThisRun': 33, 'idempotent': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--retire-missing-composites', action='store_true', required=True)
    parser.add_argument('--ledger', required=True, type=Path)
    parser.add_argument('--expected-source-sha256', required=True)
    args = parser.parse_args()
    result = retire(args.source, args.output, args.ledger, expected_source_sha256=args.expected_source_sha256)
    print(json.dumps({k: result[k] for k in ('sourceZipSha256', 'worldName', 'retiredCells', 'helperRemovals', 'changedCellsThisRun', 'idempotent')}))


if __name__ == '__main__':
    main()
