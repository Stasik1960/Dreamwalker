"""Explicit V9 bounded source AUTHOR -> production REOPEN -> QA persisted replay.

Run only after the root freezes current JARs. Each runtime and report is fresh;
stop at any failed actual verdict, including verifier JSONs whose process exits0.
The original coordinate fixture, prepared input and migration plan are read-only.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
INPUT_SHA='f5c8310298d58560b1393db5340dc84ebeca4cf9e4a9c0bb0c7e1e3bd78cd89b'

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path):return json.loads(Path(path).read_text(encoding='utf8'))

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['jar','qa-jar','accepted-eula-file']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--suffix',required=True)
    p.add_argument('--source-input',type=Path,default=ROOT/'build/source-review-prepared-v9-final1/source-review-input.json')
    p.add_argument('--source-input-sha',default=INPUT_SHA)
    p.add_argument('--preload-report',type=Path,default=ROOT/'reports/SOURCE_REVIEW_PRELOAD_INDEPENDENT_V9_FINAL1.json')
    p.add_argument('--migration-plan',type=Path,default=ROOT/'reports/FIRST_SET_MIGRATION_PLAN_V9.json')
    p.add_argument('--frozen-source',type=Path,default=ROOT/'build/prototype/Source-coordinate-fixture.zip')
    args=p.parse_args()
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]{0,40}',args.suffix):raise ValueError('Unsafe evidence suffix')
    for name,value in vars(args).items():
        if isinstance(value,Path):setattr(args,name,value.resolve())
    suffix=args.suffix.upper()
    run_root=ROOT/'build'/('review-v9-source-pipeline-'+args.suffix)
    if run_root.exists():raise ValueError('Never overwrite a previous source pipeline')
    manifest=read(args.source_input)
    if sha(args.source_input)!=args.source_input_sha or manifest.get('reviewRevision')!='V9' or len(manifest['objects'])!=41 or manifest['sourceMemberCount']!=110:
        raise ValueError('Source manifest differs from the exact approved V9 prepared input')
    prepared_world=args.source_input.parent/'world'
    if not (prepared_world/'level.dat').is_file():raise ValueError('Prepared immutable source world is missing')
    preload=read(args.preload_report)
    if preload.get('status')!='PASS_PRELOAD_PRESERVATION' or preload['input_sha256']!=args.source_input_sha:
        raise ValueError('Source preparation has no exact independent preload proof')
    if sha(args.frozen_source)!=manifest['sourceFixtureSha256']:raise ValueError('Frozen original coordinate fixture changed')
    run_root.mkdir(parents=True)
    frozen={str(path):sha(path) for path in [args.jar,args.qa_jar,args.source_input,args.preload_report,args.migration_plan,args.frozen_source]}
    artifact_dir=run_root/'artifacts';artifact_dir.mkdir()
    production=artifact_dir/args.jar.name;qa=artifact_dir/args.qa_jar.name
    for source,target in [(args.jar,production),(args.qa_jar,qa)]:
        shutil.copyfile(source,target)
        if sha(target)!=frozen[str(source)]:raise ValueError('Immutable runtime JAR copy differs')
    record={'schema':'dreamwalker-review-v9-source-pipeline-v1','status':'RUNNING','frozen_inputs':frozen,
            'production_jar_sha256':sha(production),'qa_jar_sha256':sha(qa),'steps':[],'user_review':'PENDING_USER_REVIEW','full_task_status':'NOT_READY_FULL_TASK'}
    status=run_root/'pipeline.json'
    def save():status.write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    def assert_frozen():
        for path,expected in frozen.items():
            if sha(path)!=expected:raise ValueError('A frozen artifact/input changed: '+path)
    def execute(label,command,report,expected):
        assert_frozen()
        if report.exists():raise ValueError('Never overwrite existing evidence: '+str(report))
        row={'step':label,'command':command,'report':str(report),'status':'RUNNING'};record['steps'].append(row);save()
        print(label+' START',flush=True)
        result=subprocess.run(command,cwd=ROOT,check=False)
        if not report.is_file():raise ValueError('Step produced no report: '+label)
        actual=read(report);row['actual_status']=actual.get('status');row['process_exit_code']=result.returncode;row['report_sha256']=sha(report);save()
        if result.returncode or not actual.get('status','').startswith(expected) or actual.get('failures'):
            raise ValueError('Actual process/report verdict failed: '+label+' '+str(actual.get('status')))
        row['status']='PASS';save();print(label+' PASS',flush=True);return actual
    def reject_actual(message):
        record['steps'][-1]['status']='FAIL';record['steps'][-1]['error']=message;save();raise ValueError(message)
    reports={phase:ROOT/'reports'/f'SERVER_SOURCE_REVIEW_{phase}_V9_{suffix}.json' for phase in ['AUTHOR','REOPEN','REPLAY']}
    worlds={};wrappers={}
    save()
    try:
        for phase in ['AUTHOR','REOPEN','REPLAY']:
            command=[sys.executable,'-X','utf8',str(ROOT/'tools/run_final_server.py'),'--jar',str(production),'--profile','minimal',
                     '--accepted-eula-file',str(args.accepted_eula_file),'--startup-timeout','180','--shutdown-timeout','60',
                     '--run-name','source-review-v9-'+phase.lower()+'-'+args.suffix,'--report',str(reports[phase])]
            previous=prepared_world if phase=='AUTHOR' else worlds['AUTHOR'] if phase=='REOPEN' else worlds['REOPEN']
            command+=['--world-copy',str(previous)]
            if phase!='REOPEN':command+=['--extra-mod',str(qa),'--source-input',str(args.source_input)]
            value=execute('SOURCE_'+phase,command,reports[phase],'PASS')
            if value.get('status')!='PASS' or value.get('exit_code')!=0 or value.get('termination') or value.get('artifact_sha256')!=sha(production) or value.get('rp_initialization_count')!=1 or value.get('original_world_loaded') is not False:
                reject_actual('Source server wrapper failed current artifact/normal-exit/isolation proof: '+phase)
            if phase!='REOPEN':
                raw=value['bounded_source_output']
                record['steps'][-1]['bounded_actual_status']=raw['result'].get('status');save()
                if sha(raw['path'])!=raw['sha256'] or read(raw['path'])!=raw['result']:reject_actual('Actual source output bytes differ')
                expected='PASS_BOUNDED_SOURCE_MIGRATION_REQUIRES_PRODUCTION_REOPEN' if phase=='AUTHOR' else 'PASS_SOURCE_MIGRATION_ALREADY_APPLIED_NO_OP'
                if raw['result'].get('status')!=expected:reject_actual('Actual source author/replay failed: '+phase)
                if phase=='REPLAY' and raw['result'].get('idempotence')!='PASS_PERSISTED_SIGNATURE_UUID_AND_ALL_FOOTPRINT_BINDINGS':reject_actual('Persisted source replay was not an exact no-op')
            elif any(row.get('extra_mod') for row in value['modset']) or value.get('bounded_source_input'):
                reject_actual('Source production reopen still includes QA')
            wrappers[phase]=value;worlds[phase]=Path(value['run_directory'])/'isolated-smoke-world'
        outputs={name:ROOT/'reports'/f'SOURCE_REVIEW_{name}_INDEPENDENT_V9_{suffix}.json' for name in ['POSTSAVE','POSTSAVE_REOPEN','SECOND_SAVE','PERSISTED_REPLAY']}
        actual_raw=wrappers['AUTHOR']['bounded_source_output']['path']
        for name,phase in [('POSTSAVE','AUTHOR'),('POSTSAVE_REOPEN','REOPEN')]:
            command=[sys.executable,'-X','utf8',str(ROOT/'tools/verify_source_review_saved.py'),'--before',str(prepared_world),'--after',str(worlds[phase]),
                     '--input',str(args.source_input),'--runtime-report',actual_raw,'--output',str(outputs[name])]
            execute(name,command,outputs[name],'PASS_ARCHITECTURE_AND_FOREIGN_DATA_WITH_EXPLICIT_RUNTIME_DIFFS')
        for name,phase in [('SECOND_SAVE','REOPEN'),('PERSISTED_REPLAY','REPLAY')]:
            command=[sys.executable,'-X','utf8',str(ROOT/'tools/verify_source_review_reopen.py'),'--author',str(worlds['AUTHOR']),'--after',str(worlds[phase]),
                     '--input',str(args.source_input),'--original',str(prepared_world),'--output',str(outputs[name])]
            execute(name,command,outputs[name],'PASS_PERSISTED_OWNERSHIP_NATIVE_DATA_AND_NONRECURSIVE_RP_SOURCE_PAYLOAD')
        archive=ROOT/'build/prototype'/('First-set-migrated-source-fixture-v9-'+args.suffix+'.zip')
        archive_report=ROOT/'reports'/f'FIRST_SET_SOURCE_REVIEW_SCENE_V9_{suffix}.json'
        command=[sys.executable,'-X','utf8',str(ROOT/'tools/archive_source_review.py'),'--revision','v9','--artifact',str(production),'--artifact-sha',sha(production),
                 '--world',str(worlds['REOPEN']),'--input',str(args.source_input),'--preload-report',str(args.preload_report),'--migration-plan',str(args.migration_plan),
                 '--original-fixture',str(args.frozen_source),'--archive',str(archive),'--report',str(archive_report)]
        for phase in ['AUTHOR','REOPEN','REPLAY']:command+=['--'+phase.lower()+'-report',str(reports[phase])]
        for flag,name in [('postsave-report','POSTSAVE'),('postsave-reopen-report','POSTSAVE_REOPEN'),('second-save-report','SECOND_SAVE'),('persisted-replay-report','PERSISTED_REPLAY')]:command+=['--'+flag,str(outputs[name])]
        execute('ARCHIVE_EXACT_PRODUCTION_REOPEN',command,archive_report,'PASS_ARCHIVE_EXACT_PRODUCTION_REOPEN')
        assert_frozen();record['status']='PASS_SOURCE_AUTHOR_PRODUCTION_REOPEN_PERSISTED_REPLAY_INDEPENDENT_ARCHIVE_PENDING_USER_REVIEW';record['archive_report']=str(archive_report);save()
    except BaseException as error:
        record['status']='FAIL';record['error']=str(error);save();raise
    return 0

if __name__=='__main__':raise SystemExit(main())
