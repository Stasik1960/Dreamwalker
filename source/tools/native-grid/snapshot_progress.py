"""Small durable root cursor; underlying stage journals remain authoritative."""
import argparse,datetime,hashlib,json
from pathlib import Path
P=Path(__file__).resolve().parents[2];R=P.parent/'research'
def read(relative):
    path=R/relative
    return json.loads(path.read_text(encoding='utf-8-sig')) if path.is_file() else None
parser=argparse.ArgumentParser();parser.add_argument('--phase',default='dense_pilot_authoring');args=parser.parse_args()
doc={'ready':False,'updatedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'phase':'dense_pilot_authoring','originalPairUntouched':True,'source':str(P),'sourceWorld':r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018\deliverables\block-fixes-2-20261020\map\bloodborne-blocks-fixed-2','contract':str(R/'CONTRACT-V1.txt'),'mapWritesStarted':False,'assets':read('geometry-final/launch-receipt.json'),'fragments':read('fragments.geometry.receipt.json') or read('fragments.receipt.json'),'plan':read('native-grid-plan/progress.json'),'build':read('build/latest.json'),'physicsRetry':read('pilot-native-physics-clean/launch-receipt.json'),'pinnedProfile':str(R/'profiles/profile-lock.json'),'openIssues':['Complete native substrate/helper overhang coverage and actual base physics','Compile and apply dense pilot only after migration gate','Native relight with zero remaining debt','Pilot functional checks plus one matched separate-server pair','Promote same disk plan and apply entire city','Final normal-client movement route/save/reopen, then packaging and hashes'],'recovery':'Reuse research/native-grid-plan and append-only catalog-state.sqlite. Do not restart source conversion. Do not open candidate while NATIVE-GRID-INCOMPLETE.json exists. Original blocks.2 pair stays checkpoint.'}
if doc['plan']:
    doc['mapWritesStarted']=bool(doc['plan'].get('meta',{}).get('applyStarted',False))
doc['phase']=args.phase
doc['mapWritesStarted']=doc['mapWritesStarted'] or (R/'pilot/candidate/NATIVE-GRID-INCOMPLETE.json').is_file() or (R/'pilot/candidate/NATIVE-GRID-MIGRATION.json').is_file()
doc['supplementalAssets']=read('geometry-supplement/launch-receipt.json')
doc['functionalSelection']=read('function-qa/selection-receipt.json')
doc['baselineFunctions']=read('function-qa/baseline/launch-receipt.json')
doc['candidateFunctions']=read('function-qa/candidate/launch-receipt.json')
doc['candidateFunctionContinuations']={name:read('function-qa/'+name+'/launch-receipt.json') for name in ('candidate-continuation','candidate-door-loaded','candidate-actual-footprint')}
doc['preservedAssemblies']=str(R/'preserved-assemblies.json') if (R/'preserved-assemblies.json').is_file() else None
doc['baselinePilot']=read('pilot/performance-baseline/receipt.json')
doc['candidatePilot']=read('pilot/performance-candidate/receipt.json')
doc['bridgeCarrierAudit']=read('bridge-carrier-audit-context/launch-receipt.json') or read('bridge-carrier-audit-corrected/launch-receipt.json') or read('bridge-carrier-audit/launch-receipt.json')
doc['failedBaselineEvidence']=read('function-qa/baseline/launch-receipt.json')
doc['correctedBootstrapFunctions']=read('function-qa/baseline-bootstrap/launch-receipt.json')
doc['pilotBootstrapRepair']=read('pilot/bootstrap-repair/receipt.json')
doc['failedIntrinsicReference']=read('function-qa/baseline-reference/launch-receipt.json')
doc['nativeRelight']=read('native-grid-plan/relight-engine.json')
light_binding=read('native-grid-plan/relight-binding.json')
if light_binding and light_binding.get('engineReceipt'):
    receipt_path=Path(light_binding['engineReceipt'])
    if receipt_path.is_relative_to(R) and receipt_path.is_file():doc['nativeRelight']=read(receipt_path)
