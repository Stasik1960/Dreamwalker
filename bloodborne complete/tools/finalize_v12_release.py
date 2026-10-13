"""Stage compact native V12 evidence; --apply updates only its release acceptance files.

Run through CodexControl/Guard.ps1 -Mode Operation after the root's final report.
This never launches Minecraft, rebuilds a JAR or changes the closed gallery ZIP.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import zipfile
from PIL import Image
from run_v11_server import ROOT, ordinary, require

RELEASE=ROOT.parent/'releases/Dreamwalker-BB/v12'
JAR='dreamwalker-bb-fabric-1.20.1-0.1.0-v12.jar'
WORLD='Dreamwalker-BB-v12-Gallery.zip'
EXPECTED={'G1',*[f'T{i}' for i in range(1,13)],*[f'C{i}' for i in range(1,5)],'P1','P2','D1','D2','L1'}

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def read(path):return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def write(path,value):Path(path).write_bytes((json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode('utf8'))

def build_file(path):
    path=Path(path)
    if not path.is_absolute():path=ROOT/path
    require('..' not in path.parts and path.is_relative_to(ROOT/'build'),'Source must be in project build: '+str(path))
    for parent in reversed([path,*path.parents]):
        if parent.is_relative_to(ROOT/'build'):ordinary(parent)
    require(path.is_file(),'Missing evidence file: '+str(path))
    return path

def index_references(native,bound):
    references=native.get('evidence',[])
    require(7<=len(references)<=16,'Native report needs its hash-bound state/launch evidence')
    sources=[build_file(row['path']) for row in references]
    require(len(set(sources))==len(sources),'Duplicate native evidence source')
    indexed=[];names=set();total=0
    for reference,path in zip(references,sources):
        require(path.suffix.lower()=='.json' or path.name=='launch-console.log','Only native JSON and closed-server console evidence may be bundled')
        expected=reference.get('sha256','')
        require(re.fullmatch('[0-9a-f]{64}',expected) and sha(path)==expected,'Native evidence SHA differs: '+path.name)
        name=path.name if sum(other.name==path.name for other in sources)==1 else path.parent.name+'-'+path.name
        require(name not in names,'Native evidence archive name collision: '+name);names.add(name)
        if path.name=='launch-console.log':
            require(path.parent.name.startswith('runtime-server-v12-'),'Only isolated V12 server console logs are accepted')
            profiles=[read(source) for source in sources if source.suffix.lower()=='.json']
            require(any(profile.get('schema')=='dreamwalker-v11-server-v1' and profile.get('status')=='PASS'
                and profile.get('exit_code')==0 and profile.get('artifact_sha256')==bound
                and profile.get('console_sha256')==expected and Path(profile.get('evidence',''))==path
                for profile in profiles),'Console log needs its closed PASS/exit0 server profile: '+str(path))
        size=path.stat().st_size;total+=size
        row={key:value for key,value in reference.items() if key!='path'}
        row.update(path='evidence/'+name,sourceRelativePath=path.relative_to(ROOT).as_posix(),bytes=size)
        indexed.append(row)
    require(total<=16*1024*1024,'Native JSON/console evidence exceeds16MiB compact budget')
    return indexed,sources

def normalized_qa(previous):
    with zipfile.ZipFile(RELEASE/JAR) as archive:metadata=json.loads(archive.read('fabric.mod.json'))
    require(metadata.get('id')=='bloodborne_dw' and metadata.get('version')=='0.1.0-v12','Release JAR Fabric metadata differs')
    actual=previous['serverGameTests'];core=previous['transactionCoreChecks']
    require(isinstance(core,int) and core>0 and all(isinstance(actual[key],int) and actual[key]>=0
        for key in ('tests','failures','errors','skipped')),'Invalid existing build evidence counters')
    passed=actual['tests']-actual['failures']-actual['errors']-actual['skipped']
    require(passed>0 and not(actual['failures'] or actual['errors'] or actual['skipped']),'Existing build evidence must be complete PASS')
    return {'revision':'V12','gameTests':{'total':actual['tests'],'passed':passed,'failed':actual['failures'],
        'errors':actual['errors'],'skipped':actual['skipped'],'xml':'GAME-TESTS.xml','xmlSha256':previous['gameTestsXmlSha256']},
        'transactionChecks':{'passed':core,'buildResultSha256':previous['buildResultSha256']},
        'productionJar':{'file':JAR,'sha256':sha(RELEASE/JAR),'bytes':(RELEASE/JAR).stat().st_size,
            'fabricMetadataId':metadata['id'],'fabricMetadataVersion':metadata['version']}}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True,help='Fresh staging folder under project build')
    parser.add_argument('--frame-label',action='append',default=[],help='Exact native frame label; repeat for explicit selection')
    parser.add_argument('--max-frames',type=int,default=20,choices=range(1,25))
    parser.add_argument('--apply',action='store_true',help='After every gate passes, add evidence and update V12 release docs')
    args=parser.parse_args();report=build_file(args.report);report_sha=sha(report);native=read(report)
    output=args.output if args.output.is_absolute() else ROOT/args.output
    require('..' not in output.parts and output.is_relative_to(ROOT/'build') and not output.exists(),'Fresh project-build output required')
    for parent in reversed(list(output.parents)):
        if parent.is_relative_to(ROOT/'build') and parent.exists():ordinary(parent)
    require(native.get('schema')=='dreamwalker-v12-native-acceptance-v1','Wrong native report schema')
    status=native.get('status','')
    require(status in ('PASS','COMPLETE') or status.startswith('PASS_'),'Native report is not final: '+status)
    cases=native.get('cases',[])
    require(len(cases)==22 and {c['case'] for c in cases}==EXPECTED,'Exactly the 22 agreed case IDs are required')
    require(all(c.get('status')=='PASS' and str(c.get('evidence','')).strip() for c in cases),'Every native case needs actual PASS evidence')
    for path in (RELEASE,RELEASE/'QA.json',RELEASE/JAR,RELEASE/WORLD,RELEASE/'gallery-manifest.json'):ordinary(path)
    previous=read(RELEASE/'QA.json');gallery=read(RELEASE/'gallery-manifest.json')
    readme=(RELEASE/'README.md').read_text(encoding='utf-8-sig')
    pending='Игровая приёмка новых GUI, режимов и связок пока PENDING.'
    require(not args.apply or pending in readme,'Expected preliminary README paragraph missing')
    bound=sha(RELEASE/JAR);world_sha=sha(RELEASE/WORLD)
    require(bound==native.get('productionJarSha256')==previous['productionJarSha256']==gallery['productionJarSha256'],'Native/JAR/gallery SHA binding differs')
    require(world_sha==previous['worldZipSha256'],'Closed gallery ZIP changed')
    require(gallery['productionReopen']['verifiedExhibits']==121 and previous['serverGameTests']['failures']==previous['serverGameTests']['errors']==0,'Existing gallery/build evidence must pass')
    normalized=normalized_qa(previous)
    require(not any((RELEASE/name).exists() for name in ('QA-EVIDENCE.zip','NATIVE-ACCEPTANCE.json','NATIVE-FRAMES.json','NATIVE-EVIDENCE.json')),'Final native evidence already exists; preserve it')
    references,reference_sources=index_references(native,bound)
    frames=native.get('frames',[]);require(frames and len({f['label'] for f in frames})==len(frames),'Native frame labels must be present and unique')
    labels=args.frame_label
    require(len(labels)==len(set(labels)) and len(labels)<=24,'Explicit selection must use at most24 unique labels')
    if labels:
        require(set(labels)<={f['label'] for f in frames},'Unknown selected frame label')
        selected={f['label'] for f in frames if f['label'] in labels}
    else:
        count=min(args.max_frames,len(frames))
        indices=[round(i*(len(frames)-1)/(count-1)) for i in range(count)] if count>1 else [0]
        selected={frames[i]['label'] for i in indices}
    indexed=[];selected_bytes=0
    for index,frame in enumerate(frames,1):
        path=build_file(frame['path'])
        require(path.parent.name=='screenshots' and path.parent.parent.name.startswith('runtime-client-v12-'),'Only actual isolated V12 Minecraft screenshots are accepted')
        with Image.open(path) as pixels:
            require(pixels.format=='PNG' and pixels.width>=64 and pixels.height>=64,'Invalid native PNG frame')
            dimensions=list(pixels.size);pixels.load()
        chosen=frame['label'] in selected
        name=f'{index:03d}-'+re.sub('[^A-Za-z0-9_-]','_',frame['label'])[:64]+'.png'
        row={key:value for key,value in frame.items() if key!='path'}
        row.update(sourceRelativePath=path.relative_to(ROOT).as_posix(),sha256=sha(path),bytes=path.stat().st_size,
                   dimensions=dimensions,selected=chosen,evidencePath='native/'+name if chosen else None)
        indexed.append(row)
        if chosen:selected_bytes+=row['bytes']
    require(selected_bytes<=16*1024*1024,'Select fewer --frame-label entries; compact native PNG budget is16MiB')
    require(sha(report)==report_sha,'Native report changed during validation')
    output.mkdir(parents=True);(output/'native').mkdir();(output/'evidence').mkdir()
    for row,source in zip(references,reference_sources):
        destination=output/row['path'];shutil.copyfile(source,destination)
        require(sha(source)==sha(destination)==row['sha256'],'Referenced evidence changed during copy: '+source.name)
    for frame,row in zip(frames,indexed):
        if row['selected']:
            destination=output/row['evidencePath'];shutil.copyfile(build_file(frame['path']),destination)
            require(sha(destination)==row['sha256'],'Native copy bytes differ')
    published=dict(native);published['frames']=indexed;published['evidence']=references
    published['provenance']={'originalReportRelativePath':report.relative_to(ROOT).as_posix(),'originalReportSha256':report_sha,
       'productionJarSha256':bound,'closedGalleryZipSha256':world_sha,'nativePngIntegrityVerified':len(frames),
       'selectedNativePngs':len(selected),'bundledEvidenceReferences':len(references),
       'evidencePaths':'Relative paths resolve inside QA-EVIDENCE.zip; copied reference files retain their original hash-bound bytes.',
       'selection':'Explicit frame labels' if labels else 'Evenly spaced chronological report frames',
       'scope':'Root-reported22 native functional cases; PNG integrity is verified separately. This is not continuous video, FPS/MSPT or AI/combat measurement.'}
    write(output/'NATIVE-ACCEPTANCE.json',published)
    write(output/'NATIVE-FRAMES.json',{'schema':'dreamwalker-v12-native-frame-index-v1','productionJarSha256':bound,'frames':indexed})
    write(output/'NATIVE-EVIDENCE.json',{'schema':'dreamwalker-v12-native-evidence-index-v1','productionJarSha256':bound,'evidence':references})
    write(output/'gallery-summary.json',{'productionJarSha256':bound,'closedGalleryZipSha256':world_sha,
       'exhibits':121,'items':103,'npcs':18,'productionReopen':gallery['productionReopen']})
    bundle=output/'QA-EVIDENCE.zip'
    with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for path in sorted(output.rglob('*')):
            if path.is_file() and path!=bundle:archive.write(path,path.relative_to(output).as_posix())
    with zipfile.ZipFile(bundle) as archive:
        require(archive.testzip() is None,'Evidence ZIP CRC failure')
        for name in archive.namelist():require(hashlib.sha256(archive.read(name)).hexdigest()==sha(output/name),'Packaged evidence bytes differ')
    digest=sha(bundle);(output/'QA-EVIDENCE.zip.sha256').write_bytes((digest+'  QA-EVIDENCE.zip\n').encode('utf8'))
    require(sha(report)==report_sha and sha(RELEASE/JAR)==bound and sha(RELEASE/WORLD)==world_sha,'Inputs changed before finalization')
    require(all(sha(source)==row['sha256'] for row,source in zip(references,reference_sources)),'Referenced evidence changed before finalization')
    if args.apply:
        for name in ('QA-EVIDENCE.zip','QA-EVIDENCE.zip.sha256','NATIVE-ACCEPTANCE.json','NATIVE-FRAMES.json','NATIVE-EVIDENCE.json'):
            shutil.copyfile(output/name,RELEASE/name);require(sha(output/name)==sha(RELEASE/name),'Release evidence copy differs')
        previous.update(status='PASS_BUILD_GALLERY_AND_22_NATIVE_CASES',nativeAcceptanceCases=cases,nativeClientActionsRun=True,
          nativeAcceptance={'status':'PASS_22_NATIVE_CASES','report':'NATIVE-ACCEPTANCE.json','originalReportSha256':report_sha,
             'publishedReportSha256':sha(RELEASE/'NATIVE-ACCEPTANCE.json'),'nativeFrames':len(frames),'selectedNativeFrames':len(selected),
             'bundledEvidenceReferences':len(references),'evidenceIndex':'NATIVE-EVIDENCE.json',
             'evidenceArchive':'QA-EVIDENCE.zip','evidenceArchiveSha256':digest,'evidenceArchiveBytes':bundle.stat().st_size,
             'scope':published['provenance']['scope'],'closedGallerySnapshotUnchanged':True})
        previous.update(normalized)
        write(RELEASE/'QA.json',previous)
        readme=readme.split(pending,1)[0]+(
          'Все22 новых функциональных случая выполнены настоящими клиентами на этом JAR: режимы\n'
          'инструмента, собаки, ALL-связки, пара фонарей, BASE/ALT и радиус, GUI команд,\n'
          'личные/общие лимиты, удаление, контур90006 и сохранение/повторный вход.\n'
          'Точные действия и пределы доказательств: [NATIVE-ACCEPTANCE.json](NATIVE-ACCEPTANCE.json).\n'
          f'Проверена целостность {len(frames)} нативных PNG; {len(selected)} выбранных кадров включены в\n'
          '[QA-EVIDENCE.zip](QA-EVIDENCE.zip).\n'
          'Ссылки evidence/ и native/ в итоговом отчёте разрешаются внутри этого архива;\n'
          'хеши сохранённых состояний и закрытых запусков: [NATIVE-EVIDENCE.json](NATIVE-EVIDENCE.json).\n'
          'Это функциональный проход; FPS/MSPT, AI/бой и непрерывная видеозапись\n'
          'не заявляются. Исходный закрытый ZIP галереи\n'
          'сохраняет предварительный встроенный статус; итоговая приёмка записана во внешних файлах.\n')
        (RELEASE/'README.md').write_bytes(readme.encode('utf8'))
        tests=normalized['gameTests'];core=normalized['transactionChecks']['passed']
        summary=f'# V12: итоговая приёмка\n\nPASS: сборка{tests["passed"]}/{tests["total"]} серверных GameTests и{core}/{core} проверка транзакций; новая галерея121;\n'
        summary+='ровно одна рамка bb_tool; collision+2/gap2; неизменные UUID/позиции/настройки после\nproduction-only сохранения и повторного открытия.\n\n'
        summary+='PASS: все22 новые native случая. Действия настоящего клиента и доказательства:\n[NATIVE-ACCEPTANCE.json](NATIVE-ACCEPTANCE.json), [QA-EVIDENCE.zip](QA-EVIDENCE.zip).\n\n'
        summary+='| Случай | Статус | Проверено |\n| --- | --- | --- |\n'
        for case in cases:summary+='| '+case['case']+' | PASS | '+case['name'].replace('|','/')+' |\n'
        summary+='\nНативные кадры — дискретные снимки. FPS/MSPT, AI/бой и полный художественный\nпросмотр всех121 экспонатов этим проходом не заявляются. Закрытый ZIP и старые релизы\nне менялись; встроенный статус галереи предварительный, эти внешние файлы итоговые.\n'
        (RELEASE/'_ACCEPTANCE.md').write_bytes(summary.encode('utf8'))
    require(sha(RELEASE/JAR)==bound and sha(RELEASE/WORLD)==world_sha,'Closed artifacts changed')
    result={'status':'APPLIED_FINAL_NATIVE_ACCEPTANCE' if args.apply else 'STAGED_FINAL_NATIVE_ACCEPTANCE',
       'productionJarSha256':bound,'closedGalleryZipSha256':world_sha,'cases':22,'nativeFrames':len(frames),
       'selectedFrames':len(selected),'bundledEvidenceReferences':len(references),'archiveSha256':digest,'archiveBytes':bundle.stat().st_size,
       'output':str(output),'releaseModified':args.apply,'sourceOrGalleryModified':False}
    write(output/'finalization-result.json',result);print(json.dumps(result,ensure_ascii=False),flush=True)

if __name__=='__main__':main()
