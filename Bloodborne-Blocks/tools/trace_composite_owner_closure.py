"""Read-only exact historical producer graph; never a conversion fallback.

Search bounds are explicit. A cell not fully explained within those bounds
stays unresolved. Every admitted node is an actual source carrier with frozen
forward-map or original carrier-model evidence; no appearance-based grouping.
"""
import argparse
import functools
import gzip
import hashlib
import itertools
import json
from collections import defaultdict, deque
from pathlib import Path
from composite_world_oracle import EvidenceReader, ROOT, SOURCE_SHA
from inspect_historical_wall_meshes import historical_art, carrier_cells
from source_mapping_archive import archive
from build_city_compat import rotated_mesh
from verify_composite_overlap_evidence import signature
from audit_remaining_membership_meshes import MODDED_SHA
from build_modular_palette import is_cube
import modular_mesh as mm
import numpy as np


def trace_many(origins, radius=3, node_limit=128):
    paths = [ROOT/'reference-inputs/source-world.zip', ROOT/'reference-inputs/latest-modded-world.zip']
    for path, sha in zip(paths, (SOURCE_SHA, MODDED_SHA)):
        if hashlib.sha256(path.read_bytes()).hexdigest() != sha: raise ValueError('IMMUTABLE_INPUT_HASH_MISMATCH')
    source, current = (EvidenceReader(p) for p in paths)
    migration = archive()['v2\\migration.json']
    try:
        with historical_art() as jar:
            meshes = json.loads(gzip.decompress(jar.read('bloodborne_blocks/v2/meshes.json.gz')))
            definitions = {b['id']:b for b in json.loads(jar.read('bloodborne_blocks/v2/definitions.json'))['blocks']}
            @functools.lru_cache(None)
            def module(ident, facing):
                return frozenset(signature(rotated_mesh(meshes[ident], ('north','east','south','west').index(facing))['polygons']))

            @functools.lru_cache(None)
            def template(state):
                if not state: return {}, 'missing source'
                name, _, suffix = state.partition('['); ident = name.removeprefix('minecraft:')
                if ident not in migration: return {}, 'not an archived carrier'
                props = {**migration[ident]['default'], **dict(v.split('=',1) for v in suffix.rstrip(']').split(',') if v)}
                key = ','.join(k+'='+v for k,v in sorted(props.items()))
                parts = migration[ident]['states'].get(key)
                if isinstance(parts, list):
                    cells = defaultdict(set)
                    for part in parts:
                        if part['id'] not in meshes: return {}, 'archived module absent'
                        cells[tuple(part['offset'])].update(module(part['id'], part.get('properties',{}).get('facing','north')))
                    return {p:frozenset(v) for p,v in cells.items()}, 'frozen forward mapping'
                if not isinstance(parts, dict) or not parts.get('keep'): return {}, 'no frozen source state'
                try: cells = carrier_cells(jar, ident, props)
                except (KeyError, ValueError) as exc: return {}, str(exc)
                return {p:frozenset(signature(v)) for p,v in cells.items()}, 'historical retained carrier model'

            @functools.lru_cache(maxsize=65536)
            def observed(point):
                state = current.state('minecraft:overworld', point)[1]
                if not state: return None
                name, _, suffix = state.partition('['); ident = name.removeprefix('bloodborne_blocks:')
                if ident not in meshes: return None
                props = dict(v.split('=',1) for v in suffix.rstrip(']').split(',') if v)
                return module(ident, props.get('facing','north'))

            @functools.lru_cache(maxsize=65536)
            def producer(point):
                state = source.state('eh_s2:yharnam', point)[1]
                parts, provenance = template(state)
                cells = {tuple(point[i]+p[i] for i in range(3)): v for p,v in parts.items() if v}
                return state, cells, provenance

            @functools.lru_cache(maxsize=65536)
            def occluder(point):
                """Unchanged, independently attributable opaque cube, never owned."""
                state = current.state('minecraft:overworld',point)[1]
                if not state: return None
                ident = state.split('[',1)[0].removeprefix('bloodborne_blocks:')
                definition = definitions.get(ident,{})
                if not definition.get('full_cube') or definition.get('layer') != 'solid': return None
                polys = meshes[ident]['polygons']
                if not is_cube(polys): return None
                if any(not 0 <= value <= 1 for p in polys for vertex in p['vertices'] for value in vertex[:3]): return None
                if not all(np.all(mm.alpha_patch(p) == 255) for p in polys): return None
                original, parts, provenance = producer(point)
                if parts.get(point) != observed(point): return None
                return {'position':list(point),'source':original,'actual':state,'evidence':provenance,
                        'policy':'Frozen opaque_dominant: exact opaque carrier component; its complete source object must also close'}

            def trace_one(origin):
                nodes = {}; queue = deque([tuple(origin)]); examined = set(); errors = []
                rejected = {}
                while queue:
                    root = queue.popleft()
                    if root in nodes: continue
                    if len(nodes) >= node_limit:
                        errors.append({'reason':'node_limit','root':list(root)}); break
                    state, cells, provenance = producer(root)
                    if not cells:
                        errors.append({'reason':'no_complete_source_geometry','root':list(root),'detail':provenance}); continue
                    nodes[root] = (state, cells, provenance)
                    for point, own in cells.items():
                        actual = observed(point)
                        if actual is None or (not own <= actual and occluder(point) is None):
                            errors.append({'reason':'source_geometry_not_present','root':list(root),'cell':list(point),
                                           'missing':None if actual is None else len(own-actual)}); continue
                        if not own <= actual and occluder(point) is not None:
                            queue.append(point)
                        if point in examined: continue
                        examined.add(point)
                        if occluder(point) is not None and not own <= actual: continue
                        supplied = set().union(*(parts.get(point, frozenset()) for _,parts,_ in nodes.values()))
                        extra = actual-supplied
                        if not extra: continue
                        for offset in itertools.product(range(-radius,radius+1), repeat=3):
                            candidate = tuple(point[i]+offset[i] for i in range(3))
                            if candidate in nodes: continue
                            _, parts, _ = producer(candidate)
                            incoming = parts.get(point, frozenset())
                            if not incoming or not incoming & extra or not incoming <= actual: continue
                            # Every cell of this source object must remain present.
                            absent = [{'position':list(p), 'actual':current.state('minecraft:overworld',p)[1],
                                       'missing':None if observed(p) is None else len(faces-observed(p))}
                                      for p,faces in parts.items() if observed(p) is None or (not faces <= observed(p) and occluder(p) is None)]
                            if absent:
                                rejected[candidate] = {'sourceRoot':list(candidate),'state':producer(candidate)[0],
                                                       'explainsCell':list(point),'missingComponents':absent}
                                continue
                            queue.append(candidate)
                union = defaultdict(set)
                for _, cells, _ in nodes.values():
                    for point, faces in cells.items(): union[point].update(faces)
                cell_rows = []
                for point, faces in sorted(union.items()):
                    actual = observed(point)
                    missing = None if actual is None else len(faces-actual)
                    extra = None if actual is None else len(actual-faces)
                    witness = occluder(point) if missing else None
                    if witness is not None and point not in nodes:
                        errors.append({'reason':'occluder_owner_not_closed','cell':list(point)})
                    if (missing or extra or actual is None) and witness is None:
                        errors.append({'reason':'owner_union_not_exact','cell':list(point),'missing':missing,'extra':extra})
                    cell_rows.append({'position':list(point),'actual':current.state('minecraft:overworld',point)[1],
                                      'owners':[list(r) for r,(_,parts,_) in nodes.items() if point in parts],
                                      'missing':missing,'extra':extra,'preservedOccluder':witness})
                has_occlusion = any(c['preservedOccluder'] for c in cell_rows)
                return {'result':'UNRESOLVED' if errors or not nodes else 'HISTORICAL_OWNER_CLOSURE_WITH_OCCLUSION' if has_occlusion else 'EXACT_HISTORICAL_OWNER_CLOSURE',
                        'scope':'Historical source membership only; not runtime/conversion/world PASS',
                        'sourceSha256':SOURCE_SHA,'moddedSha256':MODDED_SHA,
                        'seed':list(origin),'searchRadius':radius,'nodeLimit':node_limit,
                        'objects':[{'sourceRoot':list(root),'state':state,'evidence':provenance,
                                    'cells':[list(p) for p in sorted(cells)]} for root,(state,cells,provenance) in nodes.items()],
                        'cells':cell_rows,'errors':errors,'rejectedPartialOwners':list(rejected.values())}
            results = []
            covered = {}
            for number, origin in enumerate(origins):
                origin = tuple(origin)
                if origin in covered:
                    results.append({'result':'REFER_TO_PROVEN_CLOSURE','seed':list(origin),'closureIndex':covered[origin]})
                    continue
                result = trace_one(origin)
                if result['result'] != 'UNRESOLVED':
                    for obj in result['objects']: covered[tuple(obj['sourceRoot'])] = len(results)
                results.append(result)
                if number % 100 == 0: print('owner closure audit',number,'/',len(origins),flush=True)
            return results
    finally: source.close(); current.close()


def trace(origin, radius=3, node_limit=128):
    return trace_many([origin], radius, node_limit)[0]


if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('x',type=int);p.add_argument('y',type=int);p.add_argument('z',type=int)
    p.add_argument('output',type=Path);p.add_argument('--radius',type=int,default=3);p.add_argument('--node-limit',type=int,default=128)
    a=p.parse_args();data=trace((a.x,a.y,a.z),a.radius,a.node_limit)
    a.output.write_text(json.dumps(data,indent=2)+'\n',encoding='utf8')
    print(data['result'],'objects',len(data['objects']),'cells',len(data['cells']),'errors',len(data['errors']),flush=True)
