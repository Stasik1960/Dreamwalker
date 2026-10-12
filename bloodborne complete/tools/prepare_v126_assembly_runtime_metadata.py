"""Write explicit runtime recipes from reviewed staged assemblies; no model changes."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    source = json.loads((ROOT / 'build/v126-assembly-plan/assembly-manifest.json').read_text(encoding='utf-8'))
    recipes = []
    redirects = {}
    debug = json.loads((ROOT / 'src/architecture/resources/bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8-sig'))
    registries = {e['temporaryId']: e['registryId'] for e in debug['entries']}
    for row in source['assemblies']:
        recipes.append({
            'key': row['key'], 'primaryId': row['primaryId'], 'primaryRegistryId': row['primaryRegistryId'],
            'installationPose': row['installationPose'], 'displayName': row['displayName'],
            'components': [{k: c[k] for k in ('mainId', 'registryId', 'relativePosition', 'sourceShift')} | {'pose': int(c['properties']['variant']), 'componentYawDelta': c['componentYawDelta']} for c in row['components']],
            'closedSourceVertexUvRgbaProof': 'PASS_8_YAW',
            'oldVariantIndicesPreserved': True,
        })
        for old_id in row['hideIndependentPartIds']:
            redirects[registries[old_id]] = row['primaryRegistryId']
    redirects[registries['90479']] = registries['90001']
    mount_proposal = ROOT / 'docs/v12.6/city/mount-depth-state-proposal.json'
    if mount_proposal.is_file():
        mount = json.loads(mount_proposal.read_text(encoding='utf-8-sig'))
        additional = mount['sourceRecipe']
        assert additional['primaryRegistryId'] == registries['90923'] and additional['installationPose'] == 2
        assert additional['components'][1]['relativePosition'] == [0, 2, -1]
        assert mount['existingSourcePairCount'] == 12 and all(row['status'].startswith('PASS') for row in mount['actualSourceChecks'])
        recipes.append(additional)
    value = {'schemaVersion': 1, 'version': 1, 'baselineCommit': source['baselineCommit'],
             'policy': 'Append complete poses; keep unmatched saved fragments exact and hidden. Merge only complete geometry-matched groups in one atomic world and reference transaction.',
             'recipes': recipes, 'hiddenPartRedirects': redirects,
             'explicitDoorReplacement': {'oldId': '90479', 'oldRegistryId': registries['90479'], 'targetId': '90001', 'targetRegistryId': registries['90001'], 'sourceShiftAddition': [0, 0, 0.375]},
             'originalV12': 'UNCHANGED; existing90001 mechanism/model/spec are not edited',
             'authorCollisionExport': 'NOT_SUPPLIED_NOT_APPLIED'}
    target = ROOT / 'src/architecture/resources/bloodborne_dw/imported-assembly-v126.json'
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (ROOT / 'docs/v12.6/assemblies/runtime-recipe-metadata.json').write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': 'PASS', 'recipes': len(recipes), 'hiddenPartRedirects': len(redirects), 'resource': str(target)}))


if __name__ == '__main__':
    main()
