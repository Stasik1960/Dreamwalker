"""Exact release-JAR catalogue gate for every source-city owner.

Read-only except independent docs. A descriptor/quad match never substitutes
for runtime registration. Explicit transparent/excluded source artwork is
classified with source evidence rather than restored into the mod.
"""
import collections
import hashlib
import io
import json
from pathlib import Path
import zipfile

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.6-functional-assemblies.jar'
CITY = ROOT/'docs/v12.6/city/city-final-aggregated.json'
DEST = ROOT/'docs/v12.6/city'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    data = read(CITY)
    exclusions = read(ROOT/'docs/remaining-transfer/source-transparent-exclusions.json')
    excluded = collections.defaultdict(list)
    for entry in exclusions['entries']:
        excluded[entry['registryId']].append(entry)
    with zipfile.ZipFile(JAR) as jar:
        catalogue = json.loads(jar.read('bloodborne_dw/remaining-catalogue.json'))
        debug = json.loads(jar.read('bloodborne_dw/debug_catalogue.json'))
        debugrows = {r['registryId']: r for r in debug['entries']}
        new = {'bloodborne_dw:'+r['path'] for r in catalogue['objects']}
        # Exact initialisation path: RemainingArchitecture.paths appends the
        # catalogue to these five preserved V12 composite prototypes. Native
        # V12 wall and ladder registrations are explicit in their initializers.
        originals = {'bloodborne_dw:'+p for p in ['prototype_double_door', 'prototype_wood_window',
                    'prototype_thin_window', 'prototype_tree', 'prototype_roof', 'prototype_ladder',
                    'prototype_ladder_art_1', 'prototype_ladder_art_2', 'prototype_wall']}
        originals.update('bloodborne_dw:prototype_wall_skin_'+str(i) for i in [0,1,2,3,4,5,7])
        technical = {'bloodborne_dw:source_ladder_backing'}
        entrypoint = jar.read('dev/dreamwalker/bloodbornedw/DreamwalkerBb.class')
        assert all(p.split(':')[1].encode() in entrypoint for p in originals if p in
                   {'bloodborne_dw:prototype_double_door','bloodborne_dw:prototype_wood_window',
                    'bloodborne_dw:prototype_thin_window','bloodborne_dw:prototype_tree','bloodborne_dw:prototype_roof'})
        assert b'bloodborne_dw/remaining-catalogue.json' in jar.read('dev/dreamwalker/bloodbornedw/architecture/RemainingArchitecture.class')
        registered = new|originals|technical
        groups = collections.defaultdict(list)
        for owner in data['owners']:
            if owner['registryId'] not in registered:
                groups[owner['registryId']].append(owner)
        missing = []
        for registry, owners in sorted(groups.items()):
            states = collections.Counter(json.dumps(r['properties'], sort_keys=True) for r in owners)
            row = {'registryId': registry, 'count': len(owners), 'mainIds': sorted(set(r['mainId'] for r in owners)),
                   'debugCatalogueEntry': debugrows.get(registry), 'stateCounts': dict(states),
                   'sourceCarrierCounts': dict(collections.Counter(r['sourceCarrier'] for r in owners)),
                   'sourceApplicationCounts': dict(collections.Counter(json.dumps(r['sourceApplications'], sort_keys=True) for r in owners)),
                   'sourcePositions': [r['sourcePosition'] for r in owners],
                   'provenance': [{'sourcePosition': r['sourcePosition'], 'instanceUuid': r['instanceUuid'],
                                  'sourceOldRegistryId': r.get('sourceOldRegistryId'), 'sourceOldVariant': r.get('sourceOldVariant'),
                                  'resolution': r.get('resolution')} for r in owners]}
            if registry in excluded:
                proofs = []
                for entry in excluded[registry]:
                    for texture in entry['textures']:
                        namespace, path = texture['texture'].split(':', 1) if ':' in texture['texture'] else ('minecraft', texture['texture'])
                        relocated = 'assets/bloodborne_dw/textures/block/remaining/source/'+namespace+'/'+path+'.png'
                        pixels = jar.read(relocated)
                        image = Image.open(io.BytesIO(pixels)).convert('RGBA')
                        alpha = list(image.getchannel('A').getextrema())
                        assert alpha == [0,0], (registry, texture['texture'], alpha)
                        assert hashlib.sha256(pixels).hexdigest() == texture['sha256']
                        proofs.append({'texture': texture['texture'], 'jarEntry': relocated, 'sha256': texture['sha256'],
                                       'alphaExtrema': alpha, 'size': list(image.size)})
                expected = set(model for entry in excluded[registry] for model in entry['sourceModels'])
                assert all(set(a['model'] for a in r['sourceApplications']) <= expected for r in owners)
                row.update({'classification': 'EXPLICIT_PREEXISTING_SOURCE_TRANSPARENT_HELPER',
                            'proposedDisposition': 'sourceInvisibleServices + nativeConversion=minecraft:air; do not restore registration or vanilla glass',
                            'exclusionEvidence': 'docs/remaining-transfer/source-transparent-exclusions.json',
                            'actualReleasePngProofs': proofs,
                            'reason': exclusions['reason']})
            else:
                row.update({'classification': 'UNCLASSIFIED_MISSING_REGISTRATION_REQUIRES_EXACT_SOURCE_REVIEW',
                            'proposedDisposition': 'Do not guess a replacement, restore excluded artwork, or silently omit.'})
            missing.append(row)
        report = {'schemaVersion': 1, 'status': 'PASS_ALL_CITY_OWNERS_RUNTIME_REGISTERED' if not missing else 'FAIL_UNREGISTERED_SOURCE_CITY_OWNERS',
                  'productionJar': str(JAR), 'productionJarSha256': sha(JAR), 'citySha256': sha(CITY),
                  'runtimeNewCatalogueRegistrations': len(new), 'protectedV12BuildingRegistrations': len(originals),
                  'ownerCount': len(data['owners']), 'distinctOwnerRegistryIds': len(set(r['registryId'] for r in data['owners'])),
                  'registeredOwnerCount': sum(r['registryId'] in registered for r in data['owners']),
                  'missingOwnerCount': sum(len(v) for v in groups.values()), 'missingDistinctRegistryCount': len(groups),
                  'missing': missing, 'originalMapWritten': False, 'productionResourcesWritten': False,
                  'nativeAuthorFailureRootCause': 'Historical resolver indexes all frozen descriptors, including explicit transparent exclusions. V12.6 finalizer filtered style retirements but omitted the earlier transparent-exclusion gate; geometric descriptor proof cannot establish registration.'}
        report['explicitTransparentSourceServiceCount']=sum(r.get('status')=='EXPLICIT_PREEXISTING_SOURCE_TRANSPARENT_HELPER_AIR' for r in data['sourceInvisibleServices'])
        report['transparentServiceCountsByReservedId']=dict(collections.Counter(r.get('reservedNumber') for r in data['sourceInvisibleServices']
            if r.get('status')=='EXPLICIT_PREEXISTING_SOURCE_TRANSPARENT_HELPER_AIR'))
        target = DEST/'exact-jar-city-registration-audit.json'
        if missing:
            (DEST/'exact-jar-city-registration-initial-missing.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf8', newline='\n')
        target.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf8', newline='\n')
        print(json.dumps({k: report[k] for k in ['status','productionJarSha256','ownerCount','distinctOwnerRegistryIds',
                                              'registeredOwnerCount','missingOwnerCount','missingDistinctRegistryCount']}, ensure_ascii=False))
        print(json.dumps({'missing': [{k:r[k] for k in ['registryId','count','mainIds','classification','sourceCarrierCounts']} for r in missing]}, ensure_ascii=False))


if __name__ == '__main__':
    main()
