"""Freeze diagnostic evidence without changing the mod or either gallery."""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/v12-reported-regression'
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
read = lambda path: json.loads(path.read_text(encoding='utf8'))
static = read(OUT / 'static-comparison.json')
copies = {'build-report.json': ROOT / 'build/v12-wall-audit-1/build-report.json'}
native = {}
servers = {}
for label in ('original', 'delivered'):
    run = ROOT / f'build/runtime-server-v12-wall-regression-{label}-1'
    copies[f'{label}-native-wall.json'] = run / 'wall-regression-output.json'
    copies[f'{label}-server.json'] = ROOT / f'build/v12-wall-audit-1/server-{label}.json'
    copies[f'{label}-console.log'] = run / 'launch-console.log'
    native[label] = read(copies[f'{label}-native-wall.json'])
    servers[label] = read(copies[f'{label}-server.json'])
    assert native[label]['status'] == servers[label]['status'] == 'PASS'
    assert len(native[label]['cases']) == 8
    assert all(row['status'] == 'PASS' for row in native[label]['cases'])
    assert sha(copies[f'{label}-console.log']) == servers[label]['console_sha256']
assert static['status'] == 'PASS'
assert native['original']['cases'] == native['delivered']['cases']
assert native['original']['oldCreativeBlockDefaults'] == native['delivered']['oldCreativeBlockDefaults']
assert servers['original']['artifact_sha256'] == static['publishedV12Sha256']
assert servers['delivered']['artifact_sha256'] == static['deliveredV122Sha256']
release = ROOT.parent / 'releases/Dreamwalker-BB/v12.2-simple-collisions'
expected = {
    'dreamwalker-bb-fabric-1.20.1-0.1.0-v12.2-simple-collisions.jar': 'ab2c7806fe0bfce94cdd081924fe9a643b087caa9b8b1ca7b17fcb35f3874c32',
    'Dreamwalker-BB-V12.2-Flat-Gallery.zip': '1110cae7cb4796121c7c9d3eec2f635b1b44936b05bb0faea963bf6599577cda',
    'dreamwalker-bb-fabric-1.20.1-0.1.0-v12.2-simple-collisions-full-source.zip': '56a627b4abc4e78b5bf203ce313b64b2cab5d77518db65d1946dbfc68d42265d',
}
for name, digest in expected.items():
    assert sha(release / name) == digest, name
manifest = ROOT / 'docs/gallery-v12.2/GALLERY-MANIFEST.json'
assert sha(manifest) == '9c85912f02889bc48c4a3db18e326d5b7c5fe3126555671e63df03557196a866'
gallery_walls = [row for row in static['galleryNativeV12Poses'] if row['id'] in ('90002', '90011')]
assert len(gallery_walls) == 2
assert all(row['placedState']['connections'] == 'manual' for row in gallery_walls)
for name, source in copies.items():
    shutil.copyfile(source, OUT / name)
result = {
    'schema': 'dreamwalker-v12-wall-regression-diagnosis-v1',
    'baselineCommit': '4a271348f2ac42b1b3da20db32056c9b60c4875c',
    'originalV12CodeCommit': '821de077917f696a13600e900a244ffc05230c2f',
    'confirmedRegression': 'Gallery generator changes 90002 and 90011 to connections=manual. Native pick copies that state; subsequent placement retains manual behavior.',
    'checks': {
        'originalProductionWallBehavior': 'PASS',
        'deliveredProductionWallBehavior': 'PASS',
        'nativeWallResultsExactlyEqual': 'PASS',
        'oldElevenCreativeBlockDefaultsAndNbtExactlyEqual': 'PASS',
        'oldCatalogueResourcesAndWallRpClasses': 'PASS',
        'gallery90002And90011AutomaticNeighborBehavior': 'FAIL',
        'duplicate90011IntroducedAfterV12': 'DISPROVED',
        'exhaustiveManualGameplayAllOldObjects': 'NOT_RUN',
        'newClientVisualTestThisDiagnostic': 'NOT_RUN',
    },
    'casesPerProductionJar': 8,
    'originalJarSha256': static['publishedV12Sha256'],
    'deliveredJarSha256': static['deliveredV122Sha256'],
    'diagnosticAddonSha256': sha(ROOT / 'build/libs/dreamwalker-wall-regression-audit-1.0.0.jar'),
    'nativeDefaultsCompared': len(native['original']['oldCreativeBlockDefaults']),
    'galleryWalls': gallery_walls,
    'productionReleaseFilesUnchanged': expected,
    'galleryManifestUnchangedSha256': sha(manifest),
    'evidenceSha256': {name: sha(OUT / name) for name in copies},
    'productionFixApplied': False,
    'sourceScope': 'Only diagnostic tools and this new evidence directory are added; production sources, release JARs and galleries are unchanged during diagnosis.',
}
(OUT / 'runtime-comparison.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
print(json.dumps({'evidence': str(OUT), 'checks': result['checks'], 'casesPerJar': 8, 'nativeDefaultsCompared': result['nativeDefaultsCompared']}, ensure_ascii=False))
