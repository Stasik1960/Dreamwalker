"""Addressed read-only admission diagnostic, never writes maps or SQLite."""
import collections,json
from pathlib import Path
import migrate as m
import context_stage as c

plan=m.ROOT/'research/native-grid-plan'
db=c.attached_readonly(m,plan)
cx,cz=-8,3
source,rel,bound=m.get_source_chunk(db,'.',cx,cz);source=m.compound(source.root)
target=Path(m.meta(db,'target'))/'region'/f'r.{cx>>5}.{cz>>5}.mca'
_,_,compression,payload=next(c._region_chunks(m,target,{(cx,cz)}))
actual=m.compound(m.decode_nbt(payload,compressed={1:'gzip',2:'zlib',3:None}[compression]).root)
changes,bes=c._content(m,db,'.',cx,cz,source,'stage1_')
ss=c._sections(m,source);aa=c._sections(m,actual);counts=collections.Counter();examples={}
for sy in sorted(set(ss)|set(aa)|{v[1]>>4 for v in changes}):
    for i in range(4096):
        at=(cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15))
        expected=changes.get(at,m.source_state(ss,at));observed=m.source_state(aa,at)
        if expected!=observed:
            pair=(m.canonical(expected),m.canonical(observed));counts[pair]+=1
            if len(examples.setdefault(pair,[]))<10:examples[pair].append(list(at))
report={'schemaVersion':1,'status':'READ_ONLY_DIFF','chunk':['.',cx,cz],'sourceRegion':rel,'boundSourceRegionSha256':bound,'targetRegion':str(target),'targetRegionSha256':m.sha(target),'statesExpected':c._states_digest(m,ss,changes),'statesActual':c._states_digest(m,aa),'expectedBlockEntitiesSha256':c._bes_digest(m,bes),'actualBlockEntitiesSha256':c._bes_digest(m,actual.get('block_entities',m.Tag(9,[],10)).value),'stage1Placements':len(changes),'differingCells':sum(counts.values()),'pairs':[{'expected':json.loads(e),'actual':json.loads(a),'count':n,'examples':examples[(e,a)]} for (e,a),n in counts.most_common()],'sourceMetadata':{k:source[k].value for k in ('DataVersion','Status') if k in source},'actualMetadata':{k:actual[k].value for k in ('DataVersion','Status') if k in actual},'mapWrites':0}
out=plan/'context-extension/bind-state-diff--8-3.json';m.atomic_json(out,report)
print(json.dumps({'output':str(out),'differingCells':report['differingCells'],'pairs':report['pairs'][:10],'expectedBlockEntitiesSha256':report['expectedBlockEntitiesSha256'],'actualBlockEntitiesSha256':report['actualBlockEntitiesSha256']},ensure_ascii=False))
db.close()
