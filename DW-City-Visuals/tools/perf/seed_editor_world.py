"""Create an offline City Editor stress world by adding only editor cell NBT.

The source is never opened for writing. A new world copy is required under
.local/editor-validation/stress-fixtures; no game process is started.
"""
import argparse
import copy
import hashlib
import json
import math
import shutil
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

WORKSPACE = Path('D:/DreamWalker')
DEFAULT_SOURCE = WORKSPACE / '.local/dw-fix-20261028/checkpoints/test-launch-microstutter-v22-20261029/world/bloodborne'
FIXTURES = WORKSPACE / '.local/editor-validation/stress-fixtures'
PERF = Path(__file__).resolve().parent
sys.path.insert(0, str(PERF.parent / 'map_io'))
import world_io as w

def guard():
    import subprocess
    shell = shutil.which('pwsh') or shutil.which('powershell')
    if not shell:
        raise RuntimeError('PowerShell is required for the DreamWalker workspace guard')
    subprocess.run([shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                    r'C:\Users\vakir\.codex\dreamwalker\Assert-Workspace.ps1'], cwd=WORKSPACE, check=True)

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()

def block_pos(x, y, z):
    # Minecraft BlockPos.asLong: signed 26-bit X/Z and signed 12-bit Y.
    value = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
    return value - (1 << 64) if value >= (1 << 63) else value

def candidates(source, route):
    target_chunks = set()
    cameras = route['cameras']
    for cam in cameras:
        cx, cz = math.floor(cam['x']) >> 4, math.floor(cam['z']) >> 4
        for dz in range(-3, 4):
            for dx in range(-3, 4):
                target_chunks.add((cx + dx, cz + dz))
    found = []
    catalogs = set()
    for path in sorted(source.glob('**/region/r.*.*.mca')):
        if '/DIM' in path.as_posix() or '/dimensions/' in path.as_posix():
            continue
        rx, rz = w._region_origin(path)
        present = [(cx, cz) for cx, cz in target_chunks if rx <= cx < rx + 32 and rz <= cz < rz + 32]
        if not present:
            continue
        region = w.RegionFile.open(path)
        for cx, cz in present:
            stored = region.get_chunk(cx-rx, cz-rz)
            if stored is None:
                continue
            nbt = stored.nbt()
            root = w.compound(nbt.root)
            native = root.get('DreamwalkerVisuals')
            if native is not None:
                native_data = w.compound(native)
                value = native_data.get('Catalog')
                if value is not None and value.type == w.TAG_STRING:
                    catalogs.add(value.value)
            sections = w.child(nbt.root, 'sections', w.TAG_LIST)
            if sections is None:
                continue
            for section in sections.value:
                section_data = w.compound(section)
                ytag = section_data.get('Y')
                if not ytag or ytag.type != w.TAG_BYTE:
                    continue
                block_data = w.section_blocks(section)
                if block_data is None:
                    continue
                palette, indexes = block_data
                names = [w.block_state_key(entry) for entry in palette]
                for idx, palette_index in enumerate(indexes):
                    name = names[palette_index].split('[', 1)[0]
                    # CityEditorServer.supported(): registered bloodborne_dw block and non-air.
                    # The palette state is already resolved from the live world registry schema.
                    if not name.startswith('bloodborne_dw:'):
                        continue
                    x = cx * 16 + (idx & 15)
                    z = cz * 16 + ((idx >> 4) & 15)
                    y = (ytag.value << 4) + (idx >> 8)
                    # Keep records close to at least one established camera view.
                    if any(abs(x-c['x']) <= 52 and abs(z-c['z']) <= 52 and abs(y-c['y']) <= 48 for c in cameras):
                        found.append((cx, cz, block_pos(x, y, z)))
    return sorted(set(found), key=lambda p: (p[0], p[1], p[2])), catalogs

