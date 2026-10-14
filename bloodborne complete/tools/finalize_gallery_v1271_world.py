"""Reuse reviewed gallery navigation and describe current source-city repairs."""
import argparse, json, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
path=ROOT/'tools/finalize_gallery_v127_world.py'
source=path.read_text(encoding='utf8').replace('dreamwalker-flat-gallery-v127','dreamwalker-flat-gallery-v1271').replace('dw_gallery_v127_entered','dw_gallery_v1271_entered').replace('V12.7','V12.7.1').replace('V12.7-Compact','V12.7.1-Compact')
namespace={'__file__':__file__,'__name__':'v1271_navigation_adapter'}
exec(compile(source,str(path),'exec'),namespace)

def readme_text(m,navigation):
    city=m['city'];preparation=m['galleryPreparation'];summary=m['catalogue']['summary']
    parts=[
        'Dreamwalker BB v12.7.1 — галерея и городской фрагмент',
        'Minecraft 1.20.1, Java17, Fabric Loader0.16.10, Fabric API0.92.9+1.20.1, GeckoLib4.4.9. Для открытия мира нужен только мод и его обычные зависимости. WorldEdit необязателен; исходный ресурспак и QA addon не нужны.',
        'Производственный JAR SHA-256: '+m['productionJarSha256'],
        navigation,
        f"Основных платформ: {len(m['exhibits'])}. В каталоге сохранены оригинальные V12,539 новых архитектурных типов и два служебных блока. Скрытые исходные стили/фрагменты используются только для точной совместимости города.",
        'Дверь90923 по умолчанию использует исходный монтаж2: верхняя арка отнесена назад. Старый прямой монтаж1 сохранён как совместимое состояние и показан отдельным образцом. Закрытые и открытые двери расположены в секции doors.',
        f"Городской образец использует уже подготовленные данные v12.7 для {preparation['reusedCityOwners']} прежних корней; положения исходных ячеек сохранены. {preparation['restoredDrawableCityCells']} ранее пропущенных исходных ячеек восстановлены точными скрытыми моделями, {preparation['nativeSourceCarrierCells']} исходных носителей сохраняют нативный вид, физику и свет. Часть прежних owner-блоков заменена точными нативными состояниями без block entity. Исходные архивы и их взвешенный выбор моделей не пересозданы. Границы фрагмента обрезают продолжение города.",
        'Исходные тёмно-серые текстуры сохранены. Ночная темнота соответствует освещению мира. Этот мир служит образцом; результаты нативной проверки и ограничения приведены в файлах Verification финального релиза.',
        'ALT — предусмотренный и сохраняемый режим переключения. Сейчас ни одного отдельного ALT-вида не загружено: BASE и ALT выглядят одинаково. /bb alt <радиус> и /bb base <радиус>, допустимы1–300 блоков; /bb cancel и /bb status. /bb select <радиус> выбирает область для инструмента; /bb none возвращает один объект.',
        '/function dw_gallery:alt и :base показывают кликабельную команду для игрока Creative. Нажмите сообщение или введите /bb вручную: этот инструмент требует игрока и не вызывается напрямую из серверной datapack-функции.',
        'DW-барьер93230 виден жёлтым значком при удержании предмета; блокирует только игроков. DW-свет93231 невидим, проходим и излучает свет14. /function dw_gallery:barrier и :light выдают предметы и переносят к образцам. :day / :night меняют время.',
        'Служебные невидимые исходные носители города не добавлены в Creative. Не заменяйте их DW-барьером: их исходная физика отличается от барьера только для игроков.',
    ]
    return '\n\n'.join(parts)+'\n'

def main():
    namespace['readme_text']=readme_text
    namespace['main']()
    world=Path(sys.argv[sys.argv.index('--world')+1]).resolve()
    manifest_path=Path(sys.argv[sys.argv.index('--manifest')+1])
    m=json.loads(manifest_path.read_text(encoding='utf-8-sig'))
    functions=world/'datapacks/dw_gallery_entry/data/dw_gallery/functions'
    for name,number in (('barrier','93230'),('light','93231')):
        row=next(r for r in m['exhibits'] if r['id']==number)
        (functions/(name+'.mcfunction')).write_text('function '+row['visitFunction']+'\ngive @s '+row['registry']+'\n',encoding='utf8')
    (functions/'day.mcfunction').write_text('time set 6000\n',encoding='utf8')
    (functions/'night.mcfunction').write_text('time set 13000\n',encoding='utf8')
    for name,command in (('alt','bb alt 300'),('base','bb base 300')):
        message={'text':'Нажмите для /'+command+' (сейчас ALT = BASE, отдельного ALT-вида нет).','color':'gold',
                 'clickEvent':{'action':'run_command','value':'/'+command},
                 'hoverEvent':{'action':'show_text','contents':{'text':'Команда выполняется от игрока Creative; радиус ограничен 300 блоками.'}}}
        (functions/(name+'.mcfunction')).write_text('tellraw @s '+json.dumps(message,ensure_ascii=False)+'\n',encoding='utf8')
    helpfile=functions/'help.mcfunction'
    helpfile.write_text(helpfile.read_text(encoding='utf8')+'tellraw @s '+json.dumps({'text':'ALT сейчас показывает BASE: /bb alt <1–300>, /bb base <1–300>, /bb cancel, /bb status. Служебные образцы: :barrier / :light; время: :day / :night.','color':'gold'},ensure_ascii=False)+'\n',encoding='utf8')
    assert 'b98c752b' not in (world/'README-RU.txt').read_text(encoding='utf-8-sig')

if __name__=='__main__':main()
