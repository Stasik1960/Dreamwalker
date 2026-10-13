"""Read-only full-plan lighting fixture with exact lossless coordinate runs.

No source ZIP/world modification. One section at a time; repeated index/palette
patterns have bounded caches, and statistics are keyed by finite decisions.
"""
import argparse,collections,functools,gzip,hashlib,json,sqlite3,time,zlib
from pathlib import Path
import numpy as np
from v127_visual_export import ROOT,OUT,write_json

PLAN=ROOT/'build/v127-full-conversion-first.sqlite'
DEST=ROOT/'docs/v12.7/conversion'
def token(value):return json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=False)
def state(registry,properties):
    return registry+('['+','.join(k+'='+str(v) for k,v in sorted(properties.items()))+']' if properties else '')
def runs(indices):
    splits=np.flatnonzero(np.diff(indices)>1)+1
    return tuple((int(part[0]),len(part)) for part in np.split(indices,splits)) if len(indices) else ()
@functools.lru_cache(maxsize=128)
def pattern(blob):
    index=np.frombuffer(zlib.decompress(blob),dtype='<u2');assert len(index)==4096
    result=[]
    for did in np.unique(index):
        at=np.flatnonzero(index==did)
        result.append((int(did),len(at),int(at[0]),int(at[0]>>8),int(at[-1]>>8),runs(at)))
    return tuple(result)
@functools.lru_cache(maxsize=128)
def palette(text):return tuple(row[0] for row in json.loads(text))
def positions(linear):return np.concatenate([np.arange(start,start+length,dtype=np.int32) for start,length in linear])

