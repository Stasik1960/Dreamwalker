"""Publish frozen read-only audit inputs and exact NEW pose/source references."""
import collections,functools,json,sqlite3,zlib
import numpy as np
from v127_visual_export import ROOT,RES,write_json
def main():
    fixture=json.loads((ROOT/'build/v127-source-lighting-decisions.json').read_text(encoding='utf-8'))
    assert fixture['status']=='PASS_READ_ONLY_COMPLETE_SOURCE_LIGHTING_DECISIONS'
    backing_file=ROOT/'docs/v12.7/conversion/enclosed-vanilla-filler-proof.json'
    if backing_file.is_file():
        backing=json.loads(backing_file.read_text(encoding='utf-8'))
        assert backing['sourceHashes']==fixture['sourceHashes'] and backing['status']=='PASS_READ_ONLY_OPAQUE_SOURCE_ENCLOSED_FILLER_PROOF'
        fixture['enclosedOpaqueSourceStates']=sorted({r['sourceState'] for r in backing['opaqueSourceDecisionProofs']})
        fixture['enclosedVanillaFillerCells']=backing['provenEnclosedVanillaFillerCells']
        fixture['opaqueBackingProof']='docs/v12.7/conversion/enclosed-vanilla-filler-proof.json'
    db=sqlite3.connect(f"file:{(ROOT/fixture['sourcePlan']).resolve()}?mode=ro",uri=True);whole={};helpers={}
    @functools.lru_cache(maxsize=32)
    def source_section(dimension,cx,cz,sy):
        row=db.execute('SELECT source_palette,source_indices FROM sections WHERE dimension=? AND cx=? AND cz=? AND sy=?',(dimension,cx,cz,sy)).fetchone();assert row is not None
        return json.loads(row[0]),np.frombuffer(zlib.decompress(row[1]),dtype='<u2')
    for (encoded,) in db.execute('SELECT data FROM whole_objects'):
        owner=json.loads(encoded);decision=owner['decision'];properties=decision['properties'];target=decision['registryId']+'['+','.join(k+'='+str(v) for k,v in sorted(properties.items()))+']';payload=decision.get('payload',{})
        key=json.dumps([target,payload],sort_keys=True);group=whole.setdefault(key,{'targetState':target,'payload':payload,'occurrences':0,'anchorYMinimum':owner['position'][1],'anchorYMaximum':owner['position'][1],'samples':[]})
        group['occurrences']+=1;group['anchorYMinimum']=min(group['anchorYMinimum'],owner['position'][1]);group['anchorYMaximum']=max(group['anchorYMaximum'],owner['position'][1])
        if len(group['samples'])<3:group['samples'].append({'dimension':owner['dimension'],'position':owner['position']})
        for position in owner['consumedSourceCells']:
            offset=[position[i]-owner['position'][i] for i in range(3)]
            if offset==[0,0,0]:continue
            x,y,z=position;palette,indices=source_section(owner['dimension'],x//16,z//16,y//16);source=palette[int(indices[(y&15)*256+(z&15)*16+(x&15)])][0]
            key=json.dumps([target,payload,offset,source],sort_keys=True);helper=helpers.setdefault(key,{'targetState':target,'payload':payload,'sourceState':source,'relativeCell':offset,'occurrences':0,'samples':[]})
            helper['occurrences']+=1
            if len(helper['samples'])<3:helper['samples'].append({'dimension':owner['dimension'],'position':position,'rootPosition':owner['position']})
    db.close();fixture['wholeOwnerHeightCases']=list(whole.values());fixture['wholeOwnerOccurrences']=sum(r['occurrences'] for r in whole.values())
    fixture['wholeConsumedHelperCases']=list(helpers.values());fixture['wholeConsumedNonrootOccurrences']=sum(r['occurrences'] for r in helpers.values())
    groups={}
    for row in fixture['cases']:
        registry=row.get('targetRegistryId')
        if row['action'] not in {'OWNER','COMPACT','CONSUMED'} or not registry or not registry.startswith('bloodborne_dw:source_'):continue
        properties=row['targetProperties'];pose=int(properties.get('variant',0))+16*int(properties.get('art_bank',0));opened=properties.get('open','false')=='true'
        wet=str(properties.get('waterlogged','false')).lower()=='true'
        key=(registry,pose,opened,wet);group=groups.setdefault(key,{'registryId':registry,'variant':pose,'open':opened,'stateProperties':{'waterlogged':'true'} if wet else {},'sources':{}})
        source=group['sources'].setdefault(row['sourceState'],{'sourceState':row['sourceState'],'occurrences':0,'caseIds':[]});source['occurrences']+=row['occurrences'];source['caseIds'].append(row['caseId'])
    objects=[]
    for key,group in sorted(groups.items()):group['sources']=[v for k,v in sorted(group['sources'].items())];objects.append(group)
    metadata={'schemaVersion':1,'policy':'EXACT_NATIVE_SOURCE_LUMINANCE_OPACITY_FLUID_UNIQUE_PER_NEW_LOGICAL_POSE_AND_REVIEWED_WET_STATE; conflicts are reported, never guessed; original V12 settings/optics untouched',
        'sourceHashes':fixture['sourceHashes'],'coordinateRunFile':fixture['coordinateRunFile'],'coordinateRunSha256':fixture['coordinateRunSha256'],'objects':objects}
    write_json(ROOT/'src/gametest/resources/bloodborne_dw/v127-source-lighting-decisions.json',fixture)
    write_json(RES/'bloodborne_dw/imported-source-lighting-v127.json',metadata)
    print(json.dumps({'status':'PASS_FROZEN_AUDIT_INPUT_PUBLISHED','nativeDecisionCases':len(fixture['cases']),'newPoseEmissionSourceGroups':len(objects),'newRegistryTypes':len({r['registryId'] for r in objects})}))
if __name__=='__main__':main()
