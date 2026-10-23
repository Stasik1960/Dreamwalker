"""Compare one actual FULL JAR with the accepted immutable 99f4 runtime.

Four NativeGrid data files and exact compiler bank JSON markers may differ.
An optional root-approved receipt permits only five named item-hook classes
and one exact retained_whole_items.json. No wildcard exemptions exist.
Does not repeat the historical 40ce resource audit, build, SQL or gameplay QA.
"""
import argparse
import hashlib
import json
import os
import struct
import zipfile
from pathlib import Path, PurePosixPath

ACCEPTED_SHA = '99f450f814d187cf089a7fe523a5db5928cfafcb273c4ac1c4479f2df50bbde3'
NS = 'assets/bloodborne_dw/'
DATA = NS + 'native_grid/'
STATUS = 'PASS_EXACT_FULL_GENERATED_RESOURCE_DIFF_NOT_GAMEPLAY'
PATCH_STATUS = 'PASS_EXACT_FULL_GENERATED_AND_REVIEWED_RUNTIME_PATCH_NOT_GAMEPLAY'
PATCH_CLASSES = {
    'dev/dreamwalker/bloodbornedw/composite/StaticArchitectureRuntime.class',
    'dev/dreamwalker/bloodbornedw/composite/CompositeClient.class',
    'dev/dreamwalker/bloodbornedw/composite/mixin/StaticArchitectureBreakMixin.class',
    'dev/dreamwalker/bloodbornedw/composite/NativeGridRetainedWholeItems.class',
    'dev/dreamwalker/bloodbornedw/composite/NativeGridRetainedWholeItems$Whitelist.class',
}
QUAD_BYTES = 11 + 4 * 32  # NGQ1: material/cull/face/tint/flags + four xyzuv/color/light/normal vertices.


def require(value, message):
    if not value:raise ValueError(message)


def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for data in iter(lambda: stream.read(1 << 20), b''):value.update(data)
    return value.hexdigest()


def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def binding(path, expected=None):
    path = Path(path).resolve(strict=True)
    value = {'path': str(path), 'sha256': sha(path)}
    require(expected is None or value['sha256'] == expected, 'Explicit artifact SHA changed: ' + str(path))
    return value


def checked(reference):
    binding(reference['path'], reference['sha256'])
    return read(reference['path'])


def payload_hash(value):return hashlib.sha256(value).hexdigest()


