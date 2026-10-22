"""Package the completed world once, verify one restoration, retain its receipt."""
from pathlib import Path
import hashlib, json, os, shutil, sys, zipfile
from datetime import datetime, timezone
sys.path.insert(0, str(Path(__file__).resolve().parent))
import portable_static as c

checkout=c.LOCAL/'publish-main'
release=checkout/'releases/Dreamwalker-BB/v12.7.4-static-city.6-20261017'
verify=c.LOCAL/'publish-restore-check-01'
c.need(not release.exists() and not verify.exists(), 'Packaging/restoration destination already exists')
release.mkdir(parents=True)
build=c.read(c.LOCAL/'work/source/GAME-BUILD-RECEIPT.json')
full=c.read(c.WORK/'full-evidence-2/result.json')
c.need(full['status']=='PASS_COMPLETE_EXPERIMENTAL_STATIC_DATA_MIGRATION_GAMEPLAY_UNVERIFIED', 'Completed migration receipt required')
native=full['nativeResult']
artifacts=[]
def record(path,role):
    row={'file':path.relative_to(release).as_posix(),'bytes':path.stat().st_size,'sha256':c.sha(path),'role':role}
    artifacts.append(row)
    return row
def copy(source,relative,role,expected=None):
    target=release/relative
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(source,target)
    row=record(target,role)
    if expected: c.need(row['sha256']==expected, 'Published copy differs from existing receipt')
    return row

world_entries=[]
lock_bytes=(c.WORLD/'session.lock').read_bytes()
archive=release/'Dreamwalker-Static-City.6-EXPERIMENTAL-world.zip'
with c.stopped_world(c.WORLD):
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as output:
        for parent,dirs,files in os.walk(c.WORLD):
            parent=Path(parent)
            if parent==c.WORLD: dirs[:]=[x for x in dirs if x!='.dw-static-runtime']
            dirs.sort()
            for name in sorted(files):
                path=parent/name
                c.need(not (getattr(path.lstat(),'st_file_attributes',0)&1024),'World contains a reparse point')
                relative=path.relative_to(c.WORLD).as_posix()
                digest=hashlib.sha256(); size=0
                info=zipfile.ZipInfo.from_file(path,'world/'+relative)
                info.compress_type=zipfile.ZIP_DEFLATED
                with output.open(info,'w',force_zip64=True) as target:
                    if relative=='session.lock':
                        target.write(lock_bytes);digest.update(lock_bytes);size=len(lock_bytes)
                    else:
                        with path.open('rb') as source:
                            for block in iter(lambda:source.read(4*1024*1024),b''):
                                target.write(block);digest.update(block);size+=len(block)
                world_entries.append({'path':relative,'bytes':size,'sha256':digest.hexdigest()})
archive_row=record(archive,'completed experimental world; game .6 required')
c.atomic_json(release/'WORLD-MANIFEST.json',{'schemaVersion':1,'files':world_entries,'fileCount':len(world_entries),'logicalBytes':sum(x['bytes'] for x in world_entries),'excludedFolders':['world/.dw-static-runtime (migration server binaries, not Minecraft world data; preserved locally)']})
record(release/'WORLD-MANIFEST.json','world restoration file manifest')

verify.mkdir()
with zipfile.ZipFile(archive) as source:
    expected={'world/'+row['path']:row for row in world_entries}
    c.need(set(source.namelist())==set(expected) and len(source.infolist())==len(expected),'Archive completeness differs')
    for entry in source.infolist():
        parts=Path(entry.filename).parts
        c.need(not Path(entry.filename).is_absolute() and '..' not in parts and parts[0]=='world','Unsafe archive path')
        target=verify.joinpath(*parts)
        target.parent.mkdir(parents=True,exist_ok=True)
        digest=hashlib.sha256(); size=0
        with source.open(entry) as incoming,target.open('xb') as outgoing:
            for block in iter(lambda:incoming.read(4*1024*1024),b''):
                outgoing.write(block);digest.update(block);size+=len(block)
        row=expected[entry.filename]
        c.need(size==row['bytes'] and digest.hexdigest()==row['sha256'],'Restored world data mismatch: '+entry.filename)
