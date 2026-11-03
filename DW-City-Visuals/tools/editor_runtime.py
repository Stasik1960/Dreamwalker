"""Prepare and run isolated editor QA/performance clients; never edit the source world."""
import argparse, datetime, hashlib, json, os, shutil, subprocess, sys, time, uuid, zipfile
from pathlib import Path

WORKSPACE = Path('D:/DreamWalker')
RUNTIME = WORKSPACE / '.local/dw-fix-20261028/runtime'
DEFAULT_WORLD = WORKSPACE / '.local/dw-fix-20261028/checkpoints/test-launch-microstutter-v22-20261029/world/bloodborne'
ROOT = WORKSPACE / '.local/editor-validation/runs'

def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for block in iter(lambda: f.read(1024*1024), b''): h.update(block)
    return h.hexdigest()

def write(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def world_manifest(folder):
    files={path.relative_to(folder).as_posix():sha(path)
           for path in sorted(folder.rglob('*')) if path.is_file() and path.name!='session.lock'}
    digest=hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode('utf-8')).hexdigest()
    return {'sha256':digest,'files':files}

def guard():
    shell = shutil.which('pwsh') or shutil.which('powershell')
    subprocess.run([shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                    r'C:\Users\vakir\.codex\dreamwalker\Assert-Workspace.ps1'], cwd=WORKSPACE, check=True)

def metadata(path):
    with zipfile.ZipFile(path) as z: return json.loads(z.read('fabric.mod.json'))

