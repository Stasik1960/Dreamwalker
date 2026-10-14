"""Bind every reachable old compact case to current/planned native targets.

Uses the persisted decision dictionary and original lossless native fixture;
no sections, original map, weight selection or preparation are recomputed.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sqlite3

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/gametest/resources/bloodborne_dw'

def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def state(registry,properties):return registry+('['+','.join(f'{key}={value}' for key,value in sorted(properties.items()))+']' if properties else '')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--planned-mapping',type=Path);args=parser.parse_args()
    plan=ROOT/'build/v1271-city-preparation/source-plan.sqlite'
    connection=sqlite3.connect(plan.resolve().as_uri()+'?mode=ro',uri=True)
    decisions={i:json.loads(value) for i,value in connection.execute('SELECT id,data FROM decisions')};connection.close()
    planned={}
    if args.planned_mapping:
        document=read(args.planned_mapping)
        rows=document.get('mappings',document.get('contexts',document.get('rows',[])))
        if isinstance(rows,dict):rows=[dict(value,decisionId=int(key)) for key,value in rows.items()]
        for row in rows:
            ids=row.get('decisionIds',[row.get('decisionId')]);target=row.get('target',row)
            registry=target.get('registryId',target.get('targetRegistryId'));properties=target.get('properties',target.get('targetProperties'))
            assert registry and properties is not None,row
            for i in ids:planned[i]=(registry,properties)
    fixture_path=RES/'v127-source-lighting-decisions.json';fixture=read(fixture_path);groups=[];original=0;filler=0
    for case in fixture['cases']:
        ids=case['decisionIds']
        if case['action']=='COMPACT':original+=1
        elif case['action']=='KEEP' and any(decisions[i].get('action')=='COMPACT' for i in ids):filler+=1
        else:continue
        targets={}
        for i in ids:
            decision=decisions[i]
            if i in planned:registry,props=planned[i]
            else:
                assert decision['action'] in ('COMPACT','VANILLA'),(i,decision)
                registry,props=decision['registryId'],decision.get('properties',{})
            text=state(registry,props)
            targets.setdefault(text,[]).append(i)
        groups.append({'caseId':case['caseId'],'sourceState':case['sourceState'],'originalAction':case['action'],'originalOccurrences':case['occurrences'],'samples':case['samples'],'targets':[{'state':text,'decisionIds':ids} for text,ids in targets.items()]})
    assert original==1810,(original,filler)
    output={'schemaVersion':1,'status':'INPUT_READY_FOR_EXHAUSTIVE_ACTUAL_COMPACT_FLAGS','sourcePlanSha256':hashlib.sha256(plan.read_bytes()).hexdigest(),'originalNativeFixtureSha256':hashlib.sha256(fixture_path.read_bytes()).hexdigest(),'plannedMappingSha256':hashlib.sha256(args.planned_mapping.read_bytes()).hexdigest() if args.planned_mapping else None,'originalReachableCompactGroups':original,'additionalPreviouslyKeptFillerGroups':filler,'uniqueCurrentOrPlannedTargetCases':sum(len(group['targets']) for group in groups),'originalCompactOccurrences':sum(group['originalOccurrences'] for group in groups if group['originalAction']=='COMPACT'),'plannedDictionaryRecords':len(planned),'mapOrSectionsRescanned':False,'groups':groups}
    path=RES/'v1271-compact-native-flag-cases.json';path.write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps({k:output[k] for k in output if k!='groups'}))

if __name__=='__main__':main()
