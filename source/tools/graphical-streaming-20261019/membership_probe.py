import shutil,subprocess,zipfile,sys
from common import *
wait_foreign();out=OUT/'membership-fix'
if '--prototype' in sys.argv:out=out/'rows-prototype';out.mkdir(exist_ok=True)
else:
 candidate=json.loads((OUT/'candidate.json').read_text());previous=out/'previous-release/game/dreamwalker-bb-materials.1-streaming.7.jar'
 with zipfile.ZipFile(previous) as a,zipfile.ZipFile(candidate['jar']) as b:
  names=set(a.namelist())|set(b.namelist());changes=[n for n in sorted(names) if n not in a.namelist() or n not in b.namelist() or a.read(n)!=b.read(n)]
 assert 'dev/dreamwalker/bloodbornedw/composite/ShardedCompositeLedger.class' in changes
 assert all(n=='dev/dreamwalker/bloodbornedw/composite/ShardedCompositeLedger.class' or n.startswith('dev/dreamwalker/bloodbornedw/composite/MembershipNbtReader') and n.endswith('.class') for n in changes),changes
 write(out/'reused-checks.json',{'status':'PREVIOUS_44_CHECKS_REUSED_FOR_BYTE_IDENTICAL_MECHANISMS','previousJarSha256':sha(previous),'productionJarSha256':candidate['sha256'],'changedClasses':changes,'newReaderChecks':'Targeted cold-reader GameTests plus exact real-NBT probe; previous root sync, native packet witness, door/stair and palette checks are not repeated.'})
if '--reuse-probe' in sys.argv:
 prototype=OUT/'membership-fix/rows-prototype';inputs=json.loads((prototype/'nbt-input.json').read_text())
 assert inputs['readerSourceSha256']==sha(PROJECT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/MembershipNbtReader.java')==json.loads((OUT/'build-production.json').read_text())['sourceHashes']['src/architecture/java/dev/dreamwalker/bloodbornedw/composite/MembershipNbtReader.java']
 assert sha(Path(inputs['source']))==inputs['sha256']
 assert json.loads((prototype/'nbt-probe.json').read_text())['status']=='PASS_EXACT_RETAINED_FIELDS'
 for name in ['nbt-probe.json','nbt-input.json','nbt-probe.log','nbt-layout.json','nbt-fields.csv']:shutil.copyfile(prototype/name,out/name)
 sys.exit(0)
prepared=json.loads((OUT/'after-owner-transport-only/prepared.json').read_text());basecp=prepared['serverArgs'][prepared['serverArgs'].index('-cp')+1]
named=Path(r'C:\Users\vakir\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2\minecraft-merged-1.20.1-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.jar')
classes=PROJECT/'build/membership-nbt-probe';classes.mkdir(exist_ok=True);cp=str(named)+';'+basecp
def near(p):
 x,z=map(int,p.stem[len('owners-'):].split('.'));return 30<=x<=70 and -20<=z<=20
source=max((p for p in (SOURCE/'data/bloodborne_dw_owner_shards').glob('owners-*.nbt') if near(p)),key=lambda p:p.stat().st_size)
before=sha(source);write(out/'nbt-input.json',{'source':str(source),'sha256':before,'readerSourceSha256':sha(PROJECT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/MembershipNbtReader.java'),'bytes':source.stat().st_size,'method':'One warm-up of both paths; one full and one selective NBT read. Thread allocated bytes are exact for this reader thread, not the whole game.'})
with (out/'nbt-probe.log').open('w') as log:
 subprocess.run([str(JAVA_HOME/'bin/javac.exe'),'-J-Xmx256m','-proc:none','--release','17','-cp',cp,'-d',str(classes),str(TOOLS/'NbtMembershipProbe.java'),str(PROJECT/'src/architecture/java/dev/dreamwalker/bloodbornedw/composite/MembershipNbtReader.java')],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
 subprocess.run([str(JAVA),'-Xmx1G','-cp',str(classes)+';'+cp,'dev.dreamwalker.bloodbornedw.composite.NbtMembershipProbe',str(source),str(out)],stdout=log,stderr=subprocess.STDOUT,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
assert sha(source)==before
