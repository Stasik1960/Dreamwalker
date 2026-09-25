"""Export observed city architecture from the immutable 2.0.1 artifact.

Reuses the static mesh and cell geometry formats. No world is edited here;
production logical resources are never regenerated or overwritten.
"""
from __future__ import annotations
import argparse, copy, gzip, hashlib, json, math, zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
JAR = ROOT.parent / 'releases/Bloodborne-Blocks/bloodborne-blocks-2.0.1-mc1.20.1.jar'
JAR_SHA = 'e400442c1711b013dd73d2a7763fc7c34e778af05876d90b69c2bedced3c1212'
INVENTORY_SHA = '5a384889cb5d2de94acaa16c8b1a7771345dbf449dbef2438c4c62c0a41d7b4b'
PAGE_SIZE = 16
FACING = ['north', 'east', 'south', 'west']

def serial(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))+'\n').encode('utf8')

def digest(value):
    return hashlib.sha256(serial(value)).hexdigest()

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(serial(value))

def split_state(key):
    name, _, tail = key.partition('[')
    return name.split(':', 1)[-1], (dict(v.split('=', 1) for v in tail.rstrip(']').split(',')) if tail else {})

def state_key(props):
    return ','.join(k+'='+v for k,v in sorted(props.items()))

def rotated_mesh(mesh, turns):
    polygons = []
    for polygon in mesh['polygons']:
        vertices = []
        for x,y,z,u,v in polygon['vertices']:
            x,z = [(x,z),(1-z,x),(1-x,1-z),(z,1-x)][turns]
            if not all(math.isfinite(a) and -0.00001 <= a <= 1.00001 for a in (x,y,z)):
                raise ValueError('Historical module leaves its cell')
            vertices.append([round(min(1,max(0,a)),6) for a in (x,y,z)]+[u,v])
        polygons.append({'texture':polygon['texture'],'vertices':vertices})
    return {'polygons':polygons}

def resolved_geometry(data, ident, key):
    value = data['blocks'][ident]['states'][key]
    return copy.deepcopy(data['profiles'][value['ref']] if 'ref' in value else value)

def coarse_boxes(boxes, limit=4):
    """Conservatively merge nearest box pairs; retain stairs/door gaps where possible."""
    boxes = [list(b) for b in boxes]
    def volume(b): return math.prod(max(0,b[i+3]-b[i]) for i in range(3))
    def union(a,b): return [min(a[i],b[i]) for i in range(3)]+[max(a[i],b[i]) for i in range(3,6)]
    while len(boxes)>limit:
        _,i,j,merged=min((volume(union(a,b))-volume(a)-volume(b),i,j,union(a,b))
            for i,a in enumerate(boxes) for j,b in enumerate(boxes) if j>i)
        boxes[i]=merged;boxes.pop(j)
    return boxes

