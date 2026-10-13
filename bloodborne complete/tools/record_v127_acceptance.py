"""Record final acceptance only from explicitly supplied, already passed proofs.

Never builds, launches Java, converts a world, packages or publishes. No lookup
of 'latest' reports. Static/dev proofs require exact final-source attestation;
native client/server/converter/resource/pixel proofs require their own JAR SHA.
"""
import argparse
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
from package_v127_release import ROOT, REPO, DOC, SOURCE_HASHES, read, sha, ordinary, evidence_path
from run_v127_full_conversion import IMPLEMENTED_STYLE

NAMES = ('build', 'gametests', 'native-client', 'native-reopen', 'production-server',
         'converter-fixture', 'pixel-review', 'resources', 'preservation', 'alt',
         'collision', 'export', 'fullplan', 'manual-placement', 'worldedit')
STRICT_JAR = {'native-client', 'native-reopen', 'production-server', 'converter-fixture', 'pixel-review', 'resources'}
SCOPE = ['bloodborne complete/src', 'bloodborne complete/tools', 'bloodborne complete/build.gradle',
         'bloodborne complete/gradle.properties', 'bloodborne complete/settings.gradle', 'bloodborne complete/gradle']


def binding(value):
    return value.get('productionJarSha256', value.get('artifact_sha256'))


def passed(name, value):
    status = str(value.get('status', ''))
    assert status.startswith('PASS') and all(word not in status for word in ('PENDING', 'PREPARED', 'NOT_RUN', 'PARTIAL')), (name, status)
    for key in ('failures', 'errors', 'currentUnresolvedTestFailures'):
        assert not value.get(key), (name, key, value.get(key))


