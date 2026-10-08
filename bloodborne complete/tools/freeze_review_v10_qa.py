"""Freeze a fully compiled optional V10 QA addon; never replace earlier attempts."""
import argparse, hashlib, json, shutil, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--attempt', type=int, required=True)
    p.add_argument('--log', type=Path, required=True)
    p.add_argument('--production-freeze', type=Path, default=ROOT/'build/frozen-artifacts/v10-attempt-5/manifest.json')
    a = p.parse_args()
    assert 'BUILD SUCCESSFUL' in a.log.read_text(encoding='utf8', errors='replace')
    production = json.loads(a.production_freeze.read_text(encoding='utf8'))
    assert sha(Path(production['artifact'])) == production['sha256']
    assert all(sha(ROOT/row['path']) == row['sha256'] for row in production['mainSourceInputs'])
    destination = ROOT/'build/frozen-artifacts'/f'v10-qa-{a.attempt}'
    assert a.attempt > 0 and not destination.exists(), 'Keep earlier QA binaries'
    source = ROOT/'build/libs/dreamwalker-first-set-review-qa-0.1.0-prototype.5.jar'
    with zipfile.ZipFile(source) as z:
        assert z.testzip() is None
        meta = json.loads(z.read('fabric.mod.json'))
        assert meta['id'] == 'bloodborne_dw_review'
        assert 'dev.dreamwalker.bloodbornedw.review.ReviewV10ClientBootstrap' in meta['entrypoints']['client']
        assert 'dev.dreamwalker.bloodbornedw.review.ReviewV10SceneBootstrap' in meta['entrypoints']['main']
    destination.mkdir(parents=True)
    artifact = destination/source.name
    shutil.copyfile(source, artifact)
    assert sha(source) == sha(artifact)
    result = {'schema': 'dreamwalker-v10-qa-freeze-v1',
              'status': 'QA_ONLY_BUILT_ACTUAL_CLIENT_RUNTIME_PENDING',
              'artifact': str(artifact), 'sha256': sha(artifact),
              'productionJarSha256': production['sha256'],
              'compileLog': str(a.log.resolve()), 'compileLogSha256': sha(a.log),
              'sourceInputs': [{'path': f.relative_to(ROOT).as_posix(), 'sha256': sha(f)}
                               for f in sorted((ROOT/'src/review').rglob('*')) if f.is_file()]}
    (destination/'manifest.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    print(json.dumps({k: v for k, v in result.items() if k != 'sourceInputs'}))


if __name__ == '__main__':
    main()
