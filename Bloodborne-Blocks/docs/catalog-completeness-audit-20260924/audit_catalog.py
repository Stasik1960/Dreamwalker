"""Audit-only crosswalk. Writes reports in this directory, never runtime/world files.

Models, source cells, discovery candidates and approved families are different units.
Source-model lineage is not a proof that every polygon survives a manual split.
"""
from __future__ import annotations
import collections
import gzip
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from world_io import RegionFile, compound, child, TAG_COMPOUND, TAG_LIST, TAG_LONG_ARRAY, unpack_palette_indices


def read(path):
    path = Path(path)
    raw = path.read_bytes()
    return json.loads(gzip.decompress(raw) if path.suffix == '.gz' else raw)


def write(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def protected_inputs():
    paths = [p for folder in ('src', 'tools') for p in (ROOT / folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts]
    digest = hashlib.sha256()
    for p in sorted(paths):
        digest.update(p.relative_to(ROOT).as_posix().encode() + b'\0' + bytes.fromhex(sha(p)))
    return {'runtime_and_tools_tree': digest.hexdigest(), 'file_count': len(paths),
            'source_world': sha(ROOT / 'reference-inputs/source-world.zip'),
            'source_pack': sha(ROOT / 'reference-inputs/source-resource-pack.zip'),
            'vanilla_client': sha(VANILLA),
            'jar': sha(JAR), 'gallery': sha(GALLERY)}


def refs(value):
    result = set()
    if isinstance(value, dict):
        for k, v in value.items():
            if k in ('model', 'parent') and isinstance(v, str):
                name = v if ':' in v else 'minecraft:' + v
                # Historic importer copied source paths to its own namespace.
                result.add(name.replace('bloodborne_blocks:', 'minecraft:', 1))
            else:
                result.update(refs(v))
    elif isinstance(value, list):
        for v in value:
            result.update(refs(v))
    return result


def source_ids(value):
    result = set()
    if isinstance(value, dict):
        if isinstance(value.get('id'), str) and value['id'].startswith('minecraft:'):
            result.add(value['id'])
        for v in value.values():
            result.update(source_ids(v))
    elif isinstance(value, list):
        for v in value:
            result.update(source_ids(v))
    return result


def model_id(path):
    bits = path.split('/')
    return bits[1] + ':' + '/'.join(bits[3:])[:-5]


def historic_blockstates(ids, commit):
    """One read-only git batch, not 503 subprocesses or a checkout of old resources."""
    ids = sorted(ids)
    queries = [f'{commit}:Bloodborne-Blocks/src/main/resources/assets/bloodborne_blocks/blockstates/{i}.json' for i in ids]
    raw = subprocess.check_output(['git', 'cat-file', '--batch'], input=('\n'.join(queries)+'\n').encode(), cwd=ROOT)
    stream = io.BytesIO(raw); result = {}
    for ident in ids:
        header = stream.readline().decode().strip().split()
        if header[-1] == 'missing':
            continue
        size = int(header[-1]); result[ident] = json.loads(stream.read(size)); assert stream.read(1) == b'\n'
    return result


def active_models(blockstate, state):
    def matches(condition):
        if 'AND' in condition: return all(matches(c) for c in condition['AND'])
        if 'OR' in condition: return any(matches(c) for c in condition['OR'])
        return all(state.get(k) in str(v).split('|') for k,v in condition.items())
    result = set()
    for key,value in blockstate.get('variants', {}).items():
        if matches(dict(piece.split('=',1) for piece in key.split(',') if piece)):
            result.update(refs(value))
    for part in blockstate.get('multipart', []):
        if matches(part.get('when', {})): result.update(refs(part['apply']))
    return result


def gallery_inventory():
    with zipfile.ZipFile(GALLERY) as z:
        metadata = json.loads(z.read(next(n for n in z.namelist() if n.endswith('/production-gallery.json'))))
        actual = {}
        counts = collections.Counter()
        for name in z.namelist():
            if '/region/' not in name or not name.endswith('.mca'):
                continue
            for stored in RegionFile(z.read(name)).chunks():
                data = compound(stored.nbt().root)
                cx, cz = int(data['xPos'].value), int(data['zPos'].value)
                for section in data['sections'].value:
                    sec = compound(section)
                    bs = child(section, 'block_states', TAG_COMPOUND)
                    if not bs:
                        continue
                    palette = child(bs, 'palette', TAG_LIST)
                    entries = [compound(p) for p in palette.value]
                    if not any(str(p['Name'].value).startswith('bloodborne_blocks:') for p in entries):
                        continue
                    packed = child(bs, 'data', TAG_LONG_ARRAY)
                    values = unpack_palette_indices(packed.value if packed else [], len(entries))
                    for at, index in enumerate(values):
                        row = entries[index]; name = str(row['Name'].value)
                        if not name.startswith('bloodborne_blocks:'):
                            continue
                        position = (cx * 16 + (at & 15), int(sec['Y'].value) * 16 + (at >> 8), cz * 16 + ((at >> 4) & 15))
                        props = {k: str(v.value) for k, v in compound(row['Properties']).items()} if 'Properties' in row else {}
                        actual[position] = (name.split(':', 1)[1], props); counts[name] += 1
        for item in metadata['positions']:
            assert actual[tuple(item['position'])] == (item['id'], item['properties']), 'gallery metadata differs from actual MCA'
        return metadata, counts


def main():
    before = protected_inputs()
    frozen = read(ROOT / 'docs/production-authoring-inputs.json.gz')
    old_blocks = {b['id']: b for b in frozen['definitions']['blocks']}
    current = read(ROOT / 'src/main/resources/bloodborne_blocks/logical/definitions.json')
    blocks = {b['id']: b for b in current['blocks']}
    contracts = {f['id']: f for f in read(ROOT / 'src/main/resources/bloodborne_blocks/logical/contracts-v2.json')['families']}
    old_contracts = {f['id']: f for f in frozen['contracts']['families']}
    production = read(ROOT / 'docs/production-logical-palette.json')
    selected = {b['id']: b for b in production['objects']}
    exclusions = {b['id']: b for b in production['excluded']}
    discovery = read(ROOT / 'docs/manual-source-assemblies.json')
    candidates = discovery['candidates']
    old_authoring = {x['id']: x for x in read(ROOT / 'docs/logical-families-v3.json')['objects']}
    source_mapping = read(ROOT / 'docs/pre-production-source-mapping.json.gz')
    legacy = source_mapping['definitions.json']['blocks']
    legacy_by_id = {b['id']:b for b in legacy}
    legacy_states = historic_blockstates(legacy_by_id, frozen['baseline_commit'])
    migration_by_family = collections.defaultdict(list)
    for rule in frozen['migration']['rules']:
        migration_by_family[rule['target']['id']].append(rule)
    usage = read(ROOT / 'docs/production-source-usage.json.gz')
    full_world = read(OUT / 'world-state-usage.json.gz')
    assert full_world['source_sha256'] == before['source_world'] == production['source_world_sha256']
    assert before['source_pack'] == usage['pack_sha256'] == production['source_pack_sha256']
    gallery, placed = gallery_inventory()
    gallery_rows = {r['id']: r for r in gallery['positions']}
    assert set(gallery_rows) == set(blocks) == set(selected)
    with zipfile.ZipFile(JAR) as z:
        shipped = {b['id'] for b in json.loads(z.read('bloodborne_blocks/logical/definitions.json'))['blocks']}
        assert shipped == set(blocks), 'local JAR has a different palette'
    with zipfile.ZipFile(ROOT / 'reference-inputs/source-resource-pack.zip') as z:
        model_paths = sorted(n for n in z.namelist() if '/models/' in n and n.endswith('.json'))
        models = {}; parse_notes = {}
        for n in model_paths:
            text = z.read(n).decode('utf-8-sig')
            try:
                models[model_id(n)] = json.loads(text)
            except json.JSONDecodeError:
                value, end = json.JSONDecoder().raw_decode(text.lstrip())
                tail = text.lstrip()[end:]
                if re.sub(r'//[^\n]*|/\*[\s\S]*?\*/', '', tail).strip():
                    raise
                models[model_id(n)] = value
                parse_notes[model_id(n)] = 'valid root JSON followed by comments; audit reads root, does not rewrite original pack'
        pack_states = {n.split('/')[1] + ':' + Path(n).stem: json.loads(z.read(n)) for n in z.namelist() if '/blockstates/' in n and n.endswith('.json')}

    # A source application may use a vanilla model that inherits a pack model.
    # Resolving only parents of files physically present in the pack misses these.
    with zipfile.ZipFile(VANILLA) as vanilla:
        parent_models = {model_id(n): json.loads(vanilla.read(n)) for n in vanilla.namelist()
                         if n.startswith('assets/minecraft/models/') and n.endswith('.json')}
    parent_models.update(models)

    def closure(names):
        found, queue = set(), list(names)
        while queue:
            name = queue.pop()
            if name in found:
                continue
            found.add(name)
            parent = parent_models.get(name, {}).get('parent')
            if parent:
                queue.append(parent if ':' in parent else 'minecraft:' + parent)
        return found

    world_models = collections.defaultdict(collections.Counter)
    # This uses actual selected weighted applications, not merely possibilities.
    for row in full_world.get('model_usage') or []:
        for dimension, value in row['dimensions'].items():
            count = value['count'] if isinstance(value, dict) else value
            world_models[row['model']][dimension] += count
    if not world_models:
        # A full-state-only scanner cannot prove other-dimension weighted selection.
        for state in usage['states']:
            for variant in state['positional_variants']:
                for app in variant['apps']:
                    world_models[app['model']]['eh_s2:yharnam'] += variant['count']
    inherited = collections.defaultdict(collections.Counter)
    for model, dimensions in world_models.items():
        for parent in closure([model]):
            inherited[parent].update(dimensions)
    state_counts = collections.defaultdict(collections.Counter)
    for row in full_world['states']:
        for dimension, value in row['dimensions'].items():
            state_counts[row['id']][dimension] += value['count']

    # Explicit lineage only; no semantic inference from mesh/name similarity.
    lineage = {}
    for ident in old_blocks:
        family = contracts.get(ident, old_contracts.get(ident))
        names = refs(family if family else old_authoring.get(ident, {}))
        for rule in migration_by_family[ident]:
            for part in [rule.get('source', {})] + (rule.get('members') or []):
                sid = part.get('id')
                state = {**legacy_by_id.get(sid, {}).get('default', {}), **part.get('properties', {})}
                names.update(active_models(legacy_states.get(sid, {}), state))
        lineage[ident] = closure(names) & set(models)
    model_old = collections.defaultdict(list); model_live = collections.defaultdict(list)
    for ident, names in lineage.items():
        for name in names:
            model_old[name].append(ident)
            if ident in blocks:
                model_live[name].append(ident)
    carrier_model_refs = collections.defaultdict(list)
    for carrier, data in pack_states.items():
        for name in closure(refs(data)) & set(models):
            carrier_model_refs[name].append(carrier)

    regressions = []
    family_rows = []
    not_mentioned = {'o_c003','o_c046','o_c1319','o_c474','o_c561','o_cases_0','o_iron_gate','o_iron_railing'}
    for ident, old in sorted(old_blocks.items()):
        live = ident in blocks
        status = selected[ident]['status'] if live else exclusions[ident]['status']
        old_rules = migration_by_family[ident]
        source_carriers = source_ids(contracts.get(ident, old_contracts.get(ident, {})))
        for rule in old_rules:
            for component in [rule.get('source', {})] + (rule.get('members') or []):
                if component.get('id'):
                    source_carriers.add('minecraft:' + component['id'].split(':')[-1])
        if old.get('source') and old.get('source') != 'minecraft:stone':
            source_carriers.add(old.get('source', 'unknown'))
        replacements = []
        if not live:
            if status == 'REMOVED_DUPLICATE': replacements = ['o_c008_2']
            elif ident in ('o_c001_a','o_c001_b','o_c009_a','o_c009_b','o_dead_tree_planter'): replacements = ['o_c001']
            elif ident in ('o_c282_a','o_c282_b'): replacements = ['o_c282']
            elif status == 'REMOVED_SUPERSEDED': replacements = sorted(x for x in blocks if x.startswith(ident + '_'))
        category = None if live else {
            'UNRESOLVED': 'unresolved_ambiguous',
            'REMOVED_SUPERSEDED': 'requested_split',
            'REMOVED_FRAGMENT': 'fragment_merged',
            'REMOVED_DUPLICATE': 'duplicate_deduplicated',
            'REMOVED_COMPATIBILITY': 'compatibility_removed',
        }[status]
        # Semantic successors are NOT aliases for migration of old modded worlds.
        # The current runtime deliberately has empty old-logical migration rules.
        migration_replacement = None
        if replacements:
            targets = [i for i in replacements if any(s['migration_source_pattern'] for s in contracts[i]['states'].values())]
            migration_replacement = {
                'scope': 'corresponding authoritative vanilla source patterns, NOT an old registry-ID alias',
                'target_ids': targets,
                'manual_only_successor_ids': sorted(set(replacements)-set(targets)),
                'legacy_modded_world_migration_supported': False,
                'conditions': 'exact compiled source pattern + guards + atomic occupancy checks; not rerun in this audit',
                'compiled_rule_locations': [f'contracts-v2.json/families/{i}/states/*/migration_source_pattern' for i in targets],
            }
            if ident == 'o_c008_4':
                migration_replacement['property_transform'] = {'facing_delta_degrees': -90,
                    'north': 'west', 'east': 'north', 'south': 'east', 'west': 'south'}
            if ident == 'o_c654':
                assert targets == ['o_c654_a']
                migration_replacement['explanation'] = 'User selected window A for old source placements; B has zero migration_source_pattern entries and is manual-only.'
        behavior = old.get('behavior', 'static')
        reason = 'one canonical BASE/closed/default specimen per production ID' if live else exclusions[ident]['reason']
        if status == 'UNRESOLVED' and ident not in old_contracts:
            reason = 'Excluded by set(frozen Contract V2 families); no per-family source/visual adjudication in production selector.'
        row = {'id': ident, 'previously_registered': True, 'previously_visible': ident not in frozen['hidden'],
               'previous_behavior': behavior, 'previous_contract_v2': ident in old_contracts,
               'current_registry': live, 'shipped_jar': ident in shipped, 'gallery': ident in gallery_rows,
               'status': status, 'absence_category': category, 'reason': reason,
               'semantic_successor_ids': replacements, 'migration_replacement': migration_replacement,
               'source_model_lineage': sorted(lineage[ident]), 'source_carrier_cells_by_dimension': dict(sum((state_counts[c] for c in source_carriers), collections.Counter())),
               'source_carriers': sorted(source_carriers), 'source_occurrences': selected[ident].get('source_occurrences') if live else None,
               'placeholder_source_ignored': old.get('source') == 'minecraft:stone',
               'source_count_warning': 'carrier/model counts do not prove semantic assembly occurrences; absent old exact rules were not rerun',
               'previous_migration_rule_count': len(old_rules),
               'visual_acceptance': 'NOT_VERIFIED',
               'nightmare_visual_notes': 'NOT_MENTIONED' if ident in not_mentioned else 'consult per-review corrections; not equivalent to visual PASS'}
        if live:
            row['gallery_position'] = gallery_rows[ident]['position']
            row['gallery_state'] = gallery_rows[ident]['state']
            row['registered_states'] = len(blocks[ident]['states'])
            row['states_not_shown'] = len(blocks[ident]['states']) - 1
            row['not_shown_categories'] = ['hidden_alt_variant', 'filtered_out_orientation_gameplay_or_visual_variants']
        elif status == 'UNRESOLVED':
            regressions.append({'id': ident, 'kind': 'runtime_catalog_coverage_removed', 'category': 'regression_lost',
                                'intentional_filter': True, 'functional': behavior != 'static', 'previous_behavior': behavior,
                                'source_model_lineage': row['source_model_lineage'], 'previous_gameplay_correctness': 'NOT_ESTABLISHED_BY_PRESENCE',
                                'cause': 'non-V2 family omitted by production selection; not an unexpected packaging loss',
                                'fix_not_authorized_in_this_audit': True})
        family_rows.append(row)

    model_rows = []
    for name, data in sorted(models.items()):
        live = sorted(model_live[name]); old = sorted(model_old[name]); observed = dict(inherited[name])
        if live:
            category = None; explanation = 'linked through live contract/source provenance; does not prove all original polygons or all variants are shown'
        elif name.split(':', 1)[1].startswith('item/'):
            category = 'other'; explanation = 'item-model asset; block/world gallery is not an item inventory audit'
        elif not observed and full_world.get('model_usage'):
            category = 'not_present_in_source_world'; explanation = 'no actual blockstate application or parent use in scanned terrain; entity/item/structure uses are outside scope'
        elif any(exclusions.get(i, {}).get('status') == 'UNRESOLVED' for i in old):
            category = 'unresolved_ambiguous'; explanation = 'only excluded historical logical lineage; no production target'
        elif old:
            category = 'legacy_removed'; explanation = 'historical logical lineage retired without current source-model lineage'
        else:
            category = 'filtered_out'; explanation = 'no explicit production-family lineage; source asset was never promoted by the current V2-only selector'
        model_rows.append({'model': name, 'asset_kind': name.split(':', 1)[1].split('/')[0],
                           'parse_note': parse_notes.get(name),
                           'parent': data.get('parent'), 'own_elements': len(data.get('elements', [])),
                           'pack_blockstate_carriers': sorted(carrier_model_refs[name]),
                           'direct_world_applications_by_dimension': dict(world_models[name]),
                           'world_use_including_parent_by_dimension': observed,
                           'old_logical_lineage': old, 'production_lineage': live,
                           'gallery_family_lineage': sorted(set(live) & set(gallery_rows)),
                           'complete_render_export': 'NOT_PROVEN' if live else 'NO_EXPLICIT_PRODUCTION_LINEAGE',
                           'absence_category': category, 'reason': explanation})

    by_review = collections.defaultdict(list)
    for ident, item in selected.items():
        for review in item['source_reviews']: by_review[review].append(ident)
    candidate_rows = []
    for c in candidates:
        review = c['review_id']; alias = c.get('variant_of'); live = sorted(by_review.get(alias or review, []))
        candidate_rows.append({'review_id': review, 'status': c['status'], 'variant_of': alias,
                               'production_ids': live, 'gallery_ids': live, 'boundary_flags': c.get('boundary_flags', []),
                               'source_pattern_count': len(c.get('source_patterns', [])), 'historical_pattern_match_count': c.get('similar_count'),
                               'source_models': sorted(refs(c.get('source_patterns', []))),
                               'absence_category': None if live else 'unresolved_ambiguous' if c['status'] == 'UNRESOLVED_VISUAL' else 'filtered_out',
                               'reason': 'latest production review association, not historical manual decision alone' if live else 'not included in reviewed production subset; candidate is not a confirmed object'})

    omitted_states = []
    for ident, block in sorted(blocks.items()):
        shown = gallery_rows[ident]['state']
        for state in block['states']:
            if state == shown:
                continue
            props = dict(part.split('=', 1) for part in state.split(','))
            omitted_states.append({'id': ident, 'state': state,
                'runtime_mesh': block['models'][state], 'current_registry': True, 'gallery_specimen': False,
                'absence_category': 'hidden_alt_variant' if props.get('visual') == 'alt' else 'filtered_out',
                'explanation': 'gallery generator places one canonical BASE/closed/default specimen per ID, not all states',
                'different_from_gallery_properties': {k: v for k, v in props.items() if gallery_rows[ident]['properties'].get(k) != v}})

    retired_layers = []
    for label, mapping_key in [('legacy_carriers', 'definitions.json'), ('modular_fragments', 'v2\\definitions.json')]:
        ids = sorted(b['id'] for b in source_mapping[mapping_key]['blocks'])
        assert not set(ids) & set(blocks)
        retired_layers.append({'layer': label, 'count': len(ids), 'registry_ids': ids,
            'current_registry': False, 'gallery': False, 'absence_category': 'legacy_removed',
            'source_mapping': 'docs/pre-production-source-mapping.json.gz::' + mapping_key,
            'reason': 'entire mechanical carrier/modular registry layer retired by production cleanup; mapping preserved as offline source evidence',
            'semantic_object_count': None,
            'warning': 'not independent logical families; do not add to model/family/discovery counts'})

    diagnostics = [
        {'id': 'AUDIT_PROVENANCE_OVERCLAIM', 'severity': 'medium', 'kind': 'reporting_regression',
         'families': sorted(not_mentioned), 'finding': 'Every production row claims NightmareRunning QA/latest corrections, including eight explicitly NOT_MENTIONED families. Semantic approval and visual acceptance are different.'},
        {'id': 'AUDIT_CIRCULAR_COVERAGE_GATES', 'severity': 'medium', 'kind': 'coverage_gap',
         'finding': 'Gallery and registry tests validate the same generated subset; they cannot detect omitted source-pack families. Per-review counts cannot detect a wrong same-count ID substitution.'},
        {'id': 'AUDIT_RECIPE_VS_LIVE_RESOURCE', 'severity': 'medium', 'kind': 'coverage_gap',
         'finding': 'Recipe unit tests do not automatically prove those exact component partitions/polygons are shipped. Visual acceptance remains unverified.'},
    ]
    summary = {'gallery_type': 'PRODUCTION_SUBSET_ONE_CANONICAL_STATE_PER_ID_NOT_FULL_RESOURCE_PACK_CATALOG',
               'pack_model_assets': len(models), 'pack_model_kinds': dict(collections.Counter(r['asset_kind'] for r in model_rows)),
               'pack_blockstate_files': len(pack_states),
               'world_model_asset_usage_scope': full_world.get('model_usage_status', 'all scanned dimensions actual selected applications' if full_world.get('model_usage') else 'Yharnam actual variants only; other dimensions state histogram'),
               'pack_models_observed_as_block_app_or_parent': sum(bool(r['world_use_including_parent_by_dimension']) for r in model_rows),
               'pack_models_with_live_provenance': sum(bool(r['production_lineage']) for r in model_rows),
               'pack_models_without_live_provenance': sum(not r['production_lineage'] for r in model_rows),
               'model_absence_categories': dict(collections.Counter(r['absence_category'] for r in model_rows if r['absence_category'])),
               'previous_logical_families': len(old_blocks), 'registry_logical_ids': len(blocks), 'gallery_specimens': len(gallery_rows),
               'registered_states': sum(len(b['states']) for b in blocks.values()), 'states_shown': len(gallery_rows),
               'runtime_meshes': len(read(ROOT / 'src/main/resources/bloodborne_blocks/logical/meshes.json.gz')),
               'gallery_distinct_meshes': len({blocks[i]['models'][r['state']] for i,r in gallery_rows.items()}),
               'historical_logical_missing': sum(not r['current_registry'] for r in family_rows),
               'coverage_losses': len(regressions),
               'requested_removals': sum(not r['current_registry'] and r['status'] != 'UNRESOLVED' for r in family_rows),
               'historical_missing_categories': dict(collections.Counter(r['absence_category'] for r in family_rows if r['absence_category'])),
               'gallery_omitted_states': len(omitted_states),
               'retired_mechanical_registry_layers': {r['layer']: r['count'] for r in retired_layers},
               'functional_families_removed': sum(r['functional'] for r in regressions),
               'catalog_candidate_rows': len(candidates), 'candidate_status_counts': dict(collections.Counter(r['status'] for r in candidate_rows)),
               'candidate_rows_linked_to_production': sum(bool(r['production_ids']) for r in candidate_rows),
               'semantic_object_total_in_resource_pack': None,
               'semantic_total_reason': 'pack contains model assets/fragments/templates, not an authoritative complete semantic-object list',
               'all_missing_objects_claim': 'complete within enumerated asset/family/candidate universes, not an invented semantic-object universe'}
    after = protected_inputs(); assert before == after, 'audit mutated protected artifacts'
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    write('MANIFEST.json', {'schema_version': 2, 'audited_commit': commit, 'historical_comparison_commit': frozen['baseline_commit'],
                          'read_only_proof': {'before': before, 'after': after},
                          'summary': summary, 'world_scan_summary': {k:v for k,v in full_world.items() if k not in ('states','model_usage')},
                          'pack_models': model_rows, 'logical_families': family_rows, 'discovery_candidates': candidate_rows,
                          'gallery_actual_mca_counts': dict(placed),
                          'supplementary_crosswalks': ['gallery-omitted-states.json', 'retired-registry-layers.json.gz'], 'diagnostics': diagnostics})
    write('gallery-omitted-states.json', {'omitted_count': len(omitted_states), 'states': omitted_states})
    (OUT / 'retired-registry-layers.json.gz').write_bytes(gzip.compress(json.dumps(retired_layers, sort_keys=True, separators=(',', ':')).encode(), mtime=0))
    write('missing-families.json', {'units_must_not_be_added_together': True,
          'historical_logical_families': [r for r in family_rows if not r['current_registry']],
          'discovery_candidates': [r for r in candidate_rows if not r['production_ids']],
          'pack_model_assets_without_live_lineage': [r for r in model_rows if not r['production_lineage']]})
    write('regressions.json', {'definition': 'loss of previously registered catalog coverage; intentional exclusion does not prove previous functional correctness',
                             'coverage_losses': regressions, 'reporting_and_test_gaps': diagnostics})
    write('unresolved-problem-families.json', {'unresolved_excluded': [r for r in family_rows if r['status']=='UNRESOLVED'],
          'unresolved_status_caveat': 'production selector labels non-V2 exclusions UNRESOLVED; this is not proof of a newly diagnosed geometric ambiguity in each family',
          'unresolved_visual_discovery_candidates': [r for r in candidate_rows if r['status']=='UNRESOLVED_VISUAL'],
          'explicitly_not_mentioned_in_nightmare_visual_review': sorted(not_mentioned),
          'retained_mount_plane_ambiguity': ['o_wall_deco_1', 'o_c654_a', 'o_c654_b'],
          'all_production_visual_acceptance': 'NOT_VERIFIED', 'diagnostics': diagnostics,
          'source_placement_conflicts': ['C282 foreign panes', 'C654 broad wall panel vs foreign context', 'C1491 historical barrier conflict; not retested by conversion this audit'],
          'unidentified': ['NightmareRunning image9 fence: NEEDS_USER_DEBUG']})
    print(json.dumps(summary, ensure_ascii=False, indent=2))


JAR = ROOT.parent / 'releases/Bloodborne-Blocks/bloodborne-blocks-2.1.0-alpha.1-production-palette-20260924-mc1.20.1.jar'
GALLERY = ROOT.parent / 'releases/Bloodborne-Blocks/production-palette-gallery-ready-20260924.zip'
VANILLA = Path(os.environ.get('BLOODBORNE_VANILLA_JAR', Path.home() / '.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'))
if __name__ == '__main__':
    main()
