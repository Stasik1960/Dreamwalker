"""Append-only family review IDs and user-authoritative decisions; no mod writes.

`init` inventories existing candidates, `catalog` renders only one batch plus
accepted POC examples. `decide` records explicit user decisions, never migrates a
world or generates runtime resources. Semantic interpretation belongs to users.
"""
from __future__ import annotations

import argparse
import copy
import gzip
import hashlib
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
LOGICAL = RES / 'bloodborne_blocks/logical'
MANIFEST = ROOT / 'docs/manual-families.json'
STATUSES = {'UNREVIEWED', 'APPROVED', 'MERGE', 'SPLIT', 'CONNECTED',
            'STATE_VARIANTS', 'NOT_OBJECT', 'DELETE', 'AMBIGUOUS'}
ACTIONS = {'OK': 'APPROVED', 'NEEDS_REVIEW': 'AMBIGUOUS',
           **{x: x for x in STATUSES - {'UNREVIEWED', 'APPROVED', 'AMBIGUOUS'}}}


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def save(path, value):
    path = Path(path)
    data = json.dumps(value, ensure_ascii=False, indent=2) + '\n'
    if not path.exists() or path.read_text(encoding='utf-8') != data:
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(path.suffix + '.tmp')
        temporary.write_text(data, encoding='utf-8')
        temporary.replace(path)


def state_key(props):
    return ','.join(f'{k}={v}' for k, v in sorted(props.items()))


def priority(definition):
    ident, semantic = definition['id'], definition['semantic']
    if semantic in ('tree', 'bush', 'plant'): return 0
    if semantic == 'container' or 'books' in ident: return 1
    if semantic in ('statue', 'lamp'): return 2
    if definition.get('behavior') in ('door', 'gate', 'shutter'): return 4
    if semantic in ('window', 'column', 'ornament', 'trim', 'roof'): return 3
    if definition.get('behavior') == 'connected' or semantic in ('fence', 'floor'): return 5
    return 6


def condition(where, props):
    if 'OR' in where: return any(condition(x, props) for x in where['OR'])
    if 'AND' in where: return all(condition(x, props) for x in where['AND'])
    return all(props.get(k) in str(v).split('|') for k, v in where.items())


def applications(blockstate, props):
    groups = []
    for key, value in blockstate.get('variants', {}).items():
        if condition(dict(x.split('=', 1) for x in key.split(',') if x), props):
            groups.append(value if isinstance(value, list) else [value])
    for row in blockstate.get('multipart', []):
        if condition(row.get('when', {}), props):
            groups.append(row['apply'] if isinstance(row['apply'], list) else [row['apply']])
    return groups


