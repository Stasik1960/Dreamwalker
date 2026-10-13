"""Read actual native offset flags; source archives/world remain unchanged."""
import argparse,collections,gzip,json
from pathlib import Path
from v125_map_index import ROOT,token
from v127_apply_full_map_poses import write
def main(path):
    proof=json.loads(path.read_text(encoding='utf-8-sig'));rows=[r for r in proof['cases'] if r.get('sourceHasNativeModelOffset')];counts=collections.Counter()
    for r in rows:counts[r['action']]+=r['occurrences']
    out={'status':'PASS_READONLY_NATIVE_OFFSET_FLAG_INVENTORY','sourceNativeOffsetCases':len(rows),'actionOccurrences':dict(counts),'rows':rows,'worldWritten':False}
    write(ROOT/'build/v127-native-source-model-offset-cases.json',out)
    owner={r['caseId']:r for r in rows if r['action']=='OWNER'};fixture=json.loads((ROOT/'src/gametest/resources/bloodborne_dw/v127-source-lighting-decisions.json').read_text(encoding='utf-8-sig'));payload={r['caseId']:r['payload'] for r in fixture['cases']};positions=[]
    with gzip.open(ROOT/proof['coordinateRunFile'],'rt',encoding='utf8') as source:
        for line in source:
            r=json.loads(line)
            if r['caseId'] not in owner:continue
            native=owner[r['caseId']];cx,cz=r['chunk'];sy=r['sectionY']
            for start,length in r['linearRuns']:
                for i in range(start,start+length):positions.append({'dimension':r['dimension'],'position':[cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15)],'sourceState':native['sourceState'],'targetState':native['targetState'],'payload':payload[r['caseId']]})
    assert len(positions)==64 and all(r['targetState'].startswith('bloodborne_dw:source_') for r in positions)
    doc={'schemaVersion':1,'status':'PASS_NATIVE_SOURCE_MODEL_OFFSET_64_CELL_PLAN','sourceHashes':proof['sourceHashes'],'sourceStates':sorted({r['sourceState'] for r in positions}),'ownerSourceCells':len(positions),'removedSourceCells':counts['REMOVE'],'rows':positions,'policy':'Disabled full author reads the actual native source BlockState.getModelOffset at exact original XYZ and adds inverse target yaw to existing local SourceShift once. No original V12/model/manual placement settings change; no guessed random hash.'}
    write(ROOT/'docs/v12.7/conversion/source-native-model-offset-policy.json',doc);write(ROOT/'src/architecture/resources/bloodborne_dw/imported-source-model-offset-v127.json',{k:v for k,v in doc.items() if k!='rows'});write(ROOT/'src/gametest/resources/bloodborne_dw/v127-source-model-offset-cases.json',doc)
    print(token({'cases':len(rows),'actions':dict(counts),'ownerCoordinates':len(positions),'sourceStates':doc['sourceStates']}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('proof',type=Path);main(p.parse_args().proof)
