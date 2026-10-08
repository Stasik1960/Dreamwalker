"""Bind fresh Candidate12 review commands only after actual build and FINAL4 preload.

No Minecraft, Gradle, world decoding, runtime validation, or archive creation occurs
here. The previous command document is preserved byte-exact as historical input.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OLD_SHA = '8dd5d1976f7348a9c08899acd723ba62358cc9611d4694bec3fc2b4914e3c1c0'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def relative(path):
    return path.resolve().relative_to(ROOT.resolve()).as_posix()


def write_new(path, value):
    raw = (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        assert path.read_bytes() == raw, 'Refusing to overwrite differing prepared input: ' + str(path)
    else:
        path.write_bytes(raw)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--expected-sha', required=True)
    p.add_argument('--qa-jar', type=Path, required=True)
    p.add_argument('--native-xml', type=Path, required=True)
    p.add_argument('--build-log', type=Path, required=True)
    p.add_argument('--source-plan', type=Path, default=ROOT/'reports/FIRST_SET_MIGRATION_PLAN_V9_FINAL4.json')
    p.add_argument('--source-input', type=Path, default=ROOT/'build/source-review-prepared-v9-final4/source-review-input.json')
    p.add_argument('--preload-report', type=Path, default=ROOT/'reports/SOURCE_REVIEW_PRELOAD_INDEPENDENT_V9_FINAL4.json')
    p.add_argument('--accepted-eula-file', type=Path, required=True)
    p.add_argument('--from-commands', type=Path, default=ROOT/'tools/package_review_v9_commands.json')
    p.add_argument('--output', type=Path, default=ROOT/'tools/package_review_v9_commands.json')
    p.add_argument('--replace-current', action='store_true')
    a = p.parse_args()
    assert re.fullmatch('[0-9a-f]{64}', a.expected_sha) and a.expected_sha != OLD_SHA
    assert sha(a.jar) == a.expected_sha, 'Actual frozen Candidate12 JAR does not match declared SHA'
    old_raw = a.from_commands.read_bytes()
    old = json.loads(old_raw.decode('utf-8'))
    assert old['production_sha256'] == OLD_SHA, 'Binder only accepts the immutable Candidate11 predecessor'
    qa_sha = sha(a.qa_jar)
    assert qa_sha == old['qa_sha256'], 'QA changed; review the expected byte-identical QA13 contract before binding'
    cases = list(ET.parse(a.native_xml).getroot().iter('testcase'))
    assert cases and all(not x.findall('failure') and not x.findall('error') for x in cases)
    log = a.build_log.read_text(encoding='utf-8', errors='strict')
    assert f'All {len(cases)} required tests passed' in log and 'BUILD SUCCESSFUL' in log
    core = re.search(r'PASS (\d+) transaction core checks', log)
    assert core and int(core.group(1)) == 20
    assert '_12.xml' in a.native_xml.name and 'ATTEMPT_12.log' in a.build_log.name
    plan, source, preload = read(a.source_plan), read(a.source_input), read(a.preload_report)
    previous_source = read(ROOT/'build/source-review-prepared-v9-final3/source-review-input.json')
    assert plan['production_descriptor_artifact']['sha256'] == a.expected_sha
    assert source['sourcePlanSha256'] == sha(a.source_plan)
    assert preload['status'] == 'PASS_PRELOAD_PRESERVATION' and preload['input_sha256'] == sha(a.source_input)
    for key in ('objects', 'nonmemberPreconditions', 'sourcePhysicsMovements', 'descriptorSha256',
                'technicalLightCells', 'temporaryTypeIds', 'sourcePhysicsReferenceSha256'):
        assert source[key] == previous_source[key], 'Unexpected source contract change: ' + key
    assert (len(source['objects']), source['sourceMemberCount'], len(source['nonmemberPreconditions']),
            len(source['sourcePhysicsMovements']), len(source['descriptorSha256'])) == (41, 110, 158, 7, 6)
    assert preload['counts']['manifest_preconditions_checked'] == 275
    assert re.search(r'^\s*eula\s*=\s*true\s*$', a.accepted_eula_file.read_text(encoding='utf-8'), re.MULTILINE)
    if a.output.exists():
        assert a.replace_current and a.output.resolve() == a.from_commands.resolve(), 'Explicit replacement of current commands required'
    relative(a.output)
    new_jar, new_qa = relative(a.jar), relative(a.qa_jar)
    input_sha = sha(a.source_input)
    old_input_sha = old['current_input_sha256']['sourcePreparedFinal3']

    def remap(value):
        if isinstance(value, dict):
            return {k: remap(v) for k, v in value.items()}
        if isinstance(value, list):
            return [remap(v) for v in value]
        if not isinstance(value, str):
            return value
        if value == old['frozen_artifacts']['production']['path']:
            return new_jar
        if value == old['frozen_artifacts']['qa']['path']:
            return new_qa
        if value == OLD_SHA:
            return a.expected_sha
        if value == old_input_sha:
            return input_sha
        if value == '<EXISTING_ACCEPTED_EULA_FILE>':
            return str(a.accepted_eula_file.resolve())
        value = value.replace('CLIENT_FULL_KAPPA_V9_RELEASE9', 'CLIENT_FULL_KAPPA_V9_RELEASE11')
        value = value.replace('CLIENT_MINIMAL_V9_RELEASE9', 'CLIENT_MINIMAL_V9_RELEASE10')
        value = value.replace('CLIENT_MINIMAL_REENTER_V9_RELEASE9', 'CLIENT_MINIMAL_REENTER_V9_RELEASE10')
        value = value.replace('minimal-release9/client-review-output', 'minimal-release10/client-review-output')
        value = value.replace('ATTEMPT_11', 'ATTEMPT_12').replace('CANDIDATE11', 'CANDIDATE12')
        value = value.replace('RELEASE8', 'RELEASE9').replace('release8', 'release9')
        value = value.replace('FINAL3', 'FINAL4').replace('final3', 'final4')
        return value

    d = copy.deepcopy(old)
    for field in ('required_runtime_reports', 'source_runtime_reports', 'diagnostics_runtime_reports'):
        d[field] = remap(old[field])
    d['commands'] = {}
    for key, argv in old['commands'].items():
        if 'history_not_rerun' in key:
            d['commands'][key] = argv
            continue
        argv = remap(argv)
        for option in ('--full-client-report', '--shader-report'):
            while option in argv:
                idx = argv.index(option)
                del argv[idx:idx+2]
        if 'tools/package_review_v9.py' in argv:
            idx = argv.index('--output')
            argv[idx+1] = 'build/delivery/v9-release9'
            exclusion = 'reports/REVIEW_V9_CANDIDATE11_TELEMETRY_EXCLUSIONS.json'
            if exclusion not in argv:
                argv.extend(['--extra-evidence', exclusion])
        d['commands'][key] = argv
    history = ROOT/'reports/v9-history'/f'package_review_v9_commands-candidate11-{hashlib.sha256(old_raw).hexdigest()}.json'
    history.parent.mkdir(parents=True, exist_ok=True)
    if history.exists():
        assert history.read_bytes() == old_raw
    else:
        history.write_bytes(old_raw)
    d['historical_candidate11'] = {'productionSha256': OLD_SHA, 'commandSnapshot': relative(history),
                                  'commandSnapshotSha256': hashlib.sha256(old_raw).hexdigest(),
                                  'exclusionReport': 'reports/REVIEW_V9_CANDIDATE11_TELEMETRY_EXCLUSIONS.json',
                                  'functionalSuccessesRemainHistorical': old['actual_completed_evidence'],
                                  'telemetryPerformanceReleaseProof': False}
    d['production_sha256'], d['qa_sha256'] = a.expected_sha, qa_sha
    d['frozen_artifacts'] = {'production': {'path': new_jar, 'sha256': a.expected_sha, 'bytes': a.jar.stat().st_size},
                             'qa': {'path': new_qa, 'sha256': qa_sha, 'bytes': a.qa_jar.stat().st_size,
                                    'status': 'BYTE_IDENTICAL_QA13; NEW_PRODUCTION_SEPARATION_GATE_PENDING'},
                             'nativeXml': relative(a.native_xml), 'buildLog': relative(a.build_log)}
    d['actual_completed_evidence'] = {
        'nativeAndCore': {'status': f'PASS_NATIVE{len(cases)}_CORE20_BUILD', 'nativeXml': relative(a.native_xml), 'buildLog': relative(a.build_log)},
        'sourceFinal4Preload': {'status': 'PASS_PRELOAD_PRESERVATION_RUNTIME_NOT_RUN', 'report': relative(a.preload_report)}}
    d['expected_counts_seen_read_only']['registeredNativeMethods'] = len(cases)
    for key in list(d['current_input_sha256']):
        if key.startswith(('sourcePreparedFinal3', 'sourcePlanFinal3', 'sourcePreloadIndependentFinal3', 'clientMarkerRelease8')):
            del d['current_input_sha256'][key]
    d['current_input_sha256'].update(sourcePreparedFinal4=input_sha, sourcePlanFinal4=sha(a.source_plan), sourcePreloadIndependentFinal4=sha(a.preload_report))
    marker = read(ROOT/'build/review-v9-client-input-release8.json')
    marker['productionJarSha256'] = a.expected_sha
    marker_path = ROOT/'build/review-v9-client-input-release9.json'
    write_new(marker_path, marker)
    shader = copy.deepcopy(marker)
    shader['diagnosticsReview']['seconds'] = 60
    old_shader = read(ROOT/'build/review-v9-client-shader-input-release10.json')
    assert len(old_shader['expectedShaderOptions']) == 42
    shader['expectedShaderPack'], shader['expectedShaderOptions'] = old_shader['expectedShaderPack'], old_shader['expectedShaderOptions']
    shader_path = ROOT/'build/review-v9-client-shader-input-release11.json'
    write_new(shader_path, shader)
    d['client_marker'] = {'path': relative(marker_path), 'sha256': sha(marker_path), 'status': 'PREPARED_RUNTIME_PENDING', 'diagnosticsReview': marker['diagnosticsReview']}
    d['strict_shader_marker'] = {'path': relative(shader_path), 'sha256': sha(shader_path), 'options': 42, 'status': 'PREPARED_RUNTIME_PENDING'}
    d['current_input_sha256']['clientMarkerRelease9'] = sha(marker_path)
    d['current_input_sha256']['strictShaderMarkerRelease11'] = sha(shader_path)
    d['optional_current_argument_groups']['full_client'] = ['--full-client-report', 'reports/CLIENT_FULL_KAPPA_V9_RELEASE11.json']
    d['optional_current_argument_groups']['shader'] = {'status': 'NOT_SELECTED_PENDING_CURRENT_ACTUAL_STRICT_PROOF', 'argv': [],
                                                     'candidate_template': ['--shader-report', 'reports/CLIENT_FULL_KAPPA_V9_RELEASE11.json']}
    d['selected_shader_optional'] = False
    d['full_client_retry'] = {'report': 'reports/CLIENT_FULL_KAPPA_V9_RELEASE11.json', 'profile': 'full_client', 'heap_gb': 6,
                              'status': 'PENDING_ACTUAL_CANDIDATE12_RUNTIME', 'limits': 'No4GB compatibility claim; all prior failures and successful superseded runs retained.'}
    d['pending_evidence'] = {'static': 'PENDING_CURRENT_ART_RP_QA_SEPARATION_AUDITS', 'ordinaryRelease9': 'PENDING_SIX_ACTUAL_PHASES_AND_FOUR_INDEPENDENT_VALIDATORS_ARCHIVE',
                            'sourceRelease9': 'PENDING_FINAL4_THREE_PHASES_FOUR_VALIDATORS_ARCHIVE', 'diagnosticsRelease9': 'PENDING_CLEAN_SERVER_OFF_ON_REENTER_AND_CLIENT_RECORDING_WITH_RETAINED_TARGET_TIMINGS',
                            'clients': 'PENDING_MINIMAL10_RECORDING_REENTER10_FULL_KAPPA_STRICT11_HEAP6GB', 'package': 'PENDING_CURRENT_CHECKPOINT_PRIMARY_COLLECTION_SOURCE_DOCS_FREEZE'}
    d['source_open_contract']['status'] = 'CURRENT_CANDIDATE12_RUNTIME_PENDING; PREVIOUS_EXACT_POLICY_HISTORICAL'
    d['diagnostics_runtime_reports']['status'] = 'PLANNED_CANDIDATE12_SERVER9_CLIENT10_STRICT_FULL11_NOT_RUN'
    d['command_execution_notes']['completed_do_not_overwrite'] = []
    d['command_execution_notes']['all_other_commands'] = 'ROOT_PLANNED_FRESH_CANDIDATE12_RUNTIME_OR_POST_PROOF_GATES; NOT_EXECUTED_BY_THIS_BINDER'
    d['status'] = 'PREPARED_CANDIDATE12_NATIVE_BUILD_AND_FINAL4_PRELOAD_PASS_RUNTIME_CLIENT_PENDING'
    d['instructions'] = ['Candidate12 actual native/build and FINAL4 preload are validated by this binder. Static audits and all current runtime/client/archive gates are pending; no Candidate11 runtime PASS substitutes for the new SHA.',
                         'Root owns Minecraft/Gradle. Use fresh ordinary/source/diagnostics server suffixrelease9, minclient10/reenter10, strict fullKappa11 at6GB. No heavy parallel work during matched frame/performance windows.',
                         'Source FINAL4 retains41objects/110members/275preconditions/7movements/6descriptor hashes. Existing source wood-window OPEN must refuse new brick overlap atomically; source door must actually commit and permit movement.',
                         'Full-client/shader optional groups are selected only after completed exact-current proof. ALT remains optional NOT_RUN. Review/diagnostic full texts remain required in TASK/PROGRESS and checkpoint args.',
                         'Candidate11 full9/10 loaded/saved/exited normally and full10 strict42shader passed; its confirmed timing-data loss and repeated whole-packet serialization exclude telemetry performance release proof. Keep all raw successes/failures.',
                         'Checkpoint/collection/package wait for all current proofs and final source/docs freeze. Manual acceptance remains pending; complete catalogue/city/gallery/final IDs are NOT_READY.']
    d['qa_sha256_status'] = 'BYTE_IDENTICAL_QA13_VERIFIED_BY_BINDER_NEW_SEPARATION_GATE_PENDING'
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(d, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': d['status'], 'output': relative(a.output), 'productionSha256': a.expected_sha,
                      'sourceInputSha256': input_sha, 'history': relative(history)}))


if __name__ == '__main__':
    main()
