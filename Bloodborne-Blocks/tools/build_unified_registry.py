"""Deduplicate individual art entries using the frozen geometric audit.

Saved pivots, materials and physics are retained as private states of one
rotatable registry object. Author-defined and functional blocks are protected.
"""
from __future__ import annotations
import argparse, copy, gzip, hashlib, json, sqlite3, shutil, re
from collections import defaultdict,Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src/main/resources'
SOURCE=ROOT/'build/unified-source/city';CITY=RES/'bloodborne_blocks/city'
AUDIT=ROOT/'build/model-audit-read/model-duplication-audit/technical/audit.json'
FACING=('north','east','south','west')
def serial(v):return json.dumps(v,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode('utf8')
def digest(v):return hashlib.sha256(serial(v)).hexdigest()
def read(p):return json.loads(Path(p).read_bytes())
def write(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(serial(v)+b'\n')
def props(s):return dict(v.split('=',1) for v in s.split(',') if v)
def key(p):return ','.join(k+'='+str(v) for k,v in sorted(p.items()))
def full(i,s):return 'bloodborne_blocks:'+i+('['+s+']' if s else '')
def remap_document(document,definitions,mapping):
    """Rewrite immutable document references from legacy states to registry states."""
    old={d['id']:d for d in definitions['blocks']}
    def target(ident,properties):
        simple=ident.split(':')[-1]
        if simple not in old:return None
        state=key({**old[simple]['default'],**properties})
        return mapping[full(simple,state)]
    def component(value):
        translated=target(value['id'],value['properties'])
        if translated:
            value.update(legacyId=value['id'],legacyProperties=value['properties'],id=translated['id'],properties=translated['properties'])
    result=copy.deepcopy(document)
    for recipe in result['objects']:
        for value in recipe['components']:component(value)
        choices=[]
        for choice in recipe['choices']:
            for value in choice.get('sourceComponents',[]):component(value)
            ident=choice.get('id')
            if ident and ident.split(':')[-1] in old and choice.get('candidates'):
                families=defaultdict(list)
                for candidate in choice['candidates']:
                    translated=target(ident,props(candidate))
                    families[translated['id'].split(':')[-1]].append(key(translated['properties']))
                for family,states in families.items():
                    rewritten=copy.deepcopy(choice)
                    rewritten.update(id=family,candidates=sorted(set(states)),legacyCandidateId=ident)
                    choices.append(rewritten)
            else:choices.append(choice)
        recipe['choices']=choices
    return result
def turn_xyz(x,y,z,n):return ((x,y,z),(1-z,y,x),(1-x,y,1-z),(z,y,1-x))[n%4]
def rotate_mesh(mesh,n):
    if n%4==0:return mesh
    return {'polygons':[{**p,'vertices':[[round(a,6) for a in turn_xyz(*v[:3],n)]+v[3:] for v in p['vertices']]}for p in mesh['polygons']]}
def rotate_geometry(g,n):
    result=copy.deepcopy(g);cells={}
    for text,cell in g['cells'].items():
        x,y,z=map(int,text.split(','));u,w=((x,z),(-z,x),(-x,-z),(z,-x))[n%4];value=copy.deepcopy(cell)
        for field in ('collision','outline','placement'):
            if field not in value:continue
            boxes=[]
            for a,b,c,d,e,f in cell[field]:
                corners=[turn_xyz(x,b,z,n) for x in (a,d) for z in (c,f)]
                boxes.append([round(v,6)for v in[min(v[0]for v in corners),b,min(v[2]for v in corners),max(v[0]for v in corners),e,max(v[2]for v in corners)]])
            value[field]=boxes
        cells[f'{u},{y},{w}']=value
    result['cells']=cells;result['anchor']=[0,0,0];result['render_offset']=[0,0,0]
    cells.setdefault('0,0,0',{'collision':[],'outline':[]});return result
def stream_members(path):
    decoder=json.JSONDecoder()
    with gzip.open(path,'rt',encoding='utf8')as stream:
        buffer='';pos=0;eof=False
        while True:
            if pos>1048576:buffer=buffer[pos:];pos=0
            while pos>=len(buffer)and not eof:
                part=stream.read(1048576);eof=not part;buffer+=part
            while pos<len(buffer)and buffer[pos]in' \r\n\t,{':pos+=1
            if pos<len(buffer)and buffer[pos]=='}':return
            if pos>=len(buffer):
                if eof:return
                continue
            start=pos
            try:
                name,end=decoder.raw_decode(buffer,pos)
                while end<len(buffer)and buffer[end].isspace():end+=1
                if end>=len(buffer):raise json.JSONDecodeError('incomplete',buffer,end)
                if buffer[end]!=':':raise ValueError('mesh member colon missing')
                end+=1
                while end<len(buffer)and buffer[end].isspace():end+=1
                value,end=decoder.raw_decode(buffer,end)
            except json.JSONDecodeError:
                part=stream.read(1048576)
                if not part:raise ValueError('truncated meshes')
                buffer=buffer[start:]+part;pos=0;continue
            pos=end;yield name,value
class MeshStore:
    def __init__(self,source,path):
        self.db=sqlite3.connect(path);self.db.execute('CREATE TABLE IF NOT EXISTS meshes(id TEXT PRIMARY KEY,payload BLOB)');self.db.execute('CREATE TABLE IF NOT EXISTS inputs(path TEXT PRIMARY KEY,sha TEXT)')
        names=('meshes.json.gz','owner-meshes.json.gz')
        changed=any(self.db.execute('SELECT sha FROM inputs WHERE path=?',(n,)).fetchone()!=(hashlib.sha256((source/n).read_bytes()).hexdigest(),)for n in names)
        if changed:self.db.execute('DELETE FROM meshes');self.db.execute('DELETE FROM inputs');self.db.commit()
        for name in names:
            p=source/name;sha=hashlib.sha256(p.read_bytes()).hexdigest()
            if self.db.execute('SELECT sha FROM inputs WHERE path=?',(name,)).fetchone()==(sha,):continue
            for ident,mesh in stream_members(p):self.db.execute('INSERT OR REPLACE INTO meshes VALUES(?,?)',(ident,serial(mesh)))
            self.db.execute('INSERT OR REPLACE INTO inputs VALUES(?,?)',(name,sha));self.db.commit()
    def get(self,ident):
        row=self.db.execute('SELECT payload FROM meshes WHERE id=?',(ident,)).fetchone()
        if row is None:raise ValueError('missing source mesh '+ident)
        return json.loads(row[0])
def protected(d):return not d.get('models')or bool(d.get('document_item'))or d.get('behavior')not in(None,'static')or d['id']=='building_stone_brick_wall'
def behavior(d):
    return {k:d.get(k)for k in('kind','source','semantic','layer','emissive','animated','hardness','resistance','slipperiness','jump','velocity','behavior')}
def cell_family(d,row,mesh,g):
    # The cell fragments are compatibility states, not whole constructions.
    # Similar pieces of the same material/settings/extent and physical type
    # share a carrier; every exact mesh and collision remains a private pose.
    xyz=[v[:3]for p in mesh['polygons']for v in p['vertices']]
    dims=[max(v[i]for v in xyz)-min(v[i]for v in xyz)for i in range(3)]if xyz else[0,0,0]
    if row['normalization']['turns']%2:dims[0],dims[2]=dims[2],dims[0]
    dims=tuple(round(v*4)/4 for v in dims)
    # Numbered atlas slices belong to the same authored material family.
    # This affects only fragment carriers; complete objects still use exact
    # normalized shape evidence. All original slice textures remain in poses.
    materials=Counter(re.sub(r'_[0-9]+$','',p['texture'])for p in mesh['polygons'])
    textures=materials.most_common(1)[0][0]if materials else'empty'
    rotated=rotate_geometry(g,row['normalization']['turns'])
    boxes=[]
    for text,cell in rotated['cells'].items():
        off=list(map(int,text.split(',')))
        boxes.extend([[b[i]+off[i%3]for i in range(6)]for b in cell['collision']])
    if boxes:
        shift=[min(b[i]for b in boxes)for i in range(3)]
        extents=[max(b[i+3]for b in boxes)-min(b[i]for b in boxes)for i in range(3)]
        collision=(len(boxes),tuple(round(v*4)/4 for v in extents))
    else:collision=[]
    return ('cell',digest(behavior(d)),textures,dims,digest(collision))
def plan(source=SOURCE,audit=AUDIT,store=None):
    data=read(source/'definitions.json');byid={d['id']:d for d in data['blocks']};groups=defaultdict(list);seen=set()
    for row in read(audit)['entries']:
        d=byid[row['id']]
        if protected(d):continue
        art=props(row['state']);sel={k:art[k]for k in('variant','visual')if k in art}
        states=[s for s in d['states']if all(props(s).get(k)==v for k,v in sel.items())]
        if store is not None and not d.get('whole_owner'):
            g=read_geometry['blocks'][d['id']]['states'][row['state']];g=read_geometry.get('profiles',{}).get(g.get('ref'),g)
            family=cell_family(d,row,store.get(d['models'][row['state']]),g)
        else:family=('whole',row['normalized_geometry'],digest(behavior(d)))
        groups[family].append({'id':d['id'],'states':states,'turn':row['normalization']['turns'],'shift':row['normalization']['shift'],'evidence':row['key']});seen.update((d['id'],s)for s in states)
    missing=[(d['id'],s)for d in data['blocks']if not protected(d)for s in d['states']if(d['id'],s)not in seen]
    if missing:raise ValueError('audit omits mutable states: '+str(missing[:10]))
    return data,byid,groups
def build(apply=False):
    global read_geometry
    geom=read_geometry=read(SOURCE/'geometry.json');store=MeshStore(SOURCE,ROOT/'build/unified-model-cache.sqlite')
    data,byid,groups=plan(store=store)
    def physical(i,s):
        g=geom['blocks'][i]['states'][s];return geom.get('profiles',{}).get(g.get('ref'),g)
    stage=ROOT/'build/unified-generated';stage.mkdir(parents=True,exist_ok=True);assetroot=stage/'resources';assetroot.mkdir(exist_ok=True)
    blocks=[];gblocks={};profiles={};mapping={};review=[];old_ids=set(byid);mesh_file=stage/'owner-meshes.json.gz'
    with mesh_file.open('wb')as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0)as output:
        output.write(b'{');first=True
        def emit(mid,mesh):
            nonlocal first
            if not first:output.write(b',')
            first=False;output.write(serial(mid)+b':'+serial(mesh))
        keep_meshes=set()
        for d in data['blocks']:
            if not protected(d):continue
            blocks.append(copy.deepcopy(d));gblocks[d['id']]=copy.deepcopy(geom['blocks'][d['id']])
            for s,g in gblocks[d['id']]['states'].items():
                if'ref'in g:profiles[g['ref']]=geom['profiles'][g['ref']]
                mapping[full(d['id'],s)]={'id':'bloodborne_blocks:'+d['id'],'properties':props(s),'rootOffset':[0,0,0]}
            keep_meshes.update(d.get('models',{}).values())
        for mid in sorted(keep_meshes):emit(mid,store.get(mid))
        for gi,(family,rows)in enumerate(sorted(groups.items())):
            poses={};targets={}
            for row in sorted(rows,key=lambda r:r['evidence']):
                d=byid[row['id']]
                for s in row['states']:
                    p=props(s);f=FACING.index(p.get('facing','north'));n=(row['turn']-f)%4;mesh=rotate_mesh(store.get(d['models'][s]),n);g=physical(d['id'],s)
                    if any(g.get('render_offset',[0,0,0])):raise ValueError('nonzero source render offset '+d['id'])
                    g=rotate_geometry(g,n);values=d['states'][s];token=digest({'mesh':mesh,'geometry':g,'state':values})
                    rank=(sum(abs(v)for v in row['shift']),len(g['cells']),row['id'],s)
                    if token not in poses:poses[token]={'mesh':mesh,'geometry':g,'values':values,'rank':rank,'source':[d['id'],s]}
                    targets[full(d['id'],s)]=(token,(-n)%4)
            ordered=sorted(poses,key=lambda t:poses[t]['rank'])
            if len(ordered)>32768:raise ValueError(f'family exceeds private-state limit: {family} {len(ordered)}')
            ident='owner_unified_'+digest(family)[:20];sample=byid[rows[0]['id']];d=copy.deepcopy(sample)
            d.update(id=ident,kind='generic',source='minecraft:stone',logical=False,city_compat=True,whole_owner=True,unified=True,modular=True,extra_facing=True,creative=False,custom_geometry=True,offset='none',full_cube=False,semantic=sample.get('semantic')or'architecture',properties={'facing':list(FACING),'root_anchor':['canonical'],'variant':[str(i)for i in range(len(ordered))]},default={'facing':'north','root_anchor':'canonical','variant':'0'},placement_properties={'variant':'0'},models={},states={})
            for field in('visual_models','behavior','document_item','compat_layers'):d.pop(field,None)
            d['cell_local']=family[0]=='cell'
            states={};indices={t:str(i)for i,t in enumerate(ordered)};emitted=set()
            for token in ordered:
                pose=poses[token]
                # Store one authored mesh; the client applies facing at bake time.
                mid=ident+'_'+digest(pose['mesh'])[:20]
                if mid not in emitted:emit(mid,pose['mesh']);emitted.add(mid)
                for n,facing in enumerate(FACING):
                    p={'facing':facing,'root_anchor':'canonical','variant':indices[token]};s=key(p)
                    g=rotate_geometry(pose['geometry'],n);ref='unified_'+digest(g)[:24];profiles.setdefault(ref,g);states[s]={'ref':ref};d['models'][s]=mid;d['states'][s]=pose['values']
            for old,(token,n)in targets.items():mapping[old]={'id':'bloodborne_blocks:'+ident,'properties':{'facing':FACING[n],'root_anchor':'canonical','variant':indices[token]},'rootOffset':[0,0,0]}
            blocks.append(d);gblocks[ident]={'states':states}
            if len(rows)>1:review.append({'id':ident,'reason':'similar material/size/physics fragments'if family[0]=='cell'else'normalized complete shape','sources':[r['evidence']for r in rows],'privateStates':len(ordered),'defaultSource':poses[ordered[0]]['source']})
            if gi%1000==0:print(f'families={gi}/{len(groups)} mappedStates={len(mapping)}',flush=True)
            if apply:
                textures=sorted({p['texture']for pose in poses.values()for p in pose['mesh']['polygons']})or['minecraft:block/stone']
                write(assetroot/f'assets/bloodborne_blocks/models/block/city/{ident}.json',{'parent':'minecraft:block/block','textures':{'particle':textures[0],**{str(i):v for i,v in enumerate(textures)}},'elements':[]})
                write(assetroot/f'assets/bloodborne_blocks/models/item/{ident}.json',{'parent':'bloodborne_blocks:block/city/'+ident})
                write(assetroot/f'assets/bloodborne_blocks/blockstates/{ident}.json',{'variants':{key({k:v for k,v in props(s).items()if len(d['properties'][k])>1}):{'model':'bloodborne_blocks:block/city/'+ident}for s in d['states']}})
                write(assetroot/f'data/bloodborne_blocks/loot_tables/blocks/{ident}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'bloodborne_blocks:'+ident}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    # gzip context appends its trailer only after JSON has closed.
        output.write(b'}')
    final={**data,'blocks':blocks};geometry={'blocks':gblocks,'profiles':profiles};migrated={'schemaVersion':1,'states':mapping,'counts':{'oldIds':len(old_ids),'newIds':len(blocks),'families':len(groups),'mappedStates':len(mapping),'mergedFamilies':len(review)}}
    document=remap_document(read(SOURCE/'document-final.json'),data,mapping)
    write(stage/'definitions.json',final);write(stage/'geometry.json',geometry);write(stage/'unified-migration.json',migrated);write(stage/'merge-decisions.json',review);write(stage/'document-final.json',document);print(json.dumps(migrated['counts']),flush=True)
    if not apply:return migrated
    removed=old_ids-{d['id']for d in blocks}
    sidecars={}
    for name in('migration.json','owner-runtime-mappings.json'):
        value=read(SOURCE/name)
        for target in value['states'].values():
            old=full(target['id'].split(':')[-1],key(target['properties']))
            if old not in mapping:raise ValueError('sidecar target missing '+old)
            new=mapping[old];target['id']=new['id'];target['properties']=new['properties']
            if'shape'in target:
                g=gblocks[new['id'].split(':')[-1]]['states'][key(new['properties'])];g=profiles.get(g.get('ref'),g);target['shape']=[list(map(int,p.split(',')))for p in g['cells']]
        sidecars[name]=value
    # All sidecar validation finishes before live resource mutation starts.
    for name,v in[('definitions.json',final),('geometry.json',geometry),('unified-migration.json',migrated),('document-final.json',document),*sidecars.items()]:write(CITY/name,v)
    (CITY/'owner-meshes.json.gz').write_bytes(mesh_file.read_bytes())
    with (CITY/'meshes.json.gz').open('wb')as raw,gzip.GzipFile(fileobj=raw,mode='wb',mtime=0)as f:f.write(b'{}')
    shutil.copytree(assetroot,RES,dirs_exist_ok=True)
    for ident in removed:
        for p in(RES/f'assets/bloodborne_blocks/blockstates/{ident}.json',RES/f'assets/bloodborne_blocks/models/item/{ident}.json',RES/f'assets/bloodborne_blocks/models/block/city/{ident}.json',RES/f'data/bloodborne_blocks/loot_tables/blocks/{ident}.json'):
            if p.exists():p.unlink()
    wall=read(SOURCE/'reviewed-wall-family.json');wall['sourceMappingsSha256']=hashlib.sha256((CITY/'owner-runtime-mappings.json').read_bytes()).hexdigest()
    wall['unifiedAliases']={i:{s:mapping[full(i,s)]for s in byid[i]['states']}for i in wall['aliases']}
    wall['legacyModels']={i:byid[i]['models']for i in wall['aliases']};write(CITY/'reviewed-wall-family.json',wall)
    for locale in('ru_ru','en_us'):
        path=RES/f'assets/bloodborne_blocks/lang/{locale}.json';lang=read(path)
        # Avoid an O(translation keys x retired IDs) pruning loop.
        for k in list(lang):
            i=k.removeprefix('block.bloodborne_blocks.')if k.startswith('block.bloodborne_blocks.')else k.removeprefix('city.bloodborne_blocks.').rsplit('.',1)[0]
            if i in removed:del lang[k]
        for d in blocks:
            if d.get('unified'):lang['block.bloodborne_blocks.'+d['id']]=('Архитектура: 'if locale=='ru_ru'else'Architecture: ')+d['semantic']
        write(path,lang)
    eqpath=RES/'bloodborne_blocks/creative-equivalence.json';eq=read(eqpath);eq['redirects']={k:v for k,v in eq['redirects'].items()if k.split('|')[0]not in removed and v.split('|')[0]not in removed};write(eqpath,eq)
    from sync_debug_ids import main as sync_ids
    sync_ids();return migrated
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--apply',action='store_true');a=p.parse_args();build(a.apply)
