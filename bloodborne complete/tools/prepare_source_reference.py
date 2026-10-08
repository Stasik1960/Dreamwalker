"""Prepare an isolated original-version Forge world and QA-only physics probe."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
from source_reference_names import names,ROOT,REF

JDK=Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma')
SOURCE=Path('C:/Users/vakir/Downloads/bbmc_v16_map (1).zip')
MODS=[Path('C:/Users/vakir/Downloads/Bloodborne_X_Minecraft_mod_6.0.jar'),Path('C:/Users/vakir/Downloads/geckolib-forge-1.18-3.0.57.jar')]

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    mods=REF/'mods';mods.mkdir(exist_ok=True)
    for path in MODS:shutil.copyfile(path,mods/path.name)
    world=REF/'original-world'
    if world.exists():raise RuntimeError('Refuse to replace existing source-reference world')
    world.mkdir()
    records=[]
    with zipfile.ZipFile(SOURCE) as archive:
        for entry in archive.infolist():
            destination=(world/entry.filename).resolve()
            if not destination.is_relative_to(world.resolve()):raise ValueError('Unsafe world archive path')
            if entry.is_dir():destination.mkdir(parents=True,exist_ok=True);continue
            data=archive.read(entry);destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(data)
            records.append({'path':entry.filename,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    evidence=json.loads((ROOT/'reports/FIRST_SET_SOURCE_EVIDENCE.json').read_text(encoding='utf-8'))
    cells=[{'purpose':purpose,'pos':pos} for purpose,pos in [
        ('source_double_door_lower',[-93,59,-212]),('source_double_door_header',[-93,61,-212]),
        ('source_window',[-32,37,-1120]),('source_thin_window',[-121,53,-241]),
        ('source_ladder_art',[177,33,-1108]),('source_ladder_physics',[177,33,-1107]),
        ('source_wall',[154,58,-976])]]
    # Exact18-cell local tree samples are held in the evidence JSON; read all
    # unique pos records in its tree subtrees, not a guessed filled AABB.
    def collect(value):
        if isinstance(value,dict):
            if isinstance(value.get('pos'),list) and len(value['pos'])==3 and all(isinstance(v,int) for v in value['pos']):
                cells.append({'purpose':'source_architecture_tree_context','pos':value['pos']})
            for v in value.values():collect(v)
        elif isinstance(value,list):
            for v in value:collect(v)
    collect(evidence['confirmed_local_tree_assemblies'])
    unique={tuple(row['pos']):row for row in cells};cells=list(unique.values())
    movement=[
        {'purpose':'tree_foliage_gap_west','start':[-17.5,128.05,-505.5],'delta':[0,0,3]},
        {'purpose':'tree_central_source_wool','start':[-15.5,128.05,-505.5],'delta':[0,0,3]},
        {'purpose':'source_melon_trunk','start':[-15.5,119.05,-505.5],'delta':[0,0,3]},
        {'purpose':'source_door_center','start':[-92.5,58.05,-213.5],'delta':[0,0,4]},
        {'purpose':'source_door_side','start':[-93.6,58.05,-213.5],'delta':[0,0,4]},
        {'purpose':'source_window_center','start':[-31.5,37.05,-1121.5],'delta':[0,0,3]},
        {'purpose':'source_ladder_entry','start':[177.5,33.05,-1105.5],'delta':[0,0,-2]},
    ]
    (REF/'probe-input.json').write_text(json.dumps({'methods':names(),'cells':cells,'movements':movement},indent=2)+'\n',encoding='utf-8')
    java=ROOT/'tools/java/SourcePhysicsProbe.java';classes=REF/'probe-classes';classes.mkdir(exist_ok=True)
    # Compile against installed source-version classes. The probe never enters
    # the production Fabric source set or production mods profile.
    libraries=sorted((REF/'libraries').rglob('*.jar'))
    result=subprocess.run([str(JDK/'bin/javac.exe'),'--release','17','-cp',os.pathsep.join(map(str,libraries)),'-d',str(classes),str(java)],capture_output=True,text=True)
    (ROOT/'reports/SOURCE_PHYSICS_PROBE_COMPILE.log').write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode:raise RuntimeError(result.stderr[-3000:])
    probe=mods/'dreamwalker-source-physics-probe.jar'
    toml='modLoader="javafml"\nloaderVersion="[40,)"\nlicense="All-Rights-Reserved"\n[[mods]]\nmodId="dreamwalker_source_probe"\nversion="1.0.0"\ndisplayName="Isolated source physics QA"\n'
    with zipfile.ZipFile(probe,'w',zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('META-INF/mods.toml',toml)
        for path in sorted(classes.rglob('*.class')):archive.write(path,path.relative_to(classes).as_posix())
    eula=Path('C:/Users/vakir/.codex/worktrees/bloodborne-material-review/DW/bloodborne complete/build/production-smoke/final-city/eula.txt')
    assert 'eula=true' in eula.read_text(encoding='utf-8')
    shutil.copyfile(eula,REF/'eula.txt')
    (REF/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=65161\nonline-mode=false\nlevel-name=original-world\nview-distance=5\nsimulation-distance=5\nspawn-protection=0\nmax-tick-time=180000\n',encoding='utf-8')
    report={'schema':'dreamwalker-original-source-reference-v1','status':'PREPARED_NOT_RUN','minecraft':'1.18.2','forge':'40.2.0',
        'original_world_sha256':digest(SOURCE),'world_files_copied_byte_exact':records,
        'mods':[{'path':str(path),'sha256':digest(path)} for path in MODS],
        'qa_probe_sha256':digest(probe),'qa_source_sha256':digest(java),'qa_probe_cells':len(cells),'qa_probe_movements':len(movement),
        'run_directory':str(REF),'eula_source':str(eula),'java_home':str(JDK),
        'source_visual_reference':'NOT_RUN','manual_source_gameplay':'NOT_RUN',
        'source_modpack':'Original Bloodborne6.0 and GeckoLib3.0.57 only; additional original-mod configuration not assumed'}
    (ROOT/'reports/SOURCE_REFERENCE.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'prepared':str(REF),'source_files':len(records),'probe_cells':len(cells),'movements':len(movement)}))
if __name__=='__main__':main()
