"""Catalog B v2: full Yharnam source inventory, bounded canonical review batch.

No runtime resources or worlds are written. Exact source patterns are separate
from geometry-based, explicitly provisional review-family grouping.
"""
from __future__ import annotations

import copy
import gzip
import functools
import hashlib
import itertools
import json
import math
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

from manual_review_world import _load_blockstates, DEFAULT_SOURCEPACK, DEFAULT_VANILLA, EXPECTED_SHA256, EXPECTED_PACK_SHA256, _sha256
from source_assembly_review import ROOT, SourceReader, clusters, fingerprint, normalized, dump
from source_assembly_index import build_index, positions
from source_assembly_signatures import descriptor, equivalent
from source_assembly_visuals import source_model

VERSION = 'source-assembly-pipeline-v2.1'
PRIORITY = ['tree','grave','books','luggage','crate','bag','statue','lantern','column','window','spire','door_gate','other_prefab','connected','architecture']
LABELS = dict(zip(PRIORITY,['Дерево','Могила / мемориал','Книги','Багаж','Ящики / ёмкости','Мешки','Статуя','Фонарь / фонарный столб','Колонна / столб','Окно','Шпиль','Дверь / ворота','Другой декор','Соединяемый элемент','Архитектурный материал']))


def stable_hash(value):
    return hashlib.sha256(json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()


def exact_signature(cells):
    """Translation only: no hypothetical vanilla rotation may weaken migration."""
    return stable_hash(normalized(cells,0))


def active_choices(raw, props):
    def condition(where):
        if 'OR' in where: return any(condition(v) for v in where['OR'])
        if 'AND' in where: return all(condition(v) for v in where['AND'])
        return all(props.get(k) in str(v).split('|') for k,v in where.items())
    groups = []
    for key,value in raw.get('variants',{}).items():
        if condition(dict(p.split('=',1) for p in key.split(',') if '=' in p)):
            groups.append([[copy.deepcopy(app)] for app in (value if isinstance(value,list) else [value])])
    for part in raw.get('multipart',[]):
        if condition(part.get('when',{})):
            value = part['apply']; groups.append([[copy.deepcopy(app)] for app in (value if isinstance(value,list) else [value])])
    return groups


def component(state, relative, number, blockstates):
    name, pairs = state
    groups = active_choices(blockstates.get(name,{}),dict(pairs))
    for choices in groups:
        for option in choices:
            for app in option: app['offset'] = list(relative)
    apps = [app for choices in groups for app in choices[0]]
    return {'number':number,'relative':list(relative),'source':{'id':name,'properties':dict(pairs)},
            'apps':apps,'model_choices':groups,'preview_policy':'ALL_ALTERNATIVES; assembly image is only a schematic combination'}


def category_for(components):
    names = [app['model'].split(':')[-1].lower() for c in components for choices in c.get('model_choices',[]) for option in choices for app in option]
    text = ' '.join(names)
    tests = [('tree',('oak_log','birch_log','spruce_log','jungle_log','acacia_log','tree')),
             ('grave',('tombstone','grave','memorial')),('books',('book',)),('luggage',('cases','case_','luggage')),
             ('crate',('wooden_box','crate','barrel')),('bag',('bag_',)),('statue',('statue','hunter')),
             ('lantern',('lantern','lamp')),('column',('column','pillar')),('window',('window',)),
             ('spire',('spire','pinnacle')),('door_gate',('door','gate')),('connected',('fence','wall','curb'))]
    # Existing naming hints only prioritise review; never import F boundaries,
    # models, placement or approval. Original artwork still comes from ZIP.
    hints = ' '.join(category_hints().get(c['source']['id'].split(':')[-1],{}).get('ru','').lower() for c in components)
    if 'шпил' in hints: return 'spire'
    for category, words in tests:
        if any(word in text for word in words): return category
    return 'other_prefab'


@functools.lru_cache(None)
def category_hints():
    path = ROOT/'docs/semantic-catalog-v2.json'
    return json.loads(path.read_text(encoding='utf8')) if path.exists() else {}


def review_category(components):
    """Labels/ordering only: original vanilla filenames often name wrong objects."""
    inferred = category_for(components)
    if inferred in ('tree','grave','books','luggage','crate','bag','statue','lantern','spire'): return inferred
    hints = [category_hints().get(c['source']['id'].split(':')[-1],{}).get('category') for c in components]
    if hints and all(h=='container' for h in hints): return 'crate'
    target = {'column':'column','window':'window','door':'door_gate','gate':'door_gate'}
    if hints and all(h in target for h in hints) and len(set(hints))==1: return target[hints[0]]
    if hints and all(h=='trim' for h in hints): return 'other_prefab'
    if inferred == 'door_gate' and 'column' in hints: return 'column'
    return inferred


def boundary_class(comp):
    """All states are inventoried; only geometric background is excluded from joins."""
    category = category_for([comp])
    models = [source_model(app['model']) for choices in comp['model_choices'] for option in choices for app in option]
    if not models or not any(model.get('elements') for model in models): return 'empty_visual'
    # The category does not define the carrier universe. Recognizable finite
    # artwork bypasses this conservative structural-background heuristic.
    if category in PRIORITY[:12]: return 'finite_proposal'
    full = all(any(e.get('from') == [0,0,0] and e.get('to') == [16,16,16] and len(e.get('faces',{})) == 6 for e in model.get('elements',[])) for model in models)
    if full: return 'full_cube_context'
    return 'finite_proposal'


def dump_gzip(path, value):
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open('wb') as stream:
        with gzip.GzipFile(fileobj=stream,mode='wb',mtime=0) as zipped:
            zipped.write(json.dumps(value,ensure_ascii=False,separators=(',',':')).encode())


def pattern_label(index):
    result = ''
    while True:
        result = chr(65+index%26)+result; index = index//26-1
        if index < 0: return result


def choose_batch(rows, count=18):
    active = [r for r in rows if r.get('status') == 'UNREVIEWED' and not r.get('user_decision') and r.get('source_patterns') and 'NOT_REDISCOVERED' not in r.get('boundary_flags',[])]
    by_category = defaultdict(list)
    for row in active:
        by_category[row['category']].append(row)
    def art_priority(row):
        models=[app['model'] for c in row['components'] for app in c['apps']]
        if row['category']=='door_gate': return not all(any(t in m for t in ('/addon/gate','/aca_door','/bir_door','/dark_door')) for m in models)
        if row['category']=='window': return not all('/hold/window' in m for m in models)
        return False
    for bucket in by_category.values():
        bucket.sort(key=lambda r: (art_priority(r),any(review_category([c]) != r['category'] for c in r['components']),
                                  len(r['components']) == 1,
                                  not any('retained_C_boundary_proposal' in p['boundary_methods'] for p in r['source_patterns']),
                                  -len({c['source']['id'] for c in r['components']}), -r['similar_count'], int(r['review_id'][1:])))
    chosen = []
    # Diversity cap: no category takes a quarter of an 18-card batch.
    for _ in range(3):
        for category in [c for c in PRIORITY if c not in ('architecture','connected')]:
            if len(chosen) == count: break
            if by_category[category]: chosen.append(by_category[category].pop(0)['review_id'])
    # Backfill only when diversity is exhausted, including structural inventory.
    leftovers = [r for category in PRIORITY for r in by_category[category]]
    chosen.extend(r['review_id'] for r in leftovers[:max(0,count-len(chosen))])
    return chosen


def assert_merge_authority(prior_ids, old_rows):
    if len(prior_ids)<2: return
    canonical = prior_ids[0]
    established = all(old_rows[k].get('status')=='VARIANT_OF' and old_rows[k].get('variant_of')==canonical and old_rows[k].get('user_decision') is None for k in prior_ids[1:])
    if established: return
    if any(old_rows[k].get('user_decision') is not None or old_rows[k].get('status','UNREVIEWED') not in ('UNREVIEWED','VARIANT_OF') for k in prior_ids):
        raise ValueError('Cannot auto-alias IDs with authoritative review decisions: '+', '.join(prior_ids))


def generate_v2(source, scope, previous=None, limit=18):
    if scope.get('dimension','eh_s2:yharnam') != 'eh_s2:yharnam': raise ValueError('Catalog B v2 requires full Yharnam scope')
    if not 15 <= limit <= 25: raise ValueError('Review batch size must be 15..25')
    if _sha256(source) != EXPECTED_SHA256: raise ValueError('Wrong source world')
    if _sha256(DEFAULT_SOURCEPACK) != EXPECTED_PACK_SHA256: raise ValueError('Wrong source pack')
    index = build_index(source,ROOT/'build/source-assembly-v2/carriers.sqlite',pack=DEFAULT_SOURCEPACK,vanilla=DEFAULT_VANILLA)
    if not index['regions']: raise ValueError('Empty Yharnam scan; refusing misleading coverage')
    blockstates = _load_blockstates(DEFAULT_SOURCEPACK,DEFAULT_VANILLA)
    states = {r['state_id']:r for r in index['states']}
    state_values = {sid:(r['source']['id'],tuple(sorted(r['source']['properties'].items()))) for sid,r in states.items()}
    comps, classes, missing = {}, {}, {}
    for sid, state in state_values.items():
        c = component(state,[0,0,0],1,blockstates)
        try: cls = boundary_class(c)
        except (ValueError,KeyError) as exc: cls = 'unresolved_visual'; missing[sid] = str(exc)
        comps[sid], classes[sid] = c, cls
    print('Catalog B: observed states',len(states),'boundary classes',dict(Counter(classes.values())),flush=True)
    eligible = [sid for sid in states if classes[sid] == 'finite_proposal']
    selected_positions = positions(index,eligible)
    cells = {pos:state_values[sid] for pos,sid in selected_positions.items()}
    patterns, rejected = {}, Counter()
    def add_pattern(group, count, examples, method):
        signature = exact_signature(group)
        if signature in patterns:
            row = patterns[signature]
            # Existing source-cluster counts must not be inflated by retained
            # historical boundary proposals of the same exact pattern.
            if method == 'spatial_cluster': row['similar_count'] += count
            row['boundary_methods'] = sorted(set(row['boundary_methods']+[method]))
            return row
        origin = min(group,key=lambda p:(p[1],p[2],p[0]))
        components = [component(state,[pos[i]-origin[i] for i in range(3)],n,blockstates)
                      for n,(pos,state) in enumerate(sorted(group.items(),key=lambda x:(x[0][1],x[0][2],x[0][0])),1)]
        row = {'exact_source_signature':signature,'legacy_spatial_fingerprint':fingerprint(group)[0],
               'components':components,'similar_count':count,'example':{'dimension':'eh_s2:yharnam','anchor':list(origin)},
               'examples':examples[:3],'rotations':[0],'boundary_methods':[method],
               'boundary_flags':['PROPOSED_BOUNDARY'],'context':[]}
        patterns[signature] = row
        return row
    # Every observed carrier state survives separately, even when it is a floor
    # material or a single-model object outside the current review batch.
    for sid,r in states.items():
        p = add_pattern({tuple(r['examples'][0]):state_values[sid]},r['count'],r['examples'],'single_source_cell_inventory')
        p['boundary_class'] = classes[sid]
    vertical = {state_values[sid][0] for sid in eligible if category_for([comps[sid]]) == 'tree'}
    for group in clusters(cells,vertical):
        if len(group) == 1: continue # already retained, not discarded
        if len(group) > 24: rejected['oversized_connected_cluster'] += 1; continue
        span = [max(p[i] for p in group)-min(p[i] for p in group) for i in range(3)]
        if span[0]>8 or span[1]>17 or span[2]>8: rejected['extended_nonfinite_cluster'] += 1; continue
        origin = min(group,key=lambda p:(p[1],p[2],p[0]))
        add_pattern(group,1,[list(origin)],'spatial_cluster')
    # Old C proposals remain evidence, not semantic decisions. Re-evaluate their
    # actual cell combinations throughout Yharnam without adopting F boundaries.
    positions_by_state = defaultdict(set)
    for pos,sid in selected_positions.items(): positions_by_state[state_values[sid]].add(pos)
    legacy_signatures = defaultdict(list)
    for old in (previous or {}).get('candidates',[]):
        for pattern in old.get('source_patterns') or [old]:
            components = pattern.get('components',[])
            if not components: continue
            local = {tuple(c['relative']):(c['source']['id'],tuple(sorted(c['source'].get('properties',{}).items()))) for c in components}
            signature = exact_signature(local)
            if signature in patterns:
                if signature not in legacy_signatures[old['review_id']]: legacy_signatures[old['review_id']].append(signature)
                continue
            # Exact layout and properties, not vanilla-rotation assumptions.
            offset, first = next(iter(local.items())); matches = []
            for pos in sorted(positions_by_state[first]):
                origin = tuple(pos[i]-offset[i] for i in range(3))
                if all(tuple(origin[i]+rel[i] for i in range(3)) in positions_by_state[state] for rel,state in local.items()): matches.append(origin)
            if matches:
                origin = matches[0]; world = {tuple(origin[i]+p[i] for i in range(3)):s for p,s in local.items()}
                row = add_pattern(world,len(matches),[list(p) for p in matches[:3]],'retained_C_boundary_proposal')
                if signature not in legacy_signatures[old['review_id']]: legacy_signatures[old['review_id']].append(signature)
    print('Catalog B: exact source patterns',len(patterns),'multi',sum(len(p['components'])>1 for p in patterns.values()),flush=True)
    # Shape descriptors are cached by actual artwork/layout, not vanilla IDs.
    descriptor_cache = {}; desc_by_pattern = {}; failed_patterns = []
    for n,(sig,p) in enumerate(patterns.items(),1):
        art_key = stable_hash([{'apps':c['apps'],'model_choices':c['model_choices']} for c in p['components']])
        try:
            if art_key not in descriptor_cache: descriptor_cache[art_key] = descriptor(p['components'])
            desc_by_pattern[sig] = descriptor_cache[art_key]
        except (ValueError,KeyError) as exc:
            failed_patterns.append({'exact_source_signature':sig,'reason':str(exc)})
            p['boundary_flags'].append('UNRESOLVED_VISUAL'); p['visual_error'] = str(exc)
            desc_by_pattern[sig] = {'grouping_bucket':'unresolved:'+sig,'canonical_visual_family_signature':None}
        if n%100 == 0: print('Catalog B geometry',n,'/',len(patterns),flush=True)
    # Old IDs win representative selection; then deterministic pattern order.
    priority_sigs = list(dict.fromkeys(sig for k in sorted(legacy_signatures,key=lambda k:int(k[1:])) for sig in legacy_signatures[k]))
    ordered = priority_sigs+[sig for sig in sorted(patterns) if sig not in priority_sigs]
    family_groups = []; bucket_groups = defaultdict(list)
    for sig in ordered:
        if sig not in desc_by_pattern: continue
        d = desc_by_pattern[sig]; chosen = None; evidence = None
        for number in bucket_groups[d['grouping_bucket']]:
            root_sig = family_groups[number]['root']
            evidence = equivalent(desc_by_pattern[root_sig],d)
            if evidence is not None: chosen = number; break
        if chosen is None:
            chosen = len(family_groups); family_groups.append({'root':sig,'patterns':[]}); bucket_groups[d['grouping_bucket']].append(chosen)
            evidence = {'method':'representative','yaw':0}
        family_groups[chosen]['patterns'].append((sig,evidence))
    old_rows = {r['review_id']:r for r in (previous or {}).get('candidates',[])}
    next_id = max((int(k[1:]) for k in old_rows),default=0)+1
    rows, aliases = [], []
    for family in family_groups:
        sigs = {sig for sig,_ in family['patterns']}
        prior_ids = sorted([k for k,old_sigs in legacy_signatures.items() if set(old_sigs) & sigs],key=lambda k:int(k[1:]))
        assert_merge_authority(prior_ids,old_rows)
        if any(r['review_id'] in prior_ids for r in rows):
            raise ValueError('Previously canonical family split: preserve history with an explicit split review, not reused IDs')
        if prior_ids: rid = prior_ids[0]
        else: rid = f'C{next_id:03}'; next_id += 1
        exemplar = copy.deepcopy(patterns[family['root']]); descriptor_value = desc_by_pattern[family['root']]
        pattern_rows = []
        for number,(sig,evidence) in enumerate(family['patterns']):
            p = copy.deepcopy(patterns[sig]); p['pattern_id'] = pattern_label(number); p['review_equivalence'] = evidence
            p['canonical_visual_family_signature'] = descriptor_value['canonical_visual_family_signature']
            pattern_rows.append(p)
        category = review_category(exemplar['components'])
        if len(exemplar['components']) == 1 and exemplar.get('boundary_class') == 'full_cube_context': category = 'architecture'
        row = {**exemplar,'review_id':rid,'status':old_rows.get(rid,{}).get('status','UNREVIEWED'),
               'user_decision':old_rows.get(rid,{}).get('user_decision'), 'category':category,
               'hypothesis':LABELS[category]+': canonical review candidate, не утверждённый объект',
               'canonical_visual_family_signature':descriptor_value['canonical_visual_family_signature'],
               'source_patterns':pattern_rows,'similar_count':sum(p['similar_count'] for p in pattern_rows),
               'rotations':sorted({p['review_equivalence'].get('yaw',0) for p in pattern_rows}),
               'count_scope':f"All {len(index['regions'])} eh_s2:yharnam terrain regions; counts sum pattern matches, not proven distinct semantic objects",
               'history':copy.deepcopy(old_rows.get(rid,{}).get('history',[]))}
        if row['status'] == 'VARIANT_OF': row['status'] = 'UNREVIEWED'
        if 'UNRESOLVED_VISUAL' in exemplar['boundary_flags']: row['status'] = 'UNRESOLVED_VISUAL'
        rows.append(row)
        for alias in prior_ids[1:]:
            original = copy.deepcopy(old_rows[alias]); history = original.setdefault('history',[])
            event = {'event':'review_geometry_dedup','variant_of':rid,'previous_spatial_fingerprint':original.get('spatial_fingerprint'),
                     'manual_decision_applied':False}
            if event not in history: history.append(event)
            original.update(status='VARIANT_OF',variant_of=rid,canonical_visual_family_signature=row['canonical_visual_family_signature'])
            aliases.append(original)
    # Never silently drop historical IDs if a changed boundary is not recovered.
    emitted = {r['review_id'] for r in rows+aliases}
    for rid,old in old_rows.items():
        if rid not in emitted:
            preserved = copy.deepcopy(old); preserved['boundary_flags'] = sorted(set(preserved.get('boundary_flags',[])+['POSSIBLY_INCOMPLETE','NOT_REDISCOVERED']))
            aliases.append(preserved)
    rows += aliases; rows.sort(key=lambda r:int(r['review_id'][1:]))
    batch = choose_batch(rows,limit)
    # Context lookup uses the complete source universe. Candidate geometry never
    # auto-absorbs a neighboring carrier just because it exists in that index.
    with zipfile.ZipFile(source) as archive:
        reader = SourceReader(archive,index['regions'])
        carrier_names = {r['source']['id'] for r in states.values()}
        for row in rows:
            if row['review_id'] not in batch: continue
            for p in row['source_patterns']:
                anchor = p['example']['anchor']; members = {tuple(c['relative']) for c in p['components']}
                bounds = [range(min(v[i] for v in members)-1,max(v[i] for v in members)+2) for i in range(3)]
                context, outside = [], False
                for rel in itertools.product(*bounds):
                    if rel in members: continue
                    state = reader.get('eh_s2:yharnam',tuple(anchor[i]+rel[i] for i in range(3)))
                    if state is None: outside = True; continue
                    if state[0] in ('minecraft:air','minecraft:cave_air','minecraft:void_air'): continue
                    context.append({'relative':list(rel),'source':{'id':state[0],'properties':dict(state[1])},'bloodborne_carrier':state[0] in carrier_names})
                    if state[0] in carrier_names and any(max(abs(rel[i]-m[i]) for i in range(3))<=1 for m in members): outside = True
                p['context'] = context
                if outside: p['boundary_flags'].append('POSSIBLY_INCOMPLETE')
            row['context'] = row['source_patterns'][0]['context']; row['boundary_flags'] = sorted({f for p in row['source_patterns'] for f in p['boundary_flags']})
    active_rows = [r for r in rows if r.get('status')!='VARIANT_OF' and r.get('source_patterns') and 'NOT_REDISCOVERED' not in r.get('boundary_flags',[])]
    canonical_rows = [r for r in active_rows if r.get('canonical_visual_family_signature') is not None]
    coverage = {'terrain_regions_scanned':len(index['regions']),'terrain_chunks_scanned':index['chunks'],'bloodborne_carrier_cells':index['carrier_cells'],
                'observed_carrier_states':len(states),'exact_spatial_patterns':len(patterns),'canonical_family_candidates':len(canonical_rows),
                'single_cell_exact_patterns':sum(len(p['components'])==1 for p in patterns.values()),
                'multi_cell_exact_patterns':sum(len(p['components'])>1 for p in patterns.values()),
                'single_cell_canonical_candidates':sum(len(r['components'])==1 for r in canonical_rows),
                'multi_cell_canonical_candidates':sum(len(r['components'])>1 for r in canonical_rows),
                'rejected_clusters_by_reason':dict(rejected),'carrier_state_boundary_classes':dict(Counter(classes.values())),
                'unresolved_visual_patterns':len(failed_patterns),'unresolved_visual_candidates':len(active_rows)-len(canonical_rows),
                'rejected_cluster_count':sum(rejected.values()),
                'batch_cards':len(batch),'candidates_outside_batch':len(active_rows)-len(batch),
                'canonical_candidates_outside_batch':len(canonical_rows)-len(batch)}
    manifest = {'format':VERSION,'source':{'path':'reference-inputs/source-world.zip','sha256':EXPECTED_SHA256,'sha256_after':_sha256(source)},
                'sourcepack_sha256':EXPECTED_PACK_SHA256,'scan_scope':{'dimension':'eh_s2:yharnam','regions':index['regions'],'carrier_universe':'pack/model/parent/texture/mapping overrides; all observed states'},
                'signature_policy':{'exact':'raw source ID/properties/relative XYZ; translation only','canonical':'textured geometry, yaw/translation, narrow component-layout review equivalence; not semantic approval'},
                'preview_policy':'all independent weighted/unweighted choices shown; no positional RNG claim',
                'batch_id':'batch-02','batch_review_ids':batch,'coverage':coverage,'candidates':rows}
    if manifest['source']['sha256_after'] != EXPECTED_SHA256: raise ValueError('Read-only source changed')
    dump(ROOT/'docs/source-assembly-coverage.json',coverage)
    dump_gzip(ROOT/'docs/source-assembly-carrier-index.json.gz',{'source':manifest['source'],'regions':index['regions'],'states':index['states']})
    dump_gzip(ROOT/'docs/source-assembly-exact-patterns.json.gz',{'patterns':list(patterns.values()),'unresolved_visual_patterns':failed_patterns})
    dump_gzip(ROOT/'docs/source-assembly-single-cell-candidates.json.gz',{'candidate_ids':[r['review_id'] for r in active_rows if len(r['components'])==1],
                                                                     'exact_source_signatures':[sig for sig,p in patterns.items() if len(p['components'])==1]})
    return manifest
