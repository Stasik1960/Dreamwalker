"""Run a fresh V9 ordinary stand through QA restart and production-only save.

Each phase uses a new isolated directory and exact fixed JAR bytes. Stop at the
first failed wrapper or actual authoring/gameplay verdict; retain every attempt.
No original installation or world is changed.
"""
from __future__ import annotations
import argparse, hashlib, json, subprocess, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jar',type=Path,required=True)
    p.add_argument('--qa-jar',type=Path,required=True)
    p.add_argument('--accepted-eula-file',type=Path,required=True)
    p.add_argument('--suffix',required=True)
    args=p.parse_args()
    args.jar=args.jar.resolve();args.qa_jar=args.qa_jar.resolve()
    frozen={str(path):sha(path) for path in (args.jar,args.qa_jar)}
    record={'schema':'dreamwalker-v9-sequential-server-pipeline-v1','artifacts':frozen,'phases':[],'status':'RUNNING'}
    status=ROOT/'build'/('review-v9-server-pipeline-'+args.suffix+'.json')
    if status.exists(): raise ValueError('Never overwrite a previous pipeline')
    def save(): status.write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
    save()
    try:
        for profile in ('minimal','full_server'):
            previous=None
            label='MINIMAL' if profile=='minimal' else 'FULL'
            for phase in ('AUTHOR','GAMEPLAY_REOPEN','PRODUCTION_REOPEN'):
                for path,expected in frozen.items():
                    if sha(Path(path))!=expected: raise ValueError('Frozen artifact changed: '+path)
                report=ROOT/'reports'/('FIRST_SET_'+label+'_'+phase+'_V9_'+args.suffix.upper()+'.json')
                if report.exists(): raise ValueError('Never overwrite a previous phase report: '+str(report))
                command=[sys.executable,'-X','utf8',str(ROOT/'tools/run_final_server.py'),
                    '--jar',str(args.jar),'--profile',profile,
                    '--accepted-eula-file',str(args.accepted_eula_file),
                    '--run-name','first-set-v9-'+profile+'-'+phase.lower()+'-'+args.suffix,
                    '--startup-timeout','180','--shutdown-timeout','60','--report',str(report)]
                if previous: command+=['--world-copy',str(previous)]
                if phase!='PRODUCTION_REOPEN':
                    command+=['--extra-mod',str(args.qa_jar),'--gameplay-timeout','90',
                        '--gameplay-input',str(ROOT/'tools'/('first_set_v9_gameplay_input.json' if phase=='AUTHOR' else 'first_set_v9_gameplay_input_reopen.json'))]
                if phase=='AUTHOR': command+=['--scene-input',str(ROOT/'tools/first_set_scene_v9_input.json')]
                print(label+' '+phase+' START',flush=True)
                row={'profile':profile,'phase':phase,'report':str(report),'status':'RUNNING','command':command}
                record['phases'].append(row);save()
                completed=subprocess.run(command,cwd=ROOT,check=False)
                if not report.is_file(): raise ValueError('Phase produced no wrapper: '+str(report))
                wrapper=json.loads(report.read_text(encoding='utf8'))
                if completed.returncode or wrapper.get('status')!='PASS' or wrapper.get('artifact_sha256')!=frozen[str(args.jar)] or wrapper.get('termination'):
                    raise ValueError('Actual server phase failed: '+str(report))
                if phase=='AUTHOR' and wrapper.get('review_scene_output',{}).get('result',{}).get('status')!='PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN':
                    raise ValueError('Actual ordinary scene author failed: '+str(report))
                if phase!='PRODUCTION_REOPEN':
                    expected='PASS_V9_ORDINARY_GAMEPLAY_AUTHOR_REQUIRES_ACTUAL_RESTART' if phase=='AUTHOR' else 'PASS_V9_ORDINARY_GAMEPLAY_AFTER_ACTUAL_RESTART'
                    if wrapper.get('ordinary_gameplay_output',{}).get('result',{}).get('status')!=expected:
                        raise ValueError('Actual ordinary gameplay failed: '+str(report))
                row['status']='PASS';previous=Path(wrapper['run_directory'])/'isolated-smoke-world';save()
                print(label+' '+phase+' PASS',flush=True)
        record['status']='PASS_SIX_ACTUAL_PHASES_REQUIRES_INDEPENDENT_SAVED_WORLD_AUDIT';save()
    except BaseException as exc:
        record['status']='FAIL';record['error']=str(exc);save();raise
    return 0
if __name__=='__main__': raise SystemExit(main())
