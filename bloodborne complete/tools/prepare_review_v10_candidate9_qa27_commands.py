"""Prepare telemetry-only QA27 FULL5ATT3/MIN17/REENTER5 metadata; never launch."""
import argparse
import copy
import hashlib
import json
from pathlib import Path


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--previous-plan', type=Path, default=Path('tools/review_v10_candidate9_runtime_bound26.json'))
    p.add_argument('--qa-manifest', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--full-attempt3', action='store_true', required=True)
    a = p.parse_args()
    if a.output.exists():
        raise RuntimeError('Never overwrite previous prepared commands or runtime history')
    old, qa = read(a.previous_plan), read(a.qa_manifest)
    if qa['productionJarSha256'] != old['productionJarSha256'] or 'v10-qa-27' not in str(qa['artifact']):
        raise RuntimeError('QA27 must have actual provenance for exact unchanged production9')
    artifact = Path(qa['artifact'])
    if sha(artifact) != qa['sha256']:
        raise RuntimeError('Actual frozen QA27 binary differs from manifest')
    addon = str(artifact.resolve().relative_to(Path.cwd().resolve())).replace('\\', '/')
    data = copy.deepcopy(old)
    server_before = {k: v for k,v in old['commands'].items() if any(tool in v for tool in
        ['tools/run_review_v10_candidate_servers.py', 'tools/run_review_v10_alias_servers.py'])}
    commands = {}
    for key, original in old['commands'].items():
        argv = list(original)
        if 'tools/run_final_client.py' in argv:
            argv[argv.index('--extra-mod')+1] = addon
        argv = [x.replace('v10-client-full-release5-attempt2', 'v10-client-full-release5-attempt3')
                 .replace('V10_CLIENT_FULL_RELEASE5_ATTEMPT_2', 'V10_CLIENT_FULL_RELEASE5_ATTEMPT_3')
                 .replace('V10_CLIENT_FULL5_ATT2_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT',
                          'V10_CLIENT_FULL5_ATT3_ROSTER_PARTS_MIDDLE_DIAGNOSTICS_INDEPENDENT') for x in argv]
        key = key.replace('full5_att2', 'full5_att3')
        commands[key] = argv
    if any(commands[k] != value for k,value in server_before.items()):
        raise RuntimeError('Server Author8/alias3/diagnostics5 commands must remain exactly bound to QA25')
    current_client_launches = [v for v in commands.values() if 'tools/run_final_client.py' in v]
    if len(current_client_launches) != 3 or any(v[v.index('--extra-mod')+1] != addon for v in current_client_launches):
        raise RuntimeError('Exactly FULL5ATT3/MIN17/REENTER5 must use QA27')
    full = next(v for v in current_client_launches if '--profile' in v and v[v.index('--profile')+1] == 'full_client')
    if full[full.index('--run-name')+1] != 'v10-client-full-release5-attempt3' or '--unattended-no-focus-pause' not in full:
        raise RuntimeError('Fresh full attempt3 and isolated focus option are required')
    if any('tools/verify_v10_actual_client.py' in v and '--require-middle-pick' not in v for v in commands.values()):
        raise RuntimeError('All current ordinary-client checks must keep strict actual middle key gate')
    data.update({'schema': 'dw-v10-candidate9-server25-client27-runtime-commands-v1',
        'status': 'PREPARED_EXACT_SERVER_QA25_CLIENT_QA27_FULL5ATT3_RUNTIME_NOT_RUN',
        'executionPerformed': False, 'clientFreezeVerified': True,
        'clientQaJar': addon, 'clientQaJarSha256': qa['sha256'],
        'clientQaFreezeManifest': {'path': str(a.qa_manifest.resolve()), 'sha256': sha(a.qa_manifest)},
        'commands': commands,
        'previousServer25Client26Plan': {'path': str(a.previous_plan.resolve()), 'sha256': sha(a.previous_plan), 'preservedExact': True},
        'qaArtifactRoleBindings': {'alreadyStoppedServerAuthor8PristineReopen6FullOwnedScene5': old['serverQaJar'],
            'futureAlias3AndDedicatedDiagnostics5': old['serverQaJar'],
            'onlyFreshFull5Att3Min17Reenter5Att1': addon},
        'historicalFull5Attempt2Failure': {'wrapper': 'reports/V10_CLIENT_FULL_RELEASE5_ATTEMPT_2.json',
            'wholeStatus': 'FAIL', 'phase': 8, 'qualifiedSubset': 'reports/V10_CLIENT_FULL5_ATT2_COMPLETED_ORDINARY_PARTIAL_AUDIT.json',
            'menuFailureCause': 'NOT_YET_ESTABLISHED: telemetry-only QA27 will capture actual target/packet observations.',
            'substitutionForRetry': False},
        'clientOnlyChanges': {'scope': 'Bounded passive server UseItemCallback and client/server menu open observations. Exact UUID predicate, ordinary input, readiness and 160tick limit preserved.',
            'productionChanged': False, 'serverCommandsChanged': False, 'worldReAuthorRequired': False,
            'newClientRuntimeProof': 'NOT_RUN'},
    })
    data['sequence'][1] = 'Root separate GO: FULL5ATT3 first for actual menu telemetry; after successful current full proof MIN17 -> distinct REENTER5 from MIN17 saved world. All clients QA27; server QA25 unchanged.'
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': data['status'], 'output': str(a.output), 'serverArgvUnchanged': True}))


if __name__ == '__main__':
    main()
