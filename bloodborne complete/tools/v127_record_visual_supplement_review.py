"""Record image review of the frozen final source-state additions; docs only."""
import json
from v127_visual_export import ROOT,RES,OUT,write_json,digest
from v127_finalize_visual_supplement import REASONS
def main():
    document=json.loads((OUT/'final-visual-supplement.json').read_text(encoding='utf-8'));assert len(document['objects'])==22 and sum(len(r['poses']) for r in document['objects'])==78
    for row in document['objects']:
        path=OUT/row['image'];assert path.is_file();row['reviewStatus']='PASS_SOFTWARE_TEXTURED_FOUR_YAW_VISUAL_REVIEW';row['reviewedImageSha256']=digest(path.read_bytes())
        row['reasonForSeparateType']=REASONS.get(row['registryId'],'Исходные монтажные части, ориентации, материал или точные скрытые состояния существующего функционального типа; добавленные копии для opacity/fluid/luminance не предлагают отдельный рабочий тип или предмет.')
    document['nativeClientReview']='SEE_ROOT_REPORT';document['reviewedWorkingIdsUnchanged']=True
    document['sourceVisualDefects']=[{'registryId':'bloodborne_dw:source_oak_prison_door','id':'93160','variant':1,'model':'minecraft:block/addon/oak_door_2','observation':'Верхний исходный сегмент не имеет видимых пикселей во всех четырёх textured-проекциях; alpha0 подтверждён по всем6 UV-граням. Исходный JSON и PNG сохранены в архиве; пустые отдельные клетки удаляются с точными координатами.','conversionEvidence':'docs/v12.7/conversion/source-zero-uv-models.json'}]
    write_json(OUT/'final-visual-supplement.json',document);print(json.dumps({'status':'PASS_DOC_ONLY_FINAL_SOFTWARE_REVIEW','types':22,'poses':78,'views':312}))
if __name__=='__main__':main()
