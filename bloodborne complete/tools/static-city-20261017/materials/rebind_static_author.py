"""Record and apply the author-only input-scope correction at a stopped cursor."""
from datetime import datetime, timezone
from pathlib import Path
import shutil
import portable_static as control

work, world, runtime = control.WORK, control.WORLD, control.RUNTIME
receipt = control.read(control.PREPARED)
author = Path(receipt['author']['path'])
old_sha = receipt['author']['sha256']
new_sha = control.sha(author)
control.need(old_sha != new_sha, 'Author must have changed')
history = work / 'full-evidence' / 'author-rebind-history'
control.need(not history.exists(), 'Rebind history already exists')
with control.stopped_world(world):
    cp = world / control.CP
    cp_sha = control.sha(cp)
    control.need(cp_sha == 'b0c5e40dbadb129964ce95414a763f6d5be5b43e127dceec68c13ebee72f99a9', 'Unexpected resume cursor')
    control.need(control.fully_drained(control.read(cp)), 'Cursor must be drained')
    control.need(control.sha(Path(receipt['productionGame']['path'])) == receipt['productionGame']['sha256'], 'Production game changed')
    runtime_author = next(Path(row['path']) for row in receipt['mods'] if row['sha256'] == old_sha)
    control.need(control.sha(runtime_author) == old_sha, 'Prior runtime author changed')
    marker_path = Path(receipt['marker']['path'])
    authorization_path = Path(receipt['authorization']['path'])
    history.mkdir()
    for path in (control.PREPARED, marker_path, authorization_path, runtime_author, cp):
        shutil.copy2(path, history / path.name)
    shutil.copy2(author, runtime_author)
    authorization = control.read(authorization_path)
    authorization['authorJarSha256'] = new_sha
    control.atomic_json(authorization_path, authorization)
    marker = control.read(marker_path)
    marker['cityStaticAuthorJarSha256'] = new_sha
    marker['staticArchitectureDataMigrationAuthorizationSha256'] = control.sha(authorization_path)
    control.atomic_json(marker_path, marker)
    receipt['author'] = control.pin(author)
    receipt['mods'] = [control.pin(runtime_author) if row['sha256'] == old_sha else row for row in receipt['mods']]
    receipt['authorization'] = control.pin(authorization_path)
    receipt['marker'] = control.pin(marker_path)
    receipt.setdefault('authorRebinds', []).append(str(history / 'REBIND-RECEIPT.json'))
    control.atomic_json(control.PREPARED, receipt)
    control.atomic_json(history / 'REBIND-RECEIPT.json', {
        'status': 'AUTHOR_ONLY_SCOPE_NARROWING_AT_DRAINED_CURSOR',
        'updatedUtc': datetime.now(timezone.utc).isoformat(),
        'operationId': receipt['operationId'],
        'oldAuthorSha256': old_sha, 'newAuthorSha256': new_sha,
        'retainedCheckpointSha256': cp_sha,
        'productionGameSha256': receipt['productionGame']['sha256'],
        'runtimeManifestSha256': receipt['manifest']['sha256'],
        'reason': 'Exclude whole components with existing assembly/type-alias input families before selection; exact all-static replay retained; unknown selected mismatches still fail closed.',
        'policy': str(control.LOCAL / 'work/source/staticAuthor-resources/.SOURCE-SCOPE-POLICY.json'),
        'checkpointRows': 1024, 'unchangedChunkLimits': True,
        'worldRegionsWritten': False, 'gameplayVerified': False,
    })
    build_receipt_path = control.LOCAL / 'work/source/GAME-BUILD-RECEIPT.json'
    build_receipt = control.read(build_receipt_path)
    build_receipt['authorSha256'] = new_sha
    build_receipt['authorBuildLog'] = str(control.LOCAL / 'work/source/static-author-scope-build.log')
    build_receipt['authorScopePolicy'] = str(control.LOCAL / 'work/source/staticAuthor-resources/.SOURCE-SCOPE-POLICY.json')
    control.atomic_json(build_receipt_path, build_receipt)
    state_path = control.LOCAL / 'work/SESSION-STATE.json'
    state = control.read(state_path)
    state.update(updatedUtc=datetime.now(timezone.utc).isoformat(),
        status='STOPPED_CURSOR_REBOUND_READY_TO_RESUME', nativeFullExecSession=None,
        durableCursorLine=896, durableCursorSha256=cp_sha,
        authorRebindReceipt=str(history / 'REBIND-RECEIPT.json'))
    control.atomic_json(state_path, state)
print(new_sha)
