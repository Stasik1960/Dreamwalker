"""Build isolated approval specimens and an unchanged copy of city context."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import math
import os
import shutil
import tempfile
from pathlib import Path

from build_nightmare_gallery import geometry_cells, mesh_bounds, state_key
from city_palette import audit_helpers
from convert_logical_world import World, PART, block_pos_long
from reviewed_migration_fixture import write_fixture
from world_io import TAG_COMPOUND, TAG_INT, TAG_LIST, TAG_LONG, TAG_STRING, Tag

ROOT = Path(__file__).resolve().parents[1]
CITY = ROOT / 'src/main/resources/bloodborne_blocks/city'
OUT = ROOT / 'build/autumn-launch/Gallery-Compact-Final'
MAIN = ROOT / 'build/autumn-launch/Main-City-Release'


def load_json(path):
    return json.loads(path.read_bytes())


def load_mesh_bounds(path):
    """Read compressed mesh records one at a time, retaining bounds only."""
    bounds = {}
    decoder = json.JSONDecoder()
    with gzip.open(path, 'rt', encoding='utf8') as source:
        buffer = source.read(65536)
        offset = 0

        def next_character():
            nonlocal buffer, offset
            while offset >= len(buffer):
                chunk = source.read(65536)
                if not chunk:
                    raise ValueError(f'unexpected end of mesh file: {path}')
                buffer = buffer[offset:] + chunk
                offset = 0
            return buffer[offset]

        def skip_whitespace():
            nonlocal offset
            while next_character().isspace():
                offset += 1

        def decode():
            nonlocal buffer, offset
            while True:
                try:
                    value, offset = decoder.raw_decode(buffer, offset)
                    return value
                except json.JSONDecodeError as error:
                    chunk = source.read(65536)
                    if not chunk:
                        raise ValueError(f'invalid mesh file: {path}') from error
                    buffer = buffer[offset:] + chunk
                    offset = 0

        skip_whitespace()
        if next_character() != '{':
            raise ValueError(f'mesh file is not an object: {path}')
        offset += 1
        while True:
            skip_whitespace()
            if next_character() == '}':
                return bounds
            name = decode()
            if not isinstance(name, str):
                raise ValueError(f'mesh identifier is not a string: {path}')
            skip_whitespace()
            if next_character() != ':':
                raise ValueError(f'mesh value is missing: {path}')
            offset += 1
            skip_whitespace()
            bounds[name] = mesh_bounds(decode())
            skip_whitespace()
            separator = next_character()
            if separator == '}':
                return bounds
            if separator != ',':
                raise ValueError(f'mesh separator is invalid: {path}')
            offset += 1


def load_data(city=CITY):
    resources = city.parent
    doc = load_json(city / 'document-final.json')
    logical = load_json(resources / 'logical/definitions.json')['blocks']
    definitions = {d['id']: d for d in logical + load_json(city / 'definitions.json')['blocks']}
    geometry = {'blocks': {}, 'profiles': {}}
    meshes = {}
    for folder in (resources / 'logical', city):
        g = load_json(folder / 'geometry.json')
        geometry['blocks'].update(g['blocks'])
        geometry['profiles'].update(g.get('profiles', {}))
        for name in ('meshes.json.gz', 'owner-meshes.json.gz'):
            if (folder / name).is_file():
                meshes.update(load_mesh_bounds(folder / name))
    contracts = {d['id']: d for d in load_json(resources / 'logical/contracts-v2.json')['families']}
    ids = load_json(resources / 'debug-ids.json')['ids']
    return doc, definitions, geometry, meshes, contracts, ids, [d['id'] for d in logical]


def canonical(d):
    props = {**d['default'], **d.get('placement_properties', {})}
    for k, value in [('facing', 'north'), ('root_anchor', 'canonical'), ('open', 'false'), ('face', 'wall')]:
        if value in d.get('properties', {}).get(k, []):
            props[k] = value
    return props


def specimens(data):
    doc, defs, geometry, meshes, contracts, ids, logical_ids = data
    rows = []

    def add(ident, props, role, item=None):
        d = defs[ident]
        key = state_key(props)
        if key not in d['models']:
            raise ValueError(f'unregistered specimen state: {ident} {key}')
        render = contracts.get(ident, {}).get('states', {}).get(key, {}).get('render_mesh')
        mesh_id = render['id'] if render else d['models'][key]
        g = geometry['blocks'][ident]['states'][key]
        g = geometry['profiles'].get(g.get('ref'), g)
        offset = render['offset'] if render else g.get('render_offset', [0, 0, 0])
        raw = meshes[mesh_id]
        bounds = [raw[i] + offset[i % 3] for i in range(6)]
        footprint = geometry_cells(ident, key, contracts, geometry) if d.get('whole_owner') or ident in logical_ids else [(0, 0, 0)]
        if (0, 0, 0) not in footprint:
            raise ValueError('specimen root is not part of footprint: ' + ident)
        rows.append({'id': ident, 'debug_id': ids[ident], 'properties': props, 'state': key,
                     'role': role, 'item': item, 'bounds': bounds, 'footprint': footprint})
        return rows[-1]

    for ident in sorted(set(logical_ids) | {d['id'] for d in defs.values() if d.get('whole_owner') and not d.get('document_item')}):
        d = defs[ident]
        base = canonical(d)
        # Unified owners encode their selectable private meshes in `variant`.
        # The gallery is a public review surface, so show only their explicit
        # canonical placement instead of expanding every implementation variant.
        if d.get('unified'):
            add(ident, base, 'historical')
            continue
        # Preserve actual artistic alternatives and open states, without yaw duplicates.
        for key in d['models']:
            props = dict(part.split('=', 1) for part in key.split(','))
            if all(props[k] == base[k] for k in props if k in {'facing', 'root_anchor', 'face', 'waterlogged', 'half', 'axis'}):
                add(ident, props, 'historical')
    for recipe in doc['objects']:
        ident, item = recipe['id'], recipe['item']
        base = canonical(defs[ident])
        for facing in ('north', 'east', 'south', 'west'):
            add(ident, {**base, 'facing': facing}, 'document_result', item)
        if 'open' in base:
            add(ident, {**base, 'open': 'true'}, 'document_open', item)
        if 'face' in base:
            for face in ('floor', 'ceiling'):
                add(ident, {**base, 'face': face}, 'document_mount', item)
        for n, component in enumerate(recipe['components']):
            d = defs[component['id'].split(':')[1]]
            # Each source part is exhibited independently; no guessed owner closure.
            add(d['id'], component['properties'], f'source_part_{n+1}', item)['_review_document']=recipe['id']
        for choice_index, choice in enumerate(recipe['choices']):
            candidate_rows = []
            source_rows = []
            for component in choice.get('sourceComponents', []):
                ident = component['id'].split(':')[-1]
                if ident not in defs:
                    raise ValueError(f'unregistered source component: {ident}')
                specimen=add(ident, component['properties'], 'decision_source_component', item)
                specimen['_review_document']=recipe['id'];source_rows.append(specimen)
            if choice.get('candidates') and choice.get('id'):
                d = defs[choice['id']]
                base_candidate = canonical(d)
                for key in choice['candidates']:
                    props = dict(part.split('=', 1) for part in key.split(','))
                    if all(props[k] == base_candidate[k] for k in props if k in {'facing', 'root_anchor', 'face'}):
                        specimen=add(d['id'], props, 'unproven_source_variant', item)
                        specimen['_review_document']=recipe['id'];candidate_rows.append(specimen)
            historical={'owner_final_13':'o_shuttered_window','owner_final_16':'o_balustrade'}.get(recipe['id'])
            if historical and choice.get('reason')=='additional-or-historical-art-needs-gallery-review':
                candidate_rows.extend(r for r in rows if r['id']==historical and r['role']=='historical')
            # Every decision gets a physical review marker, including decisions
            # without a proven candidate.  The marker is deliberately geometry
            # free: it must never imply an invented model.
            decision = dict(choice)
            decision['_document_id'] = recipe['id']
            decision['_choice_index'] = choice_index
            decision['_decision'] = True
            decision['_decision_label'] = f"doc-{recipe['id']}-choice-{decision['_choice_index']+1}"
            decision['_source_components'] = choice.get('sourceComponents', [])
            rows.append({'id': recipe['id'] + '__decision_' + str(choice_index + 1), 'debug_id': ids[recipe['id']],
                         'properties': {}, 'state': '', 'role': 'document_decision',
                         'item': item, 'bounds': [0, 0, 0, 0, 0, 0], 'footprint': [(0, 0, 0)],
                         'decision': decision, '_decision': True,
                         'source_rows': source_rows, 'candidate_rows': candidate_rows})
    return rows


def add_entity(point, data):
    return Tag(TAG_COMPOUND, {**data, **{axis: Tag(TAG_INT, value) for axis, value in zip(('x', 'y', 'z'), point)}})


def build_cells(rows):
    cells, entities, occupied = {}, [], set()
    # Derive a compact shelf layout from each specimen's actual extents.  The
    # pad includes the sign's one-block surround as well as mesh/part bounds.
    layouts = []
    for row in rows:
        bounds, footprint = row['bounds'], row['footprint']
        low = [min(0, math.floor(bounds[i]), *(p[i] for p in footprint)) for i in range(3)]
        high = [max(1, math.ceil(bounds[i+3]), *(p[i]+1 for p in footprint)) for i in range(3)]
        marker = (low[0]-2, low[2]-2)
        pad_low = (min(low[0], marker[0]-1), min(low[2], marker[1]-1))
        pad_high = (max(high[0], 4), max(high[2], 4))
        layouts.append({'low': low, 'high': high, 'pad_low': pad_low, 'pad_high': pad_high})
    row_depths = []
    for start in range(0, len(rows), 16):
        row_depths.append(max(l['pad_high'][1] - l['pad_low'][1] for l in layouts[start:start+16]))
    row_z = 16
    positions = []
    for row_index, start in enumerate(range(0, len(rows), 16)):
        x = 16384
        for index in range(start, min(start + 16, len(rows))):
            layout = layouts[index]
            width = layout['pad_high'][0] - layout['pad_low'][0]
            positions.append((x - layout['pad_low'][0], row_z - layout['pad_low'][1]))
            x += width + 3
        row_z += row_depths[row_index] + 3
    for index, row in enumerate(rows):
        bounds, footprint = row['bounds'], row['footprint']
        layout = layouts[index]
        low, high = layout['low'], layout['high']
        root = (positions[index][0], 64 - low[1], positions[index][1])
        owner = 'minecraft:air' if row.get('_decision') else 'bloodborne_blocks:' + row['id']
        for offset in footprint:
            point = tuple(root[i] + offset[i] for i in range(3))
            if point in occupied:
                raise ValueError(f'specimen overlap at {point}')
            occupied.add(point)
            cells[point] = (owner, row['properties']) if point == root else (PART, {})
            if point != root:
                entities.append(add_entity(point, {'id': Tag(TAG_STRING, PART), 'Owner': Tag(TAG_STRING, owner),
                                                   'Root': Tag(TAG_LONG, block_pos_long(*root))}))
        # A full rectangular pad makes the three-block navigation gaps clear.
        pad_min_x = root[0] + layout['pad_low'][0]
        pad_max_x = root[0] + layout['pad_high'][0]
        pad_min_z = root[2] + layout['pad_low'][1]
        pad_max_z = root[2] + layout['pad_low'][1] + row_depths[index // 16]
        pad_points = {(x, 63, z) for x in range(pad_min_x, pad_max_x) for z in range(pad_min_z, pad_max_z)}
        marker = (root[0]+low[0]-2, 64, root[2]+low[2]-2)
        for p in pad_points:
            if p in occupied:
                raise ValueError('pad overlaps geometry')
            cells[p] = ('minecraft:smooth_stone', {})
        if marker in cells:
            raise ValueError('marker overlaps geometry')
        cells[marker] = ('minecraft:oak_sign', {'rotation': '8', 'waterlogged': 'false'})
        if row.get('_decision'):
            d = row['decision']
            text = [row['debug_id'], d['_document_id'], f"choice {d['_choice_index']+1}", d.get('reason', 'no reason')]
        else:
            text = [row['debug_id'], row['id'], row['role'], 'Final ' + str(row['item']) if row['item'] else 'Historical']
        front = Tag(TAG_COMPOUND, {'messages': Tag(TAG_LIST, [Tag(TAG_STRING, json.dumps({'text': t})) for t in text], TAG_STRING),
                                   'color': Tag(TAG_STRING, 'black'), 'has_glowing_text': Tag(1, 0)})
        entities.append(add_entity(marker, {'id': Tag(TAG_STRING, 'minecraft:sign'), 'front_text': front, 'back_text': front}))
        row.update(root=list(root), marker=list(marker), pad_bounds=[pad_min_x, 63, pad_min_z, pad_max_x-1, 63, pad_max_z-1],
                   tp=f'/tp @s {root[0]} {root[1]+2} {root[2]-3}')
    return cells, entities


def select_rows(rows, selected):
    """Keep referenced source/candidate specimens when selecting document decisions."""
    if selected is None:
        return rows
    keep = {id(row) for row in rows if row['id'] in selected or row.get('_review_document') in selected or
            (row.get('_decision') and row['decision']['_document_id'] in selected)}
    for row in rows:
        if id(row) in keep:
            for reference in row.get('source_rows', []) + row.get('candidate_rows', []):
                keep.add(id(reference))
    return [row for row in rows if id(row) in keep]


def build(output=OUT, source=MAIN, city=CITY, selected=None, case_report=None, document_report=None):
    output = Path(output).resolve()
    source = Path(source).resolve() if source else None
    city = Path(city).resolve()
    if output.exists() or source and (output == source or source in output.parents) or any(output == p or p in output.parents for p in (city, city.parent/'logical')):
        raise ValueError('gallery output must be new and outside city')
    data = load_data(city)
    rows = specimens(data)
    rows = select_rows(rows, selected)
    cells, entities = build_cells(rows)
    if not rows:
        raise ValueError('gallery must have specimens')
    output.parent.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix='approval-gallery-', dir=output.parent))
    folder = stage / 'world'
    try:
        write_fixture(folder, cells, block_entities=entities, floor=False, level_name='Bloodborne Approval Gallery')
        copied = []
        if source:
            for name in ('region', 'entities', 'poi', 'dimensions', 'DIM-1', 'DIM1'):
                if not (source / name).exists():
                    continue
                for path in (source / name).rglob('*'):
                    if not path.is_file():
                        continue
                    relative = path.relative_to(source)
                    target = folder / relative
                    if target.exists():
                        raise ValueError('refusing to overwrite a gallery file: ' + str(relative))
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(path, target)
                    digest = hashlib.sha256(path.read_bytes()).hexdigest()
                    if hashlib.sha256(target.read_bytes()).hexdigest() != digest:
                        raise ValueError('city context copy differs')
                    copied.append({'path': relative.as_posix(), 'sha256': digest})
        cases = []
        if case_report:
            report = load_json(Path(case_report))
            cases = [{k: r[k] for k in ('dimension', 'origin', 'reason', 'tp', 'conflictingCells') if k in r} for r in report['rejected']]
        document_cases = []
        if document_report:
            report = load_json(Path(document_report))
            source_cases = report.get('unresolved', report.get('documentUnresolved', report.get('unresolvedVariants', [])))
            for r in source_cases:
                point=r.get('position',r.get('origin'))
                dim=r.get('dimension','minecraft:overworld')
                document_cases.append({**r,'document':f'owner_final_{r["item"]:02d}' if 'item' in r else r.get('document','?'),
                                       'tp':r.get('tp') or (f'/execute in {dim} run tp @s {point[0]} {point[1]+2} {point[2]}' if point else '')})
        world = World(folder, {})
        audit = audit_helpers(world, city)
        if not audit['ok']:
            raise ValueError('gallery helper audit failed: ' + str(audit['orphans'][:5]))
        choices = []
        for row in rows:
            if row.get('_decision'):
                d = row['decision']
                result_row=next((r for r in rows if r['id']==d['_document_id'] and r['role']=='document_result'),None)
                source_rows=row.get('source_rows',[]) or [r for r in rows if r.get('_review_document')==d['_document_id'] and r['role'].startswith('source_part_')]
                choices.append({'document': d['_document_id'], 'index': d['_choice_index'] + 1,
                                'reason': d.get('reason', ''), 'tp': row['tp'],
                                'decision_ref': d['_document_id'] + ':' + str(d['_choice_index'] + 1),
                                'sourceComponents': d.get('_source_components', []),
                                'sourceTps': [r['tp'] for r in source_rows if 'tp' in r],
                                'candidates': d.get('candidates', []),
                                'candidateTps': [r['tp'] for r in row.get('candidate_rows', []) if 'tp' in r],
                                'result': d['_document_id'], 'resultTp': result_row['tp'] if result_row else None})
        manifest = {'schemaVersion': 3, 'specimens': rows, 'choices': choices, 'cityCases': cases,
                    'documentCases': document_cases,
                    'cityContext': copied, 'helperAudit': audit, 'navigation': {'firstPod': rows[0]['tp'], 'spacing': 3,
                    'columns': 16, 'layout': 'variable-size rectangular pads; three empty blocks between neighboring edges'}}
        (folder / 'gallery-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
        # Fast search and teleport index without opening a large JSON document.
        nav = ['# Галерея на утверждение', '', 'Режим creative, полёт. Таблички показывают действительный пятизначный ID.',
               'Источник, варианты и нерешённые части каждого пункта находятся рядом в отдельных экспонатах.',
               'Копия города сохранена на исходных координатах; спорные стыки перечислены в cityCases.',
               'Площадки имеют фактический размер экспоната; между соседними краями оставлено 3 пустых блока.', '', '| ID | Объект | Вариант | Переход |', '|---|---|---|---|']
        nav += [f'| {r["debug_id"]} | {r["id"]} | {r["role"]} | `{r["tp"]}` |' for r in rows]
        nav += ['', '## Решения по документу', '', '| Документ | Причина | TP | Кандидаты/источник |', '|---|---|---|---|']
        nav += [f'| {c["document"]} / choice {c["index"]} | {c["reason"]} | `{c["tp"]}` | результат: `{c["resultTp"]}`; кандидаты: {", ".join(c["candidateTps"]) or "не установлены"}; исходные части: {", ".join(c["sourceTps"]) or "не установлены"} |' for c in choices]
        if document_cases:
            nav += ['', '## Неразрешённые пункты отчёта', '', '| Документ | Choice | Причина | TP |', '|---|---:|---|---|']
            nav += [f'| {c.get("document", "?")} | {c.get("choice", "?")} | {c.get("reason", "")} | `{c.get("tp", "")}` |' for c in document_cases]
        (folder / 'Review-Cases.md').write_text('\n'.join(['# Спорные места города', '', 'Копия города находится на исходных координатах. Команды ведут к сохранённой конструкции.', '', '| № | Причина | Переход |', '|---|---|---|'] + [f'| {i+1} | {c["reason"]} | `{c.get("tp", "")}` |' for i,c in enumerate(cases)])+'\n', encoding='utf8')
        (folder / 'Gallery-Index.md').write_text('\n'.join(nav)+'\n', encoding='utf8')
        os.replace(folder, output)
        print(json.dumps({'specimens': len(rows), 'uniqueIds': len({r['id'] for r in rows}), 'helpers': audit['checked'],
                          'cityFiles': len(copied), 'cityCases': len(cases)}), flush=True)
        return manifest
    finally:
        shutil.rmtree(stage, ignore_errors=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, default=OUT)
    p.add_argument('--source', type=Path, default=MAIN)
    p.add_argument('--case-report', type=Path, default=ROOT/'build/autumn-launch/conversion-recovered.json')
    p.add_argument('--document-report', type=Path, default=None)
    args = p.parse_args()
    build(args.output, args.source, case_report=args.case_report, document_report=args.document_report)
