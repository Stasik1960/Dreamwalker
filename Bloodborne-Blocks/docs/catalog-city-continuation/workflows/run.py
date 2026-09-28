"""Run the existing diagnostic tools without changing their gates or inputs."""
import argparse, datetime, json, os, subprocess, sys, time
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
RUN = PROJECT / 'build/catalog-city-diagnostic-20260927-run3'
SOURCE = PROJECT / 'reference-inputs/latest-modded-world.zip'
SOURCE_SHA = 'c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9'
sys.path.insert(0, str(PROJECT / 'tools'))

def step(name, *args, expected=(0,)):
    command = [sys.executable, '-B', '-X', 'utf8', *map(str, args)]
    record = {'command': command, 'startedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat()}
    print('START', name, flush=True)
    start = time.monotonic()
    with (RUN / (name + '.log')).open('wb') as log:
        process = subprocess.Popen(command, cwd=PROJECT, stdout=log, stderr=subprocess.STDOUT,
                                   env=dict(os.environ, PYTHONDONTWRITEBYTECODE='1'))
        record['processId'] = process.pid
        (RUN / (name + '.command.json')).write_text(json.dumps(record, indent=2)+'\n', encoding='utf8')
        try:
            process.wait(timeout=2400)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=30)
            raise
    record.update(exitCode=process.returncode, seconds=round(time.monotonic()-start, 2))
    (RUN / (name + '.command.json')).write_text(json.dumps(record, indent=2)+'\n', encoding='utf8')
    print('DONE', name, json.dumps(record), flush=True)
    if process.returncode not in expected:
        raise SystemExit(process.returncode)

parser = argparse.ArgumentParser()
parser.add_argument('phase', choices=('convert', 'verify', 'all'))
parser.add_argument('--run-dir', type=Path)
args = parser.parse_args()
if args.run_dir:
    RUN = args.run_dir.resolve()
RUN.mkdir(parents=True, exist_ok=True)
if args.phase in ('convert', 'all'):
    step('01-convert', 'tools/convert_logical_world.py', SOURCE, RUN/'world', '--source-mode', 'modded',
         '--atomic-owner-groups', '--expected-source-sha256', SOURCE_SHA, '--report', RUN/'first.json', '--progress')
if args.phase in ('verify', 'all'):
    step('02-independent', 'tools/check_logical_world.py', SOURCE, RUN/'world', RUN/'first.json',
         '--resources', 'src/main/resources/bloodborne_blocks/logical')
    step('03-protected', 'tools/check_composite_world.py', RUN/'world', RUN/'protected.json', expected=(0, 1))
    step('04-residuals', 'tools/classify_atomic_world_scan.py', RUN/'first.json', RUN/'protected.json',
         '--output', RUN/'residuals.json.gz')
    from convert_logical_world import safe_extract, hash_tree, World, DEFAULT_RESOURCES, load_defaults
    from city_palette import audit_helpers
    source_copy = safe_extract(SOURCE, RUN/'source-copy')
    step('05-preservation', 'tools/verify_modded_preservation.py', source_copy, RUN/'world', RUN/'first.json',
         '--result', RUN/'preservation.json')
    print('START helper audit', flush=True)
    helper_audit = audit_helpers(World(RUN/'world', load_defaults(DEFAULT_RESOURCES)), DEFAULT_RESOURCES.parent/'city')
    (RUN/'helpers.json').write_text(json.dumps(helper_audit, indent=2)+'\n', encoding='utf8')
    print('DONE helper audit', helper_audit['checked'], 'orphan count', len(helper_audit['orphans']), flush=True)
    step('06-second-pass', 'tools/convert_logical_world.py', RUN/'world', RUN/'second-world', '--source-mode', 'modded',
         '--atomic-owner-groups', '--report', RUN/'second.json', '--progress')
    step('07-second-independent', 'tools/check_logical_world.py', RUN/'world', RUN/'second-world', RUN/'second.json',
         '--resources', 'src/main/resources/bloodborne_blocks/logical')
    step('08-second-preservation', 'tools/verify_modded_preservation.py', RUN/'world', RUN/'second-world', RUN/'second.json',
         '--result', RUN/'second-preservation.json')
    first, second = hash_tree(RUN/'world'), hash_tree(RUN/'second-world')
    comparison = {'byteIdentical': first == second, 'firstFiles': len(first), 'secondFiles': len(second),
                  'changedPaths': sorted(key for key in first.keys() | second.keys() if first.get(key) != second.get(key))}
    (RUN/'second-byte-comparison.json').write_text(json.dumps(comparison, indent=2)+'\n', encoding='utf8')
    print(json.dumps(comparison), flush=True)
