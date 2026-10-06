"""Build real Anvil platforms above v16; frozen-ID manual-edit protocol, no future edits."""
import argparse,collections,copy,hashlib,json,math,pathlib,random,zipfile
import complete_world_io as w
ROOT=pathlib.Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def state(name,props=None):return w.Tag(w.TAG_COMPOUND,{'Name':w.Tag(w.TAG_STRING,name),**({'Properties':w.Tag(w.TAG_COMPOUND,{k:w.Tag(w.TAG_STRING,v) for k,v in props.items()})} if props else {})})
def layout(ids,edges,width=25):
 # Optimize the measured six-face co-occurrence graph on a 2D four-neighbor grid.
 # Stable seed and lexical ties ensure the human receives a reproducible layout.
 height=math.ceil(len(ids)/width);size=width*height;graph=collections.defaultdict(dict)
 for pair,weight in edges.items():
  a,b=pair.split(',');graph[a][b]=weight;graph[b][a]=weight
 def near(i):return [j for j in (i-1,i+1,i-width,i+width) if 0<=j<size and abs(i%width-j%width)+abs(i//width-j//width)==1]
 def score(order):return sum(graph.get(order[i],{}).get(order[j],0) for i in range(size) for j in near(i) if j>i)
 initial=list(ids)+[None]*(size-len(ids));best=initial[:];baseline=score(best);rng=random.Random(16015)
 # Start at a graph hub and greedily fill neighboring cells.
 remaining=set(ids);greedy=[None]*size
 for i in range(size):
  if not remaining:break
  chosen=max(remaining,key=lambda b:(sum(graph.get(b,{}).get(greedy[j],0) for j in near(i)),sum(graph.get(b,{}).values()),-int(b)))
  greedy[i]=chosen;remaining.remove(chosen)
 if score(greedy)>score(best):best=greedy
 adjacency=[near(i) for i in range(size)]
 def local(order,affected):return sum(graph.get(order[i],{}).get(order[j],0) for i,j in affected)
 for _ in range(80000):
  a,b=rng.sample(range(size),2);affected={(min(i,j),max(i,j)) for i in (a,b) for j in adjacency[i]};before=local(best,affected)
  best[a],best[b]=best[b],best[a]
  if local(best,affected)<before:best[a],best[b]=best[b],best[a]
 return best,width,{'algorithm':'measured-six-face-greedy-plus-80000-pair-swaps','weighted_score':score(best),'id_order_score':baseline,'seed':16015,'grid_neighbors':4}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--world',type=pathlib.Path,required=True);p.add_argument('--report',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);p.add_argument('--map-analysis',type=pathlib.Path,required=True);a=p.parse_args()
 if a.output.exists():raise ValueError('Refusing to overwrite '+str(a.output))
 catalog_path=ROOT/'src/main/resources/assets/bloodborne_dw/catalog.json';catalog=json.loads(catalog_path.read_text(encoding='utf-8'));blocks=catalog['blocks'];byid={b['id']:b for b in blocks};conversion=json.loads(a.report.read_text(encoding='utf-8'))
 assert conversion['max_nonair_y']==319 and sha(a.world)==conversion['output_sha256']
 audit=next(x for x in json.loads(a.map_analysis.read_text(encoding='utf-8'))['archives'] if x['file']=='bbmc_v16_map (1).zip');observed=collections.defaultdict(list)
 for s,count in audit['block_state_counts'].items():observed[s.split('[',1)[0]].append((count,s))
 order,width,metrics=layout(list(byid),conversion['adjacency']);edits=collections.defaultdict(dict);platforms=[];base_y=336;platform_size=7;stride=9
 def put(x,y,z,s):edits[(x//16,z//16)][(x%16,y,z%16)]=s
 for slot,id in enumerate(order):
  if id is None:continue
  b=byid[id];x=-80+(slot%width)*stride;z=-1104+(slot//width)*stride;rx=x+3;rz=z+3;ry=base_y+1
  props={}
  if observed[b['source']]:
   key=max(observed[b['source']])[1]
   if '[' in key:props=dict(v.split('=',1) for v in key.split('[',1)[1].rstrip(']').split(','))
  props['visual']='base'
  if props.get('half') in ('upper','lower'):props['half']='lower'
  if props.get('part') in ('head','foot'):props['part']='foot'
  for dx in range(platform_size):
   for dz in range(platform_size):put(x+dx,base_y,z+dz,state('minecraft:stone'))
  source=b['source'].split(':',1)[1]
  # Native substrate/support preserves placement validity; it is not another
  # catalog block and is excluded from future composite capture.
  substrate='minecraft:grass_block' if any(t in source for t in ('sapling','flower','tulip','orchid','allium','dandelion','poppy','fern','grass','bush','roots','fungus','mushroom')) else 'minecraft:stone'
  if source in ('dead_bush','cactus'):substrate='minecraft:sand'
  if source in ('wheat','carrots','potatoes','beetroots','melon_stem','pumpkin_stem'):substrate='minecraft:farmland'
  if source=='soul_fire':substrate='minecraft:soul_sand'
  put(rx,base_y,rz,state(substrate))
  supports=[(rx,base_y,rz)];direction={'north':(0,-1),'south':(0,1),'east':(1,0),'west':(-1,0)}
  facing=props.get('facing','north');dx,dz=direction.get(facing,(0,-1))
  if 'wall_' in source or props.get('face')=='wall':put(rx-dx,ry,rz-dz,state('minecraft:stone'));supports.append((rx-dx,ry,rz-dz))
  if props.get('hanging')=='true' or props.get('face')=='ceiling':put(rx,ry+1,rz,state('minecraft:stone'));supports.append((rx,ry+1,rz))
  root=state('bloodborne_dw:'+id,props);put(rx,ry,rz,root);logical_cells=[(rx,ry,rz)]
  if props.get('half')=='lower':upper=dict(props,half='upper');put(rx,ry+1,rz,state('bloodborne_dw:'+id,upper));logical_cells.append((rx,ry+1,rz))
  if props.get('part')=='foot':head=dict(props,part='head');put(rx+dx,ry,rz+dz,state('bloodborne_dw:'+id,head));logical_cells.append((rx+dx,ry,rz+dz))
  # Sign is below root height and remains outside the reserved construction volume.
  label=(x,base_y+1,z);put(*label,state('minecraft:oak_sign',{'rotation':'0','waterlogged':'false'}))
  platforms.append({'block_id':id,'source':b['source'],'grid_slot':slot,'root':[rx,ry,rz],'initial_state':w.block_state_key(root),'logical_block_cells':logical_cells,'platform_bounds':{'min':[x,base_y,z],'max':[x+6,base_y,z+6]},'construction_bounds':{'min':[x+1,ry,z+1],'max':[x+5,ry+14,z+5]},'excluded_support_cells':supports,'label':label})
 dim={'ultrawarm':False,'natural':True,'coordinate_scale':1.0,'has_skylight':True,'has_ceiling':False,'ambient_light':0.0,'piglin_safe':False,'bed_works':True,'respawn_anchor_works':False,'has_raids':True,'logical_height':576,'min_y':-64,'height':576,'infiniburn':'#minecraft:infiniburn_overworld','effects':'minecraft:overworld','monster_spawn_block_light_limit':0,'monster_spawn_light_level':{'type':'minecraft:uniform','value':{'min_inclusive':0,'max_inclusive':7}}}
 dimpath=ROOT/'src/main/resources/data/bloodborne_dw/dimension_type/gallery_overworld.json';dimpath.parent.mkdir(parents=True,exist_ok=True);dimpath.write_text(json.dumps(dim,indent=2)+'\n')
 touched=0;roots_found=set();chunk_edits=dict(edits);a.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(a.world) as inp,zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as out:
  for info in inp.infolist():
   data=inp.read(info);name=info.filename
   if data and name.startswith('region/') and name.endswith('.mca'):
    reg=w.RegionFile(data)
    for c in reg.chunks():
     nbt=c.nbt();r=w.compound(nbt.root);key=(r['xPos'].value,r['zPos'].value);changes=chunk_edits.pop(key,{})
     # The gallery dimension uses 10-bit heightmaps. Retain every original
     # value and repack it, then raise affected columns to the platform top.
     tops={}
     for (x,y,z),s in changes.items():tops[x+16*z]=max(tops.get(x+16*z,0),y+65)
     for hm in r.get('Heightmaps',w.Tag(w.TAG_COMPOUND,{})).value.values():
      vals=w.unpack_palette_indices(hm.value,512,256)
      for column,height in tops.items():vals[column]=max(vals[column],height)
      hm.value=w.pack_palette_indices(vals,1024)
     if changes:
      sections=r['sections'].value;groups=collections.defaultdict(dict)
      for (x,y,z),s in changes.items():groups[y//16][(y%16)*256+z*16+x]=s
      for sy,positions in groups.items():
       section=next((s for s in sections if w.compound(s)['Y'].value==sy),None)
       if section is None:
        section=w.Tag(w.TAG_COMPOUND,{'Y':w.Tag(w.TAG_BYTE,sy),'biomes':w.Tag(w.TAG_COMPOUND,{'palette':w.Tag(w.TAG_LIST,[w.Tag(w.TAG_STRING,'minecraft:plains')],w.TAG_STRING)}),'block_states':w.Tag(w.TAG_COMPOUND,{'palette':w.Tag(w.TAG_LIST,[state('minecraft:air')],w.TAG_COMPOUND)})});sections.append(section)
       palette,ix=w.section_blocks(section);lookup={w.block_state_key(s):i for i,s in enumerate(palette)}
       for index,s in positions.items():
        sk=w.block_state_key(s)
        if sk not in lookup:lookup[sk]=len(palette);palette.append(s)
        ix[index]=lookup[sk]
       bs={'palette':w.Tag(w.TAG_LIST,palette,w.TAG_COMPOUND)}
       if len(palette)>1:bs['data']=w.Tag(w.TAG_LONG_ARRAY,w.pack_palette_indices(ix,len(palette)))
       w.compound(section)['block_states']=w.Tag(w.TAG_COMPOUND,bs)
      be_tag=r.setdefault('block_entities',w.Tag(w.TAG_LIST,[],w.TAG_COMPOUND));be_tag.list_type=w.TAG_COMPOUND;be=be_tag.value
      # Native providers need their real block entities in the saved world,
      # independently of whether a client has visited the platform yet.
      providers={'beehive':'beehive','daylight_detector':'daylight_detector','lectern':'lectern','beacon':'beacon','end_gateway':'end_gateway','end_portal':'end_portal','ender_chest':'ender_chest'}
      for pform in platforms:
       provider=providers.get(pform['source'].split(':',1)[1])
       if provider:
        for x,y,z in pform['logical_block_cells']:
         if (x//16,z//16)==key:be.append(w.Tag(w.TAG_COMPOUND,{'id':w.Tag(w.TAG_STRING,'minecraft:'+provider),'x':w.Tag(w.TAG_INT,x),'y':w.Tag(w.TAG_INT,y),'z':w.Tag(w.TAG_INT,z)}))
      for pform in platforms:
       x,y,z=pform['label']
       if (x//16,z//16)!=key:continue
       txt=[json.dumps({'text':pform['block_id']}),json.dumps({'text':pform['source'].split(':')[1][:18]}),'{"text":""}','{"text":""}'];be.append(w.Tag(w.TAG_COMPOUND,{'id':w.Tag(w.TAG_STRING,'minecraft:sign'),'x':w.Tag(w.TAG_INT,x),'y':w.Tag(w.TAG_INT,y),'z':w.Tag(w.TAG_INT,z),**{'Text'+str(i+1):w.Tag(w.TAG_STRING,t) for i,t in enumerate(txt)}}))
      w.invalidate_chunk_lighting(r);touched+=1
      for pform in platforms:
       x,y,z=pform['root']
       if (x//16,z//16)==key:
        section=next(s for s in sections if w.compound(s)['Y'].value==y//16);pal,ix=w.section_blocks(section);assert w.block_state_key(pal[ix[(y%16)*256+(z%16)*16+x%16]])==pform['initial_state'];roots_found.add(pform['block_id'])
     reg.set_chunk(c.x,c.z,nbt,timestamp=c.timestamp)
    data=reg.to_bytes();print(json.dumps({'gallery_region':name,'modified_platform_chunks':touched}),flush=True)
   elif name=='level.dat':
    nbt=w.decode_nbt(data,compressed='gzip');d=w.compound(w.compound(nbt.root)['Data']);wg=w.compound(w.compound(d['WorldGenSettings'])['dimensions']);w.compound(wg['minecraft:overworld'])['type']=w.Tag(w.TAG_STRING,'bloodborne_dw:gallery_overworld');d['LevelName']=w.Tag(w.TAG_STRING,'Bloodborne DW — gallery');d['SpawnX']=w.Tag(w.TAG_INT,-77);d['SpawnY']=w.Tag(w.TAG_INT,338);d['SpawnZ']=w.Tag(w.TAG_INT,-1101);d['GameType']=w.Tag(w.TAG_INT,1);d['allowCommands']=w.Tag(w.TAG_BYTE,1)
    for key in ('Player',):d.pop(key,None)
    packs=w.compound(d['DataPacks']);enabled=packs['Enabled'].value
    for pack in ('fabric','file/bloodborne_gallery'):
     if pack not in [v.value for v in enabled]:enabled.append(w.Tag(w.TAG_STRING,pack))
    data=w.encode_nbt(nbt,compressed='gzip')
   out.writestr(name,data)
  assert not chunk_edits,'Some gallery chunks are absent: '+str(list(chunk_edits)[:10]);assert len(roots_found)==len(blocks)
  manifest={'schema_version':3,'source_world_sha256':sha(a.world),'catalog_sha256':sha(catalog_path),'gallery_platform_y':336,'source_max_nonair_y':319,'platform_size':7,'gap':2,'grid_width':width,'layout_metrics':metrics,'platforms':platforms,'validated_root_count':len(roots_found),'modified_platform_chunks':touched,'future_edits_applied':False}
  out.writestr('gallery-manifest.json',json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');out.writestr('datapacks/bloodborne_gallery/pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Extended build height for Bloodborne manual gallery'}}));out.writestr('datapacks/bloodborne_gallery/data/bloodborne_dw/dimension_type/gallery_overworld.json',json.dumps(dim))
 a.output.with_suffix('.gallery-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 with zipfile.ZipFile(a.output) as z:assert z.testzip() is None
 print(json.dumps({'output':str(a.output),'actual_validated_roots':len(roots_found),'layout':metrics,'sha256':sha(a.output)}))
if __name__=='__main__':main()
