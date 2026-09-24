"""Read-only audit consistency checks; no game, build or conversion is launched."""
import json
import os
import zipfile
from pathlib import Path

import audit_catalog as audit
import scan_all_dimensions as census


def main():
    manifest = audit.read(audit.OUT / 'MANIFEST.json')
    summary = manifest['summary']
    models = manifest['pack_models']
    families = manifest['logical_families']
    candidates = manifest['discovery_candidates']
    assert manifest['schema_version'] == 2
    assert len(models) == len({m['model'] for m in models}) == 734
    assert len(families) == len({f['id'] for f in families}) == 298
    assert len(candidates) == len({c['review_id'] for c in candidates}) == 2249
    assert sum(f['current_registry'] for f in families) == 33
    assert summary['coverage_losses'] + summary['requested_removals'] == summary['historical_logical_missing'] == 265
    assert summary['registered_states'] - summary['states_shown'] == summary['gallery_omitted_states'] == 491
    assert sum(summary['model_absence_categories'].values()) + summary['pack_models_with_live_provenance'] == 734
    by_id = {f['id']: f for f in families}
    assert by_id['o_c654']['semantic_successor_ids'] == ['o_c654_a', 'o_c654_b']
    assert by_id['o_c654']['migration_replacement']['target_ids'] == ['o_c654_a']
    assert by_id['o_c654']['migration_replacement']['manual_only_successor_ids'] == ['o_c654_b']
    assert by_id['o_c008_4']['migration_replacement']['property_transform']['facing_delta_degrees'] == -90

    frozen = audit.read(audit.ROOT / 'docs/production-authoring-inputs.json.gz')
    current = audit.read(audit.ROOT / 'src/main/resources/bloodborne_blocks/logical/definitions.json')
    live_meshes = audit.read(audit.ROOT / 'src/main/resources/bloodborne_blocks/logical/meshes.json.gz')
    aliases = audit.read(audit.ROOT / 'docs/production-resource-pruning.json')['texture_aliases']
    old_blocks = {b['id']: b for b in frozen['definitions']['blocks']}
    geometry_retention = []
    for block in current['blocks']:
        ident = block['id']
        if ident == 'o_c282':
            geometry_retention.append({'id': ident, 'result': 'EXPECTED_RECOMPILE',
                'reason': 'latest production correction deliberately replaced two leaf IDs with one double-door object'})
            continue
        mismatches = []
        for state, mesh in block['models'].items():
            old_mesh = frozen['meshes'][old_blocks[ident]['models'][state]]
            normalized = {'polygons': [{'texture': aliases.get(p['texture'], p['texture']), 'vertices': p['vertices']}
                                       for p in old_mesh['polygons']]}
            if normalized != live_meshes[mesh]:
                mismatches.append(state)
        geometry_retention.append({'id': ident, 'result': 'PASS' if not mismatches else 'MISMATCH',
            'compared_states': len(block['models']), 'mismatches': mismatches,
            'comparison': 'all textured vertices after exact-content texture aliases; provenance metadata omitted'})
    assert not any(row['result'] == 'MISMATCH' for row in geometry_retention), geometry_retention
    audit.write('retained-geometry-check.json', {
        'baseline_commit': frozen['baseline_commit'], 'families': geometry_retention,
        'does_not_prove': 'correctness of the original manual partition, gameplay, in-city migration or visual acceptance'})

    full = audit.read(audit.OUT / 'world-state-usage.json.gz')
    previous = audit.read(audit.ROOT / 'docs/production-source-usage.json.gz')
    def state_key(row):
        return row['id'], tuple(sorted(row['properties'].items()))
    yharnam = {state_key(r): r['dimensions']['eh_s2:yharnam']['count'] for r in full['states'] if 'eh_s2:yharnam' in r['dimensions']}
    old = {state_key(r): sum(v['count'] for v in r['positional_variants']) for r in previous['states']}
    assert yharnam == old, 'all-dimension census differs from previous full Yharnam histogram'

    # Check that the census carrier universe includes every vanilla blockstate
    # that can reach a model supplied by this pack, including vanilla parents.
    vanilla = Path(os.environ['BLOODBORNE_VANILLA_JAR'])
    resolved_models, blockstates, pack_model_ids = {}, {}, set()
    for path in (vanilla, census.PACK):
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                if not name.startswith('assets/minecraft/') or not name.endswith('.json'):
                    continue
                if '/models/' not in name and '/blockstates/' not in name:
                    continue
                obj = json.JSONDecoder().raw_decode(z.read(name).decode('utf-8-sig').lstrip())[0]
                if '/models/' in name:
                    mid = audit.model_id(name)
                    resolved_models[mid] = obj
                    if path == census.PACK:
                        pack_model_ids.add(mid)
                else:
                    blockstates['minecraft:' + Path(name).stem] = obj
    def closure(names):
        seen, todo = set(), list(names)
        while todo:
            name = todo.pop()
            if name in seen:
                continue
            seen.add(name)
            parent = resolved_models.get(name, {}).get('parent')
            if parent:
                todo.append(parent if ':' in parent else 'minecraft:' + parent)
        return seen
    carriers = census.carrier_universe()
    outside = {k: sorted(closure(audit.refs(v)) & pack_model_ids) for k, v in blockstates.items()
               if k not in carriers and closure(audit.refs(v)) & pack_model_ids}
    assert not outside, 'pack-dependent carriers omitted by census: ' + str(outside)
    expected_used = closure({m['model'] for m in full['model_usage']}) & pack_model_ids
    reported_used = {m['model'] for m in models if m['world_use_including_parent_by_dimension']}
    assert expected_used == reported_used, 'world model dependency mismatch: ' + str(expected_used ^ reported_used)

    checked = 0
    with zipfile.ZipFile(audit.JAR) as z:
        assert z.testzip() is None
        for path in (audit.ROOT / 'src/main/resources').rglob('*'):
            if not path.is_file():
                continue
            relative = path.relative_to(audit.ROOT / 'src/main/resources').as_posix()
            if relative == 'fabric.mod.json':
                source = json.loads(path.read_bytes())
                shipped = json.loads(z.read(relative))
                source['version'] = shipped['version']  # Gradle expands only this template field.
                assert source == shipped
            elif relative == 'bloodborne_blocks.mixins.json':
                source = json.loads(path.read_bytes())
                shipped = json.loads(z.read(relative))
                assert shipped.pop('refmap') == 'bloodborne-blocks-refmap.json'
                assert 'bloodborne-blocks-refmap.json' in z.namelist()
                assert source == shipped  # Loom injects the refmap at build time.
            else:
                assert z.read(relative) == path.read_bytes(), 'different shipped resource: ' + relative
            checked += 1

    assert audit.protected_inputs() == manifest['read_only_proof']['before'] == manifest['read_only_proof']['after']
    audit.write('verification.json', {
        'result': 'PASS', 'manifest_schema': 2, 'checked_live_resources_against_shipped_jar': checked,
        'jar_crc': 'PASS', 'all_pack_model_dependent_blockstates_in_census_universe': True,
        'carrier_universe_size': len(carriers), 'omitted_pack_dependent_carriers': outside,
        'all_2605_yharnam_state_counts_match_previous_census': True,
        'world_model_dependencies_match_vanilla_and_pack_parent_graph': True,
        'retained_families_geometry_exactly_matches_baseline': 32,
        'intentional_recompiled_double_door': 'o_c282',
        'protected_inputs_unchanged': True,
        'visual_client_check': 'NOT_RUN', 'build': 'NOT_RUN', 'conversion': 'NOT_RUN',
        'checks': ['asset/family/candidate cardinality and unique IDs', 'disjoint missing categories',
                   'C654 manual-only B vs source A', 'C008_4 facing transform', '491 omitted states',
                   'actual MCA roots checked by audit_catalog.py', 'pack carrier reachability through vanilla parents',
                   'current resources byte-match JAR except fabric.mod.json version expansion and Loom mixin refmap injection'],
    })
    print(json.dumps(audit.read(audit.OUT / 'verification.json'), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
