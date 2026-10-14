"""Invoke selected prepared steps sequentially with persistent logs/results.

Without --execute this only lists the requested concrete commands. Root must
serialize native/JVM work and run this wrapper through Guard -Mode Operation.
"""
import argparse, json, os, subprocess, traceback
from pathlib import Path
from verify_v1271_final_readiness import ROOT,ordinary,read,sha,write

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--workflow',type=Path,default=ROOT/'build/v1271-gallery/workflow.json')
    p.add_argument('--steps',default='4,5');p.add_argument('--execute',action='store_true')
    p.add_argument('--extra-evidence',type=Path,action='append',default=[],help='Additional verified evidence for package step12 only')
    p.add_argument('--report',type=Path,default=ROOT/'build/v1271-gallery/author-workflow-result.json');a=p.parse_args()
    report=ordinary(a.report);workflow_path=ordinary(a.workflow);workflow=read(workflow_path)
    indices=[int(v) for v in a.steps.split(',')];assert indices==sorted(set(indices)) and all(1<=i<=len(workflow['steps']) for i in indices)
    assert not a.extra_evidence or indices==[12],'Additional evidence is valid only for gallery packaging step12'
    production=ordinary(workflow['productionJar']);digest=sha(production);rows=[]
    try:
        for index in indices:
            step=workflow['steps'][index-1];command=list(step['command']);assert Path(command[0]).is_file() and Path(command[1]).is_relative_to(ROOT/'tools')
            for artifact in a.extra_evidence:command.extend(['--evidence',str(ordinary(artifact))])
            row={'step':index,'name':step['name'],'requiresJvm':step['requiresJvm'],'command':command,'status':'PREPARED_NOT_EXECUTED'};rows.append(row)
            write(report,{'status':'RUNNING' if a.execute else 'PREPARED_COMMANDS_NOT_EXECUTED','productionJarSha256':digest,'workflowSha256':sha(workflow_path),'steps':rows})
            if not a.execute:continue
            log=report.with_name(report.stem+f'-step{index:02d}.log')
            with log.open('wb') as output:
                code=subprocess.run(command,cwd=ROOT,stdout=output,stderr=subprocess.STDOUT,env=os.environ.copy(),creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0).returncode
            row.update(status='PASS_PROCESS_EXIT_REQUIRES_NATIVE_REPORT' if code==0 else 'FAIL_PROCESS',exitCode=code,log=str(log),logSha256=sha(log))
            assert code==0,('Workflow step failed',index,str(log))
            assert sha(production)==digest,'Production JAR changed during isolated gallery workflow'
        write(report,{'status':'PASS_SEQUENTIAL_PROCESS_STEPS_REQUIRES_NATIVE_REPORTS' if a.execute else 'PREPARED_COMMANDS_NOT_EXECUTED','productionJarSha256':digest,'workflowSha256':sha(workflow_path),'productionJarUnchanged':sha(production)==digest,'steps':rows})
    except BaseException:
        write(report,{'status':'FAIL','productionJarSha256':digest,'steps':rows,'error':traceback.format_exc()});raise

if __name__=='__main__':main()
