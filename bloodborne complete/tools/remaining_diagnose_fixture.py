"""Read-only mapped Minecraft bytecode diagnosis for out-of-structure fixtures."""
import json,subprocess,traceback,zipfile
from pathlib import Path
from world_io import RegionFile,compound
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'build/remaining-transfer/fixture-diagnosis'
JAVA=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javap.exe')
JAR=next((ROOT/'.gradle/loom-cache/minecraftMaven').rglob('minecraft-merged-cdeb4ce359-*.jar'))
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(JAR) as archive:
        names=[name[:-6].replace('/','.') for name in archive.namelist() if name.endswith('.class') and (name.startswith('net/minecraft/test/') or 'ServerEntityManager' in name)]
    (OUT/'class-names.json').write_text(json.dumps(names,indent=2),encoding='utf-8')
    for name in ['net.minecraft.server.world.ServerEntityManager','net.minecraft.server.world.ServerWorld','net.minecraft.test.TestRunner','net.minecraft.test.TestBatchRunner','net.minecraft.test.BeforeBatch','net.minecraft.test.TestFunctions']:
        result=subprocess.run([str(JAVA),'-classpath',str(JAR),'-c','-p',name],capture_output=True,text=True)
        (OUT/(name.rsplit('.',1)[-1]+'.txt')).write_text(result.stdout+result.stderr,encoding='utf-8')
    saved=[]
    for run in ['remaining-final-3','remaining-final-4','remaining-final-6']:
        path=ROOT/('build/gametest-'+run+'/world/entities/r.0.0.mca')
        if path.stat().st_size<8192:
            saved.append({'run':run,'entityRegionStatus':'EMPTY_FILE_NO_SAVED_EVIDENCE'});continue
        for chunk in RegionFile(path.read_bytes()).chunks():
            root=compound(chunk.nbt().root)
            entities=root.get('Entities')
            if entities is None:continue
            for value in entities.value:
                entity=compound(value);pos=entity.get('Pos')
                if pos is None:continue
                pos=[coordinate.value for coordinate in pos.value]
                if (run!='remaining-final-6' and 180<=pos[0]<=240 and 0<=pos[2]<=12) or (run=='remaining-final-6' and entity['id'].value.startswith('bloodborne_rp:')):
                    saved.append({'run':run,'id':entity['id'].value,'pos':pos,'fields':list(entity),'chunkPosition':list(root['Position'].value),'links':repr(entity.get('Links')),'open':entity.get('Open').value if entity.get('Open') else None})
    (OUT/'saved-fixture-entities.json').write_text(json.dumps(saved,indent=2),encoding='utf-8')
    (OUT/'status.json').write_text(json.dumps({'status':'PASS_READ_ONLY','mappedMinecraftJar':str(JAR)},indent=2),encoding='utf-8')
if __name__=='__main__':
    try:main()
    except Exception:
        OUT.mkdir(parents=True,exist_ok=True);(OUT/'status.json').write_text(json.dumps({'status':'FAIL','traceback':traceback.format_exc()},indent=2),encoding='utf-8');raise
