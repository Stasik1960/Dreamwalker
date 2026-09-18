"""Build a cell-local, semantically named palette alongside legacy registry IDs.

Legacy resources remain readable. This writes only v2 resources and a deterministic
state-to-cell mapping for the separate offline world converter.
"""
from modular_mesh import *
import argparse, gzip

OUT=RES/'bloodborne_blocks/v2'
FACING=['north','east','south','west']

def dump(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    text=json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n'
    if not path.exists() or path.read_text(encoding='utf-8')!=text:path.write_text(text,encoding='utf-8')

def read_meshes():
    compressed=OUT/'meshes.json.gz'
    if compressed.exists():
        with gzip.open(compressed,'rt',encoding='utf-8') as stream:return json.load(stream)
    return json.loads((OUT/'meshes.json').read_text(encoding='utf-8'))

def dump_meshes(meshes):
    raw=(json.dumps(meshes,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf-8')
    data=gzip.compress(raw,compresslevel=6,mtime=0)
    # Normalize gzip's OS byte too, so Windows/Linux regeneration agrees.
    data=data[:9]+b'\xff'+data[10:]
    destination=OUT/'meshes.json.gz'
    if not destination.exists() or destination.read_bytes()!=data:
        temporary=destination.with_suffix('.gz.tmp');temporary.write_bytes(data);temporary.replace(destination)

def statekey(s):return ','.join(f'{k}={v}' for k,v in sorted(s.items()))

def rotate_boxes(boxes,turns):
    r=rotation(1,turns*90);out=[]
    for b in boxes:
        pts=(np.array(list(itertools.product(*zip(b[:3],b[3:]))))-.5)@r.T+.5
        out.append(np.r_[pts.min(axis=0),pts.max(axis=0)].round(6).clip(0,1).tolist())
    return out

def bounds(polys):
    pts=np.array([v[:3] for p in polys for v in p['vertices']]);lo=pts.min(axis=0).clip(0,1);hi=pts.max(axis=0).clip(0,1)
    for a in range(3):
        if hi[a]-lo[a]<1/64:
            lo[a]=max(0,min(lo[a],1-1/64));hi[a]=max(hi[a],lo[a]+1/64)
    return np.r_[lo,hi].round(6).tolist()

def compact_faces(polys):
    unique={}
    for p in polys:
        vertices=[tuple(round(float(x),6) for x in v) for v in p['vertices']]
        key=(p['texture'],min(tuple(vertices[i:]+vertices[:i]) for i in range(len(vertices))))
        unique.setdefault(key,p)
    items=list(unique.values());by_plane={};removed=set()
    for i,p in enumerate(items):
        v=np.array(p['vertices']);normal=np.cross(v[1,:3]-v[0,:3],v[2,:3]-v[0,:3]);length=np.linalg.norm(normal)
        if length<1e-9:removed.add(i);continue
        key=tuple(sorted(tuple(round(float(x),6) for x in row[:3]) for row in v))
        for j,n in by_plane.get(key,[]):
            if j not in removed and np.dot(normal/length,n)<-.99999 and np.all(alpha_patch(p)>=250) and np.all(alpha_patch(items[j])>=250):
                removed.update((i,j));break
        else:by_plane.setdefault(key,[]).append((i,normal/length))
    return [p for i,p in enumerate(items) if i not in removed]

def is_cube(polys):
    if len(polys)<6:return False
    faces={}
    for p in polys:
        v=np.array(p['vertices'])[:,:3]
        for axis in range(3):
            for edge in (0,1):
                if not np.all(abs(v[:,axis]-edge)<1e-6):continue
                axes=[a for a in range(3) if a!=axis];q=v[:,axes];low=q.min(axis=0);high=q.max(axis=0)
                area=abs(np.dot(q[:,0],np.roll(q[:,1],1))-np.dot(q[:,1],np.roll(q[:,0],1)))/2
                if abs(area-np.prod(high-low))>1e-6:continue
                faces.setdefault((axis,edge),[]).append([*low,*high])
    if len(faces)!=6:return False
    for rects in faces.values():
        xs=sorted(set([0.,1.]+[r[a] for r in rects for a in (0,2)]));area=0
        for a,b in zip(xs,xs[1:]):
            spans=sorted((r[1],r[3]) for r in rects if r[0]<=a+1e-7 and r[2]>=b-1e-7)
            end=0;length=0
            for low,high in spans:
                length+=max(0,high-max(low,end));end=max(end,high)
            area+=(b-a)*length
        if area<1-1e-6:return False
    return True

class Palette:
    def __init__(self):
        self.blocks={};self.meshes={};self.geometry={};self.reuse={};self.names={};self.sources={};self.choices={};self.public=None;self.compacted=set();self.persisted=set()

    def piece(self,polys,collisions,semantic,source,creative=True,luminance=0):
        box=bounds(polys);soft=semantic['category'] in ('tree','bush','plant','floor_decoration')
        # Normalize coarse physics BEFORE identity, or equivalent geometry with
        # obsolete detailed carrier shapes creates thousands of duplicate IDs.
        if soft:collisions=[]
        elif is_cube(polys):collisions=[[0,0,0,1,1,1]]
        elif not collisions or len(collisions)>4:collisions=[box]
        collisions=sorted({tuple(round(float(v),6) for v in b) for b in collisions})
        # Equivalent quarter-turns share a single item, not four duplicate items.
        key=digest(polys)+':'+semantic['category']+':'+str(luminance)+':'+json.dumps(collisions)
        if key in self.reuse:return self.reuse[key]
        hashes=[]
        for turn in range(4):
            ps=rotate_polys(polys,turn);bs=rotate_boxes(collisions,turn)
            h=hashlib.sha256((digest(ps)+semantic['category']+str(luminance)+json.dumps(sorted(bs))).encode()).hexdigest()[:16]
            hashes.append((h,turn,ps,bs))
        h,turn,ps,bs=min(hashes,key=lambda t:t[0]);ident='m_'+h
        if ident not in self.blocks:
            cube=is_cube(ps);box=bounds(ps)
            soft=semantic['category'] in ('tree','bush','plant','floor_decoration')
            if soft:bs=[]
            # Retain only useful coarse step/passages, never texture-level physics.
            if len(bs)>4:bs=[box]
            if not soft and not bs:bs=[box]
            layer='solid'
            for p in ps:
                alpha=alpha_patch(p)
                if semantic['category']=='window' and np.any((alpha>0)&(alpha<250)):layer='translucent';break
                if np.any(alpha<250):layer='cutout'
            self.meshes[ident]={'polygons':ps}
            definition={'id':ident,'source':'minecraft:stone','layer':layer,'kind':'generic','offset':'none',
                'hardness':1.5,'resistance':6,'slipperiness':.6,'velocity':1,'jump':1,
                'extra_facing':True,'custom_geometry':not cube,'full_cube':cube and layer=='solid',
                'emissive':any(p['texture'] in DATA.get('emissive_textures',{}) for p in ps),'animated':False,'orphan':False,'modular':True,'creative':creative,
                'semantic':semantic['category'],'properties':{'facing':FACING},'default':{'facing':'north'},
                'states':{'facing='+f:[0,0,int(luminance)] for f in FACING}}
            self.blocks[ident]=definition;self.names[ident]=semantic['ru'];self.sources[ident]=[source]
            self.geometry[ident]={'states':{}}
            for i,f in enumerate(FACING):
                cb=rotate_boxes(bs,-i);ob=rotate_boxes([box],-i)
                self.geometry[ident]['states']['facing='+f]={'cells':{'0,0,0':{'collision':cb,'outline':ob}},'anchor':[0,0,0],'render_offset':[0,0,0]}
        else:
            self.blocks[ident]['creative']|=creative
            if source not in self.sources[ident]:self.sources[ident].append(source)
        result={'id':ident,'properties':{'facing':FACING[turn]}}
        self.reuse[key]=result
        return result

    def save(self):
        for ident,mesh in self.meshes.items():
            if ident in self.compacted:continue
            compact=compact_faces(mesh['polygons'])
            if compact:mesh['polygons']=compact
            self.compacted.add(ident)
        if self.public is not None:
            for ident,d in self.blocks.items():d['creative']=ident in self.public
            dump(OUT/'public-items.json',sorted(self.public))
        for ident,d in self.blocks.items():
            if d['semantic']=='window':
                if any(np.any((a:=alpha_patch(p))>0) and np.any((a>0)&(a<250)) for p in self.meshes[ident]['polygons']):
                    d['layer']='translucent';d['full_cube']=False
        # Remove only obsolete generated module assets, never legacy/user models.
        import re
        for folder in (ASSETS/'blockstates', ASSETS/'models/block/v2', ASSETS/'models/item', RES/'data/bloodborne_blocks/loot_tables/blocks'):
            if not folder.exists():continue
            assert ROOT.resolve() in folder.resolve().parents
            for path in folder.glob('m_*.json'):
                if re.fullmatch(r'm_[0-9a-f]{16}',path.stem) and path.stem not in self.blocks:path.unlink()
        dump(OUT/'definitions.json',{'blocks':list(self.blocks.values())})
        profiles={};geometry={}
        for ident,block in self.geometry.items():
            states={}
            for state,value in block['states'].items():
                key='v2_'+hashlib.sha256(json.dumps(value,sort_keys=True,separators=(',',':')).encode()).hexdigest()[:20]
                profiles.setdefault(key,value);states[state]={'ref':key}
            geometry[ident]={'states':states}
        dump(OUT/'geometry.json',{'profiles':profiles,'blocks':geometry})
        dump_meshes(self.meshes)
        dump(OUT/'sources.json',self.sources)
        for ident,d in self.blocks.items():
            # Content-addressed modules are immutable. A loaded palette already
            # has these four assets; only new IDs require individual file writes.
            if ident in self.persisted:continue
            dump(ASSETS/'blockstates'/f'{ident}.json',{'variants':{sk:{'model':f'bloodborne_blocks:block/v2/{ident}','y':FACING.index(dict(p.split('=') for p in sk.split(','))['facing'])*90} for sk in d['states']}})
            textures={str(i):t for i,t in enumerate(dict.fromkeys(p['texture'] for p in self.meshes[ident]['polygons']))}
            particle=next(iter(textures.values()),'minecraft:block/stone')
            # Empty baked delegate supplies standard item transformations. Actual
            # immutable mesh quads are filled once by ModularBakedModel.
            dump(ASSETS/'models/block/v2'/f'{ident}.json',{'parent':'minecraft:block/block','textures':{'particle':particle,**textures},'elements':[]})
            dump(ASSETS/'models/item'/f'{ident}.json',{'parent':f'bloodborne_blocks:block/v2/{ident}'})
            dump(RES/'data/bloodborne_blocks/loot_tables/blocks'/f'{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'bloodborne_blocks:'+ident}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
        self.persisted=set(self.blocks)
        for lang in ('ru_ru','en_us'):
            p=ASSETS/'lang'/f'{lang}.json';data=json.loads(p.read_text(encoding='utf-8-sig'))
            data={k:v for k,v in data.items() if not k.startswith('block.bloodborne_blocks.m_') or k.split('.')[-1] in self.blocks}
            for ident,name in self.names.items():data['block.bloodborne_blocks.'+ident]=name if lang=='ru_ru' else 'Bloodborne '+self.blocks[ident]['semantic'].replace('_',' ')
            dump(p,data)
        legacy=json.loads((ROOT/'docs/semantic-catalog-v2.json').read_text(encoding='utf-8-sig'))
        for tag,cats in [('climbable',{'ladder'}),('mineable/pickaxe',{'masonry','column','trim','roof','stairs','slab','window','statue','ornament'}),('mineable/axe',{'bench','container','wood','tree'})]:
            values=['bloodborne_blocks:'+i for i,d in self.blocks.items() if d['semantic'] in cats]
            # Legacy ladders use their state-aware climb handler, not a whole-ID tag.
            if tag!='climbable':values+=['bloodborne_blocks:'+i for i,d in legacy.items() if d['category'] in cats]
            dump(RES/'data/minecraft/tags/blocks'/f'{tag}.json',{'replace':False,'values':sorted(values)})

def build():
    semantics=json.loads((ROOT/'docs/semantic-catalog-v2.json').read_text(encoding='utf-8-sig'))
    # Catalog can include notes/variant overrides in a separate top-level field.
    if 'blocks' in semantics:overrides=semantics.get('model_overrides',{});semantics=semantics['blocks']
    else:overrides={}
    extra=ROOT/'docs/semantic-model-overrides-v2.json'
    if extra.exists():overrides.update(json.loads(extra.read_text(encoding='utf-8-sig')))
    geo=json.loads((RES/'bloodborne_blocks/geometry.json').read_text())
    palette=Palette();mapping={};kept=[];native=[]
    observed=json.loads((ROOT/'docs/world-inventory.json').read_text())['blocks']['stateCounts']
    for index,b in enumerate(DATA['blocks']):
        ident=b['id'];base=semantics[ident];bs=json.loads((ASSETS/'blockstates'/f'{ident}.json').read_text())
        # Functional ordinary stairs/slabs already use native placement rules;
        # only retain these if every visual state really fits its own cell.
        native_ok=b['kind'] in ('stairs','slab') and all(len((geo['profiles'][g['ref']] if 'ref' in g else g)['cells'])<=1 and set((geo['profiles'][g['ref']] if 'ref' in g else g)['cells'])<={'0,0,0'} for g in geo['blocks'][ident]['states'].values())
        keep=base.get('keep_large',False) or native_ok or base['category'] in ('ornament','statue','container','lamp','bench','door','tree')
        if keep and ident!='spruce_button':kept.append(ident)
        if native_ok:native.append(ident)
        state_map={};cache={};observed_keys=set()
        for worldkey in observed:
            if not worldkey.startswith('bloodborne_blocks:'+ident+'[') and worldkey!='bloodborne_blocks:'+ident:continue
            props=dict(b['default'])
            if '[' in worldkey:props.update(dict(p.split('=') for p in worldkey.split('[',1)[1].rstrip(']').split(',') if p))
            observed_keys.add(statekey(props))
        # Expose each authored variant, not every combination of obsolete carrier
        # switches/orientations. Also include every state actually used by the map.
        signatures=set();selected={};default_key=statekey(b['default'])
        for sk in [default_key]+list(b['states']):
            s=dict(x.split('=') for x in sk.split(',') if x)
            if s.get('assembled')=='true' and sk not in observed_keys:continue
            apps=[a[0] for a in applications(bs,s)]
            signature=json.dumps([a['model'] for a in apps])
            if signature in signatures and sk not in observed_keys and sk!=default_key:continue
            signatures.add(signature);selected[sk]=b['states'][sk]
        for sk,sv in selected.items():
            state=dict(x.split('=') for x in sk.split(',') if x)
            if ident=='spruce_button':state_map[sk]=[];continue
            if keep:state_map[sk]={'keep':True};continue
            apps=[a[0] for a in applications(bs,state)]
            appkey=json.dumps(apps,sort_keys=True)+':'+str(sv[2])
            if appkey in cache:state_map[sk]=cache[appkey];continue
            semantic=base
            for a in apps:
                name=a['model'].split('/')[-1]
                for pattern,value in overrides.items():
                    if a['model']==pattern or name==pattern or (pattern.endswith('*') and name.startswith(pattern[:-1])):semantic=value;break
            merged={}
            for a in apps:
                for c,polys in cells_for_app(json.dumps(a,sort_keys=True)).items():merged.setdefault(c,[]).extend(polys)
            g=geo['blocks'][ident]['states'][sk];g=geo['profiles'][g['ref']] if 'ref' in g else g
            pieces=[]
            for c,ps in sorted(merged.items()):
                cb=g['cells'].get(','.join(map(str,c)),{}).get('collision',[])
                piece=palette.piece(ps,cb,semantic,ident,True,sv[2]);pieces.append({'offset':list(c),**piece})
            state_map[sk]=pieces;cache[appkey]=pieces
        mapping[ident]={'default':b['default'],'states':state_map}
        if index%20==0:print(index,ident,'unique modules',len(palette.blocks),flush=True)
    palette.public={p['id'] for spec in mapping.values() for value in [spec['states'][statekey(spec['default'])]] if isinstance(value,list) for p in value}
    palette.save();dump(OUT/'migration.json',mapping)
    dump(OUT/'legacy-creative.json',kept)
    for lang in ('ru_ru','en_us'):
        path=ASSETS/'lang'/f'{lang}.json';names=json.loads(path.read_text(encoding='utf-8'))
        for ident,s in semantics.items():names['block.bloodborne_blocks.'+ident]=s['ru'] if lang=='ru_ru' else 'Bloodborne '+s['category'].replace('_',' ')
        dump(path,names)
    dump(ROOT/'docs/modular-palette-report.json',{'modules':len(palette.blocks),'retained_large_or_native':kept,'native_building_blocks':native,'legacy_ids':len(DATA['blocks']),'mapping_states':sum(len(v['states']) for v in mapping.values())})
    print('PALETTE COMPLETE',len(palette.blocks),'modules',len(kept),'legacy/native items',flush=True)

if __name__=='__main__':build()
