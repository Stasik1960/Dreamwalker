import copy,json,shutil,subprocess,sys,traceback
from pathlib import Path
PROJECT=Path(__file__).absolute().parents[2];ROOT=PROJECT.parent
sys.path.insert(0,str(Path(__file__).parent))
from dedicated import OUT,RUN,WORLD,JAVA,ordinary,sha,write
sys.path.insert(0,str(PROJECT/'tools/city-streaming-20261018'))
from world_io import *

def main():
    out=OUT/'reopen';out.mkdir(exist_ok=False)
    after=json.loads((OUT/'after/prepared.json').read_text());assert after['status']=='PASS_NATIVE_DEDICATED_ROUTE'
    probes=[];inputs=[]
    for x,y,z in [(253,66,-915),(603,70,-100),(940,28,129),(284,118,-649),(290,137,-656)]:
        region=WORLD/'region'/f'r.{x>>9}.{z>>9}.mca';inputs.append({'path':str(region),'sha256':sha(region)})
        chunk=RegionFile.open(region).get_chunk((x>>4)&31,(z>>4)&31).nbt().root.value
        section=next(s for s in chunk['sections'].value if s.value['Y'].value==y>>4)
        palette,indices=section_blocks(section);state=palette[indices[((y&15)*16+(z&15))*16+(x&15)]]
        entity=next((b for b in chunk.get('block_entities',Tag(TAG_LIST,[],TAG_COMPOUND)).value if [b.value[k].value for k in ['x','y','z']]==[x,y,z]),None)
        probes.append(Tag(TAG_COMPOUND,{'pos':Tag(TAG_INT_ARRAY,[x,y,z]),'state':copy.deepcopy(state),'hasEntity':Tag(TAG_BYTE,entity is not None),'entity':copy.deepcopy(entity) if entity else Tag(TAG_COMPOUND,{})}))
    expected=out/'expected.nbt';write_nbt(expected,NbtFile('',Tag(TAG_COMPOUND,{'probes':Tag(TAG_LIST,probes,TAG_COMPOUND)})))
    write(out/'saved-inputs.json',{'status':'READ_FROM_AFTER_NORMALLY_SAVED_WORKING_MAP_NO_RESET','workingMap':str(WORLD),'regions':inputs,'expectedSha256':sha(expected)})
    for p in ordinary(RUN/'mods').glob('dreamwalker-streaming-qa.jar'):ordinary(p).unlink()
    addon=next(p for p in (PROJECT/'build/libs').glob('dreamwalker-reopen-qa-*.jar') if not p.name.endswith('-dev.jar'))
    shutil.copyfile(addon,RUN/'mods'/addon.name)
    args=[s for s in after['args'] if not s.startswith('-Ddw.qa.output=')];args.insert(0,'-Ddw.reopen.output='+str(out))
    argfile=RUN/'reopen.args';argfile.write_text('\n'.join('"'+s.replace('\\','/').replace('"','\\"')+'"' for s in args)+'\n')
    with (out/'console.log').open('w') as log:
        child=subprocess.run([str(JAVA),'@'+str(argfile)],cwd=RUN,stdout=log,stderr=subprocess.STDOUT,timeout=240,creationflags=subprocess.CREATE_NO_WINDOW)
    write(out/'receipt.json',{'exitCode':child.returncode,'productionJarSha256':after['jarSha256'],'addonSha256':sha(addon),'workingMap':str(WORLD)})
    assert child.returncode==0 and json.loads((out/'reopen.json').read_text())['status']=='PASS_NEW_PROCESS_SAVED_MAP_REOPEN'

if __name__=='__main__':
    try:main()
    except BaseException:
        (OUT/'reopen-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
