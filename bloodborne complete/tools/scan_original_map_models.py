#!/usr/bin/env python3
"""Exact read-only source model applications in original v16 terrain ZIP.

Counts standard 1.20.1 baked logical model choices, before occlusion/rendering.
Every weighted choice is resolved at the actual source cell XYZ. Only reports
are written; no world file is extracted, edited, converted, or opened by a game.
"""
from __future__ import annotations

from collections import Counter, defaultdict
from datetime import datetime, timezone
import ast
import copy
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import struct
import subprocess
import sys
import time
import traceback
import zipfile

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
LEGACY_TOOLS = ROOT.parent / 'Bloodborne-Blocks/tools'
sys.path.append(str(LEGACY_TOOLS))
try:
    from scan_production_usage import weighted_indices
    from source_variant_rng import weighted_index, JavaRandom, position_seed, signed
    from scan_original_map_states import decode_indices, state_details, dimension_for, sha256
    from world_io import RegionFile, compound, TAG_LIST, TAG_COMPOUND, TAG_LONG_ARRAY
    # Load only this pure existing function. Importing the legacy pipeline also
    # imports Pillow geometry renderers which this read-only scan never uses.
    helper_source = (LEGACY_TOOLS / 'source_assembly_pipeline.py').read_text(encoding='utf8')
    helper_ast = ast.parse(helper_source)
    helper_node = next(node for node in helper_ast.body if isinstance(node, ast.FunctionDef) and node.name == 'active_choices')
    helper_namespace = {'copy': copy}
    exec(compile(ast.Module(body=[helper_node], type_ignores=[]), str(LEGACY_TOOLS / 'source_assembly_pipeline.py'), 'exec'), helper_namespace)
    active_choices = helper_namespace['active_choices']
except BaseException:
    failure = ROOT / 'docs/map-pack-absence'
    failure.mkdir(parents=True, exist_ok=True)
    (failure / 'model-scan-status.txt').write_text(traceback.format_exc(), encoding='utf8')
    raise

PACK = ROOT.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
WORLD = ROOT.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v16_map.zip'
VANILLA = Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')
OUT = ROOT / 'docs/map-pack-absence'
EXPECTED_PACK = '29b31e3744af5794ae95ebe4c5474e3b70dfb08c143355bce815c08ceef09a36'
EXPECTED_WORLD = '39e83f2941b768fd2ae9f84d8f57dc76aec78bd926dc5e38eab3059251d2bb08'
REGION = re.compile(r'r\.(-?\d+)\.(-?\d+)\.mca$')


def utc():
    return datetime.now(timezone.utc).isoformat()


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def normalized_model(model):
    return model if ':' in model else 'minecraft:' + model


def condition(where, properties):
    # Exactly the raw vanilla condition syntax used by the supplied archive.
    if 'OR' in where:
        if len(where) != 1:
            raise ValueError('mixed OR condition requires separate vanilla validation')
        return any(condition(value, properties) for value in where['OR'])
    if 'AND' in where:
        if len(where) != 1:
            raise ValueError('mixed AND condition requires separate vanilla validation')
        return all(condition(value, properties) for value in where['AND'])
    if any(str(value).startswith('!') for value in where.values()):
        raise ValueError('negated condition requires separate vanilla validation')
    return all(properties.get(key) in property_string(value).split('|') for key, value in where.items())


def property_string(value):
    # Gson JsonPrimitive.getAsString() returns lowercase booleans; Python str()
    # does not. The original pack has genuine JSON boolean wall conditions.
    return 'true' if value is True else 'false' if value is False else str(value)


def normalize_condition(where):
    return {key: [normalize_condition(value) for value in values] if key in ('OR', 'AND') else property_string(values) for key, values in where.items()}


