import subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'utility-evidence';D.mkdir(exist_ok=True)
jar=next(Path('C:/Users/vakir/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged').rglob('*.jar'))
names=['fluid.Fluid','fluid.EmptyFluid','world.chunk.WorldChunk','world.chunk.Chunk','block.Waterloggable','world.World','block.piston.PistonBlock','entity.Entity','client.render.FluidRenderer','item.BlockItem']
with (D/'mapped-api.txt').open('w',encoding='utf-8') as out:
 for name in names:
  subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-p','-c' if name in ('world.World','block.Waterloggable') else '-s','-classpath',str(jar),'net.minecraft.'+name],stdout=out,stderr=out,timeout=30)
