"""One numbered, editable platform per registered block above the city."""
from __future__ import annotations
import argparse, copy, hashlib, json, math, os, shutil
from pathlib import Path
import numpy as np
from build_unified_registry import stream_members,serial,key
from upgrade_gallery_city import StreamingWorld
from convert_logical_world import PART,hash_tree,state_of,block_pos_long
from world_io import (Tag,NbtFile,RegionFile,compound,section_blocks,TAG_BYTE,
    TAG_INT,TAG_LONG,TAG_STRING,TAG_LIST,TAG_COMPOUND)

def _city_bbox(world):
    low=[10**9]*3;high=[-10**9]*3
    for chunk in world.chunks.values():
        if chunk.dimension!='minecraft:overworld':continue
        for sec in chunk.root().get('sections',Tag(TAG_LIST,[],TAG_COMPOUND)).value:
            data=compound(sec);bs=data.get('block_states')
            if not bs:continue
            names=[compound(t)['Name'].value for t in compound(bs)['palette'].value]
            occupied=[i for i,n in enumerate(names)if n not in {'minecraft:air','minecraft:cave_air','minecraft:void_air'}]
            if not occupied:continue
            _,indices=section_blocks(sec);used=np.flatnonzero(np.isin(np.asarray(indices),occupied))
            if not len(used):continue
            xyz=[chunk.x*16+(used&15),int(data['Y'].value)*16+(used>>8),chunk.z*16+((used>>4)&15)]
            for a in range(3):low[a]=min(low[a],int(xyz[a].min()));high[a]=max(high[a],int(xyz[a].max()))
    if low[0]==10**9:raise ValueError('empty city')
    return tuple(low+high)