def canonical(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')) + '\n').encode()


def jar_entries(path):
    result = {}
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            if entry.is_dir():continue
            name = entry.filename;relative = PurePosixPath(name)
            require(name not in result and not relative.is_absolute() and relative.as_posix() == name and
                    '..' not in relative.parts and '\\' not in name, 'Duplicate/unsafe JAR payload name')
            digest = hashlib.sha256()
            with archive.open(entry) as stream:
                for data in iter(lambda: stream.read(1 << 20), b''):digest.update(data)
            result[name] = digest.hexdigest()
    return result


def first_materials(path, catalog):
    """Read only the exact compiler particle selector from each bound mesh."""
    result = [];size = Path(path).stat().st_size
    with Path(path).open('rb') as stream:
        header = stream.read(8)
        require(len(header) == 8 and header[:4] == b'NGQ1', 'Expected frozen NGQ1 mesh resource')
        count = struct.unpack('>i', header[4:])[0]
        require(count == len(catalog['meshes']), 'Frozen catalog/mesh count differs')
        for metadata in catalog['meshes']:
            data = stream.read(4);require(len(data) == 4, 'Truncated frozen mesh record')
            quads = struct.unpack('>i', data)[0]
            require(quads >= 0 and quads == metadata['quadCount'] and stream.tell() + quads * QUAD_BYTES <= size,
                    'Frozen mesh quad count/bounds differ')
            material = None
            if quads:
                material = struct.unpack('>i', stream.read(4))[0]
                require(0 <= material < len(catalog['materials']), 'Particle material outside actual catalog')
                stream.seek(quads * QUAD_BYTES - 4, 1)
            result.append(material)
        require(stream.tell() == size, 'Unexpected trailing frozen mesh payload')
    return result


def catalog_line_ending(reference):
    binding(reference['path'], reference['sha256'])
    with Path(reference['path']).open('rb') as stream:
        stream.seek(-2, os.SEEK_END)
        tail = stream.read(2)
    require(tail.endswith(b'\n'), 'Frozen catalog must end in LF or CRLF')
    return b'\r\n' if tail == b'\r\n' else b'\n'


def expected_markers(catalog, mesh_path, line_ending):
    require(line_ending in (b'\n', b'\r\n'), 'Only the bound catalog LF/CRLF marker ending is supported')
    def marker(value):
        return payload_hash(canonical(value)[:-1] + line_ending)
    require(catalog['schemaVersion'] == 1 and catalog['bankSize'] == 128 and catalog['cells'], 'Actual FULL bank128 catalog required')
    material = first_materials(mesh_path, catalog);expected = {}
    banks = (len(catalog['cells']) + 127) // 128
    for bank in range(banks):
        variants = {}
        for part in range(128):
            index = bank * 128 + part;name = 'native_grid_%d_%d' % (bank, part)
            sprite = 'minecraft:block/stone'
            if index < len(catalog['cells']):
                mesh = catalog['cells'][index]['mesh']
                require(type(mesh) is int and 0 <= mesh < len(material), 'Cell mesh outside frozen catalog')
                if material[mesh] is not None:sprite = catalog['materials'][material[mesh]]['sprite']
            expected[NS + 'models/block/' + name + '.json'] = marker({'textures': {'particle': sprite}})
            variants['part=' + str(part)] = {'model': 'bloodborne_dw:block/' + name}
        expected[NS + 'blockstates/native_grid_%d.json' % bank] = marker({'variants': variants})
        expected[NS + 'models/item/native_grid_%d.json' % bank] = marker({'parent': 'bloodborne_dw:block/native_grid_%d_0' % bank})
    return expected, banks


def approved_item_patch(a, accepted, full, append):
    if a.runtime_patch is None:
        require(a.runtime_patch_sha256 is None, 'A runtime patch SHA needs its explicit receipt')
        return {}, None, []
    require(a.runtime_patch_sha256, 'Pin the root-approved runtime patch receipt SHA')
    reference = binding(a.runtime_patch, a.runtime_patch_sha256);patch = read(a.runtime_patch)
    require(patch['status'] == 'ROOT_SOURCE_APPROVED_FOR_AFFECTED_QA_NOT_DELIVERY' and
            patch['kind'] == 'EXACT_RETAINED_WHOLE_ITEM_RUNTIME_SOURCE_PATCH' and
            patch['accepted'] == accepted and patch['full'] == full, 'Runtime patch lacks exact root source approval/99f4/FULL binding')
    classes = patch['classes']
    require(set(classes) == PATCH_CLASSES and all(isinstance(value, str) and len(value) == 64 and
            all(ch in '0123456789abcdef' for ch in value) for value in classes.values()), 'Only the five explicit remapped item-hook class hashes are permitted')
    refs = [reference, patch['rootReview'], patch['resource'], patch['resourceProof'], patch['sourceDeltaProof'], *patch['sourceFiles']]
    for ref in refs:binding(ref['path'], ref['sha256'])
    require(Path(patch['rootReview']['path']).stat().st_size > 0, 'Actual root source observation is missing')
    sources = {Path(row['path']).name: row for row in patch['sourceFiles']}
    require(len(sources) == len(patch['sourceFiles']) == 4 and set(sources) ==
            {'StaticArchitectureRuntime.java', 'CompositeClient.java', 'StaticArchitectureBreakMixin.java', 'NativeGridRetainedWholeItems.java'}, 'Runtime patch source-file scope differs')
    delta = checked(patch['sourceDeltaProof'])
    require(delta['status'] == 'PASS_SOURCE_TEXT_HOOK_DELTA_NOT_GAMEPLAY' and
            delta['physicsRenderTargetRemoveCleanupAndRegistryTextUnchangedExceptListedHooks'] is True and len(delta['files']) == 3 and
            {row['name'] for row in delta['files']} == {'StaticArchitectureRuntime.java', 'CompositeClient.java', 'StaticArchitectureBreakMixin.java'},
            'Exact pre-item source/hook delta proof missing')
    for row in delta['files']:
        require(row['name'] in sources and row['afterSha256'] == sources[row['name']]['sha256'] and row['allOtherSourceTextExactIgnoringCRLF'] is True,
                'The root-approved class patch changed unrelated old source text')
        refs.append({'path': row['beforePath'], 'sha256': row['beforeSha256']})
    resource, proof = checked(patch['resource']), checked(patch['resourceProof'])
    require(proof['status'] == 'PASS_EXACT_RETAINED_NATIVE_WHOLE_ITEM_RESOURCE_NOT_GAMEPLAY' and
            proof['kind'] == 'FINITE_OBSERVED_RETAINED_NATIVE_WHOLE_ITEMS' and proof['sourceBinding'] == append['sourceBinding'] and
            proof['resource'] == patch['resource'] and proof['finalCompositions'] == append['inputs']['compositions'] and
            proof['originalV12Excluded'] is True and proof['gameplayPass'] is False, 'Final retained-item resource has no exact observed FULL authoring proof')
    keys = [row['legacyPose'] for row in resource['poses']]
    require(resource['schemaVersion'] == 1 and len(keys) == len(set(keys)) == proof['resourcePoseCount'] and
            set(keys) == set(proof['observedLegacyPoses']), 'Runtime item whitelist differs from the finite observed final fallback set')
    expected = {**classes, DATA + 'retained_whole_items.json': patch['resource']['sha256']}
    return expected, reference, refs


def verify(a):
    accepted = binding(a.accepted, ACCEPTED_SHA);full = binding(a.full, a.full_sha256)
    freeze_ref = binding(a.frozen, a.frozen_sha256);freeze = read(a.frozen)
    require(freeze['status'] == 'FROZEN_RESOURCES_READY' and freeze['kind'] == 'COMPLETED_FULL_CATALOG_AUTHORING_HANDOFF',
            'Actual completed FULL authoring freeze required')
    append = checked(freeze['inputBinding']);prefix = checked(freeze['prefixProof'])
    require(append['status'] == prefix['status'] == 'PASS' and append['mode'] == 'EXPLICIT_FULL_APPEND_INPUT_BINDING' and
            append == freeze['inputBindings'], 'FULL append/prefix source binding differs')
    require(all(prefix.get(key) is True for key in ('allPriorIndexedRowsExact', 'meshPayloadPrefixExact', 'priorRealCellMarkersExact')),
            'Previously verified accepted catalog/mesh/real-marker prefix is required')
    catalog = checked(freeze['catalog']);binding(freeze['meshes']['path'], freeze['meshes']['sha256'])
    compiler = checked(freeze['compilerReceipt'])
    require(compiler['status'] == 'PASS' and compiler['catalogSha256'] == freeze['catalog']['sha256'] == prefix['catalogSha256'] and
            compiler['meshesSha256'] == freeze['meshes']['sha256'] == prefix['meshesSha256'] and
            compiler['banks'] == freeze['derivedCounts'] == prefix['currentCounts'] and
            compiler['compilerAlgorithmSha256'] == append['compilerAlgorithmSha256'], 'Frozen compiler/output/count binding differs')
    carrier_ref = binding(a.bridge_carriers, a.bridge_carriers_sha256);carriers = read(a.bridge_carriers)
    require(Path(carrier_ref['path']).parent == Path(freeze['catalog']['path']).resolve().parent and
            Path(carrier_ref['path']).name == 'bridge_carriers.json', 'Explicit actual bridge_carriers must be in the frozen catalog directory')
    require(set(carriers) == {'schemaVersion', 'nbtKey', 'registryIds'} and carriers['schemaVersion'] == 1 and
            carriers['nbtKey'] == 'NativeGridBase' and isinstance(carriers['registryIds'], list) and
            all(isinstance(value, str) and ':' in value for value in carriers['registryIds']) and
            len(set(carriers['registryIds'])) == len(carriers['registryIds']), 'Unexpected carrier resource contract')
    marker_ending = catalog_line_ending(freeze['catalog'])
    expected, banks = expected_markers(catalog, freeze['meshes']['path'], marker_ending)
    expected.update({DATA + 'catalog.json': freeze['catalog']['sha256'], DATA + 'meshes.bin': freeze['meshes']['sha256'],
                     DATA + 'compiler-receipt.json': freeze['compilerReceipt']['sha256'], DATA + 'bridge_carriers.json': carrier_ref['sha256']})
    generated_count = len(expected)
    approved, runtime_patch, patch_refs = approved_item_patch(a, accepted, full, append)
    require(not expected.keys() & approved.keys(), 'A source-approved item hook cannot replace compiler output')
    expected.update(approved)
    old, new = jar_entries(a.accepted), jar_entries(a.full)
    require(old[DATA + 'catalog.json'] == prefix['priorCatalogSha256'] and old[DATA + 'meshes.bin'] == prefix['priorMeshesSha256'],
            'FULL freeze does not append the catalog/meshes actually loaded in accepted99f4')
    failures = [];changed = [];added = []
    for name, digest in old.items():
        if name not in new:failures.append({'entry': name, 'reason': 'accepted_entry_removed'})
        elif new[name] != digest:
            if name not in expected:failures.append({'entry': name, 'reason': 'changed_outside_exact_generated_set'})
            else:changed.append(name)
    for name in new.keys() - old.keys():
        if name not in expected:failures.append({'entry': name, 'reason': 'added_outside_exact_generated_set'})
        else:added.append(name)
    for name, digest in expected.items():
        if new.get(name) != digest:failures.append({'entry': name, 'reason': 'actual_full_generated_payload_missing_or_not_frozen_compiler_output'})
    references = [accepted, full, freeze_ref, carrier_ref, freeze['catalog'], freeze['meshes'], freeze['compilerReceipt'], freeze['inputBinding'], freeze['prefixProof'], *patch_refs]
    for reference in references:binding(reference['path'], reference['sha256'])
    stable = hashlib.sha256()
    for name in sorted(old):
        if name not in expected:stable.update((name + '\0' + old[name] + '\n').encode())
    passed = not failures
    return {'schemaVersion': 1, 'status': (PATCH_STATUS if runtime_patch else STATUS) if passed else 'FAIL_FULL_JAR_COMPATIBILITY',
            'kind': 'FULL_JAR_COMPATIBILITY_WITH_ACCEPTED_99F4', 'accepted': accepted, 'full': full,
            'frozenFull': freeze_ref, 'catalog': freeze['catalog'], 'meshes': freeze['meshes'], 'bridgeCarriers': carrier_ref,
            'prefixProof': freeze['prefixProof'], 'sourceBinding': append['sourceBinding'],
            'oldEntries': len(old), 'fullEntries': len(new), 'finiteGeneratedEntries': generated_count, 'bankCount': banks,
            'generatedMarkerLineEnding': 'CRLF' if marker_ending == b'\r\n' else 'LF',
            'allRuntimeClassesByteExact': passed and runtime_patch is None, 'runtimeCompatibilityPassed': passed,
            'allUnchangedRuntimeClassesByteExact': passed, 'reviewedRuntimePatch': runtime_patch,
            'allOldNonGeneratedResourcesByteExact': passed,
            'allTexturesByteExact': passed, 'allOtherAcceptedEntriesByteExact': passed,
            'changedGeneratedEntries': sorted(name for name in changed if name not in approved), 'addedGeneratedEntries': sorted(name for name in added if name not in approved),
            'changedApprovedRuntimeEntries': sorted(name for name in changed if name in approved),
            'addedApprovedRuntimeEntries': sorted(name for name in added if name in approved),
            'unchangedNonGeneratedInventorySha256': stable.hexdigest(), 'failures': failures,
            'generatedWhitelist': {'dataFiles': sorted(name for name in expected if name.startswith(DATA)),
                                   'markers': 'Only actual bank0..N-1 blockstates/item models and all128 block part models; bytes reconstructed from frozen catalog/NGQ1 first material.',
                                   'textureExemptions': [], 'classExemptions': [], 'metadataExemptions': [],
                                   'separatelyApprovedExactRuntimePayloads': approved},
            'original40ceAuditRepeated': False, 'builds': 0, 'jvmRuns': 0, 'sqlReads': 0, 'worldReads': 0, 'mapWrites': 0,
            'limits': 'Actual new JAR compatibility only. Does not replace mandatory TARGET bridge audit, map/light, source functionality or affected gameplay checks.'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('accepted', 'full', 'frozen', 'bridge-carriers', 'out'):p.add_argument('--' + name, type=Path, required=True)
    for name in ('full-sha256', 'frozen-sha256', 'bridge-carriers-sha256'):p.add_argument('--' + name, required=True)
    p.add_argument('--runtime-patch', type=Path);p.add_argument('--runtime-patch-sha256')
    a = p.parse_args();require(not a.out.exists(), 'Fresh compatibility receipt required')
    result = verify(a);a.out.parent.mkdir(parents=True, exist_ok=True)
    temporary = a.out.with_name(a.out.name + '.writing')
    with temporary.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())
    os.link(temporary, a.out);temporary.unlink()
    print(json.dumps({'status': result['status'], 'out': str(a.out), 'sha256': sha(a.out), 'failures': len(result['failures']),
                      'changedGeneratedEntries': len(result['changedGeneratedEntries']), 'addedGeneratedEntries': len(result['addedGeneratedEntries'])}))
    if result['failures']:raise SystemExit(2)


if __name__ == '__main__':main()