def build(inventory):
    if hashlib.sha256(JAR.read_bytes()).hexdigest() != JAR_SHA:
        raise ValueError('Historical artifact checksum mismatch')
    inventory_bytes = inventory.read_bytes()
    if hashlib.sha256(inventory_bytes).hexdigest() != INVENTORY_SHA:
        raise ValueError('City page IDs are frozen to the reviewed MODDED inventory; append new pages instead of repaging existing saves')
    counts = json.loads(inventory_bytes)['blocks']['stateCounts']
    observed = sorted(k for k in counts if k.startswith('bloodborne_blocks:') and ':architecture_part' not in k)
    semantics = json.loads((ROOT/'docs/semantic-catalog-v2.json').read_text(encoding='utf-8-sig'))
    semantics = semantics.get('blocks',semantics)
    with zipfile.ZipFile(JAR) as archive:
        archive_names = set(archive.namelist())
        original = json.loads(archive.read('bloodborne_blocks/definitions.json'))
        old = {b['id']:b for b in original['blocks']}
        modular = {b['id']:b for b in json.loads(archive.read('bloodborne_blocks/v2/definitions.json'))['blocks']}
        meshes = json.loads(gzip.decompress(archive.read('bloodborne_blocks/v2/meshes.json.gz')))
        modular_geometry = json.loads(archive.read('bloodborne_blocks/v2/geometry.json'))
        legacy_geometry = json.loads(archive.read('bloodborne_blocks/geometry.json'))
        names = json.loads(archive.read('assets/bloodborne_blocks/lang/ru_ru.json'))
        needed_textures, needed_models = set(), set()
        exported_meshes, profiles, blocks, geometries, mapping, translations = {}, {}, [], {}, {}, {}
        groups = defaultdict(list)
        native_ids = set()
        missing_modules = []
        for saved in observed:
            ident, props = split_state(saved)
            if ident.startswith('o_'):
                continue
            if not ident.startswith('m_'):
                if ident not in old: raise ValueError('Unknown legacy ID '+ident)
                native_ids.add(ident)
                continue
            if ident not in modular:
                missing_modules.append({'state':saved,'count':counts[saved],'reason':'absent_from_historical_artifact'})
                continue
            definition = modular[ident]
            key = state_key({**definition['default'],**props})
            if key not in definition['states']: raise ValueError('Unknown module state '+saved)
            mesh = rotated_mesh(meshes[ident], FACING.index(props.get('facing','north')))
            mesh_key = 'city_'+digest(mesh)[:24]
            exported_meshes.setdefault(mesh_key,mesh)
            needed_textures.update(p['texture'] for p in mesh['polygons'])
            geometry = resolved_geometry(modular_geometry,ident,key)
            if set(geometry['cells'])-{'0,0,0'}: raise ValueError('Module owns other cells')
            for cell in geometry['cells'].values():
                if len(cell['collision'])>4: raise ValueError('Detailed module collision')
                if definition.get('semantic') in ('bush','plant','floor_decoration'):cell['collision']=[]
            geometry['anchor']=[0,0,0]
            geometry['render_offset']=[0,0,0]
            profile='city_'+digest(geometry)[:24];profiles.setdefault(profile,geometry)
            category=definition.get('semantic','masonry')
            group=(category,definition['layer'],bool(definition.get('full_cube')),bool(definition.get('emissive')))
            groups[group].append((saved,definition,key,mesh_key,profile,names.get('block.bloodborne_blocks.'+ident,'Архитектурная секция')))
        for group, entries in sorted(groups.items()):
            category,layer,cube,emissive=group
            # Group order and source keys make page IDs deterministic for this frozen input.
            group_key=hashlib.sha256(serial(group)).hexdigest()[:8]
            for start in range(0,len(entries),PAGE_SIZE):
                page=entries[start:start+PAGE_SIZE]
                if len(page)==1:page=page+page # StateManager requires two possible property values.
                ident=f'city_{category}_{group_key}_{start//PAGE_SIZE:04d}'
                definition=copy.deepcopy(page[0][1]);definition.update(id=ident,city_compat=True,logical=False,creative=False,
                    extra_facing=False,properties={'variant':[str(i) for i in range(len(page))]},
                    default={'variant':'0'},states={},models={})
                geometry_states={};textures=set();variants={}
                for index,(saved,source,key,mesh_key,profile,label) in enumerate(page):
                    variant=str(index);sk='variant='+variant
                    definition['states'][sk]=source['states'][key]
                    definition['models'][sk]=mesh_key
                    geometry_states[sk]={'ref':profile}
                    mapping[saved]={'id':'bloodborne_blocks:'+ident,'properties':{'variant':variant}}
                    translations['city.bloodborne_blocks.'+ident+'.'+variant]=label
                    textures.update(p['texture'] for p in exported_meshes[mesh_key]['polygons'])
                    variants[sk]={'model':'bloodborne_blocks:block/city/'+ident}
                blocks.append(definition);geometries[ident]={'states':geometry_states}
                translations['block.bloodborne_blocks.'+ident]=page[0][-1]
                write_json(RES/f'assets/bloodborne_blocks/blockstates/{ident}.json',{'variants':variants})
                tx={str(i):t for i,t in enumerate(sorted(textures))};tx['particle']=next(iter(sorted(textures)),'minecraft:block/stone')
                write_json(RES/f'assets/bloodborne_blocks/models/block/city/{ident}.json',{'parent':'minecraft:block/block','textures':tx,'elements':[]})
                write_json(RES/f'assets/bloodborne_blocks/models/item/{ident}.json',{'parent':'bloodborne_blocks:block/city/'+ident})
        # Ordinary native stair/slab behavior and authored saved decor are retained.
        # This is observed compatibility, not a restoration of all 503 carriers.
        for ident in sorted(native_ids):
            definition=copy.deepcopy(old[ident]);definition.update(city_compat=True,logical=False,creative=False)
            blocks.append(definition);states={}
            for key in definition['states']:
                value=resolved_geometry(legacy_geometry,ident,key)
                for cell in value['cells'].values():
                    cell['collision']=coarse_boxes(cell['collision'])
                    cell['outline']=coarse_boxes(cell['outline'],1)
                profile='city_'+digest(value)[:24];profiles.setdefault(profile,value);states[key]={'ref':profile}
            geometries[ident]={'states':states}
            translations['block.bloodborne_blocks.'+ident]=semantics.get(ident,{}).get('ru',names.get('block.bloodborne_blocks.'+ident,ident))
            bs=json.loads(archive.read(f'assets/bloodborne_blocks/blockstates/{ident}.json'))
            def collect(value):
                if isinstance(value,dict):
                    if isinstance(value.get('model'),str):needed_models.add(value['model'])
                    for child in value.values():collect(child)
                elif isinstance(value,list):
                    for child in value:collect(child)
            collect(bs)
            write_json(RES/f'assets/bloodborne_blocks/blockstates/{ident}.json',bs)
            item=f'assets/bloodborne_blocks/models/item/{ident}.json'
            if item in archive_names:needed_models.add('bloodborne_blocks:item/'+ident)
        copied=[]
        def copy_asset(path):
            raw=archive.read(path);destination=RES/path
            if destination.exists():
                same=(json.loads(destination.read_bytes())==json.loads(raw)) if path.endswith('.json') else destination.read_bytes()==raw
                if not same:raise ValueError('Would overwrite current production asset '+path)
            else:
                destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(raw);copied.append(path)
        seen=set()
        while needed_models:
            model=needed_models.pop()
            if model in seen or model.startswith('minecraft:'):continue
            seen.add(model);namespace,path=model.split(':',1);filename=f'assets/{namespace}/models/{path}.json'
            data=json.loads(archive.read(filename));copy_asset(filename)
            if data.get('parent'):needed_models.add(data['parent'])
            needed_textures.update(t for t in data.get('textures',{}).values() if not t.startswith('#'))
        emissive=original.get('emissive_textures',{})
        needed_textures.update(emissive[t] for t in list(needed_textures) if t in emissive)
        for texture in sorted(needed_textures):
            if texture.startswith('minecraft:'):continue
            namespace,path=texture.split(':',1);filename=f'assets/{namespace}/textures/{path}.png';copy_asset(filename)
            if filename+'.mcmeta' in archive_names:copy_asset(filename+'.mcmeta')
        city=RES/'bloodborne_blocks/city'
        write_json(city/'definitions.json',{'blocks':blocks,'emissive_textures':emissive})
        write_json(city/'geometry.json',{'profiles':profiles,'blocks':geometries})
        city.mkdir(parents=True,exist_ok=True)
        (city/'meshes.json.gz').write_bytes(gzip.compress(serial(exported_meshes),mtime=0))
        write_json(city/'migration.json',{'schema':1,'historicalJarSha256':JAR_SHA,'inventorySha256':hashlib.sha256(inventory_bytes).hexdigest(),'states':mapping})
        # Separate namespace language file avoids rewriting production language assets.
        write_json(RES/'assets/bloodborne_city/lang/ru_ru.json',translations)
        write_json(RES/'assets/bloodborne_city/lang/en_us.json',translations)
        # Drops carry the actual variant via existing ArchitectureBlock state-preservation.
        for block in blocks:
            ident=block['id']
            write_json(RES/f'data/bloodborne_blocks/loot_tables/blocks/{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'bloodborne_blocks:'+ident}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        proof={'historicalJarSha256':JAR_SHA,'inventorySha256':hashlib.sha256(inventory_bytes).hexdigest(),
            'nativeIds':sorted(native_ids),'pageSize':PAGE_SIZE,'blocks':len(blocks),'states':sum(len(b['states']) for b in blocks),
            'moduleStatesMapped':len(mapping),'meshCount':len(exported_meshes),'profiles':len(profiles),'copiedAssets':copied,
            'missingModules':missing_modules,
            'serverLoadsPolygons':False,'newTickingBlockEntities':0,'maximumModuleCollisionBoxes':4}
        write_json(ROOT/'docs/city-compat/build-proof.json',proof)
        print(json.dumps({k:v for k,v in proof.items() if k not in ('copiedAssets','nativeIds')}))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('inventory',type=Path);args=parser.parse_args();build(args.inventory)
