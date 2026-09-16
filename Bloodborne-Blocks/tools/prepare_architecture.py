"""One-time 1.1 migration of palette semantics, door leaves and new placement variants.

Legacy states keep their authored origin. assembled=true is used only for new
placements, so existing cities do not silently move when the jar is replaced.
"""
from catalog_geometry import *
import hashlib

DOORS={'acacia_stairs','birch_stairs','dark_oak_stairs'}
NATIVE_STAIRS={'end_stone_brick_stairs','polished_andesite_stairs','prismarine_stairs','andesite_stairs','brick_stairs','cobbled_deepslate_stairs','cobblestone_stairs','deepslate_brick_stairs','deepslate_tile_stairs','granite_stairs','mossy_cobblestone_stairs','polished_deepslate_stairs','polished_diorite_stairs','stone_brick_stairs','stone_stairs'}
SOFT={'acacia_log','birch_log','dark_oak_log','jungle_log','oak_log','spruce_log','stripped_birch_wood','cyan_wool','light_blue_wool','lime_wool','magenta_wool','pink_wool','yellow_wool','asset_e','dandelion'}

def dump(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
def key(state):return ','.join(f'{k}={v}' for k,v in sorted(state.items()))

SHIFT_CACHE={}
def shift_model(name,delta,suffix):
    cachekey=(name,tuple(np.round(delta,6)))
    if cachekey in SHIFT_CACHE:return SHIFT_CACHE[cachekey]
    md=copy.deepcopy(model(name));md.pop('parent',None);md.pop('display',None)
    md['bloodborne_offset']=[round(float(v),6) for v in delta]
    dest='block/placed/'+hashlib.sha256(repr(cachekey).encode()).hexdigest()[:20]
    dump(ASSETS/'models'/(dest+'.json'),md)
    SHIFT_CACHE[cachekey]='bloodborne_blocks:'+dest
    return SHIFT_CACHE[cachekey]

def open_door(name):
    md=copy.deepcopy(model(name));md.pop('parent',None);result=[]
    for e in md['elements']:
        # Full-width panel becomes two leaves. Narrow jambs stay in place.
        if e['to'][0]-e['from'][0]<32:result.append(e);continue
        original_start,original_end=e['from'][0],e['to'][0]
        frame=6 if 'aca_door' in name or 'bir_door' in name else 0
        start,end=original_start+frame,original_end-frame;mid=(start+end)/2
        def slice_panel(low,high):
            part=copy.deepcopy(e);part['from'][0]=low;part['to'][0]=high
            a=(low-original_start)/(original_end-original_start);b=(high-original_start)/(original_end-original_start)
            for side,face in part['faces'].items():
                if side in ('north','south','up','down') and 'uv' in face:
                    u,v,U,V=face['uv'];face['uv']=[u+(U-u)*a,v,u+(U-u)*b,V]
                face.pop('cullface',None)
            return part
        if frame:
            result.extend([slice_panel(original_start,start),slice_panel(end,original_end)])
        for left in (True,False):
            lo=start if left else mid;hi=mid if left else end;n=slice_panel(lo,hi)
            inset=(e['to'][2]-e['from'][2])/2
            hinge=np.array([start+inset if left else end-inset,0,(e['from'][2]+e['to'][2])/2])
            angle=-90 if left else 90;rot=rotation(1,angle)
            pts=np.array(list(itertools.product(*zip(n['from'],n['to']))))
            pts=(pts-hinge)@rot.T+hinge;n['from']=pts.min(axis=0).round(6).tolist();n['to']=pts.max(axis=0).round(6).tolist();n.pop('rotation',None)
            face_map={'north':'east','east':'south','south':'west','west':'north'} if left else {'north':'west','west':'south','south':'east','east':'north'}
            n['faces']={face_map.get(s,s):f for s,f in n['faces'].items()}
            for s in ('up','down'):
                if s in n['faces']:n['faces'][s]['rotation']=(n['faces'][s].get('rotation',0)+(90 if left else 270))%360
            result.append(n)
    md['elements']=result;dest='block/functional/'+name.split(':')[1].replace('/','_')+'_open';dump(ASSETS/'models'/(dest+'.json'),md)
    return 'bloodborne_blocks:'+dest

def prepare():
    if DATA.get('architecture_revision')==1:raise SystemExit('Already migrated; use committed resources as generator inputs.')
    policies={}
    for b in DATA['blocks']:
        ident=b['id'];path=ASSETS/'blockstates'/f'{ident}.json';bs=json.loads(path.read_text());oldkind=b['kind'];apps=default_apps(b)
        points=np.array([p for a in apps for e in elements(a) for p in corners(e,a)])
        lo=points.min(axis=0) if len(points) else np.zeros(3);hi=points.max(axis=0) if len(points) else np.ones(3)
        if ident in DOORS:b['kind']='model_door'
        elif oldkind=='stairs' and ident not in NATIVE_STAIRS:b['kind']='generic'
        elif oldkind in ('fence','wall','pane','gate','door'):b['kind']='generic'
        elif oldkind=='trapdoor' and ident not in ('bamboo_trapdoor','cherry_trapdoor','iron_trapdoor','mangrove_trapdoor'):b['kind']='generic'
        elif oldkind=='slab' and (np.max(np.abs(lo))>1e-6 or not np.allclose(hi,[1,.5,1])):b['kind']='generic'
        # A random positional offset cannot be shared reliably by collision helper cells.
        b['offset']='none'
        if ident in DOORS:
            b['properties']['open']=['false','true'];b['default']['open']='false'
            b['states']={key({**dict(x.split('=') for x in k.split(',') if x),'open':value}):v for k,v in b['states'].items() for value in ('false','true')}
            vs={}
            for k,v in bs['variants'].items():
                vs[k+',open=false']=v;opened=copy.deepcopy(v)
                choices=opened if isinstance(opened,list) else [opened]
                if 'shape=straight' in k:
                    for a in choices:a['model']=open_door(a['model'])
                vs[k+',open=true']=opened
            bs['variants']=vs
        assembled='variants' in bs and b['kind'] in ('generic','model_door','plant','pillar') and (np.max(np.abs(lo))>1e-5 or np.max(hi)>1.00001)
        if assembled:
            b['properties']['assembled']=['false','true'];b['default']['assembled']='false'
            oldstates=b['states'];b['states']={};newvariants={}
            for oldkey,value in oldstates.items():
                state=dict(x.split('=') for x in oldkey.split(',') if x)
                closed={**state,'open':'false'} if ident in DOORS else state
                groups=applications(bs,closed)
                pts=[p for choices in groups for a in choices for e in elements(a) for p in corners(e,a)]
                delta=-np.min(pts,axis=0) if pts else np.zeros(3)
                for placed in ('false','true'):
                    k=key({**state,'assembled':placed});b['states'][k]=value
                    # Flatten multipart applications into one generated model. This preserves all matching parts.
                    if placed=='false':continue
                    merged={'textures':{},'elements':[]};index=0
                    for choices in applications(bs,state):
                        # Geometry-changing random variants are stabilized during collision generation.
                        a=choices[0];md=model(a['model']);variant=rotation(1,-a.get('y',0))@rotation(0,-a.get('x',0))
                        localdelta=variant.T@delta
                        suffix=ident+'/'+str(list(oldstates).index(oldkey))+'_'+str(index)
                        shifted=shift_model(a['model'],localdelta,suffix)
                        newvariants[k]={**a,'model':shifted} if index==0 else newvariants[k]
                        index+=1
                    # Generic pane/fence multipart models stay in their legacy coordinates:
                    # they are a collection of authored pieces, not one movable prop.
                    if index>1:raise ValueError('Multipart assembly must be preserved: '+ident)
            # Append assembled to all existing conditions for legacy rendering.
            if 'multipart' in bs:
                # Multi-part blocks are kept using BlockItem anchor offsets instead of shifting resources.
                b['states']=oldstates;b['properties'].pop('assembled');b['default'].pop('assembled');assembled=False
            else:
                oldvs=bs['variants'];bs['variants']={k+(',assembled=false' if k else 'assembled=false'):v for k,v in oldvs.items()}
                bs['variants'].update(newvariants)
        dump(path,bs)
        policies[ident]={'behavior':b['kind'],'previous_behavior':oldkind,'collision':'none' if ident in SOFT or b['kind']=='plant' else 'model','assembled':bool(assembled),'review':'visual_catalog_and_all_state_geometry'}
    DATA['architecture_revision']=1
    dump(RES/'bloodborne_blocks/definitions.json',DATA);dump(ROOT/'docs/block-policies.json',policies)
    print('Prepared semantics and placement for',len(policies),'blocks')

if __name__=='__main__':prepare()
