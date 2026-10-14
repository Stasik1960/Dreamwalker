"""Primary native bytecode evidence; no model or world is loaded/modified."""
import json,subprocess,traceback
from pathlib import Path
from prepare_v1271_city_compatibility import ROOT,OUT,write,sha

def main():
    jar=next((ROOT/'.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-cdeb4ce359').rglob('*.jar'))
    binary=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma\bin\javap.exe')
    result=subprocess.run([str(binary),'-classpath',str(jar),'-c','-p','net.minecraft.client.render.model.ModelLoader'],capture_output=True,text=True,check=True)
    OUT.mkdir(parents=True,exist_ok=True);(OUT/'native-model-loader-bytecode.txt').write_text(result.stdout,encoding='utf8')
    write(OUT/'native-model-loader-bytecode.json',{'status':'PASS_READONLY_PRIMARY_NAMED_MINECRAFT_BYTECODE_CAPTURE','minecraftJar':str(jar),'minecraftJarSha256':sha(jar),'class':'net.minecraft.client.render.model.ModelLoader','runtimeFallbackConfirmed':False})

if __name__=='__main__':
    try:main()
    except BaseException:write(ROOT/'build/v1271-model-loader-inspection-error.json',{'error':traceback.format_exc()});raise
