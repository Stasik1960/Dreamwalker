"""Use the reviewed isolated server runner with a fully cached pinned bootstrap.

All remaining CLI options are forwarded unchanged to run_v11_server.py. No
metadata or library fetch can reach the network. --prepare-only audits caches
and dependencies without starting Java or creating an isolated runtime.
"""
import argparse, hashlib, json, sys, traceback, zipfile
from pathlib import Path
import run_v11_server as server
from verify_v1271_final_readiness import ROOT,ordinary,sha,write

CACHE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete\build\runtime-server-v127-new-gallery-author2')
METADATA_URL='https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.16.10/server/json'
EXPECTED={'org.ow2.asm:asm:9.7.1','org.ow2.asm:asm-analysis:9.7.1','org.ow2.asm:asm-commons:9.7.1','org.ow2.asm:asm-tree:9.7.1','org.ow2.asm:asm-util:9.7.1','net.fabricmc:sponge-mixin:0.15.4+mixin.0.8.7','net.fabricmc:intermediary:1.20.1','net.fabricmc:fabric-loader:0.16.10'}

def cached_inputs(cache):
    cache=ordinary(cache);profile=ordinary(cache/'fabric-loader-profile.json');raw=profile.read_bytes();metadata=json.loads(raw)
    assert metadata['id']=='fabric-loader-0.16.10-1.20.1' and metadata['inheritsFrom']=='1.20.1'
    assert {r['name'] for r in metadata['libraries']}==EXPECTED and len(metadata['libraries'])==8
    fetched={METADATA_URL:raw};libraries=[]
    for library in metadata['libraries']:
        group,name,version=library['name'].split(':');relative=Path(group.replace('.','/'))/name/version/(name+'-'+version+'.jar')
        path=ordinary(cache/'libraries'/relative);digest=sha(path)
        if library.get('sha256'):assert digest==library['sha256'],('Cached official library SHA mismatch',library['name'])
        with zipfile.ZipFile(path) as jar:assert jar.testzip() is None
        # The reviewed runner first prefers these user caches. Verify that its
        # selected candidate is byte-identical to the audited bootstrap; when
        # none exists its fetch() fallback is served from the cached bytes.
        candidates=[Path.home()/'AppData/Roaming/.minecraft/libraries'/relative]
        candidates+=sorted((Path.home()/'.gradle/caches/modules-2/files-2.1'/group/name/version).glob('*/*.jar'))
        preferred=next((p for p in candidates if p.is_file() and not p.name.endswith(('-sources.jar','-javadoc.jar')) and (not library.get('sha256') or sha(p)==library['sha256'])),None)
        if preferred is not None:ordinary(preferred);assert sha(preferred)==digest,('Preferred loader cache differs from audited bootstrap',library['name'],str(preferred))
        url=library.get('url','https://maven.fabricmc.net/').rstrip('/')+'/'+relative.as_posix()
        assert url.startswith('https://maven.fabricmc.net/') and url not in fetched
        fetched[url]=path.read_bytes();libraries.append({'coordinate':library['name'],'source':str(path),'sha256':digest,'officialSha256':library.get('sha256'),'preferredUserCache':str(preferred) if preferred else None})
    dependencies=[]
    for group,name,version in server.DEPENDENCIES:
        cached=Path.home()/'.gradle/caches/modules-2/files-2.1'/group/name/version
        path=next((p for p in sorted(cached.glob('*/*.jar')) if not p.name.endswith(('-sources.jar','-javadoc.jar'))),None)
        assert path is not None,('Pinned cached runtime mod missing',name,version)
        ordinary(path)
        with zipfile.ZipFile(path) as jar:
            assert jar.testzip() is None;mod=json.loads(jar.read('fabric.mod.json'))
            assert mod['id']==('geckolib' if name.startswith('geckolib') else 'fabric-api') and mod['version']==version
        dependencies.append({'id':mod['id'],'version':version,'source':str(path),'sha256':sha(path)})
    vanilla=ordinary(Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-server.jar')
    with zipfile.ZipFile(vanilla) as jar:assert jar.testzip() is None
    return fetched,{'status':'PASS_OFFLINE_PINNED_BOOTSTRAP_PREFLIGHT_SERVER_NOT_LAUNCHED','bootstrapCache':str(cache),'loaderMetadataSha256':hashlib.sha256(raw).hexdigest(),'loaderLibraries':libraries,'runtimeDependencies':dependencies,'minecraftServerSha256':sha(vanilla),'networkFetches':0,'jvmStarted':False}

def main():
    p=argparse.ArgumentParser(description=__doc__,add_help=False)
    p.add_argument('--bootstrap-cache',type=Path,default=CACHE);p.add_argument('--prepare-only',action='store_true')
    p.add_argument('--offline-preflight-report',type=Path,default=ROOT/'build/v1271-gallery/offline-bootstrap-preflight.json')
    own,remaining=p.parse_known_args()
    try:
        fetched,record=cached_inputs(own.bootstrap_cache);write(ordinary(own.offline_preflight_report),record)
        if own.prepare_only:return
        def fetch_offline(url):
            assert url in fetched,('Uncached bootstrap URL rejected; network disabled',url)
            return fetched[url]
        server.fetch=fetch_offline;sys.argv=[str(ROOT/'tools/run_v11_server.py'),*remaining]
        server.main()
    except BaseException:
        write(ordinary(own.offline_preflight_report),{'status':'FAIL_OFFLINE_PREFLIGHT_OR_SERVER','error':traceback.format_exc(),'networkFetches':0});raise

if __name__=='__main__':main()
