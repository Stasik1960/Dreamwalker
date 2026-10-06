"""Build descriptive asset metadata from local audit inputs; copies no source bodies/assets."""
import argparse,json,re
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--source',type=Path,required=True)
p.add_argument('--manifest',type=Path,required=True)
p.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'src/main/resources/assets/bloodborne_rp')
args=p.parse_args()
base=args.source
manifest=json.loads(args.manifest.read_text(encoding='utf-8'))
bindings={b['model_class']:b for b in manifest['bindings']}
files={f['path']:f for f in manifest['files']}
classes={f.stem:f for f in base.rglob('*.java')}
registry=(base/'core/init/EntityInit.java').read_text(encoding='utf-8')
renderer_registration=(base/'core/event/ClientListener.java').read_text(encoding='utf-8')
renderers=dict(re.findall(r'EntityInit\.(\w+)\.get\(\), (\w+)::new',renderer_registration))
catalog={}
controller_loops=json.loads((Path(__file__).parent/"original-controller-loops.json").read_text())
def add(identifier,model_class,width,height,scale=1):
 b=bindings[model_class]
 geometry=b['geo'][0];texture=b['texture'][0]
 anim=next((a for a in b['animation'] if a in files),None)
 clips={}
 if anim:
  for c in files[anim].get('animations',[]):
   suffix=c['name'].split('.')[-1]
   loop=c['loop']
   clips[suffix]={'name':c['name'],'seconds':c['length'] or 1,'loop':loop is True,
                 'loopMode':controller_loops.get(c['name'],'hold_on_last_frame' if loop=='hold_on_last_frame' else 'loop' if loop is True else 'once')}
 strip=lambda path:path.removeprefix('assets/bloodborne/')
 catalog[identifier]={'id':identifier,'model':strip(geometry),'texture':strip(texture),
  'animation':strip(anim) if anim else 'animations/fallback.animation.json',
  'width':width,'height':height,'scale':scale,'displayName':identifier.replace('_',' ').title(),'clips':clips}
for line in registry.splitlines():
 m=re.search(r'(\w+)\s*=\s*ENTITIES.register\("([^"]+)"',line)
 if not m:continue
 field,identifier=m.groups()
 if identifier in ('damage_hitbox','target_dummy'):continue
 dimensions=re.search(r'\.m_20699_\(([\d.]+)f, ([\d.]+)f\)',line)
 if not dimensions:raise ValueError('Missing dimensions '+identifier)
 renderer=renderers[field]
 renderer_text=classes[renderer].read_text(encoding='utf-8')
 model=re.search(r'new (\w+Model)\(',renderer_text)
 if not model:raise ValueError('Missing model for '+identifier)
 scales=re.findall(r'public float get(?:Width|Height)Scale\([^)]*\)\s*\{\s*return ([\d.]+)f',renderer_text)
 if scales and len(set(scales))!=1:raise ValueError('Nonuniform renderer scale '+identifier)
 render_scale=re.search(r'float scaleFactor = ([\d.]+)f',renderer_text)
 scale=float(scales[0]) if scales else float(render_scale[1]) if render_scale else 1
 add(identifier,model.group(1),float(dimensions[1]),float(dimensions[2]),scale)
for identifier,model in {'sawcleaver_false':'SawCleaverModel','sawcleaver_true':'SawCleaverExtendedModel',
 'sawspear_false':'SawSpearModel','sawspear_true':'SawSpearExtendedModel',
 'boomhammer_false':'BoomHammerModel','boomhammer_true':'BoomHammerLitModel'}.items():add(identifier,model,0.25,1)
assert len(catalog)==102,len(catalog)
args.output.mkdir(parents=True,exist_ok=True)
(args.output/'catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
# Own fallback geometry/animations for code-only builds, not copied foreign artwork.
geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.rp_fallback','texture_width':64,'texture_height':64,'visible_bounds_width':4,'visible_bounds_height':8,'visible_bounds_offset':[0,2,0]},'bones':[
 {'name':'body','pivot':[0,12,0],'cubes':[{'origin':[-4,12,-2],'size':[8,12,4],'uv':[16,16]}]},
 {'name':'head','pivot':[0,24,0],'cubes':[{'origin':[-4,24,-4],'size':[8,8,8],'uv':[0,0]}]},
 {'name':'rightarm','pivot':[-5,22,0],'cubes':[{'origin':[-8,12,-2],'size':[4,12,4],'uv':[40,16]}]},
 {'name':'leftarm','pivot':[5,22,0],'cubes':[{'origin':[4,12,-2],'size':[4,12,4],'uv':[32,48]}]},
 {'name':'rightleg','pivot':[-2,12,0],'cubes':[{'origin':[-4,0,-2],'size':[4,12,4],'uv':[0,16]}]},
 {'name':'leftleg','pivot':[2,12,0],'cubes':[{'origin':[0,0,-2],'size':[4,12,4],'uv':[16,48]}]}]}]}
animations={}
for spec in catalog.values():
 for c in spec['clips'].values():
  bones={}
  if 'attack' in c['name']:bones={'rightarm':{'rotation':{'0':[0,0,0],'0.25':[-70,0,0],'0.5':[0,0,0]}}}
  animations[c['name']]={'animation_length':max(c['seconds'],0.5),'loop':c['loop'],'bones':bones}
(args.output/'geo').mkdir(exist_ok=True);(args.output/'animations').mkdir(exist_ok=True)
(args.output/'geo/fallback.geo.json').write_text(json.dumps(geo,indent=2)+'\n',encoding='utf-8')
(args.output/'animations/fallback.animation.json').write_text(json.dumps({'format_version':'1.8.0','animations':animations},indent=2)+'\n',encoding='utf-8')
print(json.dumps({'catalog_records':len(catalog),'copied_artwork':0,'missing_animation_specs':sum(not v['clips'] for v in catalog.values())}))
