"""Generation-time support-plane normalization, independent of source matching.

No family IDs, semantic regrouping, texture editing or world mutations. Existing
authored gameplay primitives win over geometric guesses. Meshes remain immutable:
the renderer already consumes the precomputed canonical render_mesh.offset.
"""
from __future__ import annotations

import argparse
import copy
import gzip
import hashlib
import itertools
import json
import math
from pathlib import Path

from logical_contract_v2 import BUDGETS, MATRICES, box_cells, rotate_box, rotate_cell

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources/bloodborne_blocks/logical'
EPSILON = 1e-6
FUNCTIONAL = {'DOOR', 'GATE', 'FENCE', 'WALL', 'STAIRS'}
TRANSFORM = {'rotations': MATRICES}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf8'))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False) + '\n'
    if not path.exists() or path.read_text(encoding='utf8') != content:
        path.write_text(content, encoding='utf8')


def points(mesh, offset):
    return [tuple(vertex[i] + offset[i] for i in range(3))
            for polygon in mesh['polygons'] for vertex in polygon['vertices']]


def bounds(vertices):
    if not vertices or any(not math.isfinite(n) for point in vertices for n in point):
        raise ValueError('empty/nonfinite rendered mesh')
    return [min(v[i] for v in vertices) for i in range(3)] + [max(v[i] for v in vertices) for i in range(3)]


def volume(box):
    return math.prod(max(0, box[i+3]-box[i]) for i in range(3))


def union_volume(boxes):
    # Authored Contract V2 boxes are few. Avoid exponential work on invalid input.
    if len(boxes) > 8:
        return None
    total = 0
    for size in range(1, len(boxes)+1):
        for group in itertools.combinations(boxes, size):
            intersection = [max(b[i] for b in group) for i in range(3)] + [min(b[i+3] for b in group) for i in range(3)]
            total += (-1)**(size+1) * volume(intersection)
    return total


def source_identity(family):
    return {'canonical_anchor': family['canonical_anchor'], 'placement_policy': family.get('placement_policy'),
            'review_source_patterns': family.get('review_source_patterns'),
            'states': {key: {'rotation': state['rotation'], 'mesh': state['render_mesh']['id'],
                            'migration_source_pattern': state['migration_source_pattern']}
                       for key, state in family['states'].items()}}


def mount(family):
    """Only explicit policy/plane is evidence; model names/IDs are not evidence."""
    policy = family.get('placement_policy')
    explicit = family.get('support_plane')
    if policy in ('FLOOR', 'GROUND'):
        if explicit and (explicit.get('normal') != [0, 1, 0] or explicit.get('distance') != 0):
            return None, 'floor policy conflicts with explicit support plane'
        return {'normal': [0, 1, 0], 'distance': 0, 'source': 'placement_policy'}, None
    if explicit:
        normal, distance = explicit.get('normal'), explicit.get('distance')
        if (normal not in ([1,0,0], [-1,0,0], [0,1,0], [0,-1,0], [0,0,1], [0,0,-1])
                or type(distance) not in (int, float) or not math.isfinite(distance)
                or abs(distance) > 64 or not explicit.get('reason')):
            return None, 'explicit support plane must have axial inward normal, bounded distance and reason'
        return {**explicit, 'source': 'explicit canonical-north plane'}, None
    return None, 'wall/ceiling/hanging/support policy has no explicit plane; no floor inference'


def plane_for_state(plane, state):
    normal = rotate_cell(plane['normal'], state['rotation'], TRANSFORM)
    # Plane is in the canonical north frame and rotates about the same pivot.
    pivot = (.5, 0, .5)
    distance = plane['distance'] + sum((normal[i]-plane['normal'][i])*pivot[i] for i in range(3))
    return normal, distance


def clearance(vertices, normal, distance):
    return min(sum(normal[i]*v[i] for i in range(3))-distance for v in vertices)


def translate_boxes(boxes, shift):
    return [[round(value+shift[i % 3], 9) for i, value in enumerate(box)] for box in boxes]


