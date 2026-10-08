"""Bounded evidence collector for the V9 type/glazing/wall changes only."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
GROUPS={'glazingmountgametests.':3,'prototypewindowgametests.':7,'prototypewallgametests.':10,'debugcataloguegametests.':2}
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--gametest','--nativeXML',type=Path,required=True);parser.add_argument('--client',type=Path);parser.add_argument('--jar',type=Path);parser.add_argument('--native-log',type=Path);args=parser.parse_args()
    cases=list(ET.parse(args.gametest).getroot().iter('testcase'));groups=[]
    for prefix,expected in GROUPS.items():
        owned=[case for case in cases if case.get('name','').lower().startswith(prefix)]
        assert len(owned)==expected,(prefix,len(owned),expected)
        failures=[{'name':case.get('name'),'failure':failure.get('message')} for case in owned for failure in case.findall('failure')+case.findall('error')]
        groups.append({'classPrefix':prefix,'testCases':len(owned),'failures':failures,'status':'PASS' if not failures else 'FAIL','names':[case.get('name') for case in owned]})
    assert all(group['status']=='PASS' for group in groups),'Owned V9 type/glazing/wall/catalogue native test failure'
    total=sum(group['testCases'] for group in groups)
    report={'schema':'dreamwalker-v9-type-glazing-runtime-evidence-v1','status':'PASS_22_OWNED_NATIVE_CLIENT_PENDING','nativeXml':str(args.gametest.resolve()),'nativeXmlSha256':sha(args.gametest),'ownedGroups':groups,'ownedNativeTests':total,
            'wholeNativeSuiteCaseCount':len(cases),'wholeNativeSuiteFailingCases':sum(bool(case.findall('failure')+case.findall('error')) for case in cases),'wholeSuitePassClaimed':False,
            'nativeScope':'Actual item placement, cardinal glazing, independent mounts/support removal, typed persistence, legacy angular pick normalization,8wall type items, nativeAUTO topology, native seam/cap union and ordinary solid-RP rejection, TEMP aliases and item/entity presentation immutability.',
            'manualVisualAcceptance':'NOT_RUN','wholeCityConversion':False}
    if args.native_log:
        rows=[json.loads(match.group(1)) for match in re.finditer(r'DW_V9_WALL_STACK_GEOMETRY (\{[^\r\n]+\})',args.native_log.read_text(encoding='utf8',errors='replace'))]
        assert len(rows)==1,('Expected one actual native wall geometry counter row',len(rows))
        geometry=rows[0];assert geometry['pairsChecked']==192 and geometry['samePlayerUnion'] and not geometry['migrationExceptionAdded']
        report['wallGeometry']={'nativeLog':str(args.native_log.resolve()),'nativeLogSha256':sha(args.native_log),'actualJunctionCounters':geometry,
                                'standaloneRawCollisionMaximumBoxes':5,'standaloneDiagonalCollisionMaximumBoxes':1,'selectionMaximumBoxes':1,'nativeRawFormRecipesChecked':1296,
                                'junctionCacheMaximumEntries':4096,'junctionFragmentsAreSeparateFromStandaloneBudget':True,
                                'junctionScope':'Own upper attributes duplicated native wall guard to lower RAW. Own lower attributes duplicate extension to an eligible non-own native wall or minecraft full-cube cap. No composite/RP/entity cap subtraction.',
                                'stateCounts':{'legacyPrimary':82944,'eachFixedMaterial':10368,'fixedMaterialTypes':7,'allWallTypesTotal':155520},
                                'fpsClaim':False}
    if args.jar:report['productionJarSha256']=sha(args.jar)
    if args.client:
        client=json.loads(args.client.read_text(encoding='utf8'));assert client['status']=='PASS_CLIENT_WORLD_RESOURCES_NORMAL_STOP_REQUESTED'
        families=client['registryFamilies'];assert len(families)==17 and all(row['blockRegistered'] and row['itemRegistered'] for row in families)
        provider=client['wallModelProvider'];assert provider['stateBindings']==155520 and provider['visualKeys']==17152 and provider['primitiveBakedModels']==160 and provider['clonedBakedQuads']==0
        assert len(client['glazingActualBakedMountBounds'])==48 and all(row['twoOpposingAuthoredFaces'] for row in client['glazingActualBakedMountBounds'])
        assert len(client['legacyGlazingCanonicalInventoryArt'])==6 and len(client['wallLegacyToIndependentTypeBakedEquality'])==128
        assert client['ladderStateModelsChecked']==1152 and len(client['ordinaryAndSourceLadderBakedEquality'])==576
        report.update({'status':'PASS_22_OWNED_NATIVE_AND_ACTUAL_CLIENT_MODEL_OBSERVATION','clientReport':str(args.client.resolve()),'clientReportSha256':sha(args.client),'clientMarkerSha256':client['markerSha256'],
                       'clientProof':{'registryFamilies':families,'wallModelProvider':provider,'glazingPoseChecks':48,'legacyCanonicalInventoryChecks':6,'wallLegacyToIndependentBakedEqualityPairs':128,'ladderStateBakes':1152,'ordinarySourceLadderEqualityPairs':576},
                       'normalStopRequested':client['normalStopRequested'],'actualSaveExit':'SEPARATE_WRAPPER_PROOF_REQUIRED'})
    target=ROOT/'reports/V9_TYPES_GLAZING_RUNTIME.json'
    if target.exists():
        old=target.read_bytes();history=ROOT/'reports/v9-history'/('V9_TYPES_GLAZING_RUNTIME-'+hashlib.sha256(old).hexdigest()+'.json');history.parent.mkdir(parents=True,exist_ok=True)
        if history.exists():assert history.read_bytes()==old
        else:history.write_bytes(old)
    target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8');print(json.dumps({'status':report['status'],'ownedNativeTests':total,'wholeSuiteFailingCases':report['wholeNativeSuiteFailingCases']}))
if __name__=='__main__':main()