def branches_for(raw, properties):
    rows = []
    for key, value in raw.get('variants', {}).items():
        fields = dict(part.split('=', 1) for part in key.split(',') if '=' in part)
        if condition(fields, properties):
            rows.append(('variant:' + key, value))
    for index, part in enumerate(raw.get('multipart', [])):
        where = part.get('when', {})
        if condition(where, properties):
            label = 'multipart:' + str(index) + ':' + json.dumps(where, sort_keys=True, separators=(',', ':'))
            rows.append((label, part['apply']))
    normalized_raw = copy.deepcopy(raw)
    for part in normalized_raw.get('multipart', []):
        if 'when' in part:
            part['when'] = normalize_condition(part['when'])
    checked = active_choices(normalized_raw, properties)
    groups = [[[app] for app in (value if isinstance(value, list) else [value])] for _, value in rows]
    if groups != checked:
        raise ValueError('independent condition matching disagrees with active_choices')
    return [(label, group) for (label, _), group in zip(rows, checked)]


def raw_blockstates():
    rows = {}
    direct = set()
    with zipfile.ZipFile(VANILLA) as archive:
        for name in archive.namelist():
            if '/blockstates/' in name and name.endswith('.json'):
                namespace = name.split('/')[1]
                carrier = namespace + ':' + name.split('/blockstates/', 1)[1][:-5]
                rows[carrier] = json.loads(archive.read(name))
    with zipfile.ZipFile(PACK) as archive:
        prefixes = [name[:-len('pack.mcmeta')] for name in archive.namelist() if name.endswith('pack.mcmeta')]
        if len(prefixes) != 1:
            raise ValueError('resource pack requires exactly one pack.mcmeta')
        for name in archive.namelist():
            short = name.removeprefix(prefixes[0])
            if short.startswith('assets/') and '/blockstates/' in short and short.endswith('.json'):
                namespace = short.split('/')[1]
                carrier = namespace + ':' + short.split('/blockstates/', 1)[1][:-5]
                rows[carrier] = json.loads(archive.read(name))
                direct.add(carrier)
    inventory = json.loads((ROOT / 'docs/remaining-transfer/source-inventory.json').read_text(encoding='utf8'))
    if inventory['summary']['pack_sha256'] != EXPECTED_PACK:
        raise ValueError('source inventory describes another resource ZIP')
    affected = {row['source'] for row in inventory['states']}
    carriers = direct | affected
    if set(carriers) - set(rows):
        raise ValueError('missing blockstate resources: ' + str(sorted(set(carriers) - set(rows))))
    return {key: rows[key] for key in carriers}, direct, affected


def rng_validation():
    # Store authoritative cached bytecode alongside scalar/vector comparisons.
    javap = Path('C:/Users/vakir/Downloads/jdk-21.0.10-full/bin/javap.exe')
    bytecode = subprocess.run([str(javap), '-classpath', str(VANILLA), '-c', '-p', 'apa', 'fxd', 'fxa', 'dgz', 'dhl', 'bck'], capture_output=True, text=True, check=True).stdout
    (OUT / 'rng-bytecode.txt').write_text(bytecode, encoding='utf8')
    xyz = np.random.default_rng(1201011).integers([-30000000, -2048, -30000000], [30000000, 2048, 30000000], size=(5000, 3), dtype=np.int64)
    xyz = np.concatenate((xyz, np.array([[0, 0, 0], [324, 69, -155], [9123, 64, -512]], dtype=np.int64)))
    comparisons = 0
    for weights in ([1, 1], [1, 2, 7], [100, 50, 500], [3, 2, 1, 8]):
        for multipart in (False, True):
            vector = weighted_indices(weights, xyz, multipart).tolist()
            scalar = [weighted_index(weights, tuple(map(int, position)), multipart) for position in xyz]
            if vector != scalar:
                raise ValueError('vectorized RNG differs from verified scalar RNG')
            comparisons += len(xyz)
    if JavaRandom(0).next_long() != -4962768465676381896:
        raise ValueError('JavaRandom fixed reference mismatch')
    return {'status': 'PASS', 'scalar_vector_comparisons': comparisons, 'coordinate_samples': len(xyz), 'bytecode': 'rng-bytecode.txt', 'bytecode_sha256': hashlib.sha256(bytecode.encode()).hexdigest(), 'int_min_policy': 'Math.abs(Integer.MIN_VALUE) remains negative; Weighting.getAt returns first entry for negative ticket; helper maps that outcome to option 0', 'seed_policy': 'int32 x*3129871, int64 z*116129781, signed long square/add and arithmetic >>16; classic LCG signed nextLong halves; multipart pre-nextLong then reset same seed for every applicable child'}


