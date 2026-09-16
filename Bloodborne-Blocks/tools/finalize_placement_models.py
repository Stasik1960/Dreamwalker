"""Rebuild placed model metadata without exceeding vanilla JSON coordinate limits."""
from prepare_architecture import *

for name in ['aca_door_1','aca_door_2','bir_door_1','bir_door_2','dark_door_1','dark_door_2']:
    open_door('bloodborne_blocks:block/'+name)
model.cache_clear()
count=0
for b in DATA['blocks']:
    if 'assembled' not in b['properties']:continue
    bs=json.loads((ASSETS/'blockstates'/f'{b["id"]}.json').read_text())
    for statekey in b['states']:
        state=dict(x.split('=') for x in statekey.split(',') if x)
        if state['assembled']!='true':continue
        old={**state,'assembled':'false'};closed={**old,'open':'false'} if b['id'] in DOORS else old
        baseapps=[v[0] for v in applications(bs,closed)]
        pts=[p for a in baseapps for e in elements(a) for p in corners(e,a)]
        delta=-np.min(pts,axis=0) if pts else np.zeros(3)
        original=[v[0] for v in applications(bs,old)];placed=[v[0] for v in applications(bs,state)]
        for a,p in zip(original,placed):
            md=copy.deepcopy(model(a['model']));md.pop('parent',None);md.pop('display',None)
            # Translation invalidates face culling against the root block's neighbours.
            for element in md.get('elements',[]):
                for face in element.get('faces',{}).values():face.pop('cullface',None)
            mat=rotation(1,-a.get('y',0))@rotation(0,-a.get('x',0))
            md['bloodborne_offset']=np.round(mat.T@delta,6).tolist()
            dump(ASSETS/'models'/(p['model'].split(':')[1]+'.json'),md);count+=1
print('Finalized',count,'placed variants using baked offsets.')
