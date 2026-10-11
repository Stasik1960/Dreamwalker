"""Plan a fresh gallery of one NEW working type per station from the final JAR.

The original V12 stations and their ordinary art are retained. Compatibility
registrations are never displayed as separate working objects. No world is
written here; the marker is consumed only by the isolated V12.4 QA author.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
import zipfile
from prepare_gallery_v122_input import ArchiveGeometry, composite_visual as old_composite_visual
from gallery_platform_plan import rotate, bounds_of_points

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = 'dreamwalker-flat-gallery-v124'


def pose_index(row):
    """Mirror CompositeSpec.poseVariant, rather than using a catalogue row index."""
    state = row.get('placedState', row['blockStateTag'])
    if 'half' in state and 'shape' in state:
        shapes = ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right')
        index = shapes.index(state['shape']) + (5 if state['half'] == 'top' else 0)
        if row['registry'] == 'bloodborne_dw:source_stairs_polished_deepslate_stairs_bottom' and state.get('trim') == 'true':
            index += 10
        return index
    if 'height' in state and row['registry'] == 'bloodborne_dw:source_hold_ladder_02':
        return 1 if state['height'] == 'two' else 0
    index = int(state.get('variant', row.get('variant', 0))) + 16 * int(state.get('art_bank', '0'))
    if row['registry'] == 'bloodborne_dw:source_stairs_polished_deepslate_stairs_bottom' and state.get('trim') == 'true':
        index += 10
    return index


def composite_visual(spec, row, models):
    selected = copy.deepcopy(row)
    selected['variant'] = pose_index(row)
    selected['blockStateTag'] = dict(row.get('placedState', row['blockStateTag']))
    return old_composite_visual(spec, selected, models)


def native_visual(row, archive, models):
    registry = row['registry'].split(':')[1]
    state = row.get('placedState', row['blockStateTag'])
    if registry.startswith('prototype_wall'):
        material = 6 if registry == 'prototype_wall' else int(registry.rsplit('_', 1)[1])
        # Exact immutable primitive selection used by PrototypeWallModels.
        diagonal = int(state.get('rotation', '0')) & 1
        quarter = int(state.get('rotation', '0')) // 2
        profile = state.get('profile', 'base')
        points = []
        if state.get('up', 'true') == 'true':
            model = f'bloodborne_dw:block/wall/{profile}/post' + ('_diagonal' if diagonal else '')
            points.extend(rotate(point, 1, -quarter * 90, [8, 8, 8]) for point in models.points(model))
        for direction, name in enumerate(('north', 'east', 'south', 'west')):
            shape = state.get(name, 'none')
            if shape == 'none':
                continue
            primitive = 'low' if shape == 'low' else f'tall_{material}'
            model = f'bloodborne_dw:block/wall/{profile}/{primitive}' + ('_diagonal' if diagonal else '')
            points.extend(rotate(point, 1, -((quarter + direction) & 3) * 90, [8, 8, 8]) for point in models.points(model))
        assert points, 'Empty wall state: ' + row['key']
        return bounds_of_points(points)
    # The original V12 ladder has its own authored reflection/physical mount.
    from prepare_gallery_v122_input import native_visual as original_native_visual
    return original_native_visual(row, archive, models)


def canonical_catalogue(original, archive):
    catalogue = copy.deepcopy(original)
    manifest = json.loads(archive.read('bloodborne_dw/imported-type-aliases.json'))
    remaining = json.loads(archive.read('bloodborne_dw/remaining-catalogue.json'))
    debug = json.loads(archive.read('bloodborne_dw/debug_catalogue.json'))
    stairs = json.loads(archive.read('bloodborne_dw/imported-stair-types.json'))
    groups = {group['primaryRegistryId']: group for group in manifest['groups']}
    retired = {member for group in groups.values() for member in group['members'] if member != group['primaryRegistryId']}
    names = {'bloodborne_dw:' + row['path']: row for row in remaining['objects']}
    numbers = {row['registryId']: row for row in debug['entries']}
    stair_paths = {'bloodborne_dw:' + row['path'] for row in stairs['stairTypes']}
    ladder_paths = {'bloodborne_dw:' + row['path'] for row in stairs['ladderTypes']}
    all_rows = original['exhibits']
    removed = [row for row in all_rows if row['section'] == 'error']
    source_rows = {}
    for row in all_rows:
        if row['section'] != 'error':
            source_rows.setdefault(row['registry'], []).append(row)
    exhibits = []
    kept_new = set()
    for previous in all_rows:
        if previous['section'] == 'error' or previous['registry'] in retired:
            continue
        row = copy.deepcopy(previous)
        registry = row['registry']
        if row['origin'] != 'V12' and row['kind'] == 'architecture':
            if registry in kept_new:
                continue
            kept_new.add(registry)
            assert registry in names, 'Working NEW object missing production catalogue: ' + registry
            group = groups.get(registry)
            family = [r for member in (group['members'] if group else [registry]) for r in source_rows.get(member, [])]
            assert family
            # Absent means every source-art member of this functional type was
            # absent. Mixed types belong to new; exact source status stays below.
            row['section'] = 'absent' if all(r['section'] == 'absent' for r in family) else 'new'
            row['key'] = registry
            row['id'] = numbers[registry]['temporaryId']
            row['name'] = names[registry]['displayName']
            row['variant'] = 0
            row['state'] = {'variant': '0', 'rotation': '0', 'open': 'false', 'profile': 'base'}
            if registry in stair_paths:
                row['state']['connections'] = 'auto'
                row['semanticStairState'] = {'half': 'bottom', 'shape': 'straight', 'encodedVariant': 0}
                if registry == 'bloodborne_dw:source_stairs_polished_deepslate_stairs_bottom':
                    row['state']['trim'] = 'false'
            if registry in ladder_paths:
                row['state']['height'] = 'one'
            if group and group['kind'] == 'artwork' and group.get('poseCount', 0) > 16:
                row['state']['art_bank'] = '0'
            row['sourceModels'] = names[registry]['variants'][0]['sourceModels']
            row['sourceParts'] = []
            row['sourceGroupIds'] = sorted({r['id'] for r in family})
            row['sourceGroupMapStates'] = [{'key': r['key'], 'mapStatus': r.get('mapStatus', ''), 'section': r['section']} for r in family]
            row['workingTypePolicy'] = 'ONE_PRIMARY_INSTALLATION_ITEM_DEFAULT_STATE; all art, mount and connections remain interactive states'
            row['availablePoseCount'] = len(names[registry]['variants'])
        exhibits.append(row)
    expected_new = {registry for registry in names if registry not in retired}
    assert kept_new == expected_new, 'Missing/unexpected NEW gallery working types: ' + repr(sorted(expected_new ^ kept_new))
    assert len({row['key'] for row in exhibits}) == len(exhibits)
    catalogue['exhibits'] = exhibits
    catalogue['sections'] = [row for row in catalogue['sections'] if row['id'] != 'error']
    catalogue['sections'].append({'id': 'items', 'title': 'Предметы V12 в рамках', 'floor': 'minecraft:blue_concrete'})
    catalogue['displayPolicy'] = 'Original V12 retained; every NEW working functional type once in its canonical default pose. Compatibility aliases omitted. Other art/connection/mount poses are states of the same item.'
    catalogue['classificationPolicy'] = 'Original V12 origin retained; NEW type absent only if all source members were absent; damaged source stations never restored'
    return catalogue, removed, retired


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--catalogue', type=Path, default=ROOT / 'docs/gallery-v12.1/catalogue.json')
    parser.add_argument('--vanilla-jar', type=Path, default=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    args = parser.parse_args()
    original = json.loads(args.catalogue.read_text(encoding='utf-8-sig'))
    assert original['status'] == 'PASS'
    digest = hashlib.sha256(args.production_jar.read_bytes()).hexdigest()
    geometries, ru_names = {}, {}
    with zipfile.ZipFile(args.production_jar) as archive:
        assert archive.testzip() is None
        catalogue, removed, retired = canonical_catalogue(original, archive)
        models = ArchiveGeometry(archive, args.vanilla_jar)
        for entry in archive.namelist():
            if entry.endswith('/lang/ru_ru.json'):
                ru_names.update(json.loads(archive.read(entry)))
        for row in catalogue['exhibits']:
            row['registryId'], row['itemId'], row['numericId'] = row['registry'], row['item'], row['id']
            row['blockStateTag'] = dict(row['state'])
            row['profile'], row['open'] = 'base', False
            if row['registry'].startswith('bloodborne_dw:prototype_wall'):
                material = 6 if row['registry'] == 'bloodborne_dw:prototype_wall' else int(row['registry'].rsplit('_', 1)[1])
                row['blockStateTag'].update(up='true', north='none', east='none', south='none', west='none', rotation='0', connections='auto')
                if material == 1:
                    # Native original-V12 diagonal-post canonical form. This
                    # is its preserved placement behavior, not a gallery hack.
                    row['blockStateTag'].update(rotation='1', connections='manual')
            if row['kind'] == 'architecture':
                path = 'bloodborne_dw/composite/' + row['registry'].split(':')[1] + '.json'
                geometry = composite_visual(json.loads(archive.read(path)), row, models) if path in archive.namelist() else native_visual(row, archive, models)
                assert all(math.isfinite(value) for value in geometry)
                geometries[row['key']] = geometry
                row['localVisualBounds'] = geometry
                row['surfaceClearance'] = 1 / 1024 if abs(geometry[4] - geometry[1]) < 1e-8 else 0
                if row['surfaceClearance']:
                    row['galleryPlanarLiftNote'] = 'Gallery-only 1/1024-block lift prevents coplanar floor z-fighting; original zero-thickness model unchanged'
                row['visualGeometryEvidence'] = 'Exact final-production rendered element corners and active canonical state, not a collision/selection envelope'
    catalogue['productionJar'], catalogue['productionJarSha256'] = str(args.production_jar.resolve()), digest
    catalogue['summary'] = {'placeableBuildingTypes': len(catalogue['exhibits']),
                            'newWorkingTypes': sum(row['origin'] != 'V12' and row['kind'] == 'architecture' for row in catalogue['exhibits']),
                            'retiredAliasTypesExcluded': len(retired), 'removedRedStations': len(removed),
                            'runtimeNonBuildingCreativeItems': 'DISCOVER_AT_AUTHORING'}
    marker = {'guard': 'FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY', 'revision': 'V12',
              'gallerySchema': SCHEMA, 'displayRevision': 'V12.4',
              'productionJarSha256': digest, 'catalogue': catalogue,
              'localVisualBounds': geometries, 'russianItemNames': ru_names,
              'removedRedExhibits': [{'key': row['key'], 'id': row['id'], 'registry': row['registry']} for row in removed],
              'layoutPolicy': 'Each actual visual footprint rounded to cells +2 total; grids with exactly 2 empty cells between platforms',
              'verticalPolicy': 'Actual rendered minimum Y equals the platform surface; only zero-thickness planes use an explicit gallery-only 1/1024-block lift',
              'functionalTypePolicy': 'One main station and one Creative placing item per NEW canonical functional type; aliases remain registered for saved-world compatibility'}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(marker, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')
    print(json.dumps({'status': 'PASS', **catalogue['summary'], 'exactArchitectureGeometry': len(geometries)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