def proposed_record(definition, rules, curated, contracts, geometry, names):
    ident = definition['id']
    props = {**definition['default'], **definition.get('placement_properties', {})}
    key = state_key(props)
    selected_rules = [r for r in rules if state_key(r['target']['properties']) == key]
    row = next((r for r in curated.get(ident, {}).get('states', []) if state_key(r['properties']) == key), None)
    if row:
        apps = row['apps']
    else:
        # Reproduce the ONE authored application of the existing auto-family.
        # This is provenance for review, not new semantic grouping.
        source = (selected_rules or rules)[0]['source'] if (selected_rules or rules) else None
        if not source: raise ValueError('Missing provenance: ' + ident)
        bs = read(RES / 'assets/bloodborne_blocks/blockstates' / (source['id'] + '.json'))
        groups = applications(bs, source['properties'])
        if len(groups) != 1 or len(groups[0]) != 1:
            raise ValueError('Ambiguous auto-family preview: ' + ident)
        app = dict(groups[0][0])
        app['y'] = ('north', 'east', 'south', 'west').index(props.get('facing', 'north')) * 90
        if 'face' in props: app['x'] = {'floor': 0, 'wall': 90, 'ceiling': 180}[props['face']]
        apps = [app]
    representative = selected_rules[0] if selected_rules else (rules[0] if rules else {})
    carriers = ([{**representative['source'], 'offset': [0, 0, 0]}] if 'source' in representative else []) + representative.get('members', [])
    components = []
    for number, app in enumerate(apps, 1):
        components.append({'number': number, 'app': app,
            'source_blocks': [s for s in carriers if s.get('offset', [0, 0, 0]) == app.get('offset', [0, 0, 0])],
            'reference_kind': 'existing_legacy_provenance_not_raw_vanilla'})
    old = geometry['blocks'][ident]['states'][key]
    if 'ref' in old: old = geometry['profiles'][old['ref']]
    contract = contracts.get(ident)
    if contract:
        anchor = contract['canonical_anchor']
        collision = contract['collision_policy']
        selection = 'ONE_AUTHORED_AABB'
        rotations = contract['rotations']
        mirror = contract['mirror_policy']
    else:
        anchor = {'cell': old.get('anchor', [0, 0, 0]), 'legacy_clicked_side_dependent': True}
        collision, selection = 'LEGACY_MODEL_DERIVED', 'LEGACY_COMPOSITE_OUTLINE'
        rotations = [0, 90, 180, 270] if 'facing' in definition['properties'] else [0]
        mirror = 'LEGACY_RUNTIME_UNREVIEWED'
    result = {
        'logical_id': ident, 'registry_id': 'bloodborne_blocks:' + ident,
        'name_ru': names.get('block.bloodborne_blocks.' + ident, ident),
        'semantic_category': definition['semantic'], 'priority_group': priority(definition),
        'status': 'APPROVED' if contract else 'UNREVIEWED',
        'architecture_status': 'POC_ACCEPTED' if contract else 'LEGACY_CANDIDATE',
        'user_decision': {'action': 'OK', 'authority': 'user', 'basis': 'accepted_five_family_poc'} if contract else None,
        'canonical_anchor': anchor, 'object_type': definition.get('behavior', 'static'),
        'collision_policy': collision, 'selection_policy': selection,
        'allowed_rotations': rotations, 'mirror_policy': mirror,
        'relationships': [], 'notes': [], 'decision_history': [],
        'preview_state': key, 'preview_mesh': definition['models'][key],
        'states': definition['properties'], 'state_models': definition['models'],
        'components': components, 'legacy_rules': rules,
        'component_scope': 'Numbered model applications in preview_state; cells/polygons are not semantic parts.',
        'classification_confidence': {'level': 'USER_CONFIRMED_POC' if contract else 'PROVISIONAL',
            'basis': 'User accepted contract' if contract else 'Inherited category only; object boundaries NOT confirmed'},
        'source_patterns_v2': contract['states'] if contract else {},
    }
    return result


def reviewed_contract_skip_reason(contract):
    """Catalog B owns user-reviewed v2 provenance; legacy auto-catalog does not guess it."""
    if contract and contract.get('authority') == 'user' and contract.get('review_source_patterns'):
        return 'covered_by_catalog_b_review_source_patterns'
    return None


def oriented_contract_projection(definition, rules, contract, geometry):
    """Versioned provenance for an accepted POC that gained orientation states."""
    return {'version': 'oriented-contract-provenance-v1', 'definition': definition,
            'rules': rules, 'contract': contract,
            'geometry': geometry['blocks'].get(definition['id'])}


def north_contract_states(contract):
    strip = lambda value: ','.join(part for part in value.split(',') if part != 'facing=north')
    return {strip(state): value for state, value in contract['states'].items() if 'facing=north' in state}


def is_accepted_orientation_expansion(row, definition, own_rules, contract):
    """Accept only a north-preserving facing expansion as a one-time baseline."""
    if row.get('architecture_status') != 'POC_ACCEPTED' or not contract:
        return False
    if definition.get('default', {}).get('facing') != 'north' or 'facing' not in definition.get('properties', {}):
        return False
    strip = lambda value: ','.join(part for part in value.split(',') if part != 'facing=north')
    north_models = {strip(state): mesh for state, mesh in definition['models'].items() if 'facing=north' in state}
    north_properties = {key: value for key, value in definition['properties'].items() if key != 'facing'}
    return (row.get('states') == north_properties and row.get('state_models') == north_models and
            row.get('legacy_rules') == own_rules and row.get('source_patterns_v2') == north_contract_states(contract))


def existing_source_provenance(row, definition, rules, curated, contracts, geometry, names):
    """Return comparable legacy provenance, or a versioned oriented-POC baseline."""
    contract = contracts.get(definition['id'])
    try:
        proposed = proposed_record(definition, rules, curated, contracts, geometry, names)
        return 'legacy-proposed-v1', digest(proposed), False
    except ValueError:
        if row.get('source_projection_version') == 'oriented-contract-provenance-v1':
            return 'oriented-contract-provenance-v1', digest(oriented_contract_projection(definition, rules, contract, geometry)), False
        if not is_accepted_orientation_expansion(row, definition, rules, contract):
            raise
        return 'oriented-contract-provenance-v1', digest(oriented_contract_projection(definition, rules, contract, geometry)), True


