"""Copy isolated game launch logs byte-exact into portable report evidence."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def export(report_path: Path) -> dict:
    report_path = report_path.resolve()
    report_path.relative_to((ROOT / 'reports').resolve())
    report = json.loads(report_path.read_text(encoding='utf8'))
    run = Path(report['run_directory']).resolve()
    run.relative_to((ROOT / 'build').resolve())
    source = run / 'launch-console.log'
    data = source.read_bytes()
    sha = hashlib.sha256(data).hexdigest()
    expected = report.get('console_sha256')
    if expected is not None and expected != sha:
        raise ValueError('Runtime console differs from the report: ' + str(report_path))
    target = ROOT / 'reports/runtime' / (report_path.stem + '.log')
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.read_bytes() != data:
        raise ValueError('Refusing to overwrite different portable runtime evidence: ' + str(target))
    target.write_bytes(data)
    if target.read_bytes() != data or source.read_bytes() != data:
        raise IOError('Byte-exact runtime evidence/source verification failed')
    record = {'path': target.relative_to(ROOT).as_posix(), 'source': str(source),
              'sha256': sha, 'bytes': len(data), 'byte_exact': True,
              'source_unchanged': True}
    report['portable_console_evidence'] = record
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    return {'report': str(report_path), **record}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path, nargs='+')
    args = parser.parse_args()
    for report in args.report:
        print(json.dumps(export(report), ensure_ascii=False))

if __name__ == '__main__':
    main()
