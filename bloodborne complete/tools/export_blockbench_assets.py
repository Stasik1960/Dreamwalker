"""Export original, editable GeckoLib models and complete companion assets without decompiling code."""
import argparse,hashlib,json,pathlib,re,zipfile
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--jar',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True)
p.add_argument('--ported-assets',type=pathlib.Path,help='Optional companion repaired JSON files from the Fabric import')
a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[1];a.output.mkdir(parents=True,exist_ok=True)
if a.ported_assets and (a.ported_assets/'assets/bloodborne_rp').is_dir():a.ported_assets=a.ported_assets/'assets/bloodborne_rp'
catalog=json.loads((root/'src/main/resources/assets/bloodborne_rp/catalog.json').read_text(encoding='utf8'))
def write(path,data):
 out=a.output/path;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
records=[]
with zipfile.ZipFile(a.jar) as z:
 prefix='assets/bloodborne/'
 for name in z.namelist():
  if name.startswith(prefix) and not name.endswith('/'):
   rel=pathlib.PurePosixPath(name)
   assert '..' not in rel.parts
   write('original/'+name,z.read(name))
 sounds=json.loads(z.read(prefix+'sounds.json'))
 for key,spec in catalog.items():
  if prefix+spec['model'] not in z.namelist():continue
  base='models/'+key+'/'
  refs={}
  for part in ('model','texture','animation'):
   src=prefix+spec[part]
   if src in z.namelist():
    dest=base+spec[part];write(dest,z.read(src));refs[part]=dest
    if a.ported_assets and (a.ported_assets/spec[part]).exists():
     write('port-variants/'+spec[part],(a.ported_assets/spec[part]).read_bytes())
  # Include all sound files/events beside each model. This deliberately avoids guessed bindings:
  # several original event IDs refer to sibling mobs, and some OGG references are absent.
  metadata={'id':key,'files':refs,'render_scale':spec['scale'],'dimensions':[spec['width'],spec['height']],
   'sounds_index':'../../original/assets/bloodborne/sounds.json','sounds_directory':'../../original/assets/bloodborne/sounds',
   'ported_variant_directory':'../../port-variants' if a.ported_assets else None,
   'animation_clips':spec['clips'],'note':'Source geometry/animation/PNG bytes are unchanged. Runtime loop modes can override JSON defaults.'}
  write(base+'model.json',json.dumps(metadata,ensure_ascii=False,indent=2).encode('utf8'));records.append(metadata)
 missing=[]
 for event,value in sounds.items():
  for sound in value.get('sounds',[]):
   if isinstance(sound,dict):
    if sound.get('type')=='event':continue
    sound=sound['name']
   namespace,_,path=sound.partition(':')
   if not path:path=namespace;namespace='bloodborne'
   name=f'assets/{namespace}/sounds/{path}.ogg'
   if name not in z.namelist():missing.append({'event':event,'file':name})
 files=[{'path':f.relative_to(a.output).as_posix(),'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in sorted(a.output.rglob('*')) if f.is_file() and f.name!='export-manifest.json']
 report={'source_sha256':hashlib.sha256(a.jar.read_bytes()).hexdigest(),'models':records,'missing_source_sounds':missing,'files':files,
  'format':'Original Bedrock/GeckoLib .geo.json, .animation.json, PNG, OGG. All original assets also preserved under original/.'}
 write('export-manifest.json',json.dumps(report,ensure_ascii=False,indent=2).encode('utf8'))
 print(json.dumps({'models':len(records),'files':len(files),'missing_source_sound_references':len(missing)}))