def min_value_hits(xyz, multipart):
    # The production helper maps MIN_VALUE to 0. Track actual hits explicitly.
    coordinates = np.asarray(xyz, dtype=np.int64)
    x = (coordinates[:, 0] * 3129871).astype(np.int32).astype(np.int64)
    with np.errstate(over='ignore'):
        value = x ^ (coordinates[:, 2] * 116129781) ^ coordinates[:, 1]
        seed = (value * value * 42317861 + value * 11) >> 16
    def nextlong(s):
        mask = np.uint64((1 << 48) - 1)
        s = (s.astype(np.uint64) ^ np.uint64(25214903917)) & mask
        s = (s * np.uint64(25214903917) + np.uint64(11)) & mask
        hi = (s >> np.uint64(16)).astype(np.uint32).view(np.int32).astype(np.int64)
        s = (s * np.uint64(25214903917) + np.uint64(11)) & mask
        lo = (s >> np.uint64(16)).astype(np.uint32).view(np.int32).astype(np.int64)
        return (hi << 32) + lo
    if multipart:
        seed = nextlong(seed)
    return np.flatnonzero(nextlong(seed).astype(np.int32) == -(1 << 31))


def seed_override_audit():
    """Find every declared block seed override in the exact vanilla class files."""
    mapping = Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2/mappings.tiny')
    classes = {}
    for line in mapping.read_text(encoding='utf8').splitlines():
        fields = line.split('\t')
        if fields[0] == 'c' and fields[3].startswith('net/minecraft/block/'):
            classes[fields[1]] = fields[3]
    def methods(payload):
        at = 8
        count = struct.unpack_from('>H', payload, at)[0]; at += 2
        constants = [None] * count
        slot = 1
        while slot < count:
            kind = payload[at]; at += 1
            if kind == 1:
                length = struct.unpack_from('>H', payload, at)[0]; at += 2
                constants[slot] = payload[at:at + length].decode('utf8', errors='replace'); at += length
            elif kind in (3, 4): at += 4
            elif kind in (5, 6): at += 8; slot += 1
            elif kind in (7, 8, 16, 19, 20): at += 2
            elif kind in (9, 10, 11, 12, 17, 18): at += 4
            elif kind == 15: at += 3
            else: raise ValueError('unsupported class constant tag ' + str(kind))
            slot += 1
        at += 6
        interfaces = struct.unpack_from('>H', payload, at)[0]; at += 2 + interfaces * 2
        def skip_members(offset, retain=False):
            count = struct.unpack_from('>H', payload, offset)[0]; offset += 2
            values = []
            for _ in range(count):
                access, name, desc, attrs = struct.unpack_from('>HHHH', payload, offset); offset += 8
                if retain: values.append({'access': access, 'name': constants[name], 'descriptor': constants[desc]})
                for __ in range(attrs):
                    length = struct.unpack_from('>I', payload, offset + 2)[0]; offset += 6 + length
            return offset, values
        at, _ = skip_members(at)
        at, result = skip_members(at, True)
        return result
    declared = []
    with zipfile.ZipFile(VANILLA) as archive:
        for obfuscated, named in classes.items():
            for method in methods(archive.read(obfuscated + '.class')):
                if method['name'] == 'a' and method['descriptor'] == '(Ldcb;Lgu;)J':
                    declared.append({'class': named, 'obfuscated': obfuscated, 'method': method})
    raw, direct, affected = raw_blockstates()
    weighted = []
    for carrier, value in raw.items():
        choices = list(value.get('variants', {}).values()) + [part['apply'] for part in value.get('multipart', [])]
        if any(isinstance(choice, list) and len(choice) > 1 for choice in choices):
            weighted.append(carrier)
    javap = Path('C:/Users/vakir/Downloads/jdk-21.0.10-full/bin/javap.exe')
    overrides = [row['obfuscated'] for row in declared if not row['class'].endswith('/AbstractBlock')]
    bytecode = subprocess.run([str(javap), '-classpath', str(VANILLA), '-c', '-p', *overrides], capture_output=True, text=True, check=True).stdout
    (OUT / 'seed-override-bytecode.txt').write_text(bytecode, encoding='utf8')
    tall_plant_carriers = {'minecraft:' + name for name in ('sunflower', 'lilac', 'rose_bush', 'peony', 'tall_grass', 'large_fern', 'tall_seagrass', 'pitcher_plant', 'pitcher_crop')}
    seed_override_carriers = {carrier for carrier in raw if carrier.endswith('_door') or carrier.endswith('_bed')} | tall_plant_carriers
    overlap = sorted(set(weighted) & seed_override_carriers)
    result = {'status': 'PASS', 'declared_seed_methods': declared, 'weighted_carriers': sorted(weighted), 'weighted_seed_override_carriers': overlap, 'overlap_interpretation': 'No weighted carrier in this supplied source pack is a DoorBlock, BedBlock, or TallPlantBlock carrier; their special seed has no model selection effect.', 'mapping_sha256': sha256(mapping), 'vanilla_jar_sha256': sha256(VANILLA), 'bytecode': 'seed-override-bytecode.txt'}
    dump(OUT / 'seed-override-audit.json', result)
    return result


