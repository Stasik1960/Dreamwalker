"""Package a locally authored/reopened gallery with hashes and source tooling.

No GitHub publication, world conversion or production build occurs. Actual
native/pixel/performance acceptance is supplied separately by the final release
verification, and is never manufactured by this archive writer.
"""
import argparse, hashlib, json, shutil, zipfile
from pathlib import Path
from run_v11_server import files_without_links,ordinary,sha

ROOT=Path(__file__).resolve().parents[1]
NAME='Dreamwalker-BB-V12.7.1-Gallery'
WRAPPERS=['prepare_gallery_v1271_input.py','build_gallery_v1271_addon.py','verify_gallery_v1271.py','finalize_gallery_v1271_world.py','prepare_gallery_v1271_delivery.py','package_gallery_v1271.py','v1271_prepared_city.py','verify_v1271_final_readiness.py','audit_v1271_prepared_decision_usage.py','package_v1271_preparation.py']

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def archive(path,entries):
    assert not path.exists(),'Refusing to overwrite a packaged gallery'
    records=[]
    with zipfile.ZipFile(path,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as z:
        for source,name in entries:
            ordinary(source);digest=sha(source);z.write(source,name);assert sha(source)==digest
            records.append({'path':name,'bytes':source.stat().st_size,'sha256':digest})
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        for row in records:
            h=hashlib.sha256()
            with z.open(row['path']) as stream:
                for data in iter(lambda:stream.read(1<<20),b''):h.update(data)
            assert h.hexdigest()==row['sha256']
    return {'file':path.name,'sha256':sha(path),'bytes':path.stat().st_size,'members':records}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--world',type=Path,required=True);p.add_argument('--production-jar',type=Path,required=True)
    p.add_argument('--before-report',type=Path,required=True);p.add_argument('--reopen-report',type=Path,required=True);p.add_argument('--server-report',type=Path,required=True)
    p.add_argument('--preparation-readiness',type=Path,required=True)
    p.add_argument('--evidence',type=Path,action='append',default=[]);p.add_argument('--addon-source',type=Path,required=True)
    p.add_argument('--destination',type=Path,default=ROOT.parent/'releases/Dreamwalker-BB/v12.7.1-city-fixes');a=p.parse_args()
    world=a.world.absolute();dest=a.destination.absolute();assert world.is_relative_to((ROOT/'build').absolute()) and world.drive.upper()=='C:' and dest.drive.upper()=='C:'
    ordinary(world)
    for parent in (dest,*dest.parents):
        if parent.exists():ordinary(parent)
    digest=sha(a.production_jar)
    m=read(world/'gallery-manifest.json');assert m['schema']=='dreamwalker-flat-gallery-v1271' and m['productionJarSha256']==digest
    ready=read(a.preparation_readiness)
    assert ready['status']=='PASS_READ_ONLY_REUSED_PREPARATION_AND_NATIVE_RESOURCE_BINDING' and ready['productionJarSha256']==digest
    assert ready['discardedSourceCells']==0 and ready['inputFilesUnchanged']
    assert ready['sourcePlanSha256']==m['galleryPreparation']['sourcePlanSha256'] and ready['runtimeManifestSha256']==m['galleryPreparation']['runtimeManifestSha256']
    before,reopen=read(a.before_report),read(a.reopen_report)
    for report in (before,reopen):assert report['status']=='PASS' and report['productionJarSha256']==digest and report['inputFilesUnchanged']
    assert reopen['productionReopenCompared'] and before['stableExhibits']==reopen['stableExhibits']
    server=read(a.server_report);assert server['status']=='PASS' and server['exit_code']==0 and not server['errors'] and server['artifact_sha256']==digest
    assert Path(server['run_directory']).absolute()/'isolated-smoke-world'==world
    assert not any(row['id']=='dreamwalker_gallery_author' for row in server.get('extraMods',[])), 'Final reopen must use production without authoring QA'
    # Tie this actual delivery folder to the disk proof, not another saved copy.
    assert Path(reopen['world']).absolute()==world
    dest.mkdir(parents=True,exist_ok=True)
    gallery=archive(dest/(NAME+'.zip'),[(path,NAME+'/'+path.relative_to(world).as_posix()) for path in files_without_links(world) if path.relative_to(world).parts[0] not in {'playerdata','stats','advancements'}])
    evidence=[a.before_report,a.reopen_report,a.server_report,a.preparation_readiness,*a.evidence]
    verification=archive(dest/(NAME+'-Verification.zip'),[(path,f'{i:02d}-'+path.name) for i,path in enumerate(evidence,1)])
    # Include the Python helper graph, not isolated wrappers missing imports.
    tools=sorted((ROOT/'tools').glob('*.py'))
    tools += [ROOT/'tools/gallery-v127/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV127Author.java']
    parent_input=Path(m['galleryPreparation']['previousGalleryInput'])
    assert sha(parent_input)==m['galleryPreparation']['previousGalleryInputSha256']
    tooling=archive(dest/(NAME+'-Tools.zip'),[(path,path.relative_to(ROOT).as_posix()) for path in tools]+[(a.addon_source,'generated/FlatGalleryV1271Author.java'),(parent_input,'inputs/v127-gallery-parent-input.json')])
    for name in ('README-RU.txt','GALLERY-INDEX.csv'):shutil.copyfile(world/name,dest/name)
    record={'status':'PASS_LOCAL_GALLERY_PACKAGED_AFTER_PRODUCTION_REOPEN','productionJarSha256':digest,'qaNeededForSavedWorld':False,'originalMapChanged':False,'galleryCreated':True,'mainPlatforms':len(m['exhibits']),'reviewPlatforms':len(m['reviewPlatforms']),'cityOwners':len(m['city']['placedOwners']),'cityNativePassThrough':len(m['city']['passThroughBlocks']),'galleryPreparation':m['galleryPreparation'],'archives':[gallery,verification,tooling],'nativeVisualAndPerformanceAcceptance':'Refer to the separately supplied final release acceptance; disk proof alone does not certify pixel/FPS equality'}
    (dest/'gallery-release-manifest.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    (dest/'GALLERY-SHA256SUMS.txt').write_text(''.join(sha(path)+'  '+path.name+'\n' for path in sorted(dest.iterdir()) if path.is_file() and path.name.startswith(NAME)),encoding='ascii')

if __name__=='__main__':main()
