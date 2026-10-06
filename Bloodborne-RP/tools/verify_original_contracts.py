"""Compare the port's metadata/artwork against the supplied, unmodified Forge JAR."""
import argparse,hashlib,json,pathlib,re,subprocess,zipfile
from import_private_assets import rewrite
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--jar',type=pathlib.Path,required=True)
p.add_argument('--javap',type=pathlib.Path,required=True)
p.add_argument('--assets',type=pathlib.Path,required=True)
p.add_argument('--output',type=pathlib.Path,required=True)
a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[1]
if (a.assets/'assets/bloodborne_rp').is_dir():a.assets=a.assets/'assets/bloodborne_rp'
catalog=json.loads((root/'src/main/resources/assets/bloodborne_rp/catalog.json').read_text(encoding='utf8'))
renderers=json.loads((root/'tools/original-renderers.json').read_text())
report={'source_sha256':hashlib.sha256(a.jar.read_bytes()).hexdigest(),'catalog':len(catalog),'renderer_checks':0,'clip_checks':0,'asset_checks':0,'weapon_form_checks':0,'source_idle_loop_omissions':[]}
assert report['source_sha256']=='399541635dfc559580193ca38da074f7158e064647d612fdb6d92236565266d2'
with zipfile.ZipFile(a.jar) as z:
 for key,spec in catalog.items():
  for part in ('model','texture','animation'):
   path=spec[part];src='assets/bloodborne/'+path
   if src not in z.namelist():continue
   actual=a.assets/path
   assert actual.exists(),f'missing asset: {actual}'
   if part=='texture':assert actual.read_bytes()==z.read(src),f'changed original texture: {key} {path}'
   if part=='model':assert json.loads(actual.read_bytes())==rewrite(json.loads(z.read(src))),f'changed original geometry: {key} {path}'
   report['asset_checks']+=1
  src='assets/bloodborne/'+spec['animation']
  if src in z.namelist():
   anim=json.loads(z.read(src))['animations']
   for clip in spec['clips'].values():
    original=anim[clip['name']];assert clip['loop']==(original.get('loop') is True),f'raw loop bool: {clip["name"]}'
    assert clip['seconds']==original.get('animation_length',clip['seconds'])
    report['clip_checks']+=1
   if 'idle' in spec['clips'] and 'loop' not in anim[spec['clips']['idle']['name']]:report['source_idle_loop_omissions'].append(key)
  if key in renderers:
   r=renderers[key];bytecode=subprocess.check_output([str(a.javap),'-classpath',str(a.jar),'-p','-c',r['renderer']],text=True)
   checks=[]
   for match in re.finditer(r'public float get(?:Height|Width)Scale\([^)]*\);\s*Code:([\s\S]*?)(?=\n  (?:public|protected)|\Z)',bytecode):
    constant=re.search(r'// float ([\d.]+)f',match[1])
    if constant:checks.append(float(constant[1]))
    elif 'fconst_1' in match[1]:checks.append(1.0)
   if key=='cleric_beast':assert '// float 1.5f' in bytecode;checks=[1.5]
   if checks:assert all(v==r['scale'] for v in checks),(key,checks,r['scale'])
   assert spec['scale']==r['scale'],f'renderer scale: {key}'
   assert [spec['width'],spec['height']]==r['dimensions'],f'dimensions: {key}'
   report['renderer_checks']+=1
 for key,family in [('saw_cleaver','sawcleaver'),('saw_spear','sawspear'),('boom_hammer','boomhammer')]:
  for form in ('false','true'):
   target=root/'src/main/resources/assets/bloodborne_rp/models/item'/(key+('_extended' if form=='true' else '')+'.json')
   assert json.loads(target.read_text())['display']==json.loads(z.read('assets/bloodborne/models/item/'+family+'_'+form+'.json'))['display']
   report['weapon_form_checks']+=1
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
