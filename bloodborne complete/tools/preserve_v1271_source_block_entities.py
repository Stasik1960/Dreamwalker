"""Preserve 56 original typed block-entity records without executing mechanics."""
import base64,collections,gzip,json,sqlite3,traceback
from pathlib import Path
from prepare_v1271_city_compatibility import ROOT,OUT,read,write,sha,token
from v1271_prepared_city import PreparedCity
from world_io import Tag,NbtFile,encode_nbt

def typed(value):
    kind=value['type'];data=value['value']
    if kind==10:data={k:typed(v) for k,v in data.items()}
    elif kind==9:data=[typed(v) for v in data]
    elif kind==7:data=bytes.fromhex(data)
    return Tag(kind,data,value.get('listType'))

def main():
    evidence=ROOT/'docs/v12.7/conversion/original-entities-block-entities.jsonl.gz'
    original=read(ROOT/'docs/v12.7/conversion/original-entities-summary.json');assert sha(evidence)==original['typedEvidenceSha256']
    plan=ROOT/'build/v1271-city-preparation/source-plan.sqlite';rows=[]
    with PreparedCity(plan) as city,gzip.open(evidence,'rt',encoding='utf8') as source:
        for line in source:
            record=json.loads(line)
            if record['category']!='block_entity':continue
            position=[int(v) for v in record['sourcePosition']];dim=record['dimension'];decision=city.decision_at(dim,*position);assert decision
            action=decision['action'];owner=position
            if action=='CONSUMED':
                data=city.connection.execute('SELECT data FROM whole_objects WHERE id=?',(decision['wholeObject'],)).fetchone();assert data
                whole=json.loads(data[0]);owner=whole['position'];action='OWNER'
            blob=encode_nbt(NbtFile('',typed(record['typedNbt'])))
            rows.append({'dimension':dim,'sourcePosition':position,'ownerPosition':owner,'sourceId':record['id'],'decisionAction':action,'nbtBase64':base64.b64encode(blob).decode(),'typedNbtSha256':__import__('hashlib').sha256(blob).hexdigest(),'policy':'Attach inert exact typed source NBT to mapped owner; KEEP retains real vanilla BE; no-BE source target retains savedworld metadata only'})
    assert len(rows)==56
    runtime=plan.parent/'runtime';manifest=read(runtime/'manifest.json');manifest['originalSourceBlockEntities']=rows;manifest['originalSourceBlockEntityPolicy']='Exact typed archival metadata; mapped owner payload has OriginalSourceBlockEntities; no generated inventory/GUI or original commands/datapacks are executed'
    con=sqlite3.connect(plan);summary=json.loads(con.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);summary['originalSourceBlockEntityCount']=len(rows);summary['originalSourceBlockEntityTargetActions']=dict(collections.Counter(r['decisionAction'] for r in rows));con.execute('UPDATE metadata SET data=? WHERE key=?',(token(summary),'summary'));con.commit();con.close()
    manifest.update(summary);manifest['sourcePlanSha256']=sha(plan);write(runtime/'manifest.json',manifest)
    proof={'status':'PASS_EXACT_TYPED_ORIGINAL_BLOCK_ENTITY_METADATA_PRESERVATION','count':56,'actions':summary['originalSourceBlockEntityTargetActions'],'typedSourceEvidenceSha256':sha(evidence),'runtimeManifestSha256':sha(runtime/'manifest.json'),'sourcePlanSha256':sha(plan),'sourceProgramsExecuted':False,'inventoriesAndBooks':'Exact original typed NBT retained; custom model targets do not gain an invented vanilla GUI','nativeValidation':'PENDING_OWNER_PAYLOAD_IMPORT'}
    write(OUT/'source-block-entity-metadata.json',proof)

if __name__=='__main__':
    result=ROOT/'build/v1271-source-metadata-result.json'
    try:main();write(result,{'status':'PASS','exitCode':0})
    except BaseException:write(result,{'status':'FAIL','exitCode':1,'error':traceback.format_exc()});raise
