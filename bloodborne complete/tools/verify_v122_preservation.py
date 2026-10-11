"""Read-only, artifact-bound preservation and future collision-selector audit.

This revision is allowed to alter NEW physical collision only and retire the two
actual red exhibits. Existing V12 rendering, state, assets and implementations
are checked independently against both the immediate release and original V12.
No author export is installed or claimed by this audit.
"""
from __future__ import annotations
import argparse
import copy
import csv
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile
import re

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
PREVIOUS_COMMIT = '4bcb19ce6864862e2bca3a3fc4c76b93a34e3544'
V12_COMMIT = '8b770a03de21554a21c91700a1c1c6fe86c894c4'
PREVIOUS_JAR = REPO / 'releases/Dreamwalker-BB/v12.1-resource-transfer/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.1-resource-transfer.jar'
REMOVED = {'source_jungle_trapdoor_bottom': '93148', 'source_jungle_trapdoor_open': '93149'}
ALLOWED_CHANGED_CLASSES = {
    'dev/dreamwalker/bloodbornedw/composite/CompositeSpec.class',
    'dev/dreamwalker/bloodbornedw/composite/CompositeRuntime.class',
    'dev/dreamwalker/bloodbornedw/composite/CompositeRuntime$1Hit.class',
    'dev/dreamwalker/bloodbornedw/composite/CompositeSourceShift.class',
    'dev/dreamwalker/bloodbornedw/composite/FabricCompositeWorld.class',
    'dev/dreamwalker/bloodbornedw/composite/mixin/RpClimbingMixin.class',
}

def sha(data):
    return hashlib.sha256(data).hexdigest()

def blob(commit, relative):
    return subprocess.check_output(['git', 'show', commit + ':bloodborne complete/' + relative], cwd=REPO)

def nonphysical(document):
    result = copy.deepcopy(document)
    result.pop('simplePhysicalCollision', None)
    result.pop('physicalCollisionPolicy', None)
    result.pop('collisionPolicy', None)
    # Two source ladders receive a separately reviewed climbing volume. It is
    # physical behaviour, never an edit to their visual/picking source geometry.
    if result['id'].split(':')[1] in {'source_hold_ladder_02', 'source_hold_ladder_03'}:
        result.pop('importedClimbingBounds', None)
    for variant in result['variants']:
        for pose in variant['poses'].values():
            pose.pop('collision', None)
            pose.pop('simpleCollisionByRotation', None)
    return result

def normalized(data):
    return data.decode('utf-8-sig').replace('\r\n', '\n')

