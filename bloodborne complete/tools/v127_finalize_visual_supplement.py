"""Apply two source-visual reviewed NEW grille labels and record final review."""
import copy,json
from v127_visual_export import ROOT,RES,OUT,write_json

NAMES={
    'bloodborne_dw:source_assembly_white_stained_glass_pane':('93228','Решётка с поперечинами','Горизонтальные поперечины короткой решётки; выглядит как серый металл, а не белое стекло.'),
    'bloodborne_dw:source_assembly_yellow_stained_glass_pane':('93229','Высокая узорная решётка','Длинные вертикальные прутья и узор сверху/снизу; отличается от коротких горизонтальных поперечин93228.'),
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
    for row in document['objects']:
        row['reviewStatus']='PASS_SOFTWARE_TEXTURED_FOUR_YAW_VISUAL_REVIEW'
        if row['registryId'] in NAMES:row['name']=NAMES[row['registryId']][1];row['reasonForSeparateType']=NAMES[row['registryId']][2]
        else:row['reasonForSeparateType']='Точная исходная конструкция сухого дерева; состояния сохраняют исходные части ствола и ветвей для исходной карты. Оригинальные деревья V12 защищены от изменений.'
    document['nameChanges']=changes;document['nativeClientReview']='SEE_ROOT_REPORT';write_json(OUT/'final-visual-supplement.json',document)
    print(json.dumps({'status':'PASS_TWO_NEW_LABELS_ONLY','reviewedTypes':len(document['objects']),'reviewedPoses':sum(len(r['poses']) for r in document['objects'])}))

if __name__=='__main__':main()
