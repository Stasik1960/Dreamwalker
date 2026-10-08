"""Prepare client QA26 retry metadata while preserving exact server QA25 bindings."""
import argparse
import copy
import hashlib
import json
from pathlib import Path


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--previous-plan', type=Path, default=Path('tools/review_v10_candidate9_runtime_bound25.json'))
    p.add_argument('--qa-manifest', type=Path)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    if a.output.exists():
        raise RuntimeError('Never overwrite earlier runtime/QA preparation metadata')
    old = read(a.previous_plan)
    data = copy.deepcopy(old)
    client_jar = 'build/frozen-artifacts/v10-qa-26/dreamwalker-first-set-review-qa-0.1.0-prototype.5.jar'
    client_sha = 'PENDING_QA26_ACTUAL_FREEZE'
    if a.qa_manifest:
        qa = read(a.qa_manifest)
        artifact = Path(qa['artifact'])
        if qa['productionJarSha256'] != old['productionJarSha256'] or 'v10-qa-26' not in str(artifact):
            raise RuntimeError('Actual QA26 must be bound to exact unchanged production9')
        if sha(artifact) != qa['sha256']:
            raise RuntimeError('Actual QA26 binary differs from its freeze manifest')
        client_jar = str(artifact.resolve().relative_to(Path.cwd().resolve())).replace('\\', '/')
        client_sha = qa['sha256']
        data['clientQaFreezeManifest'] = {'path': str(a.qa_manifest.resolve()), 'sha256': sha(a.qa_manifest)}
    for key, argv in list(data['commands'].items()):
        if 'tools/run_final_client.py' in argv:
            argv[argv.index('--extra-mod')+1] = client_jar
        # Only FULL5 attempt1 was actually run. MIN17 and REENTER5 are still fresh planned names.
        data['commands'][key] = [x.replace('v10-client-full-release5-attempt1', 'v10-client-full-release5-attempt2')
                                 .replace('V10_CLIENT_FULL_RELEASE5_ATTEMPT_1', 'V10_CLIENT_FULL_RELEASE5_ATTEMPT_2')
                                 .replace('V10_CLIENT_FULL5_ATT1_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT',
                                          'V10_CLIENT_FULL5_ATT2_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT') for x in argv]
    full_key = 'independent_full5_att1_after_normal_stop_require_middle'
    if full_key in data['commands']:
        data['commands']['independent_full5_att2_after_normal_stop_require_middle'] = data['commands'].pop(full_key)
    server_keys = [k for k,v in old['commands'].items() if
                   any(script in v for script in ['tools/run_review_v10_candidate_servers.py',
                       'tools/run_review_v10_alias_servers.py'])]
    if any(data['commands'][k] != old['commands'][k] for k in server_keys):
        raise RuntimeError('Client retry must preserve every prepared server/alias/diagnostics argv exactly')
    full = data['commands']['actual_full5_only_after_previous_normal_pass']
    if '--unattended-no-focus-pause' not in full:
        raise RuntimeError('Do not silently alter the current isolated focus option')
    data.update({'schema': 'dw-v10-candidate9-server25-client26-runtime-commands-v1',
        'status': 'PREPARED_EXACT_SERVER_QA25_CLIENT_QA26_RUNTIME_NOT_RUN' if a.qa_manifest
                  else 'PREPARED_SERVER_QA25_CLIENT_QA26_PENDING_ACTUAL_FREEZE_NO_EXECUTION',
        'executionPerformed': False, 'clientFreezeVerified': bool(a.qa_manifest),
        'serverQaJar': old['qaJar'], 'serverQaJarSha256': old['qaJarSha256'],
        'clientQaJar': client_jar, 'clientQaJarSha256': client_sha,
        'qaArtifactRoleBindings': {'alreadyStoppedServerAuthor8PristineReopen6FullOwnedScene5': old['qaJar'],
            'futureAlias3AndDedicatedDiagnostics5': old['qaJar'],
            'onlyFreshFull5Att2Min17Reenter5Att1': client_jar},
        'previousServer25Client25Plan': {'path': str(a.previous_plan.resolve()), 'sha256': sha(a.previous_plan), 'preservedExact': True},
        'historicalFull5Attempt1Failure': {'wrapper': 'reports/V10_CLIENT_FULL_RELEASE5_ATTEMPT_1.json',
            'wholeStatus': 'FAIL', 'phase': 0, 'screen': 'DownloadingTerrainScreen',
            'correction': 'reports/V10_FULL_LOADING_SCREEN_CAUSAL_CORRECTION.json', 'substitutionForRetry': False},
        'clientOnlyChanges': {'scope': 'QA26 waits for native DownloadingTerrainScreen close before any actor setup; initial first-entry snapshot remains before any setup.',
            'productionChanged': False, 'serverCommandsChanged': False,
            'worldReAuthorRequired': False, 'newClientRuntimeProof': 'PENDING_ACTUAL_RUN'},
        'focusFlagCausalClaim': 'UNSUPPORTED: exact screen class434 is downloading terrain, and failure happened while pauseOnLostFocus=false.',
    })
    data['sequence'][1] = 'Root-approved FULL5ATT2 from pristine Author8 first; then MIN17 -> REENTER5 from actual MIN17 save. All three use frozen QA26; server remains QA25.'
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': data['status'], 'output': str(a.output), 'serverCommandsUnchanged': True}))


if __name__ == '__main__':
    main()
