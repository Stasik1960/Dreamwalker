"""Verify V12 invariants and exact source assets in the combined transfer JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
DOC = ROOT / 'docs/remaining-transfer'
ALLOWED = {
    'src/architecture/java/dev/dreamwalker/bloodbornedw/DreamwalkerBb.java',
    'src/architecture/java/dev/dreamwalker/bloodbornedw/debug/UnifiedCreativeCatalogue.java',
    'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeItem.java',
    'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeRuntime.java',
    'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeClient.java',
    'src/architecture/java/dev/dreamwalker/bloodbornedw/link/CompositeMechanismBridge.java',
    'src/architecture/resources/bloodborne_dw/debug_catalogue.json',
    'src/architecture/resources/fabric.mod.json',
}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, required=True)
    args = parser.parse_args()
    baseline = json.loads((DOC / 'baseline.json').read_text(encoding='utf-8'))
    checks, failures = {}, []
    changed = []
    for relative, digest in baseline['implementation'].items():
        path = ROOT / relative
        if not path.is_file() or sha(path.read_bytes()) != digest:
            changed.append(relative)
            if relative not in ALLOWED:
                failures.append('Preserved implementation changed: ' + relative)
    checks['oldImplementationUnchangedExceptExplicitIntegration'] = not any(p not in ALLOWED for p in changed)
    checks['oldRpUnchanged'] = not any(p.startswith('src/rp/') for p in changed)
    checks['galleryAuthorSourcesUnchanged'] = not any(p.startswith('src/gallery/') for p in changed)
    checks['sourceMapAndPackUnchanged'] = all(sha((REPO / p).read_bytes()) == digest for p, digest in baseline['referenceInputs'].items())
    if not checks['sourceMapAndPackUnchanged']:
        failures.append('Reference input ZIP changed')
    published_v12 = REPO / 'releases/Dreamwalker-BB/v12/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.jar'
    with zipfile.ZipFile(published_v12) as old:
        old_catalogue = json.loads(old.read('bloodborne_dw/debug_catalogue.json'))
        # Loom injects the production refmap into mixin metadata during remap.
        # The source SHA above must remain frozen; artifact equality uses the
        # exact previously published artifact for these processed files only.
        old_processed_mixins = {name: sha(old.read(name)) for name in old.namelist() if name.endswith('.mixins.json')}
    current = json.loads((ROOT / 'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8'))
    checks['oldTypeNumbersAndAliasesUnchanged'] = current['entries'][:len(old_catalogue['entries'])] == old_catalogue['entries'] and current.get('stateAliases') == old_catalogue.get('stateAliases')
    if not checks['oldTypeNumbersAndAliasesUnchanged']:
        failures.append('Frozen V12 catalogue rows/state aliases changed')
    numbers = [row['temporaryId'] for row in current['entries']]
    checks['uniqueFiveDigitNumbers'] = len(numbers) == len(set(numbers)) and all(len(n) == 5 and n.isdigit() for n in numbers)
    if not checks['uniqueFiveDigitNumbers']:
        failures.append('Invalid/reused type number')
    imported = json.loads((DOC / 'imported-resources.json').read_text(encoding='utf-8'))
    with zipfile.ZipFile(args.jar) as archive:
        names = archive.namelist()
        baseline_vanilla = {p.split('/resources/', 1)[1]: digest for p, digest in baseline['implementation'].items()
                            if '/resources/assets/minecraft/' in p and not p.startswith('src/gallery/')}
        checks['combinedSingleMetadataNoVanillaOverrides'] = names.count('fabric.mod.json') == 1 and all(
            entry.filename in baseline_vanilla and sha(archive.read(entry.filename)) == baseline_vanilla[entry.filename]
            for entry in archive.infolist() if entry.filename.startswith('assets/minecraft/') and not entry.is_dir())
        checks['noQaOrGalleryInProduction'] = not any('/remainingqa/' in n or n.startswith('dev/dreamwalker/bloodbornedw/gallery/') for n in names)
        for row in imported['resources']:
            if row['file'] not in names or sha(archive.read(row['file'])) != row['sha256']:
                failures.append('JAR resource missing/different: ' + row['file'])
        checks['allImportedResourcesInJar'] = not any(f.startswith('JAR resource') for f in failures)
        for relative, digest in baseline['implementation'].items():
            if '/resources/' not in relative or relative in ALLOWED or relative.startswith('src/gallery/') or relative.endswith('fabric.mod.json'):
                continue
            entry = relative.split('/resources/', 1)[1]
            expected = old_processed_mixins.get(entry) if entry.endswith('.mixins.json') else digest
            if expected is None or entry not in names or sha(archive.read(entry)) != expected:
                failures.append('V12 JAR resource changed: ' + entry)
        checks['oldBuildProcessedMixinMetadataMatchesPublishedV12'] = all(
            name in names and sha(archive.read(name)) == digest for name, digest in old_processed_mixins.items())
        checks['oldModelsTexturesStatesAndRpInJarUnchanged'] = not any(f.startswith('V12 JAR resource') for f in failures)
    for name, passed in checks.items():
        if not passed and not any(name in f for f in failures):
            failures.append('Check failed: ' + name)
    report = {'schema': 'dreamwalker-transfer-preservation-v1', 'baselineCommit': baseline['commit'],
              'jar': str(args.jar.resolve()), 'jarSha256': sha(args.jar.read_bytes()),
              'status': 'FAIL' if failures else 'PASS', 'checks': checks,
              'buildProcessedMixinReference': {'artifact': str(published_v12), 'sha256': sha(published_v12.read_bytes()), 'resources': old_processed_mixins},
              'changedIntegrationFiles': changed, 'failures': failures}
    (DOC / 'preservation-results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if failures:
        raise SystemExit('\n'.join(failures))

if __name__ == '__main__':
    main()