c.atomic_json(release/'PACKAGE-RESTORE-RECEIPT.json',{'schemaVersion':1,'status':'PASS_ONE_COMPLETENESS_SHA256_AND_RESTORATION','verifiedUtc':datetime.now(timezone.utc).isoformat(),'archive':archive_row,'restoredFiles':len(world_entries),'restoredBytes':sum(x['bytes'] for x in world_entries),'restorationPasses':1,'nativeWorldSessionLockHeldDuringPackaging':True,'minecraftLaunched':False,'gameplayVerified':False,'temporaryRestorationRemovedAfterSuccess':True})
record(release/'PACKAGE-RESTORE-RECEIPT.json','one packaging completeness/SHA256/restoration receipt')
# Remove only this script's own successfully verified temporary extraction.
resolved=verify.resolve()
c.need(resolved==c.LOCAL.resolve()/'publish-restore-check-01' and resolved.parent==c.LOCAL.resolve(),'Temporary cleanup path escapes intended directory')
c.need(not (getattr(resolved.lstat(),'st_file_attributes',0)&1024),'Temporary extraction became a reparse point')
shutil.rmtree(resolved)

copy(Path(build['gameJar']),Path(build['gameJar']).name,'game mod',build['sha256'])
copy(Path(build['sourcesJar']),Path(build['sourcesJar']).name,'game mod sources',build['sourcesSha256'])
copy(Path(build['author']),'tools/'+Path(build['author']).name,'author tool; do not install in game profile',build['authorSha256'])
evidence={
 'native-full-result.json':c.WORK/'full-evidence-2/result.json',
 'native-full-console.log':c.WORK/'full-evidence-2/console.log',
 'pilot-result.json':c.WORK/'pilot-evidence/result.json',
 'pilot-preservation.json':c.WORK/'pilot-preservation-2/result.json',
 'selection-proof.json':c.LOCAL/'work/catalog-expansion/generated/selection-proof.json',
 'geometry-delta-check.json':c.LOCAL/'work/catalog-expansion/EXPORT-DELTA-CHECK.json',
 'game-build-receipt.json':c.LOCAL/'work/source/GAME-BUILD-RECEIPT.json',
 'functional-input-scope.json':c.LOCAL/'work/source/staticAuthor-resources/.SOURCE-SCOPE-POLICY.json',
}
for name,source in evidence.items(): copy(source,'evidence/'+name,'existing evidence; not a new test')
c.atomic_json(release/'MANIFEST.json',{'schemaVersion':1,'status':full['status'],'sourceHandoffRelease':'https://github.com/Stasik1960/Dreamwalker/releases/tag/dw-city-handoff-20261017-r1','sourceCommit':'581e340b0baa4546635002249ed29ed4264c2855','sourceCompletedGame4Sha256':c.SOURCE_GAME4_SHA256,'gameSha256':build['sha256'],'gameSourcesSha256':build['sourcesSha256'],'authorSha256':build['authorSha256'],'catalogSha256':build['catalogSha256'],'operationId':native['operationId'],'checkpointSha256':full['retainedCheckpointSha256'],'processedComponents':native['committed']+native['alreadyPresent']+native['architectureSkipped'],'committedComponents':native['committed'],'alreadySavedComponents':native['alreadyPresent'],'skippedComponents':native['architectureSkipped'],'migratedOwners':native['architectureOwners'],'migratedCells':native['architectureCells'],'skippedOwners':native['architectureSkippedOwners'],'skippedCells':native['architectureSkippedCells'],'skipReasons':native['architectureSkipReasons'],'preparedRootCoveragePercent':native['architectureOwners']/2754940*100,'gameplayVerified':False,'knownUnresolvedEdgeCaveat':full['knownUnresolvedEdgeCaveat'],'artifacts':artifacts})
(release/'SHA256SUMS.txt').write_text(''.join(x['sha256']+'  '+x['file']+'\n' for x in artifacts)+c.sha(release/'MANIFEST.json')+'  MANIFEST.json\n',encoding='utf-8')
print(json.dumps({'status':'PACKAGED_AND_RESTORED_ONCE','release':str(release),'worldFiles':len(world_entries),'worldLogicalBytes':sum(x['bytes'] for x in world_entries),'worldZipBytes':archive.stat().st_size,'worldZipSha256':archive_row['sha256'],'temporaryRestoreRemoved':not verify.exists()},ensure_ascii=False))
