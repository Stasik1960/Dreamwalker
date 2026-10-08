"""Package every file of the verified production-only review world; omit only session.lock."""
from __future__ import annotations
import argparse, hashlib, json, zipfile
from pathlib import Path
from verify_review_v8_scene import ROOT, read, require, resolve, sha

PREFIX='First-set-review-v8-scene/'

def snapshot(world):
    return {path.relative_to(world).as_posix():{'bytes':path.stat().st_size,'sha256':sha(path)}
            for path in sorted(world.rglob('*')) if path.is_file() and path.relative_to(world).as_posix()!='session.lock'}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verification-report',type=Path,required=True)
    parser.add_argument('--artifact-sha',required=True)
    parser.add_argument('--output',type=Path,default=ROOT/'build/prototype/First-set-review-v8-scene.zip')
    parser.add_argument('--report',type=Path,default=ROOT/'reports/REVIEW_V8_SCENE_ARCHIVE.json')
    args=parser.parse_args();args.output=resolve(args.output);args.report=resolve(args.report)
    require(args.output.is_relative_to((ROOT/'build').resolve()),'Review world archive must stay in the project build area')
    require(not args.output.exists(),'Never overwrite a review world archive; select a fresh revision path')
    verification_path=resolve(args.verification_report);verified=read(verification_path)
    require(verified['status']=='PASS_SAVED_OWNERS_NATIVE_FIXTURES_AND_PRODUCTION_REOPEN','Scene has no completed independent verification')
    require(verified['production_jar_sha256']==args.artifact_sha,'Independent proof belongs to a different production JAR')
    world=resolve(verified['world']);require(world.is_relative_to((ROOT/'build').resolve()) and (world/'level.dat').is_file(),'Only an isolated build world can be packaged')
    require(not args.output.resolve().is_relative_to(world),'Archive output would change the source world')
    for path_key,sha_key in [('author_report','author_report_sha256'),('production_reopen_report','production_reopen_report_sha256'),('scene_input','scene_input_sha256')]:
        require(sha(verified[path_key])==verified[sha_key],'Primary proof/input changed after verification: '+path_key)
    # Re-run the verifier after its report was written, so a stale proof cannot
    # authorize packaging a world subsequently edited by another run.
    from verify_review_v8_scene import run
    class VerificationArgs:pass
    check=VerificationArgs();check.author_report=Path(verified['author_report']);check.server_report=Path(verified['production_reopen_report']);check.scene_input=Path(verified['scene_input']);check.artifact_sha=args.artifact_sha
    fresh=run(check);require(fresh==verified,'Current world no longer matches the independent verification report')
    before=snapshot(world);args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        folder=zipfile.ZipInfo(PREFIX,(2026,10,7,0,0,0));folder.create_system=3;folder.external_attr=0o40755<<16;archive.writestr(folder,b'')
        for relative,row in sorted(before.items()):
            data=(world/relative).read_bytes();require(len(data)==row['bytes'] and hashlib.sha256(data).hexdigest()==row['sha256'],'Source file changed during archive read')
            info=zipfile.ZipInfo(PREFIX+relative,(2026,10,7,0,0,0));info.create_system=3;info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,data)
    with zipfile.ZipFile(args.output) as archive:
        require(set(archive.namelist())=={PREFIX}|{PREFIX+path for path in before},'Archive omitted/added a world file')
        for relative,row in before.items():
            data=archive.read(PREFIX+relative);require(len(data)==row['bytes'] and hashlib.sha256(data).hexdigest()==row['sha256'],'ZIP entry differs from exact source bytes')
    require(snapshot(world)==before,'Source world changed during packaging')
    result={'schema':'dreamwalker-review-v8-scene-archive-v1','status':'PACKAGED_VERIFIED_SCENE_PENDING_USER_REVIEW',
        'production_jar_sha256':args.artifact_sha,'sceneId':verified['sceneId'],'world':str(world),
        'verification_report':str(verification_path),'verification_report_sha256':sha(verification_path),
        'output':str(args.output.resolve()),'sha256':sha(args.output),'world_prefix':PREFIX,
        'file_count':len(before),'uncompressed_bytes':sum(row['bytes'] for row in before.values()),
        'files':[dict(path=path,**row) for path,row in sorted(before.items())],'omitted_files':['session.lock'],
        'every_world_file_byte_verified':'PASS','source_world_unchanged_during_archive':'PASS',
        'root_count':verified['actual_root_count'],'root_counts_by_kind':verified['root_counts_by_kind'],
        'composite_owners':verified['composite_owners'],'cached_root_and_helper_bes':verified['cached_root_and_helper_bes'],
        'ledger_cell_count':verified['ledger_cell_count'],'native_ladder_and_wall_roots':verified['native_ladder_and_wall_roots'],
        'native_fixtures':verified['native_fixtures'],'rp_controls':verified['rp_controls'],
        'visual_acceptance':'PENDING_USER_REVIEW','creative_ui_acceptance':'PENDING_USER_REVIEW',
        'original_city_included':False,'chunks_cropped':False,'generated_terrain_removed':False}
    args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'status':result['status'],'roots':result['root_count'],'files':len(before),'sha256':result['sha256']}))

if __name__=='__main__':main()
