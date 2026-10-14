"""Append finite optical source poses; keep every V127 artwork/state prefix.

No source world or prepared SQL is read or written. The output mapping lets the
existing prepared-city derivation remap only its 24 affected compact decisions.
"""
from __future__ import annotations
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources'
DATA = RES / 'bloodborne_dw'
FAMILY = 'bloodborne_dw:source_snow_height12'

def read(path: Path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def write(path: Path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')

def main():
    # Source carriers were prepared at different authored cardinal rotations.
    # Rotating all native shapes relative to one assumed yaw displaces culling.
    rotation_evidence = read(ROOT / 'docs/final-city-repair/geometry/compact-optical-reference-rotations.json')
    reference_rotations = {x['sourceState']: x['referenceRotation'] for x in rotation_evidence['sources']}
    assert len(reference_rotations) == 24
    descriptor_path = DATA / 'composite/source_snow_height12.json'
    descriptor = read(descriptor_path)
    old_prefix = copy.deepcopy(descriptor['variants'][:4])
    assert len(old_prefix) == 4
    light_path = DATA / 'imported-source-lighting-v127.json'
    lighting = read(light_path)
    prior = next(x for x in lighting['objects'] if x['registryId'] == FAMILY and x['variant'] == 3 and not x['open'])
    sources = sorted(prior['sources'], key=lambda x: x['sourceState'])
    assert len(sources) == 24 and sum(x['occurrences'] for x in sources) == 2905
    assert all(x['sourceState'].startswith(('minecraft:quartz_stairs[', 'minecraft:diorite_stairs[')) for x in sources)
    compact_path = DATA / 'imported-compact-v127.json'
    compact = read(compact_path)
    compact_row = next(x for x in compact['types'] if x['registryId'] == FAMILY)
    shapes = copy.deepcopy(compact_row['shapes']['3'])
    parts_path = DATA / 'imported-source-parts-v127.json'
    parts = read(parts_path)
    states_path = RES / 'assets/bloodborne_dw/blockstates/source_snow_height12.json'
    states = read(states_path)
    base_branches = [copy.deepcopy(x) for x in states['multipart'] if x.get('when', {}).get('compact') == 'true' and x['when'].get('variant') == '3' and x['when'].get('art_bank', '0') == '0']
    assert len(base_branches) == 4
    for branch in states['multipart']:
        if branch.get('when', {}).get('compact') == 'true' and 'art_bank' not in branch['when']:
            branch['when']['art_bank'] = '0'
    mapping = []
    for index, source in enumerate(sources, 4):
        pose = copy.deepcopy(old_prefix[3])
        pose['sourceStateRefs'] = [source['sourceState']]
        pose['sourcePropertyAlias'] = False
        pose['nativeSourceOptics'] = source['sourceState']
        pose['conversionOnly'] = True
        if len(descriptor['variants']) == index:
            descriptor['variants'].append(pose)
        else:
            assert descriptor['variants'][index] == pose
        if index not in compact_row['poses']:
            compact_row['poses'].append(index)
        if index not in compact_row['opaquePoses']:
            compact_row['opaquePoses'].append(index)
        compact_row['shapes'][str(index)] = copy.deepcopy(shapes)
        reference_rotation = reference_rotations[source['sourceState']]
        compact_row.setdefault('nativeSourceOptics', {})[str(index)] = {'sourceState': source['sourceState'], 'referenceRotation': reference_rotation}
        optical = {'registryId': FAMILY, 'variant': index, 'open': False, 'stateProperties': {}, 'sources': [copy.deepcopy(source)]}
        existing = next((x for x in lighting['objects'] if x['registryId'] == FAMILY and x['variant'] == index and not x['open']), None)
        if existing is None:
            lighting['objects'].append(optical)
        else:
            assert existing == optical
        part = {'registryId': FAMILY, 'variant': index, 'mainId': '90935', 'sourceApplications': copy.deepcopy(pose['sourceClosedApplications']), 'defaultInstallationPose': 2, 'status': 'EXACT_EXISTING_ARTWORK_WITH_NATIVE_SOURCE_OPTICS'}
        existing = next((x for x in parts['parts'] if x['registryId'] == FAMILY and x['variant'] == index), None)
        if existing is None:
            parts['parts'].append(part)
        else:
            assert {k:v for k,v in existing.items() if k!='defaultInstallationPose'} == {k:v for k,v in part.items() if k!='defaultInstallationPose'}
            existing['defaultInstallationPose'] = 2
        for original in base_branches:
            branch = copy.deepcopy(original)
            branch['when']['variant'] = str(index % 16)
            branch['when']['art_bank'] = str(index // 16)
            if branch not in states['multipart']:
                states['multipart'].append(branch)
        mapping.append({'registryId': FAMILY, 'sourceState': source['sourceState'], 'oldPose': 3, 'newPose': index, 'referenceRotation': reference_rotation, 'properties': {'variant': str(index % 16), 'art_bank': str(index // 16)}, 'occurrences': source['occurrences'], 'caseIds': source['caseIds']})
    assert len(descriptor['variants']) == 28
    assert descriptor['variants'][:4] == old_prefix
    aliases_path = DATA / 'imported-type-aliases.json'
    aliases = read(aliases_path)
    group = next(x for x in aliases['groups'] if x['primaryRegistryId'] == FAMILY)
    group['poseCount'] = 28
    group['opticalSourcePosePolicy'] = 'APPEND_ONLY_24_HIDDEN_NATIVE_CARRIER_STATES; ORIGINAL_ART_AND_ALIAS_POSES_UNCHANGED'
    for path, value in [(descriptor_path, descriptor), (compact_path, compact), (light_path, lighting), (parts_path, parts), (states_path, states), (aliases_path, aliases)]:
        write(path, value)
    output = ROOT / 'docs/final-city-repair/geometry/compact-optical-pose-mapping.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    write(output, {'schemaVersion': 1, 'registryId': FAMILY, 'originalPosePrefix': 4, 'totalPoses': 28, 'nativeSourceStates': 24, 'occurrences': 2905, 'blockEntitiesAdded': 0, 'oldGeometryAndStatePrefixPreserved': True, 'newPosesHiddenFromArtCyclingAndPickRedirectsTo': 2, 'mappings': mapping})
    print(json.dumps({'status': 'PASS_APPENDED_FINITE_OPTICAL_SOURCE_POSES', 'mapping': str(output), 'poses': 24, 'occurrences': 2905, 'blockEntitiesAdded': 0}))

if __name__ == '__main__':
    main()