def existing_row_has_source_drift(row, definition, rules, curated, contracts, geometry, names):
    version, fingerprint, accepted_expansion = existing_source_provenance(
        row, definition, rules, curated, contracts, geometry, names)
    if accepted_expansion and row.get('source_projection_version') != version:
        # The frozen catalog predates facings.  Record an explicit comparable
        # baseline only after proving every north state is byte-for-byte the old
        # catalog projection; future rule/contract changes then drift normally.
        row['source_projection_version'] = version
        row['source_projection_baseline_fingerprint'] = fingerprint
        return False
    baseline = (row.get('source_projection_baseline_fingerprint')
                if row.get('source_projection_version') == version else row['source_fingerprint'])
    return baseline != fingerprint


def refresh_manifest(existing=None, *, definitions=None):
    if definitions is None:
        definitions = read(LOGICAL / 'definitions.json')['blocks']
    rules = read(LOGICAL / 'migration.json')['rules']
    curated = {r['id']: r for r in read(ROOT / 'docs/logical-families-v3.json')['objects']}
    contracts = {r['id']: r for r in read(LOGICAL / 'contracts-v2.json')['families']}
    geometry = read(LOGICAL / 'geometry.json')
    names = read(RES / 'assets/bloodborne_blocks/lang/ru_ru.json')
    manifest = copy.deepcopy(existing) if existing else {
        'schema_version': 1, 'authority': 'user_decisions_override_automation',
        'source_world_sha256': '4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51',
        'batch_size': 25, 'families': [],
        'workflow': {'build_policy': 'Accumulate 20–30 approved families before JAR build; never build on review/catalog regeneration.',
                     'runtime_changes': 'Only explicitly confirmed families; catalog/decide do not modify runtime.',
                     'unassigned_pass': 'Deferred until all existing candidates are reviewed.'}}
    current = {r['logical_id']: r for r in manifest['families']}
    next_id = max([int(r['review_id'][1:]) for r in manifest['families']] + [0]) + 1
    present = {d['id'] for d in definitions}
    for old in manifest['families']:
        if old['logical_id'] not in present:
            old['source_drift'] = True
            old['current_source_fingerprint'] = None
    for definition in sorted(definitions, key=lambda d: (priority(d), d['id'])):
        if definition['id'] in current:
            # Never overwrite decisions, component numbering, proposed policies,
            # relationships or notes.  In particular, do not rebuild a legacy
            # preview from an evolved multipart/blockstate just to refresh it.
            old = current[definition['id']]
            own_rules = [rule for rule in rules if rule['target']['id'] == definition['id']]
            drift = existing_row_has_source_drift(old, definition, own_rules, curated, contracts, geometry, names)
            if drift:
                old['source_drift'] = True
                _version, fingerprint, _accepted = existing_source_provenance(
                    old, definition, own_rules, curated, contracts, geometry, names)
                old['current_source_fingerprint'] = fingerprint
            continue
        if reviewed_contract_skip_reason(contracts.get(definition['id'])):
            # The append-only user decision and reviewed source patterns live in
            # Catalog B.  Never infer a primary multipart application for it.
            continue
        proposed = proposed_record(definition, [r for r in rules if r['target']['id'] == definition['id']], curated, contracts, geometry, names)
        fingerprint = digest(proposed)
        proposed.update(review_id=f'F{next_id:03}', batch=(next_id - 1)//manifest['batch_size'] + 1,
                        source_fingerprint=fingerprint, current_source_fingerprint=fingerprint, source_drift=False)
        manifest['families'].append(proposed)
        next_id += 1
    validate(manifest)
    return manifest


