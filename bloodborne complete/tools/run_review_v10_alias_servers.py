"""Prepare (or explicitly run) three separate V10 compatibility disk phases.

Fresh AUTHOR keeps two old EntityType instances per alias. Distinct REENTER uses
their saved disk copy and temporary real old-item/Creative damage probes. The
last process reopens that saved world with only the ordinary production mod.
Preparation is the default; no Minecraft process starts without --execute.
"""
from __future__ import annotations
import argparse, hashlib, json, re, subprocess, sys, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENTRYPOINT = 'dev.dreamwalker.bloodbornedw.review.ReviewV10AliasCompatibilityBootstrap'
AUTHOR = 'PASS_ALIAS_AUTHOR_14_INSTANCES_REQUIRES_DISTINCT_DISK_REENTER'
REENTER = 'PASS_ALIAS_DISTINCT_DISK_REENTER_14_PRESERVED_AND_7_CANONICAL_OLD_ITEM_PROBES'


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
def read(path): return json.loads(Path(path).read_text(encoding='utf8'))
def require(condition, message):
    if not condition: raise ValueError(message)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('jar', 'qa-jar', 'accepted-eula-file'): parser.add_argument('--'+name, type=Path, required=True)
    parser.add_argument('--artifact-sha', required=True)
    parser.add_argument('--suffix', required=True)
    parser.add_argument('--execute', action='store_true')
    args=parser.parse_args()
    require(re.fullmatch('[a-zA-Z0-9_-]+',args.suffix), 'Unsafe new phase suffix')
    jar,qa,eula=(p.resolve() for p in (args.jar,args.qa_jar,args.accepted_eula_file))
    require(re.fullmatch('[0-9a-f]{64}',args.artifact_sha) and sha(jar)==args.artifact_sha, 'Exact production SHA differs')
    with zipfile.ZipFile(qa) as archive:
        metadata=json.loads(archive.read('fabric.mod.json'))
        require(ENTRYPOINT in metadata['entrypoints']['main'] and ENTRYPOINT.replace('.','/')+'.class' in archive.namelist(), 'QA artifact lacks separate alias entrypoint/class')
    require(re.search(r'(?m)^\s*eula\s*=\s*true\s*$',eula.read_text(encoding='utf8')), 'Only previously accepted EULA may be copied')
    directory=ROOT/'build'/('review-v10-alias-pipeline-'+args.suffix)
    plan_path=directory/'plan.json'
    bindings={'productionJar':str(jar),'productionJarSha256':args.artifact_sha,'qaJar':str(qa),'qaSha256':sha(qa),
              'acceptedEula':str(eula),'acceptedEulaSha256':sha(eula),'suffix':args.suffix}
    if directory.exists():
        require(plan_path.is_file(), 'Existing pipeline is not this prepared plan')
        plan=read(plan_path)
        require(plan.get('bindings')==bindings and plan.get('status')=='PREPARED_NOT_RUN', 'Historical/current bindings differ or actual pipeline already ran')
    else:
        directory.mkdir()
        markers={}
        for mode in ('AUTHOR','REENTER'):
            path=directory/'markers'/('review-v10-alias-'+mode.lower()+'.json')
            write(path,{'schema':'dw-v10-alias-disk-input-v1','revision':'V10','guard':'QA_ONLY_FRESH_ALIAS_AUTHOR_OR_DISTINCT_SAVED_REENTER',
                        'mode':mode,'productionJarSha256':args.artifact_sha,'permanentInstances':14,'instancesPerOldAlias':2,'savedOldInventoryStacks':7,
                        'permanentScope':'exact old registries remain saved; canonical item probes use new temporary instances'})
            markers[mode]={'path':str(path),'sha256':sha(path)}
        worlds={mode:ROOT/'build'/('runtime-server-v10-alias-'+mode.lower().replace('_','-')+'-'+args.suffix)/'isolated-smoke-world'
                for mode in ('AUTHOR','REENTER','PRODUCTION_REOPEN')}
        phases=[]
        for mode in ('AUTHOR','REENTER','PRODUCTION_REOPEN'):
            wrapper=ROOT/'reports'/('V10_ALIAS_'+mode+'_'+args.suffix.upper()+'.json')
            require(not wrapper.exists() and not worlds[mode].parent.exists(), 'Refusing historical report/run overwrite')
            argv=[sys.executable,str(ROOT/'tools/run_final_server.py'),'--jar',str(jar),'--accepted-eula-file',str(eula),
                  '--profile','minimal','--startup-timeout','180','--shutdown-timeout','60',
                  '--run-name',worlds[mode].parent.name.removeprefix('runtime-server-'),'--report',str(wrapper)]
            if mode!='PRODUCTION_REOPEN':argv+=['--extra-mod',str(qa),'--alias-input',markers[mode]['path'],'--alias-timeout','60']
            if mode!='AUTHOR':argv+=['--world-copy',str(worlds['AUTHOR' if mode=='REENTER' else 'REENTER'])]
            phases.append({'mode':mode,'argv':argv,'report':str(wrapper),'world':str(worlds[mode]),'status':'NOT_RUN'})
        plan={'schema':'dw-v10-alias-disk-pipeline-v1','status':'PREPARED_NOT_RUN','bindings':bindings,'markers':markers,'phases':phases,
              'permanentInstances':14,'oldAliasTypes':7,'temporaryOrdinaryItemProbes':7,
              'savedOldInventoryStacks':7,
              'temporaryCreativeDamageProbes':{'oldRegistry':7,'newCanonical':7},
              'originalInputWorld':'NONE; new isolated flat world only',
              'clientRenderingOrPackets':'NOT_RUN; model selection and real server Item/Creative guard APIs only',
              'fixtureTerrainChanges':'explicit5x5 STONE foundation patches at declared roots, not a city conversion',
              'acceptance':'PENDING_ACTUAL_PROCESSES_AND_INDEPENDENT_TYPED_READER'}
        write(plan_path,plan)
    for row in plan['markers'].values():require(sha(row['path'])==row['sha256'],'Marker bytes changed after preparation')
    if not args.execute:
        print(json.dumps({'status':plan['status'],'plan':str(plan_path),'note':'No Minecraft/build process launched'}));return 0
    try:
        plan['status']='RUNNING';write(plan_path,plan)
        for phase in plan['phases']:
            require(not Path(phase['report']).exists() and not Path(phase['world']).parent.exists(), 'Refusing previous actual phase overwrite')
            phase['status']='RUNNING';write(plan_path,plan)
            child=subprocess.run(phase['argv'],cwd=ROOT)
            wrapper=read(phase['report']) if Path(phase['report']).is_file() else {}
            phase.update(processExitCode=child.returncode,wrapperSha256=sha(phase['report']) if Path(phase['report']).is_file() else None,
                         status=wrapper.get('status','FAIL_NO_WRAPPER'),rawStatus=wrapper.get('alias_review',{}).get('result',{}).get('status'))
            write(plan_path,plan)
            require(child.returncode==0 and wrapper.get('status')=='PASS' and wrapper.get('exit_code')==0
                    and wrapper.get('artifact_sha256')==args.artifact_sha and 'termination' not in wrapper,'Actual phase did not exit/save normally: '+phase['mode'])
            if phase['mode']!='PRODUCTION_REOPEN':require(phase['rawStatus']==(AUTHOR if phase['mode']=='AUTHOR' else REENTER),'Separate actual alias proof failed')
            else:require(not any(m.get('id')=='bloodborne_dw_review' for m in wrapper['modset']) and wrapper.get('alias_input') is None,'Production-only phase accidentally contains QA')
        plan['status']='PASS_THREE_ACTUAL_NORMAL_EXITS_REQUIRES_INDEPENDENT_TYPED_SAVE_AUDIT';write(plan_path,plan)
        print(json.dumps({'status':plan['status'],'plan':str(plan_path)}));return 0
    except Exception as failure:
        plan['status']='FAIL_ALIAS_DISK_PIPELINE';plan['failure']=str(failure);write(plan_path,plan)
        print(json.dumps({'status':plan['status'],'failure':str(failure),'plan':str(plan_path)}));return 1


if __name__=='__main__':raise SystemExit(main())
