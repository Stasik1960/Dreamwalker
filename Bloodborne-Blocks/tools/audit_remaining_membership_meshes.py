"""Read-only diagnosis of complete carrier artwork in unresolved assemblies.

Containment is NOT membership approval: shared polygons require a complete
owner transaction, and missing geometry remains unresolved. No gate consumes
this diagnostic report and no world/converter resources are changed.
"""
import argparse
import functools
import gzip
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path

from inspect_historical_wall_meshes import historical_art, carrier_cells, ROOT
from composite_world_oracle import EvidenceReader
from verify_composite_overlap_evidence import signature
from build_city_compat import rotated_mesh
from build_modular_palette import compact_faces

MODDED_SHA = 'c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9'


def audit(gate_path, output):
    source = ROOT/'reference-inputs/latest-modded-world.zip'
    if hashlib.sha256(source.read_bytes()).hexdigest() != MODDED_SHA:
        raise ValueError('IMMUTABLE_MODDED_INPUT_HASH_MISMATCH')
    gate = json.loads(gate_path.read_bytes())
    wanted = {(r['rule'], tuple(r['origin'])) for r in gate['unresolved_technical_membership']}
    oracle = json.loads((ROOT/'docs/composite-grid-repair/protected-world-oracle.json').read_bytes())
    occurrences = [r for r in oracle['occurrences'] if (r['rule'], tuple(r['origin'])) in wanted]
    occurrences.sort(key=lambda r: (r['origin'][0]//16, r['origin'][2]//16, r['origin'][1]))
    reader = EvidenceReader(source)
    counts = Counter(); rows = []
    try:
        with historical_art() as jar:
            meshes = json.loads(gzip.decompress(jar.read('bloodborne_blocks/v2/meshes.json.gz')))

            @functools.lru_cache(None)
            def expected(state):
                name, _, suffix = state.partition('[')
                props = dict(p.split('=', 1) for p in suffix.rstrip(']').split(',') if p)
                try: return carrier_cells(jar, name.removeprefix('minecraft:'), props), None
                except (ValueError, KeyError) as exc: return {}, str(exc)

            @functools.lru_cache(None)
            def actual(state):
                if not state: return None
                name, _, suffix = state.partition('[')
                ident = name.removeprefix('bloodborne_blocks:')
                if ident not in meshes: return None
                props = dict(p.split('=', 1) for p in suffix.rstrip(']').split(',') if p)
                return signature(rotated_mesh(meshes[ident], ['north', 'east', 'south', 'west'].index(props.get('facing', 'north')))['polygons'])

            def at_position(state, point):
                cells, error = expected(state)
                if error != 'WEIGHTED_VARIANT_REQUIRES_POSITION_EVIDENCE': return cells, error
                name, _, suffix = state.partition('[')
                props = dict(p.split('=', 1) for p in suffix.rstrip(']').split(',') if p)
                try: return carrier_cells(jar, name.removeprefix('minecraft:'), props, tuple(point)), None
                except (ValueError, KeyError) as exc: return {}, str(exc)

            for number, occurrence in enumerate(occurrences):
                if number % 500 == 0: print('membership artwork audit', number, '/', len(occurrences), flush=True)
                polys = defaultdict(list); errors = []
                for part in occurrence['source_cells']:
                    cells, error = at_position(part['state'], part['position'])
                    if error: errors.append({'source': part['state'], 'error': error}); continue
                    for offset, faces in cells.items():
                        point = tuple(part['position'][i]+offset[i] for i in range(3))
                        polys[point].extend(faces)
                cells = []; totals = Counter()
                for point, faces in sorted(polys.items()):
                    observed = reader.state(occurrence['dimension'], point)[1]
                    token = actual(observed)
                    own = signature(compact_faces(faces))
                    status = 'NON_MODULE' if token is None else 'MISSING' if own-token else 'SHARED' if token-own else 'EXACT'
                    totals[status] += 1
                    cells.append({'position': list(point), 'actual': observed, 'status': status,
                                  'expectedPolygons': len(own), 'missing': None if token is None else len(own-token),
                                  'extra': None if token is None else len(token-own)})
                status = 'UNRESOLVED_MODEL' if errors else 'UNRESOLVED_EMPTY' if not cells else 'UNRESOLVED_MISSING' if totals['MISSING'] or totals['NON_MODULE'] else 'SHARED_REQUIRES_OWNER_CLOSURE' if totals['SHARED'] else 'EXACT_ARTWORK_REQUIRES_MAPPING_REVIEW'
                counts[status] += 1
                rows.append({'rule': occurrence['rule'], 'origin': occurrence['origin'], 'status': status,
                             'modelErrors': errors, 'cells': cells})
    finally: reader.close()
    result = {'schemaVersion': 1, 'moddedSha256': MODDED_SHA,
              'scope': 'DIAGNOSTIC ONLY; never substitutes for ownership or whole-world gates',
              'counts': dict(counts), 'occurrences': rows}
    output.write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    print(json.dumps(result['counts']), flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('gate', type=Path); p.add_argument('output', type=Path)
    a = p.parse_args(); audit(a.gate, a.output)