def validate(manifest):
    rows = manifest['families']
    if len({r['review_id'] for r in rows}) != len(rows) or len({r['logical_id'] for r in rows}) != len(rows):
        raise ValueError('Duplicate review/logical ID')
    for row in rows:
        if not re.fullmatch(r'F\d{3,}', row['review_id']) or row['status'] not in STATUSES:
            raise ValueError('Invalid review record')
        numbers = [x['number'] for x in row['components']]
        if numbers != list(range(1, len(numbers)+1)): raise ValueError('Unstable component numbering')
        decision = row.get('user_decision') or {}
        related = decision.get('related_families')
        if related is not None and (not isinstance(related, list) or row['review_id'] not in related):
            raise ValueError('Invalid relationship decision')
    by_id = {row['review_id']: row for row in rows}
    for row in rows:
        decision = row.get('user_decision') or {}
        related = decision.get('related_families')
        if not related:
            continue
        group = set(related)
        if len(group) < 2 or not group <= set(by_id):
            raise ValueError('Invalid relationship group')
        for ident in group:
            other = by_id[ident].get('user_decision') or {}
            if other.get('action') != decision.get('action') or set(other.get('related_families', [])) != group:
                raise ValueError('Asymmetric relationship group')


def apply_decisions(manifest, text, *, replace=False):
    result = copy.deepcopy(manifest)
    by_id = {r['review_id']: r for r in result['families']}
    planned, covered = {}, set()
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith('#'): continue
        match = re.fullmatch(r'(F\d{3,})\s+(OK|MERGE|SPLIT|CONNECTED|STATE_VARIANTS|NOT_OBJECT|DELETE|NEEDS_REVIEW)(?:\s*:\s*(.*))?', line)
        if not match: raise ValueError('Expected F001 OK or F001 SPLIT: 1+2 / 3; got ' + line)
        ident, action, detail = match.groups()
        if ident not in by_id: raise ValueError('Unknown review ID: ' + ident)
        row, detail = by_id[ident], detail or ''
        decision = {'action': action, 'detail': detail, 'authority': 'user'}
        involved = [ident]
        if action in ('MERGE', 'STATE_VARIANTS'):
            refs = re.findall(r'F\d{3,}', detail)
            if action == 'MERGE' and not refs: raise ValueError('MERGE needs related review IDs')
            involved = list(dict.fromkeys([ident] + refs))
            if (action == 'MERGE' or refs) and len(involved) < 2:
                raise ValueError('Relationship requires another distinct family')
            if any(x not in by_id for x in involved): raise ValueError('Unknown relationship ID')
            if len(involved) > 1:
                decision['related_families'] = involved
        if action == 'SPLIT':
            try: groups = [[int(x.strip()) for x in group.split('+')] for group in detail.split('/')]
            except ValueError as exc: raise ValueError('SPLIT needs groups like 1+2 / 3') from exc
            flat = [x for group in groups for x in group]
            if len(groups) < 2 or sorted(flat) != [c['number'] for c in row['components']]:
                raise ValueError('SPLIT must partition all numbered components exactly once')
            decision['component_groups'] = groups
        # Parse before changing anything.  Group lines deliberately prescribe
        # the same decision for every member, so conflicting/overlapping lines
        # can be rejected atomically instead of depending on input order.
        for target in involved:
            prior = planned.get(target)
            if prior is not None and prior != decision:
                raise ValueError('Contradictory transaction instructions for ' + target)
            planned[target] = copy.deepcopy(decision)
        covered.update(involved)

    # An existing group is indivisible unless every member is named by this
    # transaction (directly or as a related ID).  This permits a full dissolve
    # such as F001 OK / F003 OK and an explicit reconstitution, but never a
    # unilateral removal that would leave stale reverse relationships.
    for ident, decision in planned.items():
        old = by_id[ident].get('user_decision')
        if old == decision:
            continue
        if old and not replace:
            raise ValueError('Existing authoritative decision for ' + ident + '; explicit --replace required')
        previous_group = set((old or {}).get('related_families', []))
        if previous_group and previous_group != set(decision.get('related_families', [])) and not previous_group <= covered:
            raise ValueError('Cannot revise only part of an authoritative relationship group: ' + ', '.join(sorted(previous_group)))

    for ident, decision in planned.items():
        old = by_id[ident]
        if old.get('user_decision') == decision:
            continue
        if old.get('user_decision'):
            old['decision_history'].append(old['user_decision'])
        old['user_decision'], old['status'] = copy.deepcopy(decision), ACTIONS[decision['action']]
        related = decision.get('related_families', [])
        old['relationships'] = ([{'type': decision['action'], 'review_id': other, 'authority': 'user'}
                                 for other in related if other != ident] if len(related) > 1 else [])
    validate(result)
    return result


def esc(value):
    return html.escape(str(value), quote=True)


