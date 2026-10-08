"""Preserve completed ordinary-type observations from an incomplete client run.

Refuses to turn a partial/failed wrapper into a complete runtime/package PASS.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from package_review_v10 import read, digest, require, expected_types, exact_artifact, verified_raw_output, gate_rp_overlap


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--client-report',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    require(not a.output.exists(),'Refuse to overwrite evidence')
    sha=digest(a.jar);wrapper=read(a.client_report);exact_artifact(wrapper,sha,'partial client wrapper')
    require(wrapper.get('status')=='FAIL_CLIENT_REVIEW' and wrapper.get('exit_code')==0 and wrapper.get('integrated_save_messages_present') is True and not wrapper.get('termination'),'This collector requires an actually incomplete review with normal process exit/save')
    actual,raw=verified_raw_output(wrapper,'client_review_output');exact_artifact(actual,sha,'partial client actual loaded origin')
    require(actual.get('status')=='FAIL_ACTUAL_CLIENT_V10' and actual.get('mode')=='AUTHOR' and actual.get('failedStep'),'Failed/incomplete whole-client status must remain explicit')
    architecture,rp_types=expected_types(a.jar);arch=actual.get('ordinaryArchitectureCases',[]);rp=[row for row in actual.get('ordinaryRpCases',[]) if row.get('part')=='authored-first-part']
    require(len(arch)==18 and {row.get('registry') for row in arch}==set(architecture) and all(row.get('status')=='PASS_ACTUAL_ORDINARY_ARCHITECTURE_ITEM_AND_CREATIVE_ROOT_OR_HELPER_ATTACK' and row.get('uuid') for row in arch),'All18 ordinary architecture cases are not complete')
    require(len(rp)==69 and actual.get('expectedCanonicalRpCases')==69 and {row.get('asset') for row in rp}==rp_types,'All69 canonical artistic RP types must match actual JAR catalogue')
    flags=['nativeBlockItemPlaced','nativePlacementSameServerClientUuid','nativeBlockSurvivesRpAttack','nativeBlockOrdinaryCleanup']
    for row in rp:
        require(row.get('status')=='PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK' and row.get('uuid') and row.get('serverRegistry')=='bloodborne_rp:'+row['asset'],'Canonical ordinary placement/attack observation incomplete')
        require(all(row.get(k) is True for k in flags) and row.get('nativeBlockRegistry')=='minecraft:stone' and len(row.get('nativeBlockCell',[]))==3 and row.get('nativeBlockRelation') in {'INTERSECTS_SOURCE_SELECTION','ADJACENT_ROOT'},'Canonical native-placement/removal/cleanup observation incomplete')
        require(isinstance(row.get('nativeStableRpBefore'),dict) and row['nativeStableRpBefore'] and row['nativeStableRpBefore']==row.get('nativeStableRpAfter'),'Actual stable sameUUID RP snapshot changed')
        camera=row.get('actualSourcePartCameraCandidate',{});require(camera.get('sourceHitUuid')==row['uuid'] and camera.get('nativeActorCollisionFree') is True and camera.get('sourcePhysicalActorCollisionFree') is True and camera.get('requestedDistance') in {2,3,4} and camera['requestedDistance']<=camera.get('ordinaryReach',0)+1e-6,'Collision-free actual ordinary-reach source UUID camera proof missing')
    gate_rp_overlap(actual)
    report={'schema':'dw-v10-completed-ordinary-types-partial-client-evidence-v1','status':'PASS_LIMITED_69_RP_18_ARCH_ORDINARY_CASES_WHOLE_CLIENT_FAILED',
            'productionJarSha256':sha,'wholeClientStatus':wrapper['status'],'rawWholeClientStatus':actual['status'],'failedStep':actual['failedStep'],'failedPhase':actual.get('phase'),
            'normalProcessExit':True,'wholeScenarioPackageEligible':False,
            'inputs':{name:{'path':str(path.resolve()),'bytes':path.stat().st_size,'sha256':digest(path)} for name,path in [('jar',a.jar),('clientReport',a.client_report),('actualRawClientOutput',raw)]},
            'actualLoadedProductionOrigins':actual.get('productionArtifactOrigins',[]),
            'completedArchitectureCaseCount':len(arch),'completedCanonicalRpCaseCount':len(rp),
            'actualNativeRelations':{kind:sum(row['nativeBlockRelation']==kind for row in rp) for kind in ['INTERSECTS_SOURCE_SELECTION','ADJACENT_ROOT']},
            'completedRpTypes':[{'asset':row['asset'],'uuid':row['uuid'],'serverRegistry':row['serverRegistry'],'nativeBlockRelation':row['nativeBlockRelation'],'actualCamera':row['actualSourcePartCameraCandidate']} for row in rp],
            'completedArchitectureTypes':[{'registry':row['registry'],'uuid':row['uuid']} for row in arch],
            'actualRpOnRpTwoUuidOverlapStatus':actual['ordinaryRpOverlap']['status'],
            'limits':['Not a complete AUTHOR/REENTER/full-client runtime PASS.', 'Four additional purpose-specific opened/moving/platform/remote-part scenarios are not proved by the canonical69 population.',
                      'Actual237 menu sequence/actor restoration/persisted snapshots and final linked diagnostics were not reached by this run.',
                      'Manual visual acceptance, all-source city catalogue/migration/finalIDs/GPU/causalFPS remain unproved.',
                      'Native relation explicitly distinguishes selection intersection from adjacent root; no guarantee all69 are pixel-mesh intersections.']}
    a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf8');print(json.dumps({'status':report['status'],'rp':len(rp),'architecture':len(arch),'wholeStatus':wrapper['status'],'failedStep':actual['failedStep'],'report':str(a.output.resolve()),'sha256':digest(a.output)}))


if __name__=='__main__':main()
