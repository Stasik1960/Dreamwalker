"""Build explicit, reviewable owners from verified final-document captions.

The input caption index is evidence, not a source of commands. Missing source
variants are declared as gallery choices; no occurrence is converted by an
unverified variant. Existing production and historical owners are retained.
"""
from __future__ import annotations
import argparse, copy, gzip, hashlib, itertools, json, math
from pathlib import Path
from build_city_compat import serial, write_json

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
CITY = RES / 'bloodborne_blocks/city'
FACING = ['north', 'east', 'south', 'west']
ANCHORS = ['canonical'] + ['upper_' + str(i) for i in range(1, 9)]
LABELS = ['Ребристая секция', 'Орнамент без краевых пикселей', 'Колонна с капителью',
          'Металлическая ограда на основании', 'Цельная дверь', 'Центральный декоративный куб',
          'Узкий шпиль', 'Башенный модуль', 'Окно с решёткой', 'Архитектурный простенок',
          'Плоская розетка', 'Проницаемый декор', 'Ставчатое окно', 'Симметричный карниз',
          'Плоская панель', 'Каменный столб ограды', 'Светильник с верхними деталями',
          'Декоративное навершие', 'Приставная лестница', 'Скульптура', 'Овальный барельеф']


def load(p):
    return json.loads(p.read_bytes())


def bounds(mesh):
    v = [v for p in mesh['polygons'] for v in p['vertices']]
    if not v:
        raise ValueError('empty documented mesh')
    return [min(v[i] for v in v) for i in range(3)] + [max(v[i] for v in v) for i in range(3)]


def transform(mesh, fn):
    result = copy.deepcopy(mesh)
    for p in result['polygons']:
        for v in p['vertices']:
            v[:3] = [round(x, 8) for x in fn(*v[:3])]
    return result


def rotated_mesh(mesh, turns):
    return transform(mesh,lambda x,y,z: [((x,z),(1-z,x),(1-x,1-z),(z,1-x))[turns][0],y,((x,z),(1-z,x),(1-x,1-z),(z,1-x))[turns][1]])


def clip(mesh, axis, boundary, greater):
    polygons = []
    for p in mesh['polygons']:
        vertices = p['vertices']
        out = []
        for a, b in zip(vertices[-1:] + vertices[:-1], vertices):
            ain, bin = (a[axis] >= boundary, b[axis] >= boundary) if greater else (a[axis] <= boundary, b[axis] <= boundary)
            if ain != bin:
                t = (boundary - a[axis]) / (b[axis] - a[axis])
                out.append([a[i] + t * (b[i] - a[i]) for i in range(5)])
            if bin:
                out.append(list(b))
        if len(out) >= 3:
            polygons.append({'texture': p['texture'], 'vertices': out})
    return {'polygons': polygons}


def fit(mesh, box):
    old = bounds(mesh)
    return transform(mesh, lambda x,y,z: [box[i] + (([x,y,z][i]-old[i]) / max(old[i+3]-old[i], 1e-6)) * (box[i+3]-box[i]) for i in range(3)])


def geometry(box, *, collision=True, placement='physical'):
    cells = {}
    for x in range(math.floor(box[0]), math.ceil(box[3])):
        for y in range(math.floor(box[1]), math.ceil(box[4])):
            for z in range(math.floor(box[2]), math.ceil(box[5])):
                local = [max(0,box[i]-[x,y,z][i]) for i in range(3)] + [min(1,box[i+3]-[x,y,z][i]) for i in range(3)]
                if any(local[i] >= local[i+3] for i in range(3)):
                    continue
                physical = [local] if collision else []
                place = physical if placement == 'physical' else physical if placement == 'center' and x == z == 0 else []
                cells[f'{x},{y},{z}'] = {'collision': physical, 'outline': [local], 'placement': copy.deepcopy(place)}
    cells.setdefault('0,0,0', {'collision': [], 'outline': [], 'placement': []})
    return {'anchor': [0,0,0], 'render_offset': [0,0,0], 'cells': cells}


def turn_geometry(g, n):
    result = copy.deepcopy(g); result['cells'] = {}
    for k,c in g['cells'].items():
        x,y,z = map(int,k.split(',')); dx,dz = ((x,z),(-z,x),(-x,-z),(z,-x))[n]
        fields = {}
        for field in ('collision','outline','placement'):
            boxes = []
            for a,b,c0,d,e,f in c[field]:
                corners = [((u,v),(1-v,u),(1-u,1-v),(v,1-u))[n] for u in (a,d) for v in (c0,f)]
                boxes.append([min(p[0] for p in corners),b,min(p[1] for p in corners),max(p[0] for p in corners),e,max(p[1] for p in corners)])
            fields[field] = boxes
        result['cells'][f'{dx},{y},{dz}'] = fields
    return result


