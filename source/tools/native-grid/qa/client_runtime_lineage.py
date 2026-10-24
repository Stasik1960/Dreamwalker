"""One direct ac56 client-only successor. File comparison, never gameplay.

Producer scans both JAR payload inventories once. Consumers verify the pinned
proof, inputs and this helper's bytes; they do not repeat the entry scan.
"""
import argparse
import hashlib
import json
import os
import zipfile
from pathlib import Path, PurePosixPath

PREDECESSOR_SHA = 'ac56c8643cf46c245793191c0e5c099d95f755160b0a7109b2d6d6f5091ed0aa'
KIND = 'EXACT_CLIENT_ONLY_RUNTIME_LINEAGE'
STATUS = 'PASS_EXACT_CLIENT_ONLY_RUNTIME_COMPATIBILITY_NOT_GAMEPLAY'
PREFIX = 'dev/dreamwalker/bloodbornedw/composite/'
ALLOWED_CLASSES = frozenset(PREFIX + name for name in
                           ('NativeGridClient.class', 'NativeGridClient$SharedMesh.class', 'NativeGridClient$Model.class'))


def require(value, message):
    if not value:raise ValueError(message)


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b''):digest.update(chunk)
    return digest.hexdigest()


def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def valid_sha(value):
    return isinstance(value, str) and len(value) == 64 and all(ch in '0123456789abcdef' for ch in value)


def binding(path, expected=None):
    path = Path(path).resolve(strict=True)
    require(path.is_file(), 'Bound input is not a file')
    digest = sha(path)
    require(expected is None or valid_sha(expected) and digest == expected, 'Bound file SHA changed: ' + str(path))
    return {'path': str(path), 'sha256': digest}


def checked(reference):
    require(isinstance(reference, dict) and set(reference) == {'path', 'sha256'}, 'Exact path/SHA reference required')
    binding(reference['path'], reference['sha256'])
    return read(reference['path'])


def source_review(reference, source):
    review = checked(reference)
    require(review['kind'] == 'EXACT_NATIVE_CLIENT_LAZY_FRAPI_CACHE_SOURCE_REVIEW' and
            review['status'] == 'ROOT_SOURCE_APPROVED_CLIENT_LAZY_FRAPI_CACHE_NOT_GAMEPLAY' and
            review['readyForDelivery'] is False and isinstance(review['observations'], list) and review['observations'] and
            all(isinstance(row, str) and row.strip() for row in review['observations']) and
            isinstance(review['allowedChangedClasses'], list) and len(review['allowedChangedClasses']) == 3 and
            set(review['allowedChangedClasses']) == ALLOWED_CLASSES and review['source'] == source,
            'Exact root-reviewed NativeGridClient source scope required')
    binding(source['path'], source['sha256'])
    before = review['predecessorSource']
    binding(before['path'], before['sha256'])
    require(before['sha256'] != source['sha256'] and Path(source['path']).name == 'NativeGridClient.java',
            'Actual changed client source and predecessor required')
    return review


def entries(path):
    result = {}
    with zipfile.ZipFile(path) as jar:
        for entry in jar.infolist():
            name = entry.filename;canonical_name = name[:-1] if entry.is_dir() else name
            relative = PurePosixPath(canonical_name)
            require(name not in result and canonical_name and not relative.is_absolute() and relative.as_posix() == canonical_name and
                    '..' not in relative.parts and '\\' not in name, 'Duplicate/unsafe JAR payload entry')
            digest = hashlib.sha256()
            with jar.open(entry) as stream:
                for chunk in iter(lambda: stream.read(1 << 20), b''):digest.update(chunk)
            result[name] = digest.hexdigest()
    return result


