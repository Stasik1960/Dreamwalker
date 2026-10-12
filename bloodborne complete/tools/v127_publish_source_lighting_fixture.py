"""Publish frozen read-only audit inputs and exact NEW pose/source references."""
import collections,json,sqlite3
from v127_visual_export import ROOT,RES,write_json
def main():
    fixture=json.loads((ROOT/'build/v127-source-lighting-decisions.json').read_text(encoding='utf-8'))
    assert fixture['status']=='PASS_READ_ONLY_COMPLETE_SOURCE_LIGHTING_DECISIONS'
    db=sqlite3.connect(f"file:{(ROOT/fixture['sourcePlan']).resolve()}?mode=ro",uri=True);whole={}
    for (encoded,) in db.execute('SELECT data FROM whole_objects'):
        owner=json.loads(encoded);decision=owner['decision'];properties=decision['properties'];target=decision['registryId']+'['+','.join(k+'='+str(v) for k,v in sorted(properties.items()))+']';payload=decision.get('payload',{})
        key=json.dumps([target,payload],sort_keys=True);group=whole.setdefault(key,{'targetState':target,'payload':payload,'occurrences':0,'anchorYMinimum':owner['position'][1],'anchorYMaximum':owner['position'][1],'samples':[]})
        group['occurrences']+=1;group['anchorYMinimum']=min(group['anchorYMinimum'],owner['position'][1]);group['anchorYMaximum']=max(group['anchorYMaximum'],owner['position'][1])
        if len(group['samples'])<3:group['samples'].append({'dimension':owner['dimension'],'position':owner['position']})
    db.close();fixture['wholeOwnerHeightCases']=list(whole.values());fixture['wholeOwnerOccurrences']=sum(r['occurrences'] for r in whole.values())
    groups={}
    for row in fixture['cases']:
        registry=row.get('targetRegistryId')
        if row['action'] not in {'OWNER','COMPACT','CONSUMED'} or not registry or not registry.startswith('bloodborne_dw:source_'):continue
        properties=row['targetProperties'];pose=int(properties.get('variant',0))+16*int(properties.get('art_bank',0));opened=properties.get('open','false')=='true'
        key=(registry,pose,opened);group=groups.setdefault(key,{'registryId':registry,'variant':pose,'open':opened,'sources':{}})
        source=group['sources'].setdefault(row['sourceState'],{'sourceState':row['sourceState'],'occurrences':0,'caseIds':[]});source['occurrences']+=row['occurrences'];source['caseIds'].append(row['caseId'])
    objects=[]
    for key,group in sorted(groups.items()):group['sources']=[v for k,v in sorted(group['sources'].items())];objects.append(group)
    metadata={'schemaVersion':1,'policy':'EXACT_NATIVE_SOURCE_LUMINANCE_UNIQUE_PER_NEW_LOGICAL_POSE; conflicts are reported, never guessed; original V12 settings untouched',
        'sourceHashes':fixture['sourceHashes'],'coordinateRunFile':fixture['coordinateRunFile'],'coordinateRunSha256':fixture['coordinateRunSha256'],'objects':objects}
    write_json(ROOT/'src/gametest/resources/bloodborne_dw/v127-source-lighting-decisions.json',fixture)
    write_json(RES/'bloodborne_dw/imported-source-lighting-v127.json',metadata)
    print(json.dumps({'status':'PASS_FROZEN_AUDIT_INPUT_PUBLISHED','nativeDecisionCases':len(fixture['cases']),'newPoseEmissionSourceGroups':len(objects),'newRegistryTypes':len({r['registryId'] for r in objects})}))
if __name__=='__main__':main()