def shifted(g, height):
    result = copy.deepcopy(g)
    result['anchor'] = [0,-height,0]
    result['render_offset'] = [0,-height,0]
    result['cells'] = {f'{x},{y-height},{z}': c for k,c in result['cells'].items() for x,y,z in [map(int,k.split(','))]}
    result['cells'].setdefault('0,0,0', {'collision': [], 'outline': [], 'placement': []})
    return result


def build(index, supplement=ROOT/'docs/final-document/assembly-evidence.json'):
    rows = load(index)
    definitions = load(CITY/'definitions.json')
    by_id = {d['id']:d for d in definitions['blocks']}
    meshes = load_gz(CITY/'meshes.json.gz')
    owner_meshes = load_gz(CITY/'owner-meshes.json.gz')
    logical_defs = {d['id']:d for d in load(RES/'bloodborne_blocks/logical/definitions.json')['blocks']}
    logical_meshes = load_gz(RES/'bloodborne_blocks/logical/meshes.json.gz')
    geo = load(CITY/'geometry.json')
    definitions['blocks'] = [d for d in definitions['blocks'] if not d.get('document_item')]
    owner_meshes = {k:v for k,v in owner_meshes.items() if not k.startswith('owner_final_')}
    geo['blocks'] = {k:v for k,v in geo['blocks'].items() if not k.startswith('owner_final_')}
    recipes = []
    supplements = load(supplement) if supplement.is_file() else {}
    migration = load(CITY/'migration.json')['states']
    for number in range(1,22):
        group = [r for r in rows if r['number'] == number and r.get('registry_id')]
        origin = list(group[0]['position'])
        if number == 9: origin = [-361,-43,-55]
        if number == 14: origin = [-346,-41,-48]
        if number == 18: origin = [-336,80,-149]
        if str(number) in supplements:
            group = []
            evidence = supplements[str(number)]
            origin = evidence['origin']
            for part in evidence['parts']:
                target = migration[part['state']]
                group.append({'registry_id':target['id'], 'position':[origin[i]+part['offset'][i] for i in range(3)],
                              'city_state':target, 'exact_page_match':True, 'source_state':part['state'],
                              'file':f'derived-final-{number}-{part["offset"]}'})
        components = []; polygons = []; choices = []
        seen = set()
        for r in group:
            pos = r['position']
            if max(abs(pos[i]-origin[i]) for i in range(3)) > 32:
                separate_id=r['registry_id'].split(':')[1]
                separate_d=by_id.get(separate_id) or logical_defs.get(separate_id)
                separate_state=r.get('city_state') if r.get('exact_page_match') else None
                separate_props=separate_state['properties'] if separate_state and separate_state['id'].split(':')[1]==separate_id else separate_d['default']
                choices.append({'reason':'separate-demonstration-position','caption':r['file'],
                                'sourceComponents':[{'id':'bloodborne_blocks:'+separate_id,'properties':separate_props,'verified':bool(separate_state),'position':pos}]}); continue
            if tuple(pos) in seen: continue
            seen.add(tuple(pos))
            ident = r['registry_id'].split(':')[1]
            d = by_id.get(ident) or logical_defs.get(ident)
            if not d: raise ValueError('caption block is not registered: '+ident)
            state = r.get('city_state') if r.get('exact_page_match') else None
            if state is None or state['id'].split(':')[1] != ident:
                props = d['default']
                choices.append({'reason':'caption-state-not-in-source','caption':r['file'],'id':ident,'candidates':list(d['models'])})
            else: props = state['properties']
            key = ','.join(k+'='+v for k,v in sorted(props.items()))
            mesh_id = d['models'][key]
            mesh = meshes.get(mesh_id) or owner_meshes.get(mesh_id) or logical_meshes.get(mesh_id)
            if not mesh: raise ValueError('missing caption mesh: '+mesh_id)
            delta = [pos[i]-origin[i] for i in range(3)]
            part = transform(mesh,lambda x,y,z: [x+delta[0],y+delta[1],z+delta[2]])
            polygons.extend(part['polygons'])
            components.append({'offset':delta,'id':'bloodborne_blocks:'+ident,'properties':props,'source_state':r.get('source_state'),'verified':r.get('exact_page_match',False),'caption':r['file']})
        mesh = {'polygons':polygons}
        original_bounds = bounds(mesh)
        # Each transform is stated in the recipe and shown alongside its source
        # in the gallery. These provisional art choices do not enlarge physics.
        edits = []
        if number == 2:
            choices.append({'reason':'edge-pixel-selection-unproven-keep-source-art','item':number})
        if number == 20:
            mesh = clip(mesh,1,original_bounds[4]-0.125,False); edits.append('clip_top_two_pixels')
        if number == 7:
            cap = clip(mesh,1,original_bounds[4]-0.125,True)
            mesh = clip(mesh,1,original_bounds[4]-0.125,False)
        if number == 15:
            # Mount orientations use a single planar model. Source composition
            # remains recorded; neighbouring uncaptioned parts need review.
            mesh = fit(mesh,[0,0,0.9375,original_bounds[3],original_bounds[4],1]); edits.append('flatten_mount_panel')
            choices.append({'reason':'unidentified-surrounding-flat-parts','item':number})
        if number == 11:
            mesh = transform(mesh,lambda x,y,z:[x,y,z+1-original_bounds[5]])
            edits.append('nine_verified_flat_parts_wall_flush')
        if number == 9:
            # Keep side decoration visually, with the authored central wall-flush
            # collision. The art box still covers the complete caption group.
            mesh = fit(mesh,[original_bounds[0],original_bounds[1],0.9375,original_bounds[3],original_bounds[4],1]); edits.append('wall_flush_art_keep_xy')
        if number == 13:
            mesh = fit(mesh,[original_bounds[0],original_bounds[1],0.875,original_bounds[3],original_bounds[4],1]); edits.append('wall_flush_window_keep_xy')
        if number == 14:
            side = fit(mesh,[-1,0,1.5625,2,1.5,2])
            mesh = {'polygons':[p for turn in range(4) for p in rotated_mesh(side,turn)['polygons']]}
            edits.append('four_matching_sides_three_by_three')
        if number == 19:
            mesh = fit(mesh,[0,0,0.9375,1,1,1]); edits.append('one_cell_wall_flush_ladder')
        b = bounds(mesh)
        box = [max(0,b[0]),max(0,b[1]),max(0,b[2]),min(1,b[3]),max(0.0625,b[4]),min(1,b[5])]
        if box[3] <= box[0]:box[0],box[3]=0,1
        if box[5] <= box[2]:box[2],box[5]=0,1
        if number == 6: box = [0,0,0,1,1,1]
        if number == 7: box = [0.4375,0,0.4375,0.5625,b[4],0.5625]
        if number == 9: box = [0,0,0.9375,1,3,1]
        if number == 12: box = [0,0,0,1,1,1]
        if number == 13: box = [0,0,0.875,1,2,1]
        if number == 14: box = [-1,0,-1,2,1.5,2]
        if number == 16: box = [0,0,0,1,2,1]
        if number == 18: box = [0,0,0,1,1,1]
        if number == 19: box = [0,0,0.9375,1,1,1]
        if number in (11,15,21):box=b
        if number in (8,16):placement='empty'
        elif number == 14:placement='center'
        else:placement='physical'
        if number in (8,10,13,17,21):choices.append({'reason':'additional-or-historical-art-needs-gallery-review','item':number})
        recipe = {'item':number,'id':f'owner_final_{number:02d}','label':LABELS[number-1], 'origin':origin,
                  'components':components,'edits':edits,'choices':choices,'sourceBounds':original_bounds,
                  'physicalBox':box,'placement':placement,'status':'gallery_review' if choices else 'caption_composition'}
        emit(recipe,mesh,box,placement,definitions,geo,owner_meshes)
        recipes.append(recipe)
        if number == 3:
            # The bracket is a separate noncolliding rotatable decorative item;
            # selecting its exact polygons remains a visible gallery dispute.
            bracket = clip(mesh,1,max(b[1],b[4]-1),True)
            bracket_min_y=bounds(bracket)[1]
            bracket=transform(bracket,lambda x,y,z:[x,y-bracket_min_y,z])
            br = {**recipe,'id':'owner_final_03_bracket','label':'Кронштейн фонаря', 'components':[],
                  'choices':[{'reason':'bracket-polygon-selection-review'}], 'status':'gallery_review'}
            emit(br,bracket,bounds(bracket),'empty',definitions,geo,owner_meshes,collision=False)
            recipes.append(br)
        if number == 7:
            cr = {**recipe,'id':'owner_final_07_cap','label':'Верхние два пикселя шпиля','components':[], 'status':'caption_composition'}
            emit(cr,cap,bounds(cap),'physical',definitions,geo,owner_meshes)
            recipes.append(cr)
    write_json(CITY/'definitions.json',definitions);write_json(CITY/'geometry.json',geo)
    (CITY/'owner-meshes.json.gz').write_bytes(gzip.compress(serial(owner_meshes),mtime=0))
    write_json(CITY/'document-final.json',{'schemaVersion':1,'sourceIndexSha256':hashlib.sha256(index.read_bytes()).hexdigest(),'objects':recipes})
    print(json.dumps({'documentOwners':len(recipes),'reviewChoices':sum(len(r['choices']) for r in recipes)}))
    return recipes


