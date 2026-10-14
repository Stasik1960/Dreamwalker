"""Reuse the verified donor server/dependencies/EULA on current C; no Java.

Without --execute this validates and writes a copy recipe only. With --execute
it copies ordinary verified files into a fresh current-project build directory.
It never extracts the original map, rebuilds preparation or converts a world.
Do not run this disk work alongside native FPS measurement.
"""
import argparse,hashlib,json,re,shutil,traceback,zipfile
from pathlib import Path
from verify_v1271_final_readiness import ROOT,ordinary,read,sha,write

BASE=Path(r'C:\Users\vakir\.codex\worktrees\3f07\DW\bloodborne complete')
DONOR=BASE/'build/runtime-server-v127-new-gallery-author2'
BOOTSTRAP=BASE/'build/v127-server-bootstrap'
SQL='384b1dd6548e356dc4b078848443f9df779d9c7e2338c89e183155949d7bfffb'
RUNTIME='6da6ba36e8ccc8981483821755f688adf331b5e305f78c187863bb7936a0121f'

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--production-jar',type=Path,required=True)
    p.add_argument('--readiness',type=Path,required=True,help='Final production-bound reused-preparation readiness JSON')
    p.add_argument('--author-jar',type=Path)
    p.add_argument('--author-build-report',type=Path,help='PASS_AUTHOR_BUILD evidence for the repaired author')
    p.add_argument('--output',type=Path,default=ROOT/'build/v1271-converter-inputs')
    p.add_argument('--report',type=Path,default=ROOT/'docs/v12.7.1/conversion/converter-input-copy.json')
    p.add_argument('--java-home',type=Path,default=Path(r'C:\Users\vakir\AppData\Roaming\.minecraft\runtime\java-runtime-gamma\windows\java-runtime-gamma'))
    p.add_argument('--execute',action='store_true');a=p.parse_args()
    production=ordinary(a.production_jar);out=ordinary(a.output)
    assert out.is_relative_to(ROOT/'build') and not out.exists(),'Choose a fresh current-project build directory'
    productionHash=sha(production);ready=read(ordinary(a.readiness))
    assert ready['status']=='PASS_READ_ONLY_REUSED_PREPARATION_AND_NATIVE_RESOURCE_BINDING'
    assert ready['productionJarSha256']==productionHash,'Production JAR differs from final readiness binding'
    assert ready['sourcePlanSha256']==SQL and ready['runtimeManifestSha256']==RUNTIME
    assert ready['discardedSourceCells']==0 and ready['inputFilesUnchanged']
    prep=ROOT/'build/v1271-city-preparation';manifest=read(prep/'runtime/manifest.json')
    assert sha(prep/'source-plan.sqlite')==SQL and sha(prep/'runtime/manifest.json')==RUNTIME
    assert manifest['runtimeExecution']=='READY_REUSED_NATIVE_REFERENCE_VERIFIED_PREPARATION' and manifest['counts']['REMOVE']==0
    bootstrap=read(ordinary(BOOTSTRAP/'bootstrap.json'))
    assert bootstrap['status']=='PASS_PREPARED_NATIVE_NOT_RUN' and bootstrap['minecraft']=='1.20.1' and bootstrap['loader']=='0.16.10'
    files={}
    def add(source,relative,expected=None):
        source=ordinary(source);assert source.is_file() and relative not in files
        digest=sha(source);assert expected is None or digest==expected,source
        files[relative]={'source':str(source),'destination':str(out/relative),'sha256':digest,'bytes':source.stat().st_size}
    add(BOOTSTRAP/'bootstrap.json','bootstrap/bootstrap.json')
    add(BOOTSTRAP/'fabric-loader-profile.json','bootstrap/fabric-loader-profile.json',bootstrap['officialProfileSha256'])
    assert hashlib.sha1(ordinary(BOOTSTRAP/'minecraft-version.json').read_bytes()).hexdigest()==bootstrap['minecraftVersionOfficialSha1']
    add(BOOTSTRAP/'minecraft-version.json','bootstrap/minecraft-version.json')
    add(BOOTSTRAP/bootstrap['launcher']['path'],'bootstrap/'+bootstrap['launcher']['path'],bootstrap['launcher']['sha256'])
    for item in bootstrap['runtimeFiles']:
        relative=Path(item['sourcePath']);assert not relative.is_absolute() and '..' not in relative.parts
        add(BOOTSTRAP/relative,'bootstrap/'+relative.as_posix(),item['sha256'])
    for filename,identifier in [('fabric-api-0.92.9+1.20.1.jar','fabric-api'),('geckolib-fabric-1.20.1-4.4.9.jar','geckolib'),('worldedit-mod-7.2.15.jar','worldedit')]:
        path=ordinary(DONOR/'mods'/filename)
        with zipfile.ZipFile(path) as z:assert json.loads(z.read('fabric.mod.json'))['id']==identifier
        add(path,'dependencies/'+filename)
    eula=ordinary(DONOR/'eula.txt')
    assert re.search(r'(?m)^\s*eula\s*=\s*true\s*$',eula.read_text(encoding='utf-8-sig'))
    add(eula,'accepted-eula-evidence.txt')
    author=ordinary(a.author_jar or ROOT/'build/libs/dreamwalker-v127-full-conversion-author-1.2.7.jar')
    authorReady=False;authorHash=None
    if a.author_jar:
        assert a.author_build_report,'Supply the actual repaired author PASS build report'
        assert author.is_file()
        with zipfile.ZipFile(author) as z:
            assert json.loads(z.read('fabric.mod.json'))['id']=='dreamwalker_full_conversion_author'
            assert json.loads(z.read('dw-conversion-binding.json'))['productionJarSha256']==productionHash
        authorReady=True;authorHash=sha(author)
        built=read(ordinary(a.author_build_report))
        assert built['status']=='PASS_AUTHOR_BUILD' and built['productionJarSha256']==productionHash and built['productionJarUnchanged']
        assert ordinary(built['addon'])==author and built['addonSha256']==authorHash
    command=['tools/run_v127_full_conversion.py',
        '--derived-world',str(ROOT/'build/v1271-derived-city'),
        '--runtime-plan',str(prep/'runtime'),'--source-plan',str(prep/'source-plan.sqlite'),
        '--source-style-policy',str(ROOT/'docs/v12.7.1/conversion/source-style-policy.json'),
        '--runtime-directory',str(ROOT/'build/v1271-city-conversion-server'),
        '--production-jar',str(production),'--author-jar',str(author),
        '--fabric-api',str(out/'dependencies/fabric-api-0.92.9+1.20.1.jar'),
        '--geckolib',str(out/'dependencies/geckolib-fabric-1.20.1-4.4.9.jar'),
        '--server-launcher',str(out/'bootstrap/fabric-server-launch.jar'),
        '--server-bootstrap',str(out/'bootstrap'),
        '--accepted-eula-file',str(out/'accepted-eula-evidence.txt'),
        '--java-home',str(ordinary(a.java_home)),'--heap-gb','4','--timeout-hours','24','--authorize-full-conversion']
    record={'schemaVersion':1,'status':'PASS_REVIEWED_COPY_RECIPE_NOT_EXECUTED','productionJarSha256':productionHash,
        'sourcePlanSha256':SQL,'runtimeManifestSha256':RUNTIME,'output':str(out),'files':files,
        'readinessPath':str(ordinary(a.readiness)),'readinessSha256':sha(a.readiness),
        'authorBoundAndVerified':authorReady,'authorJarSha256':authorHash,
        'authorBuildReport':str(ordinary(a.author_build_report)) if a.author_build_report else None,
        'authorBuildReportSha256':sha(a.author_build_report) if a.author_build_report else None,
        'conversionCommandArguments':command,'conversionCommandExecuted':False,
        'derivedWorldCreated':False,'originalArchivesWritten':False,'originalMapRescanned':False,'javaLaunched':False,'newEulaAccepted':False}
    if a.execute:
        out.mkdir(parents=True)
        for relative,item in files.items():
            source=ordinary(Path(item['source']));target=ordinary(out/relative)
            assert sha(source)==item['sha256'],'Donor input changed before copy'
            target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
            assert sha(target)==item['sha256'],target
        record['status']='PASS_LOCAL_CONVERTER_INPUTS_COPIED_AND_VERIFIED'
        write(out/'manifest.json',record)
    write(ordinary(a.report),record)

if __name__=='__main__':
    try:main()
    except BaseException:write(ROOT/'build/final-city-fixes/converter-input-copy-error.json',{'error':traceback.format_exc()});raise
