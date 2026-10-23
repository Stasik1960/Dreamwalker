"""Capture exactly two immutable40ce stair classes for the final function tool.

Root authoring only: javap, no Minecraft/world/database access. The output
contains primary evidence and zero function decisions; derive() still checks
each actual host state, registered class, source spec and observed predicates.
"""
import argparse
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

SOURCE_SHA = '40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
PRIOR_SHA = '35394c6c93ae00202070a17fb362ce0e453c433008d10be02631800d6f0672e5'
CLASSES = ('dev.dreamwalker.bloodbornedw.composite.ImportedStairBlock',
           'dev.dreamwalker.bloodbornedw.composite.ImportedStairBlock$Trimmed')


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            digest.update(block)
    return digest.hexdigest()


def ref(path):
    return {'path': str(Path(path).resolve(strict=True)), 'sha256': sha(path)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prior-full-gate', type=Path, required=True)
    parser.add_argument('--prior-full-sha256', required=True)
    parser.add_argument('--javap', type=Path, required=True)
    parser.add_argument('--out-dir', type=Path, required=True)
    parser.add_argument('--policy-out', type=Path, required=True)
    args = parser.parse_args()
    prior_ref = ref(args.prior_full_gate)
    require(prior_ref['sha256'] == args.prior_full_sha256 == PRIOR_SHA,
            'Need the accepted immutable prior FULL function gate')
    prior = json.loads(args.prior_full_gate.read_text(encoding='utf-8-sig'))
    require(prior['status'] == 'PASS' and prior['scope'] == 'FULL_CITY' and
            prior['sourceJarSha256'] == SOURCE_SHA, 'Wrong prior source function gate')
    jar = Path(prior['sourceJar']).resolve(strict=True)
    require(sha(jar) == SOURCE_SHA, 'Original40ce JAR changed')
    javap = ref(args.javap)
    require(not args.out_dir.exists() and not args.policy_out.exists(),
            'Fresh output directory and primary-only policy required')
    args.out_dir.mkdir(parents=True)
    rows = []
    with zipfile.ZipFile(jar) as archive:
        for name in CLASSES:
            payload = archive.read(name.replace('.', '/') + '.class')
            result = subprocess.run([javap['path'], '-classpath', str(jar), '-p', '-c', name],
                                    capture_output=True, check=True, timeout=60)
            require(name.encode() in result.stdout and result.stdout.strip(),
                    'javap did not describe the exact requested class')
            path = args.out_dir / (name.rsplit('.', 1)[-1] + '.txt')
            with path.open('xb') as stream:
                stream.write(result.stdout)
            rows.append({'className': name, 'jarClassSha256': hashlib.sha256(payload).hexdigest(),
                         'bytecodeFile': str(path.resolve()), 'bytecodeSha256': sha(path)})
    require(sha(jar) == SOURCE_SHA and ref(args.prior_full_gate) == prior_ref and
            ref(args.javap) == javap, 'Bound authoring inputs changed')
    document = {'schemaVersion': 1, 'status': 'PASS',
                'kind': 'EXACT_STAIR_PRIMARY_BYTECODE_NOT_FUNCTION_DECISIONS',
                'sourceBinding': prior['sourceBinding'], 'sourceJar': str(jar),
                'sourceJarSha256': SOURCE_SHA, 'priorFullFunctionGate': prior_ref,
                'primaryCode': rows, 'states': [], 'javap': javap,
                'generator': ref(__file__), 'gameplayPass': False,
                'worldReads': 0, 'databaseReads': 0, 'mapWrites': 0, 'minecraftRuns': 0}
    args.policy_out.parent.mkdir(parents=True, exist_ok=True)
    with args.policy_out.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(document, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps({'status': document['status'], 'primaryClasses': len(rows),
                      'functionDecisions': 0, 'policy': ref(args.policy_out)}))


if __name__ == '__main__':
    main()
