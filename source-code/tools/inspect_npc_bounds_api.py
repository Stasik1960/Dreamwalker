import subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';jar=next(Path('C:/Users/vakir/.gradle/caches').rglob('geckolib-fabric-1.20.1-4.4.9.jar'))
for cls in ('software.bernie.geckolib.util.RenderUtils','software.bernie.geckolib.cache.object.GeoCube','software.bernie.geckolib.cache.object.GeoQuad','software.bernie.geckolib.cache.object.GeoVertex','software.bernie.geckolib.renderer.GeoEntityRenderer','software.bernie.geckolib.renderer.GeoRenderer'):
 result=subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-p','-c','-classpath',str(jar),cls],capture_output=True,text=True,encoding='utf-8',errors='replace');(D/(cls.rsplit('.',1)[-1]+'.bounds-api.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
