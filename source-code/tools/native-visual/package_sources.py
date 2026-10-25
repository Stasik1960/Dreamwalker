"""Package complete effective sources/resources, without caches or unused markers."""
import argparse,datetime,fnmatch,hashlib,json,traceback,zipfile
from pathlib import Path
P=Path(__file__).resolve().parents[2];T=P.parent.parent/'DW-visual-physics-20261024'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as stream:
        for data in iter(lambda:stream.read(1<<20),b''):h.update(data)
    return h.hexdigest()
def main():
    a=argparse.ArgumentParser();a.add_argument('--out',type=Path,required=True);a.add_argument('--production',type=Path,required=True);a=a.parse_args()
    assert not a.out.exists();assert a.production.is_file()
    files={}
    for folder in ('src','tools','gradle'):
        for path in (P/folder).rglob('*'):
            if not path.is_file() or path.is_symlink():continue
            rel=path.relative_to(P).as_posix()
            if any(part in ('__pycache__','.gradle','node_modules','build','build-native-visual') for part in path.relative_to(P).parts):continue
            if path.suffix in ('.pyc','.log','.hprof','.jfr','.zip','.mca'):continue
            if folder=='tools' and path.suffix not in ('.py','.ps1','.txt','.json','.md','.yml','.yaml','.bat','.sh','.csv','.java','.gradle','.properties'):continue
            # The Gradle resource task excludes these old files as well. Their
            # complete replacements are retained under src/nativevisual.
            if any(fnmatch.fnmatchcase(rel,'src/architecture/resources/assets/bloodborne_dw/'+mask) for mask in ('blockstates/native_grid_*.json','models/block/native_grid_*.json','models/item/native_grid_*.json')):continue
            files[rel]=path
    for name in ('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','.gitignore','.gitattributes','LICENSE','README.md'):
        if (P/name).is_file():files[name]=P/name
    newer=[name for name,path in files.items() if name.startswith('src/') and name.endswith('.java') and path.stat().st_mtime>a.production.stat().st_mtime+1]
    assert not newer,'Java changed after production build: '+repr(newer[:10])
    # The recorder used for accepted runs has the newer106-view/epoch-window
    # extension. Keep its exact source separately from the older QA source set.
    recorder=P/'validation/frozen-recorder/routeqa/java/dev/dreamwalker/bloodbornedw/routeqa/RouteClient.java'
    if not recorder.is_file():recorder=P.parent.parent/'DW-native-grid-final-freeze-20261024/userpack-recorder-candidate/src/routeqa/java/dev/dreamwalker/bloodbornedw/routeqa/RouteClient.java'
    assert recorder.is_file()
    for path in (P/'src/routeqa').rglob('*.java'):files['validation/frozen-recorder/'+path.relative_to(P/'src').as_posix()]=path
    files['validation/frozen-recorder/routeqa/java/dev/dreamwalker/bloodbornedw/routeqa/RouteClient.java']=recorder
    digests={name:sha(path) for name,path in sorted(files.items())}
    manifest={'status':'FULL_EFFECTIVE_SOURCE_RESOURCES_AND_MIGRATION_TOOLS','utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'productionSha256':sha(a.production),'files':digests,'excluded':'Build/cache files and the old native_grid marker JSON excluded by Gradle. Generated whole-role/physics markers and all effective assets are present.'}
    a.out.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.out,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name,path in sorted(files.items()):z.write(path,'source/'+name)
        z.writestr('SOURCE-MANIFEST.json',json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
        z.writestr('BUILD-RU.txt','Полные рабочие исходники находятся в source/. Нужны Java17 и сеть для первой загрузки Gradle/Maven.\nСборка: gradlew.bat --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1G remapJar\nЕсли недоступен Maven WorldEdit, добавьте -Pworldedit_api_jar=полный_путь_к_вашему_WorldEdit7.2.15.jar.\nГотовые ресурсы нового формата уже включены; compile_layout.py и конвертер мира для обычной сборки запускать не нужно.\nФормат и миграция описаны в source/tools/native-visual/FORMAT-RU.md. Готовую поставленную карту заново не конвертировать.\n')
    with zipfile.ZipFile(a.out) as z:
        assert z.testzip() is None
        for name,digest in digests.items():
            h=hashlib.sha256()
            with z.open('source/'+name) as stream:
                for data in iter(lambda:stream.read(1<<20),b''):h.update(data)
            assert h.hexdigest()==digest,name
    a.out.with_suffix('.json').write_text(json.dumps({'status':manifest['status'],'archive':str(a.out),'sha256':sha(a.out),'bytes':a.out.stat().st_size,'files':len(files),'productionSha256':manifest['productionSha256']},indent=2)+'\n',encoding='utf-8')
if __name__=='__main__':
    try:main()
    except BaseException:
        (T/'source-package-error.txt').write_text(traceback.format_exc(),encoding='utf-8');raise
