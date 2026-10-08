"""Compare offline source choices with actual verified Minecraft1.18.2 classes."""
import hashlib,json,os,subprocess,urllib.request
from pathlib import Path
from positional_rng import position_seed,weighted_index
from record_inputs import sha256
ROOT=Path(__file__).resolve().parents[1]
def main():
    jdk=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin')
    out=ROOT/'build/source-rng-oracle';out.mkdir(parents=True,exist_ok=True)
    source=ROOT/'tools/java/SourceRngOracle.java'
    subprocess.run([str(jdk/'javac.exe'),'--release','17','-d',str(out),str(source)],check=True)
    vanilla=ROOT/'inputs/extracted/vanilla-1.18.2/client.jar'
    client_report=json.loads((ROOT/'reports/CLIENT_FULL_CLIENT.json').read_text(encoding='utf-8'))
    # Launcher library paths are preserved; omit target client and Loader/mods.
    command=client_report['command'];paths=command[command.index('-cp')+1].split(os.pathsep)
    deps=[p for p in paths if 'libraries' in p and 'fabric' not in p.lower()]
    meta=json.loads((vanilla.parent/'version.json').read_text(encoding='utf-8'))
    original_deps=[]
    for lib in meta['libraries']:
        spec=lib.get('downloads',{}).get('artifact')
        if not spec or lib['name'].startswith('org.lwjgl'):continue
        dest=vanilla.parent/'libraries'/spec['path'];dest.parent.mkdir(parents=True,exist_ok=True)
        if not dest.exists():
            with urllib.request.urlopen(spec['url'],timeout=30) as response:data=response.read()
            if hashlib.sha1(data).hexdigest()!=spec['sha1']:raise ValueError('Original library SHA1 mismatch')
            dest.write_bytes(data)
        if hashlib.sha1(dest.read_bytes()).hexdigest()!=spec['sha1']:raise ValueError('Cached original library SHA1 mismatch')
        original_deps.append(str(dest))
    deps=original_deps+deps
    cp=os.pathsep.join([str(out),str(vanilla)]+deps)
    fixture=json.loads((ROOT/'reports/FIRST_FIXTURE.json').read_text(encoding='utf-8'))
    vectors=[(row['pos'],[1,1,1]) for row in fixture['instances']]
    for pos in [[0,0,0],[-1,-64,1],[2147483647,317,-2147483648],[-10000000,200,10000000]]:
        for weights in [[1,1,1],[1000,1,1,1,1],[3,7,11]]:vectors.append((pos,weights))
    args=[','.join(map(str,pos))+':'+','.join(map(str,w)) for pos,w in vectors]
    result=subprocess.run([str(jdk/'java.exe'),'-Xmx1G','-cp',cp,'dev.dreamwalker.qa.SourceRngOracle',*args],capture_output=True,text=True)
    if result.returncode:
        (ROOT/'reports/SOURCE_RNG_ORACLE_ERROR.log').write_text(result.stderr,encoding='utf-8')
        raise RuntimeError('Source oracle failed: '+result.stderr[-1800:])
    output_lines=result.stdout.splitlines();observed=[]
    for line in output_lines:
        if line.count(':')!=3:continue
        coords,weights,seed,index=line.split(':');pos=list(map(int,coords.split(',')));w=list(map(int,weights.split(',')))
        assert int(seed)==position_seed(pos),(pos,'source seed differs')
        assert int(index)==weighted_index(w,pos),(pos,w,'source selected model differs')
        observed.append({'pos':pos,'weights':w,'source_seed':int(seed),'source_choice':int(index),'status':'PASS'})
    assert len(observed)==len(vectors)
    report={'schema':'dreamwalker-source-rng-oracle-v1','status':'PASS','method':'reflection into actual verified Mojang1.18.2 Mth.c and WeightedBakedModel.a; BakedModel proxies label selected model',
        'source_client_sha256':sha256(vanilla),'source_oracle_sha256':sha256(source),'checks':observed}
    (ROOT/'reports/SOURCE_RNG_VERIFICATION.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('Actual Minecraft1.18.2 RNG oracle: '+str(len(observed))+' cases PASS (34 source sections +12 overflow/nonuniform-weight cases).')
if __name__=='__main__':main()
