"""Record the untouched V12 implementation and reference inputs before transfer."""
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
OUT = ROOT / 'docs/remaining-transfer/baseline.json'

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    if OUT.exists():
        raise SystemExit('Baseline already exists; refusing to replace it')
    paths = []
    for tree in ['src/architecture', 'src/rp', 'src/gallery']:
        paths += [p for p in (ROOT / tree).rglob('*') if p.is_file()]
    inputs = REPO / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16'
    data = {
        'schema': 'dreamwalker-remaining-transfer-baseline-v1',
        'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=REPO, text=True).strip(),
        'branch': subprocess.check_output(['git', 'branch', '--show-current'], cwd=REPO, text=True).strip(),
        'scope': 'Transfer only missing construction/decorative objects and variants; reference map and gallery immutable',
        'implementation': {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(paths)},
        'referenceInputs': {p.relative_to(REPO).as_posix(): digest(p) for p in sorted(inputs.glob('*.zip'))},
        'v12JarSha256': digest(REPO / 'releases/Dreamwalker-BB/v12/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.jar'),
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'baseline': str(OUT), 'commit': data['commit'], 'files': len(paths)}, ensure_ascii=False))

if __name__ == '__main__':
    main()
