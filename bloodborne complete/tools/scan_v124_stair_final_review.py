"""Find byte-identical previously reviewed native frames; never infer visual PASS."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/v12.4-dedup'
RUN = ROOT / 'build/runtime-client-v124-comparison-final-2'
PREVIOUS = ROOT / 'build/runtime-client-v124-comparison-final-1'
PAGES = set(range(81, 108)) | set(range(152, 177))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    reviewed = json.loads((DOC / 'native-C30-stair-review-preserved.json').read_text(encoding='utf8'))
    previous = {row['page']: row for row in reviewed['frames']}
    result = {'status': 'REVIEW_PENDING', 'assignedPages': sorted(PAGES),
              'byteIdenticalPreviouslyReviewed': [], 'freshRawReviewRequired': [], 'notYetCaptured': []}
    for page in sorted(PAGES):
        path = RUN / 'screenshots' / f'v124-type-comparison-{page:03}.png'
        if not path.exists():
            result['notYetCaptured'].append(page)
            continue
        digest = sha(path)
        row = previous.get(page)
        if row and digest == row['sha256'] and digest == sha(PREVIOUS / 'screenshots' / row['fileName']):
            result['byteIdenticalPreviouslyReviewed'].append({'page': page, 'fileName': path.name, 'sha256': digest})
        else:
            result['freshRawReviewRequired'].append({'page': page, 'fileName': path.name, 'sha256': digest})
    target = ROOT / 'build/v124-stair-final2-review-scan.json'
    target.write_bytes((json.dumps(result, ensure_ascii=False, indent=2) + '\n').encode('utf8'))
    print(json.dumps({'status': result['status'],
                     'exactC30Pages': [row['page'] for row in result['byteIdenticalPreviouslyReviewed']],
                     'freshPages': [row['page'] for row in result['freshRawReviewRequired']],
                     'pendingPages': result['notYetCaptured']}))


if __name__ == '__main__':
    main()
