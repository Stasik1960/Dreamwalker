"""Freeze a V10 production candidate without replacing earlier proof or files."""
import argparse, hashlib, json, shutil, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--attempt', type=int, required=True)
    p.add_argument('--log', type=Path, required=True)
    p.add_argument('--xml', type=Path, required=True)
    a = p.parse_args()
    text = a.log.read_text(encoding='utf8', errors='replace')
    assert 'BUILD SUCCESSFUL' in text and 'PASS 20 transaction core checks' in text
    suite = ET.parse(a.xml).getroot()
    cases = suite.findall('.//testcase')
    assert cases and not suite.findall('.//failure') and not suite.findall('.//error')
    destination = ROOT / 'build/frozen-artifacts' / f'v10-attempt-{a.attempt}'
    assert not destination.exists(), 'Earlier candidate must never be replaced'
    name = 'dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.5.jar'
    artifact = ROOT / 'build/libs' / name
    with zipfile.ZipFile(artifact) as z:
        metadata = json.loads(z.read('fabric.mod.json'))
        assert metadata['version'] == '0.1.0-prototype.5'
        assert not any('/review/' in n or '/gametest/' in n for n in z.namelist())
        assert z.testzip() is None
    destination.mkdir(parents=True)
    shutil.copyfile(artifact, destination / name)
    assert sha(artifact) == sha(destination / name)
    inputs = []
    for folder in ('src/architecture', 'src/rp'):
        for file in sorted((ROOT / folder).rglob('*')):
            if file.is_file():
                inputs.append({'path': file.relative_to(ROOT).as_posix(),
                               'bytes': file.stat().st_size, 'sha256': sha(file)})
    proof_xml = ROOT / 'reports' / f'FIRST_SET_GAMETEST_V10_ATTEMPT_{a.attempt}.xml'
    assert not proof_xml.exists()
    shutil.copyfile(a.xml, proof_xml)
    result = {'schema': 'dreamwalker-v10-production-freeze-v1',
              'status': 'BUILD_NATIVE_PASS_REQUIRES_EXACT_ARTIFACT_ORDINARY_RUNTIME',
              'version': metadata['version'], 'artifact': str(destination / name),
              'sha256': sha(artifact), 'bytes': artifact.stat().st_size,
              'nativeTests': len(cases), 'nativeFailures': 0, 'coreChecks': 20,
              'nativeXml': str(proof_xml), 'nativeXmlSha256': sha(proof_xml),
              'buildLog': str(a.log.resolve()), 'buildLogSha256': sha(a.log),
              'mainSourceInputs': inputs, 'manualUserAcceptance': 'PENDING',
              'fullOriginalTask': 'NOT_READY_FULL_CATALOGUE_NUMERIC_IDS_CITY_CONVERSION'}
    (destination / 'manifest.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps({k: v for k, v in result.items() if k != 'mainSourceInputs'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
