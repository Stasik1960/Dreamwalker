"""A final NEW-only label correction from the four-yaw textured review."""
import copy,json
from v127_visual_export import ROOT,RES,OUT,write_json
def main():
    registry='bloodborne_dw:source_stairs_prismarine_brick_stairs_bottom';path=registry.split(':')[1];old='Резная ниша с подвесками';name='Каменная балюстрада'
    descriptor=RES/f'bloodborne_dw/composite/{path}.json';spec=json.loads(descriptor.read_text(encoding='utf-8'));before=copy.deepcopy(spec);assert spec['displayName'] in (old,name);spec['displayName']=name
    comparison=copy.deepcopy(spec);comparison['displayName']=before['displayName'];assert comparison==before;write_json(descriptor,spec)
    catalogue_path=RES/'bloodborne_dw/remaining-catalogue.json';catalogue=json.loads(catalogue_path.read_text(encoding='utf-8'));row=next(r for r in catalogue['objects'] if r['path']==path);row['displayName']=name
    for variant in row['variants']:
        if variant['name']==old:variant['name']=name
    write_json(catalogue_path,catalogue)
    debug_path=RES/'bloodborne_dw/debug_catalogue.json';debug=json.loads(debug_path.read_text(encoding='utf-8'));row=next(r for r in debug['entries'] if r['registryId']==registry);assert row['temporaryId']=='93033';row['name']=name;write_json(debug_path,debug)
    language=RES/'assets/bloodborne_dw/lang/ru_ru.json';translations=json.loads(language.read_text(encoding='utf-8'));translations['block.bloodborne_dw.'+path]=name;write_json(language,translations)
    for filename,key in [('reviewed-short-names.json','newName'),('visual-inventory.json','name'),('final-visual-supplement.json','name')]:
        file=OUT/filename;document=json.loads(file.read_text(encoding='utf-8'))
        def visit(value):
            if isinstance(value,dict):
                if value.get('registryId')==registry and key in value:value[key]=name
                for child in value.values():visit(child)
            elif isinstance(value,list):
                for child in value:visit(child)
        visit(document);write_json(file,document)
    proof={'status':'PASS_NEW_LABEL_ONLY','id':'93033','registryId':registry,'beforeName':old,'afterName':name,'reason':'Четыре textured-проекции показывают каменную ограду с карнизом и столбиками; название балюстрады соответствует видимой конструкции.','descriptorExceptDisplayName':'EXACT','originalV12Modified':False,'modelsTexturesStatesIdsPhysicsModified':False}
    write_json(OUT/'final-balustrade-name-review.json',proof);print(json.dumps(proof,ensure_ascii=False))
if __name__=='__main__':main()
