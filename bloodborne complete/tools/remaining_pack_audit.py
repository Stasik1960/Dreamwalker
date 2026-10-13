"""Read source ZIP without extracting it; inventory every block state and model.

Audit outputs only. It never edits the map, galleries, or production resources.
"""
from __future__ import annotations
import argparse, csv, hashlib, json, re, zipfile
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT.parent / 'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
OUT = ROOT / 'docs/remaining-transfer'
BASE = '8b770a03de21554a21c91700a1c1c6fe86c894c4'
VANILLA = Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar')

def resource(value, kind='models', extension='.json'):
    namespace, name = value.split(':', 1) if ':' in value else ('minecraft', value)
    return f'assets/{namespace}/{kind}/{name}{extension}'

def model_id(path):
    namespace, relative = path.split('/models/')
    return namespace.split('/')[1] + ':' + relative[:-5]

def sha(data):
    return hashlib.sha256(data).hexdigest()

def refs(value):
    if isinstance(value, dict):
        if 'model' in value:
            yield {k: v for k, v in value.items() if k in ('model', 'x', 'y', 'uvlock', 'weight')}
        for item in value.values():
            yield from refs(item)
    elif isinstance(value, list):
        for item in value:
            yield from refs(item)

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(PACK) as archive:
        names = archive.namelist()
        prefix = next(name[:-len('pack.mcmeta')] for name in names if name.endswith('pack.mcmeta'))
        data = {}
        defects = []
        for name in names:
            short = name.removeprefix(prefix)
            if short.startswith('assets/') and short.endswith('.json'):
                try:
                    data[short] = json.loads(archive.read(name))
                except Exception as exc:
                    defects.append({'file': short, 'issue': 'INVALID_JSON', 'detail': str(exc)})
        packed = set(name.removeprefix(prefix) for name in names)
        vanilla = zipfile.ZipFile(VANILLA)
        vanilla_names = set(vanilla.namelist())
        def changed(path):
            if path not in packed:
                return False
            if path not in vanilla_names:
                return True
            first, second = archive.read(prefix + path), vanilla.read(path)
            if path.endswith('.json'):
                try:
                    first, second = json.loads(first), json.loads(second)
                    if '/models/' in path:
                        attrs = ('parent','textures','elements','ambientocclusion','gui_light')
                        first = {k: v for k, v in first.items() if k in attrs}
                        second = {k: v for k, v in second.items() if k in attrs}
                    return first != second
                except Exception:
                    return True
            return first != second
        def read(path):
            if path in packed:
                return archive.read(prefix + path)
            return vanilla.read(path)
        def load(path):
            if path not in data:
                data[path] = json.loads(read(path))
            return data[path]
        resolved = {}
        def resolve(key, visiting=()):
            key = key if ':' in key else 'minecraft:' + key
            if key in resolved:
                return resolved[key]
            if key in visiting:
                raise ValueError('Cyclic model: ' + key)
            path = resource(key)
            model = load(path)
            result = {'textures': {}, 'elements': [], 'dependencies': [path], 'issues': []}
            parent = model.get('parent')
            if parent and not parent.startswith('builtin/') and not parent.startswith('minecraft:builtin/'):
                try:
                    result = json.loads(json.dumps(resolve(parent, visiting + (key,))))
                    result['dependencies'].append(path)
                except Exception as exc:
                    result['issues'].append({'type': 'PARENT', 'parent': parent, 'detail': str(exc)})
            result['textures'].update(model.get('textures', {}))
            if 'elements' in model:
                result['elements'] = model['elements']
            for attr in ('ambientocclusion', 'gui_light', 'display'):
                if attr in model:
                    result[attr] = model[attr]
            result['issues'] = [issue for issue in result['issues'] if issue['type'] == 'PARENT']
            for key2, value in list(result['textures'].items()):
                seen = set()
                while isinstance(value, str) and value.startswith('#') and value[1:] not in seen:
                    seen.add(value[1:]); value = result['textures'].get(value[1:], value)
                result['textures'][key2] = value
            for element in result['elements']:
                for face in element.get('faces', {}).values():
                    texture = face.get('texture', '')
                    if texture.startswith('#') and result['textures'].get(texture[1:], texture).startswith('#'):
                        result['issues'].append({'type': 'TEXTURE_ALIAS', 'texture': texture})
            for texture in set(result['textures'].values()):
                if not isinstance(texture, str) or texture.startswith('#'):
                    continue
                path2 = resource(texture, 'textures', '.png')
                if path2 not in packed and path2 not in vanilla_names:
                    result['issues'].append({'type': 'MISSING_TEXTURE', 'texture': texture})
                else:
                    result['dependencies'].append(path2)
            result['affected'] = any(changed(path2) for path2 in result['dependencies'])
            result['fingerprint'] = sha(json.dumps({k:v for k,v in result.items() if k not in ('dependencies','issues','affected','fingerprint')}, sort_keys=True, separators=(',', ':')).encode())
            resolved[key] = result
            return result
        def safe_resolve(key):
            try:
                return resolve(key)
            except Exception as exc:
                return {'issues': [{'type': 'MODEL', 'detail': str(exc)}], 'affected': True, 'elements':[], 'textures':{}, 'dependencies':[]}
        for name in vanilla_names:
            if '/blockstates/' in name and name.endswith('.json') and name not in data:
                data[name] = json.loads(vanilla.read(name))
        model_paths = {name: value for name, value in data.items() if '/models/' in name}
        model_used = defaultdict(list)
        states = []
        for name, blockstate in list(data.items()):
            if '/blockstates/' not in name:
                continue
            carrier = name.split('/')[1] + ':' + Path(name).stem
            branches = [('variant:' + key, value) for key, value in blockstate.get('variants', {}).items()]
            branches += [('multipart:' + str(index) + ':' + json.dumps(value.get('when', {}), sort_keys=True, separators=(',', ':')), value.get('apply', {})) for index, value in enumerate(blockstate.get('multipart', []))]
            for state, branch in branches:
                models = list(refs(branch))
                if not any(safe_resolve(model['model'])['affected'] for model in models):
                    continue
                for model in models:
                    model_used[model['model']].append(carrier + '[' + state + ']')
                states.append({'source': carrier, 'state': state, 'models': models})
        for key in list(model_used):
            path = resource(key)
            if path not in model_paths and path in data:
                model_paths[path] = data[path]
        model_rows = []
        for name, model in sorted(model_paths.items()):
            key = model_id(name)
            direct = list(model.get('textures', {}).values())
            geometry = safe_resolve(key)
            model_rows.append({'model': key, 'file': name, 'parent': model.get('parent', ''), 'elements': len(model.get('elements', [])),
                               'textures': direct, 'used_by': model_used.get(key, []) or model_used.get(key.removeprefix('minecraft:'), []), 'sha256': sha(read(name)), 'resolved':geometry})
        summary = {'baseline_commit': BASE, 'pack_path': str(PACK), 'pack_sha256': sha(PACK.read_bytes()),
                   'zip_entries': len(names), 'blockstates': len({row['source'] for row in states}), 'state_branches': len(states),
                   'models': len(model_rows), 'unreferenced_models': sum(not row['used_by'] for row in model_rows),
                   'actual_pack_blockstate_files':sum('/blockstates/' in name and name.endswith('.json') for name in packed),
                   'actual_pack_model_files':sum('/models/' in name and name.endswith('.json') for name in packed),
                   'actual_pack_valid_models':sum('/models/' in name and name.endswith('.json') and name in data for name in packed),
                   'inherited_vanilla_models':sum(row['file'] not in packed for row in model_rows),
                   'defects': defects}
        used_dependencies={path for row in model_rows for path in row['resolved'].get('dependencies',[])}
        auxiliary=[]
        for name in sorted(packed):
            if name.endswith('/') or '/models/' in name or '/blockstates/' in name:continue
            kind='SOURCE_CONTEXT_OR_NON_OBJECT_RESOURCE';status='OUTSIDE_BUILDING_OBJECT_SCOPE'
            if '/textures/' in name:
                if name in used_dependencies or (name.endswith('.mcmeta') and name[:-7] in used_dependencies):
                    kind='MODEL_TEXTURE_DEPENDENCY';status='IMPORT_WITH_USING_OBJECT'
                else:
                    kind='UNREFERENCED_TEXTURE';status='NO_AUTHORED_BLOCK_MODEL_REFERENCE'
            elif '/optifine/cit/' in name:
                kind='OPTIFINE_COSMETIC_ITEM';status='OUTSIDE_BUILDING_OBJECT_SCOPE'
            elif name.endswith('optifine/emissive.properties'):
                kind='OPTIFINE_EMISSIVE_FEATURE';status='REQUIRES_NATIVE_RENDERER_SUPPORT_FOR_USING_OBJECTS'
            auxiliary.append({'file':name,'kind':kind,'status':status,'sha256':sha(archive.read(prefix+name))})
        (OUT/'source-auxiliary-resources.json').write_text(json.dumps(auxiliary,ensure_ascii=False,indent=2),encoding='utf-8')
        (OUT / 'source-inventory.json').write_text(json.dumps({'summary': summary, 'states': states, 'models': model_rows}, ensure_ascii=False, indent=2), encoding='utf-8')
        with (OUT / 'source-carriers.csv').open('w', encoding='utf-8-sig', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=['source', 'branches', 'models'])
            writer.writeheader()
            for source in sorted({row['source'] for row in states}):
                values = [row for row in states if row['source'] == source]
                models = sorted({model['model'] for row in values for model in row['models']})
                writer.writerow({'source': source, 'branches': len(values), 'models': ';'.join(models)})
        print(json.dumps(summary, ensure_ascii=True, indent=2))
        vanilla.close()

if __name__ == '__main__':
    import traceback
    try:
        main()
        (OUT / 'audit-run-status.txt').write_text('PASS', encoding='utf-8')
    except Exception:
        (OUT / 'audit-run-status.txt').write_text(traceback.format_exc(), encoding='utf-8')
        raise
