"""Package the reviewed public source/JAR and a separate full local evaluation bundle."""
import argparse
import hashlib
import json
import pathlib
import shutil
import zipfile

VERSION='1.0.0-rp.1'
EXCLUDED={'.gradle','build','build-private','run','private-assets','private-release','releases','__pycache__'}

def checksum(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def archive(output,entries):
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for path,name in sorted(entries,key=lambda e:e[1]):
            info=zipfile.ZipInfo(name,date_time=(2000,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=(0o755 if name.endswith('/gradlew') else 0o644)<<16
            z.writestr(info,path.read_bytes())

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--downloads',type=pathlib.Path,required=True)
    parser.add_argument('--dependency-cache',type=pathlib.Path,required=True)
    args=parser.parse_args()
    root=pathlib.Path(__file__).resolve().parents[1]
    public=root/'build/libs'/f'bloodborne-rp-{VERSION}.jar'
    private=root/'build-private/libs'/f'bloodborne-rp-{VERSION}.jar'
    with zipfile.ZipFile(public) as z:
        names=z.namelist()
        if len(names)!=len(set(names)) or any(n.endswith(('.ogg','.png','.class')) and n.startswith('assets/') for n in names):
            raise SystemExit('Unexpected artwork or duplicate entry in public JAR')
        geo=[n for n in names if n.startswith('assets/bloodborne_rp/geo/') and n.endswith('.json')]
        if set(geo)!={'assets/bloodborne_rp/geo/fallback.geo.json','assets/bloodborne_rp/geo/fallback_weapon.geo.json'}:
            raise SystemExit('Public geometry is not limited to original fallback assets')
    with zipfile.ZipFile(private) as z:
        if len(z.namelist())!=len(set(z.namelist())) or 'assets/bloodborne_rp/geo/cleric_beast.geo.json' not in z.namelist():
            raise SystemExit('Incomplete or duplicated private JAR')
    release=root/'releases'/VERSION;release.mkdir(parents=True,exist_ok=True)
    public_dest=release/f'bloodborne-rp-{VERSION}-public.jar';shutil.copy2(public,public_dest)
    source_zip=release/f'bloodborne-rp-{VERSION}-source.zip'
    source_entries=[(p,'Bloodborne-RP/'+p.relative_to(root).as_posix()) for p in root.rglob('*')
        if p.is_file() and not EXCLUDED.intersection(p.relative_to(root).parts) and p.suffix not in ('.log','.pyc')]
    archive(source_zip,source_entries)
    local=args.downloads/'Bloodborne-RP-1.20.1-20261004';local.mkdir(parents=True,exist_ok=True)
    mods=local/'mods';mods.mkdir(exist_ok=True)
    private_dest=mods/f'bloodborne-rp-{VERSION}-full-local.jar';shutil.copy2(private,private_dest)
    for group,artifact,version in [('net.fabricmc.fabric-api','fabric-api','0.92.9+1.20.1'),('software.bernie.geckolib','geckolib-fabric-1.20.1','4.4.9')]:
        wanted=f'{artifact}-{version}.jar';matches=list((args.dependency_cache/group/artifact/version).rglob(wanted))
        if len(matches)!=1: raise SystemExit('Missing or ambiguous runtime dependency: '+wanted)
        shutil.copy2(matches[0],mods/wanted)
    shutil.copy2(source_zip,local/source_zip.name)
    for name in ('README.md','ASSET-NOTICE.md','VALIDATION.md'):
        shutil.copy2(root/name,local/name)
    public_manifest={p.name:{'sha256':checksum(p),'bytes':p.stat().st_size} for p in (public_dest,source_zip)}
    (release/'checksums.json').write_text(json.dumps(public_manifest,indent=2)+'\n',encoding='utf-8')
    local_manifest={p.relative_to(local).as_posix():{'sha256':checksum(p),'bytes':p.stat().st_size} for p in local.rglob('*') if p.is_file() and p.name!='checksums.json'}
    (local/'checksums.json').write_text(json.dumps(local_manifest,indent=2)+'\n',encoding='utf-8')
    local_zip=args.downloads/'Bloodborne-RP-1.20.1-20261004-local.zip'
    archive(local_zip,[(p,p.relative_to(local).as_posix()) for p in local.rglob('*') if p.is_file()])
    print(json.dumps({'public':str(public_dest),'sources':str(source_zip),'local_jar':str(private_dest),'local_bundle':str(local_zip),'public_files':public_manifest},indent=2))

if __name__=='__main__': main()
