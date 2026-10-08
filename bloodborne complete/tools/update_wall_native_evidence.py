"""Attach actual coordinated GameTest results without rerunning the shape benchmark."""
from pathlib import Path
import argparse
import hashlib
import json
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def ref(path):return str(path.resolve().relative_to(ROOT)).replace('\\','/')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--xml',required=True,type=Path)
    parser.add_argument('--log',required=True,type=Path)
    args=parser.parse_args()
    report_path=ROOT/'reports/WALL_REVIEW_CAUSE_FIX_COUNTS.json'
    report=json.loads(report_path.read_text(encoding='utf-8-sig'))
    native_shapes=report['nativeShapes']
    raw_shapes_hash=hashlib.sha256(json.dumps(native_shapes,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    cases=list(ET.parse(args.xml).getroot().iter('testcase'))
    wall=[case for case in cases if case.get('name','').startswith('prototypewallgametests.')]
    assert len(wall)==9 and all(case.find('failure') is None and case.find('error') is None for case in wall)
    assert len({case.get('name') for case in wall})==9
    current=ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/wall'
    assert report['geometryCurrentSha256']==sha(current/'WallGeometry.java')
    assert report['wallBlockSha256']==sha(current/'PrototypeWallBlock.java')
    if 'localCompileTestsSha256' not in report:report['localCompileTestsSha256']=report['testsSha256']
    report['testsSha256']=sha(ROOT/'src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/PrototypeWallGameTests.java')
    report['status']='PASS_ACTUAL_DEDICATED_WALL_GAMEPLAY_AND_NATIVE_SHAPE_COUNTS'
    report['nativeGameTests']={'status':'PASS_ACTUAL_9_OF_9','xml':ref(args.xml),'xmlSha256':sha(args.xml),
        'log':ref(args.log),'logSha256':sha(args.log),'testcases':[dict(case.attrib) for case in wall],
        'wholeSuiteTestcases':len(cases),'wholeSuiteFailures':sum(case.find('failure') is not None or case.find('error') is not None for case in cases),
        'coverage':'Actual BlockItem→place, native WallBlock oracle, neighbor transitions/full/partial upper coverage, mixed LOW/TALL, upper own-wall support, rights, eight yaw, pick/drop/state palette and simple cached native shapes.',
        'mixedAboveFixture':'Native polished_deepslate_wall UP=true/NORTH=LOW above, backed by STONE north; its actual native collision covers only NORTH TALL probe. A TOP stair half-depth does not cover any whole nine-pixel native probe.',
        'priorAttempt15Failure':'Incorrect TOP-stair mixed-height test expectation; native oracle matched. Fixture corrected, production native neighbor rules unchanged.',
        'newClientResourceLoadStatus':'PENDING_COORDINATED_V8_CLIENT','manualUserAcceptance':False}
    report['limits']=[limit for limit in report['limits'] if not limit.startswith('Actual dedicated GameTests')]
    report['limits'].append('Dedicated9wall tests passed; current82944-state ordinary4G resource/client load remains a separate coordinated pending gate. Historical v7 client PASS is not evidence for the new schema.')
    report['limits']=list(dict.fromkeys(report['limits']))
    report['rawLocalNativeShapesPreservedSha256']=raw_shapes_hash
    assert native_shapes==report['nativeShapes']
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':report['status'],'wallNativePassed':len(wall),'suiteTests':len(cases),'nativeShapeCountersAndTimingsPreserved':True}))

if __name__=='__main__':main()