def main(plan,fixture):
    DEST.mkdir(parents=True,exist_ok=True)
    db=sqlite3.connect(f'file:{plan.resolve()}?mode=ro',uri=True);db.execute('BEGIN')
    summary=json.loads(db.execute("SELECT data FROM metadata WHERE key='summary'").fetchone()[0]);assert summary['status']=='PASS_COMPLETE_SOURCE_PLAN'
    decisions={int(i):json.loads(s) for i,s in db.execute('SELECT id,data FROM decisions')}
    whole={int(i):json.loads(s) for i,s in db.execute('SELECT id,data FROM whole_objects')}
    rows=[];keys={};section_count=0;start=time.monotonic();actions=collections.Counter();active=set()
    coordinates=DEST/'source-lighting-coordinate-runs.jsonl.gz';temp=coordinates.with_suffix('.gz.v127-write.tmp')
    def case(did,source,dim,cx,cz,sy,at_count,first,y0,y1,linear,out):
        decision=decisions[did];action=decision['action'];target=decision.get('registryId');properties=decision.get('properties',{});payload=decision.get('payload',{});root_shift=0
        if action=='KEEP':target_state=source
        elif action=='REMOVE':target_state='minecraft:air'
        elif action=='CONSUMED':
            owner=whole[int(decision['wholeObject'])];target=owner['decision']['registryId'];properties=owner['decision']['properties'];payload=owner['decision'].get('payload',{});target_state=state(target,properties)
            # Coordinates refer to the original component; occupancy bounds use
            # the whole owner's anchor, which is stored in the native fixture.
            root_shift=int(owner['position'][1])-(sy*16+(first>>8))
        else:target_state=state(target,properties)
        key=token([action,source,target_state,payload,root_shift])
        if key not in keys:
            number=len(rows);keys[key]=number
            rows.append({'caseId':number,'action':action,'sourceState':source,'targetState':target_state,'targetRegistryId':target,
                'targetProperties':properties,'payload':payload,'occurrences':0,'decisionIds':set(),'sourceYMinimum':sy*16+y0,'sourceYMaximum':sy*16+y1,
                'anchorYMinimum':sy*16+y0+root_shift,'anchorYMaximum':sy*16+y1+root_shift,'samples':[],
                'sourceModels':sorted({r['model'] for r in decision.get('sourceApplications',[])}),
                'parityPolicy':'EXACT_ROOT_EMISSION_REQUIRED' if action in {'OWNER','COMPACT'} else 'WHOLE_COMPONENT_CONTEXT_REPORTED' if action=='CONSUMED' else 'AUTHORIZED_REMOVAL_REPORTED' if action=='REMOVE' else 'UNCHANGED_SOURCE_STATE',
                'coordinateRunKey':number})
        row=rows[keys[key]];row['occurrences']+=at_count;row['decisionIds'].add(did)
        row['sourceYMinimum']=min(row['sourceYMinimum'],sy*16+y0);row['sourceYMaximum']=max(row['sourceYMaximum'],sy*16+y1)
        row['anchorYMinimum']=min(row['anchorYMinimum'],sy*16+y0+root_shift);row['anchorYMaximum']=max(row['anchorYMaximum'],sy*16+y1+root_shift)
        if len(row['samples'])<3:row['samples'].append({'dimension':dim,'position':[cx*16+(first&15),sy*16+(first>>8),cz*16+((first>>4)&15)],'anchorY':sy*16+(first>>8)+root_shift})
        out.write(token({'caseId':row['caseId'],'decisionId':did,'dimension':dim,'chunk':[cx,cz],'sectionY':sy,'linearRuns':linear,'count':at_count})+'\n')
        actions[action]+=at_count;active.add(did)
    with gzip.open(temp,'wt',encoding='utf-8',compresslevel=3) as out:
        for dim,cx,cz,sy,sp,si,di in db.execute('SELECT dimension,cx,cz,sy,source_palette,source_indices,decision_indices FROM sections ORDER BY dimension,cx,cz,sy'):
            names=None;source_index=None
            for did,count,first,y0,y1,linear in pattern(di):
                d=decisions[did]
                if d.get('sourceState') and d['action']!='CONSUMED':case(did,d['sourceState'],dim,cx,cz,sy,count,first,y0,y1,linear,out);continue
                if names is None:names=palette(sp)
                if len(names)==1:case(did,names[0],dim,cx,cz,sy,count,first,y0,y1,linear,out);continue
                if source_index is None:source_index=np.frombuffer(zlib.decompress(si),dtype='<u2');assert len(source_index)==4096
                indices=positions(linear)
                for pi in np.unique(source_index[indices]):
                    at=indices[source_index[indices]==pi]
                    case(did,names[int(pi)],dim,cx,cz,sy,len(at),int(at[0]),int(at[0]>>8),int(at[-1]>>8),runs(at),out)
            section_count+=1
            if section_count%20000==0:print(token({'sections':section_count,'cases':len(rows),'seconds':round(time.monotonic()-start,1)}),flush=True)
    for row in rows:row['decisionIds']=sorted(row['decisionIds'])
    temp.replace(coordinates);db.close()
    assert dict(actions)==summary['counts'] and section_count==summary['sections']
    result={'schemaVersion':1,'status':'PASS_READ_ONLY_COMPLETE_SOURCE_LIGHTING_DECISIONS','sourcePlan':str(plan.relative_to(ROOT)),
        'sourceHashes':summary['sourceHashes'],'sections':section_count,'allPaletteCells':sum(actions.values()),'actionOccurrences':dict(actions),
        'reachableDecisionIds':len(active),'unusedHistoricalDecisionIds':len(decisions)-len(active),'cases':rows,
        'coordinateRunFile':str(coordinates.relative_to(ROOT)),'coordinateRunSha256':hashlib.sha256(coordinates.read_bytes()).hexdigest(),
        'coordinateEncoding':'X fastest, then Z, then Y; index=(y&15)*256+(z&15)*16+(x&15). Each[start,length] is an exact original source coordinate run. Join by caseId.',
        'sourceArchivesModified':False,'runtimeWorldModified':False,'boundedIndexPatternCache':128,'elapsedSeconds':round(time.monotonic()-start,2)}
    write_json(fixture,result);write_json(DEST/'source-lighting-decision-audit.json',{k:v for k,v in result.items() if k!='cases'}|{'uniqueNativeDecisionCases':len(rows),'fixtureFile':str(fixture.relative_to(ROOT)),'nativeStatus':'NOT_RUN'})
    print(token({k:v for k,v in result.items() if k!='cases'}|{'uniqueNativeDecisionCases':len(rows)}),flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--plan',type=Path,default=PLAN);parser.add_argument('--fixture',type=Path,default=ROOT/'build/v127-source-lighting-decisions.json');a=parser.parse_args();main(a.plan,a.fixture)
