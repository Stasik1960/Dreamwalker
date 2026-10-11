"""Build a new gallery marker from exact closed/BASE rendered geometry.

The production archive is read, never rebuilt here. Red V12.1 stations are
removed; additional non-building creative items are discovered in the game by
the QA author, using UnifiedCreativeCatalogue.canonicalItems().
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
import zipfile
from gallery_platform_plan import ModelGeometry, rotate, bounds_of_points

ROOT = Path(__file__).resolve().parents[1]


class ArchiveGeometry(ModelGeometry):
    def __init__(self, archive, vanilla_jar):
        super().__init__(resource_roots=[], vanilla_jar=vanilla_jar)
        self.archive = archive

    def document(self, identifier):
        namespace, path = identifier.split(':', 1) if ':' in identifier else ('minecraft', identifier)
        relative = f'assets/{namespace}/models/{path}.json'
        if relative in self.archive.namelist():
            return json.loads(self.archive.read(relative))
        if self.vanilla and relative in self.vanilla.namelist():
            return json.loads(self.vanilla.read(relative))
        raise FileNotFoundError('Missing rendered model: '+identifier)


def composite_visual(spec, row, models):
    variant = spec['variants'][row['variant']]
    pose = variant['poses']['open' if row.get('open') else 'closed']
    points = []
    state = row['blockStateTag']
    for part in pose['parts']:
        model = part.get('altModel', part['model']) if row.get('profile') == 'alt' else part['model']
        pivot = part.get('pivot', [8,8,8])
        extra_pivot = part.get('extraPivot', [8,8,8])
        offset = part.get('offset', [0,0,0])
        for original in models.points(model):
            point = rotate(original, 1, part.get('extraYaw',0), extra_pivot)
            point = rotate(point, 0, -part.get('pitch',0), pivot)
            point = rotate(point, 1, -part.get('yaw',0), pivot)
            point = [point[i]+offset[i] for i in range(3)]
            mount = state.get('mount', 'vertical')
            if mount != 'vertical':
                point = rotate(point, 1, variant.get('mountAlignmentYaw',0), [8,8,8])
                point = rotate(point, 0, 90 if mount == 'floor' else -90, [8,8,8])
            point = rotate(point, 1, -int(state.get('rotation','0'))*45, [8,8,8])
            points.append(point)
    if not points:
        raise ValueError('No rendered geometry: '+row['key'])
    return bounds_of_points(points)


def native_visual(row, archive, models):
    registry = row['registry'].split(':')[1]
    state = row['blockStateTag']
    points = []
    if registry.startswith('prototype_wall'):
        material = 6 if registry == 'prototype_wall' else int(registry.rsplit('_',1)[1])
        diagonal = material == 1
        paths = ['post'] if diagonal else ['post',f'tall_{material}']
        for path in paths:
            model = f'bloodborne_dw:block/wall/base/{path}' + ('_diagonal' if diagonal else '')
            points.extend(models.points(model))
    elif registry == 'prototype_ladder':
        document = json.loads(archive.read('assets/bloodborne_dw/blockstates/prototype_ladder.json'))
        wanted = {'facing':'north','variant':'0','profile':'base','waterlogged':'false',
                  'diagonal':'false','source_clone':'false','freestanding':'true'}
        selected = [value for key,value in document['variants'].items()
                    if dict(pair.split('=',1) for pair in key.split(',')) == wanted]
        assert len(selected) == 1
        app = selected[0]
        for original in models.points(app['model']):
            point = rotate(original, 0, -app.get('x',0), [8,8,8])
            point = rotate(point, 1, -app.get('y',0), [8,8,8])
            # SourceLadderBakedModel, physical NORTH: X reflection and Z+16.
            points.append([16-point[0],point[1],32-point[2]])
        state.update(wanted)
    else:
        raise ValueError('Unknown retained native geometry: '+registry)
    return bounds_of_points(points)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--catalogue', type=Path, default=ROOT/'docs/gallery-v12.1/catalogue.json')
    parser.add_argument('--vanilla-jar', type=Path, default=Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
    args = parser.parse_args()
    original = json.loads(args.catalogue.read_text(encoding='utf-8-sig'))
    assert original['status'] == 'PASS'
    catalogue = copy.deepcopy(original)
    removed = [row for row in catalogue['exhibits'] if row['section'] == 'error']
    catalogue['exhibits'] = [row for row in catalogue['exhibits'] if row['section'] != 'error']
    digest = hashlib.sha256(args.production_jar.read_bytes()).hexdigest()
    catalogue['productionJar'] = str(args.production_jar.resolve())
    catalogue['productionJarSha256'] = digest
    catalogue['sections'] = [row for row in catalogue['sections'] if row['id'] != 'error']
    catalogue['sections'].append({'id':'items','title':'Предметы V12 в рамках','floor':'minecraft:blue_concrete'})
    geometries = {}
    ru_names = {}
    with zipfile.ZipFile(args.production_jar) as archive:
        assert archive.testzip() is None
        models = ArchiveGeometry(archive, args.vanilla_jar)
        for entry in archive.namelist():
            if entry.endswith('/lang/ru_ru.json'):
                ru_names.update(json.loads(archive.read(entry)))
        for row in catalogue['exhibits']:
            row['registryId'],row['itemId'],row['numericId'] = row['registry'],row['item'],row['id']
            row['blockStateTag'] = dict(row['state'])
            row['profile'],row['open'] = 'base',False
            if row['registry'] in ('bloodborne_dw:prototype_wall','bloodborne_dw:prototype_wall_skin_0'):
                row['blockStateTag'].update(connections='manual',up='true',north='tall',east='none',south='none',west='none',rotation='0')
            if row['registry'] == 'bloodborne_dw:prototype_wall_skin_1':
                row['blockStateTag'].update(connections='manual',up='true',north='none',east='none',south='none',west='none',rotation='1')
            if row['kind'] == 'architecture':
                path = 'bloodborne_dw/composite/'+row['registry'].split(':')[1]+'.json'
                if path in archive.namelist():
                    spec = json.loads(archive.read(path))
                    geometry = composite_visual(spec,row,models)
                else:
                    geometry = native_visual(row,archive,models)
                assert all(math.isfinite(value) for value in geometry)
                geometries[row['key']] = geometry
                row['localVisualBounds'] = geometry
                row['surfaceClearance'] = 1/1024 if abs(geometry[4]-geometry[1]) < 1e-8 else 0
                if row['surfaceClearance']:
                    row['galleryPlanarLiftNote'] = 'Gallery-only 1/1024-block lift prevents coplanar floor z-fighting; original zero-thickness model unchanged'
                row['visualGeometryEvidence'] = 'Exact rendered element corners, active BASE closed state; no collision/selection/root/ALT/open envelope'
    catalogue['summary'] = {'placeableBuildingVariants':len(catalogue['exhibits']),
                            'removedRedStations':len(removed),
                            'runtimeNonBuildingCreativeItems':'DISCOVER_AT_AUTHORING'}
    marker = {'guard':'FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY','revision':'V12',
              'gallerySchema':'dreamwalker-flat-gallery-v122','displayRevision':'V12.2',
              'productionJarSha256':digest,'catalogue':catalogue,
              'localVisualBounds':geometries,'russianItemNames':ru_names,
              'removedRedExhibits':[{'key':row['key'],'id':row['id'],'registry':row['registry']} for row in removed],
              'layoutPolicy':'Each visual footprint rounded to cells +2 total (one-cell margin each side); separate uniform-size grids, exactly 2 empty cells between platforms',
              'verticalPolicy':'Render geometry minimum Y equals platform surface exactly; 43 zero-Y-thickness planes have gallery-only 1/1024-block anti-z-fighting clearance. Existing saved VerticalOffset or entity floating-point Pos.'}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(marker,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':'PASS','placeableExhibits':len(catalogue['exhibits']),
                      'exactArchitectureGeometry':len(geometries),'removedRedStations':len(removed)},ensure_ascii=False))

if __name__ == '__main__':
    main()
