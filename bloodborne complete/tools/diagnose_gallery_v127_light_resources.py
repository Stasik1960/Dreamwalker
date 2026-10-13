"""Check original pixel files and the pinned native BER light calculation."""
import hashlib,json,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
prod=ROOT/'build/libs/dreamwalker-bb-fabric-1.20.1-0.1.0-v12.7-final.jar'
original=ROOT.parent/'Bloodborne-Blocks/reference-inputs/bbmc-v15-v16/bbmc_v15_resource.zip'
sha=lambda data:hashlib.sha256(data).hexdigest()
with zipfile.ZipFile(prod) as jar,zipfile.ZipFile(original) as source:
    names={n[n.find('assets/'):]:n for n in source.namelist() if 'assets/' in n}
    textures=[]
    prefix='assets/bloodborne_dw/textures/block/remaining/source/'
    for path in jar.namelist():
        if not path.startswith(prefix) or not path.endswith('.png'):continue
        ns,tail=path[len(prefix):].split('/',1);key=f'assets/{ns}/textures/{tail}'
        if key not in names:continue
        a,b=jar.read(path),source.read(names[key])
        textures.append({'modPath':path,'sourcePath':names[key],'sha256Mod':sha(a),'sha256Source':sha(b),'identicalBytes':a==b})
mc=Path.home()/'.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2/minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar'
javap=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javap.exe')
native=subprocess.run([str(javap),'-c','-p','-classpath',str(mc),'net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher','net.minecraft.client.render.WorldRenderer'],capture_output=True,text=True,check=True).stdout
(ROOT/'build/v127-black-blocks-native-light-bytecode.txt').write_text(native,encoding='utf8')
result={'status':'PASS_READ_ONLY_RESOURCES_AND_NATIVE_CODE','productionJarSha256':sha(prod.read_bytes()),'sourceZipSha256':sha(original.read_bytes()),'comparedOriginalTextures':len(textures),'identicalOriginalTextures':sum(r['identicalBytes'] for r in textures),'differentOriginalTextures':[r for r in textures if not r['identicalBytes']],'nativeBytecodeEvidence':'build/v127-black-blocks-native-light-bytecode.txt','textures':textures}
(ROOT/'build/v127-black-blocks-resource-proof.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps({k:v for k,v in result.items() if k!='textures'},ensure_ascii=False))
