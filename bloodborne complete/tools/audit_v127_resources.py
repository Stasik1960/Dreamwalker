"""Read-only final combined-artifact dependency and source packaging audit."""
from pathlib import Path
from collections import Counter
import argparse
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT.parent / 'releases/Dreamwalker-BB/v12.6-functional-assemblies/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.6-functional-assemblies.jar'
BASELINE_SHA = 'f5a4238d1ea241067e163c2e89f66f3133def211ab53b05d21db1375c96030d6'

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for data in iter(lambda: stream.read(1048576), b''):
            h.update(data)
    return h.hexdigest()

def refs(value, keys=('model', 'altModel')):
    if isinstance(value, dict):
        return [v for k, v in value.items() if k in keys and isinstance(v, str)] + [r for v in value.values() for r in refs(v, keys)]
    if isinstance(value, list):
        return [r for v in value for r in refs(v, keys)]
    return []

def canonical(value):
    return value if ':' in value else 'minecraft:' + value

def resource(value, kind, extension):
    namespace, name = canonical(value).split(':', 1)
    return f'assets/{namespace}/{kind}/{name}.{extension}'

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--vanilla-jar', type=Path, required=True)
    p.add_argument('--report', type=Path, default=ROOT/'docs/v12.7/resource-audit.json')
    a = p.parse_args()
    digest = sha(a.jar)
    failures = []
    counts = Counter()
    def check(ok, code, detail=None):
        if not ok:
            failures.append({'code': code, 'detail': detail})
    check(sha(BASELINE) == BASELINE_SHA, 'IMMUTABLE_BASELINE_SHA')
    with zipfile.ZipFile(a.jar) as current, zipfile.ZipFile(a.vanilla_jar) as vanilla, zipfile.ZipFile(BASELINE) as old:
        names = set(current.namelist())
        vanilla_names = set(vanilla.namelist())
        old_names = set(old.namelist())
        check(not [n for n, count in Counter(current.namelist()).items() if count > 1], 'DUPLICATE_ARCHIVE_PATHS')
        check(current.testzip() is None, 'FINAL_JAR_CRC')
        leaked = [n for n in names if any(token in n for token in ('/gametest/', '/v127qa/', '/conversion/', '/gallery/', '/remainingqa/', '/collisionqa/', '/repairtest/', 'v127-source-lighting-fixture', 'v124-baseline-content'))]
        check(not leaked, 'QA_OR_CONVERTER_LEAKED_IN_PRODUCTION', leaked)
        for name in old_names | names:
            if name.startswith('assets/minecraft/'):
                check(name in old_names and name in names and old.read(name) == current.read(name), 'VANILLA_NAMESPACE_CHANGED', name)
                counts['unchangedVanillaNamespaceEntries'] += 1
        for source in ('src/architecture/resources', 'src/rp/resources'):
            directory = ROOT/source
            for path in sorted(directory.rglob('*')):
                if not path.is_file():
                    continue
                name = path.relative_to(directory).as_posix()
                if name == 'fabric.mod.json':
                    continue
                check(name in names, 'SOURCE_RESOURCE_NOT_PACKAGED', name)
                if name not in names:
                    continue
                raw, packed = path.read_bytes(), current.read(name)
                if raw != packed:
                    transformed = False
                    if name.endswith('.mixins.json'):
                        original, final = json.loads(raw), json.loads(packed)
                        refmap = final.pop('refmap', None)
                        transformed = original == final and isinstance(refmap, str) and refmap in names
                        if transformed:
                            prefix = original['package'].replace('.', '/') + '/'
                            declared = [prefix + entry + '.class' for key in ('mixins', 'client', 'server') for entry in original.get(key, [])]
                            transformed = all(entry in names for entry in declared)
                            counts['verifiedLoomRefmapTransforms'] += 1
                    check(transformed, 'JAR_DIFFERS_REVIEWED_SOURCE_RESOURCE', name)
                counts['sourceResourcesPackaged'] += 1
        resolved, resolving, rendered, textures = {}, set(), set(), set()
        template_aliases, unused_aliases = [], []
        def data(name):
            if name in names:
                return current.read(name)
            if name in vanilla_names:
                return vanilla.read(name)
            raise ValueError('Missing dependency: ' + name)
        def model(value):
            value = canonical(value)
            if value in resolved:
                return resolved[value]
            if value.startswith('minecraft:builtin/'):
                resolved[value] = {'textures': {}, 'elements': []}
                return resolved[value]
            if value in resolving:
                raise ValueError('Cyclic model parent: ' + value)
            resolving.add(value)
            try:
                own = json.loads(data(resource(value, 'models', 'json')))
                parent = model(own['parent']) if 'parent' in own else {}
                result = dict(parent)
                result.update(own)
                result['textures'] = {**parent.get('textures', {}), **own.get('textures', {})}
                resolved[value] = result
                return result
            finally:
                resolving.remove(value)
        def texture(value, bindings):
            seen = set()
            while value.startswith('#'):
                if value in seen:
                    raise ValueError('Cyclic texture binding: ' + value)
                seen.add(value)
                if value[1:] not in bindings:
                    raise ValueError('Unbound texture: ' + value)
                value = bindings[value[1:]]
            name = resource(value, 'textures', 'png')
            raw = data(name)
            if not raw.startswith(b'\x89PNG\r\n\x1a\n'):
                raise ValueError('Not a PNG texture: ' + name)
            textures.add(name)
            if name + '.mcmeta' in names or name + '.mcmeta' in vanilla_names:
                json.loads(data(name + '.mcmeta'))
        roots = []
        for name in sorted(names):
            if name.startswith('bloodborne_dw/composite/') and name.endswith('.json'):
                for row in json.loads(current.read(name))['variants']:
                    roots.extend(refs(row['poses']))
            elif '/blockstates/' in name and name.endswith('.json'):
                roots.extend(refs(json.loads(current.read(name)), ('model',)))
            elif '/models/item/' in name and name.endswith('.json'):
                parts = name.split('/')
                roots.append(parts[1] + ':' + '/'.join(parts[3:])[:-5])
        roots.extend(refs(json.loads(current.read('bloodborne_dw/imported-stair-legacy-art.json'))))
        for value in sorted(set(roots)):
            rendered.add(canonical(value))
            try:
                document = model(value)
                bindings = document.get('textures', {})
                for element in document.get('elements', []):
                    for face in element.get('faces', {}).values():
                        texture(face['texture'], bindings)
                for name, bound in bindings.items():
                    try:
                        texture(bound, bindings)
                    except ValueError as error:
                        path = resource(value, 'models', 'json')
                        if not str(error).startswith('Unbound texture:') or path not in old_names or path not in names or old.read(path) != current.read(path):
                            raise
                        unused_aliases.append({'model': value, 'binding': name, 'value': bound, 'reason': str(error), 'status': 'UNCHANGED_BASELINE_UNUSED_INPUT'})
            except Exception as error:
                check(False, 'RENDERED_MODEL_DEPENDENCY', {'model': value, 'reason': str(error)})
        for name in sorted(names):
            if '/models/' not in name or not name.endswith('.json') or not name.startswith('assets/'):
                continue
            parts = name.split('/')
            value = parts[1] + ':' + '/'.join(parts[3:])[:-5]
            if value in rendered:
                continue
            try:
                document = model(value)
                bindings = document.get('textures', {})
                for bound in bindings.values():
                    if not bound.startswith('#'):
                        texture(bound, bindings)
                for element in document.get('elements', []):
                    for face in element.get('faces', {}).values():
                        try:
                            texture(face['texture'], bindings)
                        except ValueError as error:
                            if not str(error).startswith('Unbound texture:'):
                                raise
                            template_aliases.append({'model': value, 'parameter': face['texture']})
            except Exception as error:
                check(False, 'UNUSED_MODEL_LITERAL_OR_PARENT_DEPENDENCY', {'model': value, 'reason': str(error)})
        counts.update(renderedModelRoots=len(rendered), resolvedModelDependencies=len(resolved), resolvedTextureDependencies=len(textures), unchangedUnusedAliases=len(unused_aliases), unusedTemplateFaces=len(template_aliases))
    check(sha(a.jar) == digest, 'FINAL_JAR_CHANGED_DURING_AUDIT')
    report = {'schema': 'dreamwalker-v127-final-resource-audit-v1', 'status': 'PASS' if not failures else 'FAIL', 'productionJarSha256': digest, 'baselineJarSha256': BASELINE_SHA, 'counts': dict(counts), 'failures': failures, 'unchangedUnusedAliases': unused_aliases, 'unusedTemplateFaces': template_aliases, 'qaClassesExcluded': not leaked, 'sourceResourcePackRequired': False, 'nativeGameplay': 'NOT_PROVEN_BY_RESOURCE_AUDIT'}
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({key: report[key] for key in ('status', 'productionJarSha256', 'counts', 'failures')}, ensure_ascii=False))
    raise SystemExit(0 if report['status'] == 'PASS' else 1)

if __name__ == '__main__':
    main()
