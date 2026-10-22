"""Read one bound target slot, immutable cached input and archived placements."""
import argparse, collections, json, sqlite3, sys
from pathlib import Path
P=Path(__file__).resolve().parents[2];R=P.parent/'research'
sys.path.insert(0,str(P/'tools/native-grid-city'))
import migrate as m
import context_stage as c
plan=R/'native-grid-plan';db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+'?mode=ro',uri=True)
old=m.meta(db,'binding')['oldPlan'];db.execute('ATTACH DATABASE ? AS old',('file:'+(Path(old)/'plan.sqlite').as_posix()+'?mode=ro',))
parser=argparse.ArgumentParser();parser.add_argument('--cx',type=int,default=-8);parser.add_argument('--cz',type=int,default=3);args=parser.parse_args()
cx,cz=args.cx,args.cz;target=R/'pilot/candidate'
def read_slot(world):
    path=world/'region'/('r.%d.%d.mca'%(cx>>5,cz>>5))
    rows=list(c._region_chunks(m,path,{(cx,cz)}));m.require(len(rows)==1,'Exactly one addressed slot required')
    x,z,compression,payload=rows[0]
    return m.compound(m.decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]).root)
source=c._original_chunk(m,db,'.',cx,cz);actual=read_slot(target)
changes,bes=c._content(m,db,'.',cx,cz,source,'stage1_')
def differences(before,after,overrides):
    a=c._sections(m,before);b=c._sections(m,after);pairs=collections.Counter();examples=[];non_static=[];non_default=[]
    for sy in sorted(set(a)|set(b)|{y>>4 for x,y,z in overrides}):
        ap,av=a.get(sy,([m.state_tag(m.AIR)],[0]*4096));bp,bv=b.get(sy,([m.state_tag(m.AIR)],[0]*4096))
        aa=[m.state_json(v) for v in ap];bb=[m.state_json(v) for v in bp]
        for i in range(4096):
            pos=(cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15))
            e=overrides.get(pos,aa[av[i]]);v=bb[bv[i]]
            if e!=v:
                pairs[m.canonical({'expected':e,'actual':v})]+=1
                if len(examples)<20:examples.append({'position':list(pos),'expected':e,'actual':v})
                if not e['registryId'].startswith('bloodborne_dw:static_architecture_'):non_static.append({'position':list(pos),'expected':e,'actual':v})
                if c._comparison_state(m,e)!=c._comparison_state(m,v):non_default.append({'position':list(pos),'expected':e,'actual':v})
    return {'changedCells':sum(pairs.values()),'statePairs':[{'count':n,**json.loads(k)} for k,n in pairs.most_common()],'examples':examples,'nonStaticDifferences':non_static,'nonDefaultDifferences':non_default}
result={'kind':'READ_ONLY_ADDRESSED_PRIOR_STATE_DIFFERENCE','chunk':[cx,cz],'targetAgainstStage1':differences(source,actual,changes),'expectedBesSha256':c._bes_digest(m,bes),'actualBesSha256':c._bes_digest(m,actual.get('block_entities',m.Tag(9,[],10)).value),'persistentDatabaseWrites':0,'mapWrites':0}
original=read_slot(Path(m.meta(db,'binding')['source']))
result['cachedInputAgainstOriginalSource']=differences(source,original,{})
def be_map(tags):
    return {tuple(m.compound(t)[k].value for k in ('x','y','z')):t for t in tags}
expected_bes=be_map(bes);actual_bes=be_map(actual.get('block_entities',m.Tag(9,[],10)).value)
result['blockEntityDifferences']=[]
for pos in sorted(set(expected_bes)|set(actual_bes)):
    e=c._be_value(m,expected_bes[pos]) if pos in expected_bes else None
    a=c._be_value(m,actual_bes[pos]) if pos in actual_bes else None
    if e!=a:result['blockEntityDifferences'].append({'position':list(pos),'expected':e,'actual':a})
m.atomic_json(plan/'context-extension'/('addressed-state-difference-%d-%d.json'%(cx,cz)),result)
print(m.canonical(result))
db.close()
