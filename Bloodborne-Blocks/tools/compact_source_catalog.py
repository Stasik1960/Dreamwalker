"""Build a staged, complete-object catalog from frozen raw Minecraft states.

This is an offline extractor: it never reads or writes a world or live resources.
"""
from __future__ import annotations
import copy, gzip, hashlib, json
from pathlib import Path

from build_city_compat import JAR, JAR_SHA, coarse_boxes, resolved_geometry, serial
from source_mapping_archive import archive


FACING=("north","east","south","west")

def digest(value): return hashlib.sha256(serial(value)).hexdigest()
def key(props): return ','.join(f'{k}={v}' for k,v in sorted(props.items()))
def parse_state(value):
    if isinstance(value,tuple): return value[0].split(':')[-1],dict(value[1])
    ident,_,tail=value.partition('[')
    return ident.split(':')[-1],dict(p.split('=',1) for p in tail.rstrip(']').split(',') if p)
def source_metadata(original,state_key):
    value=original['states'][state_key]
    return {'luminance':int(value[2]) if len(value)>2 else 0,'emissive':bool(original.get('emissive')),'animated':bool(original.get('animated'))}
def choose_apps(groups):
    """Keep every multipart rule and the first authored variant for legacy physics."""
    return [group[0] for group in groups]
def polygons_for_apps(apps):
    import modular_mesh as mm
    result=[]
    for app in apps:
        model=mm.model(app['model'])
        for element in model.get('elements',[]):
            for polygon in mm.element_polys(element,app,model): result.extend(mm.tile_uv(polygon))
    return result
def complete_mesh(jar, ident, rawprops):
    import catalog_geometry as cg
    state=json.loads(jar.read(f'assets/bloodborne_blocks/blockstates/{ident}.json'))
    return {'polygons':polygons_for_apps(choose_apps(cg.applications(state,rawprops)))}
def turn_point(x,y,z,n): return ((x,y,z),(1-z,y,x),(1-x,y,1-z),(z,y,1-x))[n%4]
def rotate_mesh(mesh,n):
    return {'polygons':[{**p,'vertices':[[round(v,6) for v in turn_point(*q[:3],n)]+q[3:] for q in p['vertices']]} for p in mesh['polygons']]}
def rotate_geometry(geometry,n):
    result={'anchor':[0,0,0],'render_offset':[0,0,0],'cells':{}}
    for text,cell in geometry['cells'].items():
        x,y,z=map(int,text.split(','));u,w=((x,z),(-z,x),(-x,-z),(z,-x))[n%4];value={}
        for field in ('collision','outline','placement'):
            if field not in cell: continue
            boxes=[]
            for box in cell.get(field,[]):
                corners=[turn_point(a,b,c,n) for a in (box[0],box[3]) for b in (box[1],box[4]) for c in (box[2],box[5])]
                boxes.append([round(min(p[i] for p in corners),6) for i in range(3)]+[round(max(p[i] for p in corners),6) for i in range(3)])
            value[field]=boxes
        result['cells'][f'{u},{y},{w}']=value
    result['cells'].setdefault('0,0,0',{'collision':[],'outline':[]})
    return result
def coarse_geometry(geometry):
    result=copy.deepcopy(geometry)
    if any(result.get('render_offset',[0,0,0])): raise ValueError('nonzero historical render offset')
    result['anchor']=[0,0,0];result['render_offset']=[0,0,0]
    for cell in result['cells'].values():
        cell['collision']=coarse_boxes(cell.get('collision',[]),4)
        cell['outline']=coarse_boxes(cell.get('outline',[]),4)
        if 'placement' in cell: cell['placement']=coarse_boxes(cell['placement'],4)
    result['cells'].setdefault('0,0,0',{'collision':[],'outline':[]})
    return result
def normalized_mesh(mesh):
    polygons=[]
    for polygon in mesh['polygons']:
        vertices=[tuple(round(float(value),6) for value in vertex) for vertex in polygon['vertices']]
        polygons.append((polygon['texture'],min(tuple(vertices[index:]+vertices[:index]) for index in range(len(vertices)))))
    return {'polygons':[{'texture':texture,'vertices':[list(vertex) for vertex in vertices]} for texture,vertices in sorted(set(polygons))]}
def normalized_geometry(geometry):
    result={'anchor':[0,0,0],'render_offset':[0,0,0],'cells':{}}
    for text,cell in sorted(geometry['cells'].items()):
        result['cells'][text]={field:sorted([list(map(lambda value:round(float(value),6),box)) for box in cell[field]]) for field in ('collision','outline','placement') if field in cell}
    return result
