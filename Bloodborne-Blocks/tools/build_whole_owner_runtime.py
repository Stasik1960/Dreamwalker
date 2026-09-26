"""Whole historical owners in the existing geometry/mesh runtime; no cell fallback.

Only frozen forward models or exact retained-carrier models are admitted. A
state without reproducible complete art/physics stays unsupported. Original
source pivot is retained; the four orientations rotate about that same pivot.
"""
import copy
import gzip
import hashlib
import json
from pathlib import Path
from atomic_owner_groups import EVIDENCE, ORACLE, connected_groups, state
from build_city_compat import serial, write_json, resolved_geometry, rotated_mesh
from inspect_historical_wall_meshes import historical_art, carrier_cells
from source_mapping_archive import archive

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
CITY = RES / 'bloodborne_blocks/city'


def turn(x, z, n):
    return ((x,z), (1-z,x), (1-x,1-z), (z,1-x))[n]


def rotate_geometry(geometry, n):
    result = {'anchor': [0,0,0], 'render_offset': [0,0,0], 'cells': {}}
    for key, cell in geometry['cells'].items():
        x,y,z = map(int,key.split(','))
        dx,dz = ((x,z),(-z,x),(-x,-z),(z,-x))[n]
        value = {}
        for field in ('collision','outline'):
            boxes = []
            for a,b,c,d,e,f in cell[field]:
                corners = [turn(u,v,n) for u in (a,d) for v in (c,f)]
                boxes.append([min(p[0] for p in corners),b,min(p[1] for p in corners),
                              max(p[0] for p in corners),e,max(p[1] for p in corners)])
            value[field] = boxes
        result['cells'][f'{dx},{y},{dz}'] = value
    return result


