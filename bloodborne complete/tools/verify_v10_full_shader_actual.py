"""Small JSON-only strict Iris/getter binding alongside a passed ordinary client proof."""
import argparse
import hashlib
import json
from pathlib import Path


def read(p):
    return json.loads(p.read_text(encoding='utf-8'))


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def require(ok, reason):
    if not ok:
        raise RuntimeError(reason)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--wrapper', type=Path, required=True)
    ap.add_argument('--ordinary-independent-report', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    a = ap.parse_args()
    require(not a.output.exists(), 'Never overwrite prior runtime evidence')
    w, proof = read(a.wrapper), read(a.ordinary_independent_report)
    raw_path = Path(w['client_review_output']['path'])
    r = read(raw_path)
    require(w['status'] == 'PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT' and w['exit_code'] == 0 and
            w['integrated_save_messages_present'] is True, 'Actual full client normal save/exit required')
    require(r == w['client_review_output']['result'] and sha(raw_path) == w['client_review_output']['sha256'],
            'Actual wrapper/raw binding differs')
    require(proof['status'] == 'PASS_CURRENT_ACTUAL_CLIENT_ROSTER_PARTS_MIDDLE_KEY_DIAGNOSTICS_AND_NORMAL_EXIT_PENDING_USER_REVIEW' and
            proof['middlePickRequired'] is True and proof['productionJarSha256'] == w['artifact_sha256'] == r['productionJarSha256'],
            'Exact completed ordinary/middle/diagnostics proof required')
    marker_path = Path(w['qa_input']['source'])
    require(sha(marker_path) == w['qa_input']['sha256'], 'Authoritative strict42 marker changed')
    marker = read(marker_path)
    expected = marker['expectedShaderOptions']
    require(len(expected) == 42 and marker['expectedShaderPack'] == 'Kappa_v5.2.zip', 'Explicit expected pack/42-option marker required')
    iris = r['irisRuntime']
    final, initial = iris['final'], iris['initial']
    require(iris['status'] == final['status'] == 'PASS_ACTIVE_IRIS_RENDERING_PIPELINE_AND_REQUESTED_GETTERS' and
            final['currentPackName'] == marker['expectedShaderPack'] and final['packPresent'] is True and
            final['shadersEnabled'] is True and final['fallback'] is False and final['shaderMapPresent'] is True and
            final['pipelineClass'] == 'net.irisshaders.iris.pipeline.IrisRenderingPipeline' and
            final['frameCounterAdvanced'] is True and final['frameCounter'] > initial['frameCounter'],
            'Actual live Iris pipeline/pack/counter proof failed')
    options = final['effectiveOptions']
    require(len(options) == 42 and len({o['key'] for o in options}) == 42 and
            {o['key']: o['expected'] for o in options} == expected and
            all(o['matches'] is True and o['actual'] == o['expected'] for o in options), 'Actual42 getters differ from authoritative marker')
    profile = w['shader_profile']
    require(profile['shader_bytes_identical'] is True and profile['shader_sha256'] == profile['shader_copy_sha256'] and
            profile['original_inputs_modified'] is False and profile['existing_user_installation_modified'] is False,
            'Isolated shader source/copy provenance differs')
    out = {'schema': 'dw-v10-full-client-strict-shader-json-proof-v1',
        'status': 'PASS_CURRENT_ACTUAL_FULL_CLIENT_ACTIVE_IRIS_ALL42_GETTERS_AND_BOUND_ORDINARY_DIAGNOSTICS_PROOFS',
        'productionJarSha256': w['artifact_sha256'], 'wrapper': str(a.wrapper.resolve()), 'wrapperSha256': sha(a.wrapper),
        'raw': str(raw_path), 'rawSha256': sha(raw_path),
        'ordinaryIndependentReport': str(a.ordinary_independent_report.resolve()), 'ordinaryIndependentSha256': sha(a.ordinary_independent_report),
        'strictMarker': str(marker_path), 'strictMarkerSha256': sha(marker_path),
        'pack': final['currentPackName'], 'pipelineClass': final['pipelineClass'], 'fallback': False,
        'initialFrameCounter': initial['frameCounter'], 'finalFrameCounter': final['frameCounter'],
        'actualEffectiveOptionCount': 42, 'actualEffectiveOptions': options,
        'runtimeShaderProvenance': profile,
        'ordinaryCounts': {'canonicalRp': proof['actualCanonicalRoster']['rpCount'],
            'architecture': proof['actualCanonicalRoster']['architectureCount'],
            'canonicalMiddle': proof['actualMiddleKey']['canonicalCount'], 'oldRegistryMiddle': proof['actualMiddleKey']['oldRegistryCount'],
            'menusSteps': proof['menusActualSteps']},
        'diagnosticsBound': {'sessionId': proof['diagnostics']['sessionId'], 'instanceId': proof['diagnostics']['instanceId'],
            'deliveredBatches': proof['diagnostics']['deliveredBatches'], 'status': proof['diagnostics']['status']},
        'limits': ['GPU timing and exact per-block FPS impact are NOT_MEASURED.',
            'This JSON-only audit uses original runtime bindings; no new JAR/world/shader scan or launch.',
            'ATT2 menu-open failure was not reproduced; QA27 success does not establish its cause or a production fix.',
            'Human visual review/full original task acceptance remain PENDING.']}
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(out, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': out['status'], 'output': str(a.output)}))


if __name__ == '__main__':
    main()
