"""Package existing build/map evidence only; does not run tests or Minecraft."""
import argparse
import gzip
import hashlib
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
VERSION='2.1.0-rc.4'
OUT=ROOT/'releases/Bloodborne-Blocks'/VERSION
EVIDENCE=OUT/'evidence'
BUILD=ROOT/'build/whole-models'


def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def prepare():
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    for parent,name in (('libs',f'bloodborne-blocks-{VERSION}.jar'),('devlibs',f'bloodborne-blocks-{VERSION}-sources.jar')):
        target=OUT/name
        if target.exists():raise ValueError('delivery file already exists: '+str(target))
        shutil.copy2(ROOT/'build'/parent/name,target)
    world=BUILD/'approximate-city-v3'
    with zipfile.ZipFile(OUT/f'Bloodborne-City-{VERSION}.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path in sorted(world.rglob('*')):
            if path.is_file():
                item=zipfile.ZipInfo(f'Bloodborne-City-{VERSION}/'+path.relative_to(world).as_posix(),(1980,1,1,0,0,0))
                item.compress_type=zipfile.ZIP_DEFLATED
                z.writestr(item,path.read_bytes())
    for name in ('approximate-v3.json','approximate-verification-v3.json','approximate-v3-repeat.json',
                 'final-assemble.log','full-extended-check-2.log','production-tests.log','runtime-closure.log'):
        (EVIDENCE/(name+'.gz')).write_bytes(gzip.compress((BUILD/name).read_bytes(),mtime=0))
    shot=ROOT/'run/screenshots/2026-09-28_23.49.24.png'
    if shot.is_file():shutil.copy2(shot,EVIDENCE/'city-before.png')
    print('Packaged JAR, source JAR, full city and prior evidence; no tests executed')


def manifest():
    names={'jar':f'bloodborne-blocks-{VERSION}.jar','sourcesJar':f'bloodborne-blocks-{VERSION}-sources.jar',
           'world':f'Bloodborne-City-{VERSION}.zip','artistKit':f'Bloodborne-Artist-Kit-{VERSION}.zip',
           'conversion':'evidence/approximate-v3.json.gz','independent':'evidence/approximate-verification-v3.json.gz',
           'repeat':'evidence/approximate-v3-repeat.json.gz'}
    data={'version':VERSION,'scope':'user-approved-approximate-known-census','releaseReady':False,
          'sourceCommit':'2a5fcaa01a128ba35294cbc6003b37288482e96e',
          'sourceWorldSha256':'111253971789feea9c016c693c4122a7faee6952c11102c092d73f3475df6150',
          'worldTreeSha256':'bfc87ba8e82b899257e2475ed1722b81e1e3c034686acfd619e31d19e1a81c80',
          'assembly':'PASS: Java 17, Gradle assemble; no check or game invocation',
          'finalChecks':'cancelled at explicit user request','fullModpack':'unknown; not verified',
          'knownCensus':{'candidates':34233,'unresolved':0},'independentWorldIntegrity':'PASS',
          'preservation':'PASS','repeat':'PASS: zero transactions, byte-identical',
          'mergedToMain':False}
    for key,name in names.items():
        path=OUT/name
        data[key]={'path':path.relative_to(ROOT).as_posix(),'sha256':sha(path),'bytes':path.stat().st_size}
    (OUT/'delivery.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(data,ensure_ascii=False))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--manifest',action='store_true')
    if parser.parse_args().manifest:manifest()
    else:prepare()
