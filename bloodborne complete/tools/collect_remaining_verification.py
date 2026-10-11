"""Bind real independent/build/GameTest/client evidence to the shipped JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / 'docs/remaining-transfer'

def read(path):
    return json.loads(path.read_text(encoding='utf-8'))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--run-name', required=True)
    parser.add_argument('--client-report', type=Path)
    parser.add_argument('--native-report', type=Path)
    parser.add_argument('--network-report', type=Path)
    parser.add_argument('--world-report', type=Path)
    args = parser.parse_args()
    jar = ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.1-resource-transfer.jar'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    proof = DOC / 'evidence'
    proof.mkdir(exist_ok=True)
    build = ROOT / 'build/remaining-transfer' / args.run_name
    assert read(build / 'result.json')['status'] == 'PASS'
    xml = ROOT / 'build/test-results/gametest' / ('TEST-' + args.run_name + '.xml')
    tests = ET.parse(xml).getroot().findall('.//testcase')
    assert len(tests) >= 63 and not any(case.find('failure') is not None or case.find('error') is not None for case in tests)
    for path in (build / 'result.json', build / 'gradle.log', xml):
        shutil.copyfile(path, proof / (args.run_name + '-' + path.name))
    data = {'schema': 'dreamwalker-source-object-evidence-v1',
            'artifact': {'path': str(jar), 'sha256': digest},
            'scope': {'dependency': 'Exact source geometry/UV/texture dependency closure and combined-JAR bytes.',
                      'registration': 'All actual new registry types, unique numbers, Russian names and offered variant items.',
                      'gametest': 'Actual Minecraft GameTest server: registered item placement, art pick/copy, UUID, typed NBT, neighbours, break, openable callbacks.',
                      'client_baked': 'Every new part is baked with non-missing visible sprites; builtin creative icons are registered.',
                      'client_network': 'Only sampled actual client interactionManager packets and vanilla item pick, acknowledged by the dedicated server; physical mouse/keyboard input is separate.',
                      'saved_world': 'Only sampled installed owners/typed payload/helpers verified after a real server save and reopen.',
                      'native_manual': 'Only explicitly documented actual client input and inspected appearance, never inferred from a build or camera.'}}
    def add(phase, evidence, status='PASS'):
        data[phase] = {'*': {'*': {'status': status, 'artifactSha256': digest, 'evidence': str(evidence.relative_to(ROOT))}}}
    artifact = read(DOC / 'artifact-validation.json')
    assert artifact['status'] == 'PASS' and artifact['productionJarSha256'] == digest
    preservation = read(DOC / 'preservation-results.json')
    assert preservation['status'] == 'PASS' and preservation['jarSha256'] == digest
    add('dependency', DOC / 'artifact-validation.json')
    for phase in ('registration', 'gametest', 'server'):
        add(phase, proof / (args.run_name + '-' + xml.name))
    if args.client_report:
        client = read(args.client_report)
        assert client['status'] == 'PASS' and client['productionJarSha256'] == digest
        target = proof / 'client-baked.json'
        shutil.copyfile(args.client_report, target)
        for phase in ('client_baked', 'client'):
            add(phase, target)
    if args.native_report:
        native = read(args.native_report)
        assert native['productionJarSha256'] == digest
        data['native_manual'] = {}
        for case in native['cases']:
            verdict = case.get('nativeManualStatus', 'NOT_RUN')
            assert verdict in ('PASS', 'FAIL', 'NOT_RUN')
            if verdict != 'NOT_RUN':
                data['native_manual'].setdefault(case['registryId'], {})[str(case['variant'])] = {
                    'status': verdict, 'artifactSha256': digest,
                    'evidence': str(args.native_report.resolve().relative_to(ROOT))}
    if args.network_report:
        network = read(args.network_report)
        assert network['status'] == 'PASS' and network['productionJarSha256'] == digest
        target = proof / 'client-network.json'
        shutil.copyfile(args.network_report, target)
        data['client_network'] = {}
        for case in network['cases']:
            assert case['status'] == 'PASS'
            data['client_network'].setdefault(case['id'], {})[str(case['variant'])] = {
                'status': 'PASS', 'artifactSha256': digest, 'evidence': str(target.relative_to(ROOT))}
    if args.world_report:
        world = read(args.world_report)
        assert world['status'] == 'PASS' and world['artifact']['sha256'] == digest
        assert world.get('comparison', {}).get('typedSavedStateEqual') is True
        target = proof / 'saved-world-reopen.json'
        shutil.copyfile(args.world_report, target)
        data['saved_world'] = {}
        for case in world['plannedCases']:
            if case['savedStateStatus'] != 'PASS':
                continue
            data['saved_world'].setdefault(case['expectedRegistryId'], {})[str(case['expectedVariant'])] = {
                'status': 'PASS', 'artifactSha256': digest, 'evidence': str(target.relative_to(ROOT))}
    (DOC / 'object-verification.json').write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
