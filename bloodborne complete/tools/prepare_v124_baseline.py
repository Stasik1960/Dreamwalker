"""Freeze V12.3 inputs before any V12.4 content mutation; ordinary files only."""
from pathlib import Path
import hashlib,json,subprocess,io,zipfile
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/v12.4-dedup'
BASE='27bf67e63da6f8ba0a9dee2d1587310782c874e2'
ARCHIVE=None
def git(path):
    return ARCHIVE.read('bloodborne complete/'+path)
def main():
    global ARCHIVE
    ARCHIVE=zipfile.ZipFile(io.BytesIO(subprocess.check_output(['git','archive','--format=zip',BASE,'bloodborne complete/src'],cwd=ROOT.parent)))
    OUT.mkdir(parents=True,exist_ok=True)
    remaining=json.loads(git('src/architecture/resources/bloodborne_dw/remaining-catalogue.json'))
    descriptors={r['path']:json.loads(git('src/architecture/resources/bloodborne_dw/composite/'+r['path']+'.json')) for r in remaining['objects']}
    catalogue=json.loads(git('src/architecture/resources/bloodborne_dw/debug_catalogue.json'))
    rows=[r for r in catalogue['entries'] if not r['registryId'].split(':')[-1].startswith('source_')]
    document={'baselineCommit':BASE,'registeredNewTypes':len(descriptors),'offeredNewStates':sum(len(r['variants']) for r in remaining['objects']),'originalRows':rows,'remainingCatalogue':remaining,'debugCatalogue':catalogue,'descriptors':descriptors}
    (OUT/'baseline-content.json').write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    protected={}
    files=[p for p in ARCHIVE.namelist() if not p.endswith('/')]
    for p in files:
        rel=p.removeprefix('bloodborne complete/')
        if '/resources/' in rel and ('/composite/source_' in rel or '/models/remaining/' in rel or '/textures/remaining/' in rel or '/models/item/source_' in rel or rel.endswith(('remaining-catalogue.json','debug_catalogue.json'))):continue
        protected[rel]=hashlib.sha256(git(rel)).hexdigest()
    (OUT/'baseline-protected-hashes.json').write_text(json.dumps({'baselineCommit':BASE,'files':protected},indent=2)+'\n',encoding='utf8')
    (OUT/'WORKLOG.txt').write_text('V12.4: объединение новых типов и новая галерея\nИсходный commit: '+BASE+'\nОригинальная V12: 821de077917f696a13600e900a244ffc05230c2f\nРабочее хранилище C:, перенос E: остаётся на паузе.\nПорядок: анализ игрового вида → реализация → промежуточный commit → combined JAR → игровые тесты → новая галерея и повторное открытие → итоговые артефакты.\nИсходная карта и прежние галереи не изменяются.\n1045 новых регистраций, 1802 предлагаемых варианта до объединения.\nАвторский экспорт коллизий не предоставлен и не применялся.\n',encoding='utf8')
if __name__=='__main__':main()
