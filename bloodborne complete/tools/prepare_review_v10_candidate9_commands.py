"""Prepare fresh candidate9 argv only after exact production9/QA25 freezes; no launches."""
import argparse
import hashlib
import json
from pathlib import Path
import re


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def require(value, message):
    if not value:
        raise RuntimeError(message)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--production-manifest', type=Path, required=True)
    ap.add_argument('--qa-manifest', type=Path, required=True)
    ap.add_argument('--previous-plan', type=Path, default=Path('tools/review_v10_candidate8_runtime_bound24_full_retry3.json'))
    ap.add_argument('--output', type=Path, default=Path('tools/review_v10_candidate9_runtime_bound25.json'))
    ap.add_argument('--alias-output', type=Path, default=Path('tools/review_v10_alias_release3_commands.json'))
    ap.add_argument('--diagnostics-output', type=Path, default=Path('tools/review_v10_diagnostics_release5_commands.json'))
    args = ap.parse_args()
    require(len({p.resolve() for p in [args.output, args.alias_output, args.diagnostics_output]}) == 3,
            'Plan outputs must be distinct')
    require(all(not p.exists() for p in [args.output, args.alias_output, args.diagnostics_output]),
            'Refusing to overwrite any earlier plan')
    prod, qa, previous = map(read, [args.production_manifest, args.qa_manifest, args.previous_plan])
    require(prod['status'] == 'BUILD_NATIVE_PASS_REQUIRES_EXACT_ARTIFACT_ORDINARY_RUNTIME' and
            prod['nativeFailures'] == 0 and prod['nativeTests'] > 0 and prod['coreChecks'] >= 20,
            'Actual production9 native/build freeze is required')
    require(qa['productionJarSha256'] == prod['sha256'], 'Fresh QA25 provenance is bound to another production artifact')
    require('v10-attempt-9' in str(prod['artifact']) and 'v10-qa-25' in str(qa['artifact']),
            'Expected distinct production9/QA25 frozen directories')
    require(prod['sha256'] != previous['productionJarSha256'], 'Do not relabel historical main8 proof as candidate9')
    require(sha(Path(prod['artifact'])) == prod['sha256'] and sha(Path(qa['artifact'])) == qa['sha256'],
            'Frozen artifact bytes differ from the actual freeze manifests')
    root = Path.cwd().resolve()
    production = str(Path(prod['artifact']).resolve().relative_to(root)).replace('\\', '/')
    addon = str(Path(qa['artifact']).resolve().relative_to(root)).replace('\\', '/')
    substitutions = {
        previous['productionJar']: production,
        previous['productionJarSha256']: prod['sha256'],
        previous['serverQaJar']: addon,
        previous['minReenterClientQaJar']: addon,
        previous['fullClientQaJar']: addon,
        'v10-author-min-attempt7': 'v10-author-min-attempt8',
        'V10_SERVER_AUTHOR_MIN_ATTEMPT_7': 'V10_SERVER_AUTHOR_MIN_ATTEMPT_8',
        'V10_SCENE_BEFORE_FIRST_ENTRY_SETTINGS_7': 'V10_SCENE_BEFORE_FIRST_ENTRY_SETTINGS_8',
        'v10-production-only-reopen-release5': 'v10-production-only-reopen-release6',
        'V10_SERVER_PRODUCTION_ONLY_REOPEN_RELEASE5': 'V10_SERVER_PRODUCTION_ONLY_REOPEN_RELEASE6',
        'V10_PRISTINE_BASELINE_REOPEN_INDEPENDENT_RELEASE5': 'V10_PRISTINE_BASELINE_REOPEN_INDEPENDENT_RELEASE6',
        'v10-full-server-owned-scene-reopen-release4': 'v10-full-server-owned-scene-reopen-release5',
        'V10_SERVER_FULL_OWNED_SCENE_REOPEN_RELEASE4': 'V10_SERVER_FULL_OWNED_SCENE_REOPEN_RELEASE5',
        'V10_FULL_OWNED_SCENE_REOPEN_INDEPENDENT_RELEASE4': 'V10_FULL_OWNED_SCENE_REOPEN_INDEPENDENT_RELEASE5',
        'tools/review_v10_scene_release4_input.json': 'tools/review_v10_scene_release5_input.json',
        'tools/review-v10-diagnostics-markers-release4': 'tools/review-v10-diagnostics-markers-release5',
        'build/review-v10-candidate8-servers': 'build/review-v10-candidate9-servers',
        'tools/review_v10_client_author_release4_input.json': 'tools/review_v10_client_author_release5_input.json',
        'tools/review_v10_client_reenter_release4_input.json': 'tools/review_v10_client_reenter_release5_input.json',
        'tools/review_v10_client_full_release4_input.json': 'tools/review_v10_client_full_release5_input.json',
        'v10-client-min-attempt16': 'v10-client-min-attempt17',
        'V10_CLIENT_MIN_ATTEMPT_16': 'V10_CLIENT_MIN_ATTEMPT_17',
        'v10-client-reenter-release4-attempt1': 'v10-client-reenter-release5-attempt1',
        'V10_CLIENT_REENTER_RELEASE4_ATTEMPT_1': 'V10_CLIENT_REENTER_RELEASE5_ATTEMPT_1',
        'v10-client-full-release4-attempt3': 'v10-client-full-release5-attempt1',
        'V10_CLIENT_FULL_RELEASE4_ATTEMPT_3': 'V10_CLIENT_FULL_RELEASE5_ATTEMPT_1',
        'V10_CLIENT_MIN16_REENTER4_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT': 'V10_CLIENT_MIN17_REENTER5_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT',
        'V10_CLIENT_FULL4_ATT3_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT': 'V10_CLIENT_FULL5_ATT1_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT',
        'RP_TYPE_DUPLICATE_AUDIT_V10_CANDIDATE8_FINAL': 'RP_TYPE_DUPLICATE_AUDIT_V10_CANDIDATE9_FINAL',
        'V10_CLIENT_MIDDLE_PICK_CANDIDATE8_INDEPENDENT': 'V10_CLIENT_MIDDLE_PICK_CANDIDATE9_INDEPENDENT',
        'DIAGNOSTICS_SERVER_DISABLED_V10_RELEASE4': 'DIAGNOSTICS_SERVER_DISABLED_V10_RELEASE5',
        'DIAGNOSTICS_SERVER_ENABLED_V10_RELEASE4': 'DIAGNOSTICS_SERVER_ENABLED_V10_RELEASE5',
        'DIAGNOSTICS_SERVER_REENTER_V10_RELEASE4': 'DIAGNOSTICS_SERVER_REENTER_V10_RELEASE5',
        'REVIEW_V10_DIAGNOSTICS_RELEASE4': 'REVIEW_V10_DIAGNOSTICS_RELEASE5',
        'V10_ALIAS_AUTHOR_RELEASE2': 'V10_ALIAS_AUTHOR_RELEASE3',
        'V10_ALIAS_REENTER_RELEASE2': 'V10_ALIAS_REENTER_RELEASE3',
        'V10_ALIAS_PRODUCTION_REOPEN_RELEASE2': 'V10_ALIAS_PRODUCTION_REOPEN_RELEASE3',
        'V10_ALIAS_DISK_INDEPENDENT_RELEASE2': 'V10_ALIAS_DISK_INDEPENDENT_RELEASE3',
        'Review-v10-pristine-scene-release4': 'Review-v10-pristine-scene-release5',
        'REVIEW_V10_SCENE_ARCHIVE_RELEASE4': 'REVIEW_V10_SCENE_ARCHIVE_RELEASE5',
    }
    pattern = re.compile('|'.join(re.escape(x) for x in sorted(substitutions, key=len, reverse=True)))
    def replace(text):
        return pattern.sub(lambda m: substitutions[m.group()], text)
    key_names = {
        'actual_author7_creativepatch_reopen5_full_owned_scene4_markers4': 'actual_author8_creativepatch_reopen6_full_owned_scene5_markers5',
        'typed_minimal_reopen5_after_stop': 'typed_minimal_reopen6_after_stop',
        'typed_full_owned_scene4_after_stop': 'typed_full_owned_scene5_after_stop',
        'prepare_min16_marker_after_actual_author': 'prepare_min17_marker_after_actual_author',
        'prepare_reenter4_marker_after_actual_author': 'prepare_reenter5_marker_after_actual_author',
        'prepare_full4_strict42_marker_after_actual_author': 'prepare_full5_strict42_marker_after_actual_author',
        'actual_dedicated_off_on_reenter4_separate_root_go': 'actual_dedicated_off_on_reenter5_separate_root_go',
        'actual_min16': 'actual_min17',
        'actual_reenter4_only_after_min16_normal_pass': 'actual_reenter5_only_after_min17_normal_pass',
        'actual_full4_only_after_previous_normal_pass': 'actual_full5_only_after_previous_normal_pass',
        'independent_min16_reenter4_after_both_stop': 'independent_min17_reenter5_after_both_stop',
        'independent_linked_diagnostics4_after_actual_server3_client2_stop': 'independent_linked_diagnostics5_after_actual_server3_client2_stop',
        'prepare_alias2_no_launch': 'prepare_alias3_no_launch',
        'actual_alias2_three_phases_separate_root_go': 'actual_alias3_three_phases_separate_root_go',
        'independent_alias2_after_all_three_stop': 'independent_alias3_after_all_three_stop',
        'archive_pristine_reopen5_only_after_all_actual_required_gates': 'archive_pristine_reopen6_only_after_all_actual_required_gates',
        'independent_full4_att3_after_normal_stop_require_middle': 'independent_full5_att1_after_normal_stop_require_middle',
    }
    commands = {}
    for key, argv in previous['commands'].items():
        new = [replace(x) for x in argv]
        if '--scene-template' in new:
            new[new.index('--scene-template')+1] = 'tools/review_v10_scene_release4_input.json'
        if '--shader-marker' in new:
            new[new.index('--shader-marker')+1] = 'tools/review_v10_client_full_release4_input.json'
        if '--diagnostics-suffix' in new:
            new[new.index('--diagnostics-suffix')+1] = 'release5'
        if '--suffix' in new and 'tools/run_review_v10_alias_servers.py' in new:
            new[new.index('--suffix')+1] = 'release3'
        if 'tools/verify_v10_actual_client.py' in new:
            require('--require-middle-pick' in new, 'All current actual-client validators must require real middle key')
        commands[key_names.get(key, key)] = new
    full = commands['actual_full5_only_after_previous_normal_pass']
    require('--unattended-no-focus-pause' in full, 'Retain the isolated unattended runner option')
    old_path = str(args.previous_plan.resolve())
    out = {
        'schema': 'dw-v10-candidate9-runtime-commands-v1',
        'status': 'PREPARED_EXACT_CANDIDATE9_QA25_COMMANDS_RUNTIME_NOT_RUN',
        'executionPerformed': False, 'productionJar': production, 'productionJarSha256': prod['sha256'],
        'qaJar': addon, 'qaJarSha256': qa['sha256'],
        'productionFreeze': {'path': str(args.production_manifest.resolve()), 'sha256': sha(args.production_manifest)},
        'qaFreeze': {'path': str(args.qa_manifest.resolve()), 'sha256': sha(args.qa_manifest)},
        'nativeTestsActual': prod['nativeTests'], 'coreChecksActual': prod['coreChecks'],
        'commands': commands,
        'sequence': ['Author8 -> typed Creative settings8 -> QA-free pristine reopen6 -> full83 QA-free owned-scene reopen5.',
                     'MIN17 -> REENTER5 from MIN17 actual saved world -> FULL5 strict42 from pristine Author8 (unattended option).',
                     'Separate root GO after all clients stop: alias3 AUTHOR -> QA REENTER -> QA-free production reopen, then independent disk proof.',
                     'Separate root GO with matched hardware: diagnostic OFF60 -> ON60 same Author8 baseline -> REENTER ON saved world.',
                     'Independent current artifact checks -> pristine archive5 -> resource-agent checkpoint/package delivery5; no automatic acceptance.'],
        'requiredStaticEvidence': ['reports/RP_TYPE_DUPLICATE_AUDIT_V10_CANDIDATE9_FINAL.json',
                                   'reports/RP_INPUT_CORRESPONDENCE_V10_CANDIDATE9.json'],
        'historicalCandidate8Plan': {'path': old_path, 'sha256': sha(args.previous_plan), 'preservedExact': True},
        'historicalWholeFullFailure': 'reports/V10_CLIENT_FULL_RELEASE4_ATTEMPT_3.json',
        'historicalQualifiedSubset': 'reports/V10_CLIENT_FULL4_ATT3_PARTIAL_ATTACK_QUALIFIED_AUDIT.json',
        'newArtifactProofSubstitution': 'FORBIDDEN: prior8 proofs remain historical; only immutable templates/settings may seed new copies.',
        'manualReview': 'PENDING', 'wholeOriginalTask': 'PENDING',
    }
    aliases = {'schema': 'dw-v10-old-alias-actual-server-command-plan-v2',
               'status': 'PREPARED_CANDIDATE9_QA25_ALIAS3_NOT_RUN', 'executionPerformed': False,
               'productionJarSha256': prod['sha256'], 'qaJarSha256': qa['sha256'],
               'permanentOldRegistryInstances': 14, 'instancesPerOldAlias': 2,
               'savedOldInventoryStacks': 7, 'temporaryCanonicalOldItemProbes': 7,
               'prepareOnlyArgv': commands['prepare_alias3_no_launch'],
               'executeOnlyAfterRootGo': commands['actual_alias3_three_phases_separate_root_go'],
               'verifyOnlyAfterThreeActualNormalPassWrappersArgv': commands['independent_alias3_after_all_three_stop'],
               'expectedActualWrapperPaths': ['reports/V10_ALIAS_AUTHOR_RELEASE3.json',
                   'reports/V10_ALIAS_REENTER_RELEASE3.json', 'reports/V10_ALIAS_PRODUCTION_REOPEN_RELEASE3.json'],
               'actualEvidenceStatus': 'NOT_RUN', 'manualVisualOrClientPacketProof': 'NOT_CLAIMED'}
    diagnostics = {'schema': 'dw-v10-dedicated-diagnostics-command-plan-v2',
                   'status': 'PREPARED_CANDIDATE9_QA25_DIAGNOSTICS5_NOT_RUN', 'executionPerformed': False,
                   'productionJar': production, 'productionJarSha256': prod['sha256'],
                   'qaJar': addon, 'qaJarSha256': qa['sha256'],
                   'baseline': 'build/runtime-server-v10-author-min-attempt8/isolated-smoke-world',
                   'authorReport': 'reports/V10_SERVER_AUTHOR_MIN_ATTEMPT_8.json',
                   'settingsReport': 'reports/V10_SCENE_BEFORE_FIRST_ENTRY_SETTINGS_8.json',
                   'markerDirectory': 'tools/review-v10-diagnostics-markers-release5',
                   'markerPreparation': 'Actual Author8/settings8 candidate server phase creates new exact-SHA markers; no markers generated by this metadata tool.',
                   'serverPhaseArgvOnlyAfterSeparateRootGo': commands['actual_dedicated_off_on_reenter5_separate_root_go'],
                   'independentArgvOnlyAfterThreeServersAndTwoClientsNormalPass': commands['independent_linked_diagnostics5_after_actual_server3_client2_stop'],
                   'expectedWrapperPaths': ['reports/DIAGNOSTICS_SERVER_DISABLED_V10_RELEASE5.json',
                       'reports/DIAGNOSTICS_SERVER_ENABLED_V10_RELEASE5.json', 'reports/DIAGNOSTICS_SERVER_REENTER_V10_RELEASE5.json'],
                   'requiredGuards': ['OFF and ON copy exact same immutable Author8/settings8 world.',
                       'REENTER copies ON actual saved world.', 'Default duration remains 60 seconds.',
                       'All clients/other Minecraft/Gradle closed for matched hardware windows; record external interference if present.',
                       'No prior candidate8 runtime proof may satisfy current9 gates.'],
                   'actualEvidenceStatus': 'NOT_RUN'}
    out['separatePreparedPlans'] = {'aliases3': str(args.alias_output), 'diagnostics5': str(args.diagnostics_output)}
    # Only the explicitly retained template/previous-history paths may mention old runtime evidence.
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(out, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    for p, value in [(args.alias_output, aliases), (args.diagnostics_output, diagnostics)]:
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': out['status'], 'output': str(args.output), 'commands': len(commands)}))


if __name__ == '__main__':
    main()
