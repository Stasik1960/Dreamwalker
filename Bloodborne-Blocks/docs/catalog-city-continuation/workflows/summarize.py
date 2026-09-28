import collections, gzip, hashlib, json, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / 'build/catalog-city-diagnostic-20260927-run3'
if len(sys.argv) > 1:
    RUN = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(ROOT/'tools'))
from source_mapping_archive import archive

def read(name):
    path = RUN/name
    return json.loads(gzip.decompress(path.read_bytes()) if path.suffix == '.gz' else path.read_bytes())

first, second, gate, residuals = map(read, ('first.json', 'second.json', 'protected.json', 'residuals.json.gz'))
atomic = first['mappingDiagnostics']['atomicOwnerGroups']['groups']
group_ids = {group['transaction'] for group in atomic}
accepted_groups = {entry['transaction'] for entry in first['ledger'] if entry.get('transaction') in group_ids}
roots = {}
for entry in first['ledger']:
    outputs = entry.get('outputs')
    if outputs is None:
        target = next(change['after'] for change in entry['changes'] if change['position'] == entry['targetRoot'])
        outputs = [{'targetRoot': entry['targetRoot'], 'target': target}]
    for output in outputs:
        roots[(entry['dimension'], tuple(output['targetRoot']))] = output['target']
target_ids = sorted({target.split('[', 1)[0] for target in roots.values()})
resources = ROOT/'src/main/resources/bloodborne_blocks'
registry = {'bloodborne_blocks:architecture_part'}
for scope in ('logical', 'city'):
    registry.update('bloodborne_blocks:'+block['id'] for block in json.loads((resources/scope/'definitions.json').read_bytes())['blocks'])
frozen = archive()
old = {'bloodborne_blocks:'+block['id'] for name in ('definitions.json', 'v2\\definitions.json') for block in frozen[name]['blocks']}
old_ids, unknown_ids, already_registered = collections.Counter(), collections.Counter(), collections.Counter()
for row in first['unmatchedModules']:
    ident = row['state'].split('[', 1)[0]
    bucket = already_registered if ident in registry else old_ids if ident in old else unknown_ids
    bucket[ident] += row['count']
helpers, preservation, byte_check = map(read, ('helpers.json', 'preservation.json', 'second-byte-comparison.json'))
summary = {
    'status': 'DIAGNOSTIC_ONLY',
    'source': {'path': str(ROOT/'reference-inputs/latest-modded-world.zip'),
               'sha256': hashlib.sha256((ROOT/'reference-inputs/latest-modded-world.zip').read_bytes()).hexdigest()},
    'freshConversion': first['counts'],
    'ledgerEntries': len(first['ledger']),
    'restoredRootObjects': len(roots),
    'atomicGroups': {'total': len(group_ids), 'converted': len(accepted_groups),
                     'notConverted': len(group_ids-accepted_groups),
                     'failedGroupsByReason': dict(sorted(collections.Counter(row['preflightReason'] for row in residuals['groups']).items()))},
    'uniqueTargetRegistryFamilies': {'count': len(target_ids), 'ids': target_ids,
                                    'definition': 'Distinct restored target registry IDs; no semantic merging of technical IDs.'},
    'protectedComposite': {'result': gate['result'], 'fragmentedProtectedObjects': gate['fragmented'],
                           'familiesWithFailures': sum(row['fragmented'] > 0 for row in gate['families'].values()),
                           'foreignConflictOccurrences': len(gate['foreign_conflicts']),
                           'unprovenMemberships': len(gate['unresolved_technical_membership']),
                           'atomicGroupsPassed': gate['atomic_owner_groups']['passed'],
                           'atomicGroupsFailed': len(gate['atomic_owner_groups']['failures']),
                           'families': gate['families']},
    'helpers': {'checked': helpers['checked'], 'orphanHelpers': len(helpers['orphans']), 'ok': helpers['ok']},
    'remainingIds': {'knownOldNotRegistered': dict(sorted(old_ids.items())),
                     'knownOldDistinctIds': len(old_ids), 'knownOldBlocks': sum(old_ids.values()),
                     'unknownDistinctIds': len(unknown_ids), 'unknownBlocks': sum(unknown_ids.values()),
                     'unknown': dict(sorted(unknown_ids.items())),
                     'registeredButReportedUnmatched': dict(sorted(already_registered.items())),
                     'unmatchedStateCount': len(first['unmatchedModules'])},
    'preservation': preservation,
    'secondPass': {'counts': second['counts'], 'preservation': read('second-preservation.json'), **byte_check},
    'cleanup': 'NOT_STARTED: requires complete conversion and all preservation/registry/composite gates to pass.',
}
(RUN/'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
print(json.dumps({key: value for key, value in summary.items() if key not in {'uniqueTargetRegistryFamilies', 'remainingIds', 'protectedComposite'}}, ensure_ascii=False))
print(json.dumps({'targets': len(target_ids), 'oldIds': len(old_ids), 'oldBlocks': sum(old_ids.values()),
                  'unknownIds': len(unknown_ids), 'unknownBlocks': sum(unknown_ids.values()),
                  'fragmented': gate['fragmented'], 'unprovenMemberships': len(gate['unresolved_technical_membership'])}))
