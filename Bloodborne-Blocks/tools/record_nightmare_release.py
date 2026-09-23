"""Verify current release artifacts/reports, then copy the single accumulated JAR.

Run after successful Gradle check/build/logicalGameTest, the real ALT-resolver
check and gallery packaging. This is an artifact verifier, not proof that a
Gradle command was run; the execution result must be checked separately.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import zipfile
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree

from check_packaged_resources import validate

ROOT = Path(__file__).resolve().parents[1]
DELIVERY = ROOT.parent / 'releases/Bloodborne-Blocks/Nightmare-QA-ALT'
REPORTS = ROOT / 'build/test-results'
DOCUMENTED = ROOT / 'docs/nightmare-checks'


def require(value, message):
    if not value:
        raise ValueError(message)


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def record():
    tests = ElementTree.parse(REPORTS / 'gametest/TEST-logical-gametest.xml')
    cases = tests.findall('.//testcase')
    require(len(cases) == 25 and not tests.findall('.//failure') and not tests.findall('.//error'),
            '25 successful GameTests required; do not package a failed or pre-Nightmare report')
    names = {case.get('name', '').lower() for case in cases}
    require(any('actualregioncommanddeduplicateshelpersandpreservesownership' in name for name in names),
            'missing real command/NBT GameTest')
    require(sum(name.startswith('nightmaregametests.') for name in names) == 4, 'missing Nightmare tests')
    orientation = read(REPORTS / 'contract-orientation.json')
    require(orientation['result'] == 'PASS' and orientation['rotation_checks'] == 2960, 'orientation report mismatch')
    source = read(REPORTS / 'reviewed-batch-02-source-examples.json')
    require(Counter(case['status'] for case in source['cases']) == Counter(passed=20, blocked=8),
            'source report differs from 20 converted / 8 safe refusals')
    blocked = {(case['review_id'], case['pattern']) for case in source['cases'] if case['status'] == 'blocked'}
    require(blocked == {('C282', x) for x in 'ABCD'} | {('C654', x) for x in 'ABC'} | {('C1491', 'A')},
            'unexpected blocked placement')
    trees = read(REPORTS / 'qa2-trees.json')
    require(trees['cases'] and all(case['status'] == 'passed' for case in trees['cases']), 'tree source tests failed')
    gallery = read(DOCUMENTED / 'gallery-package.json')
    gallery_zip = DELIVERY / 'nightmare-qa-alt-gallery-20260923-final.zip'
    require(gallery['result'] == 'PASS' and gallery['zip']['sha256'] == digest(gallery_zip), 'stale gallery proof')
    kit = DELIVERY / 'Bloodborne-Alt-Visual-Starter-Kit.zip'
    with zipfile.ZipFile(kit) as archive:
        require(archive.testzip() is None, 'ALT kit CRC error')
        manifest = json.loads(archive.read('manifest.json'))
        require(len(manifest['families']) == 286 and sum(len(f['states']) for f in manifest['families'].values()) == 1610,
                'ALT kit family/state count mismatch')
    jar = ROOT / 'build/libs/bloodborne-blocks-2.1.0-alpha.1.jar'
    packaged = validate(jar, ROOT / 'src/main/resources', ROOT / 'build/classes/java/main',
                        generated_refmap='bloodborne-blocks-refmap.json')
    output_jar = DELIVERY / 'bloodborne-blocks-2.1.0-alpha.1-nightmare-qa-alt-mc1.20.1.jar'
    require(not output_jar.exists(), 'release JAR already exists; never silently replace a delivered build')
    shutil.copy2(jar, output_jar)
    for path in (REPORTS / 'contract-orientation.json', REPORTS / 'qa2-trees.json',
                 REPORTS / 'reviewed-batch-02-source-examples.json', REPORTS / 'nightmare-level-summary.txt',
                 REPORTS / 'gametest/TEST-logical-gametest.xml'):
        shutil.copy2(path, DOCUMENTED / path.name)
    result = {
        'result': 'PASS', 'scope': 'artifact/report verification, not client visual acceptance or full-city migration',
        'game_tests': len(cases), 'orientation_checks': orientation['rotation_checks'],
        'source_examples': {'converted': 20, 'safe_blocked': 8}, 'tree_checks': len(trees['cases']),
        'packaged_resources': packaged, 'gallery': gallery['counts'],
        'alt_kit': {'visible_families': 286, 'models': 1610},
        'client_bake_reload': 'NOT_PERFORMED', 'client_visual_acceptance': 'NOT_PERFORMED',
        'full_city_conversion': False,
        'files': {path.name: {'bytes': path.stat().st_size, 'sha256': digest(path),
                             'git_lfs_required': path.stat().st_size >= 100 * 1024 * 1024}
                  for path in (output_jar, gallery_zip, kit)}}
    (DOCUMENTED / 'release-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    (DELIVERY / 'SHA256.json').write_text(json.dumps(result['files'], indent=2) + '\n', encoding='utf8')
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    argparse.ArgumentParser(description=__doc__).parse_args()
    record()