def production_catalogs(jar):
    with zipfile.ZipFile(jar) as archive:
        value = json.loads(archive.read('assets/bloodborne_dw/native_grid/visual_physics.json'))
    current = value['catalogSha256']
    return current, {current, *value.get('compatibleCatalogs', [])}

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--count', type=int, choices=(10000, 50000), required=True)
    ap.add_argument('--source', type=Path, default=DEFAULT_SOURCE)
    ap.add_argument('--destination', type=Path, required=True,
                    help='New fixture directory beneath D:/DreamWalker/.local/editor-validation/stress-fixtures')
    ap.add_argument('--route', type=Path, default=PERF / 'route.json')
    ap.add_argument('--production-jar', type=Path,
                    default=WORKSPACE / '.local/editor-validation/candidate-2.jar',
                    help='JAR whose visual_physics.json defines accepted native catalogs')
    args = ap.parse_args()
    guard()
    source, destination = args.source.resolve(), args.destination.resolve()
    if not source.is_dir() or not source.is_relative_to(WORKSPACE):
        raise ValueError('Source world must be an existing world under D:/DreamWalker')
    if not destination.is_relative_to(FIXTURES.resolve()) or destination == FIXTURES.resolve():
        raise ValueError('Destination must be a child of the stress-fixtures directory')
    if destination.exists():
        raise FileExistsError('Fixture destinations are immutable; choose a new path')
    route = json.loads(args.route.read_text(encoding='utf-8-sig'))
    selected, source_catalogs = candidates(source, route)
    if not source_catalogs:
        raise RuntimeError('Could not discover the valid native visual catalog from existing route chunks; source untouched')
    catalog, accepted_catalogs = production_catalogs(args.production_jar.resolve())
    unsupported = sorted(source_catalogs - accepted_catalogs)
    if unsupported:
        raise RuntimeError(f'Production JAR rejects existing route catalogs: {unsupported}; source untouched')
    if len(selected) < args.count:
        result = {'schema': 1, 'sourceWorld': str(source), 'eligibleBloodborneDwNonAirCells': len(selected),
                  'requestedCells': args.count, 'sufficient': False, 'sourceCatalogs': sorted(source_catalogs),
                  'productionCatalog': catalog, 'sourceUntouched': True}
        preflight = FIXTURES / f'preflight-{args.count}.json'
        FIXTURES.mkdir(parents=True, exist_ok=True)
        preflight.write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
        print(json.dumps(result))
        return
    chosen = selected[:args.count]
    by_chunk = defaultdict(list)
    for i, (cx, cz, pos) in enumerate(chosen):
        by_chunk[(cx, cz)].append((pos, i % 5))

    # Complete all target-chunk checks on the source before copying any files.
    source_regions = {}
    for cx, cz in sorted(by_chunk):
        region_path = source / 'region' / f'r.{cx // 32}.{cz // 32}.mca'
        region = source_regions.setdefault(region_path, w.RegionFile.open(region_path))
        stored = region.get_chunk(cx % 32, cz % 32)
        if stored is None:
            raise RuntimeError(f'Source route chunk missing: {cx},{cz}; no fixture copied')
        root = w.compound(stored.nbt().root)
        native = root.get('DreamwalkerVisuals')
        if native is not None:
            if native.type != w.TAG_COMPOUND:
                raise RuntimeError(f'Native visual data type invalid at {cx},{cz}; no fixture copied')
            data = w.compound(native)
            native_catalog = data.get('Catalog')
            if data.get('Schema', w.Tag(w.TAG_INT, -1)).value != 1 or native_catalog is None or native_catalog.value not in accepted_catalogs:
                raise RuntimeError(f'Native visual schema/catalog invalid at {cx},{cz}; no fixture copied')
            if 'CityEditorVersion' in data or 'CityEditorCells' in data:
                raise RuntimeError(f'Preexisting City Editor data in target {cx},{cz}; no fixture copied')

    FIXTURES.mkdir(parents=True, exist_ok=True)
    source_level_before = sha(source / 'level.dat')
    source_regions_before = {str(path): sha(path) for path in sorted({source / 'region' / f'r.{cx // 32}.{cz // 32}.mca' for cx, cz in by_chunk})}
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns('session.lock'))
    edits = []
    for (cx, cz), cells in sorted(by_chunk.items()):
        region_path = destination / 'region' / f'r.{cx // 32}.{cz // 32}.mca'
        region = w.RegionFile.open(region_path)
        stored = region.get_chunk(cx % 32, cz % 32)
        if stored is None:
            raise RuntimeError(f'Copied world lost chunk {cx},{cz}')
        nbt = stored.nbt()
        root = w.compound(nbt.root)
        original = copy.deepcopy(nbt.root)
        visuals_tag = root.get('DreamwalkerVisuals')
        if visuals_tag is None:
            visuals = w.Tag(w.TAG_COMPOUND, {
                'Schema': w.Tag(w.TAG_INT, 1),
                'Catalog': w.Tag(w.TAG_STRING, catalog),
                'Sections': w.Tag(w.TAG_LIST, [], w.TAG_COMPOUND),
            })
            root['DreamwalkerVisuals'] = visuals
        else:
            visuals = visuals_tag
            if visuals.type != w.TAG_COMPOUND:
                raise ValueError('Unexpected DreamwalkerVisuals type')
        data = w.compound(visuals)
        if data.get('Schema', w.Tag(w.TAG_INT, -1)).value != 1 or data.get('Catalog', w.Tag(w.TAG_STRING, '')).value not in accepted_catalogs:
            raise ValueError(f'Unexpected native visual schema/catalog at {cx},{cz}')
        if 'CityEditorVersion' in data or 'CityEditorCells' in data:
            raise ValueError(f'Fixture source already contains City Editor cells in {cx},{cz}')
        packed = sorted(cells)
        data['CityEditorVersion'] = w.Tag(w.TAG_INT, 2)
        data['CityEditorCells'] = w.Tag(w.TAG_COMPOUND, {
            'Positions': w.Tag(w.TAG_LONG_ARRAY, [p for p, _ in packed]),
            'Styles': w.Tag(w.TAG_BYTE_ARRAY, bytes(style for _, style in packed)),
        })
        # Semantic equality after removing precisely the newly-added City Editor fields.
        check = copy.deepcopy(root)
        check_visuals = check['DreamwalkerVisuals'].value
        check_visuals.pop('CityEditorVersion', None)
        check_visuals.pop('CityEditorCells', None)
        if visuals_tag is None and not check_visuals:
            check.pop('DreamwalkerVisuals')
        if w.Tag(w.TAG_COMPOUND, check) != original:
            raise AssertionError(f'Non-editor chunk NBT changed at {cx},{cz}')
        region.set_chunk(cx % 32, cz % 32, nbt, compression=stored.compression, timestamp=stored.timestamp)
        region.save(region_path)
        edits.append({'chunk': [cx, cz], 'cells': len(cells), 'region': region_path.name})

    source_level_after = sha(source / 'level.dat')
    source_regions_after = {path: sha(Path(path)) for path in source_regions_before}
    if source_level_after != source_level_before or source_regions_after != source_regions_before:
        raise AssertionError('Source world hashes changed during fixture preparation')
    report = {'schema': 1, 'sourceWorld': str(source), 'sourceLevelSha256BeforeAndAfter': source_level_before,
              'sourceRegionSha256BeforeAndAfter': source_regions_before,
              'sourceLevelSha256After': source_level_after, 'sourceRegionSha256After': source_regions_after,
              'sourceRegionHashesUnchanged': source_regions_before == source_regions_after,
              'fixtureWorld': str(destination), 'requestedCells': args.count, 'changedCells': len(chosen),
              'changedChunks': len(edits), 'chunks': edits, 'styles': 'deterministic manual styles 0..4',
              'nativeCatalog': catalog, 'sourceCatalogs': sorted(source_catalogs),
              'preservation': 'Each edited chunk NBT compared equal after removing only CityEditorVersion and CityEditorCells; every untouched chunk remains at its original bytes.'}
    (destination / 'dw-editor-stress-fixture.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'fixture': str(destination), 'cells': len(chosen), 'chunks': len(edits)}))

if __name__ == '__main__':
    main()
