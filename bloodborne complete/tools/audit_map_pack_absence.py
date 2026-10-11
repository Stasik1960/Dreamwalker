#!/usr/bin/env python3
"""Reconcile original v15 pack with actual v16 terrain, without world writes.

Consumes independently scanned cell states and positional model applications.
Only new analysis reports are written; production code/resources remain intact.
"""
from __future__ import annotations

from collections import Counter, defaultdict
import copy
import csv
import hashlib
import json
from pathlib import Path
import re
import subprocess
import traceback
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/map-pack-absence'
PREVIOUS = ROOT / 'docs/remaining-transfer'
PACK = ROOT.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
EXPECTED_PACK = '29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36'
EXPECTED_MAP = '39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08'
INDEPENDENT = {'FINISHED_UNUSED_ART', 'STANDALONE_REFERENCED_ART'}


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(4 * 1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def normalized(model):
    return model if ':' in model else 'minecraft:' + model


def model_from_file(path):
    namespace, name = path.split('/models/')
    return namespace.split('/')[1] + ':' + name[:-5]


def visual_fingerprint(row):
    """Same exact elements/UV/textures/AO equivalence used by remaining_plan.art.

    Model display transforms and unused texture aliases are not visible world
    geometry and must not hide a certified duplicate already used in terrain.
    """
    value = row.get('resolved', {})
    elements = copy.deepcopy(value.get('elements', []))
    for element in elements:
        element.pop('name', None)
        for face in element.get('faces', {}).values():
            texture = face.get('texture', '')
            face['texture'] = value.get('textures', {}).get(texture[1:], texture) if texture.startswith('#') else texture
    visual = {'elements': elements, 'ambientocclusion': value.get('ambientocclusion', True)}
    return hashlib.sha256(json.dumps(visual, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def applications(value):
    if isinstance(value, list):
        for app in value:
            yield from applications(app)
    elif isinstance(value, dict) and 'model' in value:
        yield {**value, 'model': normalized(value['model'])}


def branches(raw):
    for selector, value in raw.get('variants', {}).items():
        yield 'variant:' + selector, list(applications(value))
    for index, part in enumerate(raw.get('multipart', [])):
        condition = json.dumps(part.get('when', {}), sort_keys=True, separators=(',', ':'))
        yield f'multipart:{index}:{condition}', list(applications(part['apply']))


def condition_matches(condition, properties):
    if 'OR' in condition:
        return any(condition_matches(part, properties) for part in condition['OR'])
    if 'AND' in condition:
        return all(condition_matches(part, properties) for part in condition['AND'])
    def primitive(value):
        return str(value).lower() if isinstance(value, bool) else str(value)
    return all(properties.get(key) in primitive(value).split('|') for key, value in condition.items())


def branch_matches(label, properties):
    if label.startswith('variant:'):
        selector = label[len('variant:'):]
        condition = dict(pair.split('=', 1) for pair in selector.split(',') if pair)
    else:
        condition = json.loads(label.split(':', 2)[2])
    return condition_matches(condition, properties)


def matching_self_check():
    cases = [
        ('variant:', {'facing': 'north'}, True),
        ('variant:facing=north,half=bottom', {'facing': 'north', 'half': 'bottom', 'waterlogged': 'false'}, True),
        ('variant:facing=north', {'facing': 'south'}, False),
        ('multipart:0:{"OR":[{"east":"low|tall"},{"north":"low"}]}', {'east': 'tall', 'north': 'none'}, True),
        ('multipart:0:{"AND":[{"east":"low|tall"},{"up":"true"}]}', {'east': 'low', 'up': 'false'}, False),
        ('multipart:0:{}', {}, True),
        ('multipart:0:{"up":true}', {'up': 'true'}, True),
        ('multipart:0:{"north":"tall","up":false}', {'north': 'tall', 'up': 'false'}, True),
    ]
    for label, properties, expected in cases:
        assert branch_matches(label, properties) == expected, (label, properties)
    return {'status': 'PASS', 'cases': len(cases)}


def csv_file(name, rows, fields):
    with (OUT / name).open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key, '') for key in fields})


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    world = load(OUT / 'world-states.json')
    usage = load(OUT / 'model-usage.json')
    inventory = load(PREVIOUS / 'source-inventory.json')
    assert world['status'] == usage['status'] == 'PASS', 'full scans must pass'
    assert world['map_sha256_before'] == world['map_sha256_after'] == EXPECTED_MAP
    assert usage['map_sha256_before'] == usage['map_sha256_after'] == EXPECTED_MAP
    assert usage['pack_sha256_before'] == usage['pack_sha256_after'] == EXPECTED_PACK
    assert sha(PACK) == inventory['summary']['pack_sha256'] == EXPECTED_PACK
    assert world['chunks_scanned'] == usage['chunks_scanned'] == 35890
    checks = matching_self_check()
    candidates = load(PREVIOUS / 'source-candidates.json')
    classifications = {row['sourceModel']: row for row in candidates['modelClassifications']}
    source_parse_defects = {}
    for defect in inventory['summary'].get('defects', []):
        if '/models/' in defect['file']:
            model = model_from_file(defect['file'])
            source_parse_defects[model] = [defect]
            classifications.setdefault(model, {'sourceModel': model, 'kind': 'SOURCE_DEFECT'})
    with (PREVIOUS / 'model-correspondence.csv').open(encoding='utf-8-sig', newline='') as stream:
        correspondence = {row['sourceModel']: row for row in csv.DictReader(stream)}
    catalog = load(ROOT / 'src/architecture/resources/bloodborne_dw/debug_catalogue.json')
    names = {entry['registryId']: entry['name'] for entry in catalog['entries']}
    numbers = {entry['registryId']: entry['temporaryId'] for entry in catalog['entries']}
    variant_names = {}
    for obj in load(ROOT / 'src/architecture/resources/bloodborne_dw/remaining-catalogue.json')['objects']:
        registry = 'bloodborne_dw:' + obj['path']
        names[registry] = obj['displayName']
        for variant in obj['variants']:
            variant_names[f'{registry}[variant={variant["variant"]}]'] = variant['name']
    localization = {}
    assets = Path('C:/Users/vakir/AppData/Roaming/.minecraft/assets')
    index = assets / 'indexes/5.json'
    if index.exists():
        record = load(index)['objects'].get('minecraft/lang/ru_ru.json')
        if record:
            language = assets / 'objects' / record['hash'][:2] / record['hash']
            if language.exists():
                assert hashlib.sha1(language.read_bytes()).hexdigest() == record['hash']
                localization = load(language)
    states = defaultdict(list)
    carrier_counts = Counter()
    for row in world['states']:
        assert row['count'] > 0
        states[row['name']].append(row)
        carrier_counts[row['name']] += row['count']
    branch_rows = {(row['source'], row['state']): row for row in inventory['states']}
    pack_models, overrides = set(), {}
    with zipfile.ZipFile(PACK, 'r') as archive:
        prefix = next(name[:-len('pack.mcmeta')] for name in archive.namelist() if name.endswith('pack.mcmeta'))
        for entry in archive.namelist():
            name = entry.removeprefix(prefix)
            if '/models/' in name and name.endswith('.json'):
                pack_models.add(model_from_file(name))
            if '/blockstates/' in name and name.endswith('.json'):
                carrier = name.split('/')[1] + ':' + Path(name).stem
                overrides[carrier] = json.loads(archive.read(entry))
        for carrier, raw in overrides.items():
            for label, apps in branches(raw):
                branch_rows[(carrier, label)] = {'source': carrier, 'state': label, 'models': apps}
    affected_carriers = {row['source'] for row in inventory['states']}
    carriers = sorted(affected_carriers | set(overrides))
    carrier_rows, state_rows = [], []
    for carrier in carriers:
        source_models = sorted({normalized(app['model']) for (block, _), row in branch_rows.items() if block == carrier for app in row['models']})
        namespace, path = carrier.split(':', 1)
        carrier_rows.append({
            'sourceBlock': carrier,
            'russianCarrierName': localization.get('block.' + namespace + '.' + path, ''),
            'scope': 'PACK_BLOCKSTATE_OVERRIDE' if carrier in overrides else 'INHERITED_MODEL_OR_TEXTURE_OVERRIDE',
            'placedCells': carrier_counts[carrier],
            'sourceModels': ';'.join(source_models),
            'status': 'ABSENT_CARRIER' if not carrier_counts[carrier] else 'PRESENT_CARRIER',
        })
    for (carrier, label), row in sorted(branch_rows.items()):
        matching = [state for state in states[carrier] if branch_matches(label, state['properties'])]
        matching_count = sum(state['count'] for state in matching)
        measured = usage['branches'].get(carrier + '|' + label, {})
        assert measured.get('count', 0) == matching_count, (carrier, label, matching_count, measured)
        state_rows.append({
            'sourceBlock': carrier, 'sourceBranch': label,
            'scope': 'PACK_BLOCKSTATE_OVERRIDE' if carrier in overrides else 'INHERITED_MODEL_OR_TEXTURE_OVERRIDE',
            'carrierPlacedCells': carrier_counts[carrier], 'matchingPlacedCells': matching_count,
            'sourceModels': ';'.join(sorted({normalized(app['model']) for app in row['models']})),
            'actualModels': json.dumps(measured.get('applications', {}), ensure_ascii=False, sort_keys=True),
            'status': 'ABSENT_STATE_BRANCH' if not matching_count else 'PRESENT_STATE_BRANCH',
        })
    model_inventory = {row['model']: row for row in inventory['models']}
    with zipfile.ZipFile(Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')) as vanilla:
        vanilla_models = {model_from_file(path) for path in vanilla.namelist() if '/models/' in path and path.endswith('.json')}
    referenced_models = {normalized(app['model']) for row in branch_rows.values() for app in row['models']}
    missing_parents = {normalized(issue['parent']) for row in model_inventory.values() for issue in row['resolved'].get('issues', []) if issue.get('type') == 'PARENT' and issue.get('parent')}
    direct = {key: row['count'] for key, row in usage['direct_models'].items() if row['count']}
    dependencies = Counter()
    for model, count in direct.items():
        resolved = model_inventory.get(model, {}).get('resolved', {})
        for path in set(resolved.get('dependencies', [])):
            if '/models/' in path and path.endswith('.json'):
                parent = model_from_file(path)
                if parent != model:
                    dependencies[parent] += count
    # Explicit visual equality uses exact resolved elements/UV/textures/AO.
    # The absence of a JSON path alone does not prove absence of duplicate art.
    used_fingerprints = defaultdict(set)
    for model in direct:
        fingerprint = visual_fingerprint(model_inventory[model]) if model in model_inventory else None
        if fingerprint:
            used_fingerprints[fingerprint].add(model)
    model_rows = []
    for model in sorted(pack_models | set(model_inventory) | set(classifications) | referenced_models | set(direct) | missing_parents):
        data = model_inventory.get(model, {})
        classification = classifications.get(model, {})
        current = correspondence.get(model, classification)
        registry = current.get('registryId', '')
        targets = current.get('offeredTargets', '')
        display_names = sorted({variant_names.get(target, names.get(target.split('[')[0], '')) for target in targets.split(';') if target})
        display_names = [value for value in display_names if value] or ([names[registry]] if registry in names else [])
        geometry = data.get('resolved', {})
        fingerprint = visual_fingerprint(data) if model in model_inventory else None
        aliases = sorted(used_fingerprints.get(fingerprint, set()) - {model}) if fingerprint else []
        applied, inherited = direct.get(model, 0), dependencies.get(model, 0)
        used_by = data.get('used_by', [])
        kind = current.get('kind', classification.get('kind', 'UNCLASSIFIED'))
        if kind == 'UNCLASSIFIED':
            kind = 'UNCHANGED_VANILLA' if model in vanilla_models else 'SOURCE_DEFECT'
        model_exists = model in pack_models or model in vanilla_models
        source_issues = geometry.get('issues', []) + source_parse_defects.get(model, [])
        if not model_exists:
            source_issues += [{'type': 'MISSING_MODEL', 'model': model, 'detail': 'Referenced source model absent from v15 and vanilla 1.20.1'}]
        if applied:
            status = 'PRESENT_DIRECT_APPLICATION'
        elif inherited:
            status = 'PRESENT_PARENT_DEPENDENCY'
        elif aliases:
            status = 'ABSENT_PATH_VISUAL_DUPLICATE_PRESENT'
        elif not used_by:
            status = 'ABSENT_NO_BLOCKSTATE_REFERENCE'
        else:
            status = 'ABSENT_NO_SELECTED_MAP_APPLICATION'
        row = {
            'sourceModel': model, 'russianName': '; '.join(display_names),
            'numericId': current.get('numericId', ''), 'registryId': registry,
            'offeredTargets': targets, 'kind': kind,
            'scope': 'PACK_MODEL_FILE' if model in pack_models else ('INHERITED_VANILLA_MODEL' if model_exists else 'MISSING_SOURCE_REFERENCE'),
            'directMapApplications': applied, 'parentMapApplications': inherited,
            'status': status, 'presentVisualAliases': ';'.join(aliases),
            'sourceStates': current.get('sourceStates', '') if isinstance(current.get('sourceStates', ''), str) else ';'.join(current.get('sourceStates', [])),
            'sample': json.dumps(usage['direct_models'].get(model, {}).get('sample', {}), ensure_ascii=False, sort_keys=True),
            'sourceIssues': json.dumps(source_issues, ensure_ascii=False, sort_keys=True),
            'sourceNote': current.get('missing', ''),
        }
        model_rows.append(row)
    carrier_fields = list(carrier_rows[0])
    state_fields = list(state_rows[0])
    model_fields = list(model_rows[0])
    absent_carriers = [row for row in carrier_rows if row['status'] == 'ABSENT_CARRIER']
    absent_states = [row for row in state_rows if row['status'] == 'ABSENT_STATE_BRANCH']
    absent_models = [row for row in model_rows if row['status'].startswith('ABSENT_')]
    independent = [row for row in absent_models if row['kind'] in INDEPENDENT and not row['presentVisualAliases'] and row['scope'] == 'PACK_MODEL_FILE']
    indexed_models = {row['sourceModel']: row for row in model_rows}
    absent_whole = []
    for obj in candidates['objects']:
        if obj.get('sourceClassification') != 'WHOLE_SOURCE_ASSEMBLY':
            continue
        registry = 'bloodborne_dw:' + obj['path']
        for variant in obj['variants']:
            parts = sorted({normalized(app['model']) for app in variant.get('closed', []) + variant.get('open', [])})
            visible = [model for model in parts if model_inventory.get(model, {}).get('resolved', {}).get('elements') and not indexed_models[model]['kind'].startswith('SOURCE_TRANSPARENT')]
            # This is a sufficient absence proof, never an inference that
            # individually present pieces occur together in a whole object.
            if visible and all(not direct.get(model) and not indexed_models[model]['presentVisualAliases'] for model in visible):
                row = {field: '' for field in model_fields}
                full_name = variant['name'] if variant['name'] == obj['displayName'] else obj['displayName'] + ' · ' + variant['name']
                row.update(sourceModel=';'.join(parts), russianName=full_name, numericId=numbers.get(registry, ''), registryId=registry,
                           offeredTargets=f'{registry}[variant={variant["variant"]}]', kind='WHOLE_SOURCE_ASSEMBLY', scope='COMPLETE_AUTHORED_SOURCE_ASSEMBLY',
                           directMapApplications=0, parentMapApplications=0, status='ABSENT_ALL_VISIBLE_SOURCE_PARTS',
                           sourceNote='Every visible source part has no direct map application or applied exact visual alias; no claim about combinations of present parts.')
                absent_whole.append(row)
    csv_file('all-carriers.csv', carrier_rows, carrier_fields)
    csv_file('absent-blocks.csv', absent_carriers, carrier_fields)
    csv_file('absent-states.csv', absent_states, state_fields)
    csv_file('all-models.csv', model_rows, model_fields)
    csv_file('absent-models.csv', absent_models, model_fields)
    csv_file('absent-objects.csv', independent + absent_whole, model_fields)
    csv_file('absent-objects-short.csv', independent + absent_whole, ['numericId', 'russianName', 'registryId', 'offeredTargets', 'sourceModel'])
    csv_file('absent-whole-objects.csv', absent_whole, model_fields)
    summary = {
        'status': 'PASS', 'analysis_commit': subprocess.check_output(['C:/Program Files/Git/cmd/git.exe', 'rev-parse', 'HEAD'], cwd=ROOT.parent, text=True).strip(),
        'map_sha256': EXPECTED_MAP, 'pack_sha256': EXPECTED_PACK,
        'source_archive_unchanged': True, 'terrain_regions': world['regions_total'],
        'terrain_chunks': world['chunks_scanned'], 'terrain_sections': world['sections_scanned'],
        'world_unique_block_ids': len(carrier_counts), 'world_unique_states': len(world['states']),
        'affected_carriers_checked': len(carrier_rows), 'pack_blockstate_overrides_checked': len(overrides),
        'pack_models_checked': len(pack_models), 'branches_checked': len(state_rows),
        'fully_absent_carriers': len(absent_carriers),
        'fully_absent_pack_blockstate_carriers': sum(row['scope'] == 'PACK_BLOCKSTATE_OVERRIDE' for row in absent_carriers),
        'fully_absent_inherited_carriers': sum(row['scope'] != 'PACK_BLOCKSTATE_OVERRIDE' for row in absent_carriers),
        'absent_branches': len(absent_states),
        'absent_branches_on_present_carriers': sum(bool(row['carrierPlacedCells']) for row in absent_states),
        'independent_absent_source_models': len(independent),
        'absent_complete_assembly_variants_proven_by_all_parts': len(absent_whole),
        'absent_complete_assembly_types_proven_by_all_parts': len({row['registryId'] for row in absent_whole}),
        'independent_absent_by_kind': dict(Counter(row['kind'] for row in independent)),
        'all_absent_model_paths_by_kind': dict(sorted(Counter(row['kind'] for row in absent_models).items())),
        'all_model_statuses': dict(sorted(Counter(row['status'] for row in model_rows).items())),
        'matching_checks': checks, 'positional_rng_checks': usage.get('rng_validation', {}),
        'scope': 'All stored terrain blocks in every region entry of original ZIP. Not inventories, CIT, entities, occlusion or ungenerated terrain.',
        'world_state_evidence_sha256': sha(OUT / 'world-states.json'),
        'model_application_evidence_sha256': sha(OUT / 'model-usage.json'),
    }
    dump(OUT / 'summary.json', summary)
    lines = [
        '# Ресурспак v15: блоки и варианты, отсутствующие в исходном мире v16', '',
        f'Проверка: **PASS**. Исходный commit анализа: `{summary["analysis_commit"]}`.', '',
        f'Прочитаны все **{world["regions_total"]} регионов / {world["chunks_scanned"]:,} чанков** исходного ZIP. Найдены **{len(carrier_counts)} ID / {len(world["states"])} реально использованных состояний**. Измерение: `minecraft:overworld`; иных terrain-регионов в ZIP нет. Мир имеет DataVersion 2975 (1.18.2). Проверены все сохранённые чанки, включая неполностью сгенерированные.', '',
        'Карта только прочитана. Содержимое ZIP не извлекалось и не изменялось; SHA до и после совпали. Считались положительные индексы ячеек, а не простое наличие записи в палитре.', '',
        '## Итоговые списки', '',
        f'- **{summary["fully_absent_pack_blockstate_carriers"]} полностью отсутствующих носителей** из {len(overrides)} файлов blockstates самого ресурспака.',
        f'- Дополнительно **{summary["fully_absent_inherited_carriers"]} отсутствующих ванильных носителей**, затронутых наследованием моделей или текстурами пакета. Они выделены отдельно: их blockstates JSON в ZIP нет.',
        f'- **{len(independent)} законченных самостоятельных исходных моделей** отсутствуют и напрямую, и среди родительских зависимостей; совпадающая используемая визуальная модель также не найдена.',
        f'- Дополнительно **{len(absent_whole)} законченных составных вариантов** ({summary["absent_complete_assembly_types_proven_by_all_parts"]} типов), у которых все видимые исходные части отсутствуют. Они тоже включены в главный CSV.',
        f'- **{summary["absent_branches_on_present_carriers"]} отсутствующих веток состояний** у носителей, которые в других состояниях на карте присутствуют. Это включает повороты/связи/позы одного объекта, поэтому число не равно числу новых объектов.', '',
        'Краткий список для просмотра: [absent-objects-short.csv](absent-objects-short.csv). Главная таблица с причинами: [absent-objects.csv](absent-objects.csv). ID носителей: [absent-blocks.csv](absent-blocks.csv). Все отсутствующие исходные модельные пути, включая служебные части и дефекты: [absent-models.csv](absent-models.csv). Состояния: [absent-states.csv](absent-states.csv).', '',
        'Русские названия декоративных объектов взяты из текущего каталога мода; это каталожные подписи, а точный исходный идентификатор дан рядом. Русское название ванильного носителя не обязательно описывает его декоративную модель.', '',
        '## Полностью отсутствующие носители из blockstates ресурспака', '',
        '| ID носителя | Русское название носителя |', '|---|---|',
    ]
    for row in absent_carriers:
        if row['scope'] == 'PACK_BLOCKSTATE_OVERRIDE':
            lines.append(f'| `{row["sourceBlock"]}` | {row["russianCarrierName"]} |')
    lines += ['', '## Законченные самостоятельные модели, отсутствующие в мире', '',
              '| № мода | Каталожное название | Исходная модель | Причина |', '|---|---|---|---|']
    for row in independent:
        reason = 'Нет blockstate-привязки' if row['status'] == 'ABSENT_NO_BLOCKSTATE_REFERENCE' else 'На карте не выбрана'
        lines.append(f'| {row["numericId"]} | {row["russianName"]} | `{row["sourceModel"]}` | {reason} |')
    lines += ['', '## Цельные составные варианты с полностью отсутствующими частями', '',
              '| № мода | Название | Вариант | Исходные части |', '|---|---|---|---|']
    for row in absent_whole:
        lines.append(f'| {row["numericId"]} | {row["russianName"]} | `{row["offeredTargets"]}` | `{row["sourceModel"]}` |')
    lines += ['', 'Это отдельное достаточное доказательство отсутствия целого объекта. Комбинации уже присутствующих частей не объявлялись отсутствующими на основании частоты отдельных моделей; отсутствующие raw-состояния приведены отдельно в `absent-states.csv`.', '']
    lines += ['', '## Служебные части, дубликаты и дефекты', '', '| Класс исходной модели | Отсутствующих путей |', '|---|---:|']
    for kind, count in summary['all_absent_model_paths_by_kind'].items():
        lines.append(f'| `{kind}` | {count} |')
    lines += ['', 'Служебные multipart/assembly-части, пустые или прозрачные helpers, шаблоны, предметные ресурсы, намеренные исключения и повреждённые исходники не включены в главный список самостоятельных законченных объектов. Они сохранены в полной таблице. Для точного дубликата с присутствующим аналогом стоит `ABSENT_PATH_VISUAL_DUPLICATE_PRESENT`: отсутствует этот JSON-путь, но его визуальный аналог присутствует.', '',
              '## Метод и границы проверки', '',
              'Variant-условия сопоставлены с подмножеством NBT Properties; лишние свойства не мешают совпадению. Multipart учитывает OR/AND и альтернативы через |. Все weighted-применения рассчитаны по сохранённым XYZ и позиционному Java RNG; наличие носителя не приравнено к наличию каждой случайной модели. Родительские модели учитываются отдельно. Отчёт описывает логическое применение моделей, независимо от закрытости соседями и видимости камерой.', '',
              'Проверялись размещённые terrain-блоки. Предметы в сундуках, CIT, entity-held/falling blocks и block-entity NBT не являются terrain-размещениями и здесь не подсчитывались. Отсутствие в сохранённом мире не предсказывает будущую генерацию. Игровая проверка, пересоздание галереи и изменения мода для этой аналитической задачи не выполнялись.', '',
              f'Ресурспак SHA-256: `{EXPECTED_PACK}`.  ', f'Карта SHA-256: `{EXPECTED_MAP}`.', '',
              'Доказательства: [world-states.json](world-states.json), [model-usage.json](model-usage.json), [summary.json](summary.json). В `all-carriers.csv` и `all-models.csv` сохранены также присутствующие строки и координатные примеры.', '']
    (OUT / 'REPORT.md').write_text('\n'.join(lines), encoding='utf-8')
    (OUT / 'audit-status.txt').write_text('PASS\n', encoding='utf-8')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / 'audit-status.txt').write_text(traceback.format_exc(), encoding='utf-8')
        raise
