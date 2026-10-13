"""Prepare a fresh catalogue gallery from the frozen V12.7 production JAR.

Reuses the reviewed V12.6 geometry/layout planner. No production or source-world
files are changed. Both alias generations are excluded from main platforms.
"""
import copy, hashlib, json, re, sys, zipfile
from pathlib import Path
import prepare_gallery_v126_input as previous

ROOT = Path(__file__).resolve().parents[1]
SHA = 'b98c752b797ef07c5acf1d4852bda83eec52717fed798d4ec76860b4f6972850'


def canonical_catalogue(original, archive):
    catalogue = copy.deepcopy(original)
    aliases = json.loads(archive.read('bloodborne_dw/imported-type-aliases.json'))
    newer = json.loads(archive.read('bloodborne_dw/imported-type-aliases-v127.json'))
    remaining = json.loads(archive.read('bloodborne_dw/remaining-catalogue.json'))
    debug = json.loads(archive.read('bloodborne_dw/debug_catalogue.json'))
    stairs = json.loads(archive.read('bloodborne_dw/imported-stair-types.json'))
    style = json.loads(archive.read('bloodborne_dw/imported-style-retirements.json'))
    assembly, defaults, active = previous.assembly_contract(archive)
    groups = {g['primaryRegistryId']: g for g in aliases['groups']}
    retired = {m for g in aliases['groups'] for m in g['members'] if m != g['primaryRegistryId']}
    for group in newer['groups']:
        # New aliases specify exact pose translations; they are compatibility
        # registrations rather than independent Creative types.
        for member in group['members']:
            identifier = member if isinstance(member, str) else member.get('registryId', member.get('oldRegistryId'))
            if identifier and identifier != group['primaryRegistryId']:
                retired.add(identifier)
    retired.update(m for g in style['retirements'] for m in g['aliases'])
    retired.update(assembly['hiddenPartRedirects'])
    names = {'bloodborne_dw:' + r['path']: r for r in remaining['objects']}
    numbers = {r['registryId']: r for r in debug['entries']}
    stair_paths = {'bloodborne_dw:' + r['path'] for r in stairs['stairTypes']}
    ladder_paths = {'bloodborne_dw:' + r['path'] for r in stairs['ladderTypes']}
    source_rows = {}
    for row in original['exhibits']:
        if row['section'] != 'error':
            source_rows.setdefault(row['registry'], []).append(row)
    exhibits = [copy.deepcopy(r) for r in original['exhibits'] if r['origin'] == 'V12' and r['section'] != 'error' and r['registry'] not in retired]
    for registry in sorted(set(names) - retired, key=lambda r: numbers[r]['temporaryId']):
        definition = names[registry]
        candidates = source_rows.get(registry, [])
        family = candidates
        if registry in groups:
            family = [r for member in groups[registry]['members'] for r in source_rows.get(member, [])]
        for recipe in assembly['recipes']:
            if recipe['primaryRegistryId'] == registry:
                family += [r for c in recipe['components'] for r in source_rows.get(c['registryId'], [])]
        row = copy.deepcopy(candidates[0]) if candidates else {
            'origin': 'NEW', 'kind': 'architecture', 'item': registry,
            'sourceIssues': [], 'mapStatus': 'ADDED_IN_V127', 'sourceParts': []}
        pose = defaults.get(registry, 0)
        state = {'variant': str(pose % 16), 'rotation': '0', 'open': 'false', 'profile': 'base'}
        if registry in stair_paths:
            state.update(connections='auto')
            if registry == 'bloodborne_dw:source_stairs_polished_deepslate_stairs_bottom':
                state['trim'] = 'false'
        if registry in ladder_paths:
            state['height'] = 'one'
        if len(definition['variants']) > 16 and registry not in stair_paths and registry not in ladder_paths:
            state['art_bank'] = str(pose // 16)
        row.update(key=registry, registry=registry, item=registry,
                   id=numbers[registry]['temporaryId'], name=definition['displayName'],
                   variant=pose, state=state, sourceModels=definition['variants'][pose]['sourceModels'],
                   section='absent' if family and all(r['section'] == 'absent' for r in family) else 'new',
                   availablePoseCount=len(definition['variants']),
                   workingTypePolicy='ONE_MAIN_INSTALLATION_ITEM_PER_CURRENT_WORKING_TYPE')
        if registry in active:
            row['workingPoseIndices'] = active[registry]
        exhibits.append(row)
    assert sum(r['origin'] != 'V12' for r in exhibits) == 539
    for number in ('93230', '93231'):
        matches = [r for r in debug['entries'] if r['temporaryId'] == number]
        assert len(matches) == 1
        entry = matches[0]; registry = entry['registryId']
        exhibits.append({'key': registry, 'registry': registry, 'item': registry,
            'id': number, 'name': entry['name'], 'kind': 'utility', 'origin': 'NEW',
            'section': 'utility', 'variant': 0, 'state': {}, 'sourceModels': [],
            'sourceParts': [], 'sourceIssues': [], 'mapStatus': 'NEW_UTILITY',
            'localVisualBounds': [0,0,0,1,1,1],
            'visualGeometryEvidence': 'Invisible utility with its held marker and item-frame icon; unit placement envelope'})
    catalogue['exhibits'] = exhibits
    catalogue['styleRetirements'] = style['retirements']
    catalogue['retiredCompatibilityAliasCount'] = len(retired)
    catalogue['sections'] = [r for r in catalogue['sections'] if r['id'] != 'error']
    catalogue['sections'] += [{'id':'items','title':'Предметы V12 в рамках','floor':'minecraft:blue_concrete'},
                              {'id':'utility','title':'Служебные блоки V12.7','floor':'minecraft:orange_concrete'}]
    catalogue['displayPolicy'] = 'V12 retained; 539 current NEW architecture types and two utility blocks, no retired aliases or damaged objects'
    catalogue['classificationPolicy'] = 'Preserved original/absent classification; V12.7 additions classified NEW'
    assert len({r['key'] for r in exhibits}) == len(exhibits)
    return catalogue, [r for r in original['exhibits'] if r['section'] == 'error'], retired


def main():
    previous.canonical_catalogue = canonical_catalogue
    previous.SCHEMA = 'dreamwalker-flat-gallery-v127'
    previous.main()
    output = Path(sys.argv[sys.argv.index('--output') + 1])
    marker = json.loads(output.read_text(encoding='utf-8'))
    assert marker['productionJarSha256'] == SHA
    marker['displayRevision'] = 'V12.7'
    # Utilities have no visible block model. Their current item is displayed
    # beside their actual placed block; the cube is only the station envelope.
    for row in marker['catalogue']['exhibits']:
        if row['kind'] == 'utility':
            marker['localVisualBounds'][row['key']] = row['localVisualBounds']
    marker['catalogue']['summary']['newWorkingTypes'] = 541
    marker['catalogue']['summary']['newArchitectureTypes'] = 539
    marker['catalogue']['summary']['utilityTypes'] = 2
    marker['reviewPlatforms'] = [{**r, 'key': r['key'].replace('V126-', 'V127-')} for r in marker['reviewPlatforms']]
    marker['city']['galleryPurpose'] = 'Compatibility/performance sample from the existing reviewed source clip; original city unchanged'
    output.write_text(json.dumps(marker, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status':'PASS_V127_GALLERY_INPUT','buildingItems':len(marker['catalogue']['exhibits']),
                      'newWorkingTypes':541,'utilityTypes':2,'productionJarSha256':SHA}))


if __name__ == '__main__':
    main()
