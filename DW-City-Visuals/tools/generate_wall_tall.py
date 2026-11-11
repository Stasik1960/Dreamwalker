"""Generate the canonical LOW and TALL poses used by the vanilla wall rules."""
from copy import deepcopy
from itertools import product
import json
from pathlib import Path

SMALL_WALLS = {
    "source_assembly_cobbled_deepslate_wall",
    "source_assembly_granite_wall",
    "source_assembly_end_stone_brick_wall",
}

MASKS = ((0,0,0,0),(1,0,0,0),(1,0,1,0),(1,1,0,0),(1,1,1,0),(1,1,1,1))

def rotate(s, n=1):
    return s[-n:] + s[:-n] if n else s

def canonical(s):
    return min(rotate(s, n) for n in range(4))

def states():
    sides = sorted({canonical(s) for s in product(range(3), repeat=4)})
    return [(s, True) for s in sides] + [(s, False) for s in sides
        if (sum(v != 0 for v in s) == 2 and ((s[0] != 0 and s[2] != 0) or (s[1] != 0 and s[3] != 0)))
        or all(s)]

def boxes(sides, post, selection=False):
    height = 16 if selection else 24
    result = []
    if post:
        result.append({'from': [4, 0, 4], 'to': [12, height, 12]})
    active = list(sides)
    if not selection:
        # Both wall arm heights share a movement collider. Joining opposite arms
        # keeps exactly the same union within the spec's three-box budget.
        if active[0] and active[2]:
            result.append({'from': [5, 0, 0], 'to': [11, height, 16]})
            active[0] = active[2] = 0
        if active[1] and active[3]:
            result.append({'from': [0, 0, 5], 'to': [16, height, 11]})
            active[1] = active[3] = 0
    for i, arm in enumerate(active):
        if not arm:
            continue
        top = (16 if arm == 2 else 14) if selection else height
        bounds = [([5, 0, 0], [11, top, 11]), ([5, 0, 5], [16, top, 11]),
                  ([5, 0, 5], [11, top, 16]), ([0, 0, 5], [11, top, 11])][i]
        result.append({'from': bounds[0], 'to': bounds[1]})
    if not selection and len(result) > 3:
        raise ValueError(f'Wall movement shape exceeds the collision budget: {sides}, post={post}')
    return result


def generate(base, resources, profile_resources):
    resources, profile_resources = Path(resources), Path(profile_resources)
    def read(path):
        for root in (resources,profile_resources):
            p = root/path
            if p.exists(): return json.loads(p.read_text(encoding='utf8'))
        return json.loads(base.read(path))
    def write(path, value):
        p=resources/path; p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(json.dumps(value,ensure_ascii=False,separators=(',',':')),encoding='utf8')
    mapping=read('bloodborne_dw/stone-fence-connections.json')
    compact=read('bloodborne_dw/imported-compact-v127.json')
    entries={x['registryId']:x for x in compact['types']}
    all_states=states()
    assert len(all_states)==33
    for family,info in mapping['families'].items():
        groups=[g for g in info['groups'] if family.endswith('_wall') or g['post'].endswith('_wall_post')]
        if not groups: continue
        name=family.split(':',1)[1]
        sp='bloodborne_dw/composite/'+name+'.json'
        bs='assets/bloodborne_dw/blockstates/'+name+'.json'
        if name in ("source_assembly_brick_wall", "source_andesite_slab_top", "source_cut_copper"):
            spec, blockstate = read(sp), read(bs)  # generated fresh by generate_wall_straights
        else:
            spec = json.loads((profile_resources/sp).read_text(encoding='utf8'))
            blockstate = json.loads(base.read(bs))
        if name in SMALL_WALLS:
            # The original poses had no art_bank. Scope them to bank 0 so they
            # cannot render again over a newly generated pose in another bank.
            for part in blockstate['multipart']:
                when = part.get('when', {})
                if 'variant' in when:
                    when.setdefault('art_bank', '0')
        entry = entries[family]
        for group in groups:
            postpart=deepcopy(spec['variants'][group['poses'][0]]['poses']['closed']['parts'][0])
            template=spec['variants'][group['poses'][1]]['poses']['closed']['parts']
            sidepart=next(deepcopy(x) for x in template if x['model'] != postpart['model'])
            tallpart=deepcopy(sidepart)
            tallpart['model']=tallpart['model'].replace('_side','_side_tall')
            key='assets/bloodborne_dw/models/'+tallpart['model'].split(':',1)[1]+'.json'
            if key not in base.namelist() and not (resources/key).exists() and not (profile_resources/key).exists():
                raise ValueError('missing tall model '+key)
            variants=[]
            for sides,up in all_states:
                # Reuse the existing six LOW forms and existing straight pose.
                chosen=None
                base_turn=0
                for i,mask in enumerate(MASKS):
                    if up and sides==canonical(mask):
                        chosen=group['poses'][i]; base_turn=next(t for t in range(4) if rotate(mask,t)==sides); break
                if not up and sides==canonical(MASKS[2]):
                    chosen=group.get('straight'); base_turn=next(t for t in range(4) if rotate(MASKS[2],t)==sides)
                if chosen is None:
                    chosen=len(spec['variants'])
                    variant=deepcopy(spec['variants'][group['poses'][0]])
                    geo=variant['poses']['closed']
                    parts=([deepcopy(postpart)] if up else [])
                    for i,v in enumerate(sides):
                        if v:
                            piece=deepcopy(tallpart if v==2 else sidepart)
                            piece['yaw']=i*90
                            parts.append(piece)
                    geo['parts']=parts;geo['collision']=boxes(sides,up);geo['selection']=boxes(sides,up,True)
                    geo['simpleCollisionByRotation']=[boxes(rotate(sides,k//2),up) for k in range(8)]
                    geo['simpleSelectionByRotation']=[boxes(rotate(sides,k//2),up,True) for k in range(8)]
                    spec['variants'].append(variant)
                    entry['poses'].append(chosen)
                    entry['shapes'][str(chosen)]=[{'collision':boxes(rotate(sides,k//2),up),
                        'aim':boxes(rotate(sides,k//2),up,True)} for k in range(8)]
                    for turn in range(4):
                        when={'compact':'true','variant':str(chosen%16),'rotation':str(turn*2),'art_bank':str(chosen//16)}
                        for piece in parts:
                            apply={'model':piece['model'],'y':(piece.get('yaw',0)+turn*90)%360,'uvlock':True}
                            blockstate['multipart'].append({'when':deepcopy(when),'apply':apply})
                variants.append({'sides':list(sides),'up':up,'pose':chosen,'baseTurn':base_turn})
            group['wallStates']=variants
        write(sp,spec);write(bs,blockstate)
    write('bloodborne_dw/stone-fence-connections.json',mapping)
    write('bloodborne_dw/imported-compact-v127.json',compact)