def context_html(occurrence):
    examples = occurrence.get('examples', [])
    if not examples: return '<p>Контекст отсутствует: нет подтверждённого совпадения исходного шаблона.</p>'
    example = examples[0]
    cells = example.get('neighborhood', [])
    members = {tuple(c['relative']) for c in example.get('source_blocks', [])}
    diagrams = []
    # Orthogonal source-block occupancy, never semantic membership inference.
    for label, a, b, fixed in [('Сверху: X/Z, Y=0', 0, 2, 1), ('Сбоку: X/Y, Z=0', 0, 1, 2)]:
        plane = {tuple(c['relative']): c for c in cells if c['relative'][fixed] == 0}
        rows = []
        for v in range(2, -3, -1):
            line = []
            for u in range(-2, 3):
                p = [0, 0, 0]; p[a], p[b] = u, v
                cell = plane.get(tuple(p))
                state = cell.get('state', {}) if cell else {}
                block = state.get('id', 'unloaded')
                cls = 'member' if tuple(p) in members else 'air' if block.endswith(':air') else 'environment'
                line.append(f'<td class="{cls}" title="{esc(str(p)+": "+block)}">{esc(block.removeprefix("minecraft:"))}</td>')
            rows.append('<tr>' + ''.join(line) + '</tr>')
        diagrams.append(f'<div><h4>{label}</h4><table class="context">{"".join(rows)}</table></div>')
    lines = []
    for c in cells:
        state = c['state']; membership = 'pattern' if tuple(c['relative']) in members else 'environment'
        lines.append(esc(f'{c["relative"]} → {state["id"]} {state.get("properties", {})} [{membership}]'))
    return ('<p>Жёлтый — ячейка существующего source pattern; серый — окружение, не часть family. '
            'Срез ±2 блока, не полный рендер объекта.</p><div class="context-grid">' + ''.join(diagrams) +
            '</div><details><summary>Relative XYZ → source block/state</summary><pre>' + '\n'.join(lines) + '</pre></details>')


