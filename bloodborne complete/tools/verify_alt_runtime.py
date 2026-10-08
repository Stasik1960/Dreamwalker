"""Verify ALT pack bytes/priority and actual saved-client model observations."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SPRITE = 'minecraft:block/red_wool'
MODEL0 = 'assets/bloodborne_dw/models/alt/prototype_ladder/0.json'
MODEL1 = 'assets/bloodborne_dw/models/alt/prototype_ladder/1.json'
PARENT04 = 'bloodborne_dw:base/source/minecraft/block/hold/wood_ladder_04'

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def require(condition, message):
    if not condition:
        raise ValueError(message)

def read(path):
    return json.loads(Path(path).read_text(encoding='utf8'))

def resolved(path):
    path = Path(path)
    return (path if path.is_absolute() else ROOT / path).resolve()

def client_evidence(path, artifact_sha):
    path = resolved(path)
    wrapper = read(path)
    require(wrapper['artifact_sha256'] == artifact_sha, 'Client wrapper has a different production JAR')
    require(wrapper['status'] == 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' and wrapper['exit_code'] == 0
            and wrapper.get('integrated_save_messages_present') is True and 'termination' not in wrapper,
            'Client world/save/normal exit is incomplete')
    run = resolved(wrapper['run_directory'])
    run.relative_to((ROOT / 'build').resolve())
    console = run / 'launch-console.log'
    require(sha(console) == wrapper['console_sha256'], 'Actual client console changed')
    out = wrapper['client_review_output']
    output_path = resolved(out['path'])
    require(sha(output_path) == out['sha256'], 'Actual client QA output changed')
    output = read(output_path)
    require(output == out['result'] and output['status'].startswith('PASS_')
            and output['normalStopRequested'] is True and output['guard'] == 'ISOLATED_SAVED_REVIEW_CLIENT_ONLY',
            'Client QA output is incomplete or differs from the wrapper')
    require(output['actualProductionOrigins'], 'Client did not identify an actual production origin')
    for origin in output['actualProductionOrigins']:
        require(origin['sha256'] == artifact_sha and sha(origin['path']) == artifact_sha,
                'Actual loaded production copy differs')
    marker = resolved(wrapper['qa_input']['source'])
    require(sha(marker) == wrapper['qa_input']['sha256'] == output['markerSha256']
            and read(marker)['productionJarSha256'] == artifact_sha and not read(marker).get('diagnosticOnly'),
            'Client marker was changed or targets a different JAR')
    return wrapper, output, {'wrapper': str(path), 'wrapper_sha256': sha(path),
                            'actual_output': str(output_path), 'actual_output_sha256': sha(output_path),
                            'console': str(console), 'console_sha256': sha(console),
                            'marker': str(marker), 'marker_sha256': sha(marker),
                            'status': wrapper['status'], 'exit_code': wrapper['exit_code']}

def ladder_rows(output):
    result = []
    for row in output['actualBakedModels']:
        model = row['model']
        if not model.startswith('ladder-state:Block{bloodborne_dw:prototype_ladder}['):
            continue
        properties = dict(field.split('=', 1) for field in model.split('[', 1)[1][:-1].split(','))
        require(not row['missingModel'] and row['quadCount'] > 0, 'A ladder baked model is empty/missing')
        result.append({**row, 'properties': properties})
    require(len(result) == 192 and len({row['model'] for row in result}) == 192,
            'Actual client must enumerate all192 ladder states exactly once')
    return result

def verify(args):
    artifact = resolved(args.jar)
    artifact_sha = sha(artifact)
    wrapper, output, ordered_evidence = client_evidence(args.client_report, artifact_sha)
    _, base_output, base_evidence = client_evidence(args.base_report, artifact_sha)
    expected_order = ['vanilla', 'fabric', 'file/' + args.pack.name]
    require(output['loadedResourcePacks'] == expected_order, 'ALT pack must have priority after fabric')
    require('file/' + args.pack.name not in base_output['loadedResourcePacks'], 'Baseline contains the ALT pack')
    run = resolved(wrapper['run_directory'])
    option_path = run / 'options.txt'
    option_line = next(line for line in option_path.read_text(encoding='utf8').splitlines()
                       if line.startswith('resourcePacks:'))
    require(json.loads(option_line.split(':', 1)[1]) == expected_order, 'Saved client options disagree with observed pack order')
    pack = resolved(args.pack)
    copied = run / 'resourcepacks' / pack.name
    pack_sha = sha(pack)
    require(sha(copied) == pack_sha, 'ALT ZIP copy differs from the source')
    declaration = next(row for row in wrapper['explicit_resource_packs'] if row['copied_name'] == pack.name)
    require(declaration['sha256'] == pack_sha and declaration['byte_copy'] == 'PASS'
            and declaration['source_unchanged_after_run'] is True, 'Launcher pack provenance is incomplete')
    with zipfile.ZipFile(pack) as archive:
        require(archive.testzip() is None and len(archive.namelist()) == len(set(archive.namelist())), 'ALT ZIP invalid')
        bytes0, bytes1 = archive.read(MODEL0), archive.read(MODEL1)
        model0, model1 = json.loads(bytes0), json.loads(bytes1)
        require(model0 == {'parent': 'bloodborne_dw:base/source/minecraft/block/hold/wood_ladder_02',
                           'textures': {'2': SPRITE}}, 'ALT variant0 model override differs')
        require(model1 == {'parent': PARENT04}, 'ALT variant1 must use the source04 model parent')
        png = 'assets/bloodborne_dw/textures/alt/prototype_ladder/test.png'
        require(png in archive.namelist(), 'Example PNG provenance absent')
        png_sha = hashlib.sha256(archive.read(png)).hexdigest()
        require(not any('alt/prototype_ladder/test' in archive.read(name).decode('utf8')
                        for name in archive.namelist() if name.endswith('.json')), 'Example PNG unexpectedly referenced')
    with zipfile.ZipFile(artifact) as archive:
        parent_path = 'assets/bloodborne_dw/models/' + PARENT04.split(':', 1)[1] + '.json'
        parent_bytes = archive.read(parent_path)
        parent = json.loads(parent_bytes)
        require(parent.get('elements') and parent.get('textures'), 'ALT variant1 parent has no authored geometry/texture')
        textures = []
        for value in set(parent['textures'].values()):
            if value.startswith('#'):
                continue
            namespace, local = value.split(':', 1)
            require(namespace == 'bloodborne_dw', 'Unexpected external parent texture closure')
            name = 'assets/' + namespace + '/textures/' + local + '.png'
            textures.append({'path': name, 'sha256': hashlib.sha256(archive.read(name)).hexdigest()})
        default_model1 = json.loads(archive.read(MODEL1))
    rows = ladder_rows(output)
    baseline = ladder_rows(base_output)
    alt0 = [row for row in rows if row['properties']['profile'] == 'alt' and row['properties']['variant'] == '0']
    require(len(alt0) == 32 and all(row['quadSpriteIds'] == [SPRITE] for row in alt0),
            'All32 actual ALTvariant0 ladder states must observe red_wool')
    require(not any(SPRITE in row['quadSpriteIds'] for row in rows if row['properties']['profile'] == 'base'),
            'ALT texture changed BASE observations')
    require(not any(SPRITE in row['quadSpriteIds'] for row in baseline), 'Baseline unexpectedly observes red_wool')
    alt1 = [row for row in rows if row['properties']['profile'] == 'alt' and row['properties']['variant'] == '1']
    vertex_comparisons = []
    vertex_available = any('quadVertexSha256' in row for row in rows)
    require(vertex_available or not args.require_vertex_comparison, 'Required actual baked vertex fingerprints are absent')
    if vertex_available:
        require(all(re.fullmatch(r'[0-9a-f]{64}', row.get('quadVertexSha256', '')) for row in rows),
                'Incomplete actual ladder vertex fingerprints')
        def pose_key(row):
            properties = row['properties']
            return tuple(properties[key] for key in ('facing', 'diagonal', 'source_clone', 'waterlogged'))
        source2 = {pose_key(row): row for row in rows if row['properties']['profile'] == 'base'
                   and row['properties']['variant'] == '2'}
        source1 = {pose_key(row): row for row in rows if row['properties']['profile'] == 'base'
                   and row['properties']['variant'] == '1'}
        require(len(alt1) == len(source2) == len(source1) == 32, 'Missing ALT/BASE paired poses')
        for actual in alt1:
            pose = pose_key(actual)
            target, original = source2[pose], source1[pose]
            equal = actual['quadVertexSha256'] == target['quadVertexSha256']
            different = actual['quadVertexSha256'] != original['quadVertexSha256']
            require(equal and different, 'ALTvariant1 actual baked vertices do not replace BASEvariant1 with BASEvariant2: ' + str(pose))
            vertex_comparisons.append({'pose': dict(zip(('facing', 'diagonal', 'source_clone', 'waterlogged'), pose)),
                'alt_variant1': actual['quadVertexSha256'], 'base_variant2': target['quadVertexSha256'],
                'base_variant1': original['quadVertexSha256'], 'alt_model': actual['model'],
                'base2_model': target['model'], 'base1_model': original['model'],
                'equals_base_variant2': equal, 'differs_from_base_variant1': different, 'status': 'PASS'})
    history = None
    if args.history_report and resolved(args.history_report).exists():
        _, historical, evidence = client_evidence(args.history_report, artifact_sha)
        history = {'evidence': evidence, 'loaded_packs': historical['loadedResourcePacks'],
                   'red_wool_ladder_states': sum(SPRITE in row['quadSpriteIds'] for row in ladder_rows(historical)),
                   'interpretation': 'World-load PASS only; fabric after the example pack shadowed its overrides. Not an ALT-application PASS.'}
    return {'schema': 'dreamwalker-first-set-alt-runtime-v2',
            'status': 'PASS_ACTUAL_ALT_PACK_TEXTURE_AND_MODEL_REPLACEMENT' if vertex_available else 'PASS_ACTUAL_ALT_PACK_AND_TEXTURE_REPLACEMENT',
            'artifact_sha256': artifact_sha, 'client_wrapper': str(resolved(args.client_report)),
            'ordered_client_evidence': ordered_evidence, 'baseline_evidence': base_evidence,
            'loaded_packs': expected_order, 'options_sha256': sha(option_path),
            'priority': 'Later packs override earlier packs; file/ALT-example.zip follows fabric.',
            'pack': {'source': str(pack), 'copy': str(copied), 'sha256': pack_sha, 'byte_copy': 'PASS',
                     'source_unchanged_after_run': True},
            'actual_red_wool_ladder_state_models': len(alt0), 'actual_base_red_wool_ladder_state_models': 0,
            'baseline_red_wool_ladder_state_models': 0, 'actual_alt_variant0_observations': alt0,
            'model_replacement': {'override_path': MODEL1, 'override_sha256': hashlib.sha256(bytes1).hexdigest(),
                                  'configured_parent': PARENT04, 'production_default_parent': default_model1['parent'],
                                  'parent_json_sha256': hashlib.sha256(parent_bytes).hexdigest(),
                                  'source_elements': len(parent['elements']), 'texture_closure': textures,
                                  'resource_configuration_and_closure': 'PASS',
                                  'actual_alt_variant1_nonmissing_state_models': len(alt1),
                                  'actual_complete_baked_vertex_identity': 'PASS_32_POSES_EQUAL_BASE2_DIFFER_BASE1' if vertex_available else 'NOT_PROBED',
                                  'vertex_hash_contract': 'SHA256 Direction.values() six cull buckets then general; face index/count, perquad declaredface/tint/shade/UTF8sprite/raw32bit vertexints; big-endian; seed0 perbucket; no model/state/class identity.',
                                  'actual_pose_comparisons': vertex_comparisons},
            'unreferenced_png': {'path': png, 'sha256': png_sha, 'referenced_by_example_models': False,
                                'applied_texture_claim': False},
            'historical_pack_order_run': history, 'manual_visual_acceptance': 'PENDING_USER_REVIEW',
            'manual_gameplay': 'NOT_RUN', 'limits': ['Observed baked quad sprite IDs prove red_wool replacement for32 states.',
                'ALTvariant1 actual baked vertex/sprite equality to BASEvariant2 is proven for32 paired poses.' if vertex_available
                else 'ALT variant1 parent04 is verified from the loaded higher-priority pack and production resource closure; full baked vertex identity is not probed.',
                'Automated resource observations do not replace user visual/gameplay acceptance.']}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--revision', default='V7')
    parser.add_argument('--jar', type=Path, default=ROOT / 'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.2.jar')
    parser.add_argument('--client-report', type=Path)
    parser.add_argument('--base-report', type=Path)
    parser.add_argument('--history-report', type=Path)
    parser.add_argument('--pack', type=Path, default=ROOT / 'build/prototype/ALT-example.zip')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--require-vertex-comparison', action='store_true', help='Reject clients without all192 actual quadVertexSha256 fingerprints')
    args = parser.parse_args()
    revision = args.revision.upper()
    require(re.fullmatch(r'V[1-9][0-9]*', revision), 'Invalid evidence revision')
    args.client_report = args.client_report or ROOT / ('reports/CLIENT_FIRST_SET_ALT_ORDERED_' + revision + '.json')
    args.base_report = args.base_report or ROOT / ('reports/CLIENT_FIRST_SET_MINIMAL_' + revision + '.json')
    args.history_report = args.history_report or ROOT / ('reports/CLIENT_FIRST_SET_ALT_' + revision + '.json')
    report = resolved(args.report or ROOT / ('reports/ALT_RUNTIME_' + revision + '.json'))
    report.relative_to((ROOT / 'reports').resolve())
    result = verify(args)
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'status': result['status'], 'artifact_sha256': result['artifact_sha256'],
                      'actual_red_wool_ladder_state_models': result['actual_red_wool_ladder_state_models'],
                      'report': str(report)}))

if __name__ == '__main__':
    main()
