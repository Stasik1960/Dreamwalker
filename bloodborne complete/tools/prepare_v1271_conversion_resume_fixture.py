"""Prepare two source rows for the bounded native converter resume QA.

Reuses the existing tiny fixture generator (level.dat only). No original city,
prepared full-world SQL or Java is opened. The root runs the native author.
"""
import argparse,gzip,json,sys,traceback,uuid
from pathlib import Path
import prepare_v127_conversion_fixture as base
from verify_v1271_final_readiness import ROOT,ordinary,read,sha,write

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--level-template',type=Path,required=True)
    p.add_argument('--production-jar',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    output=ordinary(a.output);previous=sys.argv
    try:
        sys.argv=['prepare_v127_conversion_fixture.py','--level-template',str(a.level_template),'--production-jar',str(a.production_jar),'--output',str(output)]
        base.main()
    finally:sys.argv=previous
    uid=lambda text:str(uuid.uuid5(uuid.NAMESPACE_URL,'dreamwalker-v1271-resume-probe/'+text))
    alias={'dimension':'minecraft:overworld','position':[4,80,4],'registryId':'bloodborne_dw:source_bricks_bricks_0','instanceUuid':uid('alias'),
        'properties':base.props('bloodborne_dw:source_bricks_bricks_0'),
        'payloadSnbt':'{SourceShift:[0.125d,0.25d,-0.125d],MountY:0.625d,AuditTyped:{OpaqueLong:-9223372036854775808L,OpaqueByte:-1b}}'}
    bank={'dimension':'minecraft:overworld','position':[12,80,12],'registryId':'bloodborne_dw:source_compatibility_fragment','instanceUuid':uid('banked'),
        'properties':base.props('bloodborne_dw:source_compatibility_fragment'),
        'payloadSnbt':'{SourceShift:[0.0d,0.0d,0.0d],AuditTyped:{OpaqueLong:-9223372036854775808L,OpaqueByte:-1b}}'}
    bank['properties'].update(art_bank='7',variant='2',rotation='4',profile='base',open='false')
    rows=[alias,bank];plan=output/'runtime-plan'
    for name,values in [('owners.jsonl.gz',rows),('whole-objects.jsonl.gz',[]),('entities.jsonl.gz',[])]:
        with gzip.open(plan/name,'wt',encoding='utf8') as stream:
            for row in values:stream.write(json.dumps(row,separators=(',',':'))+'\n')
    manifest=read(plan/'manifest.json');manifest['runtimeFiles']={path.name:sha(path) for path in plan.glob('*.jsonl.gz')}
    manifest['boundedResumeProbeOnly']=True;write(plan/'manifest.json',manifest)
    marker={'schemaVersion':1,'fixtureOnly':True,'productionJarSha256':sha(a.production_jar),'owners':rows,
        'originalCityConverted':False,'nativeRun':'NOT_RUN'}
    write(output/'dw-conversion-resume-probe.json',marker)
    write(output/'resume-fixture-input.json',{'status':'PASS_BOUNDED_RESUME_FIXTURE_PREPARED_NATIVE_NOT_RUN',
        'world':str(output/'world'),'runtimePlan':str(plan),'probeMarker':str(output/'dw-conversion-resume-probe.json'),
        'owners':rows,'productionJarSha256':sha(a.production_jar),'originalCityConverted':False,'javaLaunched':False})

if __name__=='__main__':
    try:main()
    except BaseException:write(ROOT/'build/final-city-fixes/converter-resume-fixture-error.json',{'error':traceback.format_exc()});raise
