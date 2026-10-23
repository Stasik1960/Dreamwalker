"""Verify actual retained-BE background types satisfy the finite bridge gate."""
from pathlib import Path
import argparse, hashlib, json, sqlite3, sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from export_cells import read_lines, state_key


FLAGS = ('opaque', 'sidedTransparency', 'luminance', 'fluidEmpty')


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def observations(paths, finite):
    known, observed, proofs = {}, set(), []
    for path in paths:
        audit = json.loads(path.read_text(encoding='utf-8-sig'))
        reference = {'path': str(path.resolve()), 'sha256': sha(path), 'runtimeSha256': audit.get('runtimeSha256'), 'catalogSha256': audit.get('catalogSha256')}
        if finite:
            require(audit.get('status') == 'PASS' and audit.get('worldReads') == audit.get('worldWrites') == 0, 'Finite host correspondence requires passed worldless observations')
        for index, row in enumerate(audit['results']):
            value = tuple(row[field] for field in FLAGS)
            observed.add(value)
            if not finite:
                continue  # Preserve the old global-only prior-comparison API.
            require(row.get('status') == 'PASS' and all(type(row[field]) is bool for field in ('opaque', 'sidedTransparency', 'fluidEmpty')) and type(row['luminance']) is int, 'Unverified or malformed exact carrier observation')
            require(row['luminance'] == 0 and row['fluidEmpty'], 'An observed bridge host is wet or emitting')
            key = state_key(row['input']['state'])
            require(key not in known or known[key]['flags'] == value, 'Conflicting native observations for exact host state: ' + key)
            host = known.setdefault(key, {'flags': value, 'sources': []})
            host['sources'].append({'audit': reference, 'resultIndex': index, 'status': row['status'], 'propertiesEvidence': row.get('propertiesEvidence')})
        proofs.append(reference)
    return known, observed, proofs


