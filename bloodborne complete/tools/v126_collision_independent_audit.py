"""Read-only V12.6 cuboid/source and player-passage audit; no Java or build.

Only writes an independent report under docs/v12.6/collisions. Continuous
Minkowski-box sweeps model an axis-aligned 0.6 x 1.8 Minecraft player, and
mirror ImportedCollisionMath's final six-face clipping after instance shift.
This is an offline geometry check, not a replacement for native GameTests.
"""
import hashlib
import itertools
import json
import math
from pathlib import Path

import numpy as np
from gallery_platform_plan import ModelGeometry
from v125_collision_geometry import box_solids, envelope, inside, oriented, solid_parts

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources/bloodborne_dw'
OUT = ROOT / 'docs/v12.6/collisions'


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')


def samples(box):
    low, high = np.asarray(box['from']), np.asarray(box['to'])
    return np.asarray([low + (high-low)*np.asarray(p)
                       for p in itertools.product((0., .125, .25, .5, .75, .875, 1.), repeat=3)])


def crop_runtime(boxes, shift=(0., 0., 0.), mount=0.):
    """Table boxes already have yaw. Units remain 16 per block here."""
    if not boxes:
        return []
    moved = [{'from': [b['from'][i]+shift[i]+(mount if i == 1 else 0.) for i in range(3)],
              'to': [b['to'][i]+shift[i]+(mount if i == 1 else 0.) for i in range(3)]} for b in boxes]
    low = [min(b['from'][i] for b in moved) for i in range(3)]
    high = [max(b['to'][i] for b in moved) for i in range(3)]
    for axis in range(3):
        if -8. < low[axis] < 0.:
            low[axis] = 0.
        if 16. < high[axis] < 24.:
            high[axis] = 16.
    result = []
    for b in moved:
        lo = [max(low[i], b['from'][i]) for i in range(3)]
        hi = [min(high[i], b['to'][i]) for i in range(3)]
        if all(lo[i] < hi[i] for i in range(3)):
            result.append({'from': lo, 'to': hi})
    return result


def transform_point(point, yaw):
    angle = math.radians(yaw*45.)
    c, s = math.cos(angle), math.sin(angle)
    if yaw % 2 == 0:
        c, s = round(c), round(s)
    x, y, z = point
    return np.asarray([8.+c*(x-8.)-s*(z-8.), y, 8.+s*(x-8.)+c*(z-8.)])


def rotate_vector(point, yaw):
    return transform_point(np.asarray(point)+[8., 8., 8.], yaw)-[8., 8., 8.]


def outside_distance(points, solids):
    """All native cuboids use orthogonal basis vectors; closest-point clamp."""
    distance = np.full(len(points), np.inf)
    for solid in solids:
        origin = solid[0]
        basis = np.column_stack((solid[4]-origin, solid[2]-origin, solid[1]-origin))
        coords = (points-origin) @ np.linalg.inv(basis).T
        closest = origin+np.clip(coords, 0., 1.) @ basis.T
        distance = np.minimum(distance, np.linalg.norm(points-closest, axis=1))
    return float(np.max(distance))


def sweep_intersections(boxes, start, finish):
    # Player feet = 0.001 block, height 1.8, width/depth 0.6. Expand boxes
    # by player half extents, then intersect the player-centre line exactly.
    half = np.asarray([4.8, 14.4, 4.8])
    delta = finish-start
    found = []
    for index, b in enumerate(boxes):
        lo, hi = np.asarray(b['from'])-half, np.asarray(b['to'])+half
        entry, leave = 0., 1.
        for axis in range(3):
            if abs(delta[axis]) < 1e-10:
                if start[axis] <= lo[axis]+1e-8 or start[axis] >= hi[axis]-1e-8:
                    entry, leave = 2., -1.
                    break
            else:
                one, two = (lo[axis]-start[axis])/delta[axis], (hi[axis]-start[axis])/delta[axis]
                entry, leave = max(entry, min(one, two)), min(leave, max(one, two))
        if entry < leave-1e-8:
            found.append({'boxIndex': index, 'box': b, 'entryT': entry, 'exitT': leave})
    return found


def door_source(door, state, profile, models):
    parts = door['partitionedClosedParts'] if state == 'closed' else door['openParts']
    hull = [{'from': [-1000., -1000., -1000.], 'to': [1000., 1000., 1000.]}]
    return solid_parts({'parts': parts, 'collision': hull}, models, profile, panel_tolerance=.500001)


