"""Read-only geometry signatures for Catalog B review clustering (not migration)."""
from __future__ import annotations

import functools
import hashlib
import json
from collections import Counter, defaultdict

import numpy as np


def _deps():
    from source_assembly_visuals import source_polys
    from catalog_geometry import rotation
    return source_polys, rotation


def _app_key(app):
    return json.dumps({key: value for key, value in app.items() if key != 'offset'}, sort_keys=True, separators=(',', ':'))


@functools.lru_cache(None)
def _local_polys(key):
    source_polys, _rotation = _deps()
    app = json.loads(key); app.pop('offset', None)
    return source_polys([app])[0]


def _poly_token(poly, transform=None, translate=None):
    values = np.asarray(poly['vertices'], float).copy()
    if transform is not None: values[:, :3] = values[:, :3] @ transform.T
    if translate is not None: values[:, :3] -= translate
    # Polygon rotation and winding have no semantic bearing for a signature.
    # Quarter-turn trig noise near a decimal half-step must not change banker's
    # rounding (e.g. 0.515625 vs 0.5156249999999999).
    values = np.round(np.round(values,10), 5)
    values[np.abs(values) < 0.000005] = 0.0  # -0.0 must not split equal art buckets
    rows = [tuple(float(value) for value in row) for row in values]
    variants = []
    for ordered in (rows, list(reversed(rows))):
        variants.extend(tuple(ordered[index:] + ordered[:index]) for index in range(len(ordered)))
    return (poly['texture'], min(variants))


def _hash(value):
    return hashlib.sha256(repr(value).encode()).hexdigest()


def _apps_polys(apps):
    result = []
    for app in apps:
        local = _local_polys(_app_key(app))
        offset = np.asarray(app.get('offset', (0, 0, 0)), float)
        for poly in local:
            copied = dict(poly); vertices = np.asarray(poly['vertices'], float).copy(); vertices[:, :3] += offset
            copied['vertices'] = vertices.tolist(); result.append(copied)
    return result


def _normal_signature(polys):
    if not polys: return _hash(())
    points = np.concatenate([np.asarray(poly['vertices'], float)[:, :3] for poly in polys])
    centre = points.mean(axis=0)
    _source, rotation = _deps()
    options = []
    for yaw in (0, 90, 180, 270):
        matrix = rotation(1, yaw)
        options.append(tuple(sorted(_poly_token(poly, matrix, centre @ matrix.T) for poly in polys)))
    return _hash(min(options))


def _oriented_signature(polys, yaw=0):
    if not polys: return _hash(())
    points = np.concatenate([np.asarray(poly['vertices'], float)[:, :3] for poly in polys])
    _source, rotation = _deps(); matrix = rotation(1, yaw)
    return _hash(tuple(sorted(_poly_token(poly, matrix, points.mean(0) @ matrix.T) for poly in polys)))


def _component_art(component, yaw=0):
    # Preserve every multipart alternative as independent labelled evidence;
    # this intentionally does not construct their cartesian product.
    polys = _apps_polys(component.get('apps', []))
    centre = np.concatenate([np.asarray(p['vertices'])[:,:3] for p in polys]).mean(0) if polys else np.zeros(3)
    _source, rotation = _deps(); matrix = rotation(1,yaw)
    def aligned_signature(values):
        return _hash(tuple(sorted(_poly_token(p,matrix,centre @ matrix.T) for p in values)))
    groups = []
    for choices in component.get('model_choices', []):
        groups.append(tuple(sorted(aligned_signature(_apps_polys(option)) for option in choices)))
    default = aligned_signature(polys)
    return _hash((default, tuple(groups)))


def descriptor(components):
    """Return compact, translation/yaw invariant geometry evidence for a layout."""
    components = list(components)
    entries = []
    union = []
    for component in components:
        polys = _apps_polys(component.get('apps', [])); union.extend(polys)
        if polys:
            points = np.concatenate([np.asarray(poly['vertices'], float)[:, :3] for poly in polys])
            centre = points.mean(axis=0)
        else: centre = np.asarray(component.get('relative', (0, 0, 0)), float)
        arts = [_component_art(component, yaw) for yaw in (0,90,180,270)]
        entries.append({'number': component.get('number'), 'art': min(arts), 'centre': centre.round(10).round(5).tolist(),
                        # One signature jointly covers default and every
                        # alternative at a given global yaw: no per-alt yaw.
                        'orientation_signatures': arts})
    if union:
        all_points = np.concatenate([np.asarray(poly['vertices'], float)[:, :3] for poly in union]); low, high = all_points.min(0), all_points.max(0)
    else: low = high = np.zeros(3)
    bucket = _hash(tuple(sorted(entry['art'] for entry in entries)))
    union_signature = _normal_signature(union)
    union_oriented = [_oriented_signature(union, yaw) for yaw in (0,90,180,270)]
    layouts=[]
    for turn,yaw in enumerate((0,90,180,270)):
        centres,_matrix = _rotate_centres(entries,yaw)
        origin = np.mean(centres,axis=0) if centres else np.zeros(3)
        layout=[]
        for entry,centre in zip(entries,centres):
            relative=np.round(np.round(centre-origin,10),4); relative[np.abs(relative)<.00005]=0.0
            layout.append((entry['orientation_signatures'][turn],tuple(relative.tolist())))
        layouts.append(tuple(sorted(layout)))
    complete_signature = _hash((union_signature,bucket,min(layouts)))
    return {'method': 'source-textured-polygons-v1', 'component_count': len(entries), 'components': entries,
            'grouping_bucket': bucket,
            # Default union alone is insufficient: multipart alternatives are
            # equally source evidence and must invalidate exact identity.
            'exact_geometry_signature': complete_signature,
            'canonical_visual_family_signature': complete_signature,
            'union_orientation_signatures': union_oriented,
            'bounds': [low.round(5).tolist(), high.round(5).tolist()]}