def emit(recipe,mesh,box,placement,definitions,geo,owner_meshes,collision=True):
    number,ident = recipe['item'],recipe['id']
    props = {'facing':FACING,'root_anchor':ANCHORS}
    if number in (5,13) and not ident.endswith(('_cap','_bracket')):props['open']=['false','true']
    if number in (11,15):props['face']=['floor','wall','ceiling']
    defaults = {k:v[0] for k,v in props.items()}
    if 'face' in props:defaults['face']='wall'
    definition = {'id':ident,'source':'minecraft:stone','city_compat':True,'logical':False,'whole_owner':True,
                  'document_item':number,'modular':True,'extra_facing':True,'offset':'none','kind':'model_door' if 'open' in props else 'generic',
                  'semantic':'ladder' if number==19 else 'window' if number in (9,13) else 'column' if number in (1,3,7,16) else 'trim',
                  'custom_geometry':True,'full_cube':False,'layer':'cutout','hardness':1.5,'resistance':6,
                  'slipperiness':0.6,'velocity':1,'jump':1,'emissive':False,'animated':False,'orphan':False,'creative':True,
                  'properties':props,'default':defaults,'states':{},'models':{}}
    geo['blocks'][ident]={'states':{}}
    textures=sorted({p['texture'] for p in mesh['polygons']})
    variants={}
    for values in itertools.product(*(props[k] for k in sorted(props))):
        state=dict(zip(sorted(props),values));key=','.join(k+'='+v for k,v in sorted(state.items()))
        turn=FACING.index(state['facing']);opened=state.get('open')=='true';face=state.get('face','wall')
        variant=f'{face}_{"open" if opened else "closed"}_{state["facing"]}'
        model=ident+'_'+variant
        if model not in owner_meshes:
            m=copy.deepcopy(mesh)
            if opened:
                # Rotate around the left jamb; exact hinge convention is visible
                # in the approval gallery before any artist correction.
                m=transform(m,lambda x,y,z:[1-z,y,x])
            if face=='floor':m=transform(m,lambda x,y,z:[x,z-0.4375,y])
            elif face=='ceiling':m=transform(m,lambda x,y,z:[x,1.4375-z,y])
            owner_meshes[model]=rotated_mesh(m,turn)
        b=box
        if opened:b=[1-box[5],box[1],box[0],1-box[2],box[4],box[3]]
        if face=='floor':b=[b[0],b[2]-0.4375,b[1],b[3],b[5]-0.4375,b[4]]
        elif face=='ceiling':b=[b[0],1.4375-b[5],b[1],b[3],1.4375-b[2],b[4]]
        g=turn_geometry(geometry(b,collision=collision and number not in (11,12,15),placement=placement),turn)
        height=0 if state['root_anchor']=='canonical' else int(state['root_anchor'].split('_')[1])
        geo['blocks'][ident]['states'][key]=shifted(g,height)
        definition['models'][key]=model;definition['states'][key]=[0,0,0]
        variants[key]={'model':'bloodborne_blocks:block/city/'+ident}
    definitions['blocks'].append(definition)
    texture_slots={str(i):t for i,t in enumerate(textures)};texture_slots['particle']=textures[0]
    write_json(RES/f'assets/bloodborne_blocks/models/block/city/{ident}.json',{'parent':'minecraft:block/block','textures':texture_slots,'elements':[]})
    write_json(RES/f'assets/bloodborne_blocks/models/item/{ident}.json',{'parent':'bloodborne_blocks:block/city/'+ident})
    write_json(RES/f'assets/bloodborne_blocks/blockstates/{ident}.json',{'variants':variants})
    write_json(RES/f'data/bloodborne_blocks/loot_tables/blocks/{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'bloodborne_blocks:'+ident}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    lang_path=RES/'assets/bloodborne_blocks/lang/ru_ru.json';lang=load(lang_path)
    lang['block.bloodborne_blocks.'+ident]=recipe['label'];write_json(lang_path,lang)


def load_gz(p):
    return json.loads(gzip.decompress(p.read_bytes()))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('index',type=Path)
    build(p.parse_args().index)
