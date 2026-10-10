"""Package a verified ordinary JAR and saved gallery; never infer client acceptance."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = 'd5d78282b5df3d274a49be8e5b947bedc6bf3ecc'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def document(path):
    return json.loads(path.read_text(encoding='utf8'))


def copy_file(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    if sha(source) != sha(destination):
        raise ValueError('Copy checksum differs: ' + str(source))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('jar', 'world', 'gallery-manifest', 'server-report', 'disk-report', 'test-xml', 'output'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--evidence', type=Path, action='append', default=[])
    parser.add_argument('--screenshot', type=Path, action='append', default=[])
    parser.add_argument('--verify-only', action='store_true', help='Validate inputs/platforms/ordinary JAR without creating delivery files')
    args = parser.parse_args()
    world = args.world.resolve()
    if not world.is_relative_to((ROOT / 'build').resolve()) or not (world / 'level.dat').is_file():
        raise ValueError('Only a saved isolated build world may be packaged')
    output = args.output.resolve()
    if not output.is_relative_to((ROOT.parent / 'releases').resolve()) or output.exists():
        raise ValueError('Use a new directory under this worktree releases folder')
    gallery, server, disk = map(document, (args.gallery_manifest, args.server_report, args.disk_report))
    artifact_sha = sha(args.jar)
    if server['status'] != 'PASS' or server['artifact_sha256'] != artifact_sha or server['exit_code'] != 0:
        raise ValueError('Saved production reopen must match this exact JAR and normal shutdown')
    if Path(server['run_directory']).resolve() / 'isolated-smoke-world' != world:
        raise ValueError('World must be the actual normally saved production reopen')
    if disk['status'] != 'PASS_SAVED_GALLERY_DISK' or Path(disk['world']).resolve() != world:
        raise ValueError('Saved NBT proof must refer to this exact world')
    if disk['manifestSha256'] != sha(args.gallery_manifest) or disk['verifiedExhibits'] != len(gallery['exhibits']):
        raise ValueError('Manifest or saved exhibit count differs')
    if disk['allowCommands'] != 1 or disk['gameType'] != 1:
        raise ValueError('Gallery must enable cheats and Creative')
    tests = ET.parse(args.test_xml).getroot()
    if list(tests.iter('failure')) or list(tests.iter('error')) or list(tests.iter('skipped')):
        raise ValueError('Required test XML contains failed/skipped checks')
    test_count = len(list(tests.iter('testcase')))
    if test_count < 147:
        raise ValueError('Full current native test report is required')
    canonical = [row for row in gallery['exhibits'] if not row['stand']]
    ids = [row['id'] for row in canonical]
    expected_ids = gallery['expectedCatalogueIds']
    if isinstance(expected_ids, str):
        expected_ids = expected_ids.split(',')  # Authoring manifest stores this native registry list as CSV.
    if len(expected_ids) != gallery['catalogueTypes'] or len(set(expected_ids)) != len(expected_ids):
        raise ValueError('Expected native catalogue contains duplicates or wrong count')
    if len(ids) != gallery['catalogueTypes'] or len(set(ids)) != len(ids) or set(ids) != set(expected_ids):
        raise ValueError('Actual canonical catalogue IDs differ')
    platforms = []
    for row in gallery['exhibits']:
        b, p = row['bounds'], row['platform']
        expected = [math.floor(b[0])-2, math.floor(b[2])-2,
                    math.ceil(b[3])+2-(math.floor(b[0])-2), math.ceil(b[5])+2-(math.floor(b[2])-2)]
        if p != expected:
            raise ValueError('Platform does not cover actual bounds plus2: ' + row['uuid'])
        platforms.append(p)
    gaps = []
    for i, a in enumerate(platforms):
        for b in platforms[i+1:]:
            dx = max(b[0]-(a[0]+a[2]), a[0]-(b[0]+b[2]), 0)
            dz = max(b[1]-(a[1]+a[3]), a[1]-(b[1]+b[3]), 0)
            if dx == 0 and dz == 0:
                raise ValueError('Platforms overlap/touch')
            gaps.append(max(dx, dz))
    if min(gaps) != 2:
        raise ValueError('Nearest platform edge gap must be2')
    with zipfile.ZipFile(args.jar) as jar:
        entries = jar.namelist()
        if any('/review/' in name or '/gametest/' in name for name in entries if name.endswith('.class')):
            raise ValueError('QA/GameTest classes are forbidden in ordinary delivery')
        metadata = json.loads(jar.read('fabric.mod.json'))
    if args.verify_only:
        print(json.dumps({'status':'PASS_PACKAGE_INPUTS', 'jarSha256':artifact_sha,
                          'catalogueTypes':len(canonical), 'exhibits':len(gallery['exhibits']),
                          'platformMargin':2, 'nearestPlatformEdgeGap':min(gaps), 'nativeTests':test_count}))
        return
    output.mkdir(parents=True)
    copy_file(args.jar, output / args.jar.name)
    copy_file(ROOT / 'docs/REVIEW_V10_MENU_GUIDE.md', output / 'Управление_90009.md')
    copy_file(ROOT / 'docs/REVIEW_90009_FIX_REPORT.md', output / 'Отчёт_исправлений.md')
    world_zip = output / 'Dreamwalker_BB_Gallery_90009.zip'
    world_files = []
    with zipfile.ZipFile(world_zip, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(world.rglob('*')):
            if not path.is_file() or path.name == 'session.lock':
                continue
            relative = path.relative_to(world).as_posix()
            archive.write(path, 'Dreamwalker_BB_Gallery_90009/' + relative)
            world_files.append({'path': relative, 'bytes': path.stat().st_size, 'sha256': sha(path)})
    with zipfile.ZipFile(world_zip) as archive:
        if archive.testzip() is not None:
            raise ValueError('Gallery ZIP CRC failure')
        for row in world_files:
            if hashlib.sha256(archive.read('Dreamwalker_BB_Gallery_90009/' + row['path'])).hexdigest() != row['sha256']:
                raise ValueError('Saved-world ZIP byte mismatch: ' + row['path'])
    with (output / 'Возможности_по_ID.csv').open('w', newline='', encoding='utf-8-sig') as stream:
        writer = csv.writer(stream)
        writer.writerow(['ID', 'Название', 'Registry', 'Категория', 'Высота', 'Сброс высоты', 'Шаг поворота', 'BASE ALT', 'Монтаж', 'Собаки', 'Открытие закрытие', 'Импульс', 'Связи', 'Фонарь', 'Число физических частей'])
        for row in sorted(canonical, key=lambda row: row['id']):
            c = row['capabilities']
            yes = lambda key: 'Да' if c.get(key, False) else 'Нет'
            writer.writerow([row['id'], row['name'], row['registry'], row['kind'], yes('height'), yes('resetHeight'),
                             c.get('rotationStepDegrees', ''), yes('profile'), yes('mount'), yes('dogs'),
                             yes('openClose'), yes('pulse'), yes('link'), yes('lamp'), c.get('physicalPartCount', '')])
    evidence = [args.gallery_manifest, args.server_report, args.disk_report, args.test_xml] + args.evidence
    for path in evidence:
        destination = output / 'evidence' / path.name
        if destination.exists():
            raise ValueError('Duplicate evidence filename: ' + path.name)
        copy_file(path, destination)
    for path in args.screenshot:
        if path.suffix.lower() != '.png':
            raise ValueError('Use actual Minecraft PNG screenshots')
        copy_file(path, output / 'screenshots' / path.name)
    payload = {
        'schemaVersion': 1, 'status': 'REVIEW_CANDIDATE_NOT_USER_ACCEPTED_V11',
        'version': metadata['version'], 'sourceBaseCommit': BASE,
        'sourceBranch': 'codex/bb-v10-90009-fix',
        'jar': {'file': args.jar.name, 'sha256': artifact_sha, 'bytes': args.jar.stat().st_size},
        'world': {'file': world_zip.name, 'sha256': sha(world_zip), 'files': world_files,
                  'savedProductionJarSha256': artifact_sha, 'authoringProductionJarSha256': gallery['productionJarSha256'],
                  'qaRequired': False, 'catalogueTypes': len(canonical), 'exhibits': len(gallery['exhibits']),
                  'platformMargin': 2, 'nearestPlatformEdgeGap': min(gaps), 'archiveCrcAndEveryFileSha256': 'PASS'},
        'nativeGameTests': {'count': test_count, 'failures': 0, 'scope': 'server internal logic, not real client mouse/GUI'},
        'capabilitiesScope': 'Actual gallery native entity/state capabilities, not visual verification of every ID',
        'compatibility': {'minecraft': '1.20.1', 'java': 17, 'loaderTested': '0.16.10',
                          'fabricApiTested': '0.92.9+1.20.1', 'geckoLibTested': '4.4.9',
                          'externalResourcePackRequired': False, 'shaderRequired': False},
        'evidenceFiles': [{'file': p.relative_to(output).as_posix(), 'sha256': sha(p)}
                          for p in sorted(output.rglob('*')) if p.is_file()]
    }
    (output / 'manifest.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps({'output': str(output), 'jarSha256': artifact_sha, 'worldSha256': sha(world_zip),
                      'catalogueTypes': len(canonical), 'exhibits': len(gallery['exhibits']), 'nativeTests': test_count}))


if __name__ == '__main__':
    main()
