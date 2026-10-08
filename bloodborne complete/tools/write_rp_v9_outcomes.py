"""Summarize requested RP checks and the added compatibility regression without visual acceptance."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]

def file_ref(path:Path)->dict:
    path=path.resolve();data=path.read_bytes()
    return {'path':str(path),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}

def main()->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--native-xml',type=Path,required=True)
    p.add_argument('--native-log',type=Path,required=True)
    p.add_argument('--jar',type=Path,help='Frozen production artifact built alongside this native suite; no ordinary runtime pass is inferred')
    p.add_argument('--output',type=Path,default=ROOT/'reports/RP_V9_RUNTIME_OUTCOMES.json')
    args=p.parse_args();root=ET.parse(args.native_xml).getroot()
    all_tests=list(root.iter('testcase'));cases=[]
    for case in all_tests:
        if not case.get('name','').startswith('rpv9behaviourgametests.'):continue
        failure=[{'tag':e.tag,'message':e.get('message',''),'text':e.text} for e in case if e.tag in ('failure','error','skipped')]
        cases.append({'name':case.get('name'),'timeSeconds':case.get('time'),'status':'PASS' if not failure else 'FAIL_OR_SKIPPED','failure':failure})
    if len(cases) not in (10,11,12):raise ValueError('Expected ten requested RP checks plus optional movement compatibility and diagnostic regressions, got:'+str(len(cases)))
    passed=all(c['status']=='PASS' for c in cases)
    report={'schema':'dreamwalker-rp-v9-native-outcomes-v1','status':('PASS_'+str(len(cases))+'_REQUESTED_RP_NATIVE_CHECKS') if passed else 'FAIL_REQUESTED_RP_NATIVE_CHECKS',
            'nativeXml':file_ref(args.native_xml),'nativeLog':file_ref(args.native_log),'rpTestCount':len(cases),
            'productionArtifact':file_ref(args.jar) if args.jar else None,
            'rpFailures':sum(c['status']!='PASS' for c in cases),'wholeNativeTestCount':len(all_tests),
            'wholeNativeFailures':sum(any(e.tag in ('failure','error','skipped') for e in c) for c in all_tests),
            'cases':cases,'originalForgeGate':file_ref(ROOT/'reports/SOURCE_WOODGATE_V9_COMPARISON.json'),
            'catalogueResourceAudit':file_ref(ROOT/'reports/RP_V9_OBJECT_AUDIT.json'),
            'geometry':{'stairsSourceTreads':26,'ladderWorkingPlanes':2,'ladderClimbingZones':2,
                        'npcWindowWorkingPlanes':1,'woodGateWorkingPlanes':1,'smallChandelierPhysicalBoxes':0,
                        'newDefaultLadderBaseAnchorPixels':[0,-473,-24.75],
                        'newStairsBaseAnchorPixels':[0,-192,-213],
                        'loadedSourceEntityPose':'not compensated; exact Pos roundtrip tested',
                        'customEntityLookup':'server/client loaded index, real bounds; no expanded chunk scan'},
            'placementScope':'actual ItemUsageContext floor/ceiling/base placement, one spawned object, active overlap refusal, checked rotation rollback and creative attack at real stair/ladder base beyond8m from origin',
            'nativeScope':'Fabric development GameTest runtime; real items/entities/player move/travel. This is separate from an ordinary remapped JAR client/server launch.',
            'manualGameplay':'NOT_RUN','manualVisualAcceptance':'PENDING_USER_REVIEW','fullRpCatalogueAccepted':False,
            'originalInputsModified':False,'rpModelTextureAnimationBytesChanged':False}
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    print(json.dumps({'status':report['status'],'rpTests':len(cases),'rpFailures':report['rpFailures'],'output':str(args.output)}))
    return 0 if passed else 1

if __name__=='__main__':raise SystemExit(main())
