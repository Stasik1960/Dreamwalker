"""Add the seven source-authored grass alternatives; never regenerate existing art."""
import copy
import gzip
import hashlib
import json
import math
from pathlib import Path

from build_accepted_bush_extension import FACINGS, VISUALS, LOGICAL, ASSETS, ROOT, write_json
from source_assembly_visuals import resource, source_polys
from source_variant_rng import guards_for


def bounds(polygons):
    points = [v for p in polygons for v in p['vertices']]
    return [round(min(p[a] for p in points), 6) for a in range(3)] + [round(max(p[a] for p in points), 6) for a in range(3)]


def add(rows, row):
    previous = [r for r in rows if r['id'] == row['id']]
    if previous and previous != [row]:
        if row['id'] not in {f'o_grass_{n}' for n in range(1,8)}:
            raise ValueError('existing resource differs: ' + row['id'])
        rows[rows.index(previous[0])] = row
    if not previous:
        rows.append(row)


def build():
    names = ('definitions', 'contracts-v2', 'geometry', 'physical-footprints', 'visual-slots', 'production-palette')
    data = {n: json.loads((LOGICAL/(n+'.json')).read_bytes()) for n in names}
    meshes = json.loads(gzip.decompress((LOGICAL/'meshes.json.gz').read_bytes()))
    template = next(b for b in data['definitions']['blocks'] if b['id'] == 'o_grass_0')
    choices = json.loads(resource('minecraft:dead_fire_coral_fan', 'blockstates', 'json'))['variants']['']
    if [c['model'] for c in choices] != [f'minecraft:block/addon/grass_{n}' for n in range(8)]:
        raise ValueError('source grass alternatives changed')
    palette_path = ROOT/'docs/production-logical-palette.json'
    palette = json.loads(palette_path.read_bytes())
    required_path = ROOT/'docs/required-production-families.json'
    required = json.loads(required_path.read_bytes())
    for number in range(1, 8):
        ident = f'o_grass_{number}'
        definition = copy.deepcopy(template)
        definition.update(id=ident, models={}, visual_models={})
        geometry, physical, states = {}, {}, {}
        component = {'id': 'minecraft:dead_fire_coral_fan', 'model_choices': [[[copy.deepcopy(c)] for c in choices]]}
        pattern = {'components': [{'id': component['id'], 'properties': {'waterlogged': 'false'},
                    'offset': [0, 0, 0], 'source_apps': [choices[number]], 'model_choices': component['model_choices']}],
                   'variant_guards': guards_for(component, [choices[number]], [0, 0, 0])}
        for rotation, facing in enumerate(FACINGS):
            polygons, _ = source_polys([{**choices[number], 'y': rotation*90}])
            polygons = [{**p, 'texture': 'bloodborne_blocks:'+p['texture'].split(':', 1)[-1],
                         'vertices': [[round(x, 6) for x in v] for v in p['vertices']]} for p in polygons]
            for polygon in polygons:
                texture = ASSETS/'textures'/(polygon['texture'].split(':', 1)[1]+'.png')
                if not texture.is_file():
                    raise ValueError('missing source texture: '+str(texture))
            box = bounds(polygons)
            # Some source sprites cross the support plane. The user accepted
            # approximate restoration; retain every vertex/UV and lift as one
            # object instead of reserving a helper inside the floor block.
            lift = max(0, -box[1])
            if lift:
                for polygon in polygons:
                    for vertex in polygon['vertices']:
                        vertex[1] = round(vertex[1]+lift, 6)
                box = bounds(polygons)
            mesh = ident+'_'+hashlib.sha256(json.dumps(polygons, sort_keys=True).encode()).hexdigest()[:20]
            if mesh in meshes and meshes[mesh] != {'polygons': polygons}:
                raise ValueError('mesh collision')
            meshes[mesh] = {'polygons': polygons}
            selection_box = [round(v,4) for v in box]
            cells = {}
            for x in range(math.floor(box[0]), math.ceil(box[3])):
                for y in range(math.floor(box[1]), math.ceil(box[4])):
                    for z in range(math.floor(box[2]), math.ceil(box[5])):
                        p = (x, y, z)
                        clipped = [max(selection_box[a]-p[a], 0) for a in range(3)]+[min(selection_box[a+3]-p[a], 1) for a in range(3)]
                        cells[','.join(map(str,p))] = {'collision': [], 'outline': [clipped]}
            cells.setdefault('0,0,0', {'collision': [], 'outline': []})
            cell_list = [list(map(int, k.split(','))) for k in cells]
            for visual in VISUALS:
                key = f'facing={facing},visual={visual}'
                model = f'bloodborne_blocks:block/logical/{ident}/{visual}/facing_{facing}'
                definition['models'][key] = mesh
                definition['visual_models'][key] = model
                geometry[key] = {'anchor': [0,0,0], 'render_offset': [0,0,0], 'cells': copy.deepcopy(cells)}
                physical[key] = {'cells': cell_list, 'boxes': []}
                states[key] = {'rotation': rotation*90, 'render_mesh': {'id':mesh, 'bounds':box, 'offset':[0,0,0]},
                               'selection_footprint': {'boxes':[selection_box]}, 'collision_footprint': {'boxes':[]},
                               'interaction_footprint': {'cells':cell_list},
                               'migration_source_pattern': [pattern] if facing == 'north' and visual == 'base' else []}
                textures = sorted({p['texture'] for p in polygons})
                value = {'parent':'minecraft:block/block', 'bloodborne_mesh':mesh,
                         'bloodborne_texture_slots':{t:'#t'+str(i) for i,t in enumerate(textures)},
                         'textures':{'particle':textures[0], **{'t'+str(i):t for i,t in enumerate(textures)}}}
                if visual == 'alt':
                    value = {'parent':model.replace('/alt/', '/base/')}
                write_json(ASSETS/'models'/(model.split(':',1)[1]+'.json'), value)
        contract = {'id':ident, 'canonical_anchor':{'cell':[0,0,0], 'pivot':[.5,0,.5]},
                    'source_art_adjustment':{'ground_lift':lift, 'reason':'user-approved approximate restoration; preserve floor'},
                    'collision_policy':'NONE', 'placement_policy':'FLOOR', 'mirror_policy':'ROTATE_ONLY',
                    'rotations':[0,90,180,270], 'states':states}
        add(data['definitions']['blocks'], definition)
        add(data['contracts-v2']['families'], contract)
        data['geometry']['blocks'][ident] = {'states':geometry}
        data['physical-footprints']['families'][ident] = physical
        add(data['visual-slots']['families'], {'id':ident, 'states':8, 'base_states':4})
        add(data['production-palette']['objects'], {'id':ident, 'semantic_label':f'Authored grass {number}',
            'source_reviews':['whole-models user-approved approximate restoration 2026-09-29']})
        write_json(ASSETS/f'blockstates/{ident}.json', {'variants':{k:{'model':v} for k,v in definition['visual_models'].items()}})
        item_bounds = states['facing=north,visual=base']['render_mesh']['bounds']
        span = max(item_bounds[a+3]-item_bounds[a] for a in range(3))
        scale = round(min(1,1.35/span),5)
        write_json(ASSETS/f'models/item/{ident}.json', {'parent':definition['visual_models']['facing=north,visual=base'],
            'display':{'gui':{'scale':[scale]*3,'translation':[0,0,0]}}})
        write_json(ROOT/f'src/main/resources/data/bloodborne_blocks/loot_tables/blocks/{ident}.json',
                   {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'bloodborne_blocks:'+ident}]}]})
        for locale in ('en_us', 'ru_ru'):
            path = ASSETS/f'lang/{locale}.json'
            language = json.loads(path.read_bytes())
            language['block.bloodborne_blocks.'+ident] = f'Authored grass {number}' if locale == 'en_us' else f'Авторская трава {number}'
            write_json(path, language)
        if ident not in required['retained_reviewed_ids']:
            required['retained_reviewed_ids'].append(ident)
        add(palette['objects'], {'id':ident, 'status':'PRODUCTION', 'semantic_label':f'Authored grass {number}',
            'source_reviews':['whole-models 2026-09-29'], 'provenance':[choices[number]['model']],
            'migration_redirect':None, 'placement_policy':'FLOOR', 'collision_policy':'NONE',
            'source_patterns':[], 'source_occurrences':0,
            'compiled_source_patterns':[{'target_state':'facing=north,visual=base','pattern':pattern}],
            'occurrence_scope':f'weighted source index {number}',
            'model_variant_relationship':{'facing':list(FACINGS),'visual':list(VISUALS)},
            'visual':{'base':'source-authored art','alt':'BASE fallback; same gameplay'},
            'migration_target':ident, 'migration_policy':'source RNG; approximate city replacement requires explicit opt-in'})
    for name, value in data.items():
        write_json(LOGICAL/(name+'.json'), value)
    (LOGICAL/'meshes.json.gz').write_bytes(gzip.compress(json.dumps(meshes,sort_keys=True,separators=(',',':')).encode(),mtime=0))
    required['expected_production_count'] = len(data['production-palette']['objects'])
    write_json(required_path, required)
    write_json(palette_path, palette)
    print('Added seven source-authored grass alternatives; existing families preserved')


if __name__ == '__main__':
    build()
