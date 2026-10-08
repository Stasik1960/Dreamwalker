"""Record the two narrow live-NBT integration hooks against the supplied RP source ZIP."""
from __future__ import annotations
import argparse
import difflib
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
HOOKS = {
    'src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java',
    'src/rp/java/dev/dreamwalker/bloodbornerp/mob/RpMobEntity.java',
}

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--revision', default='V7', help='Exact source author/postsaved evidence revision, e.g. V7')
    args = parser.parse_args()
    revision = args.revision.upper()
    assert re.fullmatch(r'V[1-9][0-9]*', revision), 'Evidence revision must be V followed by a positive integer'
    imported = json.loads((ROOT / 'reports/RP_IMPORT.json').read_text(encoding='utf8'))
    source = Path(imported['source'])
    assert digest(source.read_bytes()) == imported['source_sha256']
    rows = []
    patches = []
    with zipfile.ZipFile(source) as archive:
        for row in imported['imported_files']:
            if not row['path'].startswith('src/rp/java/'):
                continue
            original = archive.read(row['source_entry'])
            actual = (ROOT / row['path']).read_bytes()
            assert digest(original) == row['sha256']
            changed = original != actual
            assert changed == (row['path'] in HOOKS), 'Unexpected RP source edit: ' + row['path']
            rows.append({'path': row['path'], 'source_entry': row['source_entry'],
                         'source_sha256': digest(original), 'integrated_sha256': digest(actual),
                         'byte_identical': not changed})
            if changed:
                patches += difflib.unified_diff(original.decode('utf8').splitlines(True),
                                               actual.decode('utf8').splitlines(True),
                                               fromfile=row['source_entry'], tofile=row['path'])
    assert len(rows) == 30
    helper = ROOT / 'src/rp/java/dev/dreamwalker/bloodbornerp/content/SourceLegacyPayload.java'
    resources = imported['reference_jar_resources']
    assert len(resources) == 707
    reference = Path(imported['reference_jar'])
    assert digest(reference.read_bytes()) == imported['reference_jar_sha256']
    with zipfile.ZipFile(reference) as archive:
        for row in resources:
            original = archive.read(row['path'])
            assert digest(original) == row['jar_sha256']
            assert (ROOT / 'src/rp/resources' / row['path']).read_bytes() == original
    patch_path = ROOT / 'reports/RP_SOURCE_INTEGRATION.patch'
    patch_path.write_text(''.join(patches), encoding='utf8', newline='')
    report = {
        'schema': 'dreamwalker-rp-source-integration-v1', 'status': 'PASS_TWO_DECLARED_NBT_HOOKS_ONLY',
        'source_zip': str(source), 'source_sha256': imported['source_sha256'],
        'original_java_files': 30, 'original_java_byte_identical': 28, 'declared_changed_files': sorted(HOOKS),
        'java_files': rows, 'added_helper': {'path': str(helper), 'sha256': digest(helper.read_bytes())},
        'resource_files_byte_identical': 707, 'diff': str(patch_path), 'diff_sha256': digest(patch_path.read_bytes()),
        'reason': 'V3 independent game-save audit found unhandled original Forge fields dropped by the RP serializers.',
        'behavior': 'Preserve full typed original NBT under SourceLegacyPayload; passthrough unhandled fields only; current role/vanilla writers win.',
        'game_save_retention': 'PENDING_AUTHOR_' + revision + '_INDEPENDENT_POSTSAVE',
        'selected_evidence_revision': revision,
    }
    post_save = ROOT / ('reports/SOURCE_REVIEW_POSTSAVE_INDEPENDENT_' + revision + '.json')
    author = ROOT / ('reports/SERVER_SOURCE_REVIEW_AUTHOR_' + revision + '.json')
    if post_save.is_file() and author.is_file():
        saved = json.loads(post_save.read_text(encoding='utf8'))
        launched = json.loads(author.read_text(encoding='utf8'))
        artifact = Path(launched['artifact'])
        if (saved.get('status', '').startswith('PASS') and not saved.get('failures')
                and saved['layer_verdicts']['source_rp_live_typed_original_and_unhandled_fields'] == 'PASS'
                and launched.get('status') == 'PASS' and launched.get('exit_code') == 0
                and artifact.is_file() and digest(artifact.read_bytes()) == launched['artifact_sha256']):
            report.update(game_save_retention='PASS_AUTHOR_' + revision + '_INDEPENDENT_POSTSAVE',
                          game_save_evidence=str(post_save), game_save_evidence_sha256=digest(post_save.read_bytes()),
                          author_evidence=str(author), author_evidence_sha256=digest(author.read_bytes()),
                          production_sha256=launched['artifact_sha256'])
    (ROOT / 'reports/RP_SOURCE_INTEGRATION.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({key: report[key] for key in ('status', 'original_java_files', 'original_java_byte_identical', 'resource_files_byte_identical')}))

if __name__ == '__main__':
    main()
