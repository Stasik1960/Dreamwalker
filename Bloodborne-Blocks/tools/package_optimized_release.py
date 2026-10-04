"""Package the byte-preserved city and its tested optimized mod together."""
import argparse,hashlib,json,shutil,zipfile
from pathlib import Path

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path):return json.loads(Path(path).read_bytes())
def dump(path,value):Path(path).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--modpack',type=Path,required=True);args=parser.parse_args()
    root=Path(__file__).resolve().parents[1];repo=root.parent
    output=args.output.resolve();output.mkdir(parents=True,exist_ok=True)
    source=repo/'output/Bloodborne-Unified-City-Gallery-Corrected-2026-10-03'
    jar=root/'build/libs/bloodborne-blocks-2.1.0-unified-gallery.3.jar';jar_hash=sha(jar)
    log=root/'build/optimization-check-build.log'
    if 'BUILD SUCCESSFUL'not in log.read_text(encoding='utf-8',errors='replace'):raise ValueError('full build not successful')
    runtime={}
    for name in('client','server'):
        directory=root/f'build/full-pack-{name}-optimized';launch=read(directory/'launch-proof.json')
        if not launch['passed']or launch['jarSha256']!=jar_hash or launch['packSha256']!=sha(args.modpack):
            raise ValueError('runtime proof does not match final jar/pack: '+name)
        state_proof=read(directory/'worldedit-runtime-proof.json')
        if not state_proof.get('passed'):raise ValueError('runtime transitions did not pass: '+name)
        runtime[name]={'launch':launch,'states':state_proof}
    definitions={}
    for scope in('logical','city'):
        for d in read(root/f'src/main/resources/bloodborne_blocks/{scope}/definitions.json')['blocks']:definitions[d['id']]=d
    usage=read(root/'build/variant-usage-2026-10-04.json');checked=0
    for text,count in usage['inventory']['blocks']['stateCounts'].items():
        if count<=0 or not text.startswith('bloodborne_blocks:'):continue
        ident=text.split('[',1)[0].split(':',1)[1]
        if ident=='architecture_part':continue
        d=definitions[ident];p=dict(d['default'])
        if '['in text:p.update(part.split('=',1)for part in text.split('[',1)[1].rstrip(']').split(',')if '='in part)
        key=','.join(k+'='+str(v)for k,v in sorted(p.items()))
        if key not in d['states']:raise ValueError('used world state absent from final mod: '+text)
        checked+=1
    gallery=read(source/'editable-gallery-manifest.json')
    if len(gallery['specimens'])!=len(definitions)+1:raise ValueError('gallery coverage mismatch')
    expected_states=1+sum(len(d['states'])for d in definitions.values())
    if any(proof['states']['states']!=expected_states for proof in runtime.values()):raise ValueError('runtime registry differs from packaged registry')
    plan=read(root/'build/variant-pruning-plan.json');persisted=read(root/'build/complete-persisted-variant-usage.json')
    shutil.copy2(jar,output/jar.name)
    world_name='Bloodborne-Optimized-City-Gallery-2026-10-04';world_zip=output/(world_name+'.zip')
    hashes={p.relative_to(source).as_posix():sha(p)for p in source.rglob('*')if p.is_file()}
    note={'modVersion':'2.1.0-unified-gallery.3','modSha256':jar_hash,'removedUnusedVariants':plan['counts']['removedVariants'],
          'placedStatesChanged':0,'gallerySpecimens':len(gallery['specimens']),'originalFilesSha256':hashes}
    with zipfile.ZipFile(world_zip,'w',zipfile.ZIP_DEFLATED,compresslevel=6)as archive:
        for path in sorted(source.rglob('*')):
            if path.is_file():archive.write(path,world_name+'/'+path.relative_to(source).as_posix())
        archive.writestr(world_name+'/optimized-mod-version.json',json.dumps(note,ensure_ascii=False,indent=2)+'\n')
    with zipfile.ZipFile(world_zip)as archive:
        if archive.testzip()is not None:raise ValueError('map zip corrupt')
        for relative,digest in hashes.items():
            if hashlib.sha256(archive.read(world_name+'/'+relative)).hexdigest()!=digest:raise ValueError('original world file changed')
    screenshot=sorted((root/'build/full-pack-client-optimized/screenshots').glob('*.png'))[-1]
    shutil.copy2(screenshot,output/'Map-Loaded-Full-Modpack.png')
    fixed_pack=output/'Mods-Fixed-2026-10-04.zip';replaced=[];unchanged={}
    with zipfile.ZipFile(args.modpack)as original,zipfile.ZipFile(fixed_pack,'w',zipfile.ZIP_STORED)as fixed:
        for entry in original.infolist():
            if 'bloodborne'in Path(entry.filename).name.lower()and entry.filename.endswith('.jar'):
                replaced.append(entry.filename);prefix=Path(entry.filename).parent.as_posix();name=jar.name if prefix=='.'else prefix+'/'+jar.name
                fixed.writestr(name,jar.read_bytes())
            else:
                data=original.read(entry);fixed.writestr(entry,data);unchanged[entry.filename]=hashlib.sha256(data).hexdigest()
    if len(replaced)!=1:raise ValueError('expected exactly one Bloodborne jar')
    with zipfile.ZipFile(fixed_pack)as fixed:
        if fixed.testzip()is not None:raise ValueError('fixed modpack corrupt')
        for name,digest in unchanged.items():
            if hashlib.sha256(fixed.read(name)).hexdigest()!=digest:raise ValueError('other mod changed: '+name)
    shutil.copy2(root/'docs/optimization-2026-10-04/README.md',output/'README.md')
    manifest={'version':'2.1.0-unified-gallery.3','pruning':plan['counts'],'registeredIds':len(definitions)+1,'localOnlyFiles':[fixed_pack.name],
              'placedStatesVerified':checked,'originalWorldFilesPreserved':len(hashes),'gallerySpecimens':len(gallery['specimens']),
              'persistedFilesAudited':persisted['counts']['files'],'emptyPoiReference':persisted.get('emptyPoiReference'),
              'runtime':runtime,'fullBuildLogSha256':sha(log),'verificationModpackSha256':sha(args.modpack),
              'otherModFilesUnchanged':len(unchanged),'files':{p.name:{'bytes':p.stat().st_size,'sha256':sha(p)}for p in(output/jar.name,world_zip,fixed_pack,output/'Map-Loaded-Full-Modpack.png',output/'README.md')}}
    provenance=root/'build/supplied-modpack-provenance.json'
    if provenance.is_file():
        value=read(provenance)
        if value['repackedArchiveSha256']!=sha(args.modpack):raise ValueError('recovered modpack provenance mismatch')
        manifest['suppliedModpackProvenance']=value
    dump(output/'package-manifest.json',manifest)
    (output/'SHA256.txt').write_text('\n'.join(v['sha256']+'  '+k for k,v in manifest['files'].items())+'\n',encoding='utf-8')
    print(json.dumps({'version':manifest['version'],'pruning':manifest['pruning'],'mapFilesPreserved':len(hashes),'otherModsUnchanged':len(unchanged)},ensure_ascii=False))
if __name__=='__main__':main()
