"""1.2 resource migration. Preserve legacy visuals/IDs, add horizontal placement.

Never modifies a save. Re-running is safe; do not run prepare_architecture.py.
"""
from catalog_geometry import *
import hashlib

def dump(p, value):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':'))+'\n', encoding='utf-8')

def key(state):
    return ','.join(f'{k}={v}' for k,v in sorted(state.items()))

SOFT = {'acacia_log','birch_log','dark_oak_log','jungle_log','oak_log','spruce_log',
        'stripped_birch_wood','cyan_wool','light_blue_wool','lime_wool','magenta_wool',
        'orange_wool','pink_wool','yellow_wool','asset_e','dandelion','dead_fire_coral_fan',
        'potted_dead_bush','potted_azalea_bush','potted_flowering_azalea_bush',
        'potted_birch_sapling','potted_dark_oak_sapling','potted_jungle_sapling','potted_oak_sapling'}
PASSAGES = {'acacia_stairs','birch_stairs','dark_oak_stairs','acacia_wood',
            'deepslate_iron_ore','smooth_quartz_stairs','lime_glazed_terracotta',
            'asset_lime_glazed_terracotta','emerald_block','terracotta','tinted_glass',
            'nether_brick_stairs','waxed_exposed_cut_copper_stairs'}

def rotate_app(app, angle, normalized):
    app=copy.deepcopy(app)
    app['y']=(app.get('y',0)+angle)%360
    if normalized:
        points=[p for e in elements(app) for p in corners(e,app)]
        delta=-np.min(points,axis=0) if points else np.zeros(3)
        if np.max(np.abs(delta))>1e-6:
            md=copy.deepcopy(model(app['model']));md.pop('parent',None)
            matrix=rotation(1,-app.get('y',0))@rotation(0,-app.get('x',0))
            md['bloodborne_offset']=np.round(np.array(md.get('bloodborne_offset',[0,0,0]))+matrix.T@delta,6).tolist()
            name='block/rotatable/'+hashlib.sha256(json.dumps(md,sort_keys=True).encode()).hexdigest()[:20]
            dump(ASSETS/'models'/(name+'.json'),md)
            app['model']='bloodborne_blocks:'+name
    return app

def main():
    policies=json.loads((ROOT/'docs/block-policies.json').read_text())
    rotated=[]
    for b in DATA['blocks']:
        ident=b['id'];p=policies[ident]
        # Coral palette IDs also contain barrels, lanterns and books. Only the
        # reviewed vegetation models are pass-through, never every coral ID.
        p['collision']='none' if ident in SOFT or p['collision']=='none' else ('coarse' if ident in PASSAGES else 'box')
        if b['kind'] in ('stairs','slab','door','model_door','trapdoor','gate'):p['collision']='coarse'
        p['outline']='box'
        if ident=='spruce_button':p['removed']=True
        p['review']='1.2_visual_catalog_simple_physics'
        if 'facing' in b['properties'] or 'axis' in b['properties'] or b['kind']!='generic':continue
        path=ASSETS/'blockstates'/f'{ident}.json';bs=json.loads(path.read_text())
        b['properties']['facing']=['north','east','south','west'];b['default']['facing']='north'
        b['extra_facing']=True
        newstates={}
        for oldkey,value in b['states'].items():
            old=dict(v.split('=') for v in oldkey.split(',') if v)
            for facing in b['properties']['facing']:newstates[key({**old,'facing':facing})]=value
        b['states']=newstates
        if 'variants' in bs:
            result={}
            for oldkey,apps in bs['variants'].items():
                old=dict(v.split('=') for v in oldkey.split(',') if v)
                for i,facing in enumerate(b['properties']['facing']):
                    choices=apps if isinstance(apps,list) else [apps]
                    values=[rotate_app(a,i*90,old.get('assembled')=='true') for a in choices]
                    result[key({**old,'facing':facing})]=values if isinstance(apps,list) else values[0]
            bs['variants']=result
        else:
            result=[]
            for part in bs['multipart']:
                for i,facing in enumerate(b['properties']['facing']):
                    n=copy.deepcopy(part);condition=n.get('when')
                    n['when']={'AND':[condition,{'facing':facing}]} if condition else {'facing':facing}
                    apps=n['apply'];choices=apps if isinstance(apps,list) else [apps]
                    values=[rotate_app(a,i*90,False) for a in choices]
                    n['apply']=values if isinstance(apps,list) else values[0];result.append(n)
            bs['multipart']=result
        dump(path,bs);rotated.append(ident)
    DATA['architecture_revision']=2
    dump(RES/'bloodborne_blocks/definitions.json',DATA)
    dump(ROOT/'docs/block-policies.json',policies)
    report=ROOT/'docs/rotatable-blocks.json'
    if rotated:dump(report,rotated)
    print('Added rotation to',len(rotated),'blocks; policy pass covers',len(policies),'IDs')

if __name__=='__main__':main()