def catalog(manifest, output, batch=1, occurrences=None):
    from manual_review_render import render_family, render_contact_sheet
    output = Path(output).resolve(); output.mkdir(parents=True, exist_ok=True)
    batch_rows = [r for r in manifest['families'] if r['batch'] == batch]
    examples = [r for r in manifest['families'] if r['architecture_status'] == 'POC_ACCEPTED' and r not in batch_rows]
    if not batch_rows: raise ValueError('No such batch')
    drifted = [r['review_id'] for r in batch_rows + examples if r.get('source_drift')]
    if drifted:
        raise ValueError('Source evidence changed; reconcile without overwriting decisions before render: ' + ', '.join(drifted))
    with gzip.open(LOGICAL / 'meshes.json.gz', 'rt', encoding='utf-8') as stream: meshes = json.load(stream)
    if occurrences:
        from manual_review_world import config_hash, _body_hash, DEFAULT_SOURCEPACK, DEFAULT_VANILLA
        selected = [r for r in manifest['families'] if r['batch'] == batch or r['architecture_status'] == 'POC_ACCEPTED']
        expected_config = config_hash(selected, DEFAULT_SOURCEPACK, DEFAULT_VANILLA)
        if (occurrences.get('source', {}).get('sha256') != manifest['source_world_sha256']
                or occurrences.get('config_sha256') != expected_config
                or occurrences.get('body_sha256') != _body_hash(occurrences)):
            raise ValueError('Stale/incompatible occurrence report; rerun manual_review_world.py for this batch')
    occurrence_rows = (occurrences or {}).get('reviews', {})
    rendered, cards = {}, []
    for record in batch_rows + examples:
        rid = record['review_id']
        meta = render_family(record, meshes, output / 'images')
        rendered[rid] = meta
        occ = occurrence_rows.get(rid, {'status': 'not_scanned', 'candidate_count': None})
        count = occ.get('candidate_count')
        count_label = 'не установлено' if count is None else str(count)
        coords = ['%s %s' % (x['dimension'], x['anchor']) for x in occ.get('examples', [])]
        images = ''.join(f'<a href="{esc(Path(meta["paths"][kind]).relative_to(output).as_posix())}" target="_blank"><img loading="lazy" src="{esc(Path(meta["paths"][kind]).relative_to(output).as_posix())}" alt="{rid} {kind}"></a>' for kind in ('assembled', 'exploded'))
        component_rows = ''.join(f'<tr><td>{c["number"]}</td><td>{esc(c["app"]["model"])}</td><td>{esc(c["app"].get("offset", [0,0,0]))}</td></tr>' for c in record['components'])
        summary = {
            'Категория (предварительная)': record['semantic_category'],
            'Размер XYZ, блоки': meta['dimensions'], 'Bounds': meta['bounds'],
            'Компоненты: models / carriers': f'{len(record["components"])} / {meta["component_block_count"]}',
            'Canonical anchor (текущий)': record['canonical_anchor'],
            'Collision policy': record['collision_policy'], 'Selection policy': record['selection_policy'],
            'States': record['states'], 'Preview state': record['preview_state'],
            'Rotations / mirror': f'{record["allowed_rotations"]} / {record["mirror_policy"]}',
            'Source-world совпадения': f'{count_label} ({occ["status"]})',
            'Координаты примеров': '; '.join(coords) or '—',
            'Confidence': record['classification_confidence'],
        }
        facts = ''.join(f'<dt>{esc(k)}</dt><dd>{esc(v)}</dd>' for k, v in summary.items())
        choices = ''.join(f'<option>{a}</option>' for a in ACTIONS)
        cards.append(f'''<article id="{rid}" class="family"><header><h2>{rid} · {esc(record['name_ru'])}</h2><p>{esc(record['registry_id'])}</p>
<b>{esc(record['architecture_status'])} / {esc(record['status'])}</b></header>{images}<dl>{facts}</dl>
<details><summary>Нумерация компонентов и относительные offsets</summary><table><tr><th>№</th><th>Model</th><th>Offset XYZ</th></tr>{component_rows}</table><p>{esc(record['component_scope'])}</p></details>
<details><summary>Контекст правильной исходной карты</summary>{context_html(occ)}</details>
<label>Решение <select data-review="{rid}"><option value="">—</option>{choices}</select></label>
<label>Группы / связанные ID / заметка <input data-detail="{rid}" placeholder="1+2 / 3 или F042+F043"></label>
</article>''')
        print('catalog:', rid, record['logical_id'], flush=True)
    sheet = render_contact_sheet([rendered[r['review_id']] for r in batch_rows], output / f'batch-{batch:02}-contact.png')
    links = ' '.join(f'<a href="#{r["review_id"]}">{r["review_id"]}</a>' for r in batch_rows)
    preview_links = ' '.join(f'<a href="#{r["review_id"]}">{r["review_id"]} {esc(r["name_ru"])}</a>' for r in examples)
    template = (Path(__file__).with_name('manual_review_template.html')).read_text(encoding='utf-8')
    page = template.replace('<!--BATCH-->', str(batch)).replace('<!--COUNT-->', str(len(batch_rows)))
    page = page.replace('<!--LINKS-->', links).replace('<!--EXAMPLES-->', preview_links)
    page = page.replace('<!--SHEET-->', sheet.name).replace('<!--CARDS-->', '\n'.join(cards))
    (output / 'index.html').write_text(page, encoding='utf-8')
    save(output / 'catalog.json', {'batch': batch, 'review_ids': [r['review_id'] for r in batch_rows],
        'poc_examples': [r['review_id'] for r in examples], 'manifest_sha256': digest(manifest), 'rendered': rendered})
    return output / 'index.html'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('init', 'catalog', 'decide'))
    parser.add_argument('--manifest', type=Path, default=MANIFEST)
    parser.add_argument('--batch', type=int, default=1)
    parser.add_argument('--output', type=Path, default=ROOT / 'build/manual-review')
    parser.add_argument('--decisions', type=Path)
    parser.add_argument('--replace', action='store_true', help='Explicitly supersede existing user decisions, preserving history')
    args = parser.parse_args()
    if args.command == 'init':
        result = refresh_manifest(read(args.manifest) if args.manifest.exists() else None)
        save(args.manifest, result); print(f'{len(result["families"])} stable review records: {args.manifest}')
    elif args.command == 'decide':
        if not args.decisions: parser.error('--decisions is required')
        save(args.manifest, apply_decisions(read(args.manifest), args.decisions.read_text(encoding='utf-8-sig'), replace=args.replace))
        print('User decisions recorded. Runtime and world unchanged.')
    else:
        occ = args.output / 'occurrences.json'
        print(catalog(read(args.manifest), args.output, args.batch, read(occ) if occ.exists() else None))


if __name__ == '__main__': main()
