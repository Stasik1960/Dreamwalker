"""Generate immutable default-physics tables for the exact registered NEW cohort."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import numpy as np
from gallery_platform_plan import ModelGeometry
from simplify_imported_collisions import rotate_clip_boxes, exact_coalesce
from v125_collision_geometry import axis_aligned, box, box_solids, envelope, fit_large_boxes, geometry_key, inside, oriented, solid_parts, solid_volume, validate_inside_boxes, distributed_primitive_boxes

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
OUT = ROOT / 'docs/v12.5-collisions'
FUNCTIONAL = {'source_hold_ladder_01', 'source_hold_wood_ladder_01', 'source_hold_ladder_02',
              'source_hold_ladder_03', 'source_warped_gargoyle', 'source_acacia_fence_gate',
              'source_deepslate_iron_ore', 'source_potted_flowering_azalea_bush'}


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def bounds_union(values):
    return box([min(value['from'][i] for value in values) for i in range(3)],
               [max(value['to'][i] for value in values) for i in range(3)]) if values else None


def prior_domains(pose, yaw):
    old = pose.get('simpleCollisionByRotation')
    return old[yaw] if old else rotate_clip_boxes(pose['collision'], yaw, crop=False)


def restrict_to_prior(values, domains):
    # Simplification cannot restore intentionally omitted posts, or enlarge an
    # earlier collider because native rescale makes the visible model larger.
    result = []
    for value in values:
        for domain in domains:
            low = [max(value['from'][i], domain['from'][i]) for i in range(3)]
            high = [min(value['to'][i], domain['to'][i]) for i in range(3)]
            if all(high[i]-low[i]>1e-7 for i in range(3)):
                result.append(box(low, high))
    result = exact_coalesce(result) if result else []
    if len(result)>12:
        # Dropping the smallest pieces is permitted undercoverage, never a
        # union hull that could close a passage or restore rotated corners.
        result = sorted(result, key=lambda value:np.prod(np.asarray(value['to'])-value['from']), reverse=True)[:12]
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--only', nargs='*', default=[])
    args = parser.parse_args()
    catalogue = read(RES / 'bloodborne_dw/remaining-catalogue.json')
    paths = [row['path'] for row in catalogue['objects']]
    assert len(set(paths)) == len(paths) == 1045 and 'source_ladder_backing' not in paths
    native = {row['primaryRegistryId'].split(':',1)[1]
              for row in read(ROOT / 'docs/v12.4-dedup/stair-review/functional-type-fragment.json')['groups']
              if row['kind'] == 'stairs'}
    models = ModelGeometry()
    fitted_cache, table_indices, tables, objects, cases = {}, {}, [], {}, []
    for number, path in enumerate(paths):
        if args.only and path not in args.only:
            continue
        spec = read(RES / 'bloodborne_dw/composite' / (path + '.json'))
        assert spec['simplePhysicalCollision'] and spec['id'] == 'bloodborne_dw:' + path
        variants = []
        for variant_index, variant in enumerate(spec['variants']):
            selectors = {}
            for pose_name in ('closed','open'):
                pose = variant['poses'].get(pose_name, variant['poses']['closed'])
                for profile in ('base','alt'):
                    solids = solid_parts(pose, models, profile, panel_tolerance=1. if pose['collision'] else 0.)
                    rotations = []
                    for yaw in range(8):
                        values = []
                        details = {}
                        if not pose['collision']:
                            policy = 'PRESERVED_PASSABLE'
                        elif path in native or path in FUNCTIONAL:
                            old = pose.get('simpleCollisionByRotation')
                            if yaw % 2 == 0:
                                values = old[yaw] if old else rotate_clip_boxes(pose['collision'], yaw, crop=False)
                                policy = 'PRESERVED_FUNCTIONAL_CARDINAL'
                            else:
                                source = oriented(box_solids(pose['collision']), yaw)
                                # A few purposeful volumes retain treads/rails.
                                # The prior orientation domain below keeps the
                                # earlier narrow-doorway exceptions in force.
                                key = (geometry_key(source), 12, .90)
                                if key not in fitted_cache:
                                    fitted_cache[key] = fit_large_boxes(source, 12, .90)
                                values,details = fitted_cache[key]
                                policy = 'FUNCTIONAL_DIAGONAL_INSCRIBED'
                        elif solids:
                            source = oriented(solids, yaw)
                            low,high = envelope(source)
                            spans = high-low
                            upper = min(1.,sum(solid_volume(points) for points in source)/float(np.prod(spans)))
                            centre_occupied = bool(inside(np.asarray([(low+high)/2]),source)[0])
                            ordinary = axis_aligned(source) and (max(spans)<=16.00001 or upper>=.65 and centre_occupied)
                            if ordinary:
                                values = [box(low,high)]
                                policy = 'ONE_ORDINARY_MODEL_BOUNDED_BOX'
                            else:
                                planar_platform = spans[1] < min(spans[0],spans[2])*.65 and max(spans)>24
                                concave = upper<.55 and max(spans)>24 and not planar_platform
                                budget = 12 if concave else 9 if max(spans)>24 else 1
                                coverage = .90 if concave else .86
                                key = (geometry_key(source),budget,coverage)
                                if key not in fitted_cache:
                                    fitted_cache[key] = fit_large_boxes(source,budget,coverage)
                                values,details = fitted_cache[key]
                                policy = 'MAJOR_CONCAVE_SEGMENTS' if concave else 'INSCRIBED_PLATFORM_OR_ROTATED_SOLID'
                        else:
                            # Do not infer passability from texture alpha or a filename.
                            values = rotate_clip_boxes(pose['collision'],yaw,crop=False)
                            policy = 'PRESERVED_NO_FINITE_MODEL_PRIMITIVE'
                        if pose['collision'] and path == 'source_allium' and solids:
                            # Native angle0+rescale moves the actual large panels
                            # well outside the historical erroneous carrier AABB.
                            # Keep a few actual panels, never the old ghost box.
                            source=oriented(solids,yaw)
                            values=distributed_primitive_boxes(source,3)
                            policy='NATIVE_RESCALE_PANEL_SOURCE_SUPPORTS'
                        elif pose['collision'] and policy != 'PRESERVED_FUNCTIONAL_CARDINAL':
                            values = restrict_to_prior(values, prior_domains(pose,yaw))
                            if solids and path not in native and path not in FUNCTIONAL and policy != 'ONE_ORDINARY_MODEL_BOUNDED_BOX':
                                values = validate_inside_boxes(values,source)
                                if not values:
                                    # A coarse candidate can miss a smaller real
                                    # support in the earlier physical domain.
                                    candidates=distributed_primitive_boxes(source,128)
                                    values=validate_inside_boxes(restrict_to_prior(candidates,prior_domains(pose,yaw)),source)
                                    values=sorted(values,key=lambda value:np.prod(np.asarray(value['to'])-value['from']),reverse=True)[:12]
                        assert len(values)<=12
                        aim = []
                        # Also retain an aim fallback if instance clipping later
                        # removes all physical volume; ordinary poses use only physics.
                        if pose.get('selection'):
                            aim = rotate_clip_boxes([bounds_union(pose['selection'])],yaw,crop=False)
                        rotations.append({'collision':values,'aim':aim})
                        cases.append({'registryId':spec['id'],'variant':variant_index,'pose':pose_name,
                                      'profile':profile,'rotation':yaw,'policy':policy,'boxes':len(values),**details})
                    serialized = json.dumps(rotations,sort_keys=True,separators=(',',':'))
                    if serialized not in table_indices:
                        table_indices[serialized] = len(tables)
                        tables.append(rotations)
                    selectors[pose_name+profile.title()] = table_indices[serialized]
            variants.append(selectors)
        objects[spec['id']] = variants
        if number%100 == 0:
            print(json.dumps({'registeredObjectsScanned':number+1,'uniqueFittedOrientations':len(fitted_cache)}),flush=True)
    metadata = {'schemaVersion':1,'unitsPerBlock':16,'baselineCommit':'653ae307ba4c44392ee08969f3890321e95659bb',
                'policy':'dreamwalker-new-inner-default-collision-v125','authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED',
                'registeredNewTypes':1045,'maxBoxesPerOrientation':12,'objects':objects,'shapes':tables}
    OUT.mkdir(parents=True,exist_ok=True)
    raw = (json.dumps(metadata,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf8')
    preview = ROOT / 'build/v125-collision-preview.json'
    preview.write_bytes(raw)
    if args.apply:
        assert not args.only and len(objects)==1045
        target = RES / 'bloodborne_dw/imported-collision-v125.json'
        target.write_bytes(raw)
    report = {'schemaVersion':1,'status':'DEFAULT_TABLES_GENERATED' if args.apply else 'PLAN_TABLES_GENERATED',
              'registeredObjects':len(objects),'selectorPoseProfileRotations':len(cases),
              'uniqueTables':len(tables),'uniqueFittedOrientations':len(fitted_cache),
              'serializedBytes':len(raw),'metadataSha256':hashlib.sha256(raw).hexdigest(),
              'boxOccurrencesStored':sum(len(row['collision'])+len(row['aim']) for table in tables for row in table),
              'maximumBoxes':max(row['boxes'] for row in cases),'policies':dict(Counter(row['policy'] for row in cases)),
              'runtimeRaster':False,'outwardCellBoundarySnapping':False,
              'formerlyNonpassablePanelSupportToleranceModelUnits':1.,
              'nativeRescaleRule':'22.5_factor_only_for_abs_angle_22.5_otherwise_45_factor_including_zero',
              'newPhysicsRestrictedToPriorOrientationDomains':True,
              'priorDomainExceptions':['source_allium: native angle0 rescale actual panel supports, old carrier AABB inaccurate'],
              'originalV12Changed':False,'modelsTexturesIdsStateMappingsChanged':False,
              'authorCollisionExport':'NOT_SUPPLIED_NOT_APPLIED','cases':cases}
    (OUT / 'generation-report.json').write_bytes((json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode('utf8'))
    print(json.dumps({key:value for key,value in report.items() if key!='cases'}),flush=True)


if __name__ == '__main__':
    main()
