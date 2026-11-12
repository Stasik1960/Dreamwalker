"""Read back the released world and compare residual meshes with the original mod."""
from collections import Counter
import hashlib
import json
from zipfile import ZipFile

from complete_bush_repair import OUT, REPORT, RELEASE_JAR, RELEASE_WORLD, SavedDimension
from plan_bush_repair import selected_meshes
from probe_bush import BASE, JAR, NATIVE
from world_io import block_state_key, compound


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def verify_catalog(report):
    changes = {int(i): (old, removed) for i, (old, removed) in report['residual_catalog'].items()}
    with ZipFile(JAR) as original, ZipFile(RELEASE_JAR) as repaired:
        catalogs = [z.read(NATIVE + 'catalog.json') for z in (original, repaired)]
        assert [hashlib.sha256(c).hexdigest() for c in catalogs] == [report['input_catalog'], report['catalog']]
        before, after = map(json.loads, catalogs)
        for key in ('cells', 'meshes', 'shapes', 'materials', 'optics', 'wholeObjects'):
            assert before[key] == after[key][:len(before[key])], key
        assert set(changes) == set(range(len(before['cells']), len(after['cells'])))
        old_ids = {before['cells'][old]['mesh'] for old, _ in changes.values()}
        new_ids = {after['cells'][new]['mesh'] for new in changes}
        originals = dict(selected_meshes(original, before, old_ids))
        residuals = dict(selected_meshes(repaired, after, new_ids))
        mappings = [json.loads(z.read(NATIVE + 'visual_physics.json')) for z in (original, repaired)]
        assert mappings[0]['cellToPhysics'] == mappings[1]['cellToPhysics'][:len(before['cells'])]
        assert report['input_catalog'] in mappings[1]['compatibleCatalogs']
        for index, (old, removed) in changes.items():
            old_cell, new_cell = before['cells'][old], after['cells'][index]
            assert all(old_cell[k] == new_cell[k] for k in old_cell.keys() - {'mesh', 'outline', 'key'})
            faces = originals[old_cell['mesh']]
            assert all(0 <= i < len(faces) for i in removed)
            assert [face for i, face in enumerate(faces) if i not in removed] == residuals[new_cell['mesh']]
            assert mappings[0]['cellToPhysics'][old] == mappings[1]['cellToPhysics'][index]
        assert set(original.namelist()) == set(repaired.namelist())
        expected_changes = {NATIVE + name for name in ('catalog.json', 'meshes.bin', 'visual_physics.json',
                                                       'visual-repairs.json', 'selection-bounds.bin')}
        expected_changes |= {'bloodborne_dw/editor2-native-flags.bin', 'bloodborne_dw/repair/native-benches.json',
                             'bloodborne_dw/repair/native-windows.json'}
        for entry in original.infolist():
            if entry.filename not in expected_changes:
                assert original.read(entry.filename) == repaired.read(entry.filename), entry.filename
    print('Verified exact residual geometry and unchanged runtime classes:', len(changes), flush=True)


def verify_world(report):
    for scope in ('overworld', 'bloodborne_old'):
        if scope not in report:
            continue
        folder = OUT if scope == 'overworld' else OUT / 'dimensions/bloodborne_dw/bloodborne_old'
        dimension = SavedDimension(folder)
        roots = {}
        for source, anchor, model in report[scope]['restored']:
            pos = tuple(anchor)
            assert pos not in roots
            roots[pos] = model
            chunk = dimension.world.at(pos)
            assert compound(chunk.state(pos))['Name'].value == model
            entity = compound(chunk.entities[pos])
            assert entity['id'].value == 'bloodborne_dw:composite_cell'
            assert 'NativeGridBase' not in entity
            owner = compound(entity['resident'])
            assert owner['id'].value == model and owner['root'].value == anchor
            if source != anchor:
                assert 'SourceShift' in compound(entity['payload'])
        counts = Counter()
        for edit in report[scope]['edits']:
            pos = tuple(edit['pos'])
            chunk = dimension.world.at(pos)
            assert dimension.index(pos) == edit['after']
            if edit['root']:
                assert pos in roots
                counts['roots'] += 1
            elif edit['after'] is not None:
                assert block_state_key(chunk.state(pos)) == edit['before']
            counts['removed'] += len(edit['quads_removed'])
        assert counts['roots'] == len(roots) == report[scope]['counts']['normal_roots']
        assert counts['removed'] == report[scope]['counts']['removed_baked_quads']
        print(scope, dict(counts), 'unresolved', len(report[scope]['unresolved']), flush=True)


def main():
    report = json.loads(REPORT.read_bytes())
    for path in (RELEASE_JAR, RELEASE_WORLD):
        assert report['files'][path.name] == {'bytes': path.stat().st_size, 'sha256': digest(path)}
    with ZipFile(RELEASE_WORLD) as archive:
        assert set(archive.namelist()) == {'bloodborne/' + f.relative_to(OUT).as_posix()
                                         for f in OUT.rglob('*') if f.is_file()}
        for name in archive.namelist():
            with archive.open(name) as zipped:
                assert hashlib.file_digest(zipped, 'sha256').hexdigest() == digest(OUT / name.split('/', 1)[1]), name
    verify_catalog(report)
    verify_world(report)


if __name__ == '__main__':
    main()