doc['nativeRelightLaunch']=read('native-grid-plan/relight-launch-receipt.json')
doc['storageFixProof']=read('bridge-carrier-audit-storage/storage-entry-proof.json')
doc['storageFixNativeGate']=read('bridge-carrier-audit-storage/launch-receipt.json')
extension=read('native-grid-plan/context-extension/inventory.json')
if extension:
    doc['contextExtension']={key:extension[key] for key in ('status','kind','target','eligibleOwners','specificBoundaryRetentions','parentCatalog','parentRuntime','nativeSavedLight','oneGlobalDatabase','newMapCopies','mapReads','mapWrites')}
    doc['contextExtension']['inventory']=str(R/'native-grid-plan/context-extension/inventory.json')
    doc['contextExtension']['stageIntent']=read('native-grid-plan/context-extension/stage-intent.json')
    doc['contextExtension']['blockedCompose']=read('native-grid-plan/context-extension/blocked-compose-stop.json')
    doc['contextExtension']['initial37Export']=read('geometry-phase2-initial/launch-receipt.json')
    doc['contextExtension']['initial37Merge']=read('geometry-phase2-initial/merge-receipt.json')
    doc['contextExtension']['carrier19Physics']=read('geometry-phase2-carrier-physics/launch-receipt.json')
    doc['contextExtension']['carrier19Merge']=read('geometry-phase2-carrier-physics/merge-receipt.json')
    final_gate=R/'native-functional-bases/functional-carrier-extension-frozen.json'
    doc['contextExtension']['nativeFunctionGate']=str(final_gate if final_gate.is_file() else R/'native-functional-bases/gate.json')
    doc['contextExtension']['integrationSourceProof']=read('native-grid-plan/context-extension/integration-source-proof.json')
    doc['contextExtension']['compositions']=read('processes/20261022T021511-migrate.json')
    doc['contextExtension']['finalStateCoverage']=read('native-grid-plan/context-extension/final-state-coverage.json')
    doc['contextExtension']['activationFunctionGate']=str(R/'native-grid-plan/context-extension/activation-supplement/function-gate.json')
    doc['contextExtension']['activationPhysics']=read('geometry-phase2-activation-physics/launch-receipt.json')
    doc['contextExtension']['activationVisual']=read('geometry-phase2-activation-visual/launch-receipt.json')
    doc['contextExtension']['frozenCatalog']=read('native-grid-plan/context-extension/frozen-catalog.json')
    doc['contextExtension']['bindFailure']=read('processes/20261022T030749-migrate.json')
    doc['contextExtension']['postRepairBind']=read('processes/20261022T043105-migrate.json')
    doc['contextExtension']['completedBindRecovery']=read('native-grid-plan/context-extension/completed-bind-recovery/receipt.json')
    doc['contextExtension']['binding']=read('native-grid-plan/context-extension/in-place-binding.json')
    doc['contextExtension']['sourceResourceAudit']=read('native-grid-plan/context-extension/source-resource-audit.json')
    doc['contextExtension']['boundaryDependencies']=read('native-grid-plan/context-extension/boundary-dependency-inventory.json')
    doc['contextExtension']['boundaryDependenciesV2']=read('native-grid-plan/context-extension/boundary-dependency-inventory-v2.json')
    final_dependency_path=R/'native-grid-plan/context-extension/boundary-dependency-inventory-v3.json'
    if final_dependency_path.is_file():doc['contextExtension']['finalBoundaryDependencies']={'path':str(final_dependency_path),'sha256':hashlib.sha256(final_dependency_path.read_bytes()).hexdigest()}
    native_dependency_path=R/'native-functional-bases/original427-native-dependency-inventory-v3.json'
    if not native_dependency_path.is_file():native_dependency_path=R/'native-functional-bases/original427-native-dependency-inventory.json'
    native_dependencies=read(native_dependency_path)
    if native_dependencies:
        doc['contextExtension']['sourceNativeDependencyCoverage']={'path':str(native_dependency_path),'sha256':hashlib.sha256(native_dependency_path.read_bytes()).hexdigest(),**{key:native_dependencies.get(key) for key in ('status','originalChunks','existingAuxChunks','baselineAllComponents','candidateStage1RemainingComponents','candidateAfterPhase2RemainingComponents','depsComplete','additionalChunksRequired','auxiliaryComponentsDiscovered')}}
    doc['contextExtension']['boundaryRepairPlan']=str(R/'pilot/dependency-repair/plan.json')
    doc['contextExtension']['boundaryFileCommit']=read('pilot/dependency-repair/file-commit-receipt.json')
    doc['contextExtension']['boundaryAdmission']=read('pilot/dependency-repair/admission-complete.json')
    doc['contextExtension']['boundaryRepairAudit']=read('pilot/dependency-repair/audit.json')
    doc['contextExtension']['boundaryRepairReceipt']=read('pilot/dependency-repair/receipt.json')
    doc['contextExtension']['boundaryAuditProgress']=read('pilot/dependency-repair/audit-progress.json')
    doc['contextExtension']['nativeComponentCoverage']=read('native-grid-plan/context-extension/native-component-coverage.json')
    doc['contextExtension']['shardSemanticDifference']=read('pilot/dependency-repair/shard-semantic-difference.json')
    doc['contextExtension']['atomicCommitFixtures']=read('pilot/dependency-commit-fixtures/receipt.json')
doc['catalog']=read('native-grid-plan/context-extension/catalog-stats.json') or read('catalog-stats.json')
doc['openIssues']=[
    'Bind all previously agreed whole assemblies to the existing plan and correct only affected pilot objects',
    'Pilot functional checks plus one matched separate-server pair at pinned heaps',
    'Promote same disk plan and apply entire city',
    'Final normal-client movement route/save/reopen, then packaging and hashes',
]
if not doc['nativeRelight'] or doc['nativeRelight'].get('status')!='PASS' or doc['nativeRelight'].get('pending')!=0:doc['openIssues'].insert(0,'Finish dense pilot apply and actual native relight with zero debt')
if not doc['bridgeCarrierAudit'] or doc['bridgeCarrierAudit'].get('status')!='PASS':doc['openIssues'].insert(0,'Confirm actual native bridge carrier audit on the exact production JAR')
if not doc['pilotBootstrapRepair']:doc['openIssues'].insert(0,'Correct matched pilot bootstrap metadata and preserve failed 4G baseline OOM evidence')
if doc['nativeRelightLaunch'] and doc['nativeRelightLaunch'].get('status')=='FAIL':doc['openIssues'].insert(0,'Finish actual4G native relight; preserve historical OOM and explicitly invalidated old light evidence')
temporary=R/'progress.json.writing';temporary.write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding='utf-8');temporary.replace(R/'progress.json')
