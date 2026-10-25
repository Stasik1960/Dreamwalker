import subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1]
r=subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-classpath',str(P/'fix-profiles/server/mods/worldedit-mod-7.2.15.jar'),'com.sk89q.worldedit.fabric.FabricAdapter','com.sk89q.worldedit.fabric.FabricPlayer'],capture_output=True,text=True,errors='replace',timeout=30)
(P/'restoration-evidence/worldedit-api.txt').write_text(r.stdout+r.stderr,encoding='utf-8')
