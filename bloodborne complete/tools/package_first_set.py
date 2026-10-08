"""Deterministic full-source checkpoint and first-set review kit, gated by exact evidence.

This tool never claims the full task is ready or that the user review gate passed.
All outputs are scoped to this project's build/delivery. Inputs remain read-only.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

from world_io import RegionFile, block_state_key, compound, section_blocks

ROOT = Path(__file__).resolve().parents[1]
STAMP = (1980, 1, 1, 0, 0, 0)
EXCLUDED = {'build', '.gradle', '.git', '__pycache__', 'node_modules'}
SOURCE_DIRS = ('src', 'tools', 'docs', 'reports', 'gradle')
REQUIRED_PROJECT = ('build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat',
                    'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
                    'TASK.md', 'PROGRESS.md', 'INPUTS.json', 'RELEASE-STATUS.md')

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def digest(path: Path) -> str:
    with path.open('rb') as stream:
        result = hashlib.sha256()
        for part in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(part)
    return result.hexdigest()

def require(condition, message):
    if not condition:
        raise ValueError(message)

def resolve(path, project: Path) -> Path:
    path = Path(path)
    return (path if path.is_absolute() else project / path).resolve()

def read(path: Path):
    return json.loads(path.read_text(encoding='utf8'))

def json_bytes(value) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + '\n').encode('utf8')

def safe_name(name: str):
    path = PurePosixPath(name)
    require(not path.is_absolute() and '..' not in path.parts and '\\' not in name and ':' not in name,
            'Unsafe archive entry: ' + name)

def verified_archive(path: Path, prefix: str, records: list[dict]):
    expected = {prefix + row['path']: row for row in records}
    require(len(expected) == len(records), 'Duplicate archive byte manifest entries')
    with zipfile.ZipFile(path) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate ZIP entries: ' + str(path))
        actual = {name for name in archive.namelist() if not name.endswith('/')}
        require(actual == set(expected), 'Archive entries disagree with byte manifest: ' + str(path))
        for name in sorted(actual):
            safe_name(name)
            data = archive.read(name)
            row = expected[name]
            require(len(data) == row['bytes'] and sha(data) == row['sha256'], 'Packed bytes changed: ' + name)

def terrain_roots(entries):
    roots = {}
    for relative, data in entries:
        if not re.fullmatch(r'region/[^/]+\.mca', relative) or not data:
            continue
        for chunk in RegionFile(data).chunks():
            fields = compound(chunk.nbt().root)
            cx, cz = fields['xPos'].value, fields['zPos'].value
            for section in fields['sections'].value:
                values = section_blocks(section)
                if not values:
                    continue
                palette, indices = values
                keys = [block_state_key(state) for state in palette]
                candidates = {i for i, key in enumerate(keys) if key.startswith('bloodborne_dw:prototype_')}
                if not candidates:
                    continue
                sy = compound(section)['Y'].value
                for index, pi in enumerate(indices):
                    if pi in candidates:
                        roots[(cx * 16 + index % 16, sy * 16 + index // 256, cz * 16 + index // 16 % 16)] = keys[pi]
    return roots

def directory_roots(world: Path):
    return terrain_roots((p.relative_to(world).as_posix(), p.read_bytes())
                         for p in sorted((world / 'region').glob('*.mca')))

def zip_roots(path: Path, prefix: str):
    with zipfile.ZipFile(path) as archive:
        return terrain_roots((name[len(prefix):], archive.read(name)) for name in archive.namelist()
                             if name.startswith(prefix + 'region/') and name.endswith('.mca'))

def wrapper_output(wrapper, field: str, project: Path):
    output = wrapper[field]
    path = resolve(output['path'], project)
    require(digest(path) == output['sha256'], 'Wrapped output bytes changed: ' + str(path))
    actual = read(path)
    require(actual == output['result'], 'Wrapped output disagrees with actual JSON: ' + str(path))
    return path, actual

def evidence_gates(args):
    project = args.source_dir
    artifact = args.jar
    artifact_sha = digest(artifact)
    gates = []
    def gate(path, name, validator):
        path = resolve(path, project)
        value = read(path)
        validator(value)
        gates.append({'gate': name, 'path': str(path), 'sha256': digest(path), 'status': 'PASS'})
        return value
    def exact_wrapper(report):
        require(report.get('artifact_sha256') == artifact_sha, 'Evidence refers to another production JAR')
        require(report.get('status') == 'PASS' and report.get('exit_code') == 0, 'Server startup/save/normal-exit gate failed')
        require(report.get('original_world_loaded') is False and report.get('rp_initialization_count') == 1,
                'Server original-world or RP initialization gate failed')
    full = gate(args.full_server_report, 'ordinary_full_server_start_save_exit', exact_wrapper)
    require(full.get('profile') == 'full_server', 'Full server gate must use the selected full_server profile')
    full_log = resolve(full['evidence'], project)
    require(digest(full_log) == full['console_sha256'], 'Full server log bytes changed')

    def validate_scene(value):
        require(value.get('production_jar_sha256') == artifact_sha, 'New scene manifest JAR mismatch')
        require(value.get('status') == 'PACKAGED_PENDING_USER_REVIEW' and value.get('root_states_persisted') == 68,
                'Exact 68-root packaged new scene is required')
        require(value.get('normal_author_and_production_reopen_stop') == 'PASS'
                and value.get('zip_byte_verification') == 'PASS', 'New scene save/archive gate failed')
    scene = gate(args.scene_report, 'new_scene_current_jar_68_roots', validate_scene)
    scene_zip = resolve(scene['output'], project)
    require(digest(scene_zip) == scene['sha256'], 'New scene archive SHA mismatch')
    verified_archive(scene_zip, 'First-set-new-placement-scene/', scene['files'])
    for phase in ('server_report', 'author_report'):
        gate(scene[phase], 'new_scene_' + phase, exact_wrapper)

    def validate_source(value):
        require(value.get('artifact_sha256') == artifact_sha, 'Source scene manifest JAR mismatch')
        require(value.get('status', '').startswith('PASS_ARCHIVE_EXACT_PRODUCTION_REOPEN')
                and value.get('all_archived_file_bytes_verified') is True and value.get('reopened_world_unchanged') is True,
                'Source fixture must have independent exact production-reopen archive proof')
        require(value['counts']['objects'] == 41 and value['counts']['source_member_cells'] == 110,
                'Bounded source membership must remain41 objects/110 source cells')
    source = gate(args.source_scene_report, 'bounded_source_archive_current_jar', validate_source)
    source_zip = resolve(source['archive'], project)
    require(digest(source_zip) == source['archive_sha256'], 'Migrated source fixture archive SHA mismatch')
    verified_archive(source_zip, 'First-set-migrated-source-fixture/', source['file_manifest'])
    source_input = read(args.source_input)
    require(digest(args.source_input) == source['input_sha256'], 'Prepared source input differs from saved scene')
    require(source_input.get('authoringGuard') == 'SOURCE_COPY_EXPLICIT_MEMBERS_ONLY'
            and len(source_input['objects']) == 41 and source_input['sourceMemberCount'] == 110,
            'Source input membership/guard mismatch')
    require(digest(args.frozen_source) == source['source_fixture_sha256'] == source_input['sourceFixtureSha256'],
            'Frozen original source fixture was changed')
    for row in source['evidence_gates']:
        path = resolve(row['path'], project)
        require(digest(path) == row['sha256'], 'Source archive evidence was changed: ' + str(path))
        value = read(path)
        require(value.get('status', '').startswith('PASS'), 'Source archive evidence contains a failed gate')
        if 'artifact_sha256' in value:
            exact_wrapper(value)
        if 'input_sha256' in value:
            require(value['input_sha256'] == source['input_sha256'] and not value.get('failures'), 'Source typed comparison failed')
        gates.append({'gate': 'source_' + path.name, 'path': str(path), 'sha256': digest(path), 'status': 'PASS'})

    def validate_client(value):
        require(value.get('artifact_sha256') == artifact_sha and value.get('profile') == 'minimal', 'Minimal client exact-JAR mismatch')
        require(value.get('status') == 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' and value.get('exit_code') == 0
                and value.get('integrated_save_messages_present') is True and 'termination' not in value,
                'Normal minimum-client world/load/resource/save/exit gate failed')
    client = gate(args.client_report, 'ordinary_minimal_client_resources_save_exit', validate_client)
    qa_path, client_output = wrapper_output(client, 'client_review_output', project)
    require(client_output.get('status', '').startswith('PASS_') and client_output.get('normalStopRequested') is True
            and client_output.get('guard') == 'ISOLATED_SAVED_REVIEW_CLIENT_ONLY', 'Actual client QA output is incomplete')
    require(client_output.get('actualProductionOrigins') and all(row['sha256'] == artifact_sha for row in client_output['actualProductionOrigins']),
            'Client loaded a different actual production JAR')
    client_marker = resolve(client['qa_input']['source'], project)
    require(digest(client_marker) == client['qa_input']['sha256'], 'Client marker changed')
    marker = read(client_marker)
    require(marker.get('productionJarSha256') == artifact_sha and not marker.get('diagnosticOnly'), 'Diagnostic client cannot approve production packaging')
    client_world = resolve(client_output['actualWorldDirectory'], project)
    require(client_world.is_relative_to((project / 'build').resolve()), 'Client QA world must be an isolated project copy')
    require(client.get('derived_world_copy', {}).get('source_unchanged_after_run') is True, 'Client changed its source world')
    client_roots = directory_roots(client_world)
    package_roots = zip_roots(scene_zip, 'First-set-new-placement-scene/')
    require(len(package_roots) == 68 and client_roots == package_roots,
            'Client fixture root states differ from the current audited packaged68-root scene')
    root_equivalence = {'status': 'PASS_EXACT_68_OVERWORLD_ROOT_STATES', 'client_world': str(client_world),
                        'packaged_scene': str(scene_zip), 'client_qa_output_sha256': digest(qa_path),
                        'client_fixture_may_predate_current_scene_author': True,
                        'meaning': 'Same exact68 root coordinates/states; each current packaged scene identity is separately verified. No whole-world byte-identity claim.',
                        'roots': [{'pos': list(pos), 'state': state} for pos, state in sorted(package_roots.items())]}

    xml = ET.parse(args.native_xml).getroot()
    cases = list(xml.iter('testcase'))
    require(len(cases) >= args.min_native_tests and not list(xml.iter('failure')) and not list(xml.iter('error'))
            and not list(xml.iter('skipped')), 'Native GameTest XML contains missing/failed/errored/skipped tests')
    for suite in xml.iter('testsuite'):
        require(all(int(suite.get(key, '0')) == 0 for key in ('failures', 'errors', 'skipped')), 'Native XML summary indicates failures')
    gates.append({'gate': 'native_gametests', 'path': str(args.native_xml), 'sha256': digest(args.native_xml),
                  'tests': len(cases), 'failures': 0, 'errors': 0, 'skipped': 0, 'status': 'PASS',
                  'scope': 'Native developer GameTest evidence; ordinary JAR evidence is separately required above.'})
    rp = gate(args.rp_report, 'full_rp_resource_package', lambda value: require(
        value.get('final_jar_sha256') == artifact_sha and value.get('source_status') == value.get('package_status') == 'PASS',
        'RP resource exact-JAR/source-byte verification failed'))
    require(all(row['source_status'] == row['package_status'] == 'PASS' for row in rp['checks']), 'RP individual resource check failed')
    package = gate(args.package_report, 'production_contains_no_qa', lambda value: require(
        value.get('production_sha256') == artifact_sha and value.get('status') == 'PASS' and value.get('production_contains_qa') is False,
        'Production/optional QA separation gate failed'))
    with zipfile.ZipFile(artifact) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        require(metadata['id'] == 'bloodborne_dw' and 'bloodborne_rp' in metadata.get('provides', []), 'Production registry provider metadata mismatch')
        require(not any(name.startswith('dev/dreamwalker/bloodbornedw/review/') for name in archive.namelist()), 'QA classes leaked into production')
    if args.qa_jar:
        require(digest(args.qa_jar) == package['qa_sha256'], 'Optional QA JAR differs from checked package metadata')
    return {'artifact_sha256': artifact_sha, 'gates': gates, 'scene_zip': scene_zip, 'source_zip': source_zip,
            'client_root_equivalence': root_equivalence, 'full_server_other_error_lines': len(full.get('errors', []))}

def source_files(project: Path):
    for name in REQUIRED_PROJECT:
        require((project / name).is_file(), 'Full source checkpoint missing: ' + name)
    files = {project / name for name in REQUIRED_PROJECT}
    files.update(p for p in project.iterdir() if p.is_file() and
                 (p.suffix.lower() in ('.md', '.json') or p.name.upper().startswith(('README', 'LICENSE', 'COPYING')) or p.name == '.gitignore'))
    for folder in SOURCE_DIRS:
        require((project / folder).is_dir(), 'Full source checkpoint missing directory: ' + folder)
        files.update(p for p in (project / folder).rglob('*') if p.is_file()
                     and not any(part in EXCLUDED for part in p.relative_to(project).parts))
    for path in files:
        require(path.resolve().is_relative_to(project), 'Source checkpoint symlink leaves project: ' + str(path))
        safe_name(path.relative_to(project).as_posix())
    return sorted(files, key=lambda p: p.relative_to(project).as_posix())

def write_zip(destination: Path, entries: dict[str, Path | bytes]):
    require(not destination.exists(), 'Refusing to overwrite a delivery archive: ' + str(destination))
    records = []
    with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, source in sorted(entries.items()):
            safe_name(name)
            data = source.read_bytes() if isinstance(source, Path) else source
            info = zipfile.ZipInfo(name, STAMP)
            info.create_system = 3
            info.external_attr = (0o100755 if PurePosixPath(name).name == 'gradlew' else 0o100644) << 16
            info.compress_type = zipfile.ZIP_STORED if name.endswith(('.zip', '.jar')) else zipfile.ZIP_DEFLATED
            archive.writestr(info, data, compresslevel=9)
            records.append({'path': name, 'bytes': len(data), 'sha256': sha(data)})
    verified_archive(destination, '', records)
    for row in records:
        source = entries[row['path']]
        if isinstance(source, Path):
            require(source.stat().st_size == row['bytes'] and digest(source) == row['sha256'], 'Input changed while packaging: ' + str(source))
    return records

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--revision', required=True, help='Evidence/delivery revision, e.g. v6')
    parser.add_argument('--source-dir', type=Path, default=ROOT)
    parser.add_argument('--jar', type=Path, required=True)
    for name in ('client-report', 'scene-report', 'source-scene-report', 'full-server-report', 'native-xml'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--rp-report', type=Path)
    parser.add_argument('--package-report', type=Path)
    parser.add_argument('--source-input', type=Path)
    parser.add_argument('--frozen-source', type=Path)
    parser.add_argument('--alt-example', type=Path)
    parser.add_argument('--review-readme', type=Path)
    parser.add_argument('--check-matrix', type=Path)
    parser.add_argument('--qa-jar', type=Path, help='Optional separate test-only addon; never required installation')
    parser.add_argument('--extra-evidence', type=Path, action='append', default=[],
                        help='Repeat for additional full-client/restart/ALT/shader/checkpoint reports copied to kit evidence')
    parser.add_argument('--output', type=Path, help='New output directory under this project build/delivery; default build/delivery/<revision>')
    parser.add_argument('--min-native-tests', type=int, default=60)
    parser.add_argument('--check-only', action='store_true', help='Validate all evidence/read-only; do not create any deliverable')
    args = parser.parse_args()
    require(re.fullmatch(r'v[1-9][0-9]*', args.revision), 'Revision must be v followed by a positive integer')
    args.source_dir = args.source_dir.resolve()
    suffix = args.revision.upper()
    defaults = {'rp_report': f'reports/RP_PACKAGE_VERIFICATION_FIRST_SET_FINAL_{suffix}.json',
                'package_report': f'reports/REVIEW_PACKAGE_VERIFICATION_FIRST_SET_FINAL_{suffix}.json',
                'source_input': 'build/source-review-prepared-v6/source-review-input.json',
                'frozen_source': 'build/prototype/Source-coordinate-fixture.zip',
                'alt_example': 'build/prototype/ALT-example.zip', 'review_readme': 'docs/PROTOTYPE_REVIEW.md',
                'check_matrix': 'RELEASE-STATUS.md'}
    for name, default in defaults.items():
        setattr(args, name, resolve(getattr(args, name) or default, args.source_dir))
    for name in ('jar', 'client_report', 'scene_report', 'source_scene_report', 'full_server_report', 'native_xml', 'qa_jar'):
        if getattr(args, name) is not None:
            setattr(args, name, resolve(getattr(args, name), args.source_dir))
    args.extra_evidence = [resolve(path, args.source_dir) for path in args.extra_evidence]
    delivery = (ROOT / 'build/delivery').resolve()
    output = resolve(args.output, ROOT) if args.output else delivery / args.revision
    require(output.is_relative_to(delivery), 'Only this project build/delivery may receive package outputs')
    proof = evidence_gates(args)
    files = source_files(args.source_dir)
    for path in (args.alt_example, args.review_readme, args.check_matrix):
        require(path.is_file(), 'Review kit input missing: ' + str(path))
    with zipfile.ZipFile(args.alt_example) as archive:
        require(archive.testzip() is None and 'pack.mcmeta' in archive.namelist(), 'ALT example resourcepack is invalid')
    mapping = args.source_dir / 'reports/FIRST_SET_MIGRATION_PLAN.json'
    marker = args.source_input.parent / 'world/dreamwalker-source-copy.json'
    provenance = args.source_input.parent / 'source-provenance/rp-preload-migration.json'
    for path in (mapping, marker, provenance):
        require(path.is_file(), 'Source mapping/provenance input missing: ' + str(path))
    for path in args.extra_evidence:
        require(path.is_file(), 'Extra evidence input missing: ' + str(path))
    task = (args.source_dir / 'TASK.md').read_text(encoding='utf8')
    require('Не начинай массовую генерацию' in task and 'визуальная/игровая приёмка' in task,
            'User first-set acceptance gate was not retained in TASK.md')
    if args.check_only:
        print(json.dumps({'status': 'PASS_ALL_PACKAGE_GATES_READ_ONLY', 'revision': args.revision,
                          'artifact_sha256': proof['artifact_sha256'], 'source_files': len(files),
                          'full_task_status': 'NOT_READY_FULL_TASK', 'user_review': 'PENDING_USER_REVIEW'}))
        return
    require(not output.exists(), 'Never overwrite an existing delivery directory; use a new revision/output directory')
    output.mkdir(parents=True)
    base = 'dreamwalker-bb-fabric-1.20.1-' + args.revision
    source_zip = output / (base + '-full-source.zip')
    source_prefix = 'dreamwalker-bb-fabric-1.20.1-source-' + args.revision + '/'
    source_entries = {source_prefix + p.relative_to(args.source_dir).as_posix(): p for p in files}
    source_records = write_zip(source_zip, source_entries)
    source_manifest = {'schema': 'dreamwalker-full-source-checkpoint-v1', 'revision': args.revision,
                       'status': 'FULL_SOURCE_CHECKPOINT_FOR_FIRST_SET_REVIEW', 'full_task_status': 'NOT_READY_FULL_TASK',
                       'user_review': 'PENDING_USER_REVIEW', 'production_jar_sha256': proof['artifact_sha256'],
                       'archive': source_zip.name, 'archive_sha256': digest(source_zip), 'archive_bytes': source_zip.stat().st_size,
                       'file_count': len(source_records), 'files': source_records,
                       'excluded_directories': sorted(EXCLUDED), 'external_original_inputs': 'Not copied; exact original SHA/path inventory is INPUTS.json',
                       'contains_compiled_production_jar': False, 'byte_verification': 'PASS',
                       'determinism': 'Sorted paths, fixed1980 ZIP timestamps, fixed permissions, compressionlevel9; identical inputs/runtime produce identical ZIP bytes.'}
    source_manifest_path = output / (base + '-source-files.json')
    source_manifest_path.write_bytes(json_bytes(source_manifest))
    matrix = {'schema': 'dreamwalker-first-set-delivery-check-matrix-v1', 'revision': args.revision,
              'production_jar_sha256': proof['artifact_sha256'], 'technical_package_gates': proof['gates'],
              'client_root_equivalence': proof['client_root_equivalence'],
              'full_server_other_error_lines': proof['full_server_other_error_lines'],
              'full_task_status': 'NOT_READY_FULL_TASK', 'user_review': 'PENDING_USER_REVIEW',
              'full_modpack_gameplay_compatibility': 'NOT_ACCEPTED', 'mass_city_conversion': 'NOT_RUN',
              'gallery_catalog_and_final_numeric_ids': 'NOT_RUN', 'exact_live_all_nbt_identity': 'NOT_CLAIMED',
              'preservation_scope': 'Reviewed41 objects/110 source cells; typed Original/live unhandled fields and native nonmembers are audited. DFU/timers/generated chunks are declared separately.'}
    readme = (f'# Dreamwalker BB: первый набор {args.revision}\n\n'
              f'Статус полного задания: NOT_READY_FULL_TASK. Приёмка: PENDING_USER_REVIEW.\n\n'
              f'Production JAR SHA256: {proof["artifact_sha256"]}. Он лежит отдельно в mods; полный исходный проект — в full-source ZIP.\n\n'
              'Карты: исходный замороженный фрагмент, его ограниченная миграция41 объектов/110 клеток и новая площадка68 установок. Оригинальный полный город не преобразован. ALT-example.zip — отдельный пример ресурспака.\n\n'
              'TEST-ONLY-OPTIONAL содержит QA-addon только при явном включении в комплект. Для обычной установки и открытия сохранённых карт он не требуется. Зависимости и выбранные версии описаны в MODSET_PROFILE/документации; лишний standalone RP/старый Complete следует убрать при установке объединённого JAR.\n\n'
              'TASK.md требует технические проверки и пользовательскую визуальную/игровую приёмку спорных случаев до массовой генерации и преобразования города. Нативные тесты, штатный клиентский save/exit и архивные проверки не заменяют эту приёмку.\n\n'
              'Живые игровые NBT могут нормализоваться Minecraft; полный typed вход сохраняется в SourceLegacyPayload/архивной provenance. Комплект не заявляет побайтного равенства всего живого мира или полной игровой совместимости остальных модов.\n').encode('utf8')
    kit_entries = {'README-FIRST-SET.md': readme, 'CHECK-MATRIX.json': json_bytes(matrix),
                   'PROJECT-PROGRESS.md': args.source_dir / 'PROGRESS.md',
                   'PROJECT-RELEASE-STATUS.md': args.source_dir / 'RELEASE-STATUS.md',
                   'REVIEW-INSTRUCTIONS.md': args.review_readme, 'PROJECT-CHECK-MATRIX.md': args.check_matrix,
                   'mods/' + args.jar.name: args.jar, 'source/' + source_zip.name: source_zip,
                   'source/' + source_manifest_path.name: source_manifest_path,
                   'worlds/Source-coordinate-fixture.zip': args.frozen_source,
                   'worlds/First-set-migrated-source-fixture.zip': proof['source_zip'],
                   'worlds/First-set-new-placement-scene.zip': proof['scene_zip'],
                   'resourcepacks/ALT-example.zip': args.alt_example,
                   'mapping/FIRST_SET_MIGRATION_PLAN.json': mapping,
                   'mapping/source-review-input.json': args.source_input,
                   'mapping/source-copy-preload-marker.json': marker,
                   'mapping/rp-preload-migration.json': provenance,
                   'mapping/INPUTS.json': args.source_dir / 'INPUTS.json'}
    if (args.source_dir / 'README.txt').is_file():
        kit_entries['README-PLAY.txt'] = args.source_dir / 'README.txt'
    for path in (args.client_report, args.scene_report, args.source_scene_report, args.full_server_report,
                 args.rp_report, args.package_report, args.native_xml):
        kit_entries['evidence/' + path.name] = path
    for path in args.extra_evidence:
        name = 'evidence/' + path.name
        require(name not in kit_entries or kit_entries[name] == path, 'Extra evidence basename collision: ' + path.name)
        kit_entries[name] = path
    if args.qa_jar:
        kit_entries['TEST-ONLY-OPTIONAL/' + args.qa_jar.name] = args.qa_jar
        kit_entries['TEST-ONLY-OPTIONAL/README.txt'] = b'Test-only optional QA addon. Not required installation. Never install it as part of the ordinary production profile.\n'
    kit_path = output / (base + '-first-set-review-kit.zip')
    kit_records = write_zip(kit_path, kit_entries)
    require(digest(args.jar) == proof['artifact_sha256'], 'Production JAR changed during packaging')
    result = {'schema': 'dreamwalker-first-set-delivery-v1', 'revision': args.revision,
              'status': 'PASS_REVIEW_KIT_AND_FULL_SOURCE_BYTES_VERIFIED', 'full_task_status': 'NOT_READY_FULL_TASK',
              'user_review': 'PENDING_USER_REVIEW', 'production_jar_sha256': proof['artifact_sha256'],
              'source_archive': source_manifest, 'review_kit': {'archive': kit_path.name,
                  'sha256': digest(kit_path), 'bytes': kit_path.stat().st_size, 'file_count': len(kit_records), 'files': kit_records},
              'check_matrix': matrix, 'output_directory': str(output),
              'notes': ['A checkpoint for the first-set user gate, not completion of the full task.',
                        'Manifest is standalone to avoid self-hashing or recursive source archives.',
                        'Historical failed reports are preserved in the full source; gates select exact current evidence explicitly.']}
    final_manifest = output / (base + '-delivery-manifest.json')
    final_manifest.write_bytes(json_bytes(result))
    print(json.dumps({'status': result['status'], 'full_task_status': result['full_task_status'],
                      'user_review': result['user_review'], 'source_zip': str(source_zip),
                      'review_kit': str(kit_path), 'manifest': str(final_manifest)}))

if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, FileNotFoundError) as error:
        print(json.dumps({'status': 'FAIL_PACKAGE_GATE_NOT_FROZEN', 'full_task_status': 'NOT_READY_FULL_TASK',
                          'user_review': 'PENDING_USER_REVIEW', 'error': str(error)}), file=sys.stderr)
        raise SystemExit(1)
