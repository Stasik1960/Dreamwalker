"""Generate two static visual slots. Gameplay geometry and registry IDs are unchanged.

Run AFTER the authored QA compiler. No discovery or world reads.
"""
import copy
import gzip
import hashlib
import json
import os
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
LOGICAL = RES / 'bloodborne_blocks/logical'


def key(values):
    return ','.join(f'{k}={v}' for k, v in sorted(values.items()))


def strip_visual(state):
    return key({k: v for k, v in (p.split('=', 1) for p in state.split(',') if p)
                if k != 'visual'})


def state_name(state):
    # Names remain stable across generator ordering and new neighboring families.
    return 'default' if not state else state.replace('=', '_').replace(',', '__')


def expand(mapping, *, contracts=False):
    baseline = {strip_visual(k): v for k, v in mapping.items()
                if 'visual=alt' not in k.split(',')}
    result = {}
    for state, value in baseline.items():
        values = dict(p.split('=', 1) for p in state.split(',') if p)
        for mode in ('base', 'alt'):
            entry = copy.deepcopy(value)
            if contracts and mode == 'alt':
                entry['migration_source_pattern'] = []
            result[key({**values, 'visual': mode})] = entry
    return result


def write(path, data):
    raw = (json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf8')
    if not path.exists() or path.read_bytes() != raw:
        path.parent.mkdir(parents=True, exist_ok=True)
        # Publish complete JSON atomically; readers must never see truncation.
        with tempfile.NamedTemporaryFile(dir=path.parent,prefix='.visual-',delete=False) as stream:
            temporary=Path(stream.name)
            stream.write(raw)
        try: os.replace(temporary,path)
        finally: temporary.unlink(missing_ok=True)


def build(resources=RES):
    logical = resources / 'bloodborne_blocks/logical'
    definitions = json.loads((logical / 'definitions.json').read_text(encoding='utf8'))
    geometry = json.loads((logical / 'geometry.json').read_text(encoding='utf8'))
    contracts = json.loads((logical / 'contracts-v2.json').read_text(encoding='utf8'))
    family_map = {f['id']: f for f in contracts['families']}
    hidden = set(json.loads((logical / 'hidden-items.json').read_text(encoding='utf8')))
    aliases = json.loads((resources / 'bloodborne_blocks/aliases.json').read_text(encoding='utf8'))
    hidden.update(aliases.get('removed', [])); hidden.update(aliases.get('aliases', {}))
    with gzip.open(logical / 'meshes.json.gz', 'rt', encoding='utf8') as stream:
        meshes = json.load(stream)
    report = {'schemaVersion': 1, 'slots': ['base', 'alt'], 'families': []}
    for block in definitions['blocks']:
        ident = block['id']
        if ident in hidden or not block.get('creative', True):
            continue
        original_models = {strip_visual(k): v for k, v in block['models'].items()
                           if 'visual=alt' not in k.split(',')}
        block['properties']['visual'] = ['base', 'alt']
        block['default']['visual'] = 'base'
        block['states'] = expand(block['states'])
        block['models'] = expand(block['models'])
        block['visual_models'] = {}
        geometry['blocks'][ident]['states'] = expand(geometry['blocks'][ident]['states'])
        if ident in family_map:
            family_map[ident]['states'] = expand(family_map[ident]['states'], contracts=True)
        states = {}
        for state, mesh in sorted(original_models.items()):
            stem = state_name(state)
            base = f'bloodborne_blocks:block/logical/{ident}/base/{stem}'
            alt = f'bloodborne_blocks:block/logical/{ident}/alt/{stem}'
            textures = sorted({p['texture'] for p in meshes[mesh]['polygons']})
            slots = {f't{i}': t for i, t in enumerate(textures)}
            write(resources / f'assets/bloodborne_blocks/models/block/logical/{ident}/base/{stem}.json',
                  {'parent': 'minecraft:block/block', 'bloodborne_mesh': mesh,
                   'bloodborne_texture_slots': {t: f'#t{i}' for i, t in enumerate(textures)},
                   'textures': {'particle': textures[0] if textures else 'minecraft:block/stone', **slots}})
            write(resources / f'assets/bloodborne_blocks/models/block/logical/{ident}/alt/{stem}.json', {'parent': base})
            props = dict(p.split('=', 1) for p in state.split(',') if p)
            for mode, path in (('base', base), ('alt', alt)):
                full_key = key({**props, 'visual': mode})
                block['visual_models'][full_key] = path
                states[full_key] = {'model': path}
        write(resources / f'assets/bloodborne_blocks/blockstates/{ident}.json', {'variants': states})
        item_path = resources / f'assets/bloodborne_blocks/models/item/{ident}.json'
        item = json.loads(item_path.read_text(encoding='utf8'))
        item['parent'] = block['visual_models'][key({**block['default'],**block.get('placement_properties',{})})]
        write(item_path, item)
        report['families'].append({'id': ident, 'states': len(states), 'base_states': len(original_models)})
    for name, data in (('definitions.json', definitions), ('geometry.json', geometry), ('contracts-v2.json', contracts)):
        write(logical / name, data)
    write(logical / 'visual-slots.json', report)
    print(f'Visual slots: {len(report["families"])} families, {sum(f["states"] for f in report["families"])} states')
    return report


if __name__ == '__main__':
    build()
