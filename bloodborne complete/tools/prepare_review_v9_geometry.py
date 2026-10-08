"""Promote the exact user-accepted V8 tree art and set one axis-aligned roof cube."""
import copy
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources/bloodborne_dw/composite'
V8 = ROOT / 'build/delivery/v8/dreamwalker-bb-fabric-1.20.1-review-v8-full-source.zip'
PREFIX = 'dreamwalker-bb-fabric-1.20.1-full-source-review-v8/src/architecture/resources/bloodborne_dw/composite/'


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    with zipfile.ZipFile(V8) as frozen:
        tree_bytes = frozen.read(PREFIX + 'prototype_tree.json')
        roof_bytes = frozen.read(PREFIX + 'prototype_roof.json')
    tree = json.loads(tree_bytes)
    original = copy.deepcopy(tree['variants'][0])
    accepted = copy.deepcopy(tree['variants'][1])
    assert len(accepted['poses']['closed']['parts']) == 18
    original_upper = [part for part in original['poses']['closed']['parts']
                      if part['model'] != 'bloodborne_dw:tree_source/block/melon']
    assert len(original_upper) == 16
    assert accepted['poses']['closed']['parts'][2:] == original_upper
    accepted['reviewStatus'] = 'USER_ACCEPTED_V8_LOWER_AND_UPPER'
    tree['variants'] = [accepted]
    tree['sourceEvidence']['lowerProposal'].update({
        'variant': 0, 'formerlyV8Variant': 1, 'status': 'USER_ACCEPTED_V8_ONLY_EXECUTION',
        'variant0OriginalArtUnchanged': False,
        'legacyVariantMapping': {'0': 0, '1': 0},
        'compatibility': 'Old variants render the accepted execution at the same world pose; old1 is normalized to0 during update',
    })
    dump(RES / 'prototype_tree.json', tree)
    roof = json.loads(roof_bytes)
    before = copy.deepcopy(roof['variants'])
    for variant in roof['variants']:
        for pose in variant['poses'].values():
            pose['collision'] = [{'from': [0, 0, 0], 'to': [16, 16, 16]}]
    roof['physicalPlacementRules'] = 'One1x1x1 axis-aligned base cube; MountY/SourceShift translate it, visual yaw does not rotate or rasterize it. Selection/render/reservation separate.'
    roof['collisionRotation'] = 'GRID_ALIGNED'
    dump(RES / 'prototype_roof.json', roof)
    report = {
        'schema': 'dreamwalker-review-v9-accepted-geometry-v1',
        'status': 'IMPLEMENTED_USER_ACCEPTED_TREE_AND_REQUESTED_ROOF_CUBE_RUNTIME_PENDING',
        'reviewed_v8_source_archive_sha256': digest(V8.read_bytes()),
        'tree': {'type': 90005, 'v8_descriptor_sha256': digest(tree_bytes),
                 'new_descriptor_sha256': digest((RES / 'prototype_tree.json').read_bytes()),
                 'old_variant_mapping': {'0': 0, '1': 0}, 'ordinary_variants': 1,
                 'accepted_upper16_exact': True, 'accepted_lower2_exact': True,
                 'physical_boxes': 1, 'selection_boxes': 4, 'world_pose_unchanged': True},
        'roof': {'type': 90003, 'v8_descriptor_sha256': digest(roof_bytes),
                 'new_descriptor_sha256': digest((RES / 'prototype_roof.json').read_bytes()),
                 'physical_boxes_before': len(before[0]['poses']['closed']['collision']),
                 'physical_boxes_after': 1, 'physical_size_blocks': [1, 1, 1],
                 'axis_aligned_for_all_visual_yaws': True,
                 'art_and_selection_exact': all(before[i]['poses'][name]['parts'] == pose['parts'] and before[i]['poses'][name]['selection'] == pose['selection']
                     for i, variant in enumerate(roof['variants']) for name, pose in variant['poses'].items())},
        'minecraft_runtime': 'NOT_RUN_CURRENT_ARTIFACT', 'full_city': 'NOT_RUN',
    }
    dump(ROOT / 'reports/REVIEW_V9_ACCEPTED_GEOMETRY.json', report)
    print(json.dumps({'status': report['status'], 'tree_variants': 1, 'roof_collision_boxes': 1}))


if __name__ == '__main__':
    main()
