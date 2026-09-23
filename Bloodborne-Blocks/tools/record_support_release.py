"""Verify the support-normalization artifacts after successful QA, then deliver.

This checks artifacts, not whether Gradle was executed successfully. Check the
command result first. Never overwrites a previously delivered runtime JAR.
"""
from __future__ import annotations

import json
import shutil
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree

from check_packaged_resources import validate
from normalize_support_contracts import digest as contract_digest
from record_nightmare_release import digest, read, require

ROOT = Path(__file__).resolve().parents[1]
DELIVERY = ROOT.parent / 'releases/Bloodborne-Blocks/Support-Normalization'
DOCUMENTED = ROOT / 'docs/support-checks'
REPORTS = ROOT / 'build/test-results'


def record():
    contracts = read(ROOT/'src/main/resources/bloodborne_blocks/logical/contracts-v2.json')
    audit = read(ROOT/'docs/support-normalization-audit.json')
    require(audit['output_contract_sha256'] == contract_digest(contracts), 'stale normalization audit')
    require(audit['summary']['fail_families'] == 0 and audit['summary']['families_with_helpers_below'] == 0,
            'normalization has unresolved failures')
    tests = ElementTree.parse(REPORTS/'gametest/TEST-logical-gametest.xml')
    cases = tests.findall('.//testcase')
    require(len(cases) == 28 and not tests.findall('.//failure') and not tests.findall('.//error'),
            '28 passing GameTests required')
    require(sum(c.get('name','').startswith('supportplanegametests.') for c in cases) == 3,
            'missing platform/placement/persistent-helper coverage')
    orientation = read(REPORTS/'contract-orientation.json')
    require(orientation['result'] == 'PASS' and orientation['rotation_checks'] == 4 * audit['summary']['states'],
            'orientation report mismatch')
    source = read(REPORTS/'reviewed-batch-02-source-examples.json')
    statuses = Counter(c['status'] for c in source['cases'])
    require(statuses == Counter(passed=20, blocked=8), 'unexpected source migration result')
    trees = read(REPORTS/'qa2-trees.json')
    require(trees['cases'] and all(c['status'] == 'passed' for c in trees['cases']), 'source tree failure')
    gallery = read(DOCUMENTED/'gallery-package.json')
    gallery_zip = DELIVERY/'support-plane-gallery-20260924.zip'
    require(gallery['result'] == 'PASS' and gallery['zip']['sha256'] == digest(gallery_zip), 'stale gallery evidence')
    require(gallery['counts']['scope'] == 'contract_v2'
            and gallery['counts']['families'] == audit['summary']['families']
            and gallery['counts']['master_states'] == audit['summary']['states'] + 6, 'incomplete contract gallery')
    summary = REPORTS/'support-level-summary.txt'
    require('support-plane-gallery-20260924:' in summary.read_text(encoding='utf8')
            and 'vanilla-summary=true' in summary.read_text(encoding='utf8'), 'missing official level summary')
    jar = ROOT/'build/libs/bloodborne-blocks-2.1.0-alpha.1.jar'
    packaged = validate(jar, ROOT/'src/main/resources', ROOT/'build/classes/java/main',
                        generated_refmap='bloodborne-blocks-refmap.json')
    output = DELIVERY/'bloodborne-blocks-2.1.0-alpha.1-support-normalization-mc1.20.1.jar'
    require(not output.exists(), 'never overwrite a delivered JAR; choose a new checkpoint destination')
    DELIVERY.mkdir(parents=True, exist_ok=True)
    DOCUMENTED.mkdir(parents=True, exist_ok=True)
    shutil.copy2(jar, output)
    for path in (REPORTS/'contract-orientation.json', REPORTS/'qa2-trees.json',
                 REPORTS/'reviewed-batch-02-source-examples.json', summary,
                 REPORTS/'gametest/TEST-logical-gametest.xml'):
        shutil.copy2(path, DOCUMENTED/path.name)
    result = {
        'result':'PASS', 'scope':'artifact verification, not client visual acceptance or city conversion',
        'normalization':audit['summary'], 'game_tests':len(cases),
        'orientation_checks':orientation['rotation_checks'],
        'source_examples':dict(statuses), 'tree_checks':len(trees['cases']),
        'packaged_resources':packaged, 'gallery':gallery['counts'],
        'client_bake_reload':'NOT_PERFORMED', 'client_visual_acceptance':'NOT_PERFORMED',
        'full_city_conversion':False,
        'files':{p.name:{'bytes':p.stat().st_size, 'sha256':digest(p),
                         'git_lfs_required':p.stat().st_size >= 100*1024*1024} for p in (output,gallery_zip)}}
    (DOCUMENTED/'release-verification.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    (DELIVERY/'SHA256.json').write_text(json.dumps(result['files'],indent=2)+'\n',encoding='utf8')
    print(json.dumps(result),flush=True)


if __name__ == '__main__':
    record()