def read_lineage(reference, candidate, current_source):
    """Pinned file-only authority. No JAR-entry rescan or cross-call cache."""
    proof = checked(reference)
    require(proof['schemaVersion'] == 1 and proof['kind'] == KIND and proof['status'] == STATUS and
            proof['gameplayPass'] is False and proof['readyForDelivery'] is False,
            'Client-only proof is not an explicit compatibility result')
    require(proof['predecessor']['sha256'] == PREDECESSOR_SHA and proof['candidate'] == candidate and
            candidate['sha256'] != PREDECESSOR_SHA, 'Only the fixed direct ac56 successor is supported')
    binding(proof['predecessor']['path'], PREDECESSOR_SHA)
    binding(candidate['path'], candidate['sha256'])
    producer = proof['producer'];binding(producer['path'], producer['sha256'])
    require(producer['sha256'] == sha(__file__), 'Client-only proof producer differs from this current helper')
    source = proof['source'];require(binding(current_source) == source, 'Current NativeGridClient source differs')
    source_review(proof['rootSourceReviewRef'], source)
    require(proof['allowedChangedClasses'] == sorted(ALLOWED_CLASSES) and proof['sameEntryNames'] is True and
            proof['allOtherPayloadBytesExact'] is True and proof['allResourcesAndMetadataByteExact'] is True and
            proof['allServerAndCommonClassesByteExact'] is True and proof['addedEntries'] == proof['removedEntries'] == [] and
            proof['entryCount'] > 3 and valid_sha(proof['unchangedEntryInventorySha256']),
            'Complete unchanged server/resource payload authority required')
    changed = proof['changedClasses']
    require(isinstance(changed, dict) and changed and set(changed) <= ALLOWED_CLASSES and
            all(set(row) == {'beforeSha256', 'afterSha256'} and valid_sha(row['beforeSha256']) and
                valid_sha(row['afterSha256']) and row['beforeSha256'] != row['afterSha256'] for row in changed.values()),
            'A nonempty exact client-only binary delta is required')
    return proof


def produce(args):
    old = binding(args.predecessor, PREDECESSOR_SHA);new = binding(args.candidate, args.candidate_sha256)
    require(new['sha256'] != PREDECESSOR_SHA, 'Candidate must have an actual nonempty delta')
    source = binding(args.client_source, args.client_source_sha256)
    review_ref = binding(args.root_source_review, args.root_source_review_sha256)
    source_review(review_ref, source)
    before, after = entries(old['path']), entries(new['path'])
    require(before.keys() == after.keys() and ALLOWED_CLASSES <= before.keys(), 'JAR entry names differ or client classes missing')
    changed = {name: {'beforeSha256': before[name], 'afterSha256': after[name]}
               for name in sorted(before) if before[name] != after[name]}
    require(changed and set(changed) <= ALLOWED_CLASSES, 'JAR changed outside the exact three client class entries')
    stable = hashlib.sha256()
    for name in sorted(before.keys() - ALLOWED_CLASSES):stable.update((name + '\0' + before[name] + '\n').encode())
    proof = {'schemaVersion': 1, 'kind': KIND, 'status': STATUS, 'predecessor': old, 'candidate': new,
             'rootSourceReviewRef': review_ref, 'source': source, 'producer': binding(__file__),
             'allowedChangedClasses': sorted(ALLOWED_CLASSES), 'changedClasses': changed, 'entryCount': len(before),
             'sameEntryNames': True, 'addedEntries': [], 'removedEntries': [], 'allOtherPayloadBytesExact': True,
             'allResourcesAndMetadataByteExact': True, 'allServerAndCommonClassesByteExact': True,
             'unchangedEntryInventorySha256': stable.hexdigest(), 'gameplayPass': False, 'readyForDelivery': False,
             'builds': 0, 'jvmRuns': 0, 'sqlReads': 0, 'worldReads': 0, 'mapWrites': 0}
    for ref in (old, new, source, review_ref, proof['producer']):binding(ref['path'], ref['sha256'])
    source_review(review_ref, source)
    require(not args.out.exists(), 'Fresh client-only compatibility output required')
    args.out.parent.mkdir(parents=True, exist_ok=True);temporary = args.out.with_name(args.out.name + '.writing')
    with temporary.open('x', encoding='utf-8') as stream:
        json.dump(proof, stream, ensure_ascii=False, indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())
    os.link(temporary, args.out);temporary.unlink()
    return proof


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('predecessor', 'candidate', 'client-source', 'root-source-review', 'out'):
        parser.add_argument('--' + name, type=Path, required=True)
    for name in ('candidate-sha256', 'client-source-sha256', 'root-source-review-sha256'):
        parser.add_argument('--' + name, required=True)
    args = parser.parse_args();proof = produce(args)
    print(json.dumps({'status': proof['status'], 'out': str(args.out), 'sha256': sha(args.out),
                      'changedClasses': sorted(proof['changedClasses']), 'gameplayPass': False}))


if __name__ == '__main__':main()