class GalleryWorld(StreamingWorld):
    def ensure_chunk(self,dim,pos):
        cx,cz=pos[0]//16,pos[2]//16;k=(dim,cx,cz)
        if k in self.chunks.rows:return
        if dim!='minecraft:overworld':raise ValueError('gallery dimension must be overworld')
        path=self.root/'region'/f'r.{cx//32}.{cz//32}.mca'
        region=self.by_region.setdefault(path,RegionFile())
        data={'DataVersion':Tag(TAG_INT,3465),'xPos':Tag(TAG_INT,cx),'zPos':Tag(TAG_INT,cz),'yPos':Tag(TAG_INT,-4),
            'Status':Tag(TAG_STRING,'minecraft:full'),'isLightOn':Tag(TAG_BYTE,0),'InhabitedTime':Tag(TAG_LONG,0),'LastUpdate':Tag(TAG_LONG,0),
            'sections':Tag(TAG_LIST,[],TAG_COMPOUND),'block_entities':Tag(TAG_LIST,[],TAG_COMPOUND),
            'block_ticks':Tag(TAG_LIST,[],TAG_COMPOUND),'fluid_ticks':Tag(TAG_LIST,[],TAG_COMPOUND),'Heightmaps':Tag(TAG_COMPOUND,{})}
        region.set_chunk(cx&31,cz&31,NbtFile('',Tag(TAG_COMPOUND,data)),timestamp=0)
        self.chunks.rows[k]=(path,region,region.get_chunk(cx&31,cz&31));self.dirty_regions.add(path)
    def set(self,dim,pos,state):
        self.ensure_chunk(dim,pos)
        chunk=self.chunk(dim,pos[0],pos[2]);root=chunk.root()
        # Vanilla writes empty compound lists with TAG_End subtype. Once a
        # section or block entity is added its list subtype must be updated.
        for name in ('sections','block_entities'):
            tag=root.get(name)
            if tag is not None and not tag.value:tag.list_type=TAG_COMPOUND
        super().set(dim,pos,state)
        section=compound(chunk._section(pos[1]//16))
        section.setdefault('biomes',Tag(TAG_COMPOUND,{'palette':Tag(TAG_LIST,[Tag(TAG_STRING,'minecraft:plains')],TAG_STRING)}))
    def save(self):
        for path in self.by_region:path.parent.mkdir(parents=True,exist_ok=True)
        super().save()

def load_data(city):
    defs={};geometry={};defaults={};contracts={}
    for scope in (city,city.parent/'logical'):
        data=json.loads((scope/'geometry.json').read_bytes())
        for d in json.loads((scope/'definitions.json').read_bytes())['blocks']:
            defs[d['id']]=d;defaults['bloodborne_blocks:'+d['id']]=d['default']
            geometry[d['id']]={s:data.get('profiles',{}).get(g.get('ref'),g)for s,g in data['blocks'][d['id']]['states'].items()}
    for row in json.loads((city.parent/'logical/contracts-v2.json').read_bytes())['families']:contracts[row['id']]=row['states']
    ids=json.loads((city.parent/'debug-ids.json').read_bytes())['ids'];bounds={}
    for scope,names in ((city,('meshes.json.gz','owner-meshes.json.gz')),(city.parent/'logical',('meshes.json.gz',))):
        for name in names:
            for ident,mesh in stream_members(scope/name):
                vertices=[v for p in mesh['polygons']for v in p['vertices']]
                if vertices:bounds[ident]=[min(v[i]for v in vertices)for i in range(3)]+[max(v[i]for v in vertices)for i in range(3)]
    return defs,geometry,defaults,contracts,ids,bounds

def _rows(data):
    defs,geo,defaults,contracts,ids,bounds=data;rows=[]
    for ident,d in sorted(defs.items()):
        p=dict(d['default']);s=key(p);g=geo[ident][s]
        footprint=[tuple(map(int,v.split(',')))for v in g['cells']]
        if (0,0,0)not in footprint:raise ValueError('missing specimen storage root '+ident)
        contract=contracts.get(ident,{}).get(s,{})
        render=contract.get('render_mesh');mid=render['id']if render else(d.get('models')or{}).get(s)
        raw=bounds.get(mid,[min(v[i]for v in footprint)for i in range(3)]+[max(v[i]+1 for v in footprint)for i in range(3)])
        off=render.get('offset',[0,0,0])if render else g.get('render_offset',[0,0,0]);bb=[raw[i]+off[i%3]for i in range(6)]
        low=[min(0,math.floor(bb[i]),*(v[i]for v in footprint))for i in range(3)]
        high=[max(1,math.ceil(bb[i+3]),*(v[i]+1 for v in footprint))for i in range(3)]
        rows.append({'id':ident,'numericId':ids[ident],'properties':p,'state':s,'bounds':bb,'footprint':footprint,'low':low,'high':high,'editable':True})
    # A service part is shown with a real parent; it is not an orphan block.
    host=next((r for r in rows if len(r['footprint'])>1),None)
    if host is None:raise ValueError('no valid service-part parent')
    service=copy.deepcopy(host);service.update(id='architecture_part',numericId=ids['architecture_part'],editable=False,hostId=host['id'])
    service['serviceOffset']=next(v for v in host['footprint']if v!=(0,0,0));rows.append(service)
    if {r['id']for r in rows}!=set(defs)|{'architecture_part'}:raise ValueError('incomplete registry coverage')
    return rows

def sign(point,text):
    front=Tag(TAG_COMPOUND,{'messages':Tag(TAG_LIST,[Tag(TAG_STRING,json.dumps({'text':v},ensure_ascii=False))for v in text],TAG_STRING),'color':Tag(TAG_STRING,'black'),'has_glowing_text':Tag(TAG_BYTE,0)})
    return Tag(TAG_COMPOUND,{'id':Tag(TAG_STRING,'minecraft:sign'),**{a:Tag(TAG_INT,v)for a,v in zip('xyz',point)},'front_text':front,'back_text':copy.deepcopy(front)})

def build(source,output,city,citybounds=None):
    source,output,city=map(lambda p:Path(p).resolve(),(source,output,city))
    if output.exists()or source==output or source in output.parents or output in source.parents:raise ValueError('new output outside source required')
    final_output=output;output=output.with_name('.'+output.name+'.building')
    if output.exists():raise ValueError('unfinished gallery staging directory exists')
    output.parent.mkdir(parents=True,exist_ok=True);data=load_data(city);rows=_rows(data)
    source_world=StreamingWorld(source,{},cache_limit=32);bbox=tuple(citybounds)if citybounds else _city_bbox(source_world)
    platform_y=bbox[4]+4;columns=max(1,math.ceil(math.sqrt(len(rows))))
    # Variable-width columns and row depths retain exactly three air blocks.
    widths=[max(r['high'][0]-r['low'][0]+6 for r in rows[c::columns])for c in range(min(columns,len(rows)))]
    depths=[max(r['high'][2]-r['low'][2]+6 for r in rows[a:a+columns])for a in range(0,len(rows),columns)]
    totalx=sum(widths)+3*(len(widths)-1);totalz=sum(depths)+3*(len(depths)-1)
    xmin=(bbox[0]+bbox[3]-totalx)//2;zmin=(bbox[2]+bbox[5]-totalz)//2
    xs=[xmin];zs=[zmin]
    for w in widths[:-1]:xs.append(xs[-1]+w+3)
    for d in depths[:-1]:zs.append(zs[-1]+d+3)
    if any(platform_y+1-r['low'][1]+r['high'][1]>319 for r in rows):raise ValueError('gallery cannot fit below build height 320')
    shutil.copytree(source,output);world=GalleryWorld(output,data[2],cache_limit=64);specimens=[]
    for i,row in enumerate(rows):
        column,index=i%columns,i//columns;x,z=xs[column],zs[index];width,depth=widths[column],depths[index]
        root=(x+3-row['low'][0],platform_y+1-row['low'][1],z+3-row['low'][2]);owner='bloodborne_blocks:'+row.get('hostId',row['id'])
        for px in range(x,x+width):
            for pz in range(z,z+depth):world.set('minecraft:overworld',(px,platform_y,pz),('minecraft:smooth_stone',()))
        expected=[];helper_baseline=[]
        for off in row['footprint']:
            point=tuple(root[a]+off[a]for a in range(3));state=(owner,tuple(sorted(row['properties'].items())))if off==(0,0,0)else(PART,())
            if world.get('minecraft:overworld',point)not in (None,('minecraft:air',())):raise ValueError('gallery overwrites occupied object cell')
            world.set('minecraft:overworld',point,state)
            expected.append({'absolute':list(point),'state':state[0],'properties':dict(state[1])})
            if off!=(0,0,0):
                world.add_helper('minecraft:overworld',point,root,owner);helper_baseline.append({'absolute':list(point),'Root':block_pos_long(*root),'Owner':owner})
        marker=(x+1,platform_y+1,z+1);world.set('minecraft:overworld',marker,('minecraft:oak_sign',(('rotation','8'),('waterlogged','false'))))
        chunk=world.chunk('minecraft:overworld',marker[0],marker[2]);chunk.entities(create=True)[1].append(sign(marker,[row['numericId'],row['id'],'УДАЛИТЬ / ЗАМЕНИТЬ'if row['editable']else'СЛУЖЕБНАЯ ЗАВИСИМОСТЬ','Один ID, повороты внутри']));chunk.changed=True
        displayroot=tuple(root[a]+row.get('serviceOffset',(0,0,0))[a]for a in range(3))
        baseline={'cells':expected,'helperEntities':helper_baseline}
        specimen={'stableKey':'block-'+row['numericId'],'numericId':row['numericId'],'sourceBlockId':row['id'],'editable':row['editable'],
            'absoluteRoot':list(displayroot),'hostRoot':list(root),'originalFullState':row['state'],'expectedCells':expected,
            'expectedHelperEntities':helper_baseline,'inspectionBounds':[x,platform_y+1,z,x+width-1,319,z+depth-1],
            'padBounds':[x,platform_y,z,x+width-1,platform_y,z+depth-1],'marker':list(marker),
            'baselineSha256':hashlib.sha256(serial(baseline)).hexdigest(),'tp':f'/tp @s {x+1} {platform_y+2} {z+2}'}
        specimens.append(specimen)
        if i%250==0:print(f'gallery platforms {i}/{len(rows)}',flush=True)
    world.save()
    # Native spawn is above the first platform; source player files stay absent.
    from world_io import read_nbt,write_nbt
    level=read_nbt(output/'level.dat');meta=compound(compound(level.root)['Data']);first=specimens[0]['marker']
    for a,v in zip(('SpawnX','SpawnY','SpawnZ'),(first[0]+1,first[1],first[2]+1)):meta[a]=Tag(TAG_INT,v)
    compound(meta['GameRules'])['spawnRadius']=Tag(TAG_STRING,'0')
    meta['LevelName']=Tag(TAG_STRING,'Bloodborne — Unified City + Editable Gallery');write_nbt(output/'level.dat',level)
    manifest={'schemaVersion':2,'sourceHashes':hash_tree(source),'cityBounds':list(bbox),'dimension':'minecraft:overworld',
        'registryConstants':{'bloodborne_blocks:'+i:{k:v[0]for k,v in d.get('properties',{}).items()if len(v)==1}for i,d in data[0].items()if d.get('unified')},
        'galleryBand':{'minY':platform_y,'maxY':319,'columns':columns,'bounds':[xmin,platform_y,zmin,xmin+totalx-1,319,zmin+totalz-1]},
        'coverage':{'registeredIds':sorted(r['id']for r in rows),'count':len(rows)},'specimens':specimens,
        'editContract':{'spacing':3,'onePlatformPerId':True,'airBaselineIsImplicit':True,'exclude':['support below edit area','marker sign','architecture_part helper carriers'],
            'deleted':'no non-helper object blocks remain on the platform','replaced':'one object block remains','multiBlock':'all non-helper object blocks built within platform X/Z and above support; height up to319'}}
    (output/'editable-gallery-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    os.replace(output,final_output)
    return manifest

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--city',type=Path,required=True);p.add_argument('--citybounds',type=int,nargs=6);a=p.parse_args()
    m=build(a.source,a.output,a.city,a.citybounds);print(json.dumps({'coverage':m['coverage']['count'],'galleryBand':m['galleryBand']}))
