"""Author the current catalogue, verify actual saved NBT, reopen with production only, and ZIP.

Uses the small pinned localhost server launcher and plain Python world_io.
Run this process through CodexControl/Guard.ps1 -Mode Operation. No Gradle build,
original Minecraft world, client installation, editor, or diagnostic harness is used.
"""
from __future__ import annotations
import argparse
from collections import Counter
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import uuid
import zipfile
from world_io import Tag, RegionFile, read_nbt, write_nbt, compound, section_blocks, block_state_key

ROOT = Path(__file__).resolve().parents[1]
STATUS = 'PASS_AUTHORED_REQUIRES_PRODUCTION_REOPEN'
WORLD_NAME = 'Dreamwalker-BB-v11-Gallery'
REMOVED_NUMBERS = {'90008', '90009', '90013', '90014', '90015', '90016', '90017', '90018', '90019'}
REMOVED_ITEMS = {'bloodborne_dw:builder_tool', 'bloodborne_dw:composite_builder'}


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf8'))


def save(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def plain(tag):
    if tag.type == 10:
        return {key: plain(value) for key, value in tag.value.items()}
    if tag.type == 9:
        return [plain(value) for value in tag.value]
    return list(tag.value) if isinstance(tag.value, bytes) else tag.value


def identity(value):
    if isinstance(value, str):
        try:
            return str(uuid.UUID(value))
        except ValueError:
            return None
    if isinstance(value, list) and len(value) == 4:
        bits = 0
        for word in value:
            bits = (bits << 32) | (word & 0xffffffff)
        return str(uuid.UUID(int=bits))
    return None


def ordinary_files(directory):
    """Refuse reparse points instead of traversing them, including junctions."""
    directory = Path(directory).resolve()
    for current, dirs, names in os.walk(directory, followlinks=False):
        for name in dirs + names:
            path = Path(current) / name
            info = path.lstat()
            require(not stat.S_ISLNK(info.st_mode) and not (getattr(info, 'st_file_attributes', 0) & 0x400),
                    'Reparse point is outside the gallery archive scope: ' + str(path))
        for name in names:
            path = Path(current) / name
            if path.name != 'session.lock':
                yield path


def prepare_entry(world, manifest):
    level = read_nbt(world / 'level.dat')
    data = compound(compound(level.root)['Data'])
    data['allowCommands'] = Tag(1, 1)
    data['GameType'] = Tag(3, 1)
    data['LevelName'] = Tag(8, 'Dreamwalker BB v11 · Gallery')
    packs = compound(data['DataPacks'])
    enabled = packs['Enabled'].value
    name = 'file/dw_gallery_entry'
    if name not in [tag.value for tag in enabled]:
        enabled.append(Tag(8, name))
    packs['Disabled'].value = [tag for tag in packs['Disabled'].value if tag.value != name]
    write_nbt(world / 'level.dat', level)
    visits = world / 'datapacks/dw_gallery_entry/data/dw_gallery/functions/visit'
    visits.mkdir(parents=True, exist_ok=True)
    counts = Counter(row['id'] for row in manifest['exhibits'] if row['id'])
    keys = set()
    index = []
    for row in manifest['exhibits']:
        key = row['id']
        prefix = 'npc' if row['kind'] == 'rp_mob' else 'item' if row['representation'] == 'item_frame' else 'block' if row['kind'] == 'architecture' else 'object'
        if not key:
            key = prefix + '_' + re.sub(r'[^a-z0-9_]', '_', row['registry'].split(':', 1)[1])
        elif counts[key] > 1:
            key = prefix + '_' + key
        require(key not in keys, 'Repeated navigation key: ' + key)
        keys.add(key)
        low_x, low_y, low_z, high_x, high_y, high_z = row['bounds']
        x, y, z = (low_x + high_x) / 2, 64, low_z - 3
        target_y, target_z = (low_y + high_y) / 2, (low_z + high_z) / 2
        pitch = math.degrees(math.atan2(y + 1.62 - target_y, target_z - z))
        camera = [round(x, 4), y, round(z, 4), 0, round(pitch, 3)]
        function = 'dw_gallery:visit/' + key
        (visits / (key + '.mcfunction')).write_text('tp @s ' + ' '.join(map(str, camera)) + '\n', encoding='utf8')
        row['visitFunction'] = function;row['camera'] = camera
        index.append([key, row['id'], row['name'], row['kind'], row['registry'], row['item'], *camera, '/function ' + function])
    with (world / 'GALLERY-INDEX.csv').open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.writer(stream)
        writer.writerow(['visit_key', 'id', 'name', 'kind', 'registry', 'item', 'x', 'y', 'z', 'yaw', 'pitch', 'command'])
        writer.writerows(index)
    readme = 'Dreamwalker BB v11 gallery navigation\n\n'
    readme += 'Use /function dw_gallery:visit/<ID> to jump to an exhibit; for example /function dw_gallery:visit/90006.\n'
    readme += 'Shared NPC/spawn-egg IDs use npc_<ID> and item_<ID>. Items without numeric IDs use item_<registry_path>.\n'
    readme += 'GALLERY-INDEX.csv contains every command and camera coordinate. gallery-manifest.json has the same index.\n'
    readme += 'All platforms continue east with exactly2 blocks between adjacent edges.\n'
    (world / 'GALLERY-NAVIGATION.txt').write_text(readme, encoding='utf8')


def verify_world(world, manifest, manifest_path):
    world = Path(world).resolve()
    require(world.is_relative_to((ROOT / 'build').resolve()) and (world / 'level.dat').is_file(),
            'Gallery verification is confined to this project build worlds')
    require(manifest.get('schema') == 'dreamwalker-v11-gallery-v1' and manifest.get('status') == STATUS,
            'Fresh v11 gallery authoring did not pass')
    rows = manifest['exhibits']
    require(len(rows) == manifest['catalogueTypes'] and len({r['uuid'] for r in rows}) == len(rows),
            'Gallery inventory count/unique instance UUIDs differ')
    offered = [r['item'] for r in rows if r['item']]
    npcs = [r['registry'] for r in rows if r['kind'] == 'rp_mob']
    require(len(set(offered)) == len(offered) and set(offered) == set(manifest['expectedItems']),
            'Gallery does not cover every offered current item exactly once')
    require(len(set(npcs)) == len(npcs) and set(npcs) == set(manifest['expectedNpcs']),
            'Gallery does not cover each real NPC exactly once')
    require(not set(offered) & REMOVED_ITEMS and not any(r['id'] in REMOVED_NUMBERS for r in rows),
            'Removed tool or retired art appears in gallery')
    previous = None
    samples = {}
    for row in rows:
        x, z, width, depth = row['platform']
        low_x, _, low_z, high_x, _, high_z = row['bounds']
        require((x, z, x + width, z + depth) ==
                (math.floor(low_x) - 2, math.floor(low_z) - 2, math.ceil(high_x) + 2, math.ceil(high_z) + 2),
                'Platform does not equal final bounds plus2: ' + row['registry'])
        require(z == 0 and (previous is None or x - previous == 2), 'Platform edge gap is not exactly2')
        previous = x + width
        for px in range(x, x + width):
            for pz in range(z, z + depth):
                samples[(px, 63, pz)] = row['floor']
        if row['kind'] == 'architecture':
            samples[tuple(row['root'])] = row['registry']
    found = {}
    states = {}
    retired_registries = {'bloodborne_dw:prototype_ladder_art_' + str(n) for n in (1, 2)} | {
        'bloodborne_dw:prototype_wall_skin_' + str(n) for n in (2, 3, 4, 5, 7)}
    for path in sorted((world / 'region').glob('*.mca')):
        for chunk in RegionFile.open(path).chunks():
            nbt = chunk.nbt().root
            doc = plain(nbt)
            for be in doc.get('block_entities', []):
                resident = be.get('resident', {})
                key = identity(resident.get('uuid'))
                if key:
                    require(key not in found, 'Repeated persisted exhibit UUID: ' + key)
                    found[key] = be
            cx, cz = doc['xPos'], doc['zPos']
            wanted = {p for p in samples if p[0] >> 4 == cx and p[2] >> 4 == cz}
            for section in compound(nbt).get('sections', Tag(9, [], 10)).value:
                decoded = section_blocks(section)
                if not decoded:
                    continue
                palette, indices = decoded
                keys = [block_state_key(tag) for tag in palette]
                require(not retired_registries & {key.split('[', 1)[0] for key in keys},
                        'Retired block art is present in saved terrain')
                sy = compound(section)['Y'].value
                for p in wanted:
                    if p[1] >> 4 == sy:
                        states[p] = keys[indices[(p[0] & 15) + 16 * (p[2] & 15) + 256 * (p[1] & 15)]]
    for pos, expected in samples.items():
        require(states.get(pos, 'minecraft:air').split('[', 1)[0] == expected,
                f'Actual saved block differs at {pos}: {states.get(pos)} versus {expected}')
    for path in sorted((world / 'entities').glob('*.mca')):
        if not path.stat().st_size:
            continue
        for chunk in RegionFile.open(path).chunks():
            for entity in plain(chunk.nbt().root).get('Entities', []):
                key = identity(entity.get('UUID'))
                if key:
                    require(key not in found, 'Repeated entity/owner UUID: ' + key)
                    found[key] = entity
    stable = []
    for row in rows:
        require(row['uuid'] in found, 'Saved exhibit UUID missing: ' + row['registry'])
        actual = found[row['uuid']]
        if row['kind'] in ('rp_object', 'rp_mob'):
            require(actual['id'] == row['registry'], 'Saved entity type differs: ' + row['registry'])
            require(not actual.get('CustomNameVisible', 0), 'Visible entity name flag is set: ' + row['registry'])
        if row['kind'] == 'rp_mob':
            require(actual.get('NoAI') and actual.get('PersistenceRequired') and actual.get('NoGravity'),
                    'NPC exhibit can move or despawn: ' + row['registry'])
        if row['representation'] == 'item_frame':
            require(actual['id'] == 'minecraft:item_frame' and actual.get('Fixed') and actual['Item']['id'] == row['item'],
                    'Saved item frame or displayed item differs: ' + row['item'])
        if row['kind'] == 'architecture':
            resident = actual['resident']
            require(resident['id'] == row['registry'] and resident['root'] == row['root'],
                    'Saved architecture root/registry differs: ' + row['registry'])
            state = {'blockState': states[tuple(row['root'])], 'blockEntity': actual}
        else:
            keys = ('id', 'UUID', 'Pos', 'Rotation', 'Item', 'Facing', 'Fixed', 'NoAI', 'NoGravity',
                    'PersistenceRequired', 'Scale', 'Open', 'Locked', 'DogsVisible', 'VerticalOffset',
                    'CustomName', 'CustomNameVisible')
            state = {k: actual[k] for k in keys if k in actual}
        stable.append({'uuid': row['uuid'], 'registry': row['registry'], 'item': row['item'], 'state': state})
    data = plain(read_nbt(world / 'level.dat').root)['Data']
    require(data.get('GameType') == 1 and data.get('allowCommands') == 1 and
            'file/dw_gallery_entry' in data['DataPacks']['Enabled'], 'Creative gallery entry metadata missing')
    entry = (world / 'datapacks/dw_gallery_entry/data/dw_gallery/functions/entry.mcfunction').read_text(encoding='utf8')
    require('composite_builder' not in entry and 'builder_tool' not in entry, 'Gallery entry still grants a removed tool')
    for row in rows:
        if 'visitFunction' in row:
            key = row['visitFunction'].split('/', 1)[1]
            actual = (world / 'datapacks/dw_gallery_entry/data/dw_gallery/functions/visit' / (key + '.mcfunction')).read_text(encoding='utf8')
            require(actual == 'tp @s ' + ' '.join(map(str, row['camera'])) + '\n', 'Saved navigation function differs: ' + key)
    return {'status': 'PASS_V11_SAVED_GALLERY', 'world': str(world), 'manifestSha256': sha(manifest_path),
            'productionJarSha256': manifest['productionJarSha256'], 'verifiedExhibits': len(rows),
            'offeredItems': len(offered), 'realNpcs': len(npcs), 'verifiedFloorBlocks': len(samples),
            'exactTwoBlockGaps': True, 'boundsPlusTwo': True, 'stableExhibits': stable,
            'scope': 'Actual saved NBT and authored physical bounds; no claim of human visual acceptance'}


def launch(arguments):
    subprocess.run([sys.executable, str(ROOT / 'tools/run_v11_server.py'), *arguments], cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--qa-jar', type=Path, required=True)
    parser.add_argument('--accepted-eula-file', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True, help='Fresh release/evidence directory')
    parser.add_argument('--run-name', default=None)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--loader', choices=('0.16.10',), default='0.16.10')
    args = parser.parse_args()
    jar = args.jar.resolve();qa = args.qa_jar.resolve();output = args.output.resolve()
    require(not output.exists(), 'Refusing to overwrite a release/evidence directory')
    require(jar.is_file() and qa.is_file(), 'Production and gallery authoring JARs must already be built')
    output.mkdir(parents=True)
    artifact = sha(jar);name = args.run_name or ('v11-gallery-' + artifact[:12])
    marker = output / 'gallery-input.json'
    save(marker, {'schema': 'dreamwalker-v11-gallery-input-v1', 'revision': 'V11',
                  'guard': 'FRESH_ISOLATED_CATALOGUE_GALLERY_ONLY', 'productionJarSha256': artifact})
    common = ['--jar', str(jar), '--loader', args.loader,
              '--accepted-eula-file', str(args.accepted_eula_file.resolve()), '--startup-timeout', '180', '--shutdown-timeout', '90']
    if args.java_home:
        common += ['--java-home', str(args.java_home.resolve())]
    author_report = output / 'gallery-author-server.json'
    launch(common + ['--extra-mod', str(qa), '--gallery-input', str(marker), '--gallery-timeout', '180',
                     '--run-name', name + '-author', '--report', str(author_report)])
    authored = read(author_report)
    require(authored.get('status') == 'PASS' and not authored.get('errors'), 'Authoring server failed or logged an error')
    manifest_path = Path(authored['gallery']['path']);manifest = read(manifest_path)
    require(manifest['productionJarSha256'] == artifact, 'Gallery is bound to another production JAR')
    author_world = Path(authored['run_directory']) / 'isolated-smoke-world'
    prepare_entry(author_world, manifest)
    indexed_manifest_path = output / 'gallery-manifest.json'
    save(indexed_manifest_path, manifest)
    before = verify_world(author_world, manifest, indexed_manifest_path)
    save(output / 'gallery-author-disk.json', before)
    reopen_report = output / 'gallery-production-reopen.json'
    launch(common + ['--world-copy', str(author_world), '--run-name', name + '-reopen',
                     '--hold-seconds', '3', '--report', str(reopen_report)])
    reopened = read(reopen_report)
    require(reopened.get('status') == 'PASS' and not reopened.get('errors')
            and reopened['derived_world_copy']['source_unchanged_after_run'], 'Production-only reopen failed')
    require(not any(m.get('extra_mod') for m in reopened['modset']), 'Authoring add-on leaked into production-only reopen')
    final_world = Path(reopened['run_directory']) / 'isolated-smoke-world'
    after = verify_world(final_world, manifest, indexed_manifest_path)
    require(before['stableExhibits'] == after['stableExhibits'], 'Saved exhibit identity/pose/settings changed on production reopen')
    save(output / 'gallery-production-disk.json', after)
    save(output / 'gallery-manifest.json', manifest)
    archive = output / (WORLD_NAME + '.zip')
    files = sorted(ordinary_files(final_world), key=lambda p: p.relative_to(final_world).as_posix())
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as package:
        for path in files:
            package.write(path, WORLD_NAME + '/' + path.relative_to(final_world).as_posix())
        package.writestr(WORLD_NAME + '/gallery-manifest.json', json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        package.writestr(WORLD_NAME + '/GALLERY.txt',
            'Dreamwalker BB v11 / Minecraft 1.20.1 Fabric\n'
            'Unzip this folder into .minecraft/saves. Install the production mod and its dependencies.\n'
            'Creative mode, coordinates and commands enabled; first entry starts at 0.5 64 -5.5.\n'
            'Exhibits continue east. All current items and NPCs are indexed in gallery-manifest.json.\n'
            'Jump to exhibits with /function dw_gallery:visit/<ID>; see GALLERY-INDEX.csv for exact commands.\n'
            'Platform footprint = integer player collision bounds +2 on every side; adjacent edges have a 2-block gap.\n'
            'Collision-free displays use visual bounds; framed items include their support block.\n'
            'No editor/tool or QA add-on is required by this saved world.\n'
            'Authoring, saved NBT and production-only restart verified. Human visual/gameplay acceptance is separate.\n')
    with zipfile.ZipFile(archive) as package:
        require(package.testzip() is None and WORLD_NAME + '/level.dat' in package.namelist(), 'World archive failed CRC/layout verification')
        for path in files:
            require(hashlib.sha256(package.read(WORLD_NAME + '/' + path.relative_to(final_world).as_posix())).hexdigest() == sha(path),
                    'Packaged world file bytes differ')
    result = {'status': 'PASS_V11_GALLERY_PACKAGE', 'productionJarSha256': artifact,
              'worldZip': str(archive), 'worldZipSha256': sha(archive), 'worldFiles': len(files),
              'offeredItems': after['offeredItems'], 'realNpcs': after['realNpcs'], 'exhibits': after['verifiedExhibits'],
              'authoringAddonRequired': False, 'productionOnlyReopen': True, 'exactTwoBlockGaps': True,
              'actualClientVisualAcceptance': 'NOT_RUN'}
    save(output / 'gallery-package.json', result)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
