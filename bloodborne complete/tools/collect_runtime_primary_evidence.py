"""Copy declared isolated runtime logs/results into deliverable reports without changing runs."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT=Path(__file__).resolve().parents[1]
def digest(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wrapper',type=Path,action='append',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();destination=(ROOT/'reports/runtime').resolve();destination.mkdir(parents=True,exist_ok=True)
    records=[]
    for requested in args.wrapper:
        wrapper=requested.resolve()
        if not wrapper.is_relative_to((ROOT/'reports').resolve()):raise ValueError('Wrapper must be a project report')
        report=json.loads(wrapper.read_text(encoding='utf8'));run=Path(report['run_directory']).resolve()
        if not run.is_relative_to((ROOT/'build').resolve()):raise ValueError('Only explicitly isolated project runtimes can be collected')
        row={'wrapper':str(wrapper),'wrapper_sha256':digest(wrapper),'artifact_sha256':report['artifact_sha256'],'actual_phase_status':report['status'],'files':[]}
        for name in ('launch-console.log','client-review-output.json','source-review-output.json','review-scene-output.json'):
            source=run/name
            if not source.is_file():continue
            target=destination/(wrapper.stem+'.'+name)
            expected=digest(source)
            if target.exists() and digest(target)!=expected:raise ValueError('Refusing to overwrite distinct primary evidence')
            if not target.exists():shutil.copyfile(source,target)
            if digest(target)!=expected:raise ValueError('Primary byte copy differs')
            row['files'].append({'source':str(source),'delivered_path':str(target.relative_to(ROOT)),'sha256':expected,'bytes':target.stat().st_size,'byte_copy':'PASS'})
        if not row['files']:raise ValueError('No primary runtime log/result found')
        records.append(row)
    output=args.output.resolve()
    if not output.is_relative_to((ROOT/'reports').resolve()):raise ValueError('Evidence manifest belongs under reports')
    result={'schema':'dreamwalker-isolated-runtime-primary-evidence-v1','status':'PASS_PRIMARY_BYTES_COPIED_WITH_DECLARED_RUNTIME_STATUSES','runtime_phase_statuses_are_not_overridden':True,'records':records}
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':result['status'],'wrappers':len(records),'files':sum(len(r['files']) for r in records)}))
if __name__=='__main__':main()
