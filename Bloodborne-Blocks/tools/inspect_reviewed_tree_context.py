"""Bounded read-only evidence capture at four frozen review examples; never a city scan."""
import json
import hashlib
from pathlib import Path
from test_reviewed_source_examples import SourceZip, MANIFEST, SOURCE
from world_io import compound, Tag, TAG_LONG_ARRAY, unpack_palette_indices
from convert_logical_world import state_of
from source_contract_rotations import applications

ROOT = Path(__file__).resolve().parents[1]


def capture():
    reader = SourceZip(SOURCE)
    chunks = {}

    def state(dimension, point):
        cx, cz = point[0] // 16, point[2] // 16
        key = dimension, cx, cz
        if key not in chunks:
            region, _, _ = reader._region(dimension, point[0], point[2])
            record = region.get_chunk(cx % 32, cz % 32)
            sections = {}
            if record:
                for entry in compound(record.nbt().root).get('sections', Tag(9, [])).value:
                    section = compound(entry)
                    if 'block_states' not in section:
                        continue
                    blocks = compound(section['block_states'])
                    palette = [state_of(p, {}) for p in blocks['palette'].value]
                    indices = unpack_palette_indices(blocks.get('data', Tag(TAG_LONG_ARRAY, [])).value, len(palette))
                    sections[int(section['Y'].value)] = palette, indices
            chunks[key] = sections
        section = chunks[key].get(point[1] // 16)
        if section is None:
            return 'minecraft:air', ()
        palette, indices = section
        return palette[indices[(point[1] & 15) * 256 + (point[2] & 15) * 16 + (point[0] & 15)]]

    result = {'source_sha256': reader.sha256, 'scope': 'four saved tree examples only, +/- 7 XZ, -6..+10 Y', 'examples': []}
    rows = json.loads(MANIFEST.read_text(encoding='utf8'))['candidates']
    for row in rows:
        if row['review_id'] not in ('C001', 'C009'):
            continue
        for pattern in row['source_patterns']:
            anchor = pattern['example']['anchor']
            dimension = pattern['example']['dimension']
            found = []
            for y in range(-6, 11):
                for x in range(-7, 8):
                    for z in range(-7, 8):
                        relative = [x, y, z]
                        point = tuple(anchor[i] + relative[i] for i in range(3))
                        name, props = state(dimension, point)
                        if name.endswith(('_wool', '_log')):
                            found.append({'relative': relative, 'id': name, 'properties': dict(props)})
            result['examples'].append({'review_id': row['review_id'], 'pattern_id': pattern['pattern_id'],
                                       'example': pattern['example'], 'carriers': found})
    # Explicit tree reconstruction, not connectivity: a planter/trunk column
    # and exactly four atlas wing columns at +/-3, one tier above its base.
    # The two six-log Catalog B proposals each straddle TWO such trees.
    complete = []
    for example in result['examples']:
        cells = {tuple(c['relative']): c for c in example['carriers']}
        for base, carrier in cells.items():
            if carrier['id'] != 'minecraft:white_wool':
                continue
            points = [tuple(base[i] + offset[i] for i in range(3)) for offset in
                      [(0, y, 0) for y in (0, 3, 6, 9)] +
                      [(x, y, z) for x, z in ((-3, 0), (3, 0), (0, -3), (0, 3)) for y in (3, 6, 9)]]
            if any(p not in cells for p in points):
                raise ValueError('Incomplete explicit tree in bounded evidence: ' + str(base))
            if [cells[p]['id'] for p in points[:4]] != ['minecraft:' + n + '_wool' for n in ('white', 'orange', 'magenta', 'light_blue')]:
                raise ValueError('Unknown central column')
            origin = [example['example']['anchor'][i] + base[i] - (1 if i == 1 else 0) for i in range(3)]
            parts = []
            for point in points:
                c = cells[point]
                offset = [point[i] - base[i] + (1 if i == 1 else 0) for i in range(3)]
                parts.append({'id': c['id'], 'properties': c['properties'], 'offset': offset,
                              'model_choices': applications(c['id'], c['properties'])})
            raw = [{k: p[k] for k in ('id', 'properties', 'offset')} for p in parts]
            signature = hashlib.sha256(json.dumps(raw, sort_keys=True).encode()).hexdigest()
            complete.append({'review_id': example['review_id'], 'review_pattern': example['pattern_id'],
                             'dimension': example['example']['dimension'], 'master': origin,
                             'exact_source_signature': signature, 'components': parts})
    result['complete_trees'] = complete
    result['chunks_read'] = len(chunks)
    reader.close()
    target = ROOT / 'build/qa2-tree-context.json'
    target.write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    print('chunks read:', len(chunks), 'complete trees:', len(complete),
          'exact layouts:', len({t['exact_source_signature'] for t in complete}))


if __name__ == '__main__':
    capture()
