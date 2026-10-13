"""Register the new exact-source tree's already authored simple physics table."""
import copy,json,math,itertools
from v125_map_index import ROOT
from v127_apply_full_map_poses import write
R=ROOT/'src/architecture/resources/bloodborne_dw'
d=json.loads((R/'composite/source_original_tree.json').read_text(encoding='utf-8-sig'))
p=json.loads((R/'imported-full-map-v127.json').read_text(encoding='utf-8-sig'))
reg=d['id'];p['additionalRegistryIds']=[reg]
p['objects']=[r for r in p['objects'] if r['registryId']!=reg]
for index,v in enumerate(d['variants']):
    rotations=[]
    for yaw in range(8):
        values={};a=math.pi*yaw/4;c=math.cos(a);s=math.sin(a)
        for out,source in [('collision','collision'),('aim','selection')]:
            boxes=[]
            for b in v['poses']['closed'][source]:
                points=[]
                for x,y,z in itertools.product(*[(b['from'][i],b['to'][i]) for i in range(3)]):
                    x-=8;z-=8;points.append([x*c-z*s+8,y,x*s+z*c+8])
                boxes.append({'from':[round(min(q[i] for q in points),8) for i in range(3)],'to':[round(max(q[i] for q in points),8) for i in range(3)]})
            values[out]=boxes
        rotations.append(values)
    p['objects'].append({'registryId':reg,'variant':index,'rotations':rotations,'sourceVariant':v['sourceVariant']})
write(R/'imported-full-map-v127.json',p)
print('Registered 13 new source tree collision states; historical tables untouched.')
