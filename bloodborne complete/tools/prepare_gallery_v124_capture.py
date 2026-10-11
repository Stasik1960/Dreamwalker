"""Plan native, unmodified framebuffer captures of the fresh V12.4 gallery."""
from __future__ import annotations
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import re
import zipfile
from prepare_flat_gallery_capture import camera, architecture, diagnostic

ROOT = Path(__file__).resolve().parents[1]
STATE_KEYS = ('variant', 'profile', 'open', 'rotation', 'half', 'shape', 'trim', 'connections', 'height', 'art_bank', 'mount')


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
        view = camera(row['bounds'])
        slug = re.sub(r'[^A-Za-z0-9_-]', '_', row['key'])[:72]
        frame = {'fileName': f'gallery-{index + 1:02d}-{slug}.png', 'key': row['key'], 'section': row['section'],
                 'name': row['name'], 'camera': view, 'command': 'tp @s ' + ' '.join(map(str, view)), 'waitTicks': 80,
                 'rootChecks': [], 'entityChecks': []}
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
              'scope': 'Every current section, each native stair material with saved half/shape, canonical rung ladder height, V12 AUTO walls, original V12/RP objects and framed items. No block changes; native ScreenshotRecorder images remain unmodified.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8', newline='\n')


if __name__ == '__main__':
    main()
