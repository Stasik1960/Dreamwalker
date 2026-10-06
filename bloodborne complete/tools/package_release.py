"""Package complete source/art, tested JAR, city/gallery, ALT skeleton and local dependencies."""
import argparse,hashlib,json,os,pathlib,shutil,zipfile

ROOT=pathlib.Path(__file__).resolve().parents[1]
VERSION='1.0.0-complete.1'
EXCLUDED={'.git','.gradle','build','build-private','run','local-inputs','releases','__pycache__'}
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def archive(output,entries):
 with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for path,name in sorted(entries,key=lambda x:x[1]):
   info=zipfile.ZipInfo(name,(2000,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(0o755 if name.endswith('/gradlew') else 0o644)<<16;z.writestr(info,path.read_bytes())
 with zipfile.ZipFile(output) as z:assert z.testzip() is None
def source_files():
 for folder,dirs,files in os.walk(ROOT):
  dirs[:]=[n for n in dirs if n not in EXCLUDED and not n.startswith(('private-','_pipeline'))]
  for name in files:
   p=pathlib.Path(folder)/name
   if p.suffix not in ('.log','.pyc','.bak','.tmp') and name not in ('session.lock',):yield p
def manifest(folder):
 return {p.relative_to(folder).as_posix():{'sha256':digest(p),'bytes':p.stat().st_size} for p in sorted(folder.rglob('*')) if p.is_file() and p.name!='checksums.json'}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--downloads',type=pathlib.Path,required=True);p.add_argument('--dependency-cache',type=pathlib.Path,required=True);a=p.parse_args()
 release=ROOT.parent/'releases/bloodborne-dw'/VERSION;local=a.downloads/'Bloodborne-DW-1.20.1-20261006'
 if release.exists() or local.exists():raise ValueError('Refusing existing release/local destination')
 jar=ROOT/'build/libs'/f'bloodborne-dw-{VERSION}.jar';sources=ROOT/'build/libs'/f'bloodborne-dw-{VERSION}-sources.jar'
 with zipfile.ZipFile(jar) as z:
  assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
  mod=json.loads(z.read('fabric.mod.json'));assert mod['id']=='bloodborne_dw' and mod['version']==VERSION
  assert z.read('assets/bloodborne_dw/catalog.json')==(ROOT/'src/main/resources/assets/bloodborne_dw/catalog.json').read_bytes()
  assert 'assets/bloodborne_rp/geo/cleric_beast.geo.json' in z.namelist()
  assert len([n for n in z.namelist() if n.startswith('assets/bloodborne_dw/blockstates/') and n.endswith('.json')])==580
 proof=json.loads((ROOT/'docs/production-smoke-verification.json').read_text())
 assert proof['jar_sha256']==digest(jar),'Production proof predates this JAR'
 for case in ('city','city-restart','gallery','gallery-restart'):assert proof[case]['started'] and proof[case]['exit_code']==0
 assert len(proof['gallery-restart']['verified_gallery_markers']['BLOCK'])==580
 release.mkdir(parents=True);shutil.copy2(jar,release/jar.name);shutil.copy2(sources,release/sources.name)
 for name in ('Bloodborne-DW-v16.zip','Bloodborne-DW-v16-gallery.zip'):
  world=ROOT/'build/publish-worlds'/name
  with zipfile.ZipFile(world) as z:assert z.testzip() is None
  shutil.copy2(world,release/name)
 with zipfile.ZipFile(release/'Bloodborne-DW-v16-gallery.zip') as z:(release/'gallery-manifest.json').write_bytes(z.read('gallery-manifest.json'))
 for name in ('README.md','ASSET-NOTICE.md','VALIDATION.md'):shutil.copy2(ROOT/name,release/name)
 shutil.copytree(ROOT/'docs',release/'docs')
 source_zip=release/f'bloodborne-dw-{VERSION}-source.zip';archive(source_zip,[(p,'bloodborne complete/'+p.relative_to(ROOT).as_posix()) for p in source_files()])
 catalog=json.loads((ROOT/'src/main/resources/assets/bloodborne_dw/catalog.json').read_text(encoding='utf-8'))
 template=release/'Bloodborne-DW-ALT-template.zip'
 with zipfile.ZipFile(template,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  z.writestr('pack.mcmeta',json.dumps({'pack':{'pack_format':15,'description':'Bloodborne DW ALT skeleton; initially identical to BASE'}}))
  z.writestr('assets/bloodborne_dw/alt_render_layers.json','{}\n')
  z.writestr('README.md',(ROOT/'docs/HUMAN-DECISIONS-RU.md').read_bytes())
  for b in catalog['blocks']:
   for path in b['alt_model_paths']:
    name='assets/bloodborne_dw/models/'+path+'.json';z.writestr(name,(ROOT/'src/main/resources'/name).read_bytes())
 with zipfile.ZipFile(template) as z:assert z.testzip() is None
 (release/'checksums.json').write_text(json.dumps(manifest(release),indent=2)+'\n',encoding='utf-8')
 for path in release.rglob('*'):
  if path.is_file() and path.stat().st_size>=100_000_000:raise ValueError('GitHub file size limit: '+str(path))
 local.mkdir();mods=local/'mods';mods.mkdir();shutil.copy2(jar,mods/jar.name)
 for group,artifact,version in [('net.fabricmc.fabric-api','fabric-api','0.92.9+1.20.1'),('software.bernie.geckolib','geckolib-fabric-1.20.1','4.4.9')]:
  wanted=f'{artifact}-{version}.jar';matches=list((a.dependency_cache/group/artifact/version).rglob(wanted));assert len(matches)==1,'Dependency missing/ambiguous: '+wanted;shutil.copy2(matches[0],mods/wanted)
 for file in release.iterdir():
  if file.is_file() and file.name not in (jar.name,'checksums.json'):shutil.copy2(file,local/file.name)
 shutil.copytree(release/'docs',local/'docs')
 (local/'checksums.json').write_text(json.dumps(manifest(local),indent=2)+'\n',encoding='utf-8')
 print(json.dumps({'release':str(release),'local':str(local),'jar':str(mods/jar.name),'source':str(local/source_zip.name),'release_files':len(manifest(release)),'local_files':len(manifest(local))},indent=2))
if __name__=='__main__':main()
