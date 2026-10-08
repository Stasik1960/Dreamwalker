"""Explicit default60 off/on/reenter diagnostics proof on copies of one frozen derived scene.

This tool launches only when invoked. All reports, worlds and JAR copies are fresh.
It does not turn a failed QA result into a passing wrapper or alter its source world.
"""
from __future__ import annotations
import argparse, hashlib, json, re, shutil, subprocess, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
def files(world):return {p.relative_to(world).as_posix():sha(p) for p in sorted(world.rglob('*')) if p.is_file() and p.name!='session.lock'}
def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--jar',type=Path,required=True);ap.add_argument('--qa-jar',type=Path,required=True);ap.add_argument('--world',type=Path,required=True);ap.add_argument('--baseline-report',type=Path,required=True);ap.add_argument('--accepted-eula-file',type=Path,required=True);ap.add_argument('--suffix',required=True);ap.add_argument('--profile',choices=['minimal','full_server'],default='minimal');args=ap.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]+',args.suffix):raise ValueError('Unsafe suffix')
    world=args.world.resolve();jar=args.jar.resolve();qa=args.qa_jar.resolve();digest=sha(jar);baseline=json.loads(args.baseline_report.read_text(encoding='utf8'))
    if not world.is_relative_to((ROOT/'build').resolve()) or not (world/'level.dat').is_file():raise ValueError('Only saved derived scene under build is allowed')
    if baseline.get('status')!='PASS' or baseline.get('artifact_sha256')!=digest or baseline.get('exit_code')!=0:raise ValueError('Baseline production report must PASS current exact JAR')
    work=ROOT/'build'/('review-v9-diagnostics-pipeline-'+args.suffix)
    if work.exists():raise ValueError('Historical pipeline directory exists; choose fresh suffix')
    work.mkdir();artifacts=work/'artifacts';artifacts.mkdir();frozen=artifacts/jar.name;frozenqa=artifacts/qa.name;shutil.copyfile(jar,frozen);shutil.copyfile(qa,frozenqa);initial=files(world)
    report={'schema':'dreamwalker-review-v9-diagnostics-pipeline-v1','status':'RUNNING','artifactSha256':digest,'qaSha256':sha(qa),'baselineReport':{'path':str(args.baseline_report.resolve()),'sha256':sha(args.baseline_report)},'sourceWorld':str(world),'profile':args.profile,'phases':[],'manualVisualAcceptance':'NOT_RUN'};output=work/'pipeline.json';write(output,report)
    try:
        results={}
        for mode,label in [('DISABLED','off'),('ENABLED','on'),('REENTER','reenter')]:
            source=world if label!='reenter' else Path(results['on']['run_directory'])/'isolated-smoke-world'
            marker=ROOT/'tools'/('review_v9_diagnostics_'+mode.lower()+'_input.json');wrapper=ROOT/'reports'/('DIAGNOSTICS_SERVER_'+label.upper()+'_V9_'+args.suffix.upper()+'.json')
            if wrapper.exists():raise ValueError('Refusing report overwrite '+str(wrapper))
            argv=[sys.executable,str(ROOT/'tools/run_final_server.py'),'--jar',str(frozen),'--extra-mod',str(frozenqa),'--accepted-eula-file',str(args.accepted_eula_file.resolve()),'--profile',args.profile,'--world-copy',str(source),'--diagnostics-input',str(marker),'--diagnostics-timeout','100','--startup-timeout','180','--shutdown-timeout','60','--run-name','v9-diagnostics-'+label+'-'+args.suffix,'--report',str(wrapper)]
            child=subprocess.run(argv,cwd=ROOT);actual=json.loads(wrapper.read_text(encoding='utf8')) if wrapper.is_file() else {};raw=actual.get('diagnostics_review',{}).get('result',{})
            phase={'mode':mode,'argv':argv,'processExitCode':child.returncode,'wrapper':str(wrapper),'wrapperSha256':sha(wrapper) if wrapper.is_file() else None,'status':actual.get('status'),'rawStatus':raw.get('status')};report['phases'].append(phase);write(output,report)
            if child.returncode!=0 or actual.get('status')!='PASS' or actual.get('exit_code')!=0 or actual.get('artifact_sha256')!=digest or raw.get('status')!='PASS_SERVER_DIAGNOSTICS_'+mode or raw.get('checksPassed') is not True:raise ValueError('Actual '+mode+' diagnostics phase failed: '+json.dumps(phase))
            results[label]=actual
        report['sourceWorldFilesByteExactUnchanged']=initial==files(world)
        if not report['sourceWorldFilesByteExactUnchanged']:raise ValueError('Frozen original derived source world changed')
        report['status']='PASS_DIAGNOSTICS_THREE_NORMAL_SERVER_PHASES_REQUIRES_INDEPENDENT_CLIENT_AND_COMPARISON_AUDIT';write(output,report);print(json.dumps(report,ensure_ascii=False));return 0
    except Exception as failure:
        report['status']='FAIL_DIAGNOSTICS_PIPELINE';report['error']=str(failure);report['sourceWorldFilesByteExactUnchanged']=initial==files(world);write(output,report);print(json.dumps(report,ensure_ascii=False));return 1
if __name__=='__main__':raise SystemExit(main())
