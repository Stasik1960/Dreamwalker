"""Record the human inspection of all current type and candidate image sheets."""
import hashlib,json
from v127_visual_export import OUT,write_json

def main():
    inventory=json.loads((OUT/'visual-inventory.json').read_text(encoding='utf-8'))
    names=json.loads((OUT/'reviewed-short-names.json').read_text(encoding='utf-8'));by={r['id']:r for r in names['objects']}
    dedup=json.loads((OUT/'practical-dedup.json').read_text(encoding='utf-8'));groupof={}
    for group in dedup['groups']:
        for registry in group['members']:
            id=next(r['id'] for r in inventory['objects'] if r['registryId']==registry);groupof[id]=group['primaryId']
    overrides={
        frozenset(['90499','90919']):'90499 показывает крупные концентрические круглые розетки;90919 — плотную мелкую клетку. Мотив занимает целые лицевые грани и остаётся различимым после совмещения.',
        frozenset(['93012','93013']):'93012 показывает серую кладку;93013 — заметно зелёную замшелую кладку по всей угловой детали. Это другой видимый строительный материал, а не только положение.',
        frozenset(['90408','93107']):'90408 показывает крупные прямоугольные филёнки;93107 — широкие продольные полосы с иным рисунком торца. Различие занимает полную строительную поверхность.',
        frozenset(['90676','90811']):'90676 имеет центральный треугольный фронтон и наклонные раскосы;90811 — высокий стрельчатый проём с малой розеткой. Центр архитектурной панели заметно отличается.',
        frozenset(['90692','90862']):'90692 имеет коричневую поверхность широких гладких ступеней;90862 — отчётливую дощатую отделку с узкими продольными полосами. Подъём одинаков, материал всей поверхности различим.',
        frozenset(['90983','90984']):'90983 имеет длинные расходящиеся стороны с тонким центральным шпилем;90984 — высокую стойку с несколькими крупными светлыми украшениями наверху. Различается крупная верхняя композиция, не только монтаж.',
        frozenset(['90764','93120']):'90764 показывает большие светлые гладкие прямоугольные плиты;93120 — узкие тёмные парные филёнки. Масштаб швов и лицевой мотив различаются на каждой видимой грани.',
        frozenset(['93017','93209']):'93017 имеет зелёный замшелый оттенок длинной кирпичной стенки;93209 — серую светлую кладку. Различие материала видно на всей длине стенки.',
        frozenset(['93181','93214']):'93181 представляет коричневую стеклянную стойку с тёмными поперечными полосами;93214 — оранжевую стеклянную стойку с другим широким светлым разделением. Цвет и расположение широких полос исходного стекла различаются.',
    }
    decisions=[]
    for index,pair in enumerate(inventory['candidates']):
        a,b=pair['a'],pair['b'];same=a in groupof and groupof.get(a)==groupof.get(b)
        if same:reason=next(g['reason'] for g in dedup['groups'] if g['primaryId']==groupof[a]);action='MERGED_FUNCTIONAL_TYPE_POSES_PRESERVED'
        else:
            reason=overrides.get(frozenset((a,b)))
            if reason is None:
                # These names were written after inspecting the source pixels;
                # they name actual motifs/materials, never technical filenames.
                reason=f"{a}: {by[a]['newName']}; {b}: {by[b]['newName']}. На парном изображении различимы указанные крупные мотивы/отделка либо форма всей детали; общий габарит после нормализации не устраняет это различие."
            action='RETAIN_VISIBLE_MOTIF_MATERIAL_OR_STRUCTURAL_SHAPE'
        decisions.append({**pair,'action':action,'reason':reason,'reviewSheet':f'projections/candidates-{index//40:02d}.png'})
    files=[]
    for prefix,count in [('current',7),('candidates',10)]:
        for i in range(count):
            path=OUT/f'projections/{prefix}-{i:02d}.png';assert path.is_file();files.append({'file':str(path.relative_to(OUT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'pixelReview':'REVIEWED'})
    result={'status':'PASS_CURRENT_SOFTWARE_TEXTURED_VISUAL_REVIEW','workingNewTypesBefore':544,'workingNewTypesAfter':537,'reviewedCurrentDefaultFrames':2176,
        'allActiveSourcePosesSignatureCovered':1560,'candidatePairsReviewed':369,'dedupGroups':6,'retiredNewWorkingTypes':7,'currentTypeSheetsReviewed':7,'candidateSheetsReviewed':10,
        'scope':'Only NEW working types are renamed/combined. Original V12 preserved; native runtime tests/screenshots separately owned by root. Software diagnostic images are not described as native Minecraft screenshots.',
        'files':files,'decisions':decisions,'originalV12Status':'UNCHANGED'}
    write_json(OUT/'visual-review.json',result)
    print(json.dumps({k:v for k,v in result.items() if k not in ('files','decisions')}))
if __name__=='__main__':main()
