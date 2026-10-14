"""Bind six native QA camera views to the actual finalized saved gallery manifest.

Read-only source world and historical gallery; writes one small camera marker in build/.
The separate observer add-on operates only on run_v11_client's isolated world copy.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HISTORICAL = Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\runtime-server-v126-flatgallery-author-2\catalogue-gallery-output.json')


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for data in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(data)
    return digest.hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def camera(eye, target):
    delta = [target[i] - eye[i] for i in range(3)]
    yaw = math.degrees(math.atan2(-delta[0], delta[2]))
    pitch = -math.degrees(math.atan2(delta[1], math.hypot(delta[0], delta[2])))
    return [round(eye[0], 6), round(eye[1] - 1.62, 6), round(eye[2], 6), round(yaw, 6), round(pitch, 6)]


def bounds(row):
    return row.get('actualBounds', row.get('bounds'))


def check_owner(row):
    result = {'root': row['root'], 'registry': row.get('registry', row.get('registryId')),
              'properties': row.get('placedState', row.get('properties', {}))}
    if row.get('uuid'):
        result['uuid'] = row['uuid']
    require(result['registry'] and result['properties'], 'Saved owner state must be explicit')
    return result


def nearest(owners, point, number=3):
    def distance(row):
        box = bounds(row)
        if box:
            return sum(max(box[i] - point[i], point[i] - box[i + 3], 0) ** 2 for i in range(3))
        return sum((row['root'][i] + .5 - point[i]) ** 2 for i in range(3))
    rows = sorted((r for r in owners if r.get('uuid')), key=distance)
    require(len(rows) >= number, 'Actual saved city owners missing')
    return rows[:number]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--historical-manifest', type=Path, default=HISTORICAL)
    args = parser.parse_args()
    world = args.world.resolve()
    manifest_path = world / 'gallery-manifest.json'
    output = args.output.resolve()
    require(world.is_relative_to((ROOT / 'build').resolve()), 'QA source world must be in the controlled build directory')
    require(output.is_relative_to((ROOT / 'build').resolve()), 'Camera marker must be written only in build')
    require(manifest_path.is_file(), 'Finalized saved-world gallery manifest required')
    manifest, old = read(manifest_path), read(args.historical_manifest)
    production_sha = sha(args.production_jar)
    require(production_sha == manifest['productionJarSha256'], 'Saved gallery must bind this exact production JAR')
    rows = manifest['exhibits']
    doors = [r for r in rows if str(r.get('numericId', r.get('id'))) == '90923' and r.get('gridId', '').startswith('new/')]
    require(len(doors) == 1, 'Exactly one actual main 90923 exhibit required')
    door = doors[0]
    require(door['placedState']['variant'] == '2' and door['placedState']['open'] == 'false', 'Repaired default door pose 2 must be saved closed')
    door_box = bounds(door)
    door_center = [(door_box[i] + door_box[i + 3]) / 2 for i in range(3)]
    door_front = door.get('camera')
    if not door_front:
        door_front = camera([door_center[0], door_center[1] + 1, door_box[2] - 5], door_center)
    door_side = camera([door_box[3] + 5, door_center[1] + .9, door_center[2] - 1.5], door_center)
    neighborhood = [r for r in rows if r.get('gridId') == door['gridId'] and r.get('row', 999) <= door['row'] and bounds(r)]
    require(neighborhood, 'Actual main grid neighborhood required')
    main_box = [min(bounds(r)[i] for r in neighborhood) for i in range(3)] + [max(bounds(r)[i] for r in neighborhood) for i in range(3, 6)]
    main_center = [(main_box[i] + main_box[i + 3]) / 2 for i in range(3)]
    main_view = camera([main_center[0] + 8, main_box[4] + 19, main_box[2] - 27], [main_center[0], 66.5, main_center[2]])
    city = manifest['city']
    city_box = city['bounds']
    city_center = [(city_box[i] + city_box[i + 3]) / 2 for i in range(3)]
    city_view = camera([city_box[3] + 24, city_box[4] + 10, city_box[2] - 29], [city_center[0], city_center[1] - 1, city_center[2]])
    translation = city['translation']
    old_translation = old['city']['translation']
    delta = [translation[i] - old_translation[i] for i in range(3)]
    require(city.get('rawBlocks', {}).get('boundsInclusive') == old['city'].get('rawBlocks', {}).get('boundsInclusive'), 'Historical and final gallery views must use the same original city crop')
    owners = city['placedOwners']
    screenshot_views = [
        {'key': 'repaired-column-user-view', 'originalRoot': [55, 71, 229], 'originalCamera': [59.540, 71.658, 225.537, 52.4, 12.4]},
        {'key': 'repaired-wall-user-view', 'originalRoot': [78, 67, 232], 'originalCamera': [80.56, 66.0, 227.456, 15.5, 4.5]},
    ]
    frames = []

    def frame(key, view, checks, origin):
        require(len(view) == 5 and all(math.isfinite(float(x)) for x in view), 'Finite native camera required')
        frames.append({'key': key, 'fileName': f'{len(frames) + 1:02d}-{key}.png', 'camera': view,
                       'rootChecks': [check_owner(r) for r in checks], 'entityChecks': [], 'worldTime': 6000,
                       'coordinateEvidence': origin})

    frame('final-main-catalogue-overview', main_view, [door], {'actualMainGridId': door['gridId'], 'actualNeighborhoodBounds': main_box})
    frame('repaired-door-90923-front', door_front, [door], {'savedExhibitRoot': door['root'], 'savedPose': 2, 'actualBounds': door_box})
    frame('repaired-door-90923-side', door_side, [door], {'savedExhibitRoot': door['root'], 'savedPose': 2, 'actualBounds': door_box})
    frame('repaired-city-overview', city_view, nearest(owners, city_center), {'actualCityBounds': city_box, 'translation': translation})
    for view in screenshot_views:
        point = [view['originalRoot'][i] + delta[i] for i in range(3)]
        original_camera = view['originalCamera']
        translated_camera = [round(original_camera[i] + delta[i], 6) for i in range(3)] + original_camera[3:]
        frame(view['key'], translated_camera, nearest(owners, point), {
            'originalUserScreenshotRoot': view['originalRoot'], 'originalUserScreenshotCamera': original_camera,
            'historicalGalleryTranslation': old_translation, 'finalGalleryTranslation': translation,
            'verifiedTranslationDelta': delta, 'currentTargetCell': point})
    marker = {'schema': 'dreamwalker-final-gallery-native-visual-route-v1', 'v1271FinalGalleryVisualTour': True,
              'v127GalleryBenchmark': False, 'existingSavedWorldOnly': True, 'productionJarSha256': production_sha,
              'galleryManifestSha256': sha(manifest_path), 'gallerySourceWorld': str(world),
              'historicalGalleryManifestSha256': sha(args.historical_manifest), 'frames': frames}
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('w', encoding='utf-8', newline='\n') as stream:
        json.dump(marker, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps({'status': 'PASS_SIX_ACTUAL_SAVED_GALLERY_VIEWS_PREPARED', 'output': str(output), 'sha256': sha(output),
                      'manifestSha256': marker['galleryManifestSha256'], 'doorRoot': door['root'], 'translationDelta': delta,
                      'frames': [{'key': r['key'], 'camera': r['camera'], 'rootChecks': r['rootChecks']} for r in frames]}, ensure_ascii=True))


if __name__ == '__main__':
    main()
