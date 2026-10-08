"""Refresh only owned compact RP summaries from an existing optional-client proof.

No Minecraft, world parsing, ZIP decoding or production JAR hashing. Raw actual
runtime files and historical Attempt reports remain untouched.
"""
from __future__ import annotations
import argparse
import copy
import json
from pathlib import Path
from package_first_set import ROOT, digest, read, require, resolve, json_bytes


def ref(path):
    path=resolve(path,ROOT)
    return {'path':str(path),'bytes':path.stat().st_size,'sha256':digest(path)}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--proof',type=Path,required=True)
    args=parser.parse_args();path=resolve(args.proof,ROOT);proof=read(path)
    require(proof['status']=='PASS_LINKED_CURRENT_CLIENT_AND_INTEGRATED_SERVER_EXPORT_BYTES'
        and proof['profile']=='full_client','Expected existing actual full-client diagnostic proof')
    rp=proof['actual_loaded_rp_probe']
    require(rp['status']=='PASS_ACTUAL_LOADED_RP_MODEL_TRACKER_POSES','Actual loaded RP client probe did not PASS')
    full={'status':'PASS_CURRENT_FULL_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT_AND_LINKED_DIAGNOSTIC_EXPORT_BYTES',
        'wrapper':proof['wrapper'],'diagnosticExportsProof':ref(path),'session':proof['session'],
        'heapArguments':proof['heap_arguments'],'actualRootModJars':proof['root_mod_jars'],
        'selectedProfileDependencyJars':proof['root_mod_jars']-2,
        'loadedRpObjectCount':len(rp['loadedObjects']),
        'loadedRpAssets':[row['assetId'] for row in rp['loadedObjects']],
        'loadedRpProbeStatus':rp['status'],'copiedSettings':proof['copied_shader_settings'],
        'irisStrictOptionsCounterGate':proof['iris_full_options_and_counter_gate'],
        'optionalIrisObservation':proof['optional_iris_observation'],
        'newRpItemPlacementAcknowledgement':'NOT_PROBED',
        'fullProfile4GiBCompatibility':'NOT_PROVEN','manualVisualGameplay':'PENDING_USER_REVIEW'}
    timing={'proof':ref(path),'scope':'Sequential matched presentation windows; elapsed wall time includes flip/wait/scheduling. Actual render-thread CPU intervals are separate. No GPU duration or exact causal/object FPS attribution.',
        'windows':proof['timings'],
        'observedSlowOnWindow':True,
        'causeAttribution':'NOT_MEASURED; retain raw samples and investigate separately',
        'serverOwnHookCost':'Inclusive/nested sampled elapsed API intervals; not unique CPU and not all RP instances'}
    ordinary_path=ROOT/'reports/RP_V9_ORDINARY_RUNTIME_RELEASE8.json';ordinary=read(ordinary_path)
    require(ordinary['productionJarSha256']==proof['production_jar_sha256'],'Ordinary RP proof has another artifact')
    ordinary['currentFullClient']=full
    ordinary['actualClientRpModelChain']='PASS_LOADED_RP_TRACKER_MODEL_POSES_MINIMAL9_REENTER9_FULL9; RP_NEW_PLACEMENT_CHAIN_NOT_PROBED'
    ordinary_path.write_bytes(json_bytes(ordinary));ordinary_ref=ref(ordinary_path)
    names=['SOURCE_WOODGATE_V9_COMPARISON','RP_LITHIUM_COLLISION_V9_DIAGNOSIS','RP_V9_RUNTIME_OUTCOMES','RP_DIAGNOSTICS_V9_IMPLEMENTATION']
    for name in names:
        target=ROOT/'reports'/(name+'.json');document=read(target);document['currentFullClient']=copy.deepcopy(full)
        if name=='SOURCE_WOODGATE_V9_COMPARISON':
            document['ordinaryLocalFixEvidence']['proof']=ordinary_ref
        elif name=='RP_LITHIUM_COLLISION_V9_DIAGNOSIS':
            document['correctedOrdinaryEvidence']=ordinary_ref
        elif name=='RP_V9_RUNTIME_OUTCOMES':
            document['ordinaryRuntime']['proof']=ordinary_ref
            document['originalForgeGate']=ref(ROOT/'reports/SOURCE_WOODGATE_V9_COMPARISON.json')
        else:
            document['correctedFullLithiumMovement']['ordinaryEvidence']=ordinary_ref
            document['actualOwnCostObservations']['additionalFullClient']=timing
            document['newReleaseHardening']['optionalFullClientDiagnosticZips']={
                'status':'PASS_ACTUAL_LINKED_ZIP_BYTES_CHECKED_AND_PRIMARY_COLLECTION_ENABLED',
                'proof':ref(path),'collection':'package_review_v9 --full-client-report automatically retains both actual export ZIPs; required independent default60/reentry gate is unchanged',
                'validatorSyntheticTests':10,'scope':'No shader options/counter, whole catalog or manual acceptance upgrade'}
        target.write_bytes(json_bytes(document))
    print(json.dumps({'status':'PASS_OWNED_RP_FULL_CLIENT_SUMMARIES_REFRESHED','proof':str(path),'reports':names}))


if __name__=='__main__':main()
