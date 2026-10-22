"""Freeze identical separate-server test profiles; does not launch Minecraft."""
import hashlib, json, shutil, subprocess, urllib.request, zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2]
ROOT=P.parent
BASE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW-city-mesh-20261018')
OLD=BASE/'bloodborne complete/build'
O=ROOT/'research/profiles'
O.mkdir(parents=True,exist_ok=True)
JAVA=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\java.exe')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def req(url):return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Dreamwalker-native-grid-local-QA/1.0'}),timeout=90).read()
def download(project,version):
    data=json.loads(req('https://api.modrinth.com/v2/project/'+project+'/version/'+urllib.parse.quote(version,safe='')))
    assert '1.20.1' in data['game_versions'] and 'fabric' in data['loaders']
    f=next(f for f in data['files'] if f.get('primary'))
    out=O/'dependencies'/f['filename'];out.parent.mkdir(exist_ok=True)
    if not out.exists():out.write_bytes(req(f['url']))
    assert hashlib.sha512(out.read_bytes()).hexdigest()==f['hashes']['sha512']
    return out,{'project':project,'version':data['version_number'],'source':f['url'],'sha256':sha(out),'versionId':data['id']}
import urllib.parse
dependencies=[download('sodium','mc1.20.1-0.5.11'),download('indium','1.0.36+mc1.20.1')]
modsets={}
for role,folder in [('client','runtime-client-rp-streaming7'),('server','runtime-server-rp-streaming7')]:
    records=[]
    for variant in ['baseline','candidate']:
        profile=O/variant/role;mods=profile/'mods';mods.mkdir(parents=True,exist_ok=True)
        for jar in (OLD/folder/'mods').glob('*.jar'):
            lower=jar.name.lower()
            if any(s in lower for s in ('dreamwalker-bb-','native-route-qa','-checks-','checks.jar','-qa.jar','sodium','indium')):continue
            target=mods/jar.name
            if not target.exists():shutil.copy2(jar,target)
            else:assert sha(target)==sha(jar)
        if role=='client':
            for dep,_ in dependencies:shutil.copy2(dep,mods/dep.name)
        for jar in sorted(mods.glob('*.jar')):
            with zipfile.ZipFile(jar) as z:
                meta=json.loads(z.read('fabric.mod.json'))
            records.append({'variant':variant,'file':jar.name,'id':meta['id'],'version':meta['version'],'sha256':sha(jar)})
        if role=='client':
            (profile/'options.txt').write_text('version:3465\nautoJump:false\nfullscreen:false\nrenderDistance:6\nsimulationDistance:4\ngamma:0.5\nao:true\ntutorialStep:none\nmaxFps:60\nenableVsync:false\nfov:0.0\ngraphicsMode:1\nguiScale:2\npauseOnLostFocus:false\nresourcePacks:[]\nhideGui:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\n',encoding='utf-8')
        else:
            (profile/'eula.txt').write_text((OLD/folder/'eula.txt').read_text(),encoding='utf-8')
            (profile/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25579\nlevel-name=city\nview-distance=6\nsimulation-distance=4\nmax-players=2\nnetwork-compression-threshold=256\nspawn-protection=0\nallow-flight=true\ngamemode=creative\ndifficulty=peaceful\nenable-command-block=true\nmax-tick-time=120000\nsync-chunk-writes=true\n',encoding='utf-8')
    modsets[role]=records
    one={r['file']:r['sha256'] for r in records if r['variant']=='baseline'}
    two={r['file']:r['sha256'] for r in records if r['variant']=='candidate'}
    assert one==two
version=subprocess.check_output([str(JAVA),'-version'],stderr=subprocess.STDOUT,text=True)
report={'schemaVersion':1,'status':'PROFILES_FROZEN_PENDING_PRODUCTION_AND_WORLD','java':str(JAVA),'javaVersion':version,'physicalMemoryGiB':16,'clientHeap':'3G','serverHeap':'4G','minimumAvailableGiBBeforePair':9,'viewDistance':6,'simulationDistance':4,'graphics':'fancy, AO true, no shaders, max60fps, vsync false','serverScenario':'separate local Fabric server on127.0.0.1:25579; no remote hosting performance claim','mods':modsets,'addedOfficialDependencies':[r for _,r in dependencies],'productionJarPolicy':'exactly one matching Dreamwalker per profile; baseline40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51','ready':False}
(O/'profile-lock.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