def collision_clip_to_floor(boxes):
    # The support block itself owns y<0. Keep the above-floor physical volume.
    return [([box[0], 0, box[2], *box[3:]] if box[1]<0 else box)
            for box in boxes if box[4] > EPSILON]


def primitive_cells(boxes):
    return {c for box in boxes for c in box_cells(box)}


def projection_key(key):
    return ','.join(p for p in key.split(',') if not p.startswith(('facing=', 'visual=')))


def collision_proposals(family, definitions, meshes):
    """Fallback only for absent/invalid/excessive ordinary-decor policies."""
    policy = family.get('collision_policy')
    if policy in FUNCTIONAL:
        return {}, None
    excessive = any(len(s['collision_footprint']['boxes']) > 3 for s in family['states'].values())
    if policy in BUDGETS and not excessive:
        return {}, None
    if definitions.get(family['id'], {}).get('behavior', 'static') != 'static':
        return {}, 'no approved physical semantics for non-static family'
    from support_collision import simplify
    grouped = {}
    for key, state in family['states'].items():
        grouped.setdefault(projection_key(key), []).append((key, state))
    proposals = {}
    for group, rows in grouped.items():
        proposal = None
        for key, state in sorted(rows, key=lambda row: (row[1]['rotation'], row[0])):
            render = state['render_mesh']
            polygons = copy.deepcopy(meshes[render['id']]['polygons'])
            yaw = (360-state['rotation']) % 360
            for polygon in polygons:
                for vertex in polygon['vertices']:
                    point = tuple(vertex[i]+render['offset'][i]-(.5,0,.5)[i] for i in range(3))
                    rotated = rotate_cell(point, yaw, TRANSFORM)
                    vertex[:3] = [rotated[i]+(.5,0,.5)[i] for i in range(3)]
            candidate = simplify(polygons, family.get('semantic_kind'))
            if not isinstance(candidate, dict) or candidate.get('status') != 'PROPOSED':
                return {}, candidate.get('reason', 'primary physical mass is ambiguous') if isinstance(candidate, dict) else 'primary physical mass is ambiguous'
            if proposal is not None:
                previous, current = sorted(proposal['boxes']), sorted(candidate['boxes'])
                if (proposal['policy'] != candidate['policy'] or len(previous) != len(current)
                        or any(abs(a-b)>EPSILON for x,y in zip(previous,current) for a,b in zip(x,y))):
                    return {}, 'facing/visual states do not prove equivalent canonical physical masses: '+key
            else:
                proposal = candidate
        if not any('facing=' in k for k, _ in rows):
            original = sorted(proposal['boxes'])
            turned = sorted(rotate_box(b, 90, TRANSFORM) for b in original)
            if any(abs(a-b)>EPSILON for x,y in zip(original,turned) for a,b in zip(x,y)):
                return {}, 'asymmetric automatic collision requires existing facing support'
        proposals[group] = proposal
    return proposals, None


