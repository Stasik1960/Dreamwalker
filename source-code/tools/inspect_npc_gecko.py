import subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'npc-evidence';root=Path('C:/Users/vakir/.gradle/caches/modules-2/files-2.1/software.bernie.geckolib/geckolib-fabric-1.20.1/4.4.9');jar=next(p for p in root.rglob('*.jar') if not p.name.endswith('-sources.jar'))
for cls in ('software.bernie.geckolib.renderer.GeoEntityRenderer','software.bernie.geckolib.core.animation.AnimationController'):
 r=subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-p','-classpath',str(jar),cls],capture_output=True,text=True,timeout=30);(D/(cls.rsplit('.',1)[-1]+'.api.txt')).write_text(r.stdout,encoding='utf-8')
