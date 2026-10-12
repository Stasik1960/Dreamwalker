"""Plan native, unmodified framebuffer captures of the fresh V12.4 gallery."""
from __future__ import annotations
import argparse
from collections import defaultdict
import hashlib
import json
import itertools
import math
from pathlib import Path
import re
import zipfile
from prepare_flat_gallery_capture import camera, architecture, diagnostic

ROOT = Path(__file__).resolve().parents[1]
STATE_KEYS = ('variant', 'profile', 'open', 'rotation', 'half', 'shape', 'trim', 'connections', 'height', 'art_bank', 'mount')


def segment_intersects_box(eye, target, bounds):
    """Conservative obstruction query; a saved visual AABB is never edited."""
    low, high = 0.00001, .99999
    for axis in range(3):
        direction = target[axis] - eye[axis]
        if abs(direction) < 1e-10:
            if eye[axis] < bounds[axis] or eye[axis] > bounds[axis + 3]:
                return False
            continue
        a = (bounds[axis] - eye[axis]) / direction
        b = (bounds[axis + 3] - eye[axis]) / direction
        low, high = max(low, min(a, b)), min(high, max(a, b))
        if high < low:
            return False
    return True


def unobstructed_camera(row, exhibits):
    """Keep full-size framing, choose a clear deterministic gallery azimuth.

    The earlier north-only camera could sit inside a different huge V12 tree.
    Check the eye and rays across the target envelope against every other
    saved station. This changes only the QA marker, never objects or worlds.
    """
    bounds = row['bounds']
    standard = camera(bounds)
    eye0 = [standard[0], standard[1] + 1.62, standard[2]]
    center = [(bounds[i] + bounds[i + 3]) / 2 for i in range(3)]
    relative = [eye0[i] - center[i] for i in range(3)]
    targets = [[bounds[0] + (bounds[3] - bounds[0]) * x,
                bounds[1] + (bounds[4] - bounds[1]) * y,
                bounds[2] + (bounds[5] - bounds[2]) * z]
               for x, y, z in itertools.product((.02, .5, .98), (.02, .98), (.02, .5, .98))]
    targets.append(center)
    obstacles = [other for other in exhibits if other['key'] != row['key']]
    origin = row.get('renderOrigin', row['root'])
    candidates = []
    for offset in (0, 30, -30, 60, -60, 90, -90, 120, -120, 150, -150, 180,
                   15, -15, 45, -45, 75, -75, 105, -105, 135, -135, 165, -165):
        angle = math.radians(offset)
        eye = [center[0] + relative[0] * math.cos(angle) - relative[2] * math.sin(angle),
               eye0[1],
               center[2] + relative[0] * math.sin(angle) + relative[2] * math.cos(angle)]
        # The retry client uses renderDistance8; every RP entity root must be
        # comfortably within its preserved128-block registration track range.
        distance = math.hypot(eye[0] - origin[0], eye[2] - origin[2])
        if row['kind'] == 'rp_object' and distance > 112:
            continue
        blockers = []
        cone = [min(eye[i], bounds[i]) for i in range(3)] + [max(eye[i], bounds[i + 3]) for i in range(3)]
        for other in obstacles:
            box = other['bounds']
            if any(box[i + 3] < cone[i] or box[i] > cone[i + 3] for i in range(3)):
                continue
            if all(box[i] <= eye[i] <= box[i + 3] for i in range(3)) or any(segment_intersects_box(eye, target, box) for target in targets):
                blockers.append(other['key'])
        delta = [center[i] - eye[i] for i in range(3)]
        yaw = math.degrees(math.atan2(-delta[0], delta[2]))
        pitch = -math.degrees(math.atan2(delta[1], math.hypot(delta[0], delta[2])))
        view = [round(eye[0], 4), round(eye[1] - 1.62, 4), round(eye[2], 4), round(yaw, 4), round(pitch, 4)]
        candidates.append({'view': view, 'offset': offset, 'blockers': blockers, 'distance': distance})
        if not blockers:
            return view, {'policy': 'Saved visual AABBs: eye and19 target-envelope rays clear',
                          'azimuthDeltaDegrees': offset, 'blockingStations': [],
                          'entityOriginHorizontalDistance': round(distance, 4),
                          'requiredCaptureRenderDistanceChunks': 8}
    raise AssertionError('No unobstructed full-target QA camera for ' + row['key'] + ': ' + repr(candidates))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--disk-report', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--samples', type=int, default=48)
    args = parser.parse_args()
    output = args.output.resolve()
    assert output.is_relative_to((ROOT / 'build').resolve()) and not output.exists(), 'Fresh project capture marker required'
    assert 8 <= args.samples <= 100
    manifest = json.loads(args.manifest.read_text(encoding='utf-8-sig'))
    disk = json.loads(args.disk_report.read_text(encoding='utf-8-sig'))
    digest = hashlib.sha256(args.production_jar.read_bytes()).hexdigest()
    assert manifest['schema'] == 'dreamwalker-flat-gallery-v124'
    assert disk['schema'] == 'dreamwalker-flat-gallery-v124-disk-verification' and disk['status'] == 'PASS'
    assert manifest['productionJarSha256'] == disk['productionJarSha256'] == digest
    count = disk['functionalCatalogue']['newWorkingTypes']
    rows = manifest['exhibits']
    assert count == sum(row.get('origin') != 'V12' and row['kind'] == 'architecture' for row in rows)
    assert not any(diagnostic(row) or row['section'] == 'error' for row in rows)
    with zipfile.ZipFile(args.production_jar) as archive:
        native=json.loads(archive.read('bloodborne_dw/imported-stair-types.json'))
    stair_types={'bloodborne_dw:'+row['path'] for row in native['stairTypes']}
    actual_states = {row['key']: row.get('state', {}).get('properties', {}) for row in disk['stableExhibits']}
    sections = defaultdict(list)
    for row in rows:
        sections[row['section']].append(row)
    assert {'v12', 'new', 'absent', 'items'} <= set(sections), 'Required gallery sections missing'
    chosen, used = [], set()

    def add(row):
        if row['key'] not in used:
            chosen.append(row)
            used.add(row['key'])

    for section_rows in sections.values():
        add(section_rows[0])
        add(section_rows[-1])
        add(max(section_rows, key=lambda row: max(row['bounds'][i + 3] - row['bounds'][i] for i in range(3))))
    # Every canonical native stair material and the rung ladder get an actual
    # saved root/half/shape/height check; the independent GameTests cover their
    # other neighbor and clicked-half states. This capture never edits blocks.
    for row in rows:
        state = actual_states.get(row['key'], {})
        if row['registry'] in stair_types or row['registry'] == 'bloodborne_dw:source_hold_ladder_02':
            add(row)
    for registry in ('bloodborne_dw:prototype_wall', 'bloodborne_dw:prototype_wall_skin_0',
                     'bloodborne_dw:prototype_wall_skin_1', 'bloodborne_dw:prototype_ladder',
                     'bloodborne_rp:stairs', 'bloodborne_rp:ladder',
                     'bloodborne_rp:dog_cage', 'bloodborne_rp:dog_cage2', 'bloodborne_rp:dog_cage3',
                     'bloodborne_rp:chandelier_small', 'bloodborne_rp:chandelier_large',
                     'bloodborne_dw:bb_tool', 'bloodborne_rp:boom_hammer', 'bloodborne_rp:cleric_beast_spawn_egg'):
        matches = [row for row in rows if row['registry'] == registry]
        if matches:
            add(matches[0])
    # Requested sample count is a lower target; mandatory state/material/section
    # coverage is never dropped to satisfy a small arbitrary sample budget.
    for row in sorted(rows, key=lambda row: max(row['bounds'][i + 3] - row['bounds'][i] for i in range(3)), reverse=True):
        if len(chosen) >= args.samples:
            break
        add(row)
    assert len(chosen) <= 100
    frames = []
    for index, row in enumerate(chosen):
        view, camera_evidence = unobstructed_camera(row, rows)
        slug = re.sub(r'[^A-Za-z0-9_-]', '_', row['key'])[:72]
        frame = {'fileName': f'gallery-{index + 1:02d}-{slug}.png', 'key': row['key'], 'section': row['section'],
                 'name': row['name'], 'camera': view, 'command': 'tp @s ' + ' '.join(map(str, view)), 'waitTicks': 80,
                 'rootChecks': [], 'entityChecks': []}
        frame['cameraPlanningEvidence'] = camera_evidence
        if architecture(row):
            check = {'root': row['root'], 'registry': row['registry'], 'uuid': row['uuid']}
            state = actual_states[row['key']]
            assert state, 'No saved root properties: ' + row['key']
            for key in STATE_KEYS:
                if key in state:
                    check[key] = state[key]
            if row['registry'] in stair_types:
                v=int(state['variant']);shapes=('straight','inner_left','inner_right','outer_left','outer_right')
                frame['semanticStairState']={'half':'top' if v>=5 else 'bottom','shape':shapes[v%5],
                                             'encodedVariant':v,'evidence':'live root VARIANT checked against saved data; canonical ten-state stair schema'}
            frame['rootChecks'].append(check)
        elif row.get('uuid'):
            frame['entityChecks'].append({'registry': 'minecraft:item_frame' if row['representation'] == 'item_frame' else row['registry'], 'uuid': row['uuid']})
        frames.append(frame)
    result = {'schema': 'dreamwalker-flat-gallery-v124-capture-input', 'gallerySchema': manifest['schema'],
              'productionJarSha256': digest, 'manifestSha256': hashlib.sha256(args.manifest.read_bytes()).hexdigest(),
              'newWorkingTypes': count, 'nativeStairMaterials': len(stair_types), 'quitAfterCapture': True, 'frames': frames,
              'captureSetup': {'renderDistanceChunks': 8, 'worldMutation': False,
                               'policy': 'Alternate azimuths avoid other saved visual envelopes; all RP entity roots remain within112 blocks of camera'},
              'scope': 'Every current section, each native stair material with saved half/shape, canonical rung ladder height, V12 AUTO walls, original V12/RP objects and framed items. No block changes; native ScreenshotRecorder images remain unmodified.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')


if __name__ == '__main__':
    main()
