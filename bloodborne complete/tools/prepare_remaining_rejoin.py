"""Prepare read-only client rejoin expectations from a SHA-bound network PASS.

The marker performs no placement/use/pick/removal. It navigates in the copied
fresh test world and verifies server-synchronized owner UUID and exact state.
"""
import argparse
import hashlib
import json
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to((ROOT / 'build').resolve()) or output.exists():
        raise ValueError('Use a fresh output directory under project build/')
    previous = json.loads(args.report.read_text(encoding='utf-8'))
    bound = hashlib.sha256(args.jar.read_bytes()).hexdigest()
    if previous.get('status') != 'PASS' or previous.get('productionJarSha256') != bound:
        raise ValueError('Successful first network report must bind the same final JAR')
    expected = []
    for row in previous['cases']:
        if row.get('status') != 'PASS' or not row['id'].startswith('bloodborne_dw:source_'):
            raise ValueError('Every retained case must be a new source object with PASS')
        owner = str(uuid.UUID(row['retainedOwnerUuid']))
        root = row['root']
        if len(root) != 3 or any(type(n) is not int or abs(n) > 10000 for n in root):
            raise ValueError('Expected retained root must be bounded integer coordinates')
        expected.append({'case': row['case'], 'registryId': row['id'], 'variant': row['variant'],
                         'root': root, 'expectedOwnerUuid': owner,
                         'rotation': row['retainedRotation'], 'open': row['retainedOpen']})
    if not 1 <= len(expected) <= 16:
        raise ValueError('Expected 1..16 completed retained cases')
    marker = {'productionJarSha256': bound, 'freshTestWorldOnly': True, 'mode': 'VERIFY_REJOIN',
              'expectedCases': expected, 'manualMouseKeyboardInput': 'NOT_RUN',
              'firstNetworkReportSha256': hashlib.sha256(args.report.read_bytes()).hexdigest(),
              'method': 'Read-only synced server owner UUID/state after actual server restart; camera tp only.'}
    output.mkdir(parents=True)
    path = output / 'remaining-transfer-network-input.json'
    path.write_text(json.dumps(marker, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': 'PREPARED_NOT_REJOIN_PASS', 'cases': len(expected),
                      'productionJarSha256': bound, 'marker': str(path)}))


if __name__ == '__main__':
    main()
