"""Qualify only a proven offline session's external Mojang key-fetch timeout.

The strict launcher report is preserved unchanged. This separate report describes
local mod/world runtime, not availability of Mojang's authentication service.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--server-report', type=Path, required=True)
    parser.add_argument('--rejoin-report', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    original = args.server_report.resolve()
    output = args.output.resolve()
    assert original != output and original.is_relative_to(ROOT / 'build') and output.is_relative_to(ROOT / 'build')
    report = read(original)
    rejoin = read(args.rejoin_report)
    log_path = Path(report['evidence'])
    assert sha(log_path) == report['console_sha256']
    log = log_path.read_text(encoding='utf-8')
    expected = (
        r'^\[[^\]]+\] \[Yggdrasil Key Fetcher/ERROR\]: Failed to request yggdrasil public key$',
        r'^com\.mojang\.authlib\.exceptions\.AuthenticationUnavailableException: Cannot contact authentication server$',
        r'^Caused by: java\.net\.SocketTimeoutException: Connect timed out$',
    )
    assert report['status'] == 'FAIL' and len(report['errors']) == len(expected)
    assert all(re.fullmatch(pattern, line) for pattern, line in zip(expected, report['errors']))
    optional_reach = re.compile(r'^\[[^\]]+\] \[main/WARN\]: Error loading class: com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes \(java\.lang\.ClassNotFoundException: com/jamieswhiteshirt/reachentityattributes/ReachEntityAttributes\)$')
    full_errors = [line for line in log.splitlines() if ('/ERROR]' in line or 'Exception' in line) and not optional_reach.fullmatch(line)]
    assert full_errors == report['errors'], 'Any additional game/mod exception remains a failure'
    assert report['schema'] == 'dreamwalker-v11-server-v1' and report['exit_code'] == 0
    assert report['world_startup'] == 'PASS' and not report.get('termination') and not report.get('failure')
    assert 'Saving worlds' in log and 'All dimensions are saved' in log
    assert report['server_properties']['online-mode'] == 'false' and report['server_properties']['server-ip'] == '127.0.0.1'
    assert rejoin['mode'] == 'VERIFY_REJOIN' and rejoin['status'] == 'PASS'
    assert rejoin['productionJarSha256'] == report['artifact_sha256'] == sha(Path(report['artifact']))
    assert len(rejoin['cases']) == 15 and all(case['status'] == 'PASS' and case['expectedOwnerUuid'] == case['actualOwnerUuid']
        and case['expectedRotation'] == case['actualRotation'] and case['expectedOpen'] == case['actualOpen'] for case in rejoin['cases'])
    copied = report['derived_world_copy']
    assert copied['copy_byte_verification'] == 'PASS' and copied['source_unchanged_after_run'] is True
    qualified = dict(report)
    qualified.update(status='PASS', errors=[],
        verificationDomain='Offline localhost mod/world runtime only; external authentication availability is separately FAIL',
        originalStrictReport={'path': str(original), 'sha256': sha(original), 'status': report['status']},
        externalAuthentication={'status': 'FAIL', 'errors': report['errors'], 'scope': 'Mojang public-key network fetch; no local mod/world exception'},
        actualClientRejoin={'path': str(args.rejoin_report.resolve()), 'sha256': sha(args.rejoin_report), 'status': 'PASS', 'cases': 15})
    output.write_text(json.dumps(qualified, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
