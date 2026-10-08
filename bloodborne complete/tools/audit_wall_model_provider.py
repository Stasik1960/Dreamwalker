"""Audit all per-side wall render recipes against retained native source clauses.

Mixed heights are new behavior. Each selected post/arm is independently projected
to the old uniform recipe and compared to its source model/native bake settings.
Shared40JSON/160native bakes remain unchanged; no multipart selector product.
"""
from pathlib import Path
from itertools import product
from collections import defaultdict
import hashlib
import json

ROOT=Path(__file__).resolve().parents[1]
OLD=ROOT/'reports/WALL_MULTIPART_OOM_HISTORICAL.json'
OUT=ROOT/'reports/WALL_SHARED_MODEL_AUDIT.json'
ASSETS=ROOT/'src/architecture/resources/assets/bloodborne_dw'
ORDER=('north','east','south','west')

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def normalized(apply):
    assert isinstance(apply,dict),'Frozen material has no per-render RNG'
    return apply['model'],apply.get('x',0),apply.get('y',0),apply.get('uvlock',False),apply.get('weight',1)
def proposed(state):
    yaw=int(state['rotation']);quarter,diagonal=divmod(yaw,2);suffix='_diagonal' if diagonal else ''
    prefix='bloodborne_dw:block/wall/'+state['profile']+'/'
    result=[]
    if state['up']=='true':result.append((prefix+'post'+suffix,0,quarter*90,False,1))
    for i,direction in enumerate(ORDER):
        shape=state[direction]
        if shape!='none':result.append((prefix+('low' if shape=='low' else 'tall_'+state['material'])+suffix,0,((quarter+i)%4)*90,shape=='low',1))
    return result
def visual_key(state):
    code=sum(('none','low','tall').index(state[name])*3**i for i,name in enumerate(ORDER))
    art=int(state['material']) if any(state[name]=='tall' for name in ORDER) else 0
    return code+81*(int(state['up']=='true')+2*(int(state['rotation'])+8*(int(state['profile']=='alt')+2*art)))
def main():
    old=json.loads(OLD.read_text(encoding='utf-8'))['multipart'];indexed=defaultdict(list)
    for clause in old:
        when=clause['when'];assert 'OR' not in when and 'AND' not in when
        indexed[when['profile'],when['rotation']].append(clause)
    def native_projection(state):
        result=[]
        blank={**state,**{name:'false' for name in ORDER},'post':'false','course':'low'}
        if state['up']=='true':
            selected={**blank,'post':'true'}
            result.extend(normalized(c['apply']) for c in indexed[state['profile'],state['rotation']] if all(selected[k]==v for k,v in c['when'].items()))
        for name in ORDER:
            if state[name]=='none':continue
            selected={**blank,name:'true','course':state[name]}
            result.extend(normalized(c['apply']) for c in indexed[state['profile'],state['rotation']] if all(selected[k]==v for k,v in c['when'].items()))
        return result
    properties={**{name:['none','low','tall'] for name in ORDER},'up':['false','true'],'waterlogged':['false','true'],'material':list(map(str,range(8))),'rotation':list(map(str,range(8))),'profile':['base','alt'],'connections':['auto','manual']}
    count=0;keys=set();resources=set();bakes=set();mismatches=[];mixed=0
    # Only visual combinations need clause matching; water/mode expand count.
    for values in product(*(values for name,values in properties.items() if name not in ('waterlogged','connections'))):
        names=[name for name in properties if name not in ('waterlogged','connections')]
        state={**dict(zip(names,values)),'waterlogged':'false','connections':'manual'};count+=4
        expected=native_projection(state);actual=proposed(state)
        if expected!=actual:mismatches.append({'state':state,'sourceProjectedParts':expected,'actualParts':actual})
        if any(state[name]=='low' for name in ORDER) and any(state[name]=='tall' for name in ORDER):mixed+=4
        keys.add(visual_key(state))
        for item in actual:resources.add(item[0]);bakes.add(item)
    assert count==82944 and len(keys)==17152 and len(resources)==40 and len(bakes)==160
    old_audit=json.loads((ROOT/'reports/WALL_SHARED_MODEL_AUDIT_V7_HISTORICAL.json').read_text(encoding='utf-8'))
    primitive_files={model:digest(ASSETS/'models'/(model.split(':',1)[1]+'.json')) for model in sorted(resources)}
    assert primitive_files==old_audit['primitiveFilesSha256'],'User wall mechanics correction must not change accepted authored geometry/UV/source materials'
    report={'schema':'dreamwalker-wall-independent-sides-source-selection-audit-v2','status':'PASS_ALL_PER_SIDE_SOURCE_PRIMITIVE_BAKE_SETTINGS_EQUIVALENT' if not mismatches else 'FAIL',
      'scope':'Each selected mixed-height side/post independently projected to retained uniform source selector. Native X/Y, UV lock, order, weights and exact primitive bytes preserved. New gameplay/client run separate.',
      'oldMultipart':{'path':str(OLD.relative_to(ROOT)),'sha256':digest(OLD),'clauseCount':len(old)},
      'registeredPropertyCombinations':count,'mixedLowTallStateCombinations':mixed,'visualKeys':len(keys),'primitiveResourceIds':len(resources),'nativePrimitiveBakeSettings':len(bakes),
      'ignoredVisualProperties':['waterlogged','connections','material only when no TALL side'],'mismatchCount':len(mismatches),'mismatches':mismatches[:10],
      'intrinsicDiagonal':'Exact existing diagonal JSON intrinsic−45 once; provider only adds native quarter turn; no runtime quad transforms','clonedBakedQuads':0,'serverStateSchemaChanged':True,
      'primitiveFilesSha256':primitive_files,'primitiveFilesByteIdenticalToV7':True,'providerJavaSha256':digest(ROOT/'src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/wall/PrototypeWallModels.java'),
      'wallJsonSha256':digest(ASSETS/'blockstates/prototype_wall.json'),'multipartPredicates':0,'actualClientResourceResult':'PENDING_COORDINATED_NEW_4G_NATIVE_CLIENT_RUN',
      'historicalV7ActualClient':'reports/CLIENT_FIRST_SET_MINIMAL_V7.json; historical32768state4608appearance160bake proof is not proof of new82944state17152appearance schema',
      'nativeParentLinkEvidence':{'path':'reports/WALL_PARENT_LINK_NATIVE_PROOF.txt','sha256':digest(ROOT/'reports/WALL_PARENT_LINK_NATIVE_PROOF.txt'),'correction':'Top-level state delegate links shared40dependency JSON parents once per bank;160 quarter/UV bakes remain shared.'}}
    OUT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:report[k] for k in ('status','registeredPropertyCombinations','mixedLowTallStateCombinations','visualKeys','primitiveResourceIds','nativePrimitiveBakeSettings','mismatchCount','primitiveFilesByteIdenticalToV7')}))
    if mismatches:raise SystemExit(1)
if __name__=='__main__':main()
