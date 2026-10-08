"""Read model closures used by first-set descriptors; record renderer support limits."""
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/architecture/resources'


def main():
    records={}
    def visit(model):
        if model in records:return
        namespace,path=model.split(':',1)
        filename=RES/'assets'/namespace/'models'/(path+'.json')
        data=json.loads(filename.read_text(encoding='utf8'))
        record={'model':model,'sha256':hashlib.sha256(filename.read_bytes()).hexdigest(),
            'elements':len(data.get('elements',[])),
            'tintindices':[face['tintindex'] for element in data.get('elements',[]) for face in element.get('faces',{}).values() if 'tintindex' in face],
            'cullfaces':sorted({face['cullface'] for element in data.get('elements',[]) for face in element.get('faces',{}).values() if 'cullface' in face}),
            'shade_false_elements':sum(element.get('shade') is False for element in data.get('elements',[])),
            'ambient_occlusion':data.get('ambientocclusion','vanilla_default_true')}
        records[model]=record
        if ':' in data.get('parent',''):visit(data['parent'])
    kinds={}
    for filename in sorted((RES/'bloodborne_dw/composite').glob('*.json')):
        document=json.loads(filename.read_text(encoding='utf8'));models=set()
        for variant in document['variants']:
            for pose in variant['poses'].values():
                for part in pose['parts']:
                    for model in [part['model'],part.get('altModel',part['model'])]:models.add(model);visit(model)
        kinds[filename.stem]=sorted(models)
    unsupported=[record for record in records.values() if record['tintindices'] or record['cullfaces']]
    result={'schema':'dreamwalker-composite-render-features-v1','status':'PASS_SOURCE_FEATURE_AUDIT' if not unsupported else 'UNSUPPORTED_FEATURE_REVIEW_REQUIRED',
        'descriptor_kinds':kinds,'model_records':list(records.values()),'no_selected_tintindex':not any(r['tintindices'] for r in records.values()),
        'no_selected_cullface':not any(r['cullfaces'] for r in records.values()),
        'visual_acceptance':'NOT_RUN','shader_shadows':'NOT_RUN','lighting_policy':'Current BER uses supplied root packed light for all parts. Original per-carrier light/AO and shader behavior are not claimed equivalent. Per-part light sampling is a pending visual improvement.'}
    (ROOT/'reports/COMPOSITE_RENDER_FEATURES.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(result['status'],len(records),'models; tint/cull unsupported records',len(unsupported))


if __name__=='__main__':main()
