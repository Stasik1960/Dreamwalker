"""Record explicitly reviewed final source art, changing NEW labels only."""
import copy,json
from v127_visual_export import ROOT,RES,OUT,write_json

NAMES={
    'bloodborne_dw:source_assembly_white_stained_glass_pane':('93228','Решётка с поперечинами','Горизонтальные поперечины короткой решётки; выглядит как серый металл, а не белое стекло.'),
    'bloodborne_dw:source_assembly_yellow_stained_glass_pane':('93229','Высокая узорная решётка','Длинные вертикальные прутья и узор сверху/снизу; отличается от коротких горизонтальных поперечин93228.'),
    'bloodborne_dw:source_warped_gargoyle':('93159','Узорная арочная рама','Каменная рама с арочным узором и исходными верхними/нижними частями; не является открываемой дверью.'),
}
REASONS={
    'bloodborne_dw:source_original_tree':'Точная конструкция сухого дерева со сложными ветвями и исходными слоями ствола; оригинальные деревья V12 сохраняются.',
    'bloodborne_dw:source_door_fragment':'Отдельные исходные фрагменты деревянного портала: верхняя перекладина и нижний сегмент. Они нужны для одиночных частей исходной карты; существующая цельная дверь90001 и удалённый номер90479 сохраняются без изменений.',
    'bloodborne_dw:source_deepslate':'Состояния зеркальной ориентации тёмной деревянной обшивки сохраняют порядок текстур и внешний вид карты; это состояния одного материала, не отдельные рабочие типы.',
}

def main():
    catalogue=json.loads((RES/'bloodborne_dw/remaining-catalogue.json').read_text(encoding='utf-8'))
    debug=json.loads((RES/'bloodborne_dw/debug_catalogue.json').read_text(encoding='utf-8'))
    changes=[]
    for registry,(number,name,reason) in NAMES.items():
        descriptor=RES/'bloodborne_dw/composite'/f"{registry.split(':')[1]}.json"
        spec=json.loads(descriptor.read_text(encoding='utf-8'));before=copy.deepcopy(spec);old=spec['displayName'];spec['displayName']=name
        comparison=copy.deepcopy(spec);comparison['displayName']=before['displayName'];assert comparison==before
        write_json(descriptor,spec)
        row=next(r for r in catalogue['objects'] if 'bloodborne_dw:'+r['path']==registry);row['displayName']=name
        row=next(r for r in debug['entries'] if r['temporaryId']==number);assert row['registryId']==registry;row['name']=name
        changes.append({'id':number,'registryId':registry,'beforeName':old,'afterName':name,'reason':reason,'descriptorApartFromLabel':'EXACT'})
    write_json(RES/'bloodborne_dw/remaining-catalogue.json',catalogue);write_json(RES/'bloodborne_dw/debug_catalogue.json',debug)
    document=json.loads((OUT/'final-visual-supplement.json').read_text(encoding='utf-8'))
    hidden={r['registryId']:r['canonicalRegistryId'] for r in debug['entries'] if r.get('canonicalRegistryId',r['registryId'])!=r['registryId']}
    for row in document['objects']:
        row['reviewStatus']='PASS_SOFTWARE_TEXTURED_FOUR_YAW_VISUAL_REVIEW'
        if row['registryId'] in hidden:row['scope']='HIDDEN_COMPATIBILITY_REGISTRATION';row['canonicalRegistryId']=hidden[row['registryId']]
        if row['registryId'] in NAMES:row['name']=NAMES[row['registryId']][1];row['reasonForSeparateType']=NAMES[row['registryId']][2]
        elif row['registryId'] in REASONS:row['reasonForSeparateType']=REASONS[row['registryId']]
        else:row['reasonForSeparateType']='Это отдельные исходные части существующего функционального типа, сохранённые как его состояния для конвертации неполных конструкций; новый устанавливаемый тип не создаётся.'
    document['nameChanges']=changes;document['nativeClientReview']='SEE_ROOT_REPORT'
    document['sourceVisualDefects']=[{'registryId':'bloodborne_dw:source_oak_prison_door','id':'93160','variant':1,'model':'minecraft:block/addon/oak_door_2','observation':'Исходная верхняя часть не имеет видимых пикселей во всех четырёх textured-проекциях. Независимый аудит подтвердил alpha0 по всем6 исходным UV-граням. Исходник сохранён в архиве; отдельные пустые клетки удаляются при конвертации с точным журналом координат.','conversionEvidence':'docs/v12.7/conversion/source-zero-uv-models.json'}]
    write_json(OUT/'final-visual-supplement.json',document)
    print(json.dumps({'status':'PASS_NEW_LABELS_ONLY','reviewedTypes':len(document['objects']),'reviewedPoses':sum(len(r['poses']) for r in document['objects']),'sourceVisualDefects':len(document['sourceVisualDefects'])}))

if __name__=='__main__':main()
