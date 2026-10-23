"""Bind completed pilot evidence and root's actual image/movement review.

This authorizes the existing plan's full-city stage, never final map readiness.
"""
import argparse, hashlib, json
from pathlib import Path
from run_profiles import ROOT, sha, write

R=ROOT/'research'
CATALOG='638d84a879a7823fd4ed866b2109fbe864cda4690383745fbc5a53bc940fa550'
RUNTIME='99f450f814d187cf089a7fe523a5db5928cfafcb273c4ac1c4479f2df50bbde3'
def read(path):return json.loads(Path(path).read_text(encoding='utf-8'))
def bound(path):return {'path':str(path.resolve()),'sha256':sha(path)}
def main():
    p=argparse.ArgumentParser();p.add_argument('--visual-review',type=Path,required=True)
    p.add_argument('--out',type=Path,default=R/'pilot-gate.json');a=p.parse_args()
    assert not a.out.exists(),'Preserve accepted evidence; use a fresh output'
    comparison=read(R/'pilot/comparison.json');candidate=comparison['candidate']
    assert comparison['status'] in ('ONE_PAIR_ANALYZED_NOT_READINESS','BASELINE_INCOMPLETE_CANDIDATE_ANALYZED_NOT_READINESS')
    assert candidate['binding']['productionSha256']==RUNTIME
    receipt=read(R/'pilot/performance-candidate/receipt.json')
    assert receipt['status']=='PASS' and receipt['clientExitCode']==receipt['serverExitCode']==0
    marker=read(R/'pilot/candidate/NATIVE-GRID-MIGRATION.json')
    assert marker['status']=='READY_FOR_GAMEPLAY_QA' and marker['runtime']['sha256']==RUNTIME and marker['catalog']['sha256']==CATALOG
    compatibility=read(R/'function-qa/candidate-terminal-bridge/runtime-compatibility-accepted.json')
    assert compatibility['status']=='AFFECTED_QA_PASSED_RUNTIME_REBOUND_NOT_FINAL_READY'
    for name in ('runtimePatch','affectedFunctions','launchReceipt','previousMarker'):
        proof=compatibility['evidence'][name];assert sha(Path(proof['path']))==proof['sha256']
    qa=read(Path(compatibility['evidence']['affectedFunctions']['path']))
    assert qa['status']=='PASS' and qa['standRestored'] and qa['normalServerThreadFinished']
    assert all(action['status']=='PASS' for action in qa['actions'])
    launch=read(Path(compatibility['evidence']['launchReceipt']['path']));assert launch['status']=='PASS'
    light=read(R/'native-grid-plan/context-extended-relight-engine.json')
    assert light['status']=='PASS' and light['catalogSha256']==CATALOG and light['processed']==light['queued']==554 and light['pending']==0 and light['saveFlushed']
    assert compatibility['evidence']['resourcesAndOpticsUnchanged'] and compatibility['evidence']['nativeLightReusedWithoutRecalculation']
    review=read(a.visual_review)
    assert review['status']=='PASS_ACTUAL_RENDERED_VIEWS' and review['runtimeSha256']==RUNTIME and review['catalogSha256']==CATALOG
    assert review['comparisonSha256']==sha(R/'pilot/comparison.json')
    for flag in ('noNewVisibleDefects','movementUsable','noFrequentMultiSecondStalls','optimizationSupportedWithinReportedLimits'):
        assert review.get(flag) is True,flag+' remains unresolved'
    route=read(R/'pilot/route.json');assert [v['name'] for v in review['views']]==route['names']
    for view in review['views']:
        image=Path(view['path']).resolve()
        expected=(R/'profiles/candidate/client/screenshots'/('route-'+view['name']+'.png')).resolve()
        assert image==expected and sha(image)==view['sha256'] and view['observations']
    poses=read(R/'preserved-assembly-plan-preview.json');components=read(R/'preserved-assembly-component-preview.json')
    assert poses['status']=='FINITE_PREVIEW_PASS' and not poses['pilotIntersections']
    assert components['status']=='FINITE_COMPONENT_PREVIEW_PASS' and not components['pilotIntersections']
    evidence={name:bound(R/path) for name,path in {
        'comparison':'pilot/comparison.json','candidateLaunch':'pilot/performance-candidate/receipt.json',
        'baselineLaunch':'pilot/performance-baseline/receipt.json','functions':'function-qa/candidate-terminal-bridge/functions.json',
        'runtimeCompatibility':'function-qa/candidate-terminal-bridge/runtime-compatibility-accepted.json',
        'nativeLight':'native-grid-plan/context-extended-relight-engine.json','profile':'profiles/profile-lock.json',
        'recorder':'profiles/benchmark-helper.json','preservedAssemblies':'preserved-assemblies.json',
        'posePreview':'preserved-assembly-plan-preview.json','componentPreview':'preserved-assembly-component-preview.json'}.items()}
    result={'schemaVersion':1,'status':'PASS','kind':'ACTUAL_DENSE_PILOT_ACCEPTANCE_NOT_FINAL_CITY_READY',
        'denseBrickPilot':True,'catalogSha256':CATALOG,'runtimeSha256':RUNTIME,'evidence':evidence,
        'visualReview':bound(a.visual_review),'preservedOverridesRequiredBeforeFullCensus':True,
        'baselineCompleted':comparison['status']=='ONE_PAIR_ANALYZED_NOT_READINESS',
        'pairedSpeedupPercent':comparison['measuredDurationChangePercent'],
        'limitations':comparison['limitations'],'finalCityReady':False}
    write(a.out,result);print(json.dumps({'status':result['status'],'output':str(a.out),'sha256':sha(a.out)}))
if __name__=='__main__':main()
