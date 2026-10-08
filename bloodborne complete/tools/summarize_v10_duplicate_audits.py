"""Bind independently regenerated architecture/RP/cross audits; no runtime launch."""
import argparse
import hashlib
import json
from pathlib import Path


def load(path):return json.loads(path.read_text(encoding='utf8'))
def ref(path):return {'path':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['architecture','rp','cross','freeze','resource-delta','report','short-guide']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--revision-description',default='Снимки геометрии нового кандидата изменяют только безопасное чтение рабочих потоков')
    a=p.parse_args();assert not a.report.exists() and not a.short_guide.exists(),'Preserve historical audit bytes'
    arch,rp,cross,freeze,delta=map(load,[a.architecture,a.rp,a.cross,a.freeze,a.resource_delta])
    sha=freeze['sha256'];assert arch['production']['sha256']==rp['productionJarSha256']==cross['production']['sha256']==delta['current']['sha256']==sha
    assert arch['summary']['architecturePairs']==153 and not arch['confirmedGroups'] and not arch['unconfirmed']
    assert rp['scope']['offeredPairs']==2346 and rp['scope']['allPairs']==2850 and not rp['newConfirmedGroups'] and not rp['unresolvedPairs']
    assert cross['summary']['crossPairs']==1242 and cross['summary']['newConfirmedCrossGroups']==0
    roster=arch['catalogueRoster'];canonical={r['registryId']:r['temporaryId'] for r in roster if r['offered']}
    aliases=[{'retiredTemporaryId':r['temporaryId'],'oldRegistry':r['registryId'],'canonicalRegistry':r['canonicalRegistryId'],'canonicalTemporaryId':canonical[r['canonicalRegistryId']],'numberNeverReuse':True} for r in roster if r['retiredIdReserved']]
    assert len(aliases)==7 and len(rp['confirmedGroups'])==7
    report={'schema':'dreamwalker-v10-existing-construction-catalogue-duplicate-summary-v1','status':'READ_ONLY_STATIC_ALL_EXISTING_CONSTRUCTION_TYPES_AUDITED_NO_NEW_MERGES',
        'productionJarSha256':sha,'scope':{'architectureCanonical':18,'rpCanonical':69,'canonicalConstruction':87,'registeredConstructionIncludingRpAliases':94,'temporaryTableRows':118,
            'architecturePairs':153,'rpCanonicalPairs':2346,'crossCanonicalPairs':1242,'canonicalPairsTotal':3741,'rpRegisteredPairs':2850,'oldAliasCrossPairs':126,'registeredAllPairsTotal':4371,
            'oldAliasCrossBasis':'Seven source/shared-runtime aliases reduce to their canonical cross-contract. All76 share Creative-only attack and zero RP item drops, unlike architecture Survival break/one own item.',
            'nonConstructionTableRows':'18mobs+4items+2tools roster only, not construction pair proof'},
        'confirmedAlreadyImplementedRpGroups':rp['confirmedGroups'],'removedIdToKeptIdAlreadyImplemented':aliases,'newConfirmedGroups':[],'unresolvedPairs':[],
        'preservedExceptions':[arch['preservedException']],'preservedAllArchitecture':[{'temporaryId':r['temporaryId'],'registryId':r['registryId']} for r in arch['architecture']],
        'evidence':{name:ref(path) for name,path in [('architecture',a.architecture),('rpFinal',a.rp),('cross',a.cross),('freeze',a.freeze),('unchangedArtResourceDelta',a.resource_delta)]},
        'native':{'methods':freeze['nativeTests'],'failures':freeze['nativeFailures'],'coreChecks':freeze['coreChecks'],'xml':freeze['nativeXml'],'xmlSha256':freeze['nativeXmlSha256']},
        'actualMiddleKeyAliasDiskAndCurrentRuntime':'PENDING_NEW_CURRENT_ARTIFACT_CLIENT_AND_SEPARATE_TYPED_DISK_PROOFS',
        'runtimeBoundary':'Previous candidate native/client/server successes stay under their own SHA. This current-source/JAR audit is not a re-labelled client pass. Middle key all69+7oldtypes and distinct saved alias phases are mandatory future package gates.',
        'limits':['No new type merge or world edit performed.','Every supported type contract is compared; a concrete differing art/selection/physics/function witness refutes full equivalence. Manual all-pose visual acceptance is NOT_RUN.','118 TEMP rows do not finish1121 source-model assembly catalogue, final numeric IDs or city conversion.']}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    lines=['# Краткий результат проверки дублей V10','',f'Prototype.5, SHA256 `{sha}`: проверены все **18 архитектурных и 69 канонических RP-типа**. **Новых полных групп дублей нет.** Повторные экземпляры на карте сохраняют собственные UUID.','',
        'Полное совпадение требует одинаковых рисунка/пикселей/UV, всех поз, монтажа и поворотов, физики/выбора/подъёма, функций и действий. Проверены 153 архитектурные, 2346 канонические RP и 1242 межмодульные пары — все 3741 пары 87 канонических строительных типов.','',
        '## Семь прежних мебельных соответствий','',
        'Это уже существующие канонические реализации. Старые registry читаются, лишние самостоятельные предметы скрыты. Зарезервированные номера не переиспользуются.','',
        '| Старый TEMP → канонический TEMP | Старый asset → канонический |','|---|---|']
    for row in sorted(aliases,key=lambda r:r['retiredTemporaryId']):lines.append(f"| {row['retiredTemporaryId']} → {row['canonicalTemporaryId']} | {row['oldRegistry'].split(':')[1]} → {row['canonicalRegistry'].split(':')[1]} |")
    lines+=['',f"Native {freeze['nativeTests']} и {freeze['coreChecks']} core checks прошли под текущим SHA. Native alias-case проверяет старый registry/UUID/CustomName/typed source/Links и канонический pick/debug. **Реальная средняя кнопка клиента и отдельный диск-цикл семи alias-типов пока ожидаются**: эти проверки не подменяются getter-вызовом или прежними клиентскими PASS.",'',
        '## Почему похожие типы сохранены','',
        '- **Ограды 90002/90011–90017:** стойка и LOW одинаковые, но все 28 пар материалов имеют различающийся используемый RGBA-тексель высокой стороны. Из 81 формы NONE/LOW/TALL только 16 без TALL выглядят одинаково. AUTO/стойка/верхний сосед и ручные 45° учтены.',
        '- **Лестницы 90006/90018/90019:** общий PNG, разные геометрия и UV ступени, наклоны/координаты декора. Общее взбирание и крепление не отменяют другой рисунок.',
        '- **Стекло 90004/90010/90020:** window01 использует другой рисунок. Window03 [90020] прямо сохранён пользователем как исключение; raw глубина тоже отличается. Свой предмет/pick/drop и legacy-наклон сохраняются.',
        '- **RP-ворота/клетки/ограды:** различаются source-выбором, физикой или функцией; wood_gate имеет 32-тиковый импульс, статичный trapdoor не получает выдуманное OPEN-состояние. Полные 134 RP-ресурса и общие контракты поведения проверены.',
        '- **Архитектура ↔ RP:** разрешённое Survival-разрушение архитектуры возвращает один её предмет; RP отклоняет Survival-атаку и удаляется в Creative без выпадения. Это реальное различие действия, дополненное монтажом двери/дерева/лестницы.','',
        '## Основания и границы','',
        f"[Общий JSON](../reports/{a.report.name}), [архитектура](../reports/{a.architecture.name}), [RP 76 / 2850 пар](../reports/{a.rp.name}), [межмодульные 1242 пары](../reports/{a.cross.name}), [побайтное сохранение художественных ресурсов](../reports/{a.resource_delta.name}).",'',
        f"{a.revision_description}; {delta['counts']['allAssets']} художественных assets JAR и защищённые catalogue/дескрипторы совпали с предыдущим кандидатом. Явные отличия code/нехудожественных metadata перечислены в delta report. Предыдущие реальные клиентские результаты не перенесены на новый SHA. Java/ресурсы/исходный город самим аудитом не менялись. Каталог сборок из 1121 исходной модели, финальные числовые ID, конвертация города и ручное принятие остаются незавершёнными.",'']
    a.short_guide.parent.mkdir(parents=True,exist_ok=True);a.short_guide.write_text('\n'.join(lines),encoding='utf8')
    print(json.dumps({'status':report['status'],'sha256':sha,'canonicalPairs':3741,'newGroups':0,'report':str(a.report)},ensure_ascii=False))


if __name__=='__main__':main()
