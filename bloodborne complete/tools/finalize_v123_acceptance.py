"""Bind the V12.3 release decision to final, preserved native evidence."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs/v12.3-outline-icons'
JAR = ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.3-outline-icons.jar'
read = lambda name: json.loads((DOCS / name).read_text(encoding='utf8'))
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    digest = sha(JAR)
    static = read('preservation-and-resource-audit.json')
    build = read('build/build-evidence-manifest.json')
    native = read('native/server-create-qa.json')
    reopen = read('native/server-reopen-qa.json')
    nbt = read('native/saved-state-nbt-comparison.json')
    client = read('client/native-client-output.json')
    collected = read('client/evidence-index.json')
    visual = read('client/visual-review.json')
    outlines = read('client/outline-visual-review.json')
    assert static['status'] == build['status'] == native['status'] == nbt['status'] == collected['status'] == visual['status'] == outlines['status'] == 'PASS'
    assert static['artifactSha256'] == digest
    assert all(record['productionJarSha256'] == digest for record in (native, reopen, nbt, client, collected, visual, outlines))
    assert build['nativeGameTestCount'] == 63 and build['nativeGameTestFailures'] == build['nativeGameTestErrors'] == 0
    assert native['completedRoots'] == 1045 and native['exactGeometryStates'] == 28848 and native['offsetGeometryStates'] == 1046
    assert len(native['fixtures']) == 21 and all(f['status'] == 'PASS' for f in native['fixtures'])
    assert reopen['status'] == 'PASS_REOPEN' and reopen['savedRoots'] == len(nbt['roots']) == 21
    assert client['status'] == 'CAPTURE_COMPLETE' and client['captureComplete'] and client['capturedFrames'] == 9
    assert visual['nativeClientRun'] == 'runtime-client-v123-outline-icons-3'
    assert visual['canonicalIconsVisuallyReviewed'] == 69
    result = {
        'schema': 'dreamwalker-v123-final-acceptance-v1', 'status': 'PASS',
        'baselineCommit': '772804c157bd28e0122a21f255b64c82d4ec5a43',
        'productionJarSha256': digest, 'version': '0.1.0-v12.3-outline-icons',
        'build': 'PASS', 'gameTests': 'PASS', 'nativeServer': 'PASS', 'nativeClient': 'PASS',
        'gameTestXml': 'build/test-results/gametest/TEST-v123-main1.xml',
        'gameTestCount': 63, 'newRootCount': 1045, 'offeredVariants': 1802,
        'distinctPoses': 1803, 'geometryStateChecks': 28848, 'offsetGeometryChecks': 1046,
        'nativeFixtures': 21, 'nativeReopenedRoots': 21, 'nativePngFrames': 9,
        'canonicalRpIconsNativeVisualPass': 69, 'rpIconModelResources': 76,
        'protectedV12Gameplay': 'PASS_PRESERVATION_AUDIT',
        'previousPhysicalDescriptors': 'PASS_1213_UNCHANGED',
        'authorCollisionExport': 'NOT_SUPPLIED_NOT_APPLIED',
        'physicalMouseKeyboard': 'NOT_RUN', 'performanceBenchmark': 'NOT_RUN',
        'hiddenCompatibilityAliasesDirectRendering': 'NOT_RUN_7_STATIC_MAPPING_PASS',
        'allOldObjectsManualGameplaySweep': 'NOT_RUN', 'mapsChanged': False,
        'passableSelectionPolicy': 'One model-bounded ordinary AABB for picking; physical collision stays empty, per explicit user reply',
        'scopeOfNativeTesting': 'Exhaustive new descriptor states plus 21 installed native interaction fixtures, 69 canonical native item renders and 7 visually reviewed native outline screenshots; not every object individually operated in GUI',
        'historicalQaFailures': 'Preserved under history; corrected fake-player aim, root-only owner lookup, and below-floor screenshot camera. Production JAR stayed byte-identical throughout.',
    }
    (DOCS / 'acceptance.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