def method(text, name):
    match = re.search(r'(?:public|private|protected)\s+[^\n{;]*\b' + re.escape(name) + r'\([^\n]*\)\s*\{', text)
    if match is None:
        raise ValueError('Missing preserved method ' + name)
    start = match.start(); i = match.end(); depth = 1
    while depth:
        if text[i] == '{': depth += 1
        elif text[i] == '}': depth -= 1
        i += 1
    return text[start:i]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--baseline-jar', type=Path, default=PREVIOUS_JAR)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    failures, checks, changed_classes, changed_resources = [], {}, [], []
    def check(name, condition, detail=''):
        checks[name] = bool(condition)
        if not condition:
            failures.append(name + (': ' + detail if detail else ''))
    old_catalogue = json.loads(blob(V12_COMMIT, 'src/architecture/resources/bloodborne_dw/debug_catalogue.json'))
    runtime_relative = 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeRuntime.java'
    runtime_old = normalized(blob(PREVIOUS_COMMIT, runtime_relative)); runtime_current = normalized((ROOT / runtime_relative).read_bytes())
    runtime_without_hooks = ''.join(line for line in runtime_current.splitlines(keepends=True) if not any(hook in line for hook in ('ImportedCollisionMigration.', 'ImportedPhysicalShapes.collision(', 'ImportedLegacyOutline.')))
    runtime_without_hooks = runtime_without_hooks.replace('ImportedPhysicalShapes.physical(contribution,support)', 'shape(physicalParts(contribution,support))')
    check('shared_runtime_original_body_exact_after_removing_reviewed_new_scoped_hooks', runtime_without_hooks == runtime_old)
    fabric_relative = 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/FabricCompositeWorld.java'
    fabric_old = normalized(blob(PREVIOUS_COMMIT, fabric_relative)); fabric_current = normalized((ROOT / fabric_relative).read_bytes())
    gated = 'incoming.size()==1&&ImportedPhysicalShapes.isImported(incoming.keySet().iterator().next())?ImportedPhysicalShapes.exact(proposed.collision()):CompositeRuntime.shape(proposed.collision())'
    fabric_without_gate = fabric_current.replace('var physical=' + gated + ';var boxes=physical.getBoundingBoxes()', 'var boxes=CompositeRuntime.shape(proposed.collision()).getBoundingBoxes()')
    check('ordinary_legacy_entity_collision_adapter_source_exact', fabric_without_gate == fabric_old)
    climb_relative = 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin/RpClimbingMixin.java'
    climb_old = normalized(blob(PREVIOUS_COMMIT, climb_relative)); climb_current = normalized((ROOT / climb_relative).read_bytes())
    climb_without_hook = ''.join(line for line in climb_current.splitlines(keepends=True) if 'var imported=' not in line and 'if(imported.isPresent())' not in line)
    check('shared_climbing_original_body_exact_after_removing_new_imported_ladder_branch', climb_without_hook == climb_old)
    shift_relative = 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeSourceShift.java'
    shift_old = normalized(blob(PREVIOUS_COMMIT, shift_relative)); shift_current = normalized((ROOT / shift_relative).read_bytes())
    shift_without_hook = ''.join(line for line in shift_current.splitlines(keepends=True) if 'collision.putAll(spec.simplePhysicalFootprint' not in line).replace('if(!spec.simplePhysicalCollision)distribute(', 'distribute(')
    check('old_source_shift_path_source_exact', shift_without_hook == shift_old)
    spec_relative = 'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeSpec.java'
    spec_old = normalized(blob(PREVIOUS_COMMIT, spec_relative)); spec_current = normalized((ROOT / spec_relative).read_bytes())
    protected_methods = ('distribute', 'intersects', 'clip', 'footprint', 'mountedBounds', 'pose', 'bound', 'bounds', 'number', 'optionalVec', 'vec')
    changed_methods = [name for name in protected_methods if method(spec_old, name) != method(spec_current, name)]
    check('v12_shape_algorithms_methods_source_exact', not changed_methods, '; '.join(changed_methods))
    with zipfile.ZipFile(args.baseline_jar) as previous, zipfile.ZipFile(args.jar) as current:
        oldnames, names = set(previous.namelist()), set(current.namelist())
        check('jar_crc_and_no_duplicate_entries', current.testzip() is None and len(current.namelist()) == len(names))
        oldmeta, meta = json.loads(previous.read('fabric.mod.json')), json.loads(current.read('fabric.mod.json'))
        expected = dict(oldmeta); expected['version'] = meta['version']
        check('only_version_changed_in_metadata', expected == meta)
        old_debug, debug = json.loads(previous.read('bloodborne_dw/debug_catalogue.json')), json.loads(current.read('bloodborne_dw/debug_catalogue.json'))
        oldrows = {r['registryId']: r for r in old_debug['entries']}
        rows = {r['registryId']: r for r in debug['entries']}
        check('protected_v12_rows_exact', all(rows.get(r['registryId']) == r for r in old_catalogue['entries']))
        check('v12_aliases_exact', debug.get('stateAliases') == old_catalogue.get('stateAliases'))
        numbers = [r['temporaryId'] for r in debug['entries']]
        check('unique_five_digit_numbers', len(numbers) == len(set(numbers)) and all(n.isdigit() and len(n) == 5 for n in numbers))
        check('all_other_previous_catalogue_rows_exact', all(rows.get(k) == v for k, v in oldrows.items() if k.split(':')[1] not in REMOVED))
        protected_resources = [n for n in oldnames if n.startswith(('assets/bloodborne_rp/', 'assets/minecraft/')) or n.startswith('bloodborne_dw/composite/') and not Path(n).stem.startswith('source_')]
        changed_protected = [n for n in protected_resources if n not in names or previous.read(n) != current.read(n)]
        check('v12_specs_rp_and_vanilla_assets_exact', not changed_protected, '; '.join(changed_protected[:20]))
        # All visual source models/textures/blockstates/items are preserved, except
        # the retired objects' own blockstate/item mapping. Shared visual models
        # remain available to surviving dependencies and are not altered.
        visual = [n for n in oldnames if n.startswith('assets/') and not n.endswith('/')]
        retirement = json.loads((ROOT / 'docs/v12.2-simplified-collisions/retirement.json').read_text(encoding='utf-8'))
        allowed_visual_removal = {r['path'].split('/resources/', 1)[1] for r in retirement['removedFiles'] if '/resources/assets/' in r['path']}
        changed_visual = [n for n in visual if n not in allowed_visual_removal and (n not in names or previous.read(n) != current.read(n))]
        # Russian language may remove only the exact retired translations.
        for n in list(changed_visual):
            if n.endswith('/lang/ru_ru.json') or n.endswith('/lang/en_us.json'):
                before, after = json.loads(previous.read(n)), json.loads(current.read(n))
                permitted = {f'{kind}.bloodborne_dw.{p}' for p in REMOVED for kind in ('block', 'item')}
                if all(after.get(k) == v for k, v in before.items() if k not in permitted) and not set(after) - set(before):
                    changed_visual.remove(n)
        check('all_surviving_visual_assets_exact', not changed_visual, '; '.join(changed_visual[:20]))
        previous_import = json.loads(previous.read('bloodborne_dw/remaining-catalogue.json'))
        active_previous_paths = {row['path'] for row in previous_import['objects']}
        new_specs = sorted(n for n in oldnames if n.startswith('bloodborne_dw/composite/source_') and n.endswith('.json') and Path(n).stem in active_previous_paths)
        dormant_specs = sorted(n for n in oldnames if n.startswith('bloodborne_dw/composite/source_') and n.endswith('.json') and Path(n).stem not in active_previous_paths)
        check('unregistered_service_source_descriptors_exact', all(n in names and previous.read(n) == current.read(n) for n in dormant_specs))
        surviving = []
        for n in new_specs:
            path = Path(n).stem
            if path in REMOVED:
                check('retired_descriptor_absent_' + path, n not in names)
                continue
            if n not in names:
                failures.append('Surviving NEW descriptor missing: ' + n)
                continue
            before, after = json.loads(previous.read(n)), json.loads(current.read(n))
            if nonphysical(before) != nonphysical(after):
                failures.append('NEW nonphysical descriptor fields changed: ' + n)
            if not after.get('simplePhysicalCollision'):
                failures.append('NEW descriptor missing simple physical opt-in: ' + n)
            surviving.append(after)
        check('all_new_nonphysical_fields_exact', not any(f.startswith('NEW nonphysical') for f in failures))
        check('all_surviving_new_specs_opt_in', not any('missing simple physical' in f for f in failures))
        historical_outline = json.loads(gzip.decompress(current.read('bloodborne_dw/imported-legacy-outlines.json.gz')))
        historical_rows = {r['id']: r for r in historical_outline['objects']}
        expected_outline = {}
        for n in new_specs:
            if Path(n).stem in REMOVED: continue
            original = json.loads(previous.read(n))
            expected_outline[original['id']] = {'id': original['id'], 'beforeSha256': sha(previous.read(n)), 'variants': [{'poses': {pose: data.get('collision', []) for pose, data in variant['poses'].items()}} for variant in original['variants']]}
        check('new_aiming_archive_exact_original_lists', historical_rows == expected_outline)
        check('new_aiming_archive_exact_schema', historical_outline['schemaVersion'] == 1 and historical_outline['units'] == 16)
        for n in sorted(oldnames):
            if n.endswith('.class') and (n not in names or previous.read(n) != current.read(n)):
                changed_classes.append(n)
            elif n in names and previous.read(n) != current.read(n):
                changed_resources.append(n)
        allowed = ALLOWED_CHANGED_CLASSES | {n for n in changed_classes if n.startswith('dev/dreamwalker/bloodbornedw/composite/CompositeSpec$')}
        check('previous_classes_exact_except_reviewed_new_opt_in', set(changed_classes).issubset(allowed), '; '.join(sorted(set(changed_classes) - allowed)))
        javap = Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javap.exe')
        hit_class = 'dev.dreamwalker.bloodbornedw.composite.CompositeRuntime$1Hit'
        hit_before = subprocess.check_output([str(javap), '-c', '-p', '-classpath', str(args.baseline_jar), hit_class])
        hit_after = subprocess.check_output([str(javap), '-c', '-p', '-classpath', str(args.jar), hit_class])
        check('local_hit_record_bytecode_exact_without_line_tables', hit_before == hit_after)
        check('collision_parser_provider_byte_exact', all(n in names and previous.read(n) == current.read(n) for n in oldnames if n.startswith('dev/dreamwalker/bloodbornedw/compat/collision/') and n.endswith('.class')))
        check('rp_all_classes_byte_exact', all(n in names and previous.read(n) == current.read(n) for n in oldnames if n.startswith('dev/dreamwalker/bloodbornerp/') and n.endswith('.class')))
        check('no_qa_addon_in_production', not any('/collisionqa/' in n or '/gallery/' in n or '/remainingqa/' in n for n in names))
        current_sha = sha(args.jar.read_bytes())
        russian = json.loads(current.read('assets/bloodborne_dw/lang/ru_ru.json'))
        # Explicit selectors, no wildcard and no extra nonauthor state fields.
        # Primitive STRING is deliberate: CompositeData serializes every block
        # property to a string. A real export must be reconciled to its own
        # primitive types, never coerced or matched by prefixes.
        selector_count, batch_count, batch_states, batches = 0, 0, 0, []
        inventory = []
        selector_file = args.output / 'new-exact-selectors.jsonl.gz'
        with gzip.open(selector_file, 'wt', encoding='utf-8', newline='\n') as stream:
            for spec_index, spec in enumerate(surviving):
                registry = spec['id']; variants = len(spec['variants']); states = variants * 32
                if batch_count == 512 or batch_states + states > 32768:
                    batches.append({'registryCount': batch_count, 'exactStateCount': batch_states}); batch_count = batch_states = 0
                batch_count += 1; batch_states += states
                row = rows[registry]
                inventory.append({'registryId': registry, 'numericId': row['temporaryId'], 'russianName': russian.get('block.' + registry.replace(':', '.'), spec.get('displayName', registry)), 'variants': variants, 'exactSelectors': states, 'selectorPrimitiveTypes': 'variant:string, profile:string, open:string, rotation:string', 'plannedImportBatch': len(batches) + 1})
                for variant in range(variants):
                    for profile in ('base', 'alt'):
                        for opened in (False, True):
                            for yaw in range(8):
                                selector = {'variant': str(variant), 'profile': profile, 'open': str(opened).lower(), 'rotation': str(yaw)}
                                stream.write(json.dumps({'registryId': registry, 'numericId': row['temporaryId'], 'selector': selector, 'importBatch': len(batches) + 1}, ensure_ascii=False, separators=(',', ':')) + '\n')
                                selector_count += 1
        if batch_count:
            batches.append({'registryCount': batch_count, 'exactStateCount': batch_states})
        with (args.output / 'new-selector-registry.csv').open('w', encoding='utf-8-sig', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=list(inventory[0])); writer.writeheader(); writer.writerows(inventory)
        selectors = {'status': 'PREPARED_EXPORT_NOT_SUPPLIED', 'runtimeReplacementApplied': False,
            'providerConsumer': 'NOT_IMPLEMENTED_BY_V12_CONTRACT; future exact player-only consumer is required at actual author import',
            'allRegistryIds': len(surviving), 'allExactStates': selector_count, 'file': selector_file.name,
            'fileSha256': sha(selector_file.read_bytes()), 'exportLimits': {'models': 512, 'states': 32768}, 'batches': batches,
            'batchImportNote': 'register(pack) replaces the current project; it does not merge batches. A future reviewed import must explicitly bake/merge validated batches before installing player-only physics.',
            'selectorNote': 'This is an exact current BlockState mapping. An author export may use other primitive types; reconcile it explicitly instead of coercing selector values.',
            'transforms': 'Local physical geometry uses installation root, yaw about [.5,.5,.5], MountY/VerticalMount offset, then rotated SourceShift. Existing selection, origin reservation and UUID remain separate.',
            'authorOBBNote': 'Do not apply this revision AABB simplification to an author export. Its OBB rotations/ZYX, player-only scope, seats and exact state must remain as authored.'}
        report = {'schema': 'dreamwalker-v122-preservation-contract-audit-v1', 'status': 'FAIL' if failures else 'PASS',
            'baselineCommit': PREVIOUS_COMMIT, 'protectedV12Commit': V12_COMMIT, 'jar': str(args.jar.resolve()), 'jarSha256': current_sha,
            'previousJar': str(args.baseline_jar.resolve()), 'previousJarSha256': sha(args.baseline_jar.read_bytes()),
            'checks': checks, 'changedExistingClasses': changed_classes, 'changedResourceEntries': changed_resources,
            'removedActualRedRegistries': REMOVED, 'protectedResourceCount': len(protected_resources), 'visualAssetCount': len(visual),
            'futureAuthorCompatibility': selectors, 'failures': failures}
        (args.output / 'preservation-contract.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': report['status'], 'selectors': selector_count, 'newRegistries': len(surviving), 'failures': failures}, ensure_ascii=False))
    if failures:
        raise SystemExit(1)

if __name__ == '__main__':
    main()
