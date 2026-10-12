"""Copy an isolated flat function-test world and bind fresh/reopen client evidence.

No Java launch, source map edits, gallery authoring or user profile operations.
"""
from pathlib import Path
import argparse
import json
import shutil
from run_v11_server import files_without_links, ordinary, sha
from world_io import read_nbt, write_nbt, compound, Tag

ROOT = Path(__file__).resolve().parents[1]


def write(path, document):
    path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--fixture-source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--previous-evidence', type=Path)
    args = parser.parse_args()
    production = args.production_jar.resolve()
    source = args.fixture_source.resolve()
    output = args.output.resolve()
    for path in (production, source):
        ordinary(path)
        assert path.is_relative_to(ROOT / 'build'), 'Only isolated project-build inputs are allowed'
    assert output.is_relative_to(ROOT / 'build') and not output.exists()
    assert (source / 'level.dat').is_file()
    assert not (source / 'gallery-manifest.json').exists(), 'A function fixture cannot reuse a gallery'
    level = read_nbt(source / 'level.dat')
    data = compound(compound(level.root)['Data'])
    generation = compound(compound(compound(data['WorldGenSettings'])['dimensions'])['minecraft:overworld'])
    assert compound(generation['generator'])['type'].value == 'minecraft:flat', 'A fresh ordinary flat world is required'
    digest = sha(production)
    marker = {'productionJarSha256': digest, 'freshTestWorldOnly': True, 'v127NativeFunctions': True,
              'purpose': 'ISOLATED_FUNCTION_TEST_NOT_GALLERY',
              'frames': [{'fileName': 'v127-function-proof.png', 'camera': [.5, 64, -3.5, 0, 18]}]}
    if args.previous_evidence:
        prior_path = args.previous_evidence.resolve()
        ordinary(prior_path)
        assert prior_path.is_relative_to(ROOT / 'build')
        prior = json.loads(prior_path.read_text(encoding='utf-8-sig'))
        assert prior['status'] in ('PASS_ENGINE_CAPTURE', 'PASS_ENGINE_REOPEN')
        assert prior['productionJarSha256'] == digest and 'persistedState' in prior
        fixture = json.loads((source / 'v127-qa-fixture.json').read_text(encoding='utf-8-sig'))
        assert fixture['productionJarSha256'] == digest
        marker.update(v127ReopenOnly=True, previousEvidenceFile=str(prior_path),
                      previousEvidenceSha256=sha(prior_path))
    else:
        assert not (source / 'v127-qa-fixture.json').exists(), 'Fresh mode needs an unmodified new flat server world'
    output.mkdir(parents=True)
    world = output / 'world'
    world.mkdir()
    records = []
    for path in sorted(files_without_links(source)):
        relative = path.relative_to(source)
        target = world / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        assert sha(target) == sha(path)
        records.append({'path': relative.as_posix(), 'sha256': sha(path), 'bytes': path.stat().st_size})
    if not args.previous_evidence:
        data['allowCommands'] = Tag(1, 1)
        data['GameType'] = Tag(3, 1)
        data['SpawnX'] = Tag(3, 0)
        data['SpawnY'] = Tag(3, 64)
        data['SpawnZ'] = Tag(3, -6)
        write_nbt(world / 'level.dat', level)
    assert all(sha(source / row['path']) == row['sha256'] for row in records), 'Source evidence world changed'
    write(output / 'client-capture-input.json', marker)
    report = {'status': 'PASS_PREPARED_NATIVE_NOT_RUN', 'productionJarSha256': digest,
              'sourceWorld': str(source), 'sourceWorldUnchanged': True, 'fixture': str(world),
              'captureInput': str(output / 'client-capture-input.json'),
              'reopenOnly': bool(args.previous_evidence), 'copiedFiles': records,
              'sourceResourcePackInstalled': False, 'galleryCreated': False}
    write(output / 'prepare-report.json', report)
    print(json.dumps({key: report[key] for key in ('status', 'fixture', 'captureInput', 'reopenOnly')}))


if __name__ == '__main__':
    main()
