"""Future-import ID/pose handoff. Does not register or apply author collisions."""
from pathlib import Path
import argparse
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/architecture/resources/bloodborne_dw'
OUT = ROOT / 'docs/v12.7/collision'

def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--native-snapshot', type=Path)
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    rows = load(RES / 'debug_catalogue.json')['entries']
    by_registry = {row['registryId']: row for row in rows}
    assert len({row['temporaryId'] for row in rows}) == len(rows)
    canonical = {row['registryId']: row.get('canonicalRegistryId', row['registryId']) for row in rows}
    alias_documents = []
    for manifest in ['imported-type-aliases.json', 'imported-type-aliases-v127.json']:
        path = RES / manifest
        if path.exists():
            document = load(path)
            alias_documents.append((manifest, document))
            for group in document['groups']:
                for member in group['members']:
                    canonical[member] = group['primaryRegistryId']
    historical = alias_documents[0][1]['mappings']
    followup = alias_documents[1][1]['mappings'] if len(alias_documents) > 1 else []
    additions = {(row['oldRegistryId'], row['oldVariant']): row for row in followup}
    composed = []
    for row in historical:
        current = dict(row)
        later = additions.get((row['newRegistryId'], row['newVariant']))
        if later:
            current.update(newId=later['newId'], newRegistryId=later['newRegistryId'], newVariant=later['newVariant'])
        composed.append(current)
    canonical.update(load(RES / 'imported-assembly-v126.json')['hiddenPartRedirects'])
    def resolve(registry):
        seen = set()
        while canonical.get(registry, registry) != registry:
            assert registry not in seen, registry
            seen.add(registry)
            registry = canonical[registry]
        return registry
    snapshot = load(args.native_snapshot) if args.native_snapshot else None
    blocks = {row['registryId']: row for row in snapshot['blocks']} if snapshot else {}
    compact = {row['registryId']: row for row in load(RES / 'imported-compact-v127.json')['types']}
    registered_new = {'bloodborne_dw:' + row['path'] for row in load(RES / 'remaining-catalogue.json')['objects']}
    rp_models = load(ROOT / 'src/rp/resources/assets/bloodborne_rp/catalog.json')
    output = []
    for row in rows:
        registry = row['registryId']
        target = resolve(registry)
        record = {'numericIdReserved': row['temporaryId'], 'rawRegistryId': registry,
                  'rawAliases': row['aliases'], 'kind': row['kind'],
                  'canonicalRegistryId': target, 'canonicalNumericId': by_registry.get(target, {}).get('temporaryId'),
                  'numberReused': False, 'authorExport': 'NOT_SUPPLIED_NOT_APPLIED'}
        old_pose_mappings = [item for item in composed if item['oldRegistryId'] == registry]
        current_pose_mappings = [item for item in followup if item['oldRegistryId'] == registry]
        if old_pose_mappings or current_pose_mappings:
            record['legacyPoseCorrespondence'] = {
                'preV124MappingsComposedThroughV127': old_pose_mappings,
                'currentV124CopiedPoseMappings': current_pose_mappings,
                'interpretation': 'Version-aware saved-owner/item migration; rotationDelta/sourceShift/properties are exact mapping transforms, not an author collision wildcard. Current nonzero art_bank poses are already canonical and do not reuse historical low-four-bit selectors.'}
        path = RES / 'composite' / (registry.split(':', 1)[1] + '.json')
        if row['kind'] == 'rp_object':
            record.update(collisionTarget=True, runtimeRepresentation='RP_OBJECT_ENTITY',
                          legacyStudioSelectorTypes={'Open': 'JSON_BOOLEAN', 'DogsVisible': 'JSON_BOOLEAN_WHERE_PRESENT'},
                          addedVisualSelector={'BbAltVisual': 'JSON_BOOLEAN'},
                          legacySelectorMigration='Explicit legacy-selector binding to BbAltVisual=false; do not inherit BASE replacement into ALT',
                          baseNbt='BbAltVisual optional BYTE; omitted when BASE',
                          transformations=['asset scale', 'instance Scale', 'yaw/pitch', 'native animation/open transition', 'VerticalOffset already in Pos', 'Studio X already reflected'])
            record['sourceAssetPresent'] = registry.split(':', 1)[1] in rp_models
        elif row['kind'] == 'architecture':
            registered = registry in blocks if snapshot else (not registry.startswith('bloodborne_dw:source_') or registry in registered_new)
            record.update(collisionTarget=registered,
                          registrationStatus='REGISTERED_ACTUAL_NATIVE' if registered and snapshot else 'REGISTERED_PENDING_NATIVE_SNAPSHOT' if registered else 'RESERVED_UNREGISTERED_NEVER_RESTORE',
                          worldPaletteValueEncoding='STRINGS; these are not automatically the Studio typed selector',
                          selectorIntegration='Reconcile exact typed Studio selector per exported state; do not pass the whole palette state or add fields silently')
            if path.is_file():
                spec = load(path)
                state = blocks.get(registry, {})
                banked = 'art_bank' in state.get('worldPalettePropertyValues', {}) or len(spec['variants']) > 16
                record.update(descriptorSha256=sha(path), logicalPoseCount=len(spec['variants']),
                              poses=[{'logicalPose': index, 'palettePoseFields': {'variant': str(index % 16), **({'art_bank': str(index // 16)} if banked else {})},
                                      'profiles': ['base', 'alt'], 'openStates': [False, True],
                                      'openBehavior': bool(spec.get('openable', False)),
                                      'poseKind': 'AUTHORED_OR_EXPLICIT_SOURCE_APPLICATION'} for index in range(len(spec['variants']))],
                              transformations=['logical pose/art_bank', 'rotation 0..7', 'mount mode if present', 'MountY', 'VerticalOffset', 'local SourceShift once', 'open/closed pose'])
            if registry in compact:
                record.update(compactRepresentations={'false': 'Existing composite owner UUID/NBT', 'true': 'Exact imported native palette cube, no owner BE'},
                              compactPoseIndices=compact[registry]['poses'],
                              futureConsumer='The same model pose selector explicitly targets both compact=false and compact=true player movement paths; keep NPC, occupancy and placement physics unchanged')
            if registry in ('bloodborne_dw:dw_barrier', 'bloodborne_dw:dw_light'):
                record.update(runtimeRepresentation='NATIVE_UTILITY_MARKER', playersOnlyDefault=registry.endswith('dw_barrier'),
                              markerProfiles=['base', 'alt'], physicalPlayerDefault='FULL_CELL' if registry.endswith('dw_barrier') else 'EMPTY',
                              otherEntitiesAndPlacement='Preserve utility-specific permeability rules')
            if registry in blocks:
                record['actualRuntimeClass'] = blocks[registry]['runtimeClass']
                record['actualWorldPalettePropertyValues'] = blocks[registry]['worldPalettePropertyValues']
        else:
            record.update(collisionTarget=False, reason='Not a building block/placed RP object; item, weapon, mob or technical tool')
        output.append(record)
    handoff = ROOT / 'docs/COLLISION_IMPORT_HANDOFF.md'
    assert sha(handoff) == 'f7cddd1734e90d0f81dd63f1a3a373d54e4b5e203bdcdd7ad8b5de1c5bfc9f98', 'Original collision handoff must stay byte-exact'
    report = {'schema': 'dreamwalker-v127-future-collision-target-map-v1',
              'status': 'PASS_ACTUAL_NATIVE_BINDINGS' if snapshot else 'PREPARED_PENDING_NATIVE_SNAPSHOT',
              'rawReservedNumericIdCount': len(rows), 'uniqueNumericIds': True,
              'originalHandoffSha256': sha(handoff), 'authorExport': 'NOT_SUPPLIED_NOT_APPLIED',
              'runtimeCollisionOverrideConsumer': 'NOT_INSTALLED; attach during concrete reviewed author import',
              'parserLimits': {'modelsPerPartialProject': 512, 'statesPerProject': 32768, 'collidersPerState': 2048, 'seatsPerState': 64, 'jsonBytes': 16777216,
                               'interpretation': 'Batch limits do not restrict eligible existing mod IDs; combine reviewed partial projects explicitly without replacing already applied states silently'},
              'contract': {'unitsPerBlock': 16, 'rotationOrder': 'ZYX', 'scope': 'players_only', 'operation': 'replace_per_exact_state',
                           'unlistedStates': 'preserve', 'emptyReviewedReplacement': 'intentional empty player collision',
                           'selectorComparison': 'exact whole object and exact JSON primitive types; no wildcard or implicit BASE→ALT'},
              'protectedExceptions': ['90006 diagonal=true remains without physical collision unless user explicitly overrides',
                                     '90013–90017 and90018/90019 remain intentionally removed; never restore from old export',
                                     '90003 installation root remains occupied; permitted placement in protruding geometry retained',
                                     'Do not change current simple defaults, NPC collision, placement, support, door motion or no-collide lease when importing player-only data'],
              'nativeSnapshotSha256': sha(args.native_snapshot) if snapshot else None,
              'migrationManifestSha256': {name: sha(RES / name) for name, _ in alias_documents},
              'targets': output,
              'unassignedTechnicalBlockRegistries': sorted(set(blocks) - set(by_registry))}
    (OUT / 'target-map.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': report['status'], 'rawReservedNumericIdCount': len(rows), 'collisionTargets': sum(row['collisionTarget'] for row in output)}, ensure_ascii=False))

if __name__ == '__main__':
    main()