def normalize(contracts, meshes, definitions=None, hidden=()):
    """Pure deterministic transformation. Returns new contracts and full evidence."""
    result = copy.deepcopy(contracts)
    definitions = definitions or {}
    rows = []
    for family in result['families']:
        identity = source_identity(family)
        before_family = copy.deepcopy(family)
        plane, ambiguous = mount(family)
        is_floor = family.get('placement_policy') in ('FLOOR', 'GROUND')
        proposals, collision_ambiguous = collision_proposals(family, definitions, meshes)
        if proposals:
            count = max(len(p['boxes']) for p in proposals.values())
            semantic_policies = {p.get('policy') for p in proposals.values()}
            family['collision_policy'] = next(iter(semantic_policies)) if len(semantic_policies)==1 and next(iter(semantic_policies)) in ('TRUNK','POST') else {0:'NONE',1:'SIMPLE_BOX',2:'TWO_BOX',3:'THREE_BOX'}[count]
            family['collision_justification'] = 'Generation-time conservative primary-mass approximation; see support-normalization audit, not per-element/per-cell collision.'
        states = []
        for key, state in family['states'].items():
            original = before_family['states'][key]
            render = state['render_mesh']
            mesh = meshes[render['id']]
            vertices = points(mesh, render['offset'])
            old_bounds = bounds(vertices)
            old_boxes = original['collision_footprint']['boxes']
            old_cells = {tuple(c) for c in original['interaction_footprint']['cells']}
            shift = [0.,0.,0.]
            findings = []
            old_clearance = new_clearance = None
            if plane:
                normal, distance = plane_for_state(plane, state)
                old_clearance = clearance(vertices, normal, distance)
                if old_clearance < -EPSILON:
                    shift = [round(-old_clearance*n, 9) for n in normal]
                    render['offset'] = [round(render['offset'][i]+shift[i], 9) for i in range(3)]
                    state['selection_footprint']['boxes'] = translate_boxes(state['selection_footprint']['boxes'], shift)
                    findings.append('RENDER_BELOW_SUPPORT_PLANE')
                new_clearance = clearance(points(mesh, render['offset']), normal, distance)
            else:
                findings.append('AMBIGUOUS_MOUNT_POLICY')
            proposal = proposals.get(projection_key(key))
            if proposal:
                boxes = [rotate_box(b, state['rotation'], TRANSFORM) for b in proposal['boxes']]
            else:
                boxes = copy.deepcopy(old_boxes)
            # Appearance is moved once, not re-anchored. Existing physical boxes
            # follow the same correction; no raw source offsets are ever changed.
            if any(shift):
                boxes = translate_boxes(boxes, shift)
            if is_floor:
                boxes = collision_clip_to_floor(boxes)
            changed_shape = boxes != old_boxes
            state['collision_footprint']['boxes'] = boxes
            if changed_shape:
                # Preserve explicitly authored interaction-only cells, not old
                # collision storage slices. Never derive ownership from mesh bounds.
                interaction_only = old_cells-primitive_cells(old_boxes)
                cells = interaction_only | primitive_cells(boxes) | {(0,0,0)}
            else:
                cells = old_cells
            removed_below = sorted(c for c in old_cells if is_floor and c[1]<0)
            if is_floor:
                cells = {c for c in cells if c[1]>=0} | {(0,0,0)}
            state['interaction_footprint']['cells'] = [list(c) for c in sorted(cells)]
            old_below = sum(c[1]<0 for c in old_cells) if is_floor else None
            new_below = sum(c[1]<0 for c in cells) if is_floor else None
            if old_below:
                findings.extend(['HELPER_BELOW_SUPPORT_PLANE', 'GROUND_OBJECT_HAS_HELPER_BELOW_ANCHOR'])
            functional = family.get('collision_policy') in FUNCTIONAL
            if len(old_boxes)>3 and not functional:
                findings.append('EXCESSIVE_COLLISION_BOXES')
            if collision_ambiguous:
                findings.append('AMBIGUOUS_COLLISION_POLICY')
            effective_bounds = bounds(points(mesh, render['offset']))
            collision_volume = union_volume(boxes)
            ratio = collision_volume/volume(effective_bounds) if collision_volume is not None and volume(effective_bounds)>EPSILON else None
            if ratio is not None and ratio>1+EPSILON:
                findings.append('COLLISION_EXCESSIVE_EMPTY_VOLUME')
            fail = ((is_floor and plane is None)
                    or (new_clearance is not None and new_clearance < -EPSILON) or bool(new_below)
                    or family.get('collision_policy') not in BUDGETS
                    or len(boxes)>BUDGETS.get(family.get('collision_policy'), 3)
                    or (len(boxes)>3 and not functional) or len(cells)>512)
            status = 'FAIL' if fail else 'WARN' if ambiguous or collision_ambiguous or 'COLLISION_EXCESSIVE_EMPTY_VOLUME' in findings else 'PASS'
            states.append({'state':key, 'status':status, 'findings':findings,
                'render_elements':len(mesh['polygons']), 'render_element_kind':'render polygons (not source cuboids)',
                'old_render_min_y':old_bounds[1], 'applied_y_correction':shift[1], 'applied_render_correction':shift,
                'final_render_min_y':effective_bounds[1], 'old_support_clearance':old_clearance, 'final_support_clearance':new_clearance,
                'old_collision_boxes':len(old_boxes), 'new_collision_boxes':len(boxes), 'collision_policy':family.get('collision_policy'),
                'collision_to_render_bounds_volume_ratio':ratio,
                'multiple_boxes_reason':family.get('collision_justification') if len(boxes)>1 else None,
                'collision_evidence':proposal.get('evidence') if proposal else {'source':'existing authored gameplay primitives; not render-derived'},
                'collision_ambiguous_reason':collision_ambiguous,
                'old_helper_cells':len(old_cells-{(0,0,0)}), 'helper_cells':len(cells-{(0,0,0)}),
                'old_helpers_below_support_plane':old_below, 'helpers_below_support_plane':new_below,
                'removed_below_cells':[list(c) for c in removed_below],
                'old_storage_collision_slices':sum(bool(set(box_cells(b)) & {c}) for c in old_cells for b in old_boxes),
                'new_storage_collision_slices':sum(bool(set(box_cells(b)) & {c}) for c in cells for b in boxes)})
        if source_identity(family) != identity:
            raise AssertionError('normalization changed source patterns/master identity')
        statuses = {s['status'] for s in states}
        rows.append({'id':family['id'], 'hidden_compatibility':family['id'] in hidden,
            'placement_policy':family.get('placement_policy'), 'support_plane':plane, 'ambiguous_mount_reason':ambiguous,
            'status':'FAIL' if 'FAIL' in statuses else 'WARN' if 'WARN' in statuses else 'PASS', 'states':states,
            'source_identity_sha256':digest(identity)})
    all_states = [s for row in rows for s in row['states']]
    report = {'schemaVersion':1, 'algorithm':'support-normalization-v1', 'epsilon':EPSILON,
        'metrics_note':'collision totals count global primitives per declared state, including BASE/ALT; storage slices are NOT independent authored boxes',
        'source_identity_unchanged':True, 'mesh_payload_unchanged':True,
        'summary':{'families':len(rows), 'states':len(all_states),
            'ground_families':sum(row['placement_policy'] in ('FLOOR','GROUND') for row in rows),
            'below_support_families':sum(any('RENDER_BELOW_SUPPORT_PLANE' in s['findings'] for s in row['states']) for row in rows),
            'automatically_corrected_families':sum(any(any(abs(v)>EPSILON for v in s['applied_render_correction']) for s in row['states']) for row in rows),
            'ambiguous_mount_families':sum(row['support_plane'] is None for row in rows),
            'old_collision_boxes':sum(s['old_collision_boxes'] for s in all_states),
            'new_collision_boxes':sum(s['new_collision_boxes'] for s in all_states),
            'max_ordinary_collision_boxes':max((s['new_collision_boxes'] for s in all_states if s['collision_policy'] not in FUNCTIONAL), default=0),
            'families_with_old_helpers_below':sum(any(s['old_helpers_below_support_plane'] for s in row['states']) for row in rows),
            'families_with_helpers_below':sum(any(s['helpers_below_support_plane'] for s in row['states']) for row in rows),
            'fail_families':sum(row['status']=='FAIL' for row in rows)}, 'families':rows}
    return result, report