def condition_validation():
    checks = [({'up': True}, {'up': 'true'}, True), ({'up': True}, {'up': 'false'}, False), ({'up': False}, {'up': 'false'}, True), ({'OR': [{'north': 'low|tall'}, {'up': True}]}, {'north': 'none', 'up': 'true'}, True), ({'AND': [{'north': 'low|tall'}, {'up': True}]}, {'north': 'tall', 'up': 'false'}, False)]
    for where, props, expected in checks:
        if condition(where, props) is not expected:
            raise ValueError('condition reference fixture failed')
    raw, _, _ = raw_blockstates()
    evidence = json.loads((OUT / 'world-states.json').read_text(encoding='utf8'))
    observed = 0
    for state in evidence['states']:
        if state['name'] not in raw:
            continue
        groups = branches_for(raw[state['name']], state['properties'])
        if sum(label.startswith('variant:') for label, _ in groups) > 1:
            raise ValueError('overlapping vanilla variant definitions at observed state ' + state['state'])
        observed += 1
    max_sum = 0
    bool_branches = []
    for carrier, value in raw.items():
        options = list(value.get('variants', {}).values()) + [part['apply'] for part in value.get('multipart', [])]
        for option in options:
            weight_sum = sum(app.get('weight', 1) for app in (option if isinstance(option, list) else [option]))
            if weight_sum > 2147483647:
                raise ValueError('sum of source model weights exceeds vanilla int bound')
            max_sum = max(max_sum, weight_sum)
        for index, part in enumerate(value.get('multipart', [])):
            if re.search(r':\s*(true|false)', json.dumps(part.get('when', {}))):
                bool_branches.append({'source': carrier, 'multipart_index': index, 'when': part.get('when', {})})
    result = {'status': 'PASS', 'literal_reference_fixtures': len(checks), 'observed_states_checked': observed, 'overlapping_observed_variant_definitions': 0, 'max_source_weight_sum': max_sum, 'json_boolean_branches': bool_branches, 'integer_min_no_overflow_in_first_weight_subtraction': max_sum <= (1 << 30)}
    dump(OUT / 'model-condition-validation.json', result)
    return result