def build():
    evidence = json.loads(gzip.decompress(EVIDENCE.read_bytes()))
    oracle = json.loads(ORACLE.read_bytes())
    groups = connected_groups(evidence, oracle)
    protected = {tuple(c['position']) for r in oracle['occurrences'] for c in r['source_cells']}
    required = sorted({o['state'] for g in groups for p,o in g['objects'].items() if p not in protected})
    definitions = json.loads((CITY/'definitions.json').read_bytes())
    geometry = json.loads((CITY/'geometry.json').read_bytes())
    # Rebuilding this extension never repages or alters existing compatibility.
    definitions['blocks'] = [b for b in definitions['blocks'] if not b.get('whole_owner')]
    geometry['blocks'] = {k:v for k,v in geometry['blocks'].items() if not k.startswith('owner_')}
    meshes, mappings, rejected = {}, {}, []
    translations = {}
    catalog = json.loads((ROOT/'docs/semantic-catalog-v2.json').read_text(encoding='utf-8-sig'))
    catalog = catalog.get('blocks',catalog)
    migration = archive()['v2\\migration.json']
    with historical_art() as jar:
        legacy = {b['id']:b for b in json.loads(jar.read('bloodborne_blocks/definitions.json'))['blocks']}
        old_geometry = json.loads(jar.read('bloodborne_blocks/geometry.json'))
        labels = json.loads(jar.read('assets/bloodborne_blocks/lang/ru_ru.json'))
        old_meshes = json.loads(gzip.decompress(jar.read('bloodborne_blocks/v2/meshes.json.gz')))
        for source in required:
            name, pairs = state(source); carrier = name.split(':')[1]; props = dict(pairs)
            try:
                original = legacy[carrier]
                complete = {**original['default'], **props}
                if 'assembled' in complete: complete['assembled'] = 'false'
                old_key = ','.join(k+'='+v for k,v in sorted(complete.items()))
                physics = resolved_geometry(old_geometry, carrier, old_key)
                if any(physics.get('render_offset', [0,0,0])): raise ValueError('nonzero historical render offset')
                if '0,0,0' not in physics['cells']: raise ValueError('historical root lacks physical cell')
                if any(len(c['collision']) > 4 for c in physics['cells'].values()):
                    raise ValueError('historical physics requires reviewed simplification')
                entry = migration[carrier]
                source_props = {**entry['default'], **props}
                parts = entry['states'][','.join(k+'='+v for k,v in sorted(source_props.items()))]
                cells = {}
                if isinstance(parts, list):
                    for part in parts:
                        polygons = rotated_mesh(old_meshes[part['id']], ('north','east','south','west').index(part.get('properties',{}).get('facing','north')))['polygons']
                        cells.setdefault(tuple(part['offset']), []).extend(polygons)
                elif parts.get('keep'):
                    cells = carrier_cells(jar, carrier, source_props)
                else: raise ValueError('no complete source art')
                polygons, seen = [], set()
                for offset, faces in cells.items():
                    for face in faces:
                        translated = {'texture': face['texture'], 'vertices': [[v[i]+offset[i] for i in range(3)]+v[3:] for v in face['vertices']]}
                        signature = serial(translated)
                        if signature not in seen: polygons.append(translated); seen.add(signature)
                if not polygons: raise ValueError('empty source mesh')
                ident = 'owner_' + hashlib.sha256(source.encode()).hexdigest()[:20]
                definition = copy.deepcopy(original)
                definition.update(id=ident, city_compat=True, whole_owner=True, logical=False,
                    creative=False, modular=True, kind='generic', extra_facing=True, offset='none',
                    custom_geometry=True, full_cube=False, properties={'facing':['north','east','south','west']},
                    default={'facing':'north'}, states={}, models={})
                states, variants = {}, {}
                for n,facing in enumerate(('north','east','south','west')):
                    key='facing='+facing; mesh_id=ident+'_'+facing
                    rotated=[]
                    for face in polygons:
                        vertices=[]
                        for x,y,z,u,v in face['vertices']:
                            x,z=turn(x,z,n); vertices.append([x,y,z,u,v])
                        rotated.append({'texture':face['texture'],'vertices':vertices})
                    meshes[mesh_id]={'polygons':rotated}
                    definition['states'][key]=original['states'][old_key]
                    definition['models'][key]=mesh_id
                    states[key]=rotate_geometry(physics,n)
                    variants[key]={'model':'bloodborne_blocks:block/city/'+ident}
                definitions['blocks'].append(definition); geometry['blocks'][ident]={'states':states}
                textures=sorted({p['texture'] for p in polygons})
                for texture in textures:
                    ns,path=texture.split(':')
                    if ns=='minecraft':continue
                    filename=f'assets/{ns}/textures/{path}.png'
                    raw=jar.read(filename); destination=RES/filename
                    if destination.exists() and destination.read_bytes()!=raw:raise ValueError('texture differs from runtime: '+texture)
                    if not destination.exists():destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(raw)
                tx={str(i):t for i,t in enumerate(textures)};tx['particle']=textures[0]
                write_json(RES/f'assets/bloodborne_blocks/models/block/city/{ident}.json',{'parent':'minecraft:block/block','textures':tx,'elements':[]})
                write_json(RES/f'assets/bloodborne_blocks/models/item/{ident}.json',{'parent':'bloodborne_blocks:block/city/'+ident})
                write_json(RES/f'assets/bloodborne_blocks/blockstates/{ident}.json',{'variants':variants})
                write_json(RES/f'data/bloodborne_blocks/loot_tables/blocks/{ident}.json',
                    {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'bloodborne_blocks:'+ident}],
                    'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
                translations['block.bloodborne_blocks.'+ident]=catalog.get(carrier,{}).get('ru',labels.get('block.bloodborne_blocks.'+carrier,carrier))
                mappings[source]={'id':'bloodborne_blocks:'+ident,'properties':{'facing':'north'},
                                  'shape':[list(map(int,k.split(','))) for k in physics['cells']]}
            except (KeyError,ValueError) as exc:
                rejected.append({'source':source,'reason':str(exc)})
    write_json(CITY/'definitions.json',definitions);write_json(CITY/'geometry.json',geometry)
    (CITY/'owner-meshes.json.gz').write_bytes(gzip.compress(serial(meshes),mtime=0))
    result={'evidenceSha256':hashlib.sha256(EVIDENCE.read_bytes()).hexdigest(),'states':mappings,'rejected':rejected}
    write_json(CITY/'owner-runtime-mappings.json',result)
    lang_path=RES/'assets/bloodborne_blocks/lang/ru_ru.json'
    lang=json.loads(lang_path.read_bytes());lang.update(translations);write_json(lang_path,lang)
    print('Whole owner runtime states:',len(mappings),'unsupported:',len(rejected),flush=True)


if __name__=='__main__':build()