def markdown(report):
    lines = ['# Contract V2 support-plane / collision audit', '',
        'Generation-time only. No source pattern, master position, mesh payload or semantic boundary changed.', '',
        'Collision counts below are global primitives **per declared state**, not clipped helper-cell storage slices.',
        'A source cuboid count cannot be reconstructed from an untagged polygon mesh; `render_elements` counts actual render polygons.', '',
        '```json', json.dumps(report['summary'], indent=2), '```', '',
        '| ID | Placement | old minY | Y correction | old → new boxes (sum) | Policy | helpers (max) | below before → after | Status |',
        '| --- | --- | ---: | ---: | ---: | --- | ---: | ---: | --- |']
    for row in report['families']:
        ss = row['states']
        lines.append(f"| {row['id']} {'(hidden)' if row['hidden_compatibility'] else ''} | {row['placement_policy']} | {min(s['old_render_min_y'] for s in ss):.6g} | {max(s['applied_y_correction'] for s in ss):.6g} | {sum(s['old_collision_boxes'] for s in ss)} → {sum(s['new_collision_boxes'] for s in ss)} | {ss[0]['collision_policy']} | {max(s['helper_cells'] for s in ss)} | {max(s['old_helpers_below_support_plane'] or 0 for s in ss)} → {max(s['helpers_below_support_plane'] or 0 for s in ss)} | {row['status']} |")
    lines += ['', '## Manual review / warnings', '']
    for row in report['families']:
        flags = sorted({flag for s in row['states'] for flag in s['findings']})
        if row['status']!='PASS':
            lines.append(f"- `{row['id']}`: {', '.join(flags)}. {row['ambiguous_mount_reason'] or ''}".rstrip())
    lines += ['', 'Already-simple authored collision is retained, not modified to manufacture before/after reductions.',
        'Fallback simplifier tests include synthetic 27 → 1 evidence; those are not claimed as existing family counts.',
        'Wall-adjacent families without an explicit mount plane remain unchanged and AMBIGUOUS_MOUNT_POLICY.', '']
    return '\n'.join(lines)