def canonical(mesh,geometry,behavior=None):
    options=[]
    for turn in range(4):
        m,g=rotate_mesh(mesh,turn),rotate_geometry(geometry,turn)
        signature=digest({'mesh':normalized_mesh(m),'geometry':normalized_geometry(g),'behavior':behavior or {}})
        options.append((signature,turn,m,g))
    return min(options,key=lambda value:value[0])
def _write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(serial(value))
def generate(required_states,output):
    """Write a self-contained staged catalog and raw-state mapping under *output*."""
    import catalog_geometry as cg
    from inspect_historical_wall_meshes import historical_art
    output=Path(output).resolve();output.mkdir(parents=True,exist_ok=True)
    # Only this extractor owns these staged folders. Regeneration must not
    # retain asset IDs produced by earlier canonicalization experiments.
    import shutil
    for folder in ('assets','data'):
        owned=output/folder
        if owned.exists():
            if owned.is_symlink()or owned.resolve().parent!=output:raise ValueError('unsafe generated asset folder')
            shutil.rmtree(owned)
    cache=output/'extraction-cache';cache.mkdir(exist_ok=True);migration=archive()['v2\\migration.json'];groups={};mapping={};omissions=[]
    with historical_art() as jar:
        source_definitions=json.loads(jar.read('bloodborne_blocks/definitions.json'));definitions={b['id']:b for b in source_definitions['blocks']}
        old_geometry=json.loads(jar.read('bloodborne_blocks/geometry.json'))
        for index,raw in enumerate(required_states,1):
            ident,props=parse_state(raw)
            if ident not in definitions or ident not in migration: raise ValueError('missing historical source '+ident)
            original=definitions[ident]
            if original.get('behavior') not in (None,'static'): raise ValueError('active historical behavior '+ident)
            complete={**definitions[ident]['default'],**props}
            if 'assembled' in complete: complete['assembled']='false'
            state_key=key(complete)
            if state_key not in definitions[ident]['states']: raise ValueError('missing historical state '+ident+'['+state_key+']')
            behavior={name:original.get(name) for name in ('layer','hardness','resistance','slipperiness','jump','velocity','semantic')}
            metadata=source_metadata(original,state_key)
            cache_key=digest({'schemaVersion':2,'historicalJarSha256':JAR_SHA,'state':str(raw)})
            cache_path=cache/(cache_key+'.json.gz')
            if cache_path.exists():
                payload=json.loads(gzip.decompress(cache_path.read_bytes()))
                if payload.get('schemaVersion') != 2 or payload.get('historicalJarSha256') != JAR_SHA: raise ValueError('stale extraction cache')
                mesh=payload['mesh'];geometry=payload['geometry']
            else:
                mesh=complete_mesh(jar,ident,complete)
                geometry=coarse_geometry(resolved_geometry(old_geometry,ident,state_key))
                cache_path.write_bytes(gzip.compress(serial({'schemaVersion':2,'historicalJarSha256':JAR_SHA,'mesh':mesh,'geometry':geometry}),mtime=0))
            if not mesh['polygons']:
                physical=any(cell.get('collision') for cell in geometry['cells'].values())
                omission={'source':str(raw),'reason':'empty_historical_art_requires_root_decision' if physical else 'empty_historical_art_and_physics','physicalCells':sorted(geometry['cells'])}
                omissions.append(omission)
                if not physical:mapping[str(raw)]={'air':True}
                continue
            signature,turn,mesh,geometry=canonical(mesh,geometry,behavior)
            group=groups.setdefault(signature,{'mesh':mesh,'geometry':geometry,'carriers':set(),'original':original,'luminance':0,'emissive':False,'animated':False})
            group['carriers'].add(ident)
            group['luminance']=max(group['luminance'],metadata['luminance']);group['emissive']|=metadata['emissive'];group['animated']|=metadata['animated']
            mapping[str(raw)]={'signature':signature,'facing':FACING[(-turn)%4]}
            if index%100==0: print('compact source states',index,flush=True)
        blocks=[];gblocks={};profiles={};meshes={};raw_mapping={}
        for signature,entry in sorted(groups.items()):
            ident='owner_unified_'+signature[:20];mesh_id=ident+'_base';states={};original=entry['original'];definition={'id':ident,'kind':'generic','source':'minecraft:stone','logical':False,'city_compat':True,'whole_owner':True,'unified':True,'modular':True,'extra_facing':True,'creative':False,'custom_geometry':True,'offset':'none','full_cube':False,'layer':original.get('layer') or 'solid','hardness':original.get('hardness',1.5),'resistance':original.get('resistance',6),'slipperiness':original.get('slipperiness',.6),'jump':original.get('jump',1),'velocity':original.get('velocity',1),'emissive':entry['emissive'],'animated':entry['animated'],'semantic':original.get('semantic') or 'architecture','originalcarrier':sorted(entry['carriers']),'properties':{'facing':list(FACING),'root_anchor':['canonical'],'variant':['0']},'default':{'facing':'north','root_anchor':'canonical','variant':'0'},'placement_properties':{'variant':'0'},'states':{},'models':{}}
            for n,facing in enumerate(FACING):
                props={'facing':facing,'root_anchor':'canonical','variant':'0'};state=key(props);g=rotate_geometry(entry['geometry'],n);ref='compact_'+digest(g)[:24]
                profiles.setdefault(ref,g);states[state]={'ref':ref};definition['states'][state]=[0,0,entry['luminance']];definition['models'][state]=mesh_id
            meshes[mesh_id]=entry['mesh'];blocks.append(definition);gblocks[ident]={'states':states}
        for raw,value in mapping.items():
            if value.get('air'):raw_mapping[raw]={'id':'minecraft:air','properties':{},'rootOffset':[0,0,0],'omissionReason':'empty_historical_art_and_physics'}
            else:
                ident='owner_unified_'+value['signature'][:20];raw_mapping[raw]={'id':'bloodborne_blocks:'+ident,'properties':{'facing':value['facing'],'root_anchor':'canonical','variant':'0'},'rootOffset':[0,0,0]}
        needed_textures={p['texture'] for mesh in meshes.values() for p in mesh['polygons']};emissive_textures={base:glow for base,glow in source_definitions.get('emissive_textures',{}).items() if base in needed_textures}
        _write(output/'definitions.json',{'blocks':blocks,'emissive_textures':emissive_textures});_write(output/'geometry.json',{'blocks':gblocks,'profiles':profiles});_write(output/'raw-source-mapping.json',{'schemaVersion':1,'states':raw_mapping,'omissions':omissions})
        with (output/'owner-meshes.json.gz').open('wb') as rawfile: rawfile.write(gzip.compress(serial(meshes),mtime=0))
        for definition in blocks:
            ident=definition['id'];mesh=meshes[ident+'_base']
            textures=sorted({p['texture'] for p in mesh['polygons']});textures+=sorted(emissive_textures[base] for base in textures if base in emissive_textures);textures=textures or['minecraft:block/stone']
            _write(output/f'assets/bloodborne_blocks/models/block/city/{ident}.json',{'parent':'minecraft:block/block','textures':{'particle':textures[0],**{str(i):v for i,v in enumerate(textures)}},'elements':[]})
            _write(output/f'assets/bloodborne_blocks/models/item/{ident}.json',{'parent':'bloodborne_blocks:block/city/'+ident})
            _write(output/f'assets/bloodborne_blocks/blockstates/{ident}.json',{'variants':{f'facing={f}':{'model':'bloodborne_blocks:block/city/'+ident} for f in FACING}})
            _write(output/f'data/bloodborne_blocks/loot_tables/blocks/{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'bloodborne_blocks:'+ident}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        textures=needed_textures|set(emissive_textures.values())
        for texture in textures:
            namespace,path=texture.split(':',1);target=output/f'assets/{namespace}/textures/{path}.png'
            try: raw=jar.read(f'assets/{namespace}/textures/{path}.png')
            except KeyError: raw=cg.ZIP.read(f'assets/{namespace}/textures/{path}.png')
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
            metadata=f'assets/{namespace}/textures/{path}.png.mcmeta'
            try:extra=jar.read(metadata)
            except KeyError:
                try:extra=cg.ZIP.read(metadata)
                except KeyError:extra=None
            if extra is not None:target.with_name(target.name+'.mcmeta').write_bytes(extra)
    _write(output/'summary.json',{'families':len(groups),'states':len(mapping),'omissions':omissions,'canonicalization':{'schemaVersion':2,'historicalJarSha256':JAR_SHA,'weightedVariant':'first-authored','multipart':'all-matching','yaw':'minimum normalized mesh/geometry/behavior digest','lighting':'maximum static luminance; any emissive/animated flag; excluded from identity'}})
    return {'families':len(groups),'states':len(mapping),'omissions':len(omissions),'output':str(output)}

if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--states',type=Path,required=True);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
    required=json.loads(args.states.read_bytes())
    print(json.dumps(generate(sorted(required),args.output),ensure_ascii=False))
