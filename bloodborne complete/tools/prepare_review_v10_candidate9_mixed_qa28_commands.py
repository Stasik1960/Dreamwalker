"""Preserve actual FULL5ATT3/QA27 and serverQA25; prepare fresh MIN18/REENTER5ATT2 QA28."""
import argparse
import copy
import hashlib
import json
from pathlib import Path


def read(p):
    return json.loads(p.read_text(encoding='utf-8'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--previous-plan', type=Path, default=Path('tools/review_v10_candidate9_runtime_bound27.json'))
    p.add_argument('--qa-manifest', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--diagnostics-output', type=Path)
    a = p.parse_args()
    targets = [a.output] + ([a.diagnostics_output] if a.diagnostics_output else [])
    require(len({x.resolve() for x in targets}) == len(targets) and all(not x.exists() for x in targets),
            'Never overwrite earlier metadata or diagnostic plan')
    old, qa = read(a.previous_plan), read(a.qa_manifest)
    artifact = Path(qa['artifact'])
    require(qa['productionJarSha256'] == old['productionJarSha256'] and 'v10-qa-28' in str(artifact) and
            sha(artifact) == qa['sha256'], 'Actual QA28 must be bound to exact unchanged main9')
    addon = str(artifact.resolve().relative_to(Path.cwd().resolve())).replace('\\', '/')
    full_path = Path('reports/V10_CLIENT_FULL_RELEASE5_ATTEMPT_3.json')
    full = read(full_path)
    require(full['status'] == 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' and full['exit_code'] == 0 and
            full['artifact_sha256'] == old['productionJarSha256'] and
            any(x['sha256'] == old['clientQaJarSha256'] for x in full['extra_mods']),
            'Preserved FULL5ATT3 must be actual current main9/QA27 completed proof')
    data = copy.deepcopy(old)
    substitutions = {
        'v10-client-min-attempt17': 'v10-client-min-attempt18',
        'V10_CLIENT_MIN_ATTEMPT_17': 'V10_CLIENT_MIN_ATTEMPT_18',
        'v10-client-reenter-release5-attempt1': 'v10-client-reenter-release5-attempt2',
        'V10_CLIENT_REENTER_RELEASE5_ATTEMPT_1': 'V10_CLIENT_REENTER_RELEASE5_ATTEMPT_2',
        'V10_CLIENT_MIN17_REENTER5_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT':
            'V10_CLIENT_MIN18_REENTER5_ATT2_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT',
    }
    def replace(value):
        for old_value, new_value in substitutions.items():
            value = value.replace(old_value, new_value)
        return value
    server = {k:v for k,v in old['commands'].items() if any(script in v for script in
              ['tools/run_review_v10_candidate_servers.py', 'tools/run_review_v10_alias_servers.py'])}
    commands = {}
    completed_full_argv = None
    for key, original in old['commands'].items():
        argv = list(original)
        if 'tools/run_final_client.py' in argv:
            profile = argv[argv.index('--profile')+1]
            if profile == 'full_client':
                # Keep current actual full proof, but do not offer a re-launch that could overwrite it.
                completed_full_argv = argv
                continue
            argv[argv.index('--extra-mod')+1] = addon
        argv = [replace(x) for x in argv]
        key = key.replace('min17', 'min18')
        if key == 'actual_reenter5_only_after_min18_normal_pass':
            key = 'actual_reenter5_att2_only_after_min18_normal_pass'
        elif key == 'independent_min18_reenter5_after_both_stop':
            key = 'independent_min18_reenter5_att2_after_both_stop'
        commands[key] = argv
    require(completed_full_argv is not None and
            all(commands[k] == value for k,value in server.items()), 'Server QA25 argv changed')
    for argv in commands.values():
        if 'tools/run_final_client.py' in argv:
            require(argv[argv.index('--extra-mod')+1] == addon and argv[argv.index('--profile')+1] == 'minimal',
                    'Only fresh minimal/reentry pair may launch with QA28')
        if 'tools/verify_v10_actual_client.py' in argv:
            require('--require-middle-pick' in argv, 'Actual client validator must retain strict middle-key requirement')
    # Preserve the real FULL5ATT3 wrapper in all-three middle validation; only the failed minimal/reentry references change.
    middle = commands['independent_actual_middle_keys_after_all_three_clients_stop']
    require(middle[middle.index('--full-wrapper')+1] == str(full_path).replace('\\','/'), 'Do not promote a future full rerun')
    data.update({'schema': 'dw-v10-candidate9-server25-full27-minimal28-runtime-commands-v1',
        'status': 'PREPARED_CURRENT_MAIN9_SERVER25_ACTUAL_FULL27_FRESH_MIN18_REENTER5ATT2_QA28_NOT_RUN',
        'executionPerformed': False, 'clientFreezeVerified': True,
        'commands': commands, 'clientQaJar': addon, 'clientQaJarSha256': qa['sha256'],
        'minReenterClientQaJar': addon, 'minReenterClientQaJarSha256': qa['sha256'],
        'fullClientQaJar': old['clientQaJar'], 'fullClientQaJarSha256': old['clientQaJarSha256'],
        'fullClientQaFreezeManifest': old['clientQaFreezeManifest'],
        'clientQaFreezeManifest': {'path': str(a.qa_manifest.resolve()), 'sha256': sha(a.qa_manifest)},
        'previousMixedPreparation': {'path': str(a.previous_plan.resolve()), 'sha256': sha(a.previous_plan), 'preservedExact': True},
        'qaArtifactRoleBindings': {'serverAuthor8Reopen6FullOwned5Alias3Diagnostics5': old['serverQaJar'],
            'actualWholeFull5Att3AndStrictShaderAlreadyPassed': old['clientQaJar'],
            'onlyFutureMin18AndDistinctReenter5Att2': addon},
        'completedFullCurrentEvidence': {'wrapper': str(full_path), 'wrapperSha256': sha(full_path),
            'qaJarSha256': old['clientQaJarSha256'], 'actualStatus': full['status'],
            'originalLaunchArgvReadOnlyHistory': completed_full_argv, 'needsRerun': False,
            'ordinaryIndependent': 'reports/V10_CLIENT_FULL5_ATT3_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT.json',
            'strictShaderIndependent': 'reports/V10_CLIENT_FULL5_ATT3_STRICT_SHADER_INDEPENDENT.json'},
        'historicalMin17Failure': {'wrapper': 'reports/V10_CLIENT_MIN_ATTEMPT_17.json', 'wholeStatus': 'FAIL',
            'phase': 4, 'qualifiedSubset': 'reports/V10_CLIENT_MIN17_COMPLETED_SUBSETS_QUALIFIED_AUDIT.json',
            'screen': 'ChatScreen', 'openingCause': 'UNKNOWN', 'substitutionForRetry': False},
        'clientOnlyChanges': {'scope': 'QA28 bootstrap after completed diagnostic windows sends one ordinary Esc for ChatScreen only and awaits native null within20ticks. No new object/world/readiness criteria.',
            'productionChanged': False, 'serverCommandsChanged': False, 'fullCompletedQa27ProofInvalidated': False,
            'newMinimalPairRuntimeProof': 'NOT_RUN'},
    })
    data['sequence'][1] = 'FULL5ATT3/QA27 is actual completed current main9 proof and is preserved. Root separate GO: MIN18/QA28 from pristine Author8 -> REENTER5ATT2/QA28 from actual MIN18 save. Do not relaunch old full3.'
    diagnostics = {'schema': 'dw-v10-dedicated-diagnostics-mixed-client-plan-v1',
        'status': 'PREPARED_MAIN9_SERVER25_DIAGNOSTICS5_MIN18_REENTER5ATT2_QA28_NOT_RUN',
        'productionJarSha256': old['productionJarSha256'], 'serverQaJar': old['serverQaJar'],
        'serverQaJarSha256': old['serverQaJarSha256'], 'executionPerformed': False,
        'serverPhaseArgvOnlyAfterSeparateRootGo': commands['actual_dedicated_off_on_reenter5_separate_root_go'],
        'independentArgvOnlyAfterThreeServersAndTwoClientsNormalPass': commands['independent_linked_diagnostics5_after_actual_server3_client2_stop'],
        'plannedCurrentClientWrappers': ['reports/V10_CLIENT_MIN_ATTEMPT_18.json', 'reports/V10_CLIENT_REENTER_RELEASE5_ATTEMPT_2.json'],
        'plannedCurrentClientQaJarSha256': qa['sha256'],
        'fullCurrentPassedWrapper': str(full_path), 'fullCurrentQaJarSha256': old['clientQaJarSha256'],
        'matchedBaseline': 'build/runtime-server-v10-author-min-attempt8/isolated-smoke-world',
        'markerDirectory': 'tools/review-v10-diagnostics-markers-release5',
        'requiresSeparateRootGoAfterAllClientsAndBuildsStop': True}
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    if a.diagnostics_output:
        a.diagnostics_output.parent.mkdir(parents=True, exist_ok=True)
        a.diagnostics_output.write_text(json.dumps(diagnostics, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': data['status'], 'output': str(a.output), 'serverArgvUnchanged': True,
                      'actualFullPassedProofPreserved': True, 'futureMinimalClientLaunches': 2}))


if __name__ == '__main__':
    main()