def run(resources=RES, *, apply=False, report_path=None, baseline_path=None):
    from sync_reviewed_geometry import profile
    resources = Path(resources)
    before = read(resources/'contracts-v2.json')
    if baseline_path:
        baseline_path = Path(baseline_path)
        if baseline_path.exists():
            raise FileExistsError('Do not replace historical normalization input: '+str(baseline_path))
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        baseline_path.write_bytes(gzip.compress(json.dumps(before, sort_keys=True, separators=(',', ':')).encode(), mtime=0))
    with gzip.open(resources/'meshes.json.gz', 'rt', encoding='utf8') as stream:
        meshes = json.load(stream)
    definitions = {d['id']:d for d in read(resources/'definitions.json')['blocks']}
    hidden = set(read(resources/'hidden-items.json'))
    normalized, report = normalize(before, meshes, definitions, hidden)
    report['input_contract_sha256'] = digest(before)
    report['output_contract_sha256'] = digest(normalized)
    changed = normalized != before
    if apply and not report['summary']['fail_families']:
        write(resources/'contracts-v2.json', normalized)
        geometry = read(resources/'geometry.json')
        for family in normalized['families']:
            geometry['blocks'][family['id']] = profile(family)
        write(resources/'geometry.json', geometry)
    elif changed and not apply:
        raise ValueError('Contract V2 support data needs generation: run normalize_support_contracts.py --write')
    if report_path:
        report_path = Path(report_path)
        # A repeated idempotent generation must not erase the original before/after evidence.
        prior = read(report_path) if report_path.exists() else None
        if not (not changed and not report['summary']['fail_families'] and prior
                and not prior['summary']['fail_families']
                and prior.get('output_contract_sha256')==report['output_contract_sha256']):
            write(report_path, report)
            report_path.with_suffix('.md').write_text(markdown(report), encoding='utf8')
        else:
            report = prior
    print(json.dumps({'support_normalization':report['summary'], 'changed':changed}), flush=True)
    if report['summary']['fail_families']:
        raise ValueError('support normalization has unresolved QA FAIL; inspect audit, do not weaken the gate')
    return report


if __name__=='__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=RES)
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--baseline', type=Path, help='optional immutable input snapshot for this explicit audit pass')
    args = parser.parse_args()
    run(args.resources, apply=args.write, report_path=args.report, baseline_path=args.baseline)
