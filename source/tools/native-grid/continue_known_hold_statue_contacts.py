"""Root's serial runner for already reviewed finite HoldStatue repairs.

No SQL, source edits, worlds, builds, tests or source upgrades. Every mutation
uses the existing preview/hash/execute consumer under a fresh Guard operation.
An unsupported failure stops the runner with its exact receipt preserved.
"""
import argparse, datetime, hashlib, json, os, subprocess, sys, traceback
from pathlib import Path

P = Path(__file__).resolve().parents[2]
R = P.parent / 'research'
PLAN = R / 'native-grid-plan'
GUARD = Path(r'C:\Users\vakir\CodexControl\Guard.ps1')
MIGRATE = 'tools/native-grid-city/migrate.py'
HELPER = 'tools/native-grid/qa/hold_statue_native_contact.py'
PINS = {
    MIGRATE: '38dab8b724472418d49f91130b49fc954d3eb845829c83e817923371f91f5c56',
    'tools/native-grid-city/native_resume.py': 'a316616686d288cc5554441d2cc7d3fb6e747a2eef36e60e6ee6cbd379750987',
    'tools/native-grid-city/native_certificates.py': '6e7e1cd4af4d26e2eb7122fa706c3a62ff4d393d56047f30d60d76a3246336f6',
    'tools/native-grid-city/context_stage.py': 'f04362e1021287ac16f613d85ff85033d92f5b315c4bb71112df45b3f09b6d45',
    HELPER: '9ebdaea9395bc7c1dbacfdd50897498d27b882b9933b7c2abd3d4ac7f7a8c8aa',
}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            h.update(block)
    return h.hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def ref(path):
    return {'path': str(Path(path).resolve()), 'sha256': sha(path)}


def bound(reference):
    require(sha(reference['path']) == reference['sha256'], 'Changed bound evidence')
    return read(reference['path'])


def save(path, data):
    temporary = path.with_suffix('.tmp')
    with temporary.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def pins():
    for name, expected in PINS.items():
        require(sha(P / name) == expected, 'Reviewed source changed: ' + name)


def task(script, arguments, journal, out, allow_fail=False):
    pins()
    tokens = ['tools/native-grid/run_task.py', script, *arguments]
    require(all(token and not any(c.isspace() or ord(c) in (34, 39) for c in token) for token in tokens), 'Fixed task tokens must need no embedded quoting')
    prior = set((R / 'processes').glob('*-' + Path(script).stem + '.json'))
    journal['activeTask'] = {'script': script, 'args': arguments, 'startedUtc': datetime.datetime.now(datetime.timezone.utc).isoformat()}
    save(out / 'progress.json', journal)
    completed = subprocess.run(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(GUARD),
        '-Mode', 'Operation', '-Executable', sys.executable, '-Arguments', ' '.join(tokens), '-WorkingDirectory', str(P)],
        cwd=P, creationflags=subprocess.CREATE_NO_WINDOW)
    created = set((R / 'processes').glob('*-' + Path(script).stem + '.json')) - prior
    require(len(created) == 1, 'Expected exactly one new durable task receipt, never trust Guard exit0/latest.json')
    path = next(iter(created)); result = read(path)
    require(result['script'] == str((P / script).resolve()) and result['args'] == arguments, 'Task receipt script/arguments differ')
    pins()
    journal['tasks'].append(ref(path)); journal['activeTask'] = None
    save(out / 'progress.json', journal)
    require(result['status'] in ('PASS', 'FAIL') and result['exitCode'] == (0 if result['status'] == 'PASS' else 1), 'Unknown task outcome')
    require(completed.returncode == 0, 'Guard operation itself failed')
    require(allow_fail or result['status'] == 'PASS', 'Stopped task: ' + str(path))
    return path, result


