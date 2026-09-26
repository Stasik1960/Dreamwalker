"""Read-only reconstruction of historical carrier meshes from the frozen JAR.

This is evidence generation, not permission to replace unmatched world cells.
"""
import copy
import functools
import gzip
import hashlib
import io
import json
import zipfile
from collections import defaultdict
from contextlib import contextmanager

import numpy as np
from PIL import Image
import catalog_geometry as cg
import modular_mesh as mm
from build_city_compat import JAR, JAR_SHA, rotated_mesh
from build_modular_palette import compact_faces
from verify_composite_overlap_evidence import signature
from source_mapping_archive import archive
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT/'docs/composite-grid-repair/historical-wall-mesh-mappings.json'


@contextmanager
def historical_art():
    if hashlib.sha256(JAR.read_bytes()).hexdigest() != JAR_SHA:
        raise ValueError('HISTORICAL_JAR_HASH_MISMATCH')
    with zipfile.ZipFile(JAR) as jar:
        def read(path):
            try: return jar.read(path)
            except KeyError: return cg.ZIP.read(path)

        @functools.lru_cache(None)
        def model(name):
            ns, local = name.split(':') if ':' in name else ('minecraft', name)
            data = json.loads(read(f'assets/{ns}/models/{local}.json'))
            base = copy.deepcopy(model(data['parent'])) if 'parent' in data else {}
            textures = {**base.get('textures', {}), **data.get('textures', {})}
            base.update(data); base['textures'] = textures
            return base

        @functools.lru_cache(None)
        def texture(name):
            ns, local = name.split(':') if ':' in name else ('minecraft', name)
            im = Image.open(io.BytesIO(read(f'assets/{ns}/textures/{local}.png'))).convert('RGBA')
            return np.array(im.crop((0, 0, im.width, im.width)) if im.height > im.width else im)

        previous = cg.model, mm.model, mm.texture
        cg.model = mm.model = model
        mm.texture = texture
        mm.cells_for_app.cache_clear()
        try: yield jar
        finally:
            cg.model, mm.model, mm.texture = previous
            mm.cells_for_app.cache_clear()


def carrier_cells(jar, ident, props, position=None):
    props = {**archive()['v2\\migration.json'][ident]['default'], **props}
    cells = {}
    state = json.loads(jar.read(f'assets/bloodborne_blocks/blockstates/{ident}.json'))
    for group in cg.applications(state, props):
        if len(group) != 1:
            if position is None: raise ValueError('WEIGHTED_VARIANT_REQUIRES_POSITION_EVIDENCE')
            from source_variant_rng import weighted_index
            choice = weighted_index([app.get('weight', 1) for app in group], position, 'multipart' in state)
        else: choice = 0
        for cell, polys in mm.cells_for_app(json.dumps(group[choice], sort_keys=True)).items():
            cells.setdefault(cell, []).extend(polys)
    return {cell: compact_faces(polys) for cell, polys in cells.items()}


def inspect():
    samples = [
        ('red_nether_brick_wall', dict(east='tall', north='none', south='none', up='false', west='tall'), 'm_0730cfd72a0dbe29', 'east'),
        ('sandstone_wall', dict(east='tall', north='none', south='none', up='false', west='tall'), 'm_37ae347a7c9d73ae', 'east'),
        ('mossy_cobblestone_wall', dict(east='tall', north='tall', south='tall', up='true', west='none'), 'm_5992264774a8b7bf', 'west'),
    ]
    rows = []
    with historical_art() as jar:
        meshes = json.loads(gzip.decompress(jar.read('bloodborne_blocks/v2/meshes.json.gz')))
        for ident, props, actual, facing in samples:
            cells = carrier_cells(jar, ident, props)
            expected = signature(cells.get((0, 0, 0), []))
            observed = signature(rotated_mesh(meshes[actual], ['north', 'east', 'south', 'west'].index(facing))['polygons'])
            rows.append(dict(carrier=ident, properties=props, cells=list(cells), actual=actual,
                             facing=facing, expected_polygons=len(expected), actual_polygons=len(observed),
                             missing=len(expected-observed), extra=len(observed-expected)))
    return rows


def mappings():
    """Only complete single-cell carriers, exact textured faces and attribution."""
    from logical_contract_v2 import direct_rules
    from convert_logical_world import DEFAULT_RESOURCES
    raw, _ = direct_rules(DEFAULT_RESOURCES)
    allowed = {'red_nether_brick_wall', 'mossy_cobblestone_wall', 'sandstone_wall'}
    sources = archive()['v2\\sources.json']
    rows = []
    with historical_art() as jar:
        meshes = json.loads(gzip.decompress(jar.read('bloodborne_blocks/v2/meshes.json.gz')))
        index = defaultdict(list)
        for ident, origins in sources.items():
            if not allowed.intersection(origins) or ident not in meshes: continue
            for turn, facing in enumerate(('north', 'east', 'south', 'west')):
                token = frozenset(signature(rotated_mesh(meshes[ident], turn)['polygons']))
                index[token].append((ident, facing, origins))
        seen = set()
        for rule in raw:
            carrier = rule.source.state[0].split(':')[-1]
            if carrier not in allowed or rule.members or rule.outputs: continue
            if rule.source.state in seen: continue
            seen.add(rule.source.state)
            cells = carrier_cells(jar, carrier, dict(rule.source.state[1]))
            if set(cells) != {(0, 0, 0)}: continue
            token = frozenset(signature(cells[(0, 0, 0)]))
            matches = sorted((i, f) for i, f, origins in index[token] if carrier in origins)
            if not matches: continue
            rows.append({'source': [rule.source.state[0], [list(p) for p in rule.source.state[1]]],
                         'matches': [{'id': i, 'facing': f} for i, f in matches],
                         'polygonCount': len(token),
                         'polygonSha256': hashlib.sha256(repr(sorted(token)).encode()).hexdigest()})
    return {'schemaVersion': 1, 'historicalJarSha256': JAR_SHA,
            'scope': 'Exact complete single-cell textured carrier meshes; no extra or missing polygons',
            'mappings': rows}


if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser(); p.add_argument('--write-mappings', action='store_true')
    p.add_argument('--verify-mappings', action='store_true'); args = p.parse_args()
    if args.write_mappings or args.verify_mappings:
        data = mappings()
        if args.write_mappings:
            MANIFEST.write_text(json.dumps(data, indent=2)+'\n', encoding='utf8')
        else:
            if data != json.loads(MANIFEST.read_bytes()): raise ValueError('WALL_MESH_EVIDENCE_MISMATCH')
        print('Exact carrier states:', len(data['mappings']))
    else: print(json.dumps(inspect(), indent=2))
