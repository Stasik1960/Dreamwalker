"""Verify actual full-mod authoring NBT observations and prior failure scope."""
from pathlib import Path
import hashlib
import json

ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def ref(path):return path.resolve().relative_to(ROOT).as_posix()
def main():
    current_path=ROOT/'reports/FIRST_SET_FULL_AUTHOR_V8_RELEASE.json'
    previous_path=ROOT/'reports/FIRST_SET_FULL_AUTHOR_V8_FINAL.json'
    current=json.loads(current_path.read_text(encoding='utf8'));previous=json.loads(previous_path.read_text(encoding='utf8'))
    assert current['exit_code']==0 and current['status'].startswith('PASS')
    assert current['profile']==previous['profile']=='full_server'
    assert current['modset_profile_sha256']==previous['modset_profile_sha256']
    assert current['review_scene_input']['sha256']==previous['review_scene_input']['sha256']
    scene_path=Path(current['review_scene_output']['path']);scene=json.loads(scene_path.read_text(encoding='utf8'))
    assert sha(scene_path)==current['review_scene_output']['sha256']
    assert scene==current['review_scene_output']['result']
    assert scene['status']=='PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN' and len(scene['objects'])==91
    prior_scene=previous['review_scene_output']['result'];assert previous['status']==prior_scene['status']=='FAIL_SCENE_AUTHORING'
    error=prior_scene['error'];assert 'write_not_exact:Cell[x=22, y=64, z=18]'in error
    recovered=[row for row in scene['objects']if row['root']==[22,64,18]]
    assert len(recovered)==1 and recovered[0]['kind']=='prototype_thin_window' and recovered[0]['transaction']=='COMMITTED' and recovered[0]['mount']=='floor'
    probe=scene['nbtSerializationProbe'];assert probe['lithiumLoaded'] and probe['lithiumVersion']=='0.11.2'
    selected=[row for row in current['modset']if not row.get('extra_mod')]
    addons=[row for row in current['modset']if row.get('extra_mod')]
    assert len(selected)==83 and len(addons)==1 and addons[0]['id']=='bloodborne_dw_review'
    assert probe['worldMutated'] is False and probe['rawEqualityRequired'] is False
    assert len(probe['cases'])==3 and {row['name']for row in probe['cases']}=={'floor_removed_SourceShift','vertical_four_keys','nested_typed_arrays_ordered_list'}
    for row in probe['cases']:
        assert row['compoundSemanticEqual'] and row['canonicalBytesEqual'] and row['canonicalRoundTripTypedEqual']
        assert not row['rawBytesEqual'] and row['rawBeforeSha256']!=row['rawCopySha256']
        assert row['canonicalBeforeSha256']==row['canonicalCopySha256']
        assert row['beforeTypedNbt']==row['roundTripTypedNbt']
        assert 'Object2ObjectOpenHashMap' in row['beforeKeysClass'] and 'Object2ObjectOpenHashMap'in row['copyKeysClass']
    report={'schema':'dreamwalker-actual-full-mod-canonical-nbt-evidence-v1',
        'status':'PASS_ACTUAL_LITHIUM_TYPED_EQUAL_RAW_REORDER_CANONICAL_EXACT_AND_FRESH_SCENE',
        'currentArtifactSha256':current['artifact_sha256'],'previousCandidateSha256':previous['artifact_sha256'],
        'evidence':{'currentServerReport':ref(current_path),'currentServerReportSha256':sha(current_path),
            'currentSceneOutput':ref(scene_path),'currentSceneOutputSha256':sha(scene_path),
            'previousServerReport':ref(previous_path),'previousServerReportSha256':sha(previous_path),
            'previousSceneOutputSha256':previous['review_scene_output']['sha256'],
            'sameSceneInputSha256':current['review_scene_input']['sha256'],
            'sameSelectedModsetProfileSha256':current['modset_profile_sha256']},
        'actualSelectedMods':len(selected),'optionalQaAddons':len(addons),'reportedModsetRowsIncludingQa':len(current['modset']),
        'currentServerExitCode':current['exit_code'],
        'actualRuntimeNbtProbe':probe,'rawDifferentSemanticEqualCases':3,'canonicalExactTypedRoundTripCases':3,
        'priorFailureScope':{'status':previous['status'],'committedPrefixObjects':len(prior_scene['objects']),
            'firstRejectedRoot':[22,64,18],'type':'bloodborne_dw:prototype_thin_window','mount':'floor','error':error,
            'beforeNeighborPublish':True,'priorActualExpectedVsReadPayloadDumpAvailable':False},
        'currentRecovery':{'wholeFreshSceneObjects':len(scene['objects']),'samePreviouslyRejectedRoot':recovered[0],
            'authoringPassed':True,'productionReopen':'SEPARATE_GATE_PENDING','manualVisualAcceptance':'NOT_RUN'},
        'interpretation':'Actual full-mod NBT observations establish reordered encoded bytes despite equal typed compounds. Recursive canonical serialization preserves tag types/list order/arrays and removes this false encoded-snapshot rejection in the same selected-mod profile/scene. Prior failed root did not record its exact expected/read payload dump; source path, actual probe and recovered authoring corroborate the diagnosis rather than supplying that missing dump.',
        'simulationEvidenceSeparate':'reports/NBT_LITHIUM_MAP_COPY_JSHELL_SIMULATED.json',
        'limits':['Fresh scene authoring and normal exit only; production reopen and client/render acceptance are separate gates.','Existing third-party warnings remain in complete raw logs; this report only validates the supplied NBT probe and exact authoring failure/recovery scope.','No city/mass-conversion or user whole-set acceptance claim.']}
    output=ROOT/'reports/NBT_FULL_MOD_CANONICAL_RUNTIME_V8.json';output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    markdown=ROOT/'reports/NBT_FULL_MOD_CANONICAL_RUNTIME_V8.md'
    markdown.write_text(f'''# Actual full-mod NBT compatibility evidence

`{report['status']}`. Current artifact `{report['currentArtifactSha256']}`; actual selected-mod profile count{report['actualSelectedMods']}, Lithium0.11.2, normal server exit0 and91 authored objects.

All three actual runtime cases have semantic typed equality, different raw byte ordering, equal canonical bytes and exact typed round trips. Cases cover floor payload after SourceShift removal, vertical four-key payload, and nested byte/int/float/double values, primitive arrays and ordered list. Key class is actual fastutil Object2ObjectOpenHashMap. Raw/canonical SHA values and key iteration orders are retained inJSON.

The prior7f70 candidate rejected the first horizontal glass root `[22,64,18]` as `write_not_exact` after six objects. The current same-scene/same-selected-mod-profile run committed this root and all91 objects. The prior failed root did not dump its exact expected/read payload; this diagnostic limitation is retained. Recursive canonical encoding addresses the demonstrated byte-order instability without discarding typed payload checks.

Production reopen and manual visual acceptance remain separate gates. Standalone JShell simulation is separate from these actual runtime observations. Complete source reports: `{ref(current_path)}`, `{ref(previous_path)}`.
''',encoding='utf8')
    print(json.dumps({'status':report['status'],'actualSelectedMods':report['actualSelectedMods'],'lithiumVersion':probe['lithiumVersion'],'cases':3,'sceneObjects':91,'exitCode':0}))
if __name__=='__main__':main()
