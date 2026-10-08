"""Read-only source/V7/current evidence for the reported thin-window ambiguity."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = Path('C:/Users/vakir/Downloads/bbmc_v15_resource (1).zip')
CHECKPOINT = ROOT / 'build/delivery/v7/dreamwalker-bb-fabric-1.20.1-v7-full-source.zip'
PREFIX = 'dreamwalker-bb-fabric-1.20.1-source-v7/'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def main():
    delivery = json.loads((CHECKPOINT.parent / 'dreamwalker-bb-fabric-1.20.1-v7-delivery-manifest.json').read_text(encoding='utf8'))
    assert sha(CHECKPOINT.read_bytes()) == delivery['source_archive']['archive_sha256']
    rows = []
    with zipfile.ZipFile(PACK) as source, zipfile.ZipFile(CHECKPOINT) as v7:
        for number in range(1, 4):
            local = f'block/hold/window_{number:02}.json'
            source_bytes = source.read('assets/minecraft/models/' + local)
            path = ROOT / ('src/architecture/resources/assets/bloodborne_dw/models/base/source/minecraft/' + local)
            imported = json.loads(path.read_text(encoding='utf8'))
            authored = json.loads(source_bytes)
            assert imported['elements'] == authored['elements']
            assert path.read_bytes() == v7.read(PREFIX + path.relative_to(ROOT).as_posix())
            element = authored['elements'][0]
            rows.append({'variant': number - 1, 'sourceModel': 'minecraft:' + local[:-5],
                'sourceSha256': sha(source_bytes), 'importedSha256': sha(path.read_bytes()),
                'sourceGeometryAndUvExact': True, 'importedBytesExactV7': True,
                'from': element['from'], 'to': element['to'], 'rotation': element['rotation'],
                'faces': element['faces']})
    first, second, third = rows
    assert first['faces']['north']['uv'] == first['faces']['south']['uv'] == second['faces']['south']['uv']
    assert first['faces']['north']['uv'] != second['faces']['north']['uv']
    assert second['faces'] == third['faces']
    assert third['rotation']['angle'] == -45
    xml = ROOT / 'reports/FIRST_SET_GAMETEST_ATTEMPT_14.xml'
    cases = [case for case in ET.parse(xml).iter('testcase')
        if case.attrib['name'].startswith('prototypewindowgametests.')]
    assert cases and all(case.find('failure') is None and case.find('error') is None for case in cases)
    report = {'schema': 'dreamwalker-thin-window-review-diagnosis-v1',
        'status': 'PASS_SOURCE_AND_V7_VARIANT_CONTRACT_REPORTED_VISUAL_LOSS_NOT_REPRODUCED',
        'sourcePackSha256': sha(PACK.read_bytes()), 'v7SourceArchiveSha256': sha(CHECKPOINT.read_bytes()),
        'sourceForms': rows, 'v7NativeXml': str(xml), 'v7NativeXmlSha256': sha(xml.read_bytes()),
        'v7WindowTestcasesWithoutFailures': [case.attrib['name'] for case in cases],
        'sameAuthoredFace': 'window01 north/south and window02 south use the same UV; window02 north differs.',
        'diagonalSourceForm': 'window03 keeps window02 faces but uses intrinsic Y-45 and source Z endpoints4 units lower.',
        'diagnosis': 'The V7 explicit variant/item NBT tests already passed. A view of the shared back face can look identical; it does not prove art-variant loss.',
        'manualReportedCaseReproduced': False, 'manualUserAcceptance': 'PENDING_REVIEW',
        'v8Scope': 'Explicit item labels and actual place/pick/mount checks; preserve all source model bytes and intrinsic transforms.',
        'originalAssetsModified': False}
    output = ROOT / 'reports/THIN_WINDOW_VARIANT_REVIEW_DIAGNOSIS.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': report['status'], 'sourceForms': len(rows), 'v7WindowTests': len(cases)}))

if __name__ == '__main__':
    main()