def validate_report():
    report = json.loads((OUT / 'model-usage.json').read_text(encoding='utf8'))
    if report['status'] != 'PASS':
        raise ValueError('model report is not PASS')
    raw, _, _ = raw_blockstates()
    states = {row['state']: row for row in json.loads((OUT / 'world-states.json').read_text(encoding='utf8'))['states']}
    for branch in report['branches'].values():
        if sum(branch['applications'].values()) != branch['count'] or sum(branch['weighted_choices'].values()) != branch['count']:
            raise ValueError('weighted branch totals disagree: ' + branch['source'] + '|' + branch['branch'])
    if sum(row['count'] for row in report['direct_models'].values()) != sum(row['count'] for row in report['branches'].values()):
        raise ValueError('global direct application counts disagree with branch totals')
    checked = 0
    for model, row in report['direct_models'].items():
        if sum(row['dimensions'].values()) != row['count']:
            raise ValueError('model dimension counts disagree')
        sample = row['sample']
        state = states[sample['state']]
        branches = dict(branches_for(raw[state['name']], state['properties']))
        group = branches[sample['branch']]
        choice = weighted_index([option[0].get('weight', 1) for option in group], (sample['x'], sample['y'], sample['z']), 'multipart' in raw[state['name']])
        expected = normalized_model(group[choice][0]['model'])
        if expected != model:
            raise ValueError('scalar source-XYZ sample selects different model: ' + model + ' vs ' + expected)
        checked += 1
    result = {'status': 'PASS', 'model_samples_verified_with_scalar_rng': checked, 'branches_balanced': len(report['branches']), 'model_dimensions_balanced': len(report['direct_models']), 'global_direct_application_count': sum(row['count'] for row in report['direct_models'].values()), 'condition_validation': condition_validation()}
    dump(OUT / 'model-report-validation.json', result)
    return result


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'model-scan-status.txt').write_text('RUNNING\n', encoding='utf8')
    started = utc()
    start = time.monotonic()
    world_sha = sha256(WORLD)
    pack_sha = sha256(PACK)
    if world_sha != EXPECTED_WORLD or pack_sha != EXPECTED_PACK:
        raise ValueError('unexpected source archive hashes')
    evidence = json.loads((OUT / 'world-states.json').read_text(encoding='utf8'))
    if evidence['status'] != 'PASS' or evidence['map_sha256_before'] != world_sha:
        raise ValueError('independent full state scan is absent or incomplete')
    rawstates, direct, affected = raw_blockstates()
    validation = rng_validation()
    validation['condition_validation'] = condition_validation()
    seed_audit = seed_override_audit()
    if seed_audit['weighted_seed_override_carriers']:
        raise ValueError('source contains weighted carrier with overridden rendering seed; explicit seed normalization is required')
    validation['seed_override_audit'] = 'seed-override-audit.json'
    validation['weighted_seed_override_carriers'] = seed_audit['weighted_seed_override_carriers']
    validation['condition_boolean_policy'] = 'JSON booleans use Gson lowercase true/false; legacy pure matcher receives normalized condition primitives only; source branch labels retain original JSON'
    counts = Counter()
    model_counts = Counter()
    model_dimensions = defaultdict(Counter)
    model_samples = {}
    branch_counts = Counter()
    branch_models = defaultdict(Counter)
    branch_options = defaultdict(Counter)
    branch_descriptors = {}
    groups_cache = {}
    errors = []
    regions = []
    chunks = 0
    sections = 0
    weighted_cells = 0
    min_value_cells = 0
    with zipfile.ZipFile(WORLD, 'r') as archive:
        entries = sorted(name for name in archive.namelist() if name.endswith('.mca') and PurePosixPath(name).parent.name == 'region')
        prefix = evidence.get('world_prefix')
        for number, entry in enumerate(entries, 1):
            row = {'zip_entry': entry, 'dimension': dimension_for(entry, prefix), 'chunks': 0, 'sections': 0, 'status': 'PASS'}
            regions.append(row)
            dimension = row['dimension']
            try:
                match = REGION.fullmatch(PurePosixPath(entry).name)
                if not match:
                    raise ValueError('invalid region filename')
                rx, rz = map(int, match.groups())
                for stored in RegionFile(archive.read(entry)).chunks():
                    chunks += 1
                    row['chunks'] += 1
                    root = compound(stored.nbt().root)
                    root = compound(root['Level']) if 'Level' in root else root
                    cx, cz = int(root['xPos'].value), int(root['zPos'].value)
                    if cx != rx * 32 + stored.x or cz != rz * 32 + stored.z:
                        raise ValueError('chunk coordinate disagrees with region location')
                    raw_sections = root.get('sections') or root.get('Sections')
                    if raw_sections is None:
                        continue
                    for section in raw_sections.value:
                        fields = compound(section)
                        packed = fields.get('block_states')
                        if packed is None:
                            continue
                        sections += 1
                        row['sections'] += 1
                        values = compound(packed)
                        palette = values['palette'].value
                        descriptors = [state_details(value) for value in palette]
                        wanted = [index for index, (key, carrier, props) in enumerate(descriptors) if carrier in rawstates]
                        if not wanted:
                            continue
                        data = values.get('data')
                        indices = decode_indices(data.value if data else [], len(palette))
                        sy = int(fields['Y'].value)
                        for index in wanted:
                            at = np.flatnonzero(indices == index)
                            if not len(at):
                                continue
                            key, carrier, props = descriptors[index]
                            amount = len(at)
                            counts[key] += amount
                            if key not in groups_cache:
                                groups_cache[key] = branches_for(rawstates[carrier], props)
                            groups = groups_cache[key]
                            positions = None
                            multipart = 'multipart' in rawstates[carrier]
                            for label, group in groups:
                                branch = carrier + '|' + label
                                branch_descriptors.setdefault(branch, {'source': carrier, 'branch': label})
                                branch_counts[branch] += amount
                                weights = [option[0].get('weight', 1) for option in group]
                                if not weights or any(type(weight) is not int or weight <= 0 for weight in weights):
                                    raise ValueError('invalid positive model weights: ' + branch)
                                if len(group) > 1:
                                    if positions is None:
                                        positions = np.column_stack((cx * 16 + (at & 15), sy * 16 + (at >> 8), cz * 16 + ((at >> 4) & 15))).astype(np.int64)
                                    choices = weighted_indices(weights, positions, multipart)
                                    weighted_cells += amount
                                    hits = min_value_hits(positions, multipart)
                                    min_value_cells += len(hits)
                                    option_counts = np.bincount(choices, minlength=len(group))
                                else:
                                    choices = None
                                    option_counts = [amount]
                                for option, count in enumerate(option_counts):
                                    count = int(count)
                                    if not count:
                                        continue
                                    branch_options[branch][str(option)] += count
                                    app = group[option][0]
                                    model = normalized_model(app['model'])
                                    model_counts[model] += count
                                    model_dimensions[model][dimension] += count
                                    branch_models[branch][model] += count
                                    if model not in model_samples:
                                        sample_index = int(at[int(np.flatnonzero(choices == option)[0])]) if choices is not None else int(at[0])
                                        model_samples[model] = {'dimension': dimension, 'x': cx * 16 + sample_index % 16, 'y': sy * 16 + sample_index // 256, 'z': cz * 16 + (sample_index // 16) % 16, 'state': key, 'branch': label, 'application': app}
            except Exception as error:
                row['status'] = 'FAIL'
                errors.append({'zip_entry': entry, 'phase': 'region', 'error': f'{type(error).__name__}: {error}', 'traceback': traceback.format_exc()})
            dump(OUT / 'model-progress.json', {'status': 'RUNNING', 'started_utc': started, 'updated_utc': utc(), 'regions_scanned': number, 'regions_total': len(entries), 'chunks_scanned': chunks, 'sections_scanned': sections, 'carrier_cells': sum(counts.values()), 'models': len(model_counts), 'errors': errors, 'elapsed_seconds': round(time.monotonic() - start, 2), 'last_region': entry})
    after_world = sha256(WORLD)
    after_pack = sha256(PACK)
    if world_sha != after_world or pack_sha != after_pack:
        errors.append({'phase': 'input', 'error': 'original source archive changed'})
    expected_counts = {row['state']: row['count'] for row in evidence['states'] if row['name'] in rawstates}
    if dict(counts) != expected_counts:
        mismatches = {key: {'model_scan': counts.get(key, 0), 'state_scan': expected_counts.get(key, 0)} for key in set(counts) | set(expected_counts) if counts.get(key, 0) != expected_counts.get(key, 0)}
        errors.append({'phase': 'cross_check', 'error': 'model scan cell counts differ from independent full state scan', 'mismatches': mismatches})
    if chunks != evidence['chunks_scanned'] or sections != evidence['sections_scanned']:
        errors.append({'phase': 'cross_check', 'error': 'model scan terrain extent differs from independent full state scan'})
    validation.update({'weighted_branch_cell_applications': weighted_cells, 'integer_min_value_branch_cell_hits': min_value_cells})
    result = {'format': 'original-v16-exact-source-model-usage-v1', 'status': 'PASS' if not errors else 'FAIL', 'started_utc': started, 'completed_utc': utc(), 'elapsed_seconds': round(time.monotonic() - start, 2), 'read_only': True, 'no_archive_extraction': True, 'membership_basis': 'actual decoded cells and source-XYZ standard 1.20.1 positional weighted model selection; logical applications before occlusion', 'map_sha256_before': world_sha, 'map_sha256_after': after_world, 'pack_sha256_before': pack_sha, 'pack_sha256_after': after_pack, 'vanilla_jar': str(VANILLA), 'vanilla_jar_sha256': sha256(VANILLA), 'actual_pack_carriers': sorted(direct), 'affected_carriers': sorted(affected), 'analyzed_carriers': len(rawstates), 'regions': regions, 'chunks_scanned': chunks, 'sections_scanned': sections, 'carrier_cells': sum(counts.values()), 'observed_carrier_states': len(counts), 'state_counts': dict(sorted(counts.items())), 'direct_models': {model: {'count': model_counts[model], 'dimensions': dict(sorted(model_dimensions[model].items())), 'sample': model_samples[model]} for model in sorted(model_counts)}, 'branches': {branch: {**branch_descriptors[branch], 'count': branch_counts[branch], 'applications': dict(sorted(branch_models[branch].items())), 'weighted_choices': dict(sorted(branch_options[branch].items()))} for branch in sorted(branch_counts)}, 'rng_validation': validation, 'errors': errors}
    dump(OUT / 'model-usage.json', result)
    dump(OUT / 'model-progress.json', {'status': result['status'], 'started_utc': started, 'completed_utc': result['completed_utc'], 'regions_scanned': len(regions), 'chunks_scanned': chunks, 'sections_scanned': sections, 'carrier_cells': result['carrier_cells'], 'models': len(model_counts), 'errors': errors, 'elapsed_seconds': result['elapsed_seconds']})
    (OUT / 'model-scan-status.txt').write_text(result['status'] + '\n', encoding='utf8')
    if errors:
        raise SystemExit(1)


if __name__ == '__main__':
    try:
        if '--seed-audit' in sys.argv:
            OUT.mkdir(parents=True, exist_ok=True)
            seed_override_audit()
        elif '--condition-audit' in sys.argv:
            OUT.mkdir(parents=True, exist_ok=True)
            condition_validation()
        elif '--validate-report' in sys.argv:
            validate_report()
        else:
            main()
    except BaseException:
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / 'model-scan-status.txt').write_text(traceback.format_exc(), encoding='utf8')
        raise
