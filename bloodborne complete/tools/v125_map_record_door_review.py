"""Record actually inspected original textured nomination sheets; no native claim."""
from __future__ import annotations
import json
from v125_map_index import ROOT,BASELINE,dump,sha
DEST=ROOT/'docs/v12.5-city-review/doors'

def main():
    src=json.loads((DEST/'all-door-candidates.json').read_text());evidence=json.loads((DEST/'source-projection-evidence.json').read_text())
    # Human observations from the five actual original PNG sheets viewed at full
    # size in this task. No dimensions/JSON test substitutes for those pixels.
    leaves={1:'Большая деревянная двустворчатая дверь; авторская нижняя часть, V12 90001.',2:'Резной горизонтальный верх двери90001; отдельной створки здесь нет.',29:'Узкая деревянная дверная створка нижней секции; цельный источник93160.',30:'Верх той же узкой деревянной створки93160.',52:'Двустворчатая деревянная дверь в каменной рамке; нижняя часть93152.',53:'Каменный верх с небольшим остеклённым проёмом над дверью93152.',65:'Узкая деревянная дверь с арочным каменным обрамлением,90458.',70:'Резная двустворчатая деревянная дверь; нижняя часть93153.',71:'Небольшой горизонтальный резной верх двери93153.',78:'Резная деревянная двустворчатая панель90479; выглядит как створки, носительtrapdoor не определяет функцию.',90:'Деревянная двустворчатая панель в каменной раме90623; возможная нижняя дверная секция, исходный носительpressure_plate не определяет функцию.'}
    grilles={5:'Широкая металлическая орнаментированная решётка в двух рамах90031; дверь/ворота либо оконная решётка — требуется просмотр назначения.',23:'Вертикальная металлическая створка/панель с орнаментом90143; возможная секция ворот.',24:'Низкая часть большой металлической решётки; источник цельных ворот93156.',25:'Вертикальные прутья верхней части тех же ворот93156.',51:'Двухпанельная орнаментированная металлическая решёткаallium; готовый источник не найден на карте, нужен отдельный preview.',112:'Чёрная металлическая решётка90777; возможная калитка или фрагмент ограды, функция неоднозначна.'}
    ambiguous={42:'Прямоугольная решётка с каменными боковыми стойками90247; визуально пригодна для окна либо калитки.',43:'Та же группа90247 с арочным верхом; окно/калитка неоднозначны.',46:'Высокая каменная боковая рама с вертикальным орнаментом; исходное имяwarped_door/раннее названиегоргулья не доказывает функцию двери.',47:'Открытая U-образная каменная нижняя рама того же авторского набора; это не видимая створка.',48:'Прямоугольная каменная рамка с орнаментом того же набора.',49:'Высокая арочная каменная рама с вертикальным орнаментом того же набора.',80:'Небольшая деревянная жалюзийная панель в каменной рамке93147; ставня либо дверь, нужен просмотр в контексте.'}
    frames={3,10,11,13,14,15,16,17,22,27,31,32,33,34,35,36,37,38,39,40,41,45,50,54,57,60,63,64,72,79,81,82,83,98,99,102,104,105,110,117,118,119,120,121,127,128,130,131,132,133,134,135,136,137,139}
    failed={140,141,142,143,144}
    reviewed=[];candidates=[]
    for projection,row in zip(evidence['models'],src['candidates']):
        no=projection['number'];assert row['sourceModel']==projection['sourceModel']
        if no in leaves:role='VISIBLE_WOODEN_DOOR_LEAF_OR_CONFIRMED_HEADER';obs=leaves[no]
        elif no in grilles:role='METAL_GATE_LEAF_OR_RAILING_REVIEW_CANDIDATE';obs=grilles[no]
        elif no in ambiguous:role='VISIBLE_CONTEXT_AMBIGUITY_REQUIRES_USER_REVIEW';obs=ambiguous[no]
        elif no in failed:role='UNBOUND_TEMPLATE_SOURCE_NOT_FINISHED_DOOR';obs='Видимой законченной модели не получилось: исходный шаблон содержит непараметризованныеtexture/side ссылки; на карте не используется. Не создавать замену.'
        elif no in frames:role='VISIBLE_TRIM_FRAME_RAILING_OR_PILLAR_NOT_A_STANDALONE_DOOR';obs='На просмотренном изображении рама/архитектурный профиль/фрагмент ограды/колонна/настенный декор; самостоятельная дверная створка не показана.'
        else:role='VISIBLE_TREE_LAMP_WINDOW_ROOF_OR_OTHER_NOT_A_DOOR';obs='На просмотренном изображении дерево, светильник, оконная секция, остекление, кровельный/каменный декор; дверной створки нет.'
        result={**row,'reviewNumber':no,'sourceProjectionRole':role,'russianObservation':obs,'sourceProjectionSheet':projection['sheet'],'sourceProjectionSheetSha256':sha(DEST/projection['sheet']),'projectionManuallyViewed':True,'nativeVisualReview':'NOT_RUN','functionDecision':'NOT_CHANGED_NO_OPENABILITY_INFERRED'}
        reviewed.append(result)
        if no in leaves or no in grilles or no in ambiguous:candidates.append(result)
    out={'schemaVersion':1,'status':'SOURCE_PIXELS_REVIEWED_NATIVE_PREVIEW_PENDING','baselineCommit':BASELINE,'sheetsActuallyViewed':evidence['sheets'],'sourceModelsActuallyNominatedAndViewed':len(reviewed),'doorGateOrContextCandidateModels':len(candidates),'candidateModels':candidates,'allReviewedModels':reviewed,'notNativeGameClaim':'All five raw diagnostic projections actually inspected. Model fitting/culling differs from Minecraft; this is source candidate identification, not final gameplay/door behavior or native appearance acceptance.','protectedOriginalV12':'90001 identified only; existing source registry/model/item/behavior remains unchanged.'}
    dump(DEST/'source-visible-door-review.json',out)
    lines=['Кандидаты дверей и ворот: просмотр исходных моделей','Пять исходных текстурированных листов просмотрены; игровая проверка на этом этапе NOT_RUN.','Имена ванильных носителейdoor/trapdoor/fence_gate не использованы как доказательство функции.','Ни одному объекту автоматически не назначено открывание. ИсходнаяV12 не менялась.','']
    for row in candidates:
        ids=sorted({r.get('mainId','') for r in row['canonicalTargets'] if r.get('mainId')})
        ids+=sorted({r['mainId'] for r in row['knownWholeObjects'] if r.get('mainId')})
        lines.append(f'{row["reviewNumber"]:03d}; ID {",".join(sorted(set(ids))) or "исходная служебная часть"}; на карте{row["actualMapApplicationCount"]}; {row["russianObservation"]}; {row["sourceModel"]}')
    (DEST/'DOOR-CANDIDATES-RU.txt').write_text('\n'.join(lines)+'\n',encoding='utf-8-sig')

if __name__=='__main__':main()
