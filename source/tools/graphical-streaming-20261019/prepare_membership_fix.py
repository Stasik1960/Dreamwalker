import shutil,subprocess
from common import *
wait_foreign();archive=OUT/'membership-fix';archive.mkdir(exist_ok=False)
def move_inside(source,target,bound):
 assert source.resolve().is_relative_to(bound.resolve()) and target.absolute().is_relative_to(ROOT.resolve()) and not target.exists()
 for p in files(source):pass
 source.rename(target)
move_inside(ROOT/'deliverables/streaming-7-20261020',archive/'previous-release',ROOT/'deliverables')
for name in ['candidate.json','build-production.json','build-production.log','comparison.json']:
 shutil.copyfile(OUT/name,archive/name)
move_inside(OUT/'after',OUT/'after-owner-transport-only',OUT)
move_inside(OUT/'validation-tail',OUT/'validation-tail-read-tree-failure',OUT)
dest=OUT/'validation-tail-read-tree-failure/screenshots';dest.mkdir(exist_ok=True)
for p in ordinary(CLIENT/'screenshots').glob('route-*.png'):
 assert p.resolve().is_relative_to((CLIENT/'screenshots').resolve());shutil.move(str(ordinary(p)),dest/p.name)
shutil.copyfile(SERVER/'crash-reports/crash-2026-10-20_02.36.16-server.txt',OUT/'validation-tail-read-tree-failure/server/watchdog.txt')
source=PROJECT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/ShardedCompositeLedger.java';target=OUT/'source-before/ShardedCompositeLedger.java';assert not target.exists();shutil.copyfile(source,target)
jar=Path(r'C:\Users\vakir\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2\minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar')
with (archive/'nbt-api.txt').open('w') as log:
 subprocess.run([str(JAVA_HOME/'bin/javap.exe'),'-classpath',str(jar),'net.minecraft.nbt.scanner.ExclusiveNbtCollector','net.minecraft.nbt.scanner.NbtScanQuery','net.minecraft.nbt.NbtIo'],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
write(archive/'receipt.json',{'status':'PREVIOUS_MEASURED_RESULT_PRESERVED','jarSha256':sha(archive/'previous-release/game/dreamwalker-bb-materials.1-streaming.7.jar'),'newFixScope':'Skip irrelevant cells/dataTable NBT subtrees during cold saved-membership lookup; no schema/cache changes.'})