def main():
    file = RES / 'imported-collision-v126.json'
    table = read(file)
    generation = read(OUT / 'generation.json')
    assert generation['resourceSha256'] == sha(file), 'Production tables changed since generation report'
    applied = read(ROOT / 'docs/v12.6/applied-content.json')
    reasons = {r['registryId']: r for r in applied['groups']}
    doors = []
    for stage in ['build/v126-door-plan', 'build/v126-city-mounteddoor']:
        doors += read(ROOT / stage / 'door-manifest.json')['doors']
    door_lookup = {(d['registryId'], d['variant']): d for d in doors}
    model_resource_failures = []
    checked_models = set()
    checked_textures = set()
    for door in doors:
        for entry in door['models']:
            namespace, path = entry['model'].split(':', 1)
            current = ROOT/'src/architecture/resources/assets'/namespace/'models'/(path+'.json')
            checked_models.add(entry['model'])
            if not current.is_file() or sha(current) != entry['sha256']:
                model_resource_failures.append({'kind': 'STAGED_MODEL_NOT_APPLIED_EXACTLY', 'model': entry['model']})
            for texture in entry['sourceTextureDependencies']:
                namespace, path = texture.split(':', 1)
                checked_textures.add(texture)
                if not (ROOT/'src/architecture/resources/assets'/namespace/'textures'/(path+'.png')).is_file():
                    model_resource_failures.append({'kind': 'MISSING_SOURCE_TEXTURE_DEPENDENCY', 'texture': texture})
    source_sha = {}
    specs = {}
    for registry in table['objects']:
        path = RES / 'composite' / (registry.split(':')[1] + '.json')
        specs[registry] = read(path)
        source_sha[registry] = sha(path)
        assert specs[registry]['id'] == registry and specs[registry]['simplePhysicalCollision']
        assert len(specs[registry]['variants']) == len(table['objects'][registry])
    for door in doors:
        variant = specs[door['registryId']]['variants'][door['variant']]
        if variant['poses']['closed']['parts'] != door['sourceClosedParts']:
            model_resource_failures.append({'kind': 'CLOSED_ORIGINAL_SOURCE_GEOMETRY_CHANGED', 'registryId': door['registryId'], 'variant': door['variant']})
        if variant['poses']['open']['parts'] != door['openParts']:
            model_resource_failures.append({'kind': 'OPEN_PARTS_DIFFER_FROM_APPROVED_SOURCE_PARTITION', 'registryId': door['registryId'], 'variant': door['variant']})
    models = ModelGeometry()
    structural = []
    empty = []
    outside = []
    diagnostics = []
    rows_checked = 0
    for shape_index, shape in enumerate(table['shapes']):
        assert len(shape) == 8
        for yaw, row in enumerate(shape):
            rows_checked += 1
            if len(row['collision']) > 12 or len(row['aim']) > 1:
                structural.append({'shape': shape_index, 'yaw': yaw, 'kind': 'BOX_BUDGET'})
            for kind in ['collision', 'aim']:
                for b in row[kind]:
                    if not all(math.isfinite(v) for v in b['from']+b['to']) or not all(b['from'][i] < b['to'][i] for i in range(3)):
                        structural.append({'shape': shape_index, 'yaw': yaw, 'kind': 'INVALID_BOX', 'box': b})
    cache = {}
    checked = set()
    case_counts = {}
    bounds_failures = []
    source_union_checks = 0
    for case in generation['cases']:
        registry, variant, yaw = case['registryId'], case['variant'], case['yaw']
        state, profile = case['pose'], case['profile']
        selector = state+profile.title()
        row = table['shapes'][table['objects'][registry][variant][selector]][yaw]
        boxes = row['collision']
        pose_map = specs[registry]['variants'][variant]['poses']
        pose = pose_map.get(state, pose_map['closed'])
        case_counts[case['policy']] = case_counts.get(case['policy'], 0)+1
        if not pose['collision']:
            if boxes:
                structural.append({'kind': 'PASSABLE_TURNED_SOLID', 'case': case})
            continue
        if not boxes:
            empty.append({'kind': 'EMPTY_TABLE_FOR_SOLID_POSE', 'case': case})
            continue
        cropped = crop_runtime(boxes)
        if not cropped:
            empty.append({'kind': 'ALL_BOXES_REMOVED_BY_DEFAULT_ROOT_CROP', 'case': case, 'boxes': boxes})
        source_key = (registry, variant, state, profile)
        if source_key not in cache:
            door = door_lookup.get((registry, variant))
            if door:
                cache[source_key] = door_source(door, state, profile, models)
            else:
                cache[source_key] = solid_parts(pose, models, profile, panel_tolerance=1.)
        originals = cache[source_key]
        if not originals:
            diagnostics.append({'kind': 'NO_FINITE_RENDERED_SOLID_GEOMETRY', 'case': case})
            continue
        source = oriented(originals, yaw)
        lo, hi = envelope(source)
        sig = (source_key, yaw, json.dumps(boxes, sort_keys=True))
        if sig in checked:
            continue
        checked.add(sig)
        for index, b in enumerate(boxes):
            if np.any(np.asarray(b['from']) < lo-1e-5) or np.any(np.asarray(b['to']) > hi+1e-5):
                bounds_failures.append({'kind': 'BEYOND_RENDERED_BOUNDS', 'case': case, 'boxIndex': index, 'box': b,
                                        'sourceBounds': {'from': lo.tolist(), 'to': hi.tolist()}})
        door = door_lookup.get((registry, variant))
        reason = reasons.get(registry, {})
        # Ordinary <=one-cell non-passable objects may have one simple model
        # envelope by user instruction. Ladder zones and functional assembly
        # supports are purposeful domains, not exact decorative solids.
        check_union = bool(door) or (case['policy'] == 'FEW_INSCRIBED_ORDINARY_CUBOIDS'
                      and reason.get('kind') not in ('approved-complete-assembly', 'functional-ladder')
                      and registry != 'bloodborne_dw:source_hold_ladder_02')
        if check_union:
            source_union_checks += 1
            for index, b in enumerate(boxes):
                pts = samples(b)
                valid = inside(pts, source)
                if not np.all(valid):
                    outside.append({'kind': 'CUBOID_SAMPLE_OUTSIDE_SOURCE_UNION', 'case': case,
                                    'boxIndex': index, 'box': b, 'outsideCount': int(np.count_nonzero(~valid)),
                                    'totalSamples': len(pts), 'firstOutsidePoints': pts[~valid][:4].tolist(),
                                    'maxSampleDistanceOutsideSourceUnits': outside_distance(pts[~valid], source)})
    passages = []
    passage_failures = []
    city = read(ROOT / 'docs/v12.6/city/city-final-aggregated.json')
    # Descriptor entries are audited before instance transform, then ordinary
    # root placement and each actual source-shifted mounted city instance.
    for d in doors:
        registry, variant = d['registryId'], d['variant']
        normal_x = d['mainId'] == '93160'
        x, z = (4.5, 8.) if normal_x else (8.04, 8.) if d['mainId'] == '90266' else (8., 6.) if d['mainId'] == '93152' else (8., 7.) if d['mainId'] == '93153' else (8., 8.) if d['mainId'] == '90031' else (8., 0.)
        centre = np.asarray([x, 14.416, z])
        normal = np.asarray([1., 0., 0.] if normal_x else [0., 0., 1.])
        for profile in ['base', 'alt']:
            for yaw in range(8):
                c = transform_point(centre, yaw)
                direction = transform_point(centre+normal, yaw)-c
                start, finish = c-direction*48., c+direction*48.
                for state in ['closed', 'open']:
                    selector = state+profile.title()
                    row = table['shapes'][table['objects'][registry][variant][selector]][yaw]
                    runtime = crop_runtime(row['collision'])
                    hit = sweep_intersections(runtime, start, finish)
                    expected = state == 'closed'
                    record = {'mainId': d['mainId'], 'registryId': registry, 'variant': variant, 'yaw': yaw,
                              'profile': profile, 'state': state, 'expected': 'BLOCKED' if expected else 'CLEAR',
                              'observed': 'BLOCKED' if hit else 'CLEAR', 'status': 'PASS' if bool(hit) == expected else 'FAIL',
                              'runtimeBoxes': len(runtime), 'normalAxisAtYawZero': 'X' if normal_x else 'Z',
                              'playerCentreLine': [start.tolist(), finish.tolist()]}
                    if bool(hit) != expected:
                        record['intersectedBoxes'] = hit
                        passage_failures.append(record)
                    passages.append(record)
    city_checked = 0
    city_empty = []
    city_door_passages = []
    city_reference_stale = []
    for owner in city['owners']:
        registry = owner['registryId']
        if registry not in table['objects']:
            continue
        variant = int(owner['properties'].get('variant', '0'))
        yaw = int(owner['properties'].get('rotation', '0')) & 7
        profile = owner['properties'].get('profile', 'base')
        shift = owner.get('payload', {}).get('SourceShift', owner.get('sourceShift', [0., 0., 0.]))
        shifted = rotate_vector(np.asarray(shift)*16., yaw)
        mount = (float(owner.get('mountY', owner.get('payload', {}).get('MountY', 0.)))
                 +float(owner.get('payload', {}).get('VerticalOffset', 0.)))*16.
        stored_reference = owner.get('currentDescriptorReference', {})
        if stored_reference.get('sha256') and stored_reference['sha256'] != source_sha[registry]:
            city_reference_stale.append({'registryId': registry, 'sourcePosition': owner['sourcePosition']})
        for state in ['closed', 'open']:
            selector = state+profile.title()
            row = table['shapes'][table['objects'][registry][variant][selector]][yaw]
            runtime = crop_runtime(row['collision'], shifted, mount)
            city_checked += 1
            if row['collision'] and not runtime:
                city_empty.append({'registryId': registry, 'variant': variant, 'yaw': yaw, 'state': state,
                                   'sourcePosition': owner['sourcePosition'], 'sourceShift': shift, 'mount': mount/16.})
            if (registry, variant) not in door_lookup:
                continue
            door = door_lookup[registry, variant]
            origin_case = next(p for p in passages if p['registryId'] == registry and p['variant'] == variant
                               and p['yaw'] == yaw and p['profile'] == profile and p['state'] == state)
            shift_world = shifted+[0., mount, 0.]
            start, finish = [np.asarray(p)+shift_world for p in origin_case['playerCentreLine']]
            hit = sweep_intersections(runtime, start, finish)
            expected = state == 'closed'
            record = {'mainId': door['mainId'], 'variant': variant, 'yaw': yaw, 'state': state,
                      'sourcePosition': owner['sourcePosition'], 'sourceShift': shift, 'mountY': mount/16.,
                      'expected': 'BLOCKED' if expected else 'CLEAR', 'observed': 'BLOCKED' if hit else 'CLEAR',
                      'status': 'PASS' if bool(hit) == expected else 'FAIL'}
            if record['status'] == 'FAIL':
                record['intersectedBoxes'] = hit
                passage_failures.append(record)
            city_door_passages.append(record)
    helper = ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/ImportedCollisionV126.java'
    text = helper.read_text(encoding='utf8')
    outline = 'if(!physical.isEmpty())aim.putAll(physical);' in text
    # Check exact implementation signature; use a diagnostic if formatting
    # differs, never infer outline equality just from the table's aim field.
    if not outline:
        outline = 'physical.isEmpty()?ImportedCollisionMath.transform(pose.aim' in text
    failures = model_resource_failures+structural+empty+bounds_failures+outside+passage_failures+city_empty
    report = {'schemaVersion': 1, 'status': 'PASS_OFFLINE_INDEPENDENT' if not failures else 'FAIL_OFFLINE_INDEPENDENT',
              'authorCollisionExport': 'NOT_SUPPLIED_NOT_APPLIED', 'productionCollisionSha256': sha(file),
              'descriptorSha256': source_sha, 'types': len(table['objects']), 'variants': sum(map(len, table['objects'].values())),
              'shapes': len(table['shapes']), 'tableRows': rows_checked, 'generatedPoseProfileYawCases': len(generation['cases']),
              'maximumCuboids': max(len(r['collision']) for s in table['shapes'] for r in s),
              'sourceUnionChecks': source_union_checks, 'samplePattern': '7x7x7 points per unique audited cuboid; native transformed source union',
              'thinDoorPanelToleranceUnits': .5, 'runtimeFinalCropPort': 'ImportedCollisionMath.transform, six global faces, each strict extension<0.5 block',
              'generatedDoorResourcesExactSourceSHA': {'models': len(checked_models), 'textures': len(checked_textures), 'failures': len(model_resource_failures)},
              'outlineMatchesPhysicalSourcePredicate': outline, 'policyCounts': case_counts,
              'failureCounts': {'structural': len(structural), 'empty': len(empty), 'bounds': len(bounds_failures),
                                'sourceUnion': len(outside), 'doorPassage': len(passage_failures), 'cityEmpty': len(city_empty), 'sourceResources': len(model_resource_failures)},
              'failures': failures, 'doorPassages': passages, 'diagnostics': diagnostics,
              'sourceCityInstanceStateChecks': city_checked, 'sourceCityDoorPassages': city_door_passages,
              'sourceCityDescriptorReferencesStaleUntilFinalReaggregation': len(city_reference_stale),
              'nativeGameTests': 'NOT_RUN_BY_THIS_AUDIT', 'clientChecks': 'NOT_RUN_BY_THIS_AUDIT'}
    write(OUT / 'independent-geometry-and-door-passage-proof.json', report)
    write(OUT / ('independent-geometry-and-door-passage-'+sha(file)[:12]+'.json'), report)
    print(json.dumps({k: report[k] for k in ['status', 'types', 'variants', 'shapes', 'tableRows', 'generatedPoseProfileYawCases',
                                          'maximumCuboids', 'sourceUnionChecks', 'failureCounts', 'outlineMatchesPhysicalSourcePredicate']}, ensure_ascii=False))
    print(json.dumps({'firstFailures': failures[:12]}, ensure_ascii=False))


if __name__ == '__main__':
    main()
