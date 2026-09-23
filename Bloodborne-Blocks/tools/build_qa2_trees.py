"""Explicit QA2 tree correction; no discovery, source edits or city conversion.

The old C001/C009 six-log proposals straddled TWO adjacent trees. Each real tree
has four central wool cells and twelve atlas-wing cells. Frozen authored input
keeps those boundaries and source states separate from the runtime footprints.
"""
from __future__ import annotations
import argparse
import copy
import gzip
import hashlib
import json
from pathlib import Path

from build_reviewed_contracts import (LOGICAL, RESOURCES, ROOT, FACINGS, bounds,
    copy_texture, definition, dump, key, rotate_polygons, write_assets)
from compile_reviewed_migration import choices
from source_assembly_visuals import source_polys
from source_variant_rng import guards_for
from sync_reviewed_geometry import profile

TREE_ID = 'o_c001'
RETIRED = ('o_c001_a', 'o_c001_b', 'o_c009_a', 'o_c009_b', 'o_dead_tree_planter')
AUTHORING = ROOT / 'docs/reviewed-batch-02-qa2-trees.json'
COLLISION = [.25, 0, .25, .75, 10, .75]
SELECTION = [0, 0, 0, 1, 10, 1]


def corrected_tree_apps(apps):
    """Repair the positive-Z wing in the second frozen raw layout.

    Its jungle/acacia/dark-oak carriers use the negative atlas wing (U4.9375
    ..7.9375) on BOTH Z sides. The positive wing must continue the central
    U7.9375..10.9375 panel with U11..14, as the other frozen layout does.
    Keep matching/RNG and variant identities based on the unmodified sources.
    """
    models = {'jungle_log': 'oak_log', 'acacia_log': 'spruce_log', 'dark_oak_log': 'birch_log'}
    result = copy.deepcopy(apps)
    for app in result:
        name = app['model'].rsplit('/', 1)[-1]
        if app.get('offset') in ([0, 4, 3], [0, 7, 3], [0, 10, 3]) and app.get('y') == 180 and name in models:
            app['model'] = 'minecraft:block/' + models[name]
    return result


def capture_authoring(path):
    """Freeze the explicitly checked 16-cell assemblies; never silently re-author."""
    if AUTHORING.exists():
        raise FileExistsError('QA2 tree authoring already exists; explicit review required')
    evidence = json.loads(Path(path).read_text(encoding='utf8'))
    examples = evidence['complete_trees']
    if len(examples) != 8 or any(len(t['components']) != 16 for t in examples):
        raise ValueError('Expected eight bounded, complete tree examples')
    patterns = {}
    for example in examples:
        signature = example['exact_source_signature']
        patterns.setdefault(signature, {'exact_source_signature': signature,
                                       'components': example['components'], 'examples': []})['examples'].append(
            {k: example[k] for k in ('review_id', 'review_pattern', 'dimension', 'master')})
    document = {'schemaVersion': 1, 'authority': 'user QA2 complete-tree correction',
                'review_ids': ['C001', 'C009'], 'logical_id': TREE_ID,
                'superseded_building_items': list(RETIRED), 'source_sha256': evidence['source_sha256'],
                'boundary': 'one white_wool planter with four central tiers and four three-tier wings at +/-3 X/Z',
                'anchor': 'white_wool carrier minus [0,1,0], bottom center of trunk/planter',
                'history': 'supersedes SPLIT for tree construction only; original catalog C-IDs/signatures/decisions remain frozen',
                'patterns': list(patterns.values())}
    dump(AUTHORING, document, pretty=True)