def policy(result, preview, document, seen):
    require(result['status'] == 'PASS_SOURCE_DATA_READY_NOT_EXECUTED' and result['generator']['sha256'] == PINS[HELPER], 'Unknown data producer/result')
    require(all(result[key] == 0 for key in ('databaseWrites', 'worldFilesRead', 'mapWrites', 'sourceEdits', 'gameProcessesStarted')), 'Data helper performed unrelated work')
    require(document['ownerGraphTraversal'] is False and document['applyAuthorization'] is False and document['gameplayPass'] is False, 'Unreviewed data authority')
    require(preview['mapWrites'] == 0 and preview['gameplayPass'] is False, 'Preview is not authoring-only')
    expected_sources = {'migrate': PINS[MIGRATE], 'nativeResume': PINS['tools/native-grid-city/native_resume.py'],
        'nativeCertificates': PINS['tools/native-grid-city/native_certificates.py']}
    require(preview['sourceFiles'] == expected_sources, 'Preview consumer tuple changed')
    facts = bound(result['facts']); whole = facts['whole']; native = facts['native']
    require(whole['poseData']['registryId'] == 'bloodborne_dw:source_hold_statue' and native['object']['kind'] == 'substrate', 'Only this proved whole class and ordinary C are admitted')
    classification = bound(facts['wholeAuthority']['classification'])
    manifest = bound(facts['wholeAuthority']['preservedAssemblies'])
    require(native['poseData']['registryId'] not in classification['protectedRegistryIds']
        and not any(row['pose'] == native['object']['pose'] for row in manifest['poseMatches']), 'Protected v12 or confirmed assembly cannot be an automatic ordinary C')
    require(all(len(row['recipe']['fragments']) == 1 and row['sourceBE'] == {'exists': False} for row in whole['physicalMembers']), 'W must remain pure original native members')
    require(not {tuple(row) for row in whole['runtimeMemberCells']} &
        ({tuple(row) for row in native['runtimeMemberCells']} | {tuple(native['root'][i] + offset[i] for i in range(3)) for offset in native['visualOffsets']}), 'C intersects original W')
    if result['route'] == 'whole_template':
        require(document['kind'] == 'FINITE_NATIVE_WHOLE_TEMPLATE_FALLBACK_V1' and preview['status'] == 'EXACT_NATIVE_WHOLE_TEMPLATE_FALLBACK_PREVIEW', 'Unknown whole fallback')
        require(preview['wholeRowsRemoved'] == len(document['whole']['wholeCells']) and preview['bridgeRowsPruned'] == 0, 'Unexpected whole/bridge delta')
        keep = {(row['dimension'], *row['position']) for row in document['keepNativeQueue']}
        actual_keep = {(row['dimension'], *row['position']) for row in preview['pointPlan'] if row['keepNativeActivation']}
        require(keep == actual_keep and 1 <= len(keep) <= 2, 'Exact real C seed/root must remain queued')
        pruned = [row for row in preview['pointPlan'] if row['pruneQueueAndBridge']]
        require(preview['queueRowsPruned'] == len(pruned) and all(row['queue'] == [{'processed': 0}] for row in pruned), 'Only exact undemanded pending0 can be pruned')
        key = ('whole_template', whole['object']['dim'], whole['object']['uuid'])
        command, directory = 'fallback-native-whole-template', 'native-whole-template-fallbacks'
    else:
        require(result['route'] == 'visual_row' and document['kind'] == 'FINITE_NATIVE_VISUAL_PROTECTION_CORRECTION_V1'
            and preview['status'] == 'EXACT_NATIVE_VISUAL_PROTECTION_CORRECTION_PREVIEW', 'Unknown visual correction')
        row = document['protectionRow']
        require(row['reason'] == 'whole_local_overlap' and row['uuid'] == whole['object']['uuid'], 'Only exact W visual reservation may be removed')
        key = ('visual_row', row['dim'], row['x'], row['y'], row['z'], row['uuid'])
        command, directory = 'correct-native-visual-protection', 'native-visual-protection-corrections'
    require(key not in seen, 'Same repair recurred; stop for manual diagnosis')
    seen.add(key)
    return command, directory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--max-contacts', type=int, default=32)
    args = parser.parse_args(); out = args.out.resolve()
    require(out.is_relative_to(PLAN / 'full') and not out.exists() and 1 <= args.max_contacts <= 64, 'One fresh finite research directory required')
    out.mkdir(parents=True); seen = set()
    journal = {'status': 'RUNNING_KNOWN_FINITE_CONTACTS_ONLY', 'sourcePins': PINS, 'runner': ref(__file__), 'tasks': [], 'repairs': [],
        'activeTask': None, 'worldWrites': 0, 'builds': 0, 'gameRuns': 0, 'readyForDelivery': False}
    try:
        for number in range(args.max_contacts + 1):
            path, resume = task(MIGRATE, ['--plan', '../research/native-grid-plan', 'resume-native-compose'], journal, out, allow_fail=True)
            journal['latestResume'] = ref(path)
            if resume['status'] == 'PASS':
                journal['status'] = 'NATIVE_COMPOSE_FINISHED_NOT_DELIVERY_ACCEPTANCE'; save(out / 'progress.json', journal); return
            log = Path(resume['log']).read_text(encoding='utf-8')
            errors = [line for line in log.splitlines() if line.startswith('RuntimeError: ')]
            require(len(errors) == 1 and any(message in errors[0] for message in (
                'Native overhang would overlap a dedicated whole-object role',
                'Required native visual overhang meets a protected local object; retain that particular source root: ')), 'Unstudied actual failure; no automatic fallback')
            require(number < args.max_contacts, 'Finite contact budget reached; exact failed resume preserved')
            case = out / path.stem
            task(HELPER, ['--failed-run', path.relative_to(P.parent).as_posix().replace('research/', '../research/', 1), '--out-dir',
                '../research/' + case.relative_to(R).as_posix()], journal, out)
            result = read(case / 'result.json'); document = bound(result['certificate'])
            command = 'fallback-native-whole-template' if result['route'] == 'whole_template' else 'correct-native-visual-protection'
            preview = case / 'preview.json'
            arguments = ['--plan', '../research/native-grid-plan', command, '--certificate',
                '../research/' + Path(result['certificate']['path']).relative_to(R).as_posix(), '--certificate-sha256',
                result['certificate']['sha256'], '--preview', '../research/' + preview.relative_to(R).as_posix()]
            task(MIGRATE, arguments, journal, out)
            proposed = read(preview); approved_command, directory = policy(result, proposed, document, seen)
            require(approved_command == command, 'Approved route changed')
            preview_sha = sha(preview)
            task(MIGRATE, [*arguments, '--execute', '--expected-preview-sha256', preview_sha], journal, out)
            receipt = PLAN / 'full' / directory / (preview_sha + '.json'); committed = read(receipt)
            expected_status = 'PASS_SOURCE_ONLY' if result['route'] == 'whole_template' else 'PASS_SOURCE_CORRECTION_ONLY'
            require(committed['status'] == expected_status and committed['preview']['sha256'] == preview_sha
                and committed['processedQueueRowsReset'] == 0 and committed['mapWrites'] == 0, 'Committed effect differs')
            journal['repairs'].append({'route': result['route'], 'certificate': result['certificate'], 'receipt': ref(receipt),
                'nativeUuid': native_uuid(document), 'wholeUuid': result_whole_uuid(document), 'previewSha256': preview_sha})
            save(out / 'progress.json', journal)
    except BaseException:
        journal['status'] = 'STOPPED_FOR_MANUAL_ACTUAL_FAILURE_REVIEW'; journal['error'] = traceback.format_exc()
        save(out / 'progress.json', journal); raise


def native_uuid(document):
    return document['native']['uuid']


def result_whole_uuid(document):
    return document.get('whole', document.get('retainedWhole'))['object']['uuid']


if __name__ == '__main__':
    main()
