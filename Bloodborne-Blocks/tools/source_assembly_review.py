#!/usr/bin/env python3
"""Bounded Catalog B: source-state spatial candidates, never logical families.

Only explicitly listed terrain regions and seed carrier IDs are inspected.
Connectivity is a proposal for human review, not a semantic object contract.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import itertools
import json
import shutil
import zipfile
from collections import Counter, OrderedDict, defaultdict
from pathlib import Path

from manual_review_world import (EXPECTED_SHA256, EXPECTED_PACK_SHA256, DEFAULT_SOURCEPACK,
                                 DEFAULT_VANILLA, _sha256, _dimension, _state, _load_blockstates)
from world_io import RegionFile, compound, section_blocks

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'docs/manual-source-assemblies.json'
SCOPE = ROOT / 'docs/source-assembly-scope.json'
DIRECTIONS = ('north', 'east', 'south', 'west')


def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(value, ensure_ascii=False, indent=2) + '\n'
    if not path.exists() or path.read_text(encoding='utf-8') != text:
        path.write_text(text, encoding='utf-8')


def rotate_cell(pos, turns):
    x, y, z = pos
    for _ in range(turns): x, z = -z, x
    return (x, y, z)


def rotate_state(state, turns):
    name, values = state
    result = {}
    for key, value in values:
        if key in DIRECTIONS: key = DIRECTIONS[(DIRECTIONS.index(key) + turns) % 4]
        if key == 'facing' and value in DIRECTIONS: value = DIRECTIONS[(DIRECTIONS.index(value) + turns) % 4]
        if key == 'axis' and turns % 2 and value in ('x', 'z'): value = {'x':'z', 'z':'x'}[value]
        if key == 'rotation': value = str((int(value) + 4 * turns) % 16)
        result[key] = value
    return name, tuple(sorted(result.items()))


def normalized(cells, turns):
    rotated = [(rotate_cell(pos, turns), rotate_state(state, turns)) for pos, state in cells.items()]
    origin = min((p for p, _ in rotated), key=lambda p: (p[1], p[2], p[0]))
    return tuple(sorted((tuple(p[i]-origin[i] for i in range(3)), state) for p, state in rotated))


def fingerprint(cells):
    variants = [normalized(cells, turn) for turn in range(4)]
    canonical = min(variants)
    return hashlib.sha256(repr(canonical).encode()).hexdigest(), variants.index(canonical)


def clusters(cells, vertical_gap_ids=()):
    """26-neighbor connectivity of selected raw carriers; no visual/family bounds."""
    remaining = set(cells)
    steps = [p for p in itertools.product((-1, 0, 1), repeat=3) if any(p)]
    while remaining:
        start = min(remaining, key=lambda p: (p[1], p[2], p[0]))
        remaining.remove(start); queue = [start]; group = []
        while queue:
            pos = queue.pop(); group.append(pos)
            extra = []
            # Explicit bounded source-space tree window: split-height carriers
            # can be separated by air. This is a proposal, never mesh-AABB union.
            if cells[pos][0] in vertical_gap_ids:
                extra = [(dx, dy, dz) for dx,dz in itertools.product((-1,0,1),repeat=2) for dy in (-6,-5,-4,-3,-2,2,3,4,5,6)]
            for step in steps + extra:
                nxt = tuple(pos[i] + step[i] for i in range(3))
                if step in extra and nxt in cells and cells[nxt][0] not in vertical_gap_ids: continue
                if nxt in remaining: remaining.remove(nxt); queue.append(nxt)
        yield {pos: cells[pos] for pos in group}


def active_apps(raw, props):
    """Resolve source blockstate transforms; weighted choice is explicitly approximate."""
    def condition(where):
        if 'OR' in where: return any(condition(v) for v in where['OR'])
        if 'AND' in where: return all(condition(v) for v in where['AND'])
        return all(props.get(k) in str(v).split('|') for k, v in where.items())
    def choose(value):
        if isinstance(value, list):
            if not value: return []
            chosen = copy.deepcopy(value[0])
            if len(value) > 1: chosen['preview_alternatives'] = len(value)
            return [chosen]
        return [copy.deepcopy(value)] if isinstance(value, dict) and 'model' in value else []
    apps = []
    for key, value in raw.get('variants', {}).items():
        if condition(dict(pair.split('=', 1) for pair in key.split(',') if '=' in pair)): apps += choose(value)
    for entry in raw.get('multipart', []):
        if condition(entry.get('when', {})): apps += choose(entry.get('apply'))
    return apps


class SourceReader:
    def __init__(self, archive, names):
        self.archive, self.names, self.regions, self.cache = archive, {}, {}, OrderedDict()
        for name in names:
            _, rx, rz, _ = Path(name).name.split('.')
            self.names[(_dimension(name), int(rx), int(rz))] = name

    def region(self, name):
        if name not in self.regions: self.regions[name] = RegionFile(self.archive.read(name))
        return self.regions[name]

    def get(self, dim, pos):
        x, y, z = pos; key = (dim, x >> 4, z >> 4)
        if key not in self.cache:
            sections = {}; name = self.names.get((dim, x >> 9, z >> 9))
            if name:
                chunk = self.region(name).get_chunk((x >> 4) & 31, (z >> 4) & 31)
                if chunk:
                    root = compound(chunk.nbt().root)
                    for sec in root.get('sections', root.get('Sections')).value:
                        unpacked = section_blocks(sec)
                        if unpacked: sections[int(compound(sec)['Y'].value)] = unpacked
            self.cache[key] = sections
            if len(self.cache) > 96: self.cache.popitem(last=False)
        self.cache.move_to_end(key)
        sec = self.cache[key].get(y >> 4)
        if sec is None: return None
        palette, indices = sec
        return _state(palette[indices[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]])

    def scan(self, wanted):
        cells = defaultdict(dict)
        for name in sorted(self.names.values()):
            dim = _dimension(name)
            for chunk in self.region(name).chunks():
                root = compound(chunk.nbt().root)
                cx, cz = int(root['xPos'].value), int(root['zPos'].value)
                for sec in root.get('sections', root.get('Sections')).value:
                    fields = compound(sec); bs = fields.get('block_states')
                    if bs is None: continue
                    palette_tag = compound(bs).get('palette')
                    if palette_tag is None: continue
                    states = {i: _state(value) for i, value in enumerate(palette_tag.value)}
                    states = {i: state for i, state in states.items() if state[0] in wanted}
                    if not states: continue
                    _, indices = section_blocks(sec); sy = int(fields['Y'].value)
                    for index, value in enumerate(indices):
                        if value in states:
                            pos = (cx*16+(index & 15), sy*16+(index >> 8), cz*16+((index >> 4) & 15))
                            cells[dim][pos] = states[value]
            print('Catalog B read-only region:', name, flush=True)
        return cells


def merge_records(existing, fresh):
    old = {row['spatial_fingerprint']: row for row in existing}
    next_id = max((int(row['review_id'][1:]) for row in existing), default=0) + 1
    result = copy.deepcopy(existing)
    for row in fresh:
        if row['spatial_fingerprint'] in old:
            # Regeneration never overrides human decisions, component numbering or scope.
            continue
        row['review_id'] = f'C{next_id:03}'; next_id += 1
        row['status'] = 'UNREVIEWED'; row['user_decision'] = None
        result.append(row)
    return result


def generate(source, scope, previous=None, limit=12):
    before = _sha256(source)
    if before != EXPECTED_SHA256: raise ValueError('Wrong source world SHA-256')
    if _sha256(DEFAULT_SOURCEPACK) != EXPECTED_PACK_SHA256: raise ValueError('Wrong resource pack SHA-256')
    if not 1 <= len(scope['regions']) <= 3 or not 10 <= limit <= 15: raise ValueError('Bounded pass requires 1–3 regions and 10–15 candidates')
    visual = _load_blockstates(DEFAULT_SOURCEPACK, DEFAULT_VANILLA)
    carrier_category = {name: group['label'] for group in scope['carrier_groups'] for name in group['ids']}
    grouped = defaultdict(list); rejected = Counter()
    with zipfile.ZipFile(source) as archive:
        reader = SourceReader(archive, scope['regions'])
        found = reader.scan(carrier_category)
        for dim, cells in sorted(found.items()):
            for group in clusters(cells, set(scope.get('vertical_gap_carriers', []))):
                if not 2 <= len(group) <= 24: rejected['cell_count'] += 1; continue
                extents = [max(p[i] for p in group)-min(p[i] for p in group)+1 for i in range(3)]
                if extents[0] > 9 or extents[1] > 18 or extents[2] > 9: rejected['extent'] += 1; continue
                # A cluster touching unscanned terrain is not asserted to be finite.
                if any((dim, (p[0]+dx) >> 9, (p[2]+dz) >> 9) not in reader.names for p in group for dx,dz in ((-1,0),(1,0),(0,-1),(0,1))):
                    rejected['scope_edge'] += 1; continue
                signature, turn = fingerprint(group)
                grouped[signature].append((dim, group, turn))
        # Rotate through categories to keep this small batch varied.
        buckets = defaultdict(list)
        for signature, matches in sorted(grouped.items()):
            group = matches[0][1]
            category = Counter(carrier_category[s[0]] for s in group.values()).most_common(1)[0][0]
            buckets[category].append((signature, matches))
        for bucket in buckets.values():
            bucket.sort(key=lambda item: (-len({s[0] for s in item[1][0][1].values()}), -len(item[1][0][1]), -len(item[1]), item[0]))
        selected = []
        while len(selected) < limit and any(buckets.values()):
            for category in [g['label'] for g in scope['carrier_groups']]:
                if buckets[category] and len(selected) < limit: selected.append((category, *buckets[category].pop(0)))
        records = []
        for category, signature, matches in selected:
            dim, group, turn = matches[0]
            anchor = min(group, key=lambda p: (p[1], p[2], p[0]))
            components = []
            for number, (pos, state) in enumerate(sorted(group.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])), 1):
                relative = [pos[i]-anchor[i] for i in range(3)]
                apps = active_apps(visual.get(state[0], {}), dict(state[1]))
                for app in apps: app['offset'] = relative
                if not apps: raise ValueError('No original blockstate visual for ' + state[0])
                components.append({'number':number, 'relative':relative, 'source':{'id':state[0], 'properties':dict(state[1])}, 'apps':apps})
            context = []
            ranges = [range(min(p[i] for p in group)-1, max(p[i] for p in group)+2) for i in range(3)]
            for pos in itertools.product(*ranges):
                if pos in group: continue
                state = reader.get(dim, pos)
                if state is not None and state[0] not in ('minecraft:air','minecraft:cave_air','minecraft:void_air'):
                    context.append({'relative':[pos[i]-anchor[i] for i in range(3)], 'source':{'id':state[0],'properties':dict(state[1])}})
            records.append({'spatial_fingerprint':signature, 'hypothesis':category + ': конечная группа соседних source carriers; граница требует ручной проверки',
                'boundary_method':'26-neighbor source connectivity; listed tree carriers also use horizontal radius 1 / vertical gap <=6. Excludes masonry. No F-family input. Hypothesis only.',
                'example':{'dimension':dim,'anchor':list(anchor)}, 'components':components, 'context':context,
                'similar_count':len(matches), 'rotations':sorted({((turn-other_turn)%4)*90 for _,_,other_turn in matches}),
                'count_scope':'Only explicitly listed source terrain regions; exact complete carrier cluster modulo Y rotation, not whole-world occurrence count',
                'examples':[{'dimension':d,'anchor':list(min(g,key=lambda p:(p[1],p[2],p[0])))} for d,g,_ in matches[:3]],
                'mirror_policy':'not normalized', 'confidence':'PROVISIONAL_SPATIAL_CANDIDATE',
                'context_policy':'Separate one-cell shell around carrier bounds; never automatically part of candidate'})
    if _sha256(source) != before: raise ValueError('Read-only source changed')
    existing = (previous or {}).get('candidates', [])
    return {'format':'bloodborne-source-assemblies-v1', 'source':{'path':'reference-inputs/source-world.zip','sha256':before,'sha256_after':before},
            'sourcepack_sha256':EXPECTED_PACK_SHA256, 'scan_scope':scope, 'rejected_clusters':dict(rejected),
            'candidate_policy':'Proposals only. No automatic migration, no inherited family boundaries, no semantic approval.',
            'candidates':merge_records(existing, records)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,default=ROOT/'reference-inputs/source-world.zip')
    parser.add_argument('--manifest',type=Path,default=MANIFEST)
    parser.add_argument('--scope',type=Path,default=SCOPE)
    parser.add_argument('--output',type=Path,default=ROOT/'build/source-assembly-review')
    parser.add_argument('--publish',type=Path,help='Copy only generated Catalog B assets to a tracked documentation directory')
    parser.add_argument('--render-only',action='store_true')
    args = parser.parse_args()
    if args.render_only: manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    else:
        previous = json.loads(args.manifest.read_text(encoding='utf-8')) if args.manifest.exists() else None
        manifest = generate(args.source,json.loads(args.scope.read_text(encoding='utf-8')),previous)
        dump(args.manifest,manifest)
    from source_assembly_render import render_catalog
    render_catalog(manifest,args.output)
    if args.publish:
        if args.publish.resolve() == args.output.resolve(): raise ValueError('Separate publish destination required')
        shutil.copytree(args.output,args.publish,dirs_exist_ok=True)
    print('Catalog B:',len(manifest['candidates']),'candidates;',args.output/'index.html')


if __name__ == '__main__': main()
