"""Build a separately named bounded resume QA author; production JAR unchanged.

Uses the ordinary author builder's preparation, then adds an opt-in QA entrypoint
and Java source directory to this separate artifact. Root serializes all Java.
--prepare-only creates configuration without starting Java.
"""
import argparse,json,os,subprocess,sys,traceback,zipfile
from pathlib import Path
import build_v127_conversion_addon as normal
from verify_v1271_final_readiness import ROOT,ordinary,read,sha,write

NAME='dreamwalker-v127-full-conversion-author-resume-probe'

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--production-jar',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--worldedit-api-jar',type=Path,required=True)
    p.add_argument('--offline',action='store_true');p.add_argument('--prepare-only',action='store_true');a=p.parse_args()
    jar=ordinary(a.production_jar);digest=sha(jar);output=ordinary(a.output)
    assert output.is_relative_to(ROOT/'build') and not output.exists()
    previous=sys.argv
    try:
        sys.argv=['build_v127_conversion_addon.py','--production-jar',str(jar),'--output',str(output),'--prepare-only']
        normal.main()
    finally:sys.argv=previous
    metadata=read(output/'resources/fabric.mod.json')
    metadata['name']='Dreamwalker bounded native converter resume QA'
    metadata['entrypoints']['main'].append('dev.dreamwalker.bloodbornedw.conversionqa.ConversionResumeProbe')
    write(output/'resources/fabric.mod.json',metadata)
    init=output/'v127conversion.init.gradle';text=init.read_text(encoding='utf8')
    source=(ROOT/'tools/conversion-resume-v1271/java').as_posix().replace("'","\\'")
    assert "java.srcDirs=['src/conversion/java']" in text
    text=text.replace("java.srcDirs=['src/conversion/java']","java.srcDirs=['src/conversion/java','"+source+"']")
    text=text.replace("archiveBaseName='dreamwalker-v127-full-conversion-author'","archiveBaseName='"+NAME+"'")
    init.write_text(text,encoding='utf8')
    addon=ROOT/('build/libs/'+NAME+'-1.2.7.jar')
    report={'status':'PASS_RESUME_PROBE_PREPARED_NOT_COMPILED','productionJarSha256':digest,
        'initScript':str(init),'addon':str(addon),'productionJarUnchanged':True,'worldConversionAttempted':False,
        'probeOnly':True,'probeRequiresMarker':'dw-conversion-resume-probe.json'}
    if not a.prepare_only:
        java=os.environ.get('JAVA_HOME17') or os.environ.get('JAVA_HOME');assert java,'Set explicit Java17 before the root-controlled build'
        api=ordinary(a.worldedit_api_jar);assert api.is_file()
        command=[str(ROOT/'gradlew.bat'),'--no-daemon','-Pworldedit_api_jar='+str(api),'-I',str(init),'remapV127ConversionJar']
        if a.offline:command.insert(1,'--offline')
        with (output/'build.log').open('wb') as log:code=subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,env=dict(os.environ,JAVA_HOME=java)).returncode
        assert code==0 and addon.is_file() and sha(jar)==digest
        with zipfile.ZipFile(addon) as z:
            assert z.testzip() is None and json.loads(z.read('fabric.mod.json'))==metadata
            assert json.loads(z.read('dw-conversion-binding.json'))['productionJarSha256']==digest
        report.update(status='PASS_RESUME_PROBE_AUTHOR_BUILD',addonSha256=sha(addon),command=command)
    write(output/'build-report.json',report)

if __name__=='__main__':
    try:main()
    except BaseException:write(ROOT/'build/final-city-fixes/converter-resume-probe-build-error.json',{'error':traceback.format_exc()});raise