def build():
    authored = json.loads(AUTHORING.read_text(encoding='utf8'))
    definitions = json.loads((LOGICAL / 'definitions.json').read_text(encoding='utf8'))
    had_visual_slots=any(d['id']==TREE_ID and 'visual' in d['properties'] for d in definitions['blocks'])
    contracts = json.loads((LOGICAL / 'contracts-v2.json').read_text(encoding='utf8'))
    geometry = json.loads((LOGICAL / 'geometry.json').read_text(encoding='utf8'))
    with gzip.open(LOGICAL / 'meshes.json.gz', 'rt', encoding='utf8') as stream:
        meshes = json.load(stream)
    variants, rules = {}, []
    for pattern in authored['patterns']:
        parts = pattern['components']
        if len(parts) != 16 or len({tuple(p['offset']) for p in parts}) != 16:
            raise ValueError('Complete tree must have sixteen distinct source cells')
        for selected in choices(parts):
            polygons = source_polys([app for group in selected for app in group])[0]
            # One full geometry record per visual variant; no component renderer.
            token = hashlib.sha256(json.dumps(polygons, sort_keys=True).encode()).hexdigest()[:12]
            variant = 'tree_' + token
            corrected = source_polys(corrected_tree_apps([app for group in selected for app in group]))[0]
            variants.setdefault(variant, corrected)
            guards = []
            for part, apps in zip(parts, selected):
                guards.extend(guards_for(part, apps, part['offset']))
            rules.append((variant, {'components': [{k: p[k] for k in ('id', 'properties', 'offset')} for p in parts],
                                   'variant_guards': guards, 'authority': 'user', 'review_id': 'C001',
                                   'exact_source_signature': pattern['exact_source_signature'], 'source_rotation': 0,
                                   'matching_evidence': 'exact sixteen raw cells and positional RNG; complete source mesh baked at trunk-base master'}))
    names = sorted(variants)
    block = definition(TREE_ID, {'facing': list(FACINGS), 'variant': names})
    block['semantic'] = 'tree'
    family = {'id': TREE_ID, 'review_id': 'C001', 'review_ids': ['C001', 'C009'], 'authority': 'user', 'compiler_owner': 'qa2_trees',
              'canonical_anchor': {'cell': [0, 0, 0], 'pivot': [.5, 0, .5]},
              'placement_policy': 'FLOOR', 'rotations': [0, 90, 180, 270], 'mirror_policy': 'ROTATE_ONLY',
              'collision_policy': 'TRUNK', 'selection_policy': 'TRUNK',
              'collision_justification': 'One central vertical trunk primitive; planter rim, branches and canopy have no collision/helpers.',
              'review_source_patterns': authored['patterns'], 'states': {}}
    for state in block['states']:
        values = dict(pair.split('=', 1) for pair in state.split(','))
        yaw = FACINGS.index(values['facing']) * 90
        polygons = rotate_polygons(variants[values['variant']], yaw)
        for polygon in polygons:
            polygon['texture'] = copy_texture(polygon['texture'])
        mesh = 'qa2_complete_tree_' + hashlib.sha256(state.encode()).hexdigest()[:12]
        meshes[mesh] = {'polygons': polygons}
        block['models'][state] = mesh
        family['states'][state] = {'rotation': yaw, 'render_mesh': {'id': mesh, 'bounds': bounds(polygons), 'offset': [0, 0, 0]},
                                  'selection_footprint': {'boxes': [SELECTION]}, 'collision_footprint': {'boxes': [COLLISION]},
                                  'interaction_footprint': {'cells': [[0, y, 0] for y in range(10)]},
                                  'migration_source_pattern': [copy.deepcopy(rule) for variant, rule in rules
                                                               if variant == values['variant'] and yaw == 0]}
        textures = sorted({p['texture'] for p in polygons})
        dump(RESOURCES / 'assets/bloodborne_blocks/models/block/logical' / (mesh + '.json'),
             {'parent': 'minecraft:block/block', 'textures': {'particle': textures[0],
              **{str(i): t for i, t in enumerate(textures)}}, 'elements': []})
    definitions['blocks'] = [block if d['id']==TREE_ID else d for d in definitions['blocks']]
    # Registry IDs and old placed-block semantics stay loadable. They are not
    # construction options; aliases redirect inventory/pick to the entire tree.
    contracts['families'] = [family if f['id']==TREE_ID else f for f in contracts['families']]
    geometry['blocks'][TREE_ID] = profile(family)
    hidden_path = LOGICAL / 'hidden-items.json'
    hidden = set(json.loads(hidden_path.read_text(encoding='utf8')))
    hidden.update(RETIRED)
    migration_path = LOGICAL / 'migration.json'
    migration = json.loads(migration_path.read_text(encoding='utf8'))
    # Inventory-only: never feed PaletteMigration's per-block world replacement.
    # Replacing each old wing independently in a world would duplicate trees.
    for ident in RETIRED:
        migration.setdefault('item_aliases', {})[ident] = {'id': TREE_ID, 'properties': block['default']}
    write_assets(TREE_ID, block)
    # The whole tree is twelve blocks tall. Fit this item's complete mesh in
    # the inventory slot instead of retaining the generic one-block GUI scale.
    item_path = RESOURCES / f'assets/bloodborne_blocks/models/item/{TREE_ID}.json'
    item_model = json.loads(item_path.read_text(encoding='utf8'))
    item_model['display']['gui'] = {'rotation': [30, 225, 0], 'translation': [0, -4, 0], 'scale': [.05, .05, .05]}
    dump(item_path, item_model)
    for locale, name in (('en_us', 'Complete dead tree'), ('ru_ru', 'Цельное сухое дерево')):
        path = RESOURCES / f'assets/bloodborne_blocks/lang/{locale}.json'
        data = json.loads(path.read_text(encoding='utf8'))
        data['block.bloodborne_blocks.' + TREE_ID] = name
        dump(path, data)
    dump(LOGICAL / 'definitions.json', definitions)
    dump(LOGICAL / 'contracts-v2.json', contracts, pretty=True)
    dump(LOGICAL / 'geometry.json', geometry)
    dump(LOGICAL / 'meshes.json.gz', meshes, zipped=True)
    dump(hidden_path, sorted(hidden))
    dump(migration_path, migration)
    dump(ROOT / 'docs/reviewed-batch-02-qa2-tree-evidence.json',
         {'logical_id': TREE_ID, 'review_ids': ['C001', 'C009'], 'source_patterns': len(authored['patterns']),
          'complete_source_cells': 16, 'variants': names, 'states': len(block['states']), 'exact_rules': len(rules),
          'retired_items': list(RETIRED), 'collision': COLLISION, 'selection': SELECTION,
          'helpers': [[0, y, 0] for y in range(1, 10)], 'world_scan': False}, pretty=True)
    print('QA2 trees:', len(names), 'complete variants,', len(rules), 'exact guarded rules')
    if had_visual_slots:
        from build_visual_slots import build as restore_visual_slots
        restore_visual_slots()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--capture-authoring', type=Path)
    args = parser.parse_args()
    if args.capture_authoring:
        capture_authoring(args.capture_authoring)
    build()
