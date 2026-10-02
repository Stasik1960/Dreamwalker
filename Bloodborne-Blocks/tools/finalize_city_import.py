"""Bounded-memory, cell-preserving compatibility pass on a new world copy.

This pass only applies the frozen one-cell palette mapping. Unresolved assembly
semantics remain gallery work; the pass never claims to assemble those objects.
Unknown registry names prevent publication rather than silently becoming air.
"""
from __future__ import annotations

import argparse
import copy
import json
import os
import shutil
import sys
import tempfile
from collections import Counter
from pathlib import Path

from city_palette import load
from restore_yuushya import _regions
from world_io import RegionFile, Tag, TAG_LIST, TAG_COMPOUND, compound, block_state_key, section_blocks, invalidate_chunk_lighting


def finalize(source: Path, output: Path, city: Path, report_path: Path) -> dict:
    source, output, city, report_path = (Path(p).resolve() for p in (source, output, city, report_path))
    if not source.is_dir() or not (source / 'level.dat').is_file():
        raise ValueError('source must be a Java world directory')
    if output.exists() or source == output or source in output.parents:
        raise ValueError('output must be new and outside the input')
    if any(report_path == path or path in report_path.parents for path in (source, output, city, city.parent/'logical')):
        raise ValueError('report must be outside worlds and migration resources')
    mapping, definitions, hashes = load(city)
    logical = json.loads((city.parent / 'logical/definitions.json').read_bytes())['blocks']
    registered = set(definitions) | {'bloodborne_blocks:' + d['id'] for d in logical} | {'bloodborne_blocks:architecture_part'}
    output.parent.mkdir(parents=True, exist_ok=True)
    stage_parent = Path(tempfile.mkdtemp(prefix=output.name + '.staging-', dir=output.parent))
    staging = stage_parent / output.name
    mapped, unknown = Counter(), Counter()
    unknown_coords, samples = {}, {}
    changed_chunks = 0
    try:
        shutil.copytree(source, staging)
        for dimension, _, path in _regions(staging):
            region = RegionFile.open(path)
            rx, rz = (int(x) for x in path.stem.split('.')[1:])
            dirty_region = False
            for stored in region.chunks():
                # A cheap compressed-payload decode avoids constructing NBT for
                # the many chunks containing no mod blocks at all.
                if b'bloodborne_blocks:' not in stored.raw_nbt():
                    continue
                nbt = stored.nbt()
                root = compound(nbt.root)
                entities = root.get('block_entities', Tag(TAG_LIST, [], TAG_COMPOUND))
                entity_before = copy.deepcopy(entities)
                entity_positions = {tuple(int(compound(e)[a].value) for a in ('x', 'y', 'z')) for e in entities.value}
                touched = False
                for section in root.get('sections', Tag(TAG_LIST, [], TAG_COMPOUND)).value:
                    fields = compound(section)
                    bs = fields.get('block_states')
                    if not bs:
                        continue
                    palette = compound(bs)['palette'].value
                    keys = [block_state_key(t) for t in palette]
                    relevant = {i for i, key in enumerate(keys) if key in mapping or
                                (key.startswith('bloodborne_blocks:') and key.split('[')[0] not in registered)}
                    if not relevant:
                        continue
                    _, indices = section_blocks(section)
                    counts = Counter(indices)
                    sy = int(fields['Y'].value) * 16
                    for i in relevant:
                        if not counts[i]:
                            continue
                        key = keys[i]
                        if key in mapping:
                            mapped[key] += counts[i]
                            rows = samples.setdefault(key, [])
                        else:
                            unknown[key] += counts[i]
                            rows = unknown_coords.setdefault(key, [])
                        for index, value in enumerate(indices):
                            if value != i:
                                continue
                            pos = ((rx * 32 + stored.x) * 16 + (index & 15),
                                   sy + (index >> 8), (rz * 32 + stored.z) * 16 + ((index >> 4) & 15))
                            if key in mapping and pos in entity_positions:
                                raise ValueError(f'refusing mapped state with foreign block entity at {(dimension, *pos)}')
                            if key not in mapping or len(rows) < 3:
                                rows.append(list(pos))
                        if key in mapping:
                            palette[i] = copy.deepcopy(mapping[key])
                            touched = True
                if touched:
                    if root.get('block_entities', Tag(TAG_LIST, [], TAG_COMPOUND)) != entity_before:
                        raise ValueError('block-entity data changed during palette replacement')
                    invalidate_chunk_lighting(root)
                    root.pop('Heightmaps', None)
                    region.set_chunk(stored.x, stored.z, nbt, timestamp=stored.timestamp)
                    if region.get_chunk(stored.x, stored.z).nbt() != nbt:
                        raise ValueError('serialized chunk differs from edited NBT')
                    dirty_region = True
                    changed_chunks += 1
            if dirty_region:
                region.save(path)
            print(f'{dimension} {path.name}: {changed_chunks} changed chunks', flush=True)
        report = {'schemaVersion': 2, 'input': str(source), 'output': str(output),
                  'migrationHashes': hashes, 'mappedStates': dict(sorted(mapped.items())),
                  'gallerySamples': samples,
                  'unknownStates': {k: {'cells': unknown_coords[k], 'count': n, 'status': 'pending_review'}
                                    for k, n in sorted(unknown.items())},
                  'unknownCellCount': sum(unknown.values()), 'changedCells': sum(mapped.values()),
                  'changedChunks': changed_chunks,
                  'status': 'blocked' if unknown else 'complete'}
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        if not unknown:
            os.replace(staging, output)
        return report
    finally:
        shutil.rmtree(stage_parent, ignore_errors=True)


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('source', type=Path)
    p.add_argument('output', type=Path)
    p.add_argument('--city', type=Path, default=Path(__file__).resolve().parents[1] / 'src/main/resources/bloodborne_blocks/city')
    p.add_argument('--report', type=Path, required=True)
    args = p.parse_args(argv)
    result = finalize(args.source, args.output, args.city, args.report)
    print(json.dumps({k: result[k] for k in ('status', 'changedCells', 'unknownCellCount')}))
    return 0 if result['status'] == 'complete' else 2


if __name__ == '__main__':
    sys.exit(main())