def attested(path, digest, checkpoint, attestations):
    key = path.relative_to(ROOT).as_posix()
    row = attestations.get('proofs', {}).get(key)
    assert row and row['sha256'] == sha(path) and row['productionJarSha256'] == digest and row['sourceCheckpoint'] == checkpoint, ('Missing current exact proof binding', key)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--production-jar', type=Path, required=True)
    parser.add_argument('--source-checkpoint', required=True)
    for name in NAMES:
        parser.add_argument('--' + name + '-report', type=Path, required=True)
    parser.add_argument('--gametests-xml', type=Path, required=True)
    parser.add_argument('--proof-bindings', type=Path, required=True)
    parser.add_argument('--counts', type=Path, required=True, help='Reviewed final root counts JSON')
    parser.add_argument('--runtime-plan', type=Path, required=True)
    parser.add_argument('--source-plan', type=Path, required=True)
    parser.add_argument('--source-style-policy', type=Path, default=DOC / 'conversion/source-style-policy.json')
    parser.add_argument('--extra-proof', type=Path, action='append', default=[])
    parser.add_argument('--output', type=Path, default=DOC / 'acceptance.json')
    parser.add_argument('--validate-only', action='store_true')
    args = parser.parse_args()
    production = evidence_path(str(args.production_jar))
    digest = sha(production)
    checkpoint = subprocess.check_output(['git', 'rev-parse', args.source_checkpoint + '^{commit}'], cwd=REPO, text=True).strip()
    assert subprocess.run(['git', 'diff', '--quiet', checkpoint, '--', *SCOPE], cwd=REPO).returncode == 0, 'Source changed since the pre-build checkpoint'
    source_status = subprocess.check_output(['git', 'status', '--porcelain', '-z', '--untracked-files=all', '--', *SCOPE], cwd=REPO)
    assert not source_status, 'Protected implementation has tracked or untracked changes; freeze and commit before acceptance'
    bindings_path = evidence_path(str(args.proof_bindings)); attestations = read(bindings_path)
    assert attestations['status'] == 'PASS_FINAL_FROZEN_PROOF_BINDINGS'
    assert attestations['productionJarSha256'] == digest and attestations['sourceCheckpoint'] == checkpoint
    assert not attestations.get('currentUnresolvedTestFailures')
    reports, evidence = {}, {}
    def add(name, path):
        path = evidence_path(str(path))
        evidence[name] = {'path': path.relative_to(ROOT).as_posix(), 'sha256': sha(path)}
        return path
    for name in NAMES:
        path = add(name, getattr(args, name.replace('-', '_') + '_report'))
        value = read(path); passed(name, value)
        if name in STRICT_JAR:
            assert binding(value) == digest, (name, 'Native/artifact proof is not bound to the current production JAR')
        elif binding(value) is None:
            attested(path, digest, checkpoint, attestations)
        else:
            assert binding(value) == digest, (name, 'Stale artifact proof')
        reports[name] = value
    assert reports['gametests'].get('sourceCommit', reports['gametests'].get('sourceCheckpoint')) == checkpoint, 'Native dev tests must bind the exact final source commit'
    xml_path = add('nativeGameTestsXml', args.gametests_xml)
    tree = ET.parse(xml_path); cases = list(tree.getroot().iter('testcase'))
    assert cases and not list(tree.getroot().iter('failure')) and not list(tree.getroot().iter('error')) and not list(tree.getroot().iter('skipped'))
    assert all(int(suite.get(key, '0')) == 0 for suite in tree.getroot().iter('testsuite') for key in ('failures', 'errors', 'skipped'))
    xml_sha = reports['gametests'].get('nativeXmlSha256', reports['gametests'].get('xmlSha256'))
    if xml_sha is None:
        attested(xml_path, digest, checkpoint, attestations)
    else:
        assert xml_sha == sha(xml_path), 'GameTest XML changed after the native run'
    for name in ('production-server', 'native-reopen'):
        assert not reports[name].get('extraMods'), 'Production-only proof contains a QA/converter add-on'
        if 'world_startup' in reports[name]:
            assert reports[name]['world_startup'] == 'PASS'
        code = reports[name].get('exit_code', reports[name].get('exitCode'))
        if code is not None:
            assert code == 0
    fixture = reports['converter-fixture']
    assert fixture['sourceLevelDataFixerReopen'] == 'PASS' and fixture['sourceLevelDataVersion'] == 2975
    assert fixture['fullOriginalWorldConverted'] is False and fixture['galleryCreated'] is False
    assert fixture.get('savedOverlapPrivilege') is False
    resources = reports['resources']
    assert resources['qaClassesExcluded'] is True and resources['sourceResourcePackRequired'] is False
    assert reports['preservation']['counts']['originalV12CatalogueRows'] == 116
    assert not reports['alt'].get('missingVisualAlternatePoses')
    collision = reports['collision']
    assert collision.get('authorExport', collision.get('authorCollisionExport')) == 'NOT_SUPPLIED_NOT_APPLIED'
    export = reports['export']
    assert export['originalV12TypeExports'] == 0 and export['blockbenchGUIOpen'] == 'NOT_RUN'
    archive = ROOT / 'build/Dreamwalker-BB-V12.7-New-Blockbench-Assets.zip'
    assert export['archiveSha256'] == sha(archive)
    manual = reports['manual-placement']
    if 'cases' in manual:
        assert manual['cases'] and all(row.get('pass') is True for row in manual['cases'])
        assert manual['workingPlacementItems'] == len(manual['cases'])
    assert not manual.get('failures')
    fullplan = reports['fullplan']
    assert fullplan['status'] == 'PASS_COMPLETE_SOURCE_PLAN' and fullplan['knownVisibleGaps'] == 0
    assert fullplan['sourceHashes'] == SOURCE_HASHES and fullplan.get('fullWorldConverted', False) is False
    assert not str(fullplan.get('runtimeExecution', '')).startswith(('BLOCKED', 'PENDING'))
    policy_path = add('sourceStylePolicy', args.source_style_policy); policy = read(policy_path)
    assert policy['status'] == IMPLEMENTED_STYLE
    manifest_path = add('runtimeManifest', args.runtime_plan / 'manifest.json'); manifest = read(manifest_path)
    assert manifest['status'] == 'PASS_COMPLETE_SOURCE_PLAN' and manifest['knownVisibleGaps'] == 0
    assert not manifest.get('fixtureOnly', False) and manifest['sourceHashes'] == SOURCE_HASHES
    assert manifest['sourceStylePolicy'] == IMPLEMENTED_STYLE and manifest['sourceStylePolicySha256'] == sha(policy_path)
    assert not str(manifest.get('runtimeExecution', '')).startswith(('BLOCKED', 'PENDING'))
    source_plan = evidence_path(str(args.source_plan))
    assert manifest['sourcePlanSha256'] == sha(source_plan)
    required_streams = {'sections.jsonl.gz', 'owners.jsonl.gz', 'whole-objects.jsonl.gz', 'entities.jsonl.gz'}
    assert required_streams.issubset(manifest['runtimeFiles'])
    for name, expected in manifest['runtimeFiles'].items():
        assert Path(name).name == name
        path = args.runtime_plan / name
        assert ordinary(path) and sha(path) == expected
    counts_path = add('finalRootCounts', args.counts); counts = read(counts_path)
    assert counts and all(isinstance(value, int) and not isinstance(value, bool) and value >= 0 for value in counts.values())
    assert counts.get('workingNewTypes', export['workingNewTypes']) == export['workingNewTypes']
    catalogue = read(ROOT / 'src/architecture/resources/bloodborne_dw/debug_catalogue.json')['entries']
    ids = [str(row['temporaryId']) for row in catalogue]
    assert len(ids) == len(set(ids)) and all(len(value) == 5 and value.isdigit() for value in ids), 'Current catalogue repeats five-digit IDs'
    add('proofBindings', bindings_path)
    for index, extra in enumerate(args.extra_proof):
        path = add('extraProof' + str(index), extra)
        if path.suffix == '.json':
            value = read(path); passed('extraProof' + str(index), value)
            if binding(value) is None:
                attested(path, digest, checkpoint, attestations)
            else:
                assert binding(value) == digest
    assert sha(production) == digest, 'Production JAR changed during acceptance validation'
    value = {'schema': 'dreamwalker-v127-final-acceptance-v1', 'status': 'PASS',
        'productionJarSha256': digest, 'productionSourceCheckpoint': checkpoint,
        'baselineCommit': '0c985dc28e4a38900cce3055ddb5658a71ed95e5', 'counts': counts,
        'currentUnresolvedTestFailures': [], 'authorCollisionExport': 'NOT_SUPPLIED_NOT_APPLIED',
        'fullOriginalWorldConverted': False, 'galleryCreated': False, 'evidence': evidence,
        'artifactBindings': {'sourceSQLitePlan': {'path': source_plan.relative_to(ROOT).as_posix(), 'sha256': sha(source_plan)}},
        'checks': {'build': 'PASS', 'gameTests': 'PASS_NATIVE_XML_' + str(len(cases)),
            'nativeClient': 'PASS_NATIVE_CAPTURE_AND_PIXEL_REVIEW', 'nativeServerSaveReopen': 'PASS_PRODUCTION_ONLY',
            'resources': 'PASS_FINAL_JAR_CLOSURE', 'manualPlacement': 'PASS_NATIVE_ITEM_HANDLERS',
            'worldEdit': 'PASS_NATIVE_WORLD_EDIT', 'fullConversionPlan': 'PASS_COMPLETE_PLAN_AND_ORIGINAL_SCHEMA_TINY_FIXTURE',
            'preservation': 'PASS', 'alternate': 'PASS', 'collisionContract': 'PASS_NOT_APPLIED',
            'blockbenchArchive': 'PASS_ARCHIVE_STRUCTURE', 'physicalMouseKeyboard': 'NOT_RUN',
            'blockbenchGUI': 'NOT_RUN', 'fullOriginalWorldConversion': 'NOT_RUN_BY_USER_REQUEST'}}
    if args.validate_only:
        print(json.dumps({'status': 'PASS_ACCEPTANCE_PREFLIGHT_NOT_WRITTEN', 'productionJarSha256': digest, 'evidenceFiles': len(evidence)})); return
    assert ordinary(args.output)
    output = args.output.resolve(); assert output.is_relative_to(DOC) and not output.exists(), 'Never replace a prior acceptance silently'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': 'PASS_ACCEPTANCE_RECORDED', 'productionJarSha256': digest, 'output': str(output)}))


if __name__ == '__main__':
    main()