def _rotate_centres(entries, yaw):
    _source, rotation = _deps(); matrix = rotation(1, yaw)
    centres = [np.asarray(entry['centre'], float) @ matrix.T for entry in entries]
    return centres, matrix


def _bottleneck_distance(costs, limit):
    """Minimum maximum distance of a perfect bipartite match (<=24 cells).

    Unlike nearest-neighbour greedy assignment, this criterion is symmetric
    and independent of component numbering, including repeated artwork.
    """
    thresholds = sorted({cost for row in costs for cost in row if cost is not None and cost <= limit})
    def feasible(threshold):
        assigned = {}
        def augment(left, seen):
            for right,cost in enumerate(costs[left]):
                if cost is None or cost>threshold or right in seen: continue
                seen.add(right)
                if right not in assigned or augment(assigned[right],seen):
                    assigned[right]=left; return True
            return False
        return all(augment(left,set()) for left in range(len(costs)))
    if not thresholds or not feasible(thresholds[-1]): return None
    low, high = 0, len(thresholds)-1
    while low<high:
        mid=(low+high)//2
        if feasible(thresholds[mid]): high=mid
        else: low=mid+1
    return thresholds[low]


def equivalent(a, b):
    """Exact or deliberately narrow fuzzy review equivalence, otherwise None."""
    exact = a['exact_geometry_signature'] == b['exact_geometry_signature']
    if a['grouping_bucket'] != b['grouping_bucket'] or a['component_count'] != b['component_count']:
        return None
    by_art_a, by_art_b = defaultdict(list), defaultdict(list)
    for entry in a['components']: by_art_a[entry['art']].append(entry)
    for entry in b['components']: by_art_b[entry['art']].append(entry)
    if set(by_art_a) != set(by_art_b) or any(len(by_art_a[key]) != len(by_art_b[key]) for key in by_art_a): return None
    diagonal = min(np.linalg.norm(np.asarray(d['bounds'][1])-np.asarray(d['bounds'][0])) for d in (a,b))
    allowed = .0001 if exact else min(.51, max(diagonal * .08, 0.0))
    if allowed <= 0: return None
    best = None
    for yaw in (0, 90, 180, 270):
        ca, _matrix = _rotate_centres(a['components'], 0); cb, _ = _rotate_centres(b['components'], yaw)
        shift = np.mean(ca, axis=0) - np.mean(cb, axis=0); distances = []; complete = True
        for art in sorted(by_art_a):
            left = sorted(by_art_a[art], key=lambda row: (row['centre'], row['number']))
            right = sorted(zip(by_art_b[art], [cb[b['components'].index(row)] + shift for row in by_art_b[art]]), key=lambda item: (item[1].tolist(), item[0]['number']))
            costs = [[float(np.linalg.norm(np.asarray(row['centre'])-centre))
                      if row['orientation_signatures'][0] == other['orientation_signatures'][yaw//90] else None
                      for other,centre in right] for row in left]
            distance = _bottleneck_distance(costs,allowed)
            if distance is None: complete=False; break
            distances.append(distance)
        if not complete:
            continue
        max_distance = max(distances, default=0)
        spans_a = np.ptp(np.asarray(ca), axis=0); spans_b = np.ptp(np.asarray(cb), axis=0)
        span_difference = float(np.max(np.abs(spans_a-spans_b)))
        if max_distance <= allowed and span_difference <= 1.01:
            if exact:
                return {'method':'exact_geometry','tolerance':0.0001,'score':1.0,'yaw':yaw}
            candidate = {'method':'fuzzy_review_layout', 'tolerance': {'max_displacement': allowed, 'span':1.01},
                         'score': round(1-max_distance/max(allowed,1e-9), 5), 'yaw':yaw, 'max_displacement':round(max_distance,5), 'span_difference':round(span_difference,5)}
            if best is None or candidate['max_displacement'] < best['max_displacement']: best = candidate
    return best
