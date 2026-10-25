import subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];jar=next((P/'fix-profiles/client/mods').glob('*indium*.jar'));parts=[]
for name in ('link.infra.indium.renderer.render.AbstractBlockRenderContext','link.infra.indium.renderer.aocalc.AoCalculator','link.infra.indium.renderer.mesh.MutableQuadViewImpl','link.infra.indium.renderer.helper.ColorHelper'):
 r=subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-J-Xmx64m','-p','-c','-classpath',str(jar),name],capture_output=True,text=True,encoding='utf-8');parts.append(r.stdout+r.stderr)
(P/'fix-evidence/indium-lighting-javap.txt').write_text('\n'.join(parts),encoding='utf-8')