def prepare(args):
    import nbtlib
    game = ROOT / args.name
    if game.exists(): raise RuntimeError('Choose a new run name; prior evidence is preserved')
    game.mkdir(parents=True); (game/'mods').mkdir(); (game/'config').mkdir(); (game/'evidence').mkdir()
    current = json.loads((WORKSPACE/'.local/modpacks/CURRENT.json').read_text(encoding='utf-8-sig'))
    inventory = json.loads(Path(current['manifest']).read_text(encoding='utf-8-sig'))
    expected = {i['path'].replace('\\','/'): i['sha256'].lower() for i in inventory['files']}
    mods=[]
    for path in sorted(Path(current['client']).glob('*.jar')):
        meta=metadata(path)
        if meta['id']=='bloodborne_dw': continue
        digest=sha(path); relative=path.relative_to(Path(current['root'])).as_posix()
        if expected.get(relative)!=digest: raise RuntimeError('Baseline mod changed: '+relative)
        shutil.copy2(path,game/'mods'/path.name)
        mods.append({'file':path.name,'id':meta['id'],'sha256':digest})
    production=args.jar.resolve(); qa=args.qa.resolve()
    if metadata(production)['id']!='bloodborne_dw': raise RuntimeError('Expected combined production jar')
    shutil.copy2(production,game/'mods/dreamwalker-selected.jar'); shutil.copy2(qa,game/'mods/isolated-qa.jar')
    mods += [{'file':'dreamwalker-selected.jar','id':'bloodborne_dw','sha256':sha(production)},
             {'file':'isolated-qa.jar','id':metadata(qa)['id'],'sha256':sha(qa)}]
    kind=metadata(qa)['id']
    world_source=args.world.resolve(); world=game/'saves/city'
    world_input=world_manifest(world_source) if kind=='dw_flight_qa' else None
    shutil.copytree(world_source,world,ignore=shutil.ignore_patterns('session.lock'))
    if world_input is not None:
        if world_manifest(world)!=world_input:raise RuntimeError('Flight world copy differs from source snapshot')
        write(game/'evidence/world-input-manifest.json',world_input)
    (world/'data').mkdir(exist_ok=True)
    nbtlib.File({'data':nbtlib.Compound({'alt':nbtlib.Byte(args.mode=='alt'),'revision':nbtlib.Long(0)}),
                 'DataVersion':nbtlib.Int(3465)},gzipped=True).save(world/'data/dw_city_visual_mode.dat')
    with zipfile.ZipFile(production) as z:
        fingerprints=json.loads(z.read('assets/bloodborne_dw/visuals/profile-fingerprints.json'))
    fingerprint=fingerprints[args.mode]
    if isinstance(fingerprint,dict):fingerprint=fingerprint.get('sha256',fingerprint.get('fingerprint'))
    write(game/'config/dw-city-visuals.json',{'schema':1,'alt':args.mode=='alt','fingerprint':fingerprint})
    (game/'options.txt').write_text('renderDistance:12\nsimulationDistance:12\nmaxFps:260\nenableVsync:false\npauseOnLostFocus:false\nfullscreen:false\ngamma:1.0\nfov:0.0\nmipmapLevels:2\nguiScale:2\nonboardAccessibility:false\nresourcePacks:[]\n',encoding='utf-8')
    (game/'config/iris.properties').write_text('enableShaders=false\ndisableUpdateMessage=true\n',encoding='utf-8')
    runtime=json.loads((RUNTIME/'runtime.json').read_text(encoding='utf-8-sig'))
    for path in runtime['classpath']:
        if not Path(path).is_file(): raise RuntimeError('Missing classpath file '+path)
        if not Path(path).resolve().is_relative_to(WORKSPACE):raise RuntimeError('Runtime outside HDD workspace')
    arguments=['-Xms128M','-Xmx6G','-Dfabric.modsFolder='+str(game/'mods'),
               '-Djava.io.tmpdir='+str(WORKSPACE/'.local/temp'),'-Duser.home='+str(WORKSPACE/'.local')]
    if kind=='dw_editor_qa':
        arguments += ['-Ddw.editorqa.disposable=true','-Ddw.editorqa.output='+str(game/'evidence/editor-qa.json'),'-Ddw.editorqa.persistence='+args.persistence]
        if args.persistence_only: arguments += ['-Ddw.editorqa.persistenceOnly=true']
        if args.editor2_only: arguments += ['-Ddw.editorqa.editor2Only=true']
        if args.functional_after_editor2: arguments += ['-Ddw.editorqa.functionalAfterEditor2=true']
        if args.failure_hold_seconds: arguments += ['-Ddw.editorqa.failureHoldSeconds='+str(args.failure_hold_seconds)]
        if args.ui_hold_seconds: arguments += ['-Ddw.editorqa.uiHoldSeconds='+str(args.ui_hold_seconds)]
        if args.ui_only: arguments += ['-Ddw.editorqa.uiOnly=true']
        if args.ui_be_fixture: arguments += ['-Ddw.editorqa.uiBeFixture=true']
        if args.render_probe: arguments += ['-Ddw.editorqa.renderProbe=true','-Ddw.editorqa.renderProbeOutput='+str(game/'evidence/editor-render-probe.jsonl')]
        for option, name in ((args.catalogue_filter, 'catalogueFilter'),
                             (args.catalogue_exclude_verified, 'catalogueExcludeVerified')):
            if option:
                keys=json.loads(option.read_text(encoding='utf-8-sig'))
                if not isinstance(keys,list) or any(not isinstance(key,str) for key in keys):
                    raise ValueError('Catalogue scope must be a JSON string list')
                scope=game/'evidence'/str(option.name)
                shutil.copy2(option,scope)
                arguments += ['-Ddw.editorqa.'+name+'='+str(scope)]
        if args.catalogue_exclude_verified_allow_removed: arguments += ['-Ddw.editorqa.catalogueExcludeVerifiedAllowRemoved=true']
        if args.catalogue_only: arguments += ['-Ddw.editorqa.catalogueOnly=true']
    elif kind=='dw_performance_qa':
        if not args.plan: raise RuntimeError('Performance route required')
        plan=json.loads(args.plan.read_text());plan['totalTimeoutSeconds']=3600;plan['readyTimeoutSeconds']=180
        write(game/'route.json',plan)
        arguments += ['-Ddw.perf.plan='+str(game/'route.json'),'-Ddw.perf.output='+str(game/'evidence'),
                      '-Ddw.perf.expectedSha256='+sha(production),'-Ddw.perf.variant='+args.name,
                      '-Ddw.perf.startupTimeoutSeconds=1800']
    elif kind=='dw_flight_qa':
        if not args.plan:raise RuntimeError('Immutable flight plan required')
        shutil.copy2(args.plan,game/'route.json')
        arguments += ['-Ddw.flight.plan='+str(game/'route.json'),'-Ddw.flight.output='+str(game/'evidence'),
                      '-Ddw.flight.expectedSha256='+sha(production),'-Ddw.flight.variant='+args.name]
    else: raise RuntimeError('Unexpected test mod '+kind)
    username='DWEditorQA'; uid=uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:'+username).encode()).digest(),version=3)
    arguments += ['-Djava.library.path='+runtime['natives'],'-Dorg.lwjgl.librarypath='+runtime['natives'],
                  '-cp',os.pathsep.join(runtime['classpath']),runtime['javaMain'],'--username',username,
                  '--version','1.20.1','--gameDir',str(game),'--assetsDir',runtime['assets'],
                  '--assetIndex',str(runtime['assetIndex']),'--uuid',uid.hex,'--accessToken','0',
                  '--userType','legacy','--versionType','release','--width','1280','--height','720',
                  '--quickPlayPath',str(game/'quickplay.json'),'--quickPlaySingleplayer','city']
    # Java argfile quoting, not shell quoting.
    (game/'launch.args').write_text('\n'.join('"'+str(a).replace('\\','\\\\').replace('"','\\"')+'"' for a in arguments),encoding='utf-8')
    write(game/'recipe.json',{'java':str(WORKSPACE/'.local/tools/jdk-17/bin/java.exe'),'name':args.name,
          'mode':args.mode,'sourceWorld':str(world_source),'worldCopyOnly':True,'mods':mods,
          'productionSource':str(production),'sourceLevelSha256':sha(world_source/'level.dat'),
          'optionsSha256':sha(game/'options.txt'),
          'hostLogicalCpuCount':os.cpu_count(),'activeProcessorCountOverride':None,
          **({'sourceWorldFilesSha256':world_input['sha256'],'planSha256':sha(game/'route.json')} if world_input is not None else {}),
          'createdUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()})
    print(game/'recipe.json',flush=True)

def run(path):
    path=path.resolve()
    if not path.is_relative_to(ROOT):raise RuntimeError('Recipe outside disposable test root')
    recipe=json.loads(path.read_text(encoding='utf-8-sig'));game=path.parent
    for mod in recipe['mods']:
        if sha(game/'mods'/mod['file'])!=mod['sha256']:raise RuntimeError('Prepared mod changed')
    arguments=[json.loads(line) for line in (game/'launch.args').read_text(encoding='utf-8').splitlines() if line.strip()]
    if 'activeProcessorCountOverride' in recipe:
        if recipe['activeProcessorCountOverride'] is not None or any(arg.startswith('-XX:ActiveProcessorCount=') for arg in arguments):
            raise RuntimeError('Native CPU recipe must use JVM default processor detection')
    if '-Dfabric.modsFolder='+str(game/'mods') not in arguments:raise RuntimeError('Launcher mod directory differs from recipe')
    if sha(game/'options.txt')!=recipe['optionsSha256']:raise RuntimeError('Prepared options changed before launch')
    production=next(m for m in recipe['mods'] if m['id']=='bloodborne_dw')
    qa=next(m for m in recipe['mods'] if m['file']=='isolated-qa.jar')
    if qa['id'] in ('dw_performance_qa','dw_flight_qa'):
        prefix='dw.flight' if qa['id']=='dw_flight_qa' else 'dw.perf'
        expected=[arg.split('=',1)[1] for arg in arguments if arg.startswith('-D'+prefix+'.expectedSha256=')]
        if expected!=[production['sha256']]:raise RuntimeError('Performance expected SHA differs from staged production; fix preparation before starting Java')
        for key,target in (('plan',game/'route.json'),('output',game/'evidence')):
            if '-D'+prefix+'.'+key+'='+str(target) not in arguments:raise RuntimeError('Performance '+key+' differs from recipe')
        if qa['id']=='dw_flight_qa':
            if sha(game/'route.json')!=recipe['planSha256']:raise RuntimeError('Flight plan changed before launch')
            manifest=json.loads((game/'evidence/world-input-manifest.json').read_text(encoding='utf-8'))
            digest=hashlib.sha256(json.dumps(manifest['files'],sort_keys=True,separators=(',',':')).encode('utf-8')).hexdigest()
            if digest!=recipe['sourceWorldFilesSha256'] or digest!=manifest['sha256']:raise RuntimeError('Flight source world manifest changed')
    with zipfile.ZipFile(game/'mods'/production['file']) as archive:
        fingerprint=json.loads(archive.read('assets/bloodborne_dw/visuals/profile-fingerprints.json'))[recipe['mode']]
    if isinstance(fingerprint,dict):fingerprint=fingerprint.get('sha256',fingerprint.get('fingerprint'))
    config=json.loads((game/'config/dw-city-visuals.json').read_text(encoding='utf-8-sig'))
    if config.get('fingerprint')!=fingerprint or config.get('alt')!=(recipe['mode']=='alt'):raise RuntimeError('Prepared visual mode or fingerprint differs from production')
    if (game/'process.json').exists():raise RuntimeError('Run has already started; preserve evidence')
    guard()
    with (game/'console.log').open('w',encoding='utf-8') as log:
        process=subprocess.Popen([recipe['java'],'@'+str(game/'launch.args')],cwd=game,
               stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        write(game/'process.json',{'pid':process.pid,'supervisorPid':os.getpid(),'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()})
        sampler=subprocess.Popen([sys.executable,str(Path(__file__).parent/'perf/sample_process.py'),
               '--pid',str(process.pid),'--output',str(game/'evidence/process-memory.jsonl'),'--seconds','4000'],
               cwd=game,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        while process.poll() is None:time.sleep(1)
        sampler_exit=sampler.wait(timeout=10)
    write(game/'exit.json',{'exitCode':process.returncode,'samplerExitCode':sampler_exit,'finishedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()})
    if sampler_exit!=0:raise RuntimeError('Memory sampler failed with exit code '+str(sampler_exit)+'; game exit preserved in exit.json')
    print('Exit',process.returncode,flush=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name');parser.add_argument('--jar',type=Path);parser.add_argument('--qa',type=Path)
    parser.add_argument('--mode',choices=['original','alt'],default='alt')
    parser.add_argument('--world',type=Path,default=DEFAULT_WORLD);parser.add_argument('--plan',type=Path)
    parser.add_argument('--persistence',choices=['write','verify'],default='write')
    parser.add_argument('--persistence-only',action='store_true')
    parser.add_argument('--editor2-only',action='store_true',help='Run the bounded real-city brush matrix and original catalogue audit')
    parser.add_argument('--functional-after-editor2',action='store_true',help='Run the functional suite after the editor2 matrix with the prototype-wall catalogue filter')
    parser.add_argument('--ui-only',action='store_true',help='Skip functional/catalogue/persistence mutations and run only the live UI hold')
    parser.add_argument('--ui-be-fixture',action='store_true',help='Temporarily use a block-entity target in the disposable UI-only world')
    parser.add_argument('--render-probe',action='store_true',help='Bounded read-only packet/model observations in an isolated UI-only QA run')
    parser.add_argument('--ui-hold-seconds',type=int,default=0)
    parser.add_argument('--failure-hold-seconds',type=int,default=0,help='Keep a failed editor QA client open for up to 300 seconds for inspection')
    parser.add_argument('--catalogue-filter',type=Path)
    parser.add_argument('--catalogue-exclude-verified',type=Path)
    parser.add_argument('--catalogue-exclude-verified-allow-removed',action='store_true')
    parser.add_argument('--catalogue-only',action='store_true')
    parser.add_argument('--run',type=Path)
    args=parser.parse_args();guard()
    if not 0<=args.failure_hold_seconds<=300:parser.error('--failure-hold-seconds must be between 0 and 300')
    if args.run:run(args.run)
    else:
        if not args.name or not args.name.replace('-','').isalnum() or not args.jar or not args.qa:parser.error('name, jar and qa required')
        if args.functional_after_editor2:
            if not args.editor2_only or args.catalogue_only or args.ui_only:parser.error('--functional-after-editor2 requires --editor2-only and cannot use --catalogue-only or --ui-only')
            fixed_filter=WORKSPACE/'.local/editor2/functional-catalogue-filter.json'
            if args.catalogue_filter and args.catalogue_filter.resolve()!=fixed_filter.resolve():parser.error('--functional-after-editor2 requires the prototype-wall catalogue filter')
            if json.loads(fixed_filter.read_text(encoding='utf-8-sig'))!=['bloodborne_dw:prototype_wall']:parser.error('prototype-wall catalogue filter has unexpected contents')
            args.catalogue_filter=fixed_filter
        if args.ui_only and not args.ui_hold_seconds:parser.error('--ui-only requires --ui-hold-seconds')
        if args.render_probe and not (args.ui_only or args.editor2_only):parser.error('--render-probe requires --ui-only or --editor2-only')
        if args.ui_be_fixture and not args.ui_only:parser.error('--ui-be-fixture requires --ui-only')
        prepare(args)
if __name__=='__main__':main()
