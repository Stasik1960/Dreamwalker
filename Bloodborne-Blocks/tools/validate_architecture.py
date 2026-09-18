"""Offline resource/placement audit; never starts a Minecraft process."""
from catalog_geometry import *
from normalize_items import vertices
from generate_collision import texname
from render_catalog import texture
import csv

geometry=json.loads((RES/'bloodborne_blocks/geometry.json').read_text())
issues=[];json_count=0;item_count=0;state_count=0
for path in RES.rglob('*.json'):
    json.loads(path.read_text(encoding='utf-8-sig'));json_count+=1
for path in (ASSETS/'models').rglob('*.json'):
    name='bloodborne_blocks:'+path.relative_to(ASSETS/'models').with_suffix('').as_posix()
    md=model(name)
    for e in md.get('elements',[]):
        assert all(math.isfinite(v) and -16<=v<=32 for key in ('from','to') for v in e[key]),path
        assert all(e['from'][a]<=e['to'][a] for a in range(3)),path
        if 'rotation' in e:assert e['rotation']['angle'] in (-45,-22.5,0,22.5,45),path
        # Abstract parents may deliberately leave #texture for their children.
        if path.parent.name=='item':
            for face in e.get('faces',{}).values():texture(texname(md,face['texture']))
    if path.parent.name=='item' and md.get('elements'):
        assert max(math.dist(v,[8,8,8]) for v in vertices(md['elements']))<=7.00001,path
        assert all(k in md.get('display',{}) for k in ('gui','fixed','ground','firstperson_righthand','firstperson_lefthand','thirdperson_righthand','thirdperson_lefthand')),path
        item_count+=1

empty_defaults=[];rows=[]
for b in DATA['blocks']:
    bs=json.loads((ASSETS/'blockstates'/f'{b["id"]}.json').read_text())
    keys=sorted(b['properties'])
    expected={','.join(k+'='+v for k,v in zip(keys,values)) for values in itertools.product(*(b['properties'][k] for k in keys))}
    assert expected==set(b['states']),b['id']
    assert expected==set(geometry['blocks'][b['id']]['states']),b['id']
    for statekey in expected:
        state_count+=1;state=dict(x.split('=') for x in statekey.split(',') if x)
        groups=applications(bs,state)
        if 'variants' in bs:assert len(groups)==1,(b['id'],statekey,len(groups))
        for choices in groups:
            for app in choices:
                md=model(app['model'])
                for e in md.get('elements',[]):
                    for face in e.get('faces',{}).values():texture(texname(md,face['texture']))
        if state.get('assembled')=='true' and state.get('open','false')=='false':
            points=[p for choices in groups for a in choices for e in elements(a) for p in corners(e,a)]
            # Legacy artwork has gained half a model pixel of real depth. Its
            # original mounting plane/anchor stays fixed; allow that thin shell.
            if points:assert np.max(np.abs(np.min(points,axis=0)))<=1/32+1e-5,(b['id'],statekey,np.min(points,axis=0))
    key=','.join(k+'='+v for k,v in sorted(b['default'].items()))
    profile=geometry['profiles'][geometry['blocks'][b['id']]['states'][key]['ref']]
    if not profile['cells']:empty_defaults.append(b['id'])
    rows.append({'id':b['id'],'behavior':b['kind'],'states':len(b['states']),'new_anchor':'assembled' in b['properties'],
                 'default_visible':bool(profile['cells']),'models':' | '.join(a['model'] for a in default_apps(b))})
with (ROOT/'docs/block-audit.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
summary={'blocks':len(DATA['blocks']),'states':state_count,'profiles':len(geometry['profiles']),
         'json_files':json_count,'bounded_item_models':item_count,'hidden_empty_defaults':empty_defaults,
         'minecraft_started':False}
(ROOT/'docs/resource-checks.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary))
