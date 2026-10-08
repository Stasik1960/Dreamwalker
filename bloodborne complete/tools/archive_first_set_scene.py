"""Verify saved first-set roots/identity, then package the separately built review world."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import uuid
import zipfile
from world_io import RegionFile, compound, section_blocks, block_state_key

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def inventory(scene):
    roots, entities, block_entities = {}, [], {}
    for path in sorted((scene / 'region').glob('*.mca')):
        if not path.stat().st_size:
            continue
        for chunk in RegionFile(path.read_bytes()).chunks():
            fields = compound(chunk.nbt().root)
            cx, cz = fields['xPos'].value, fields['zPos'].value
            for section in fields['sections'].value:
                decoded = section_blocks(section)
                if not decoded:
                    continue
                palette, indices = decoded
                keys = [block_state_key(state) for state in palette]
                matching = {i for i, key in enumerate(keys) if key.startswith('bloodborne_dw:prototype_')}
                if not matching:
                    continue
                sy = compound(section)['Y'].value
                for i, pi in enumerate(indices):
                    if pi in matching:
                        roots[(cx * 16 + i % 16, sy * 16 + i // 256, cz * 16 + i // 16 % 16)] = keys[pi]
            for be in fields.get('block_entities').value:
                data = compound(be)
                block_entities[(data['x'].value, data['y'].value, data['z'].value)] = be
    for path in sorted((scene / 'entities').glob('*.mca')):
        if not path.stat().st_size:
            continue
        for chunk in RegionFile(path.read_bytes()).chunks():
            for entity in compound(chunk.nbt().root)['Entities'].value:
                data = compound(entity)
                entities.append({'id': data['id'].value, 'pos': [n.value for n in data['Pos'].value]})
    return roots, block_entities, entities


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server-report', type=Path, required=True)
    parser.add_argument('--author-report', type=Path, required=True)
    parser.add_argument('--scene-input', type=Path, default=ROOT / 'tools/first_set_scene_input.json')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/prototype/First-set-new-placement-scene.zip')
    parser.add_argument('--report', type=Path, default=ROOT / 'reports/FIRST_SET_REVIEW_SCENE.json')
    args = parser.parse_args()
    report = json.loads(args.server_report.read_text(encoding='utf8'))
    author = json.loads(args.author_report.read_text(encoding='utf8'))
    requested = json.loads(args.scene_input.read_text(encoding='utf8'))
    assert report['status'] == 'PASS' and report['exit_code'] == 0 and report['original_world_loaded'] is False
    assert report['artifact_sha256'] == author['artifact_sha256'], 'Author/reopen use different production artifacts'
    assert not any(row.get('extra_mod') for row in report['modset']), 'Production-only reopen must exclude authoring QA'
    assert report['derived_world_copy']['source_unchanged_after_run']
    scene = Path(report['run_directory']) / 'isolated-smoke-world'
    assert scene.resolve().is_relative_to((ROOT / 'build').resolve()) and (scene / 'level.dat').is_file()
    roots, block_entities, entities = inventory(scene)
    original_scene = Path(author['run_directory']) / 'isolated-smoke-world'
    old_roots, old_entities, _ = inventory(original_scene)
    expected = {tuple(row['root']): row for row in requested['objects']}
    assert len(expected) == 68 and set(roots) == set(expected), 'Exact 68 scene roots must survive normal reopen/save'
    assert roots == old_roots, 'A placed root state changed during production-only reopen'
    identities = []
    authored = {tuple(row['root']): row
                for row in author['review_scene_output']['result']['objects']}
    for pos, state in roots.items():
        assert state.split('[')[0] == 'bloodborne_dw:' + expected[pos]['kind']
        row = authored[pos]
        if 'ownerUuid' not in row:
            continue
        be = block_entities[pos]
        assert be == old_entities[pos], 'Composite root typed NBT changed during reopen/save'
        resident = compound(compound(be)['resident'])
        actual_uuid = str(uuid.UUID(bytes=struct.pack('>4I', *(value & 0xffffffff for value in resident['uuid'].value))))
        assert actual_uuid == row['ownerUuid']
        identities.append({'root': list(pos), 'state': state, 'uuid': actual_uuid, 'typed_nbt_unchanged': True})
    cached = 0
    for pos, be in block_entities.items():
        if compound(be)['id'].value == 'bloodborne_dw:composite_cell':
            assert old_entities.get(pos) == be, 'Cached helper typed NBT changed during reopen/save'
            cached += 1
    for fixture in requested['nativeBlocks']:
        if fixture.get('preserve'):
            pos = tuple(fixture['pos'])
            assert block_entities.get(pos) == old_entities.get(pos), 'Foreign native typed NBT changed'
    assert {'bloodborne_rp:tree1', 'bloodborne_rp:hunterlamp'}.issubset({row['id'] for row in entities})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    records = []
    with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(scene.rglob('*')):
            if not path.is_file() or path.name == 'session.lock':
                continue
            data, relative = path.read_bytes(), path.relative_to(scene).as_posix()
            info = zipfile.ZipInfo('First-set-new-placement-scene/' + relative, (2026, 10, 7, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
            records.append({'path': relative, 'bytes': len(data), 'sha256': digest(data)})
    with zipfile.ZipFile(args.output) as archive:
        for row in records:
            assert digest(archive.read('First-set-new-placement-scene/' + row['path'])) == row['sha256']
    result = {'schema': 'dreamwalker-first-set-review-scene-v1', 'status': 'PACKAGED_PENDING_USER_REVIEW',
              'kind': 'Separate new-build first-set scene; no original city',
              'production_jar_sha256': report['artifact_sha256'], 'server_report': str(args.server_report),
              'author_report': str(args.author_report), 'normal_author_and_production_reopen_stop': 'PASS',
              'root_states_persisted': len(roots), 'composite_identities': identities, 'cached_composite_bes_preserved': cached,
              'native_block_entity_retention': 'PASS_TYPED_NBT', 'entities': entities,
              'zip_byte_verification': 'PASS', 'files': records, 'omitted_files': ['session.lock'],
              'visuals': 'NOT_RUN', 'creative_ui': 'NOT_RUN', 'output': str(args.output.resolve()),
              'sha256': digest(args.output.read_bytes())}
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'scene': result['output'], 'roots': len(roots), 'identities': len(identities), 'sha256': result['sha256']}))


if __name__ == '__main__':
    main()
