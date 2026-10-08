"""Refresh current release metadata while retaining exact historical input bytes."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    path = ROOT / 'INPUTS.json'
    before = path.read_bytes()
    document = json.loads(before)
    checkpoint = json.loads((ROOT / 'reports/REVIEW_V8_CHECKPOINT.json').read_text(encoding='utf8'))
    jar = ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.3.jar'
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == checkpoint['production_jar_sha256']
    assert checkpoint['status'] == 'READY_FOR_REPEAT_USER_REVIEW'
    original_inputs = json.dumps(document['inputs'], sort_keys=True)
    target = document['target']
    if target.get('first_set_evidence_revision') != 'v8-release':
        history = ROOT / ('reports/input-history/INPUTS-before-v8-current-' + hashlib.sha256(before).hexdigest() + '.json')
        history.parent.mkdir(parents=True, exist_ok=True)
        if history.exists():
            assert history.read_bytes() == before
        else:
            history.write_bytes(before)
        target.setdefault('historical_checkpoints', []).append({
            'revision': target.get('first_set_evidence_revision'),
            'build_status': target.get('build_status'),
            'production_version': '0.1.0-prototype.2',
            'production_jar_sha256': target.pop('prototype_2_artifact_sha256', None),
            'original_metadata': history.relative_to(ROOT).as_posix(),
            'original_metadata_sha256': hashlib.sha256(before).hexdigest(),
            'review_result': 'PARTIALLY_ACCEPTED',
        })
    target.update({
        'build_status': 'PROTOTYPE_3_REMAPPED_REVIEW_V8_VERIFIED_FULL_TASK_NOT_READY',
        'production_version': checkpoint['production_version'],
        'production_jar_sha256': checkpoint['production_jar_sha256'],
        'first_set_evidence_revision': 'v8-release',
        'user_visual_game_acceptance': checkpoint['user_review'],
        'prior_set_review': checkpoint['prior_set_review'],
        'full_task_status': checkpoint['full_task_status'],
        'current_checkpoint': 'reports/REVIEW_V8_CHECKPOINT.json',
    })
    assert original_inputs == json.dumps(document['inputs'], sort_keys=True)
    path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': 'PASS_CURRENT_V8_INPUT_METADATA_HISTORY_PRESERVED', 'revision': target['first_set_evidence_revision'], 'production_jar_sha256': target['production_jar_sha256']}))


if __name__ == '__main__':
    main()