def whole_role(catalog, key, mapping):
    prefix, encoded = key.rsplit(':', 1)
    template_key, offset = prefix[len('whole:'):], json.loads(encoded)
    require(template_key and isinstance(offset, list) and len(offset) == 3 and
            all(type(value) is int and abs(value) <= 127 for value in offset) and
            key == 'whole:' + template_key + ':' + json.dumps(offset, separators=(',', ':')),
            'Invalid finite whole bridge role: ' + key)
    templates = [row for row in catalog['wholeObjects'] if row['key'] == template_key]
    require(len(templates) == 1, 'Whole bridge role needs one exact compiled template: ' + key)
    roles = [row for row in templates[0]['cells'] if row['offset'] == offset]
    require(len(roles) == 1, 'Whole bridge role needs one exact compiled offset: ' + key)
    index = roles[0]['cell']
    require(type(index) is int and 0 <= index < len(catalog['cells']) and
            (key not in mapping or mapping[key] == index), 'Whole role/global mapping correspondence differs: ' + key)
    return {'template': template_key, 'offset': offset, 'cellIndex': index}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--compositions', type=Path, required=True)
    parser.add_argument('--mapping', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--carrier-audit', type=Path, action='append', default=[], help='Repeat for exact native observation groups; global-only prior comparisons remain supported')
    parser.add_argument('--plan', type=Path, help='Read-only plan directory for finite bridge host-state/placement correspondence')
    args = parser.parse_args()
    catalog = json.loads(args.catalog.read_text(encoding='utf-8'))
    mapping = {}
    for row in read_lines(args.mapping):
        require(row['key'] not in mapping or mapping[row['key']] == row['typeIndex'], 'One mapping key names conflicting compiled types')
        mapping[row['key']] = row['typeIndex']
    count, indices, issues, whole_roles = 0, set(), [], {}
    carrier_flags = None
    known, observed, audit_proofs = observations(args.carrier_audit, args.plan is not None)
    if args.carrier_audit and args.plan is None:
        require(len(observed) == 1, 'Actual carrier flags differ by host; supply --plan for exact finite correspondence')
    if len(observed) == 1:
        carrier_flags = next(iter(observed))
    hosts, correspondence, seen, plan_proof = {}, [], set(), None
    if args.plan is not None:
        require(args.carrier_audit, '--plan requires exact native carrier observations')
        path = args.plan.resolve()/'plan.sqlite'
        database = sqlite3.connect(path.as_uri()+'?mode=ro', uri=True)
        try:
            database.execute('PRAGMA query_only=ON');database.execute('PRAGMA cache_size=-2048');database.execute('BEGIN')
            metadata = {key: json.loads(value) for key, value in database.execute("SELECT key,value FROM meta WHERE key IN ('binding','compositionGeneration')")}
            require(metadata.get('binding') and metadata.get('compositionGeneration'), 'Finite host report requires the completed bound plan generation')
            # One bounded bridge scan with exact placement PK lookups, rather
            # than a whole placements scan for each compiled composition key.
            for key, encoded, placements in database.execute('SELECT p.composition,b.host_state,COUNT(*) FROM bridge_cells b CROSS JOIN placements p WHERE p.dim=b.dim AND p.x=b.x AND p.y=b.y AND p.z=b.z GROUP BY p.composition,b.host_state ORDER BY p.composition,b.host_state'):
                host = state_key(json.loads(encoded))
                counts = hosts.setdefault(key, {})
                counts[host] = counts.get(host, 0)+placements
            require(database.total_changes == 0, 'Finite bridge report modified its read-only plan')
            plan_proof = {'path': str(path), 'sourceBinding': metadata['binding'], 'compositionGeneration': metadata['compositionGeneration'], 'readOnly': True}
        finally:
            database.close()
    def backgrounds():
        for row in read_lines(args.compositions):
            if row.get('wrapper') == 'NativeGridBase':
                yield row['key'], mapping[row['key']], False
        # Dedicated whole roles legitimately have no ordinary global mapping row.
        # Only actually bound unseen roles use their exact compiled template cell.
        if args.plan is not None:
            for key in sorted(set(hosts) - seen):
                if key.startswith('whole:'):
                    role = whole_role(catalog, key, mapping)
                    whole_roles[key] = role
                    yield key, role['cellIndex'], True
    for key, index, is_whole in backgrounds():
        count += not is_whole
        require(type(index) is int and 0 <= index < len(catalog['cells']), 'Invalid mapped background type index')
        indices.add(index)
        cell = catalog['cells'][index]
        optics = catalog['optics'][cell['optics']]
        if cell.get('fluid') is not None or optics['luminance'] != 0:
            issues.append({'key': key, 'typeIndex': index, 'luminance': optics['luminance'], 'fluid': cell.get('fluid')})
        compiled = (optics['opaque'], optics['sidedTransparency'], optics['luminance'], cell.get('fluid') is None)
        if args.plan is not None:
            require(key not in seen, 'Duplicate raw/whole bridge composition key');seen.add(key)
            bindings = hosts.get(key, {})
            if not bindings:
                issues.append({'key': key, 'typeIndex': index, 'kind': 'missing_actual_bridge_host_binding'})
            expected, details = set(), []
            for host, placements in sorted(bindings.items()):
                if host not in known:
                    issues.append({'key': key, 'typeIndex': index, 'kind': 'missing_exact_host_observation', 'state': json.loads(host)})
                    continue
                observation = known[host];expected.add(observation['flags'])
                details.append({'state': json.loads(host), 'placements': placements, 'observedCarrierFlags': dict(zip(FLAGS, observation['flags'])), 'observationKey': host})
                if compiled != observation['flags']:
                    issues.append({'key': key, 'typeIndex': index, 'kind': 'compiled_background_differs_from_exact_host_flags', 'state': json.loads(host), 'observed': dict(zip(FLAGS, observation['flags'])), 'compiled': dict(zip(FLAGS, compiled))})
            if len(expected) > 1:
                issues.append({'key': key, 'typeIndex': index, 'kind': 'one_composition_has_incompatible_host_bindings'})
            correspondence.append({'key': key, 'typeIndex': index, 'compiledCarrierFlags': dict(zip(FLAGS, compiled)),
                                   'hosts': details, **({'wholeRole': whole_roles[key]} if is_whole else {})})
        elif carrier_flags is not None and compiled != carrier_flags:
            issues.append({'key': key, 'typeIndex': index, 'kind': 'compiled_background_differs_from_observed_carrier_flags', 'opaque': optics['opaque'], 'sidedTransparency': optics['sidedTransparency'], 'luminance': optics['luminance'], 'fluidEmpty': cell.get('fluid') is None})
    for key in sorted(set(hosts)-seen):
        issues.append({'key': key, 'kind': 'actual_bound_bridge_composition_missing_wrapper_row'})
    report = {'status': 'PASS' if not issues else 'FAIL', 'wrapperCompositions': count, 'wholeRoleCompositions': len(whole_roles), 'uniqueBackgroundCellTypes': len(indices), 'allCompiledBackgroundsFluidEmpty': all(catalog['cells'][index].get('fluid') is None for index in indices), 'allCompiledBackgroundsLuminanceZero': all(catalog['optics'][catalog['cells'][index]['optics']]['luminance'] == 0 for index in indices), 'observedCarrierFlags': dict(zip(FLAGS, carrier_flags)) if carrier_flags is not None else None, 'allCompiledBackgroundsMatchObservedCarrierFlags': not issues if args.carrier_audit else None, 'catalogSha256': sha(args.catalog), 'compositionsSha256': sha(args.compositions), 'mappingSha256': sha(args.mapping), 'nativeAuditBindings': audit_proofs, 'finiteStateCorrespondence': {'plan': plan_proof, 'boundBridgePlacements': sum(sum(values.values()) for values in hosts.values()), 'actualHostStates': len({host for values in hosts.values() for host in values}), 'hostObservations': [{'key': host, 'state': json.loads(host), 'observedCarrierFlags': dict(zip(FLAGS, value['flags'])), 'sources': value['sources']} for host, value in sorted(known.items())], 'compositions': correspondence} if args.plan is not None else None, 'databaseWrites': 0, 'worldReads': 0, 'worldWrites': 0, 'issues': issues, 'scope': 'Compiled background check against provided prior native carrier observations; new-JAR runtime validate audit remains required'}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in report.items() if key != 'finiteStateCorrespondence'} if args.plan is not None else report, ensure_ascii=False))
    if issues:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
