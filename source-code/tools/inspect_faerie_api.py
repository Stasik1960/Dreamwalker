import subprocess
from pathlib import Path
P=Path(__file__).resolve().parents[1];D=P/'faerie-evidence';D.mkdir(exist_ok=True);jar=next(Path('C:/Users/vakir/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged').rglob('*.jar'))
with (D/'texture-api.txt').open('w',encoding='utf-8') as out:
 for name in ('net.minecraft.client.texture.SpriteContents$AnimatorImpl','net.minecraft.client.texture.SpriteContents$Animation','net.minecraft.client.texture.SpriteContents','net.minecraft.client.texture.Sprite','net.minecraft.client.render.texture.SpriteTicker'):
  subprocess.run([str(P/'build-tools/jdk/bin/javap.exe'),'-p','-classpath',str(jar),name],stdout=out,stderr=out,timeout=30)
